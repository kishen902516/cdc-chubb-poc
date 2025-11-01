package com.chubb.cdc.debezium.application.port.output;

import com.chubb.cdc.debezium.application.dto.ChangeEventDto;

import java.util.concurrent.CompletableFuture;

/**
 * Output port for publishing change events to Kafka.
 *
 * <p>This port defines the contract for publishing CDC events to external message brokers.
 * Implementations should handle serialization, topic resolution, and delivery guarantees.</p>
 *
 * <p>Part of the Application Layer in Clean Architecture - defines what the application needs
 * from the infrastructure layer for event publishing.</p>
 */
public interface EventPublisher {

    /**
     * Publishes a change event synchronously.
     *
     * <p>Blocks until the event is successfully published or an exception is thrown.</p>
     *
     * @param event the change event to publish
     * @throws EventPublishingException if publishing fails
     */
    void publish(ChangeEventDto event) throws EventPublishingException;

    /**
     * Publishes a change event asynchronously.
     *
     * <p>Returns immediately with a future that completes when the event is published.
     * This allows for non-blocking event publishing with backpressure handling.</p>
     *
     * @param event the change event to publish
     * @return a CompletableFuture that completes when publishing succeeds or fails
     */
    CompletableFuture<Void> publishAsync(ChangeEventDto event);

    /**
     * Exception thrown when event publishing fails.
     */
    class EventPublishingException extends Exception {
        public EventPublishingException(String message) {
            super(message);
        }

        public EventPublishingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
