package com.chubb.cdc.debezium.domain.changecapture.model;

/**
 * Enumeration of change data capture operation types.
 * Represents the type of database operation that triggered a change event.
 */
public enum OperationType {
    /**
     * A new row was inserted into the table.
     */
    INSERT,

    /**
     * An existing row was updated with new values.
     */
    UPDATE,

    /**
     * A row was deleted from the table.
     */
    DELETE
}
