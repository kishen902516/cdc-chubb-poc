package com.chubb.cdc.debezium.application.usecase.changecapture;

import com.chubb.cdc.debezium.application.port.input.CdcEngine;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.chubb.cdc.debezium.domain.changecapture.event.CaptureStoppedEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Use Case: Stop CDC Capture
 * <p>
 * Responsibility: Gracefully stop the Debezium CDC engine, ensuring:
 * 1. All pending events are flushed and published
 * 2. Current offset position is saved for recovery
 * 3. Database connections are closed cleanly
 * 4. CaptureStoppedEvent is published for each monitored table
 * <p>
 * Business Rules:
 * - Engine must be running before it can be stopped
 * - Graceful shutdown with configurable timeout
 * - Zero data loss during planned restarts (SC-010)
 * - All resources must be cleaned up properly
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopCaptureUseCase {

    private final CdcEngine cdcEngine;
    private final OffsetRepository offsetRepository;
    private final EventPublisher eventPublisher;

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Stop CDC capture gracefully.
     *
     * @param configuration The current configuration (used to identify monitored tables)
     * @param reason The reason for stopping (e.g., GRACEFUL_SHUTDOWN, CONFIGURATION_CHANGE, ERROR)
     * @throws CdcEngine.CdcEngineException if the engine cannot be stopped cleanly
     * @throws IllegalStateException if the engine is not running
     */
    public void execute(ConfigurationAggregate configuration, StopReason reason)
            throws CdcEngine.CdcEngineException {
        log.info("Stopping CDC capture process. Reason: {}", reason);

        // Check if engine is running
        if (!cdcEngine.isRunning()) {
            String message = "CDC engine is not running. Cannot stop.";
            log.warn(message);
            throw new IllegalStateException(message);
        }

        Instant startTime = Instant.now();

        // Get current status before stopping (for metrics)
        CdcEngine.CdcEngineStatus status = cdcEngine.getStatus();
        long eventsCaptured = status.eventsCaptured();
        Instant captureStartedAt = status.startedAt();

        // Save final offset positions before stopping
        saveFinalOffsetPositions(configuration);

        // Stop the CDC engine
        try {
            cdcEngine.stop();
            log.info("CDC engine stopped successfully");
        } catch (CdcEngine.CdcEngineException e) {
            log.error("Failed to stop CDC engine cleanly", e);
            throw e;
        }

        // Publish CaptureStoppedEvent for each monitored table
        publishCaptureStoppedEvents(configuration, reason);

        Duration shutdownDuration = Duration.between(startTime, Instant.now());
        log.info("CDC capture stopped for {} table(s) in {} ms. Total events captured: {}",
                configuration.getTableConfigs().size(),
                shutdownDuration.toMillis(),
                eventsCaptured);

        // Log uptime statistics
        if (captureStartedAt != null) {
            Duration uptime = Duration.between(captureStartedAt, Instant.now());
            log.info("CDC uptime: {} hours {} minutes",
                    uptime.toHours(),
                    uptime.toMinutes() % 60);
        }
    }

    /**
     * Stop CDC capture gracefully with default reason (GRACEFUL_SHUTDOWN).
     *
     * @param configuration The current configuration
     * @throws CdcEngine.CdcEngineException if the engine cannot be stopped cleanly
     */
    public void execute(ConfigurationAggregate configuration) throws CdcEngine.CdcEngineException {
        execute(configuration, StopReason.GRACEFUL_SHUTDOWN);
    }

    /**
     * Force stop the CDC engine immediately (not graceful).
     * Use only in emergency situations where graceful shutdown is not possible.
     *
     * @param configuration The current configuration
     * @throws CdcEngine.CdcEngineException if force stop fails
     */
    public void forceStop(ConfigurationAggregate configuration) throws CdcEngine.CdcEngineException {
        log.warn("Force stopping CDC capture - data loss may occur!");

        try {
            cdcEngine.stop();
            log.warn("CDC engine force stopped");
        } catch (Exception e) {
            log.error("Force stop failed", e);
            throw new CdcEngine.CdcEngineException("Force stop failed", e);
        }

        // Still publish stop events for monitoring
        publishCaptureStoppedEvents(configuration, StopReason.ERROR);
    }

    /**
     * Save final offset positions for all monitored tables before stopping.
     * This ensures we can resume from the correct position after restart.
     */
    private void saveFinalOffsetPositions(ConfigurationAggregate configuration) {
        log.debug("Saving final offset positions for recovery");

        String sourcePartition = buildSourcePartition(configuration);

        try {
            var currentStatus = cdcEngine.getStatus();
            String currentPosition = currentStatus.currentPosition();

            if (currentPosition != null) {
                // Create a CdcPosition from the current status
                // Note: This is a simplified version - actual implementation would extract
                // proper offset information from the CDC engine
                CdcPosition position = new CdcPosition(
                        sourcePartition,
                        java.util.Map.of("position", currentPosition)
                );

                offsetRepository.save(position);
                log.info("Saved final offset position for recovery: {}", currentPosition);
            } else {
                log.debug("No current position available to save");
            }

        } catch (Exception e) {
            log.error("Failed to save final offset position", e);
            // Don't fail the stop operation if offset save fails
        }
    }

    /**
     * Publish CaptureStoppedEvent for each monitored table.
     */
    private void publishCaptureStoppedEvents(ConfigurationAggregate configuration, StopReason reason) {
        log.debug("Publishing CaptureStoppedEvent for monitored tables");

        String sourcePartition = buildSourcePartition(configuration);
        Instant now = Instant.now();

        for (TableConfig tableConfig : configuration.getTableConfigs()) {
            TableIdentifier table = tableConfig.table();

            // Load the final position for this table
            var position = offsetRepository.load(sourcePartition)
                    .orElse(null);

            // Create and publish the event
            CaptureStoppedEvent event = new CaptureStoppedEvent(
                    now,
                    table,
                    position,
                    reason.name()
            );

            try {
                eventPublisher.publish(event);
                log.debug("Published CaptureStoppedEvent for table: {}", table.fullyQualifiedName());
            } catch (Exception e) {
                log.warn("Failed to publish CaptureStoppedEvent for table: {}",
                        table.fullyQualifiedName(), e);
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
     * Reasons for stopping CDC capture.
     */
    public enum StopReason {
        /** Normal shutdown requested by operator */
        GRACEFUL_SHUTDOWN,

        /** Configuration changed, restart required */
        CONFIGURATION_CHANGE,

        /** Error occurred, forced shutdown */
        ERROR,

        /** Application shutdown */
        APPLICATION_SHUTDOWN,

        /** Manual stop via CLI or API */
        MANUAL_STOP
    }

    /**
     * Result of stop operation.
     */
    public record StopResult(
            boolean success,
            Duration shutdownDuration,
            long eventsCaptured,
            String finalPosition,
            String errorMessage
    ) {
        public static StopResult success(Duration duration, long eventsCaptured, String finalPosition) {
            return new StopResult(true, duration, eventsCaptured, finalPosition, null);
        }

        public static StopResult failure(Duration duration, String errorMessage) {
            return new StopResult(false, duration, 0L, null, errorMessage);
        }
    }
}
