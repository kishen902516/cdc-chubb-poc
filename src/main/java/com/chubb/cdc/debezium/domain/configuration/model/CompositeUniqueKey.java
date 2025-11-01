package com.chubb.cdc.debezium.domain.configuration.model;

import java.util.List;

/**
 * Composite unique key for tables without a primary key.
 *
 * <p>Used to uniquely identify rows in tables that lack a primary key by combining multiple columns.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Must contain at least one column name</li>
 *   <li>Column names must be unique (no duplicates)</li>
 * </ul>
 *
 * @param columnNames ordered list of column names that form the composite key
 */
public record CompositeUniqueKey(List<String> columnNames) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if columnNames is null, empty, or contains duplicates
     */
    public CompositeUniqueKey {
        if (columnNames == null || columnNames.isEmpty()) {
            throw new IllegalArgumentException("Composite unique key must contain at least one column");
        }

        // Check for duplicates
        if (columnNames.size() != columnNames.stream().distinct().count()) {
            throw new IllegalArgumentException("Composite unique key must not contain duplicate column names");
        }

        // Make immutable copy
        columnNames = List.copyOf(columnNames);
    }
}
