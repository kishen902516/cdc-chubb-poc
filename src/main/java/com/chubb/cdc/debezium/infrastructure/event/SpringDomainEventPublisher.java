package com.chubb.cdc.debezium.infrastructure.event;

import com.chubb.cdc.debezium.application.port.output.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring-based implementation of DomainEventPublisher.
 * <p>
 * This implementation leverages Spring's ApplicationEventPublisher to publish
 * domain events within the application context. This allows other components
 * to listen for these events using Spring's @EventListener mechanism.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        if (event == null) {
            log.warn("Attempted to publish null event, ignoring");
            return;
        }

        try {
            log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
            applicationEventPublisher.publishEvent(event);
            log.debug("Successfully published domain event: {}", event.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to publish domain event: {}", event.getClass().getSimpleName(), e);
            // Re-throw as runtime exception to maintain the interface contract
            throw new RuntimeException("Failed to publish domain event", e);
        }
    }
}