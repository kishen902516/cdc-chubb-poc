package com.chubb.cdc.debezium.infrastructure.configuration;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.configuration.event.ConfigurationChangedEvent;
import com.chubb.cdc.debezium.domain.configuration.model.*;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * File-based configuration loader implementing the ConfigurationRepository port.
 *
 * <p>Loads CDC configuration from YAML files. The configuration file path is determined by:</p>
 * <ol>
 *   <li>The CDC_CONFIG_PATH environment variable</li>
 *   <li>The spring property cdc.config.path</li>
 *   <li>Default: config/cdc-config.yml</li>
 * </ol>
 *
 * <p>This adapter converts YAML structure to domain ConfigurationAggregate and validates
 * all configuration parameters.</p>
 *
 * <p>Design Pattern: Adapter (infrastructure implementing domain port)</p>
 */
@Repository
public class FileConfigurationLoader implements ConfigurationRepository {

    private static final Logger logger = LoggerFactory.getLogger(FileConfigurationLoader.class);

    private static final String DEFAULT_CONFIG_PATH = "config/cdc-config.yml";
    private static final Pattern HOST_PORT_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+:\\d+$");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+$|^[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+$|^[a-zA-Z0-9_]+$");

    private final String configPath;
    private final ObjectMapper yamlMapper;
    private final List<Consumer<ConfigurationChangedEvent>> listeners;

    /**
     * Creates a FileConfigurationLoader with the specified configuration path.
     *
     * @param configPath path to the configuration file
     */
    public FileConfigurationLoader(
        @Value("${cdc.config.path:#{environment.CDC_CONFIG_PATH ?: 'config/cdc-config.yml'}}") String configPath
    ) {
        this.configPath = resolveConfigPath(configPath);
        this.yamlMapper = createYamlMapper();
        this.listeners = new ArrayList<>();
        logger.info("FileConfigurationLoader initialized with path: {}", this.configPath);
    }

    @Override
    public ConfigurationAggregate load() throws ConfigurationLoadException {
        logger.info("Loading configuration from: {}", configPath);

        Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            throw new ConfigurationLoadException("Configuration file not found: " + configPath);
        }

        try {
            // Parse YAML to intermediate structure
            Map<String, Object> yamlData = yamlMapper.readValue(path.toFile(), Map.class);

            // Convert to domain model
            ConfigurationAggregate config = buildConfigurationAggregate(yamlData);

            // Validate
            validateConfiguration(config);

            logger.info("Configuration loaded successfully from: {}", configPath);
            return config;

        } catch (IOException e) {
            String errorMsg = "Failed to parse configuration file: " + configPath;
            logger.error(errorMsg, e);
            throw new ConfigurationLoadException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = "Failed to load configuration: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new ConfigurationLoadException(errorMsg, e);
        }
    }

    @Override
    public Instant lastModified() throws ConfigurationLoadException {
        Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            throw new ConfigurationLoadException("Configuration file not found: " + configPath);
        }

        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            String errorMsg = "Failed to get last modified time for: " + configPath;
            logger.error(errorMsg, e);
            throw new ConfigurationLoadException(errorMsg, e);
        }
    }

    @Override
    public void watch(Consumer<ConfigurationChangedEvent> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        listeners.add(listener);
        logger.debug("Configuration change listener registered");
    }

    /**
     * Builds ConfigurationAggregate from parsed YAML data.
     */
    @SuppressWarnings("unchecked")
    private ConfigurationAggregate buildConfigurationAggregate(Map<String, Object> yamlData)
        throws ConfigurationLoadException {

        // Parse database configuration
        Map<String, Object> dbConfig = (Map<String, Object>) yamlData.get("database");
        if (dbConfig == null) {
            throw new ConfigurationLoadException("Missing 'database' section in configuration");
        }
        SourceDatabaseConfig databaseConfig = parseDatabaseConfig(dbConfig);

        // Parse table configurations
        List<Map<String, Object>> tablesConfig = (List<Map<String, Object>>) yamlData.get("tables");
        if (tablesConfig == null || tablesConfig.isEmpty()) {
            throw new ConfigurationLoadException("Missing 'tables' section in configuration");
        }
        Set<TableConfig> tableConfigs = parseTableConfigs(tablesConfig, databaseConfig.getType(), databaseConfig.getDatabase());

        // Parse Kafka configuration
        Map<String, Object> kafkaConfigMap = (Map<String, Object>) yamlData.get("kafka");
        if (kafkaConfigMap == null) {
            throw new ConfigurationLoadException("Missing 'kafka' section in configuration");
        }
        KafkaConfig kafkaConfig = parseKafkaConfig(kafkaConfigMap);

        return new ConfigurationAggregate(
            databaseConfig,
            tableConfigs,
            kafkaConfig,
            Instant.now()
        );
    }

    /**
     * Parses database configuration from YAML map.
     */
    @SuppressWarnings("unchecked")
    private SourceDatabaseConfig parseDatabaseConfig(Map<String, Object> dbConfig)
        throws ConfigurationLoadException {

        try {
            String typeStr = (String) dbConfig.get("type");
            if (typeStr == null) {
                throw new ConfigurationLoadException("Database type is required");
            }
            DatabaseType type = DatabaseType.valueOf(typeStr.toUpperCase());

            String host = (String) dbConfig.get("host");
            if (host == null || host.isBlank()) {
                throw new ConfigurationLoadException("Database host is required");
            }

            Integer port = (Integer) dbConfig.get("port");
            if (port == null) {
                throw new ConfigurationLoadException("Database port is required");
            }

            String database = (String) dbConfig.get("database");
            if (database == null || database.isBlank()) {
                throw new ConfigurationLoadException("Database name is required");
            }

            String username = (String) dbConfig.get("username");
            if (username == null || username.isBlank()) {
                throw new ConfigurationLoadException("Database username is required");
            }

            String password = resolveValue((String) dbConfig.get("password"));
            if (password == null || password.isBlank()) {
                throw new ConfigurationLoadException("Database password is required");
            }

            // Parse SSL config if present
            SslConfig sslConfig = null;
            Map<String, Object> sslConfigMap = (Map<String, Object>) dbConfig.get("ssl");
            if (sslConfigMap != null) {
                sslConfig = parseSslConfig(sslConfigMap);
            }

            // Parse additional properties
            Map<String, String> additionalProps = new HashMap<>();
            Map<String, Object> addlPropsMap = (Map<String, Object>) dbConfig.get("additionalProperties");
            if (addlPropsMap != null) {
                addlPropsMap.forEach((k, v) -> additionalProps.put(k, String.valueOf(v)));
            }

            return new SourceDatabaseConfig(
                type,
                host,
                port,
                database,
                username,
                password,
                sslConfig,
                additionalProps
            );

        } catch (IllegalArgumentException e) {
            throw new ConfigurationLoadException("Invalid database type: " + e.getMessage(), e);
        }
    }

    /**
     * Parses SSL configuration from YAML map.
     */
    private SslConfig parseSslConfig(Map<String, Object> sslConfigMap) {
        Boolean enabled = (Boolean) sslConfigMap.get("enabled");
        if (enabled == null) {
            enabled = false;
        }

        if (!enabled) {
            return new SslConfig(false, null, Optional.empty(), Optional.empty(), Optional.empty());
        }

        String mode = (String) sslConfigMap.get("mode");
        SslConfig.SslMode sslMode = mode != null
            ? SslConfig.SslMode.valueOf(mode.toUpperCase())
            : SslConfig.SslMode.REQUIRE;

        String caCertPath = (String) sslConfigMap.get("caCertPath");
        String clientCertPath = (String) sslConfigMap.get("clientCertPath");
        String clientKeyPath = (String) sslConfigMap.get("clientKeyPath");

        return new SslConfig(
            true,
            sslMode,
            caCertPath != null ? Optional.of(caCertPath) : Optional.empty(),
            clientCertPath != null ? Optional.of(clientCertPath) : Optional.empty(),
            clientKeyPath != null ? Optional.of(clientKeyPath) : Optional.empty()
        );
    }

    /**
     * Parses table configurations from YAML list.
     */
    @SuppressWarnings("unchecked")
    private Set<TableConfig> parseTableConfigs(List<Map<String, Object>> tablesConfig, DatabaseType dbType, String databaseName)
        throws ConfigurationLoadException {

        Set<TableConfig> configs = new HashSet<>();

        for (Map<String, Object> tableConfig : tablesConfig) {
            String tableName = (String) tableConfig.get("name");
            if (tableName == null || tableName.isBlank()) {
                throw new ConfigurationLoadException("Table name is required");
            }

            // Parse table identifier based on database type
            TableIdentifier tableId = parseTableIdentifier(tableName, dbType, databaseName);

            // Parse include mode
            String includeModeStr = (String) tableConfig.get("includeMode");
            TableConfig.IncludeMode includeMode = includeModeStr != null
                ? TableConfig.IncludeMode.valueOf(includeModeStr.toUpperCase())
                : TableConfig.IncludeMode.INCLUDE_ALL;

            // Parse column filter
            List<String> columnFilterList = (List<String>) tableConfig.get("columnFilter");
            Set<String> columnFilter = columnFilterList != null
                ? new HashSet<>(columnFilterList)
                : Set.of();

            // Parse composite key if present
            Optional<CompositeUniqueKey> compositeKey = Optional.empty();
            Map<String, Object> compositeKeyMap = (Map<String, Object>) tableConfig.get("compositeKey");
            if (compositeKeyMap != null) {
                List<String> columns = (List<String>) compositeKeyMap.get("columnNames");
                if (columns != null && !columns.isEmpty()) {
                    compositeKey = Optional.of(new CompositeUniqueKey(columns));
                }
            }

            configs.add(new TableConfig(tableId, includeMode, columnFilter, compositeKey));
        }

        return configs;
    }

    /**
     * Parses table identifier from table name string.
     */
    private TableIdentifier parseTableIdentifier(String tableName, DatabaseType dbType, String databaseName) {
        String[] parts = tableName.split("\\.");

        if (dbType == DatabaseType.MYSQL) {
            // MySQL doesn't use schemas in the same way
            if (parts.length == 1) {
                return new TableIdentifier(databaseName, null, parts[0]);
            } else if (parts.length == 2) {
                // database.table format - use first part as database override
                return new TableIdentifier(parts[0], null, parts[1]);
            }
        } else {
            // PostgreSQL, SQL Server, Oracle use schema.table format
            if (parts.length == 2) {
                // schema.table
                return new TableIdentifier(databaseName, parts[0], parts[1]);
            } else if (parts.length == 1) {
                // Just table name, use default schema
                return new TableIdentifier(databaseName, "public", parts[0]);
            }
        }

        throw new IllegalArgumentException("Invalid table name format: " + tableName);
    }

    /**
     * Parses Kafka configuration from YAML map.
     */
    @SuppressWarnings("unchecked")
    private KafkaConfig parseKafkaConfig(Map<String, Object> kafkaConfigMap)
        throws ConfigurationLoadException {

        List<String> brokers = (List<String>) kafkaConfigMap.get("brokers");
        if (brokers == null || brokers.isEmpty()) {
            throw new ConfigurationLoadException("Kafka brokers list is required");
        }

        String topicPattern = (String) kafkaConfigMap.get("topicPattern");
        if (topicPattern == null || topicPattern.isBlank()) {
            throw new ConfigurationLoadException("Kafka topic pattern is required");
        }

        // Parse security config if present
        KafkaConfig.KafkaSecurity security = null;
        Map<String, Object> securityMap = (Map<String, Object>) kafkaConfigMap.get("security");
        if (securityMap != null) {
            security = parseKafkaSecurity(securityMap);
        }

        // Parse producer properties
        Map<String, Object> producerProps = (Map<String, Object>) kafkaConfigMap.get("producerProperties");
        if (producerProps == null) {
            producerProps = Map.of();
        }

        return new KafkaConfig(brokers, topicPattern, security, producerProps);
    }

    /**
     * Parses Kafka security configuration.
     */
    @SuppressWarnings("unchecked")
    private KafkaConfig.KafkaSecurity parseKafkaSecurity(Map<String, Object> securityMap) {
        String protocolStr = (String) securityMap.get("protocol");
        KafkaConfig.KafkaSecurity.SecurityProtocol protocol = protocolStr != null
            ? KafkaConfig.KafkaSecurity.SecurityProtocol.valueOf(protocolStr.toUpperCase())
            : null;

        String mechanismStr = (String) securityMap.get("mechanism");
        KafkaConfig.KafkaSecurity.SaslMechanism mechanism = mechanismStr != null
            ? KafkaConfig.KafkaSecurity.SaslMechanism.valueOf(mechanismStr.toUpperCase().replace("-", "_"))
            : null;

        String username = (String) securityMap.get("username");
        String password = resolveValue((String) securityMap.get("password"));

        KafkaConfig.KafkaSecurity.SslTruststore truststore = null;
        Map<String, Object> truststoreMap = (Map<String, Object>) securityMap.get("truststore");
        if (truststoreMap != null) {
            String path = (String) truststoreMap.get("path");
            String tsPassword = resolveValue((String) truststoreMap.get("password"));
            if (path != null) {
                truststore = new KafkaConfig.KafkaSecurity.SslTruststore(path, tsPassword);
            }
        }

        return new KafkaConfig.KafkaSecurity(protocol, mechanism, username, password, truststore);
    }

    /**
     * Validates the loaded configuration.
     */
    private void validateConfiguration(ConfigurationAggregate config) throws ConfigurationLoadException {
        try {
            // Validate database connection parameters
            SourceDatabaseConfig dbConfig = config.getDatabaseConfig();
            validateDatabaseConfig(dbConfig);

            // Validate Kafka brokers
            KafkaConfig kafkaConfig = config.getKafkaConfig();
            validateKafkaBrokers(kafkaConfig.brokerAddresses());

            // Validate table names
            for (TableConfig tableConfig : config.getTableConfigs()) {
                validateTableName(tableConfig.table());
            }

            // Validate SSL certificate paths if SSL enabled
            if (dbConfig.getSslConfig() != null && dbConfig.getSslConfig().enabled()) {
                validateSslCertificates(dbConfig.getSslConfig());
            }

            // Validate topic pattern
            validateTopicPattern(kafkaConfig.topicNamePattern());

            // Call domain validation
            config.validate();

        } catch (ConfigurationAggregate.InvalidConfigurationException e) {
            throw new ConfigurationLoadException("Configuration validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates database connection parameters.
     */
    private void validateDatabaseConfig(SourceDatabaseConfig config) throws ConfigurationLoadException {
        // Host validation
        if (config.getHost().contains(";") || config.getHost().contains("'")) {
            throw new ConfigurationLoadException("Invalid characters in database host");
        }

        // Port validation is already done in SourceDatabaseConfig constructor

        // Database name validation
        if (config.getDatabase().contains(";") || config.getDatabase().contains("'")) {
            throw new ConfigurationLoadException("Invalid characters in database name");
        }
    }

    /**
     * Validates Kafka broker addresses.
     */
    private void validateKafkaBrokers(List<String> brokers) throws ConfigurationLoadException {
        for (String broker : brokers) {
            if (!HOST_PORT_PATTERN.matcher(broker).matches()) {
                throw new ConfigurationLoadException("Invalid Kafka broker format: " + broker + " (expected host:port)");
            }
        }
    }

    /**
     * Validates table name format.
     */
    private void validateTableName(TableIdentifier table) throws ConfigurationLoadException {
        String fullName = table.fullyQualifiedName();
        if (!TABLE_NAME_PATTERN.matcher(fullName).matches()) {
            throw new ConfigurationLoadException("Invalid table name format: " + fullName);
        }
    }

    /**
     * Validates SSL certificate paths exist.
     */
    private void validateSslCertificates(SslConfig sslConfig) throws ConfigurationLoadException {
        if (sslConfig.caCertPath().isPresent()) {
            String certPath = sslConfig.caCertPath().get();
            if (!Files.exists(Paths.get(certPath))) {
                throw new ConfigurationLoadException("CA certificate file not found: " + certPath);
            }
        }

        if (sslConfig.clientCertPath().isPresent()) {
            String certPath = sslConfig.clientCertPath().get();
            if (!Files.exists(Paths.get(certPath))) {
                throw new ConfigurationLoadException("Client certificate file not found: " + certPath);
            }
        }

        if (sslConfig.clientKeyPath().isPresent()) {
            String keyPath = sslConfig.clientKeyPath().get();
            if (!Files.exists(Paths.get(keyPath))) {
                throw new ConfigurationLoadException("Client key file not found: " + keyPath);
            }
        }
    }

    /**
     * Validates topic pattern contains required placeholders.
     */
    private void validateTopicPattern(String pattern) throws ConfigurationLoadException {
        if (!pattern.contains("{database}") || !pattern.contains("{table}")) {
            throw new ConfigurationLoadException(
                "Topic pattern must contain {database} and {table} placeholders: " + pattern
            );
        }
    }

    /**
     * Resolves environment variable references in configuration values.
     * Format: ${VAR_NAME} or literal value
     */
    private String resolveValue(String value) {
        if (value == null) {
            return null;
        }

        if (value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            String resolved = System.getenv(varName);
            if (resolved == null) {
                logger.warn("Environment variable {} not found, using empty string", varName);
                return "";
            }
            return resolved;
        }

        return value;
    }

    /**
     * Resolves the actual configuration file path.
     */
    private String resolveConfigPath(String configPath) {
        if (configPath == null || configPath.isBlank()) {
            return DEFAULT_CONFIG_PATH;
        }

        // Check if it's an environment variable reference
        String resolved = resolveValue(configPath);
        return resolved != null && !resolved.isBlank() ? resolved : DEFAULT_CONFIG_PATH;
    }

    /**
     * Creates and configures the YAML ObjectMapper.
     */
    private ObjectMapper createYamlMapper() {
        return new ObjectMapper(new YAMLFactory());
    }
}
