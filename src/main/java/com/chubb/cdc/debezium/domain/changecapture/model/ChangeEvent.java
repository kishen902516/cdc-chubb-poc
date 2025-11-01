package com.chubb.cdc.debezium.domain.changecapture.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Value object representing a change data capture event.
 * Captures INSERT, UPDATE, or DELETE operations on a database table.
 *
 * Invariants:
 * - INSERT: before must be null, after must be present
 * - UPDATE: both before and after must be present
 * - DELETE: before must be present, after must be null
 *
 * @param table the table identifier where the change occurred
 * @param operation the type of operation (INSERT, UPDATE, DELETE)
 * @param timestamp when the change occurred (UTC)
 * @param position the CDC position/offset for this event
 * @param before the row data before the change (null for INSERT)
 * @param after the row data after the change (null for DELETE)
 * @param metadata additional metadata about the event
 */
public record ChangeEvent(
        TableIdentifier table,
        OperationType operation,
        Instant timestamp,
        CdcPosition position,
        RowData before,
        RowData after,
        Map<String, Object> metadata
) {
    /**
     * Compact constructor with validation.
     */
    public ChangeEvent {
        Objects.requireNonNull(table, "Table identifier cannot be null");
        Objects.requireNonNull(operation, "Operation type cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        Objects.requireNonNull(position, "CDC position cannot be null");
        Objects.requireNonNull(metadata, "Metadata cannot be null");

        // Validate operation-to-data correspondence
        validateEventIntegrity(operation, before, after);

        // Make metadata immutable
        metadata = Map.copyOf(metadata);
    }

    /**
     * Validates that the operation type matches the presence of before/after data.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateEventIntegrity(
            OperationType operation,
            RowData before,
            RowData after
    ) {
        switch (operation) {
            case INSERT -> {
                if (before != null) {
                    throw new IllegalArgumentException(
                            "INSERT operation must have null 'before' data");
                }
                if (after == null) {
                    throw new IllegalArgumentException(
                            "INSERT operation must have non-null 'after' data");
                }
            }
            case UPDATE -> {
                if (before == null) {
                    throw new IllegalArgumentException(
                            "UPDATE operation must have non-null 'before' data");
                }
                if (after == null) {
                    throw new IllegalArgumentException(
                            "UPDATE operation must have non-null 'after' data");
                }
            }
            case DELETE -> {
                if (before == null) {
                    throw new IllegalArgumentException(
                            "DELETE operation must have non-null 'before' data");
                }
                if (after != null) {
                    throw new IllegalArgumentException(
                            "DELETE operation must have null 'after' data");
                }
            }
        }
    }

    /**
     * Checks if this is an INSERT event.
     */
    public boolean isInsert() {
        return operation == OperationType.INSERT;
    }

    /**
     * Checks if this is an UPDATE event.
     */
    public boolean isUpdate() {
        return operation == OperationType.UPDATE;
    }

    /**
     * Checks if this is a DELETE event.
     */
    public boolean isDelete() {
        return operation == OperationType.DELETE;
    }

    /**
     * Gets a metadata value by key.
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * Builder for creating ChangeEvent instances.
     */
    public static class Builder {
        private TableIdentifier table;
        private OperationType operation;
        private Instant timestamp;
        private CdcPosition position;
        private RowData before;
        private RowData after;
        private Map<String, Object> metadata = Map.of();

        public Builder table(TableIdentifier table) {
            this.table = table;
            return this;
        }

        public Builder operation(OperationType operation) {
            this.operation = operation;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder position(CdcPosition position) {
            this.position = position;
            return this;
        }

        public Builder before(RowData before) {
            this.before = before;
            return this;
        }

        public Builder after(RowData after) {
            this.after = after;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ChangeEvent build() {
            return new ChangeEvent(table, operation, timestamp, position, before, after, metadata);
        }
    }

    /**
     * Creates a new builder for ChangeEvent.
     */
    public static Builder builder() {
        return new Builder();
    }
}
