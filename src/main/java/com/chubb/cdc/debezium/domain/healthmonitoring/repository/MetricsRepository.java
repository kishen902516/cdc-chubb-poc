package com.chubb.cdc.debezium.domain.healthmonitoring.repository;

import com.chubb.cdc.debezium.domain.changecapture.model.ChangeEvent;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.CaptureMetrics;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.TableMetrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Repository port for CDC metrics collection and retrieval.
 *
 * <p>Manages capture metrics including event counts, latency measurements,
 * and per-table statistics.</p>
 *
 * <p>This is a domain port (interface) following the Hexagonal Architecture pattern.
 * Infrastructure adapters will provide concrete implementations.</p>
 */
public interface MetricsRepository {

    /**
     * Records a captured event and its capture latency.
     *
     * <p>Updates overall metrics and table-specific metrics.</p>
     *
     * @param event the captured change event
     * @param captureLatency the time taken to capture and process the event
     * @throws IllegalArgumentException if event or captureLatency is null
     */
    void recordEvent(ChangeEvent event, Duration captureLatency);

    /**
     * Gets aggregate capture metrics for a time period.
     *
     * @param start the start of the time period (inclusive)
     * @return capture metrics for the period from start to now
     * @throws IllegalArgumentException if start is null or in the future
     */
    CaptureMetrics getMetricsSince(Instant start);

    /**
     * Gets per-table capture metrics.
     *
     * @return list of metrics for each monitored table
     */
    List<TableMetrics> getTableMetrics();

    /**
     * Resets all metrics.
     *
     * <p>Used for testing or when restarting monitoring with a clean slate.</p>
     */
    void reset();
}
