package com.chubb.cdc.debezium.presentation.cli;

import com.chubb.cdc.debezium.application.usecase.configuration.LoadConfigurationUseCase;
import com.chubb.cdc.debezium.application.usecase.configuration.ValidateConfigurationUseCase;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLI commands for configuration operations.
 * <p>
 * Supports:
 * - config validate [--file=path]: Validate configuration file
 * - config show: Display current configuration
 * <p>
 * Usage:
 * - java -jar cdc-app.jar config validate
 * - java -jar cdc-app.jar config validate --file=/path/to/config.yml
 * - java -jar cdc-app.jar config show
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ConfigCommand implements ApplicationRunner {

    private final LoadConfigurationUseCase loadConfigurationUseCase;
    private final ValidateConfigurationUseCase validateConfigurationUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Check if 'config' command is specified
        List<String> nonOptionArgs = args.getNonOptionArgs();

        if (nonOptionArgs.isEmpty() || !"config".equals(nonOptionArgs.get(0))) {
            return; // Not a config command
        }

        if (nonOptionArgs.size() < 2) {
            printHelp();
            System.exit(1);
        }

        String subCommand = nonOptionArgs.get(1).toLowerCase();

        try {
            switch (subCommand) {
                case "validate":
                    String filePath = args.containsOption("file")
                            ? args.getOptionValues("file").get(0)
                            : null;
                    handleValidate(filePath);
                    System.exit(0);
                    break;

                case "show":
                    handleShow();
                    System.exit(0);
                    break;

                default:
                    System.err.println("Unknown config sub-command: " + subCommand);
                    printHelp();
                    System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Error executing config command: " + e.getMessage());
            log.error("Config CLI command failed", e);
            System.exit(1);
        }
    }

    /**
     * Handle 'config validate' command.
     *
     * @param filePath Optional path to configuration file
     */
    private void handleValidate(String filePath) {
        System.out.println("Validating CDC configuration" +
                (filePath != null ? " from: " + filePath : "..."));
        System.out.println();

        try {
            // Load configuration
            ConfigurationAggregate config = filePath != null
                    ? loadConfigurationUseCase.executeFromPath(filePath)
                    : loadConfigurationUseCase.execute();

            System.out.println("✓ Configuration loaded successfully");

            // Validate configuration
            ValidateConfigurationUseCase.ValidationResult result =
                    validateConfigurationUseCase.execute(config);

            // Display validation results
            if (result.isValid()) {
                System.out.println("✓ Configuration validation PASSED");
                System.out.println();
                System.out.println("Configuration Summary:");
                System.out.println("  Database Type: " + config.getDatabaseConfig().getType());
                System.out.println("  Database Host: " + config.getDatabaseConfig().getHost() + ":"
                        + config.getDatabaseConfig().getPort());
                System.out.println("  Database Name: " + config.getDatabaseConfig().getDatabase());
                System.out.println("  Monitored Tables: " + config.getTableConfigs().size());
                System.out.println("  Kafka Brokers: " + config.getKafkaConfig().brokerAddresses().size());

                if (!result.warnings().isEmpty()) {
                    System.out.println();
                    System.out.println("Warnings:");
                    result.warnings().forEach(warning ->
                            System.out.println("  ⚠ " + warning)
                    );
                }

            } else {
                System.err.println("✗ Configuration validation FAILED");
                System.err.println();
                System.err.println("Errors:");
                result.errors().forEach(error ->
                        System.err.println("  ✗ " + error)
                );

                if (!result.warnings().isEmpty()) {
                    System.err.println();
                    System.err.println("Warnings:");
                    result.warnings().forEach(warning ->
                            System.err.println("  ⚠ " + warning)
                    );
                }

                throw new RuntimeException("Configuration validation failed");
            }

        } catch (LoadConfigurationUseCase.ConfigurationLoadException e) {
            System.err.println("✗ Failed to load configuration: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Handle 'config show' command.
     */
    private void handleShow() {
        System.out.println("Current CDC Configuration:");
        System.out.println();

        try {
            // Load configuration
            ConfigurationAggregate config = loadConfigurationUseCase.execute();

            // Display database configuration
            System.out.println("Database Configuration:");
            System.out.println("  Type: " + config.getDatabaseConfig().getType());
            System.out.println("  Host: " + config.getDatabaseConfig().getHost());
            System.out.println("  Port: " + config.getDatabaseConfig().getPort());
            System.out.println("  Database: " + config.getDatabaseConfig().getDatabase());
            System.out.println("  Username: " + config.getDatabaseConfig().getUsername());
            System.out.println("  SSL Enabled: " +
                    (config.getDatabaseConfig().getSslConfig() != null &&
                            config.getDatabaseConfig().getSslConfig().enabled() ? "Yes" : "No"));
            System.out.println();

            // Display monitored tables
            System.out.println("Monitored Tables (" + config.getTableConfigs().size() + "):");
            for (TableConfig tableConfig : config.getTableConfigs()) {
                System.out.println("  - " + tableConfig.table().fullyQualifiedName());
                System.out.println("    Include Mode: " + tableConfig.includeMode());

                if (tableConfig.columnFilter() != null && !tableConfig.columnFilter().isEmpty()) {
                    System.out.println("    Column Filter: " +
                            String.join(", ", tableConfig.columnFilter()));
                }

                if (tableConfig.compositeKey().isPresent()) {
                    System.out.println("    Composite Key: " +
                            String.join(", ", tableConfig.compositeKey().get().columnNames()));
                }
            }
            System.out.println();

            // Display Kafka configuration
            System.out.println("Kafka Configuration:");
            System.out.println("  Brokers: " +
                    String.join(", ", config.getKafkaConfig().brokerAddresses()));
            System.out.println("  Topic Pattern: " + config.getKafkaConfig().topicNamePattern());
            System.out.println("  Security: " +
                    (config.getKafkaConfig().security() != null ?
                            config.getKafkaConfig().security().protocol().name() : "None"));
            System.out.println();

            // Display metadata
            System.out.println("Metadata:");
            System.out.println("  Loaded At: " + config.getLoadedAt());

        } catch (Exception e) {
            System.err.println("✗ Failed to load configuration: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Print help information.
     */
    private void printHelp() {
        System.out.println("Configuration Command Help");
        System.out.println();
        System.out.println("Usage: java -jar cdc-app.jar config <sub-command> [options]");
        System.out.println();
        System.out.println("Sub-commands:");
        System.out.println("  validate           Validate configuration file");
        System.out.println("  show               Display current configuration");
        System.out.println();
        System.out.println("Options (for 'validate'):");
        System.out.println("  --file=<path>      Path to configuration file to validate");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar cdc-app.jar config validate");
        System.out.println("  java -jar cdc-app.jar config validate --file=config/cdc-config.yml");
        System.out.println("  java -jar cdc-app.jar config show");
    }
}
