package com.chubb.cdc.debezium.domain.healthmonitoring.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Health check result for Kafka connectivity.
 *
 * <p>Captures the result of a Kafka broker availability check including the number of
 * available brokers and any error information.</p>
 *
 * @param state the health state (UP, DOWN, DEGRADED, UNKNOWN)
 * @param message human-readable status message
 * @param checkedAt timestamp when check was performed
 * @param availableBrokers number of available Kafka brokers
 * @param errorMessage error message if check failed
 */
public record KafkaHealthCheck(
    HealthState state,
    String message,
    Instant checkedAt,
    int availableBrokers,
    Optional<String> errorMessage
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if required fields are null or availableBrokers is negative
     */
    public KafkaHealthCheck {
        if (state == null) {
            throw new IllegalArgumentException("Health state must not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        if (checkedAt == null) {
            throw new IllegalArgumentException("Checked timestamp must not be null");
        }
        if (availableBrokers < 0) {
            throw new IllegalArgumentException("Available brokers count must not be negative");
        }

        errorMessage = errorMessage != null ? errorMessage : Optional.empty();
    }
}
