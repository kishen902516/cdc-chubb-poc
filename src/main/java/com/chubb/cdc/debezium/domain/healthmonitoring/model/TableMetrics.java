package com.chubb.cdc.debezium.domain.healthmonitoring.model;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;

import java.time.Instant;

/**
 * Metrics for a specific table being monitored.
 *
 * <p>Tracks operation counts and last captured event information per table.</p>
 *
 * @param table the table identifier
 * @param insertCount number of INSERT operations captured
 * @param updateCount number of UPDATE operations captured
 * @param deleteCount number of DELETE operations captured
 * @param lastEventTimestamp timestamp of the most recent captured event
 * @param lastPosition CDC position of the most recent captured event
 */
public record TableMetrics(
    TableIdentifier table,
    long insertCount,
    long updateCount,
    long deleteCount,
    Instant lastEventTimestamp,
    CdcPosition lastPosition
) {

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public TableMetrics {
        if (table == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }
        if (insertCount < 0) {
            throw new IllegalArgumentException("Insert count must not be negative");
        }
        if (updateCount < 0) {
            throw new IllegalArgumentException("Update count must not be negative");
        }
        if (deleteCount < 0) {
            throw new IllegalArgumentException("Delete count must not be negative");
        }
        if (lastEventTimestamp == null) {
            throw new IllegalArgumentException("Last event timestamp must not be null");
        }
        if (lastPosition == null) {
            throw new IllegalArgumentException("Last position must not be null");
        }
    }
}
