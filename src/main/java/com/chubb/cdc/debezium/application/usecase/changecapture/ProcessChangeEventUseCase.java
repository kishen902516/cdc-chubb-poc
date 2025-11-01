package com.chubb.cdc.debezium.application.usecase.changecapture;

import com.chubb.cdc.debezium.application.dto.ChangeEventDto;
import com.chubb.cdc.debezium.application.port.output.DataNormalizer;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.chubb.cdc.debezium.domain.changecapture.model.ChangeEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.RowData;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import com.chubb.cdc.debezium.domain.configuration.model.DatabaseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Use Case: Process Change Event
 * <p>
 * Responsibility: Process a captured change event by:
 * 1. Normalizing the data (timestamps, numerics, text)
 * 2. Publishing to Kafka
 * 3. Persisting the offset position
 * <p>
 * Business Rules:
 * - Data must be normalized before publishing
 * - Offset must be saved after successful publish
 * - Failed publishes should be logged but not block capture
 * - Maintain order guarantees (process events sequentially per table)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessChangeEventUseCase {

    private final DataNormalizer dataNormalizer;
    private final EventPublisher eventPublisher;
    private final OffsetRepository offsetRepository;

    /**
     * Process a captured change event.
     *
     * @param event The change event to process
     * @param databaseType The source database type for normalization
     * @throws ProcessingException if the event cannot be processed
     */
    public void execute(ChangeEvent event, DatabaseType databaseType) {
        Instant startTime = Instant.now();
        log.debug("Processing change event: {} on table {}",
                event.operation(),
                event.table().fullyQualifiedName());

        try {
            // Step 1: Normalize the event data
            ChangeEvent normalizedEvent = normalizeEvent(event, databaseType);
            log.trace("Event data normalized for table: {}", event.table().fullyQualifiedName());

            // Step 2: Publish to Kafka
            publishEvent(normalizedEvent);

            // Step 3: Save offset position for recovery
            saveOffsetPosition(normalizedEvent);

            // Calculate and log processing time
            Duration processingTime = Duration.between(startTime, Instant.now());
            log.debug("Successfully processed {} event for table {} in {} ms",
                    event.operation(),
                    event.table().fullyQualifiedName(),
                    processingTime.toMillis());

        } catch (Exception e) {
            log.error("Failed to process change event for table: {}",
                    event.table().fullyQualifiedName(), e);
            throw new ProcessingException(
                    "Failed to process change event for table: " + event.table().fullyQualifiedName(),
                    e
            );
        }
    }

    /**
     * Normalize the change event data.
     * Applies normalization rules to timestamps, numeric types, and text.
     */
    private ChangeEvent normalizeEvent(ChangeEvent event, DatabaseType databaseType) {
        log.trace("Normalizing change event data");

        try {
            // Normalize 'before' data if present
            RowData normalizedBefore = event.before() != null
                    ? dataNormalizer.normalize(event.before().fields(), databaseType)
                    : null;

            // Normalize 'after' data if present
            RowData normalizedAfter = event.after() != null
                    ? dataNormalizer.normalize(event.after().fields(), databaseType)
                    : null;

            // Create normalized event
            return new ChangeEvent(
                    event.table(),
                    event.operation(),
                    event.timestamp(),
                    event.position(),
                    normalizedBefore,
                    normalizedAfter,
                    event.metadata()
            );

        } catch (Exception e) {
            log.error("Failed to normalize event data", e);
            throw new NormalizationException("Data normalization failed", e);
        }
    }

    /**
     * Publish the normalized event to Kafka.
     */
    private void publishEvent(ChangeEvent event) {
        log.trace("Publishing event to Kafka for table: {}", event.table().fullyQualifiedName());

        try {
            // Convert to DTO
            ChangeEventDto dto = convertToDto(event);
            eventPublisher.publish(dto);
            log.trace("Event published successfully to Kafka");

        } catch (Exception e) {
            log.error("Failed to publish event to Kafka for table: {}",
                    event.table().fullyQualifiedName(), e);
            throw new PublishException("Failed to publish event to Kafka", e);
        }
    }

    /**
     * Convert ChangeEvent domain model to DTO for publishing.
     */
    private ChangeEventDto convertToDto(ChangeEvent event) {
        return ChangeEventDto.fromDomain(event);
    }

    /**
     * Save the CDC offset position for recovery.
     * This allows resuming from the correct position after a restart.
     */
    private void saveOffsetPosition(ChangeEvent event) {
        log.trace("Saving offset position for recovery");

        try {
            offsetRepository.save(event.position());
            log.trace("Offset position saved successfully");

        } catch (Exception e) {
            // Log warning but don't fail the processing - Debezium maintains its own offset backup
            log.warn("Failed to save offset position (Debezium fallback will be used): {}",
                    e.getMessage());
        }
    }

    /**
     * Process multiple events in batch.
     * Useful for high-throughput scenarios.
     *
     * @param events The batch of events to process
     * @param databaseType The source database type for normalization
     * @return ProcessingResult containing success/failure counts
     */
    public ProcessingResult executeBatch(java.util.List<ChangeEvent> events, DatabaseType databaseType) {
        log.info("Processing batch of {} events", events.size());

        int successCount = 0;
        int failureCount = 0;
        Instant startTime = Instant.now();

        for (ChangeEvent event : events) {
            try {
                execute(event, databaseType);
                successCount++;
            } catch (ProcessingException e) {
                failureCount++;
                log.error("Failed to process event in batch", e);
            }
        }

        Duration totalTime = Duration.between(startTime, Instant.now());
        log.info("Batch processing complete: {} succeeded, {} failed, {} ms total",
                successCount, failureCount, totalTime.toMillis());

        return new ProcessingResult(successCount, failureCount, totalTime);
    }

    /**
     * Result of batch processing.
     */
    public record ProcessingResult(
            int successCount,
            int failureCount,
            Duration totalDuration
    ) {
        public int totalProcessed() {
            return successCount + failureCount;
        }

        public double successRate() {
            return totalProcessed() > 0
                    ? (double) successCount / totalProcessed()
                    : 0.0;
        }

        public double averageProcessingTimeMs() {
            return totalProcessed() > 0
                    ? (double) totalDuration.toMillis() / totalProcessed()
                    : 0.0;
        }
    }

    /**
     * Exception thrown when event processing fails.
     */
    public static class ProcessingException extends RuntimeException {
        public ProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when data normalization fails.
     */
    public static class NormalizationException extends RuntimeException {
        public NormalizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when event publishing fails.
     */
    public static class PublishException extends RuntimeException {
        public PublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
