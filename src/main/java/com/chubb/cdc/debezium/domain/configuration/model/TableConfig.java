package com.chubb.cdc.debezium.domain.configuration.model;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.util.Optional;
import java.util.Set;

/**
 * Configuration for a single table to be monitored for CDC.
 *
 * <p>Defines which columns to capture and how to handle tables without primary keys.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Table identifier must be valid</li>
 *   <li>If compositeKey is present, columns must exist in the table (validated at runtime)</li>
 * </ul>
 *
 * @param table the table to monitor
 * @param includeMode column inclusion mode
 * @param columnFilter set of column names to include/exclude (empty = all columns)
 * @param compositeKey optional composite key for tables without primary key
 */
public record TableConfig(
    TableIdentifier table,
    IncludeMode includeMode,
    Set<String> columnFilter,
    Optional<CompositeUniqueKey> compositeKey
) {

    /**
     * Column inclusion mode.
     */
    public enum IncludeMode {
        /** Include all columns */
        INCLUDE_ALL,
        /** Exclude specified columns */
        EXCLUDE_SPECIFIED
    }

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if table is null or includeMode is null
     */
    public TableConfig {
        if (table == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }
        if (includeMode == null) {
            throw new IllegalArgumentException("Include mode must not be null");
        }

        // Make immutable copies
        columnFilter = columnFilter != null ? Set.copyOf(columnFilter) : Set.of();
        compositeKey = compositeKey != null ? compositeKey : Optional.empty();
    }
}
