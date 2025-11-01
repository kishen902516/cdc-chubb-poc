package com.chubb.cdc.debezium.domain.changecapture.model;

import java.util.Map;
import java.util.Objects;

/**
 * Value object representing the current position in the CDC stream.
 * Used to track where CDC processing left off and enable recovery.
 *
 * @param sourcePartition identifies which database/connector this position belongs to
 * @param offset opaque offset data (LSN for PostgreSQL, binlog position for MySQL, etc.)
 */
public record CdcPosition(
        String sourcePartition,
        Map<String, Object> offset
) implements Comparable<CdcPosition> {

    /**
     * Compact constructor with validation.
     */
    public CdcPosition {
        Objects.requireNonNull(sourcePartition, "Source partition cannot be null");
        Objects.requireNonNull(offset, "Offset cannot be null");

        if (sourcePartition.isBlank()) {
            throw new IllegalArgumentException("Source partition cannot be blank");
        }
        if (offset.isEmpty()) {
            throw new IllegalArgumentException("Offset cannot be empty");
        }

        // Make offset immutable
        offset = Map.copyOf(offset);
    }

    /**
     * Compares two CDC positions to determine their order.
     * This is used for determining if position A is before or after position B.
     *
     * Note: Comparison is based on timestamp if available in offset,
     * otherwise positions from different partitions are considered equal.
     *
     * @param other the other CDC position to compare with
     * @return negative if this position is before other, positive if after, 0 if equal
     */
    @Override
    public int compareTo(CdcPosition other) {
        if (this.equals(other)) {
            return 0;
        }

        // Different partitions cannot be directly compared
        if (!this.sourcePartition.equals(other.sourcePartition)) {
            return this.sourcePartition.compareTo(other.sourcePartition);
        }

        // Try to compare by timestamp if available
        Long thisTimestamp = extractTimestamp(this.offset);
        Long otherTimestamp = extractTimestamp(other.offset);

        if (thisTimestamp != null && otherTimestamp != null) {
            return thisTimestamp.compareTo(otherTimestamp);
        }

        // Try to compare by sequence number if available
        Long thisSeq = extractSequenceNumber(this.offset);
        Long otherSeq = extractSequenceNumber(other.offset);

        if (thisSeq != null && otherSeq != null) {
            return thisSeq.compareTo(otherSeq);
        }

        // Cannot determine order, consider them equal
        return 0;
    }

    /**
     * Extracts timestamp from offset map if present.
     */
    private static Long extractTimestamp(Map<String, Object> offset) {
        Object timestamp = offset.get("timestamp");
        if (timestamp instanceof Number) {
            return ((Number) timestamp).longValue();
        }
        timestamp = offset.get("ts_ms");
        if (timestamp instanceof Number) {
            return ((Number) timestamp).longValue();
        }
        return null;
    }

    /**
     * Extracts sequence number from offset map if present.
     */
    private static Long extractSequenceNumber(Map<String, Object> offset) {
        Object seq = offset.get("sequence");
        if (seq instanceof Number) {
            return ((Number) seq).longValue();
        }
        seq = offset.get("event");
        if (seq instanceof Number) {
            return ((Number) seq).longValue();
        }
        return null;
    }

    /**
     * Checks if this position is after another position.
     *
     * @param other the other position to compare with
     * @return true if this position is after the other
     */
    public boolean isAfter(CdcPosition other) {
        return this.compareTo(other) > 0;
    }

    /**
     * Checks if this position is before another position.
     *
     * @param other the other position to compare with
     * @return true if this position is before the other
     */
    public boolean isBefore(CdcPosition other) {
        return this.compareTo(other) < 0;
    }
}
