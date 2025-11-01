package com.chubb.cdc.debezium.infrastructure.normalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Normalizer for numeric values.
 *
 * <p>Converts various numeric types into JSON-compatible representations
 * while preserving precision where possible.</p>
 *
 * <p>Conversion rules:</p>
 * <ul>
 *   <li>BigDecimal: Convert to double if precision allows, otherwise string</li>
 *   <li>BigInteger: Convert to long if in range, otherwise string</li>
 *   <li>Integer, Long, Short, Byte: Keep as-is</li>
 *   <li>Float, Double: Keep as-is</li>
 *   <li>String: Parse as number if possible</li>
 * </ul>
 *
 * <p>Requirement: FR-020 from spec.md</p>
 *
 * <p>Design Pattern: Strategy</p>
 */
@Component
public class NumericNormalizer implements Normalizer<Number> {

    private static final Logger logger = LoggerFactory.getLogger(NumericNormalizer.class);

    // Max safe integer in JavaScript: 2^53 - 1
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final long MIN_SAFE_INTEGER = -9007199254740991L;

    @Override
    public Object normalize(Number value, String fieldName) {
        if (value == null) {
            return null;
        }

        try {
            Object normalized = convertNumber(value);
            logger.trace("Normalized numeric field {}: {} -> {}", fieldName, value, normalized);
            return normalized;

        } catch (Exception e) {
            logger.warn("Failed to normalize numeric field {}: {}", fieldName, value, e);
            // Fall back to string representation to preserve value
            return value.toString();
        }
    }

    @Override
    public boolean canNormalize(Object value) {
        return value instanceof Number ||
               (value instanceof String && looksLikeNumber((String) value));
    }

    /**
     * Converts various numeric types to JSON-compatible format.
     */
    private Object convertNumber(Number value) {
        // BigDecimal handling
        if (value instanceof BigDecimal) {
            return convertBigDecimal((BigDecimal) value);
        }

        // BigInteger handling
        if (value instanceof BigInteger) {
            return convertBigInteger((BigInteger) value);
        }

        // Integer types - keep as-is
        if (value instanceof Integer || value instanceof Long ||
            value instanceof Short || value instanceof Byte) {
            return value;
        }

        // Floating point types - keep as-is
        if (value instanceof Float || value instanceof Double) {
            Double d = value.doubleValue();
            // Check for special values
            if (d.isNaN() || d.isInfinite()) {
                return d.toString();
            }
            return d;
        }

        // Unknown numeric type - convert to double
        return value.doubleValue();
    }

    /**
     * Converts BigDecimal to appropriate type.
     *
     * Strategy:
     * 1. If no fractional part and fits in long -> long
     * 2. If fits in double without precision loss -> double
     * 3. Otherwise -> string (to preserve precision)
     */
    private Object convertBigDecimal(BigDecimal value) {
        // Check if it's an integer value
        if (value.scale() <= 0 || value.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
            try {
                long longValue = value.longValueExact();
                // Check if it's within safe integer range for JSON
                if (longValue >= MIN_SAFE_INTEGER && longValue <= MAX_SAFE_INTEGER) {
                    return longValue;
                }
            } catch (ArithmeticException e) {
                // Doesn't fit in long, fall through to string
            }
        }

        // Try to convert to double without losing precision
        double doubleValue = value.doubleValue();
        BigDecimal reconstructed = BigDecimal.valueOf(doubleValue);

        // Check if conversion is lossless
        if (value.compareTo(reconstructed) == 0) {
            return doubleValue;
        }

        // Preserve precision by using string
        logger.debug("Converting BigDecimal to string to preserve precision: {}", value);
        return value.toPlainString();
    }

    /**
     * Converts BigInteger to appropriate type.
     *
     * Strategy:
     * 1. If fits in long -> long
     * 2. Otherwise -> string
     */
    private Object convertBigInteger(BigInteger value) {
        try {
            long longValue = value.longValueExact();
            // Check if it's within safe integer range for JSON
            if (longValue >= MIN_SAFE_INTEGER && longValue <= MAX_SAFE_INTEGER) {
                return longValue;
            }
        } catch (ArithmeticException e) {
            // Doesn't fit in long, fall through to string
        }

        // Preserve precision by using string
        logger.debug("Converting BigInteger to string to preserve precision: {}", value);
        return value.toString();
    }

    /**
     * Heuristic to detect if a string looks like a number.
     */
    private boolean looksLikeNumber(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Try to parse as number
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
