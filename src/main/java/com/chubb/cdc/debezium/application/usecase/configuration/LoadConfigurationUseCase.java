package com.chubb.cdc.debezium.application.usecase.configuration;

import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository;
import com.chubb.cdc.debezium.domain.configuration.event.ConfigurationLoadedEvent;
import com.chubb.cdc.debezium.application.port.output.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Use Case: Load Configuration
 * <p>
 * Responsibility: Load CDC configuration from external source (YAML file),
 * validate it, and publish ConfigurationLoadedEvent.
 * <p>
 * Business Rules:
 * - Configuration must be valid before being accepted
 * - Failed loads should not affect currently running configuration
 * - Configuration source path must be accessible
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadConfigurationUseCase {

    private final ConfigurationRepository configurationRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Load configuration from the configured source.
     *
     * @return The loaded and validated ConfigurationAggregate
     * @throws ConfigurationLoadException if configuration cannot be loaded or is invalid
     */
    public ConfigurationAggregate execute() {
        log.info("Loading CDC configuration");

        try {
            // Load configuration from repository
            ConfigurationAggregate configuration = configurationRepository.load();
            log.debug("Configuration loaded from source");

            // Validate the configuration
            configuration.validate();
            log.info("Configuration validated successfully");

            // Publish domain event
            ConfigurationLoadedEvent event = new ConfigurationLoadedEvent(
                    Instant.now(),
                    configuration,
                    "file-system" // TODO: Make this configurable based on repository implementation
            );
            domainEventPublisher.publish(event);
            log.debug("Published ConfigurationLoadedEvent");

            return configuration;

        } catch (Exception e) {
            log.error("Failed to load configuration", e);
            throw new ConfigurationLoadException("Failed to load CDC configuration", e);
        }
    }

    /**
     * Load configuration from a specific source path.
     *
     * @param sourcePath The path to the configuration file
     * @return The loaded and validated ConfigurationAggregate
     * @throws ConfigurationLoadException if configuration cannot be loaded or is invalid
     */
    public ConfigurationAggregate executeFromPath(String sourcePath) {
        log.info("Loading CDC configuration from path: {}", sourcePath);

        if (sourcePath == null || sourcePath.isBlank()) {
            throw new ConfigurationLoadException("Configuration source path cannot be null or empty");
        }

        // For now, delegate to the default execute() method
        // In the future, this could be enhanced to support dynamic path loading
        return execute();
    }

    /**
     * Exception thrown when configuration cannot be loaded.
     */
    public static class ConfigurationLoadException extends RuntimeException {
        public ConfigurationLoadException(String message) {
            super(message);
        }

        public ConfigurationLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
