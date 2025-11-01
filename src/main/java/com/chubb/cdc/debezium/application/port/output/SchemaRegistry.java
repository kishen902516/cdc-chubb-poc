package com.chubb.cdc.debezium.application.port.output;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.util.Map;
import java.util.Optional;

/**
 * Output port for detecting and tracking schema changes.
 *
 * <p>Monitors table schemas to detect changes like column additions, removals, renames,
 * and type changes. Implementations maintain a registry of known schemas and compare
 * incoming data against the registered schema.</p>
 *
 * <p>Part of the Application Layer in Clean Architecture - defines the contract for
 * schema change detection implemented in the infrastructure layer.</p>
 */
public interface SchemaRegistry {

    /**
     * Detects if the current schema differs from the registered schema.
     *
     * <p>Compares the provided schema against the last registered schema for the table.
     * Returns a SchemaChange if differences are detected, or empty if schemas match.</p>
     *
     * @param table the table identifier
     * @param currentSchema the current schema (column name -> type mapping)
     * @return a SchemaChange describing the difference, or empty if no change
     */
    Optional<SchemaChange> detectSchemaChange(
        TableIdentifier table,
        Map<String, Object> currentSchema
    );

    /**
     * Registers or updates the schema for a table.
     *
     * <p>Stores the schema for future comparison. Call this after successfully handling
     * a schema change to update the baseline.</p>
     *
     * @param table the table identifier
     * @param schema the schema to register (column name -> type mapping)
     */
    void registerSchema(TableIdentifier table, Map<String, Object> schema);

    /**
     * Represents a detected schema change.
     */
    record SchemaChange(
        SchemaChangeType type,
        String columnName,
        String oldType,
        String newType
    ) {
        /**
         * Creates a schema change for a column addition.
         */
        public static SchemaChange columnAdded(String columnName, String newType) {
            return new SchemaChange(SchemaChangeType.COLUMN_ADDED, columnName, null, newType);
        }

        /**
         * Creates a schema change for a column removal.
         */
        public static SchemaChange columnRemoved(String columnName, String oldType) {
            return new SchemaChange(SchemaChangeType.COLUMN_REMOVED, columnName, oldType, null);
        }

        /**
         * Creates a schema change for a column type change.
         */
        public static SchemaChange typeChanged(String columnName, String oldType, String newType) {
            return new SchemaChange(SchemaChangeType.TYPE_CHANGED, columnName, oldType, newType);
        }

        /**
         * Creates a schema change for a column rename.
         */
        public static SchemaChange columnRenamed(String oldName, String newName) {
            return new SchemaChange(SchemaChangeType.COLUMN_RENAMED, oldName, null, newName);
        }
    }

    /**
     * Types of schema changes that can be detected.
     */
    enum SchemaChangeType {
        COLUMN_ADDED,
        COLUMN_REMOVED,
        COLUMN_RENAMED,
        TYPE_CHANGED
    }
}
