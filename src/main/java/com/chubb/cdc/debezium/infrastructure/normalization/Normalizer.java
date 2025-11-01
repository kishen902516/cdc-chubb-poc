package com.chubb.cdc.debezium.infrastructure.normalization;

/**
 * Interface for field-level data normalization.
 *
 * <p>Normalizers convert database-specific data types into a consistent,
 * JSON-friendly format for publishing to Kafka.</p>
 *
 * <p>Design Pattern: Strategy (for type-specific normalization)</p>
 *
 * @param <T> the type of value this normalizer handles
 */
public interface Normalizer<T> {

    /**
     * Normalizes a value from database-specific format to a consistent format.
     *
     * @param value the value to normalize (may be null)
     * @param fieldName the name of the field being normalized (for logging/debugging)
     * @return the normalized value (may be null)
     */
    Object normalize(T value, String fieldName);

    /**
     * Checks if this normalizer can handle the given value type.
     *
     * @param value the value to check
     * @return true if this normalizer can handle the value
     */
    boolean canNormalize(Object value);
}
