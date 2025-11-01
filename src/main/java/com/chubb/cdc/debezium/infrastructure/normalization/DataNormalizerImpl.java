package com.chubb.cdc.debezium.infrastructure.normalization;

import com.chubb.cdc.debezium.application.port.output.DataNormalizer;
import com.chubb.cdc.debezium.domain.changecapture.model.RowData;
import com.chubb.cdc.debezium.domain.configuration.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of DataNormalizer that orchestrates field-level normalizers.
 *
 * <p>This service applies type-specific normalizers to convert database-specific
 * data types into a consistent, JSON-friendly format for Kafka publishing.</p>
 *
 * <p>Type detection strategy:</p>
 * <ul>
 *   <li>Timestamp types: Detected by type or field name patterns (time/date)</li>
 *   <li>Numeric types: All Number subclasses</li>
 *   <li>Text types: String, byte[], CharSequence, Clob</li>
 *   <li>Other: Boolean, null (kept as-is)</li>
 * </ul>
 *
 * <p>Design Pattern: Service (orchestrator), Strategy (delegates to type-specific normalizers)</p>
 */
@Service
public class DataNormalizerImpl implements DataNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(DataNormalizerImpl.class);

    private final TimestampNormalizer timestampNormalizer;
    private final NumericNormalizer numericNormalizer;
    private final TextNormalizer textNormalizer;

    /**
     * Creates a DataNormalizerImpl with injected normalizers.
     *
     * @param timestampNormalizer timestamp normalizer
     * @param numericNormalizer numeric normalizer
     * @param textNormalizer text normalizer
     */
    public DataNormalizerImpl(
        TimestampNormalizer timestampNormalizer,
        NumericNormalizer numericNormalizer,
        TextNormalizer textNormalizer
    ) {
        this.timestampNormalizer = timestampNormalizer;
        this.numericNormalizer = numericNormalizer;
        this.textNormalizer = textNormalizer;
        logger.info("DataNormalizerImpl initialized with {} normalizers", 3);
    }

    @Override
    public RowData normalize(Map<String, Object> rawData, DatabaseType databaseType)
        throws DataNormalizationException {

        if (rawData == null) {
            return new RowData(Map.of());
        }

        try {
            Map<String, Object> normalizedFields = new HashMap<>();

            for (Map.Entry<String, Object> entry : rawData.entrySet()) {
                String fieldName = entry.getKey();
                Object rawValue = entry.getValue();

                Object normalizedValue = normalizeField(fieldName, rawValue, databaseType);
                normalizedFields.put(fieldName, normalizedValue);
            }

            logger.trace("Normalized {} fields for database type {}", normalizedFields.size(), databaseType);
            return new RowData(normalizedFields);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to normalize row data for database type %s", databaseType);
            logger.error(errorMsg, e);
            throw new DataNormalizationException(errorMsg, e);
        }
    }

    /**
     * Normalizes a single field value.
     */
    private Object normalizeField(String fieldName, Object rawValue, DatabaseType databaseType) {
        if (rawValue == null) {
            return null;
        }

        // Try timestamp normalizer first (including field name heuristics)
        if (timestampNormalizer.canNormalize(rawValue) || isTimestampField(fieldName)) {
            try {
                return timestampNormalizer.normalize(rawValue, fieldName);
            } catch (Exception e) {
                logger.debug("Timestamp normalization failed for field {}, trying other normalizers", fieldName);
            }
        }

        // Try numeric normalizer
        if (numericNormalizer.canNormalize(rawValue)) {
            try {
                // Cast to Number since canNormalize returned true
                return numericNormalizer.normalize((Number) rawValue, fieldName);
            } catch (Exception e) {
                logger.debug("Numeric normalization failed for field {}, trying other normalizers", fieldName);
            }
        }

        // Try text normalizer
        if (textNormalizer.canNormalize(rawValue)) {
            try {
                return textNormalizer.normalize(rawValue, fieldName);
            } catch (Exception e) {
                logger.debug("Text normalization failed for field {}, keeping as-is", fieldName);
            }
        }

        // Keep other types as-is (boolean, null, etc.)
        return rawValue;
    }

    /**
     * Checks if a field name suggests it contains timestamp data.
     *
     * Heuristic based on common naming conventions.
     */
    private boolean isTimestampField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String lowerName = fieldName.toLowerCase();
        return lowerName.contains("time") ||
               lowerName.contains("date") ||
               lowerName.contains("timestamp") ||
               lowerName.endsWith("_at") ||
               lowerName.endsWith("_on") ||
               lowerName.equals("created") ||
               lowerName.equals("updated") ||
               lowerName.equals("modified");
    }
}
