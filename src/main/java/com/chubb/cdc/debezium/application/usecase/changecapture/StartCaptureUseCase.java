package com.chubb.cdc.debezium.application.usecase.changecapture;

import com.chubb.cdc.debezium.application.port.input.CdcEngine;
import com.chubb.cdc.debezium.application.port.output.DomainEventPublisher;
import com.chubb.cdc.debezium.domain.changecapture.event.CaptureStartedEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Use Case: Start CDC Capture
 * <p>
 * Responsibility: Initialize and start the Debezium CDC engine with the provided configuration.
 * <p>
 * Business Rules:
 * - Engine must not be already running
 * - Configuration must be valid before starting
 * - Load last known offset position for resume capability
 * - Publish CaptureStartedEvent for each monitored table
 * - Handle initialization failures gracefully
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartCaptureUseCase {

    private final CdcEngine cdcEngine;
    private final OffsetRepository offsetRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Start CDC capture with the given configuration.
     *
     * @param configuration The validated configuration to use for CDC
     * @throws CdcEngine.CdcEngineException if the engine cannot be started
     * @throws IllegalStateException if the engine is already running
     */
    public void execute(ConfigurationAggregate configuration) throws CdcEngine.CdcEngineException {
        log.info("Starting CDC capture process");

        // Check if engine is already running
        if (cdcEngine.isRunning()) {
            String message = "CDC engine is already running. Stop it before starting again.";
            log.warn(message);
            throw new IllegalStateException(message);
        }

        // Validate configuration
        try {
            configuration.validate();
            log.debug("Configuration validated successfully");
        } catch (Exception e) {
            log.error("Configuration validation failed", e);
            throw new CdcEngine.CdcEngineException("Invalid configuration: " + e.getMessage(), e);
        }

        // Load last known offset positions for resume capability
        loadOffsetPositions(configuration);

        // Start the CDC engine
        try {
            cdcEngine.start();
            log.info("CDC engine started successfully");
        } catch (CdcEngine.CdcEngineException e) {
            log.error("Failed to start CDC engine", e);
            throw e;
        }

        // Publish CaptureStartedEvent for each monitored table
        publishCaptureStartedEvents(configuration);

        log.info("CDC capture started for {} table(s)", configuration.getTableConfigs().size());
    }

    /**
     * Load offset positions for all configured tables to enable resume from last position.
     */
    private void loadOffsetPositions(ConfigurationAggregate configuration) {
        log.debug("Loading offset positions for resume capability");

        String sourcePartition = buildSourcePartition(configuration);

        try {
            var position = offsetRepository.load(sourcePartition);
            if (position.isPresent()) {
                log.info("Loaded offset position for resume: {}", position.get());
            } else {
                log.info("No previous offset found - will start from current position");
            }
        } catch (Exception e) {
            log.warn("Failed to load offset position, will start from current position", e);
        }
    }

    /**
     * Publish CaptureStartedEvent for each table being monitored.
     */
    private void publishCaptureStartedEvents(ConfigurationAggregate configuration) {
        log.debug("Publishing CaptureStartedEvent for monitored tables");

        String sourcePartition = buildSourcePartition(configuration);
        Instant now = Instant.now();

        for (TableConfig tableConfig : configuration.getTableConfigs()) {
            TableIdentifier table = tableConfig.table();

            // Load the initial position for this table (or use null if starting fresh)
            var position = offsetRepository.load(sourcePartition)
                    .orElse(null);

            // Create and publish the event
            CaptureStartedEvent event = new CaptureStartedEvent(
                    now,
                    table,
                    position
            );

            try {
                domainEventPublisher.publish(event);
                log.debug("Published CaptureStartedEvent for table: {}", table.fullyQualifiedName());
            } catch (Exception e) {
                log.warn("Failed to publish CaptureStartedEvent for table: {}", table.fullyQualifiedName(), e);
            }
        }
    }

    /**
     * Build a source partition identifier from the configuration.
     * Format: {serverName}-{databaseName}
     */
    private String buildSourcePartition(ConfigurationAggregate configuration) {
        var dbConfig = configuration.getDatabaseConfig();
        return String.format("%s-%s",
                dbConfig.getHost().replaceAll("[^a-zA-Z0-9]", "-"),
                dbConfig.getDatabase()
        );
    }

    /**
     * Restart CDC capture with new configuration.
     * This is useful for configuration hot reload scenarios.
     *
     * @param newConfiguration The new configuration to use
     * @throws CdcEngine.CdcEngineException if restart fails
     */
    public void restart(ConfigurationAggregate newConfiguration) throws CdcEngine.CdcEngineException {
        log.info("Restarting CDC capture with new configuration");

        // Stop if running
        if (cdcEngine.isRunning()) {
            try {
                cdcEngine.stop();
                log.debug("Stopped CDC engine for restart");
            } catch (CdcEngine.CdcEngineException e) {
                log.error("Failed to stop CDC engine for restart", e);
                throw e;
            }
        }

        // Start with new configuration
        execute(newConfiguration);
    }
}
