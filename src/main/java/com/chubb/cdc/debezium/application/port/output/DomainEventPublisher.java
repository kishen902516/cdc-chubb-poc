package com.chubb.cdc.debezium.application.port.output;

/**
 * Output port for publishing domain events within the application.
 * <p>
 * This is separate from EventPublisher which publishes CDC events to Kafka.
 * Domain events are used for internal application communication (e.g., metrics, logging).
 */
public interface DomainEventPublisher {

    /**
     * Publishes a domain event.
     *
     * @param event the domain event to publish
     */
    void publish(Object event);
}
