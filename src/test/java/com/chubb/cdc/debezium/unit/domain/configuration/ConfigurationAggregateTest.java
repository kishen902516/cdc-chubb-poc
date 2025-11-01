package com.chubb.cdc.debezium.unit.domain.configuration;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.configuration.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ConfigurationAggregate domain model.
 *
 * <p>Tests configuration validation, change detection, and table management.</p>
 */
@DisplayName("ConfigurationAggregate Domain Model Tests")
class ConfigurationAggregateTest {

    private SourceDatabaseConfig testDatabaseConfig;
    private KafkaConfig testKafkaConfig;
    private TableConfig table1Config;
    private TableConfig table2Config;
    private TableConfig table3Config;

    @BeforeEach
    void setUp() {
        testDatabaseConfig = new SourceDatabaseConfig(
            DatabaseType.POSTGRESQL,
            "localhost",
            5432,
            "testdb",
            "testuser",
            "testpass",
            null,
            java.util.Map.of()
        );

        testKafkaConfig = new KafkaConfig(
            List.of("localhost:9092"),
            "cdc.{database}.{table}",
            null,
            null
        );

        table1Config = new TableConfig(
            new TableIdentifier("testdb", "public", "orders"),
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of(),
            Optional.empty()
        );

        table2Config = new TableConfig(
            new TableIdentifier("testdb", "public", "customers"),
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of(),
            Optional.empty()
        );

        table3Config = new TableConfig(
            new TableIdentifier("testdb", "public", "products"),
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of(),
            Optional.empty()
        );
    }

    @Test
    @DisplayName("ConfigurationAggregate should validate successfully with valid data")
    void testValidConfiguration() throws ConfigurationAggregate.InvalidConfigurationException {
        // Given
        ConfigurationAggregate config = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config),
            testKafkaConfig,
            Instant.now()
        );

        // When / Then - should not throw exception
        assertThatCode(() -> config.validate()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ConfigurationAggregate should reject null database config")
    void testNullDatabaseConfigRejected() {
        // When / Then
        assertThatThrownBy(() ->
            new ConfigurationAggregate(
                null,
                Set.of(table1Config),
                testKafkaConfig,
                Instant.now()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Database configuration must not be null");
    }

    @Test
    @DisplayName("ConfigurationAggregate should reject empty table configs")
    void testEmptyTableConfigsRejected() {
        // When / Then
        assertThatThrownBy(() ->
            new ConfigurationAggregate(
                testDatabaseConfig,
                Set.of(),
                testKafkaConfig,
                Instant.now()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one table must be configured");
    }

    @Test
    @DisplayName("ConfigurationAggregate should reject null Kafka config")
    void testNullKafkaConfigRejected() {
        // When / Then
        assertThatThrownBy(() ->
            new ConfigurationAggregate(
                testDatabaseConfig,
                Set.of(table1Config),
                null,
                Instant.now()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Kafka configuration must not be null");
    }

    @Test
    @DisplayName("ConfigurationAggregate should detect duplicate table identifiers")
    void testDuplicateTablesDetected() {
        // Given - duplicate table configs
        TableConfig duplicateTable = new TableConfig(
            new TableIdentifier("testdb", "public", "orders"),  // Same as table1Config
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of("id", "status"),
            Optional.empty()
        );

        ConfigurationAggregate config = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, duplicateTable),
            testKafkaConfig,
            Instant.now()
        );

        // When / Then
        assertThatThrownBy(() -> config.validate())
            .isInstanceOf(ConfigurationAggregate.InvalidConfigurationException.class)
            .hasMessageContaining("Duplicate table identifiers");
    }

    @Test
    @DisplayName("hasChangedSince should return true for different configurations")
    void testHasChangedSinceDetectsChanges() {
        // Given
        ConfigurationAggregate config1 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),
            testKafkaConfig,
            Instant.now()
        );

        ConfigurationAggregate config2 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config),  // Added table2
            testKafkaConfig,
            Instant.now()
        );

        // When / Then
        assertThat(config2.hasChangedSince(config1)).isTrue();
    }

    @Test
    @DisplayName("hasChangedSince should return false for identical configurations")
    void testHasChangedSinceForIdenticalConfigs() {
        // Given
        ConfigurationAggregate config1 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config),
            testKafkaConfig,
            Instant.now()
        );

        ConfigurationAggregate config2 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config),
            testKafkaConfig,
            Instant.now().plusSeconds(60)  // Different timestamp, but config is same
        );

        // When / Then
        assertThat(config2.hasChangedSince(config1)).isFalse();
    }

    @Test
    @DisplayName("hasChangedSince should return true for null previous config")
    void testHasChangedSinceForNullPrevious() {
        // Given
        ConfigurationAggregate config = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),
            testKafkaConfig,
            Instant.now()
        );

        // When / Then
        assertThat(config.hasChangedSince(null)).isTrue();
    }

    @Test
    @DisplayName("addedTables should return newly added tables")
    void testAddedTablesDetection() {
        // Given
        ConfigurationAggregate oldConfig = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),
            testKafkaConfig,
            Instant.now()
        );

        ConfigurationAggregate newConfig = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config, table3Config),
            testKafkaConfig,
            Instant.now()
        );

        // When
        Set<TableIdentifier> added = newConfig.addedTables(oldConfig);

        // Then
        assertThat(added).hasSize(2);
        assertThat(added).contains(
            table2Config.table(),
            table3Config.table()
        );
    }

    @Test
    @DisplayName("addedTables should return all tables when previous config is null")
    void testAddedTablesWithNullPrevious() {
        // Given
        ConfigurationAggregate config = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config),
            testKafkaConfig,
            Instant.now()
        );

        // When
        Set<TableIdentifier> added = config.addedTables(null);

        // Then
        assertThat(added).hasSize(2);
        assertThat(added).contains(
            table1Config.table(),
            table2Config.table()
        );
    }

    @Test
    @DisplayName("removedTables should return tables that were removed")
    void testRemovedTablesDetection() {
        // Given
        ConfigurationAggregate oldConfig = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config, table3Config),
            testKafkaConfig,
            Instant.now()
        );

        ConfigurationAggregate newConfig = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),  // Removed table2 and table3
            testKafkaConfig,
            Instant.now()
        );

        // When
        Set<TableIdentifier> removed = newConfig.removedTables(oldConfig);

        // Then
        assertThat(removed).hasSize(2);
        assertThat(removed).contains(
            table2Config.table(),
            table3Config.table()
        );
    }

    @Test
    @DisplayName("removedTables should return empty set when previous config is null")
    void testRemovedTablesWithNullPrevious() {
        // Given
        ConfigurationAggregate config = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config, table2Config),
            testKafkaConfig,
            Instant.now()
        );

        // When
        Set<TableIdentifier> removed = config.removedTables(null);

        // Then
        assertThat(removed).isEmpty();
    }

    @Test
    @DisplayName("hasChangedSince should detect database config changes")
    void testDatabaseConfigChangesDetected() {
        // Given
        SourceDatabaseConfig differentDbConfig = new SourceDatabaseConfig(
            DatabaseType.MYSQL,  // Different database type
            "localhost",
            3306,
            "testdb",
            "testuser",
            "testpass",
            null,
            java.util.Map.of()
        );

        ConfigurationAggregate config1 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),
            testKafkaConfig,
            Instant.now()
        );

        ConfigurationAggregate config2 = new ConfigurationAggregate(
            differentDbConfig,
            Set.of(table1Config),
            testKafkaConfig,
            Instant.now()
        );

        // When / Then
        assertThat(config2.hasChangedSince(config1)).isTrue();
    }

    @Test
    @DisplayName("hasChangedSince should detect Kafka config changes")
    void testKafkaConfigChangesDetected() {
        // Given
        KafkaConfig differentKafkaConfig = new KafkaConfig(
            List.of("different-broker:9092"),
            "cdc.{database}.{table}",
            null,
            null
        );

        ConfigurationAggregate config1 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),
            testKafkaConfig,
            Instant.now()
        );

        ConfigurationAggregate config2 = new ConfigurationAggregate(
            testDatabaseConfig,
            Set.of(table1Config),
            differentKafkaConfig,
            Instant.now()
        );

        // When / Then
        assertThat(config2.hasChangedSince(config1)).isTrue();
    }

    @Test
    @DisplayName("ConfigurationAggregate should be immutable")
    void testConfigurationImmutability() {
        // Given
        Set<TableConfig> mutableTables = new java.util.HashSet<>();
        mutableTables.add(table1Config);

        ConfigurationAggregate config = new ConfigurationAggregate(
            testDatabaseConfig,
            mutableTables,
            testKafkaConfig,
            Instant.now()
        );

        // When - try to modify original set
        mutableTables.add(table2Config);

        // Then - config should not be affected
        assertThat(config.getTableConfigs()).hasSize(1);
    }
}
