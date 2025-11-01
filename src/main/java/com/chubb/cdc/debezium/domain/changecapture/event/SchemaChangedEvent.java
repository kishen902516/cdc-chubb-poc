package com.chubb.cdc.debezium.domain.changecapture.event;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.time.Instant;

/**
 * Domain event indicating a table schema has changed.
 *
 * <p>Published when the CDC engine detects a schema change (DDL operation) on a monitored table.</p>
 *
 * @param timestamp when the schema change was detected
 * @param table the table whose schema changed
 * @param change details of the schema change
 */
public record SchemaChangedEvent(
    Instant timestamp,
    TableIdentifier table,
    SchemaChange change
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if any parameter is null
     */
    public SchemaChangedEvent {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (table == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }
        if (change == null) {
            throw new IllegalArgumentException("Schema change must not be null");
        }
    }

    /**
     * Details of a schema change operation.
     *
     * @param type the type of schema change
     * @param columnName the affected column name
     * @param oldType the previous type (may be null for COLUMN_ADDED)
     * @param newType the new type (may be null for COLUMN_REMOVED)
     */
    public record SchemaChange(
        SchemaChangeType type,
        String columnName,
        String oldType,
        String newType
    ) {

        /**
         * Compact constructor with validation.
         *
         * @throws IllegalArgumentException if type or columnName is null
         */
        public SchemaChange {
            if (type == null) {
                throw new IllegalArgumentException("Schema change type must not be null");
            }
            if (columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("Column name must not be null or blank");
            }
        }
    }

    /**
     * Types of schema changes that can be detected.
     */
    public enum SchemaChangeType {
        /** A new column was added to the table */
        COLUMN_ADDED,

        /** A column was removed from the table */
        COLUMN_REMOVED,

        /** A column was renamed */
        COLUMN_RENAMED,

        /** A column's data type was changed */
        TYPE_CHANGED
    }
}
