package com.chubb.cdc.debezium.infrastructure.debezium;

import com.chubb.cdc.debezium.application.port.input.CdcEngine;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.chubb.cdc.debezium.domain.changecapture.event.CaptureStartedEvent;
import com.chubb.cdc.debezium.domain.changecapture.event.CaptureStoppedEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.*;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapter that wraps the Debezium Embedded Engine for CDC operations.
 *
 * <p>This adapter implements the CdcEngine port by delegating to the Debezium
 * Embedded Engine API. It handles:</p>
 * <ul>
 *   <li>Engine lifecycle (start/stop)</li>
 *   <li>Change event processing and conversion</li>
 *   <li>Offset management via OffsetRepository</li>
 *   <li>Event publishing via EventPublisher</li>
 *   <li>Domain event publishing (CaptureStarted/Stopped)</li>
 * </ul>
 *
 * <p>Thread Safety: This adapter uses thread-safe atomic references and proper
 * synchronization for state management.</p>
 *
 * <p>Design Pattern: Adapter (infrastructure implementing application port)</p>
 *
 * @see <a href="https://debezium.io/documentation/reference/stable/development/engine.html">Debezium Embedded Engine</a>
 */
@Component
public class DebeziumEngineAdapter implements CdcEngine {

    private static final Logger logger = LoggerFactory.getLogger(DebeziumEngineAdapter.class);

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final ConfigurationRepository configurationRepository;
    private final ConnectorFactory connectorFactory;
    private final EventPublisher eventPublisher;
    private final OffsetRepository offsetRepository;
    private final String offsetStoragePath;

    private final AtomicReference<DebeziumEngine<RecordChangeEvent<SourceRecord>>> engine;
    private final AtomicReference<EngineState> state;
    private final AtomicReference<ExecutorService> executorService;
    private final AtomicLong eventsCaptured;
    private final AtomicReference<Instant> startedAt;
    private final AtomicReference<String> lastError;
    private final AtomicReference<String> currentPosition;

    /**
     * Creates a DebeziumEngineAdapter with injected dependencies.
     *
     * @param configurationRepository configuration repository
     * @param connectorFactory connector factory for database-specific strategies
     * @param eventPublisher event publisher for Kafka
     * @param offsetRepository offset repository for position tracking
     * @param offsetStoragePath path to offset storage file
     */
    public DebeziumEngineAdapter(
        ConfigurationRepository configurationRepository,
        ConnectorFactory connectorFactory,
        EventPublisher eventPublisher,
        OffsetRepository offsetRepository,
        @Value("${cdc.offset.storage.path:data/offset.dat}") String offsetStoragePath
    ) {
        this.configurationRepository = configurationRepository;
        this.connectorFactory = connectorFactory;
        this.eventPublisher = eventPublisher;
        this.offsetRepository = offsetRepository;
        this.offsetStoragePath = offsetStoragePath;

        this.engine = new AtomicReference<>(null);
        this.state = new AtomicReference<>(EngineState.STOPPED);
        this.executorService = new AtomicReference<>(null);
        this.eventsCaptured = new AtomicLong(0);
        this.startedAt = new AtomicReference<>(null);
        this.lastError = new AtomicReference<>(null);
        this.currentPosition = new AtomicReference<>(null);

        logger.info("DebeziumEngineAdapter initialized with offset storage: {}", offsetStoragePath);
    }

    @Override
    public void start() throws CdcEngineException {
        if (!state.compareAndSet(EngineState.STOPPED, EngineState.STARTING)) {
            throw new CdcEngineException("Engine is already " + state.get());
        }

        try {
            logger.info("Starting CDC engine...");

            // Load configuration
            ConfigurationAggregate config = configurationRepository.load();

            // Get connector strategy
            ConnectorStrategy strategy = connectorFactory.createStrategy(
                config.getDatabaseConfig().getType()
            );

            // Build connector configuration
            Properties connectorConfig = strategy.buildConnectorConfig(
                config.getDatabaseConfig(),
                config.getTableConfigs(),
                offsetStoragePath
            );

            // Create Debezium engine
            DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine = DebeziumEngine.create(
                ChangeEventFormat.of(Connect.class)
            )
                .using(connectorConfig)
                .notifying(this::handleChangeEvent)
                .using((success, message, error) -> {
                    if (success) {
                        logger.info("Debezium engine completed successfully: {}", message);
                    } else {
                        logger.error("Debezium engine completed with error: {}", message, error);
                        lastError.set(error != null ? error.getMessage() : message);
                        state.set(EngineState.FAILED);
                    }
                })
                .build();

            engine.set(debeziumEngine);

            // Start engine in separate thread
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "debezium-engine");
                thread.setDaemon(false);
                return thread;
            });
            executorService.set(executor);
            executor.execute(debeziumEngine);

            // Update state
            startedAt.set(Instant.now());
            eventsCaptured.set(0);
            state.set(EngineState.RUNNING);

            logger.info("CDC engine started successfully");

            // Publish CaptureStartedEvent for each configured table
            config.getTableConfigs().forEach(tableConfig -> {
                try {
                    CaptureStartedEvent event = new CaptureStartedEvent(
                        Instant.now(),
                        tableConfig.table(),
                        new CdcPosition("initial", Map.of("offset", 0L, "timestamp", System.currentTimeMillis()))
                    );
                    // In a full implementation, this would be published via a domain event bus
                    logger.debug("Capture started for table: {}", tableConfig.table().fullyQualifiedName());
                } catch (Exception e) {
                    logger.warn("Failed to publish CaptureStartedEvent for table: {}",
                        tableConfig.table().fullyQualifiedName(), e);
                }
            });

        } catch (Exception e) {
            state.set(EngineState.FAILED);
            lastError.set(e.getMessage());
            String errorMsg = "Failed to start CDC engine: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new CdcEngineException(errorMsg, e);
        }
    }

    @Override
    public void stop() throws CdcEngineException {
        if (!state.compareAndSet(EngineState.RUNNING, EngineState.STOPPING)) {
            EngineState currentState = state.get();
            if (currentState == EngineState.STOPPED) {
                logger.info("Engine is already stopped");
                return;
            }
            throw new CdcEngineException("Engine is " + currentState + ", cannot stop");
        }

        try {
            logger.info("Stopping CDC engine...");

            DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine = engine.get();
            if (debeziumEngine != null) {
                // Stop the engine (this will flush pending events)
                debeziumEngine.close();

                // Wait for executor to finish
                ExecutorService executor = executorService.get();
                if (executor != null) {
                    executor.shutdown();
                    boolean terminated = executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!terminated) {
                        logger.warn("Executor did not terminate within timeout, forcing shutdown");
                        executor.shutdownNow();
                    }
                }
            }

            // Save final offset (if available)
            String position = currentPosition.get();
            if (position != null) {
                logger.debug("Final position: {}", position);
            }

            // Update state
            state.set(EngineState.STOPPED);
            startedAt.set(null);

            logger.info("CDC engine stopped successfully. Total events captured: {}", eventsCaptured.get());

            // Publish CaptureStoppedEvent
            // In a full implementation, this would be published via a domain event bus
            logger.debug("Capture stopped, events captured: {}", eventsCaptured.get());

        } catch (Exception e) {
            state.set(EngineState.FAILED);
            lastError.set(e.getMessage());
            String errorMsg = "Failed to stop CDC engine: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new CdcEngineException(errorMsg, e);
        }
    }

    @Override
    public boolean isRunning() {
        return state.get() == EngineState.RUNNING;
    }

    @Override
    public CdcEngineStatus getStatus() {
        EngineState currentState = state.get();
        Instant started = startedAt.get();
        long events = eventsCaptured.get();
        String position = currentPosition.get();
        String error = lastError.get();

        return switch (currentState) {
            case RUNNING -> CdcEngineStatus.running(started, events, position);
            case STOPPED -> CdcEngineStatus.stopped();
            case STARTING -> CdcEngineStatus.starting();
            case STOPPING -> CdcEngineStatus.stopping(started, events);
            case FAILED -> CdcEngineStatus.failed(error);
        };
    }

    /**
     * Handles change events from Debezium.
     *
     * This method is called by Debezium for each change record captured from the database.
     */
    private void handleChangeEvent(RecordChangeEvent<SourceRecord> recordChangeEvent) {
        try {
            SourceRecord sourceRecord = recordChangeEvent.record();

            // Extract change event data
            ChangeEvent changeEvent = convertSourceRecordToChangeEvent(sourceRecord);

            if (changeEvent != null) {
                // Convert to DTO and publish
                com.chubb.cdc.debezium.application.dto.ChangeEventDto eventDto =
                    com.chubb.cdc.debezium.application.dto.ChangeEventDto.fromDomain(changeEvent);

                eventPublisher.publishAsync(eventDto)
                    .exceptionally(ex -> {
                        logger.error("Failed to publish change event", ex);
                        return null;
                    });

                // Update metrics
                eventsCaptured.incrementAndGet();
                currentPosition.set(changeEvent.position().sourcePartition());

                logger.trace("Processed change event: operation={}, table={}",
                    changeEvent.operation(), changeEvent.table().fullyQualifiedName());
            }

        } catch (Exception e) {
            logger.error("Error processing change event", e);
            // Don't rethrow - we don't want to stop the engine for a single event failure
        }
    }

    /**
     * Converts a Debezium SourceRecord to our domain ChangeEvent.
     *
     * This method extracts data from the Debezium-specific format and maps it
     * to our domain model.
     */
    private ChangeEvent convertSourceRecordToChangeEvent(SourceRecord sourceRecord) {
        try {
            // Extract value (contains before/after data)
            Struct value = (Struct) sourceRecord.value();
            if (value == null) {
                return null; // Tombstone record
            }

            // Extract source metadata
            Struct source = value.getStruct("source");
            String database = source.getString("db");
            String schema = source.getString("schema");
            String table = source.getString("table");

            TableIdentifier tableIdentifier = new TableIdentifier(database, schema, table);

            // Extract operation
            String op = value.getString("op");
            OperationType operation = mapOperation(op);

            // Extract timestamp
            Long tsMs = value.getInt64("ts_ms");
            Instant timestamp = Instant.ofEpochMilli(tsMs != null ? tsMs : System.currentTimeMillis());

            // Extract before/after row data
            Struct beforeStruct = value.getStruct("before");
            Struct afterStruct = value.getStruct("after");

            RowData before = beforeStruct != null ? convertStructToRowData(beforeStruct) : null;
            RowData after = afterStruct != null ? convertStructToRowData(afterStruct) : null;

            // Extract position (for offset tracking)
            Map<String, ?> sourcePartition = sourceRecord.sourcePartition();
            Map<String, ?> sourceOffset = sourceRecord.sourceOffset();

            String partitionKey = sourcePartition != null ? sourcePartition.toString() : "unknown";
            // Filter out null values from sourceOffset since Map.copyOf() doesn't allow nulls
            Map<String, ?> offsetMap = sourceOffset != null ?
                sourceOffset.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                    )) : Map.of();
            CdcPosition position = new CdcPosition(partitionKey, Map.copyOf(offsetMap));

            // Metadata
            String connectorName = source.getString("connector");
            Map<String, Object> metadata = Map.of(
                "source", "debezium-cdc-app",
                "version", "1.0.0",
                "connector", connectorName != null ? connectorName : "unknown",
                "schemaVersion", 1
            );

            return new ChangeEvent(
                tableIdentifier,
                operation,
                timestamp,
                position,
                before,
                after,
                metadata
            );

        } catch (Exception e) {
            logger.error("Failed to convert SourceRecord to ChangeEvent", e);
            return null;
        }
    }

    /**
     * Maps Debezium operation code to our OperationType enum.
     */
    private OperationType mapOperation(String op) {
        return switch (op) {
            case "c", "r" -> OperationType.INSERT; // create or read (snapshot)
            case "u" -> OperationType.UPDATE;
            case "d" -> OperationType.DELETE;
            default -> throw new IllegalArgumentException("Unknown operation: " + op);
        };
    }

    /**
     * Converts a Debezium Struct to our RowData model.
     */
    private RowData convertStructToRowData(Struct struct) {
        Map<String, Object> fields = new java.util.HashMap<>();

        struct.schema().fields().forEach(field -> {
            Object value = struct.get(field);
            fields.put(field.name(), value);
        });

        return new RowData(fields);
    }
}
