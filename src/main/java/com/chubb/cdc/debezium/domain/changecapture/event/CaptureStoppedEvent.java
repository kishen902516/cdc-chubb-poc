package com.chubb.cdc.debezium.domain.changecapture.event;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.time.Instant;

/**
 * Domain event indicating CDC capture has stopped for a table.
 *
 * <p>Published when the CDC engine stops monitoring a table, either gracefully or due to an error.</p>
 *
 * @param timestamp when the capture stopped
 * @param table the table that was being monitored
 * @param finalPosition the last CDC position captured
 * @param reason the reason for stopping (GRACEFUL_SHUTDOWN, ERROR, CONFIGURATION_CHANGE)
 */
public record CaptureStoppedEvent(
    Instant timestamp,
    TableIdentifier table,
    CdcPosition finalPosition,
    String reason
) {

    /**
     * Standard stop reasons.
     */
    public static final String REASON_GRACEFUL_SHUTDOWN = "GRACEFUL_SHUTDOWN";
    public static final String REASON_ERROR = "ERROR";
    public static final String REASON_CONFIGURATION_CHANGE = "CONFIGURATION_CHANGE";

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if any parameter is null
     */
    public CaptureStoppedEvent {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (table == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }
        if (finalPosition == null) {
            throw new IllegalArgumentException("Final position must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be null or blank");
        }
    }
}
