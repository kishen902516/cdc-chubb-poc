package com.chubb.cdc.debezium.domain.healthmonitoring.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Capture metrics for a specific time period.
 *
 * <p>Aggregates CDC capture performance metrics including event counts and latency percentiles.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>eventsPublished ≤ eventsCaptured (can't publish more than captured)</li>
 *   <li>P50 ≤ P95 ≤ P99 (mathematical invariant for percentiles)</li>
 * </ul>
 *
 * @param eventsCaptured total number of events captured from source
 * @param eventsPublished total number of events successfully published to Kafka
 * @param eventsFailed total number of events that failed to process
 * @param captureLatencyP50 50th percentile capture latency
 * @param captureLatencyP95 95th percentile capture latency
 * @param captureLatencyP99 99th percentile capture latency
 * @param periodStart start of the measurement period
 * @param periodEnd end of the measurement period
 */
public record CaptureMetrics(
    long eventsCaptured,
    long eventsPublished,
    long eventsFailed,
    Duration captureLatencyP50,
    Duration captureLatencyP95,
    Duration captureLatencyP99,
    Instant periodStart,
    Instant periodEnd
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public CaptureMetrics {
        if (eventsCaptured < 0) {
            throw new IllegalArgumentException("Events captured must not be negative");
        }
        if (eventsPublished < 0) {
            throw new IllegalArgumentException("Events published must not be negative");
        }
        if (eventsFailed < 0) {
            throw new IllegalArgumentException("Events failed must not be negative");
        }
        if (eventsPublished > eventsCaptured) {
            throw new IllegalArgumentException(
                "Events published cannot exceed events captured: published=" + eventsPublished +
                ", captured=" + eventsCaptured
            );
        }
        if (captureLatencyP50 == null || captureLatencyP95 == null || captureLatencyP99 == null) {
            throw new IllegalArgumentException("All latency percentiles must not be null");
        }
        if (captureLatencyP50.compareTo(captureLatencyP95) > 0) {
            throw new IllegalArgumentException("P50 latency must be <= P95 latency");
        }
        if (captureLatencyP95.compareTo(captureLatencyP99) > 0) {
            throw new IllegalArgumentException("P95 latency must be <= P99 latency");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("Period start and end must not be null");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("Period start must be before or equal to period end");
        }
    }
}
