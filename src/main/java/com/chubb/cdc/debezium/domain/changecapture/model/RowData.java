package com.chubb.cdc.debezium.domain.changecapture.model;

import java.util.Map;
import java.util.Objects;

/**
 * Value object representing normalized row data from a database table.
 * All values are normalized (ISO-8601 timestamps, JSON numbers, UTF-8 text).
 *
 * @param fields immutable map of column name to normalized value
 */
public record RowData(
        Map<String, Object> fields
) {
    /**
     * Compact constructor with validation.
     */
    public RowData {
        Objects.requireNonNull(fields, "Fields cannot be null");

        // Make fields immutable
        fields = Map.copyOf(fields);
    }

    /**
     * Gets the value of a specific field.
     *
     * @param fieldName the name of the field
     * @return the field value, or null if not present
     */
    public Object getField(String fieldName) {
        return fields.get(fieldName);
    }

    /**
     * Checks if a field exists in the row data.
     *
     * @param fieldName the name of the field
     * @return true if the field exists
     */
    public boolean hasField(String fieldName) {
        return fields.containsKey(fieldName);
    }

    /**
     * Returns the number of fields in the row data.
     *
     * @return the number of fields
     */
    public int size() {
        return fields.size();
    }

    /**
     * Checks if the row data is empty.
     *
     * @return true if there are no fields
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * Creates an empty RowData.
     *
     * @return an empty RowData instance
     */
    public static RowData empty() {
        return new RowData(Map.of());
    }

    /**
     * Creates a RowData from a map of fields.
     *
     * @param fields the map of field names to values
     * @return a new RowData instance
     */
    public static RowData of(Map<String, Object> fields) {
        return new RowData(fields);
    }
}
