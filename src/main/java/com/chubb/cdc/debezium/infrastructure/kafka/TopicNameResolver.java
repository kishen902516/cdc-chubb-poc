package com.chubb.cdc.debezium.infrastructure.kafka;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Resolves Kafka topic names from table identifiers using a configurable pattern.
 *
 * <p>Topic name patterns support placeholders:</p>
 * <ul>
 *   <li>{database} - Database name</li>
 *   <li>{schema} - Schema name (may be empty for some databases)</li>
 *   <li>{table} - Table name</li>
 * </ul>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>Pattern: "cdc.{database}.{table}" + table "mydb.public.orders" -> "cdc.mydb.orders"</li>
 *   <li>Pattern: "{database}_{schema}_{table}" + table "mydb.public.orders" -> "mydb_public_orders"</li>
 *   <li>Pattern: "cdc.{database}.{schema}.{table}" + table "mydb.public.orders" -> "cdc.mydb.public.orders"</li>
 * </ul>
 *
 * <p>Topic names are validated to ensure they comply with Kafka naming rules:
 * alphanumeric, '.', '_', and '-' characters only, max length 249.</p>
 *
 * <p>Design Pattern: Service (utility component)</p>
 */
@Component
public class TopicNameResolver {

    private static final Logger logger = LoggerFactory.getLogger(TopicNameResolver.class);

    private static final String DEFAULT_PATTERN = "cdc.{database}.{table}";
    private static final int MAX_TOPIC_NAME_LENGTH = 249;

    // Kafka topic name validation pattern (alphanumeric, '.', '_', '-')
    private static final Pattern VALID_TOPIC_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private final String topicPattern;

    /**
     * Creates a TopicNameResolver with the specified pattern.
     *
     * @param topicPattern the topic naming pattern (default: "cdc.{database}.{table}")
     */
    public TopicNameResolver(
        @Value("${cdc.kafka.topic-pattern:" + DEFAULT_PATTERN + "}") String topicPattern
    ) {
        this.topicPattern = topicPattern != null && !topicPattern.isBlank()
            ? topicPattern
            : DEFAULT_PATTERN;
        logger.info("TopicNameResolver initialized with pattern: {}", this.topicPattern);
    }

    /**
     * Resolves the topic name for a table.
     *
     * @param tableIdentifier the table identifier
     * @return the resolved topic name
     * @throws InvalidTopicNameException if the resolved topic name is invalid
     */
    public String resolve(TableIdentifier tableIdentifier) throws InvalidTopicNameException {
        if (tableIdentifier == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }

        String topicName = applyPattern(tableIdentifier);
        validateTopicName(topicName);

        logger.debug("Resolved topic name for table {}: {}", tableIdentifier.fullyQualifiedName(), topicName);
        return topicName;
    }

    /**
     * Resolves topic name for a table, using a custom pattern.
     *
     * @param tableIdentifier the table identifier
     * @param customPattern custom topic pattern (overrides default)
     * @return the resolved topic name
     * @throws InvalidTopicNameException if the resolved topic name is invalid
     */
    public String resolve(TableIdentifier tableIdentifier, String customPattern)
        throws InvalidTopicNameException {

        if (tableIdentifier == null) {
            throw new IllegalArgumentException("Table identifier must not be null");
        }

        if (customPattern == null || customPattern.isBlank()) {
            return resolve(tableIdentifier);
        }

        String topicName = applyPattern(tableIdentifier, customPattern);
        validateTopicName(topicName);

        logger.debug("Resolved topic name for table {} with custom pattern: {}",
            tableIdentifier.fullyQualifiedName(), topicName);
        return topicName;
    }

    /**
     * Applies the topic pattern to generate a topic name.
     */
    private String applyPattern(TableIdentifier tableIdentifier) {
        return applyPattern(tableIdentifier, topicPattern);
    }

    /**
     * Applies a specific pattern to generate a topic name.
     */
    private String applyPattern(TableIdentifier tableIdentifier, String pattern) {
        String result = pattern;

        // Replace {database} placeholder
        String database = tableIdentifier.database() != null
            ? tableIdentifier.database()
            : "unknown";
        result = result.replace("{database}", sanitizeForTopic(database));

        // Replace {schema} placeholder
        if (result.contains("{schema}")) {
            String schema = tableIdentifier.schema() != null
                ? tableIdentifier.schema()
                : "public";
            result = result.replace("{schema}", sanitizeForTopic(schema));
        }

        // Replace {table} placeholder
        String table = tableIdentifier.table() != null
            ? tableIdentifier.table()
            : "unknown";
        result = result.replace("{table}", sanitizeForTopic(table));

        return result;
    }

    /**
     * Sanitizes a string to be safe for Kafka topic names.
     *
     * Replaces invalid characters with underscores.
     */
    private String sanitizeForTopic(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        // Replace spaces and invalid characters with underscores
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        // Collapse multiple consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");

        return sanitized.isEmpty() ? "unknown" : sanitized;
    }

    /**
     * Validates that a topic name complies with Kafka naming rules.
     *
     * Rules:
     * - Must not be empty
     * - Must be <= 249 characters
     * - Must contain only alphanumeric, '.', '_', and '-' characters
     * - Must not be "." or ".."
     */
    private void validateTopicName(String topicName) throws InvalidTopicNameException {
        if (topicName == null || topicName.isBlank()) {
            throw new InvalidTopicNameException("Topic name must not be empty");
        }

        if (topicName.equals(".") || topicName.equals("..")) {
            throw new InvalidTopicNameException("Topic name cannot be '.' or '..'");
        }

        if (topicName.length() > MAX_TOPIC_NAME_LENGTH) {
            throw new InvalidTopicNameException(
                String.format("Topic name exceeds maximum length of %d: %s",
                    MAX_TOPIC_NAME_LENGTH, topicName)
            );
        }

        if (!VALID_TOPIC_PATTERN.matcher(topicName).matches()) {
            throw new InvalidTopicNameException(
                String.format("Topic name contains invalid characters: %s " +
                    "(only alphanumeric, '.', '_', '-' allowed)", topicName)
            );
        }
    }

    /**
     * Gets the configured topic pattern.
     *
     * @return the topic pattern
     */
    public String getTopicPattern() {
        return topicPattern;
    }

    /**
     * Exception thrown when a topic name is invalid.
     */
    public static class InvalidTopicNameException extends Exception {
        public InvalidTopicNameException(String message) {
            super(message);
        }
    }
}
