package com.chubb.cdc.debezium.infrastructure.configuration;

/**
 * Exception thrown when configuration cannot be loaded or is invalid.
 *
 * This is an infrastructure-layer exception that wraps various loading failures
 * (file not found, YAML parse error, validation failure, etc.)
 */
public class ConfigurationLoadException extends RuntimeException {

    public ConfigurationLoadException(String message) {
        super(message);
    }

    public ConfigurationLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
