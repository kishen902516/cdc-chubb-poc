package com.chubb.cdc.debezium.domain.changecapture.model;

import java.util.Objects;

/**
 * Value object representing a unique table identifier.
 * Includes database name, schema (if applicable), and table name.
 *
 * @param database the database name
 * @param schema the schema name (may be null for databases without schema concept)
 * @param table the table name
 */
public record TableIdentifier(
        String database,
        String schema,
        String table
) {
    /**
     * Compact constructor with validation.
     */
    public TableIdentifier {
        Objects.requireNonNull(database, "Database name cannot be null");
        Objects.requireNonNull(table, "Table name cannot be null");

        if (database.isBlank()) {
            throw new IllegalArgumentException("Database name cannot be blank");
        }
        if (table.isBlank()) {
            throw new IllegalArgumentException("Table name cannot be blank");
        }
    }

    /**
     * Returns the fully qualified table name.
     * Format: database.schema.table (if schema is present) or database.table (if no schema)
     *
     * @return the fully qualified table name
     */
    public String fullyQualifiedName() {
        return schema != null && !schema.isBlank()
                ? String.format("%s.%s.%s", database, schema, table)
                : String.format("%s.%s", database, table);
    }

    /**
     * Creates a TableIdentifier without a schema.
     *
     * @param database the database name
     * @param table the table name
     * @return a new TableIdentifier
     */
    public static TableIdentifier of(String database, String table) {
        return new TableIdentifier(database, null, table);
    }

    /**
     * Creates a TableIdentifier with a schema.
     *
     * @param database the database name
     * @param schema the schema name
     * @param table the table name
     * @return a new TableIdentifier
     */
    public static TableIdentifier of(String database, String schema, String table) {
        return new TableIdentifier(database, schema, table);
    }
}
