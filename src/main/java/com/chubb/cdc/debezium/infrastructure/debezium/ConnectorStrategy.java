package com.chubb.cdc.debezium.infrastructure.debezium;

import com.chubb.cdc.debezium.domain.configuration.model.SourceDatabaseConfig;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;

import java.util.Properties;
import java.util.Set;

/**
 * Strategy interface for building database-specific Debezium connector configurations.
 *
 * <p>Each database type (PostgreSQL, MySQL, SQL Server, Oracle) requires different
 * connector properties and configuration approaches. This interface abstracts those
 * differences using the Strategy pattern.</p>
 *
 * <p>Design Pattern: Strategy</p>
 */
public interface ConnectorStrategy {

    /**
     * Builds Debezium connector configuration properties for the specific database type.
     *
     * @param databaseConfig source database connection configuration
     * @param tableConfigs set of tables to monitor
     * @param offsetStoragePath path to the offset storage file
     * @return configured Properties for the Debezium connector
     */
    Properties buildConnectorConfig(
        SourceDatabaseConfig databaseConfig,
        Set<TableConfig> tableConfigs,
        String offsetStoragePath
    );

    /**
     * Returns the database type this strategy supports.
     *
     * @return the database type
     */
    String getDatabaseType();
}
