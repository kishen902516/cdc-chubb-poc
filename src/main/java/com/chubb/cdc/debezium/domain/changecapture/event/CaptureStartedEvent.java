package com.chubb.cdc.debezium.domain.changecapture.event;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.time.Instant;

/**
 * Domain event indicating CDC capture has started for a table.
 *
 * <p>Published when the CDC engine begins monitoring a table for changes.</p>
 *
 * @param timestamp when the capture started
 * @param table the table being monitored
 * @param initialPosition the starting CDC position
 */
public record CaptureStartedEvent(
    Instant timestamp,
    TableIdentifier table,
    CdcPosition initialPosition
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if any parameter is null
     */
    public CaptureStartedEvent {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (table == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }
        if (initialPosition == null) {
            throw new IllegalArgumentException("Initial position must not be null");
        }
    }
}
