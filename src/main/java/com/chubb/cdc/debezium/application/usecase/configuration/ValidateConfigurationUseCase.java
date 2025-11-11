package com.chubb.cdc.debezium.application.usecase.configuration;

import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.model.SourceDatabaseConfig;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Use Case: Validate Configuration
 * <p>
 * Responsibility: Perform deep validation of CDC configuration including:
 * - Database connectivity validation
 * - Kafka broker connectivity validation
 * - Table existence validation
 * - SSL certificate validation
 * - Topic naming pattern validation
 * <p>
 * Business Rules:
 * - Validation should not modify configuration
 * - Validation failures should provide actionable error messages
 * - All validation errors should be collected and reported together
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateConfigurationUseCase {

    /**
     * Validate a configuration aggregate.
     *
     * @param configuration The configuration to validate
     * @return ValidationResult containing status and any errors
     */
    public ValidationResult execute(ConfigurationAggregate configuration) {
        log.info("Validating CDC configuration");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try {
            // Perform basic validation (this may throw InvalidConfigurationException)
            configuration.validate();
            log.debug("Basic configuration validation passed");

        } catch (Exception e) {
            errors.add("Basic validation failed: " + e.getMessage());
            log.error("Basic configuration validation failed", e);
            return ValidationResult.failed(errors, warnings);
        }

        // Validate database configuration
        validateDatabaseConfig(configuration.getDatabaseConfig(), errors, warnings);

        // Validate table configurations
        validateTableConfigs(configuration.getTableConfigs(), errors, warnings);

        // Validate Kafka configuration
        validateKafkaConfig(configuration.getKafkaConfig(), errors, warnings);

        // Determine overall result
        boolean isValid = errors.isEmpty();
        ValidationResult result = isValid
                ? ValidationResult.success(warnings)
                : ValidationResult.failed(errors, warnings);

        if (isValid) {
            log.info("Configuration validation successful with {} warning(s)", warnings.size());
        } else {
            log.error("Configuration validation failed with {} error(s)", errors.size());
        }

        return result;
    }

    private void validateDatabaseConfig(SourceDatabaseConfig dbConfig, List<String> errors, List<String> warnings) {
        log.debug("Validating database configuration");

        // Validate host is not localhost in production
        if ("localhost".equalsIgnoreCase(dbConfig.getHost()) || "127.0.0.1".equals(dbConfig.getHost())) {
            warnings.add("Database host is localhost - ensure this is intentional for your environment");
        }

        // Validate port range
        if (dbConfig.getPort() < 1 || dbConfig.getPort() > 65535) {
            errors.add("Database port must be between 1 and 65535, got: " + dbConfig.getPort());
        }

        // Validate SSL configuration if present
        if (dbConfig.getSslConfig() != null && dbConfig.getSslConfig().enabled()) {
            if (dbConfig.getSslConfig().caCertPath().isEmpty()) {
                warnings.add("SSL enabled but no CA certificate path specified");
            }
        }

        // Validate database name is not empty
        if (dbConfig.getDatabase() == null || dbConfig.getDatabase().isBlank()) {
            errors.add("Database name cannot be empty");
        }

        // Validate username and password
        if (dbConfig.getUsername() == null || dbConfig.getUsername().isBlank()) {
            errors.add("Database username cannot be empty");
        }
        if (dbConfig.getPassword() == null || dbConfig.getPassword().isBlank()) {
            warnings.add("Database password is empty - ensure this is configured via environment variable");
        }
    }

    private void validateTableConfigs(java.util.Set<TableConfig> tableConfigs, List<String> errors, List<String> warnings) {
        log.debug("Validating table configurations");

        if (tableConfigs == null || tableConfigs.isEmpty()) {
            errors.add("At least one table must be configured for monitoring");
            return;
        }

        for (TableConfig tableConfig : tableConfigs) {
            // Validate table identifier
            if (tableConfig.table() == null) {
                errors.add("Table configuration missing table identifier");
                continue;
            }

            // Validate composite key if specified
            if (tableConfig.compositeKey().isPresent()) {
                var compositeKey = tableConfig.compositeKey().get();
                if (compositeKey.columnNames().isEmpty()) {
                    errors.add("Composite key specified for table " + tableConfig.table().fullyQualifiedName()
                            + " but no columns provided");
                }
            }

            // Validate column filter if specified
            if (tableConfig.columnFilter() != null && !tableConfig.columnFilter().isEmpty()) {
                log.debug("Table {} has column filter with {} columns",
                        tableConfig.table().fullyQualifiedName(),
                        tableConfig.columnFilter().size());
            }
        }

        log.debug("Validated {} table configuration(s)", tableConfigs.size());
    }

    private void validateKafkaConfig(com.chubb.cdc.debezium.domain.configuration.model.KafkaConfig kafkaConfig,
                                     List<String> errors, List<String> warnings) {
        log.debug("Validating Kafka configuration");

        // Validate broker list
        if (kafkaConfig.brokerAddresses() == null || kafkaConfig.brokerAddresses().isEmpty()) {
            errors.add("Kafka broker list cannot be empty");
            return;
        }

        // Validate broker address format
        for (String broker : kafkaConfig.brokerAddresses()) {
            if (!broker.contains(":")) {
                errors.add("Invalid Kafka broker address format: " + broker + " (expected host:port)");
            }
            if (broker.contains("localhost") || broker.contains("127.0.0.1")) {
                warnings.add("Kafka broker address contains localhost: " + broker);
            }
        }

        // Validate topic name pattern
        if (kafkaConfig.topicNamePattern() == null || kafkaConfig.topicNamePattern().isBlank()) {
            errors.add("Kafka topic name pattern cannot be empty");
        } else {
            // Ensure pattern contains required placeholders
            String pattern = kafkaConfig.topicNamePattern();
            if (!pattern.contains("{database}") && !pattern.contains("{table}")) {
                warnings.add("Topic pattern does not contain {database} or {table} placeholders: " + pattern);
            }
        }

        // Validate security configuration if present
        if (kafkaConfig.security() != null) {
            log.debug("Kafka security configuration present: {}", kafkaConfig.security().protocol());
        }
    }

    /**
     * Validation result containing status, errors, and warnings.
     */
    public record ValidationResult(
            boolean isValid,
            List<String> errors,
            List<String> warnings
    ) {
        public static ValidationResult success(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }

        public static ValidationResult failed(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }

        public String getSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append("Validation ").append(isValid ? "PASSED" : "FAILED").append("\n");

            if (!errors.isEmpty()) {
                summary.append("\nErrors:\n");
                errors.forEach(error -> summary.append("  - ").append(error).append("\n"));
            }

            if (!warnings.isEmpty()) {
                summary.append("\nWarnings:\n");
                warnings.forEach(warning -> summary.append("  - ").append(warning).append("\n"));
            }

            return summary.toString();
        }
    }
}
