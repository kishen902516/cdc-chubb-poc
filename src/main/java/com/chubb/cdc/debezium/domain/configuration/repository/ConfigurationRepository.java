package com.chubb.cdc.debezium.domain.configuration.repository;

import com.chubb.cdc.debezium.domain.configuration.event.ConfigurationChangedEvent;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Repository port for CDC configuration persistence and watching.
 *
 * <p>Manages loading of configuration from external sources (files, config servers)
 * and monitoring for configuration changes.</p>
 *
 * <p>This is a domain port (interface) following the Hexagonal Architecture pattern.
 * Infrastructure adapters will provide concrete implementations.</p>
 */
public interface ConfigurationRepository {

    /**
     * Loads the current configuration.
     *
     * @return the loaded configuration aggregate
     * @throws ConfigurationLoadException if configuration cannot be loaded or is invalid
     */
    ConfigurationAggregate load() throws ConfigurationLoadException;

    /**
     * Gets the timestamp when the configuration was last modified.
     *
     * <p>Used to detect configuration changes for hot reloading.</p>
     *
     * @return the last modification timestamp
     * @throws ConfigurationLoadException if the timestamp cannot be determined
     */
    Instant lastModified() throws ConfigurationLoadException;

    /**
     * Registers a listener to be notified of configuration changes.
     *
     * <p>The listener will be invoked when the configuration changes,
     * allowing the application to react to configuration updates without restart.</p>
     *
     * @param listener the change event listener
     * @throws IllegalArgumentException if listener is null
     */
    void watch(Consumer<ConfigurationChangedEvent> listener);

    /**
     * Exception thrown when configuration cannot be loaded.
     */
    class ConfigurationLoadException extends Exception {
        public ConfigurationLoadException(String message) {
            super(message);
        }

        public ConfigurationLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
