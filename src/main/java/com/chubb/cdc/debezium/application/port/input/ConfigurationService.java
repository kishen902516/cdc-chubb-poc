package com.chubb.cdc.debezium.application.port.input;

import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;

/**
 * Input port for configuration operations.
 *
 * <p>Defines the contract for loading, refreshing, and validating CDC configuration.
 * This is a driving port in Clean Architecture - it defines operations that can be invoked
 * from the presentation layer (REST API, CLI, scheduled tasks) to manage configuration.</p>
 *
 * <p>Implementations are responsible for:</p>
 * <ul>
 *   <li>Loading configuration from external sources (YAML files, config servers)</li>
 *   <li>Validating configuration completeness and correctness</li>
 *   <li>Detecting configuration changes for hot reload</li>
 *   <li>Providing configuration status information</li>
 * </ul>
 */
public interface ConfigurationService {

    /**
     * Loads the CDC configuration from the configured source.
     *
     * <p>Reads the configuration file (or config server), parses it,
     * and returns a validated ConfigurationAggregate.</p>
     *
     * @return the loaded configuration
     * @throws ConfigurationException if loading or parsing fails
     */
    ConfigurationAggregate loadConfiguration() throws ConfigurationException;

    /**
     * Refreshes the configuration by reloading from the source.
     *
     * <p>Checks if the configuration has changed since the last load.
     * If changes are detected, reloads and validates the new configuration.
     * This method is typically called by a scheduled task (every 5 minutes)
     * or triggered manually via the REST API.</p>
     *
     * <p>Returns true if the configuration was refreshed (changed), false if unchanged.</p>
     *
     * @return true if configuration was refreshed, false if unchanged
     * @throws ConfigurationException if refresh fails
     */
    boolean refreshConfiguration() throws ConfigurationException;

    /**
     * Validates a configuration without applying it.
     *
     * <p>Performs all validation checks (database connectivity, Kafka brokers,
     * table names, SSL certificates) without actually changing the active configuration.</p>
     *
     * @param config the configuration to validate
     * @return true if configuration is valid, false otherwise
     */
    boolean validateConfiguration(ConfigurationAggregate config);

    /**
     * Gets the currently active configuration.
     *
     * @return the current configuration, or null if not loaded
     */
    ConfigurationAggregate getCurrentConfiguration();

    /**
     * Exception thrown when configuration operations fail.
     */
    class ConfigurationException extends Exception {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
