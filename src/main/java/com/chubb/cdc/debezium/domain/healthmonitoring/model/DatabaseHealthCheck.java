package com.chubb.cdc.debezium.domain.healthmonitoring.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Health check result for database connectivity.
 *
 * <p>Captures the result of a database connection health check including connection time
 * and any error information.</p>
 *
 * @param state the health state (UP, DOWN, DEGRADED, UNKNOWN)
 * @param message human-readable status message
 * @param checkedAt timestamp when check was performed
 * @param connectionTime time taken to establish connection (if successful)
 * @param errorMessage error message if check failed
 */
public record DatabaseHealthCheck(
    HealthState state,
    String message,
    Instant checkedAt,
    Optional<Duration> connectionTime,
    Optional<String> errorMessage
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if required fields are null
     */
    public DatabaseHealthCheck {
        if (state == null) {
            throw new IllegalArgumentException("Health state must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        if (checkedAt == null) {
            throw new IllegalArgumentException("Checked timestamp must not be null");
        }

        connectionTime = connectionTime != null ? connectionTime : Optional.empty();
        errorMessage = errorMessage != null ? errorMessage : Optional.empty();
    }
}
