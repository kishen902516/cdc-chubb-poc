package com.chubb.cdc.debezium.infrastructure.kafka;

import com.chubb.cdc.debezium.application.dto.ChangeEventDto;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka implementation of EventPublisher port.
 *
 * <p>Publishes CDC change events to Kafka topics with:</p>
 * <ul>
 *   <li>Topic name resolution using TopicNameResolver</li>
 *   <li>JSON serialization matching change-event-schema.json contract</li>
 *   <li>Retry mechanism (max 3 attempts)</li>
 *   <li>Async publishing with callbacks</li>
 * </ul>
 *
 * <p>Design Pattern: Adapter (infrastructure implementing application port)</p>
 */
@Service
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TopicNameResolver topicNameResolver;
    private final ObjectMapper objectMapper;

    /**
     * Creates a KafkaEventPublisher with injected dependencies.
     *
     * @param kafkaTemplate Spring Kafka template for message sending
     * @param topicNameResolver topic name resolver
     */
    public KafkaEventPublisher(
        KafkaTemplate<String, String> kafkaTemplate,
        TopicNameResolver topicNameResolver
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicNameResolver = topicNameResolver;
        this.objectMapper = createObjectMapper();
        logger.info("KafkaEventPublisher initialized");
    }

    @Override
    public void publish(ChangeEventDto event) throws EventPublishingException {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }

        try {
            // Resolve topic name
            String topicName = topicNameResolver.resolve(
                new com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier(
                    event.table().database(),
                    event.table().schema(),
                    event.table().table()
                )
            );

            // Serialize event to JSON
            String jsonPayload = serializeEvent(event);

            // Generate message key (use table identifier for partitioning)
            String messageKey = generateMessageKey(event);

            // Publish with retry
            publishWithRetry(topicName, messageKey, jsonPayload);

            logger.debug("Successfully published event to topic {}: operation={}, table={}",
                topicName, event.operation(), event.table().table());

        } catch (TopicNameResolver.InvalidTopicNameException e) {
            String errorMsg = "Failed to resolve topic name for event: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new EventPublishingException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = "Failed to publish event: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new EventPublishingException(errorMsg, e);
        }
    }

    @Override
    public CompletableFuture<Void> publishAsync(ChangeEventDto event) {
        if (event == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Event must not be null")
            );
        }

        return CompletableFuture.runAsync(() -> {
            try {
                publish(event);
            } catch (EventPublishingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Publishes a message to Kafka with retry mechanism.
     */
    private void publishWithRetry(String topicName, String key, String payload)
        throws EventPublishingException {

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topicName, key, payload);

                // Wait for send to complete (with timeout)
                SendResult<String, String> result = future.get(30, TimeUnit.SECONDS);

                logger.trace("Message sent to topic {} partition {} offset {}",
                    topicName,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

                return; // Success

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt < MAX_RETRY_ATTEMPTS) {
                    logger.warn("Failed to publish to Kafka (attempt {}/{}), retrying in {}ms",
                        attempt, MAX_RETRY_ATTEMPTS, RETRY_DELAY_MS, e);

                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new EventPublishingException("Publishing interrupted", ie);
                    }
                }
            }
        }

        // All retries exhausted
        String errorMsg = String.format("Failed to publish to Kafka after %d attempts", MAX_RETRY_ATTEMPTS);
        logger.error(errorMsg, lastException);
        throw new EventPublishingException(errorMsg, lastException);
    }

    /**
     * Serializes a ChangeEventDto to JSON.
     *
     * JSON format must match specs/001-debezium-cdc-app/contracts/change-event-schema.json
     */
    private String serializeEvent(ChangeEventDto event) throws EventPublishingException {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            String errorMsg = "Failed to serialize event to JSON";
            logger.error(errorMsg, e);
            throw new EventPublishingException(errorMsg, e);
        }
    }

    /**
     * Generates a message key for partitioning.
     *
     * Uses table's fully qualified name for consistent partitioning.
     */
    private String generateMessageKey(ChangeEventDto event) {
        String schema = event.table().schema() != null ? event.table().schema() + "." : "";
        String database = event.table().database() != null ? event.table().database() + "." : "";
        return database + schema + event.table().table();
    }

    /**
     * Creates and configures the ObjectMapper for JSON serialization.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
}
