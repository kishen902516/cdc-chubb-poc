package com.chubb.cdc.debezium.infrastructure.normalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Normalizer for timestamp and date/time values.
 *
 * <p>Converts various timestamp representations into ISO-8601 format with UTC timezone.
 * Output format: "2025-11-01T10:30:00Z"</p>
 *
 * <p>Supported input types:</p>
 * <ul>
 *   <li>java.sql.Timestamp</li>
 *   <li>java.time.Instant</li>
 *   <li>java.time.ZonedDateTime</li>
 *   <li>java.time.OffsetDateTime</li>
 *   <li>java.time.LocalDateTime (assumes UTC)</li>
 *   <li>java.util.Date</li>
 *   <li>Long (epoch milliseconds)</li>
 *   <li>String (attempts to parse)</li>
 * </ul>
 *
 * <p>Requirement: FR-019 from spec.md</p>
 *
 * <p>Design Pattern: Strategy</p>
 */
@Component
public class TimestampNormalizer implements Normalizer<Object> {

    private static final Logger logger = LoggerFactory.getLogger(TimestampNormalizer.class);

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Override
    public Object normalize(Object value, String fieldName) {
        if (value == null) {
            return null;
        }

        try {
            Instant instant = convertToInstant(value);
            if (instant == null) {
                logger.warn("Unable to convert timestamp value for field {}: {}", fieldName, value);
                return value.toString();
            }

            String normalized = ISO_FORMATTER.format(instant);
            logger.trace("Normalized timestamp field {}: {} -> {}", fieldName, value, normalized);
            return normalized;

        } catch (Exception e) {
            logger.warn("Failed to normalize timestamp field {}: {}", fieldName, value, e);
            return value.toString();
        }
    }

    @Override
    public boolean canNormalize(Object value) {
        if (value == null) {
            return false;
        }

        return value instanceof Timestamp ||
               value instanceof Instant ||
               value instanceof ZonedDateTime ||
               value instanceof OffsetDateTime ||
               value instanceof LocalDateTime ||
               value instanceof LocalDate ||
               value instanceof java.util.Date ||
               value instanceof Long ||
               (value instanceof String && looksLikeTimestamp((String) value));
    }

    /**
     * Converts various timestamp types to Instant.
     */
    private Instant convertToInstant(Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toInstant();
        }

        if (value instanceof Instant) {
            return (Instant) value;
        }

        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant();
        }

        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toInstant();
        }

        if (value instanceof LocalDateTime) {
            // Assume UTC timezone
            return ((LocalDateTime) value).atZone(ZoneOffset.UTC).toInstant();
        }

        if (value instanceof LocalDate) {
            // Start of day in UTC
            return ((LocalDate) value).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant();
        }

        if (value instanceof Long) {
            // Assume epoch milliseconds
            return Instant.ofEpochMilli((Long) value);
        }

        if (value instanceof String) {
            return parseStringToInstant((String) value);
        }

        return null;
    }

    /**
     * Attempts to parse a string as a timestamp.
     */
    private Instant parseStringToInstant(String value) {
        try {
            // Try ISO-8601 format first
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            try {
                // Try as LocalDateTime with various formats
                LocalDateTime ldt = LocalDateTime.parse(value);
                return ldt.atZone(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e2) {
                try {
                    // Try parsing as epoch milliseconds
                    long epochMilli = Long.parseLong(value);
                    return Instant.ofEpochMilli(epochMilli);
                } catch (NumberFormatException e3) {
                    logger.debug("Unable to parse timestamp string: {}", value);
                    return null;
                }
            }
        }
    }

    /**
     * Heuristic to detect if a string looks like a timestamp.
     */
    private boolean looksLikeTimestamp(String value) {
        // Check for common timestamp patterns
        return value.contains("T") || // ISO-8601
               value.contains("-") && value.contains(":") || // Date-time format
               value.matches("\\d{10,13}"); // Epoch milliseconds/seconds
    }
}
