package com.chubb.cdc.debezium.contract.persistence;

import com.chubb.cdc.debezium.domain.configuration.model.*;
import com.chubb.cdc.debezium.domain.configuration.model.SslConfig.SslMode;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository.ConfigurationLoadException;
import com.chubb.cdc.debezium.infrastructure.configuration.FileConfigurationLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract test for ConfigurationRepository implementations.
 *
 * This test ensures that any implementation of ConfigurationRepository
 * (file-based YAML, database, config server, etc.) adheres to the expected contract.
 *
 * TDD: This test is written FIRST and should FAIL until implementation exists.
 */
@DisplayName("ConfigurationRepository Contract Test")
class ConfigurationRepositoryContractTest {

    @TempDir
    Path tempDir;

    private Path configFilePath;
    private ConfigurationRepository configurationRepository;

    @BeforeEach
    void setUp() throws IOException {
        configFilePath = tempDir.resolve("cdc-config.yml");

        // Create a valid configuration file for testing
        String validConfig = """
            database:
              type: POSTGRESQL
              host: localhost
              port: 5432
              database: testdb
              username: testuser
              password: testpass

            tables:
              - name: public.orders
                includeMode: INCLUDE_ALL
              - name: public.customers
                includeMode: INCLUDE_ALL

            kafka:
              brokers:
                - localhost:9092
              topicPattern: "cdc.{database}.{table}"
            """;

        Files.writeString(configFilePath, validConfig);

        // Create the actual FileConfigurationLoader implementation
        configurationRepository = new FileConfigurationLoader(configFilePath.toString());
    }

    @Test
    @DisplayName("Should load valid configuration from YAML file")
    void shouldLoadValidConfiguration() throws ConfigurationLoadException {
        // When
        ConfigurationAggregate config = configurationRepository.load();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.getDatabaseConfig().getType()).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(config.getDatabaseConfig().getHost()).isEqualTo("localhost");
        assertThat(config.getDatabaseConfig().getPort()).isEqualTo(5432);
        assertThat(config.getDatabaseConfig().getDatabase()).isEqualTo("testdb");
        assertThat(config.getTableConfigs()).hasSize(2);
        assertThat(config.getKafkaConfig().brokerAddresses()).containsExactly("localhost:9092");
    }

    @Test
    @DisplayName("Should throw exception when config file does not exist")
    void shouldThrowExceptionWhenFileDoesNotExist() throws IOException {
        // Given
        Files.delete(configFilePath);

        // When/Then
        assertThatThrownBy(() -> configurationRepository.load())
            .isInstanceOf(ConfigurationLoadException.class)
            .hasMessageContaining("Configuration file not found");
    }

    @Test
    @DisplayName("Should throw exception when YAML is invalid")
    void shouldThrowExceptionWhenYamlIsInvalid() throws IOException {
        // Given
        Files.writeString(configFilePath, "invalid: yaml: content: [unclosed");

        // When/Then
        assertThatThrownBy(() -> configurationRepository.load())
            .isInstanceOf(ConfigurationLoadException.class);
    }

    @Test
    @DisplayName("Should throw exception when required fields are missing")
    void shouldThrowExceptionWhenRequiredFieldsMissing() throws IOException {
        // Given
        String invalidConfig = """
            database:
              type: POSTGRESQL
              host: localhost
            # Missing required fields: port, database, username, password
            """;
        Files.writeString(configFilePath, invalidConfig);

        // When/Then
        assertThatThrownBy(() -> configurationRepository.load())
            .isInstanceOf(ConfigurationLoadException.class);
    }

    @Test
    @DisplayName("Should return last modified timestamp of config file")
    void shouldReturnLastModifiedTimestamp() throws IOException, InterruptedException, ConfigurationLoadException {
        // Given
        Instant beforeModification = configurationRepository.lastModified();

        // Wait to ensure timestamp changes
        Thread.sleep(10);

        // Modify the file
        Files.writeString(configFilePath, Files.readString(configFilePath) + "\n# comment");

        // When
        Instant afterModification = configurationRepository.lastModified();

        // Then
        assertThat(afterModification).isAfter(beforeModification);
    }

    @Test
    @DisplayName("Should load configuration with SSL config")
    void shouldLoadConfigurationWithSslConfig() throws IOException, ConfigurationLoadException {
        // Given
        String configWithSsl = """
            database:
              type: POSTGRESQL
              host: localhost
              port: 5432
              database: testdb
              username: testuser
              password: testpass
              ssl:
                enabled: true
                mode: VERIFY_CA
                caCertPath: /path/to/ca.crt

            tables:
              - name: public.orders
                includeMode: INCLUDE_ALL

            kafka:
              brokers:
                - localhost:9092
              topicPattern: "cdc.{database}.{table}"
            """;
        Files.writeString(configFilePath, configWithSsl);

        // When
        ConfigurationAggregate config = configurationRepository.load();

        // Then
        assertThat(config.getDatabaseConfig().getSslConfig()).isNotNull();
        assertThat(config.getDatabaseConfig().getSslConfig().enabled()).isTrue();
        assertThat(config.getDatabaseConfig().getSslConfig().mode()).isEqualTo(SslMode.VERIFY_CA);
    }

    @Test
    @DisplayName("Should load configuration with composite unique keys")
    void shouldLoadConfigurationWithCompositeKeys() throws IOException, ConfigurationLoadException {
        // Given
        String configWithCompositeKey = """
            database:
              type: POSTGRESQL
              host: localhost
              port: 5432
              database: testdb
              username: testuser
              password: testpass

            tables:
              - name: public.legacy_table
                includeMode: INCLUDE_ALL
                compositeKey:
                  columns:
                    - email
                    - registration_date

            kafka:
              brokers:
                - localhost:9092
              topicPattern: "cdc.{database}.{table}"
            """;
        Files.writeString(configFilePath, configWithCompositeKey);

        // When
        ConfigurationAggregate config = configurationRepository.load();

        // Then
        assertThat(config.getTableConfigs()).hasSize(1);
        TableConfig tableConfig = config.getTableConfigs().iterator().next();
        assertThat(tableConfig.compositeKey()).isPresent();
        assertThat(tableConfig.compositeKey().get().columnNames())
            .containsExactly("email", "registration_date");
    }

    @Test
    @DisplayName("Should validate configuration on load")
    void shouldValidateConfigurationOnLoad() throws IOException {
        // Given - Invalid Kafka config (empty broker list)
        String invalidConfig = """
            database:
              type: POSTGRESQL
              host: localhost
              port: 5432
              database: testdb
              username: testuser
              password: testpass

            tables:
              - name: public.orders
                includeMode: INCLUDE_ALL

            kafka:
              brokers: []
              topicPattern: "cdc.{database}.{table}"
            """;
        Files.writeString(configFilePath, invalidConfig);

        // When/Then
        assertThatThrownBy(() -> configurationRepository.load())
            .isInstanceOf(ConfigurationLoadException.class)
            .hasMessageContaining("broker");
    }
}
