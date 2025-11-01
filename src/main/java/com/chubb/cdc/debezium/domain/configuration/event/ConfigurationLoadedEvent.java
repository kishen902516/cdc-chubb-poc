package com.chubb.cdc.debezium.domain.configuration.event;

import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;

import java.time.Instant;

/**
 * Domain event indicating configuration has been loaded.
 *
 * <p>Published when the application successfully loads configuration from a source.</p>
 *
 * @param timestamp when the configuration was loaded
 * @param configuration the loaded configuration aggregate
 * @param source the source of the configuration (file path or config server URL)
 */
public record ConfigurationLoadedEvent(
    Instant timestamp,
    ConfigurationAggregate configuration,
    String source
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if any parameter is null
     */
    public ConfigurationLoadedEvent {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration must not be null");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source must not be null or blank");
        }
    }
}
