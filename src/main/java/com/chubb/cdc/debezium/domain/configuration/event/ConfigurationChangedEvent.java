package com.chubb.cdc.debezium.domain.configuration.event;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;

import java.time.Instant;
import java.util.Set;

/**
 * Domain event indicating configuration has changed.
 *
 * <p>Published when the application detects and applies a configuration change,
 * typically during a scheduled configuration refresh.</p>
 *
 * @param timestamp when the configuration change was detected
 * @param oldConfig the previous configuration
 * @param newConfig the new configuration
 * @param addedTables tables added in the new configuration
 * @param removedTables tables removed in the new configuration
 */
public record ConfigurationChangedEvent(
    Instant timestamp,
    ConfigurationAggregate oldConfig,
    ConfigurationAggregate newConfig,
    Set<TableIdentifier> addedTables,
    Set<TableIdentifier> removedTables
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if any parameter is null
     */
    public ConfigurationChangedEvent {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (oldConfig == null) {
            throw new IllegalArgumentException("Old configuration must not be null");
        }
        if (newConfig == null) {
            throw new IllegalArgumentException("New configuration must not be null");
        }
        if (addedTables == null) {
            throw new IllegalArgumentException("Added tables must not be null");
        }
        if (removedTables == null) {
            throw new IllegalArgumentException("Removed tables must not be null");
        }

        // Make immutable copies
        addedTables = Set.copyOf(addedTables);
        removedTables = Set.copyOf(removedTables);
    }
}
