package com.chubb.cdc.debezium.domain.healthmonitoring.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Health check result for CDC engine status.
 *
 * <p>Captures the operational state of the CDC capture engine including whether it's
 * actively capturing and how many tables are being monitored.</p>
 *
 * @param state the health state (UP, DOWN, DEGRADED, UNKNOWN)
 * @param message human-readable status message
 * @param checkedAt timestamp when check was performed
 * @param isCapturing whether CDC engine is actively capturing changes
 * @param monitoredTables number of tables currently being monitored
 * @param errorMessage error message if check failed
 */
public record CdcEngineHealthCheck(
    HealthState state,
    String message,
    Instant checkedAt,
    boolean isCapturing,
    int monitoredTables,
    Optional<String> errorMessage
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if required fields are null or monitoredTables is negative
     */
    public CdcEngineHealthCheck {
        if (state == null) {
            throw new IllegalArgumentException("Health state must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        if (checkedAt == null) {
            throw new IllegalArgumentException("Checked timestamp must not be null");
        }
        if (monitoredTables < 0) {
            throw new IllegalArgumentException("Monitored tables count must not be negative");
        }

        errorMessage = errorMessage != null ? errorMessage : Optional.empty();
    }
}
