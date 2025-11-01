package com.chubb.cdc.debezium.application.port.output;

import com.chubb.cdc.debezium.domain.changecapture.model.RowData;
import com.chubb.cdc.debezium.domain.configuration.model.DatabaseType;

import java.util.Map;

/**
 * Output port for normalizing database-specific data types into a consistent format.
 *
 * <p>Different databases use different representations for timestamps, numeric types, and text.
 * This port normalizes all data into a consistent JSON-friendly format:</p>
 * <ul>
 *   <li>Timestamps: ISO-8601 format (UTC)</li>
 *   <li>Numeric types: JSON number format</li>
 *   <li>Text: UTF-8 encoding</li>
 * </ul>
 *
 * <p>Part of the Application Layer in Clean Architecture - defines the contract for
 * database-specific normalization strategies implemented in the infrastructure layer.</p>
 */
public interface DataNormalizer {

    /**
     * Normalizes raw database row data into a consistent format.
     *
     * <p>Converts database-specific types (e.g., PostgreSQL TIMESTAMP, MySQL DATETIME,
     * Oracle NUMBER) into normalized Java types that serialize cleanly to JSON.</p>
     *
     * @param rawData the raw row data from the database connector
     * @param databaseType the source database type (for type-specific normalization)
     * @return normalized row data with consistent type representations
     * @throws DataNormalizationException if normalization fails
     */
    RowData normalize(Map<String, Object> rawData, DatabaseType databaseType)
        throws DataNormalizationException;

    /**
     * Exception thrown when data normalization fails.
     */
    class DataNormalizationException extends Exception {
        public DataNormalizationException(String message) {
            super(message);
        }

        public DataNormalizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
