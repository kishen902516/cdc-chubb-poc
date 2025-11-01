package com.chubb.cdc.debezium.domain.configuration.model;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration aggregate root for CDC application.
 *
 * <p>Manages database connection, table monitoring rules, and Kafka destination configuration.</p>
 *
 * <p><b>Validation Rules:</b></p>
 * <ul>
 *   <li>At least one table must be configured</li>
 *   <li>Database connection details must be valid</li>
 *   <li>Kafka broker list must not be empty</li>
 *   <li>Topic naming pattern must be valid</li>
 * </ul>
 */
public class ConfigurationAggregate {

    private final SourceDatabaseConfig databaseConfig;
    private final Set<TableConfig> tableConfigs;
    private final KafkaConfig kafkaConfig;
    private final Instant loadedAt;

    /**
     * Creates a new configuration aggregate.
     *
     * @param databaseConfig source database configuration
     * @param tableConfigs set of table configurations
     * @param kafkaConfig Kafka destination configuration
     * @param loadedAt timestamp when configuration was loaded
     * @throws IllegalArgumentException if validation fails
     */
    public ConfigurationAggregate(
        SourceDatabaseConfig databaseConfig,
        Set<TableConfig> tableConfigs,
        KafkaConfig kafkaConfig,
        Instant loadedAt
    ) {
        if (databaseConfig == null) {
            throw new IllegalArgumentException("Database configuration must not be null");
        }
        if (tableConfigs == null || tableConfigs.isEmpty()) {
            throw new IllegalArgumentException("At least one table must be configured");
        }
        if (kafkaConfig == null) {
            throw new IllegalArgumentException("Kafka configuration must not be null");
        }
        if (loadedAt == null) {
            throw new IllegalArgumentException("Loaded timestamp must not be null");
        }

        this.databaseConfig = databaseConfig;
        this.tableConfigs = Set.copyOf(tableConfigs);
        this.kafkaConfig = kafkaConfig;
        this.loadedAt = loadedAt;
    }

    /**
     * Validates the entire configuration.
     *
     * <p>Checks that all configuration components are valid and consistent.</p>
     *
     * @throws InvalidConfigurationException if validation fails
     */
    public void validate() throws InvalidConfigurationException {
        try {
            // Database config is validated in its constructor
            // Table configs are validated in their constructors
            // Kafka config is validated in its constructor

            // Additional aggregate-level validations could go here
            // (e.g., checking for duplicate table identifiers)
            Set<TableIdentifier> uniqueTables = tableConfigs.stream()
                .map(TableConfig::table)
                .collect(Collectors.toSet());

            if (uniqueTables.size() != tableConfigs.size()) {
                throw new InvalidConfigurationException("Duplicate table identifiers found in configuration");
            }

        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("Configuration validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if this configuration has changed compared to another configuration.
     *
     * @param other the configuration to compare against
     * @return true if configurations differ
     */
    public boolean hasChangedSince(ConfigurationAggregate other) {
        if (other == null) {
            return true;
        }

        return !this.databaseConfig.equals(other.databaseConfig) ||
               !this.tableConfigs.equals(other.tableConfigs) ||
               !this.kafkaConfig.equals(other.kafkaConfig);
    }

    /**
     * Returns the set of tables added compared to a previous configuration.
     *
     * @param previousConfig the previous configuration
     * @return set of table identifiers that were added
     */
    public Set<TableIdentifier> addedTables(ConfigurationAggregate previousConfig) {
        if (previousConfig == null) {
            return tableConfigs.stream()
                .map(TableConfig::table)
                .collect(Collectors.toSet());
        }

        Set<TableIdentifier> previousTables = previousConfig.tableConfigs.stream()
            .map(TableConfig::table)
            .collect(Collectors.toSet());

        Set<TableIdentifier> currentTables = tableConfigs.stream()
            .map(TableConfig::table)
            .collect(Collectors.toSet());

        Set<TableIdentifier> added = new HashSet<>(currentTables);
        added.removeAll(previousTables);
        return added;
    }

    /**
     * Returns the set of tables removed compared to a previous configuration.
     *
     * @param previousConfig the previous configuration
     * @return set of table identifiers that were removed
     */
    public Set<TableIdentifier> removedTables(ConfigurationAggregate previousConfig) {
        if (previousConfig == null) {
            return Set.of();
        }

        Set<TableIdentifier> previousTables = previousConfig.tableConfigs.stream()
            .map(TableConfig::table)
            .collect(Collectors.toSet());

        Set<TableIdentifier> currentTables = tableConfigs.stream()
            .map(TableConfig::table)
            .collect(Collectors.toSet());

        Set<TableIdentifier> removed = new HashSet<>(previousTables);
        removed.removeAll(currentTables);
        return removed;
    }

    public SourceDatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public Set<TableConfig> getTableConfigs() {
        return tableConfigs;
    }

    public KafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigurationAggregate that = (ConfigurationAggregate) o;
        return Objects.equals(databaseConfig, that.databaseConfig) &&
               Objects.equals(tableConfigs, that.tableConfigs) &&
               Objects.equals(kafkaConfig, that.kafkaConfig) &&
               Objects.equals(loadedAt, that.loadedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(databaseConfig, tableConfigs, kafkaConfig, loadedAt);
    }

    /**
     * Exception thrown when configuration validation fails.
     */
    public static class InvalidConfigurationException extends Exception {
        public InvalidConfigurationException(String message) {
            super(message);
        }

        public InvalidConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
