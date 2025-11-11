package com.chubb.cdc.debezium.infrastructure.debezium;

import com.chubb.cdc.debezium.domain.configuration.model.SourceDatabaseConfig;
import com.chubb.cdc.debezium.domain.configuration.model.SslConfig;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SQL Server-specific connector strategy for Debezium.
 *
 * <p>Builds connector configuration for Microsoft SQL Server databases using the
 * Debezium SQL Server connector.</p>
 *
 * <p>Key SQL Server-specific configuration:</p>
 * <ul>
 *   <li>Connector class: io.debezium.connector.sqlserver.SqlServerConnector</li>
 *   <li>Uses SQL Server CDC (Change Data Capture) feature</li>
 *   <li>Requires SQL Server Agent to be running</li>
 *   <li>Database must have CDC enabled at both database and table level</li>
 * </ul>
 *
 * <p>Design Pattern: Strategy (concrete implementation)</p>
 *
 * @see <a href="https://debezium.io/documentation/reference/stable/connectors/sqlserver.html">Debezium SQL Server Connector</a>
 */
public class SqlServerConnectorStrategy implements ConnectorStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SqlServerConnectorStrategy.class);

    private static final String CONNECTOR_CLASS = "io.debezium.connector.sqlserver.SqlServerConnector";

    @Override
    public Properties buildConnectorConfig(
        SourceDatabaseConfig databaseConfig,
        Set<TableConfig> tableConfigs,
        String offsetStoragePath
    ) {
        logger.info("Building SQL Server connector configuration for database: {}",
            databaseConfig.getDatabase());

        Properties props = new Properties();

        // Connector identification
        props.setProperty("name", "sqlserver-cdc-connector");
        props.setProperty("connector.class", CONNECTOR_CLASS);

        // Database connection
        props.setProperty("database.hostname", databaseConfig.getHost());
        props.setProperty("database.port", String.valueOf(databaseConfig.getPort()));
        props.setProperty("database.user", databaseConfig.getUsername());
        props.setProperty("database.password", databaseConfig.getPassword());

        // SQL Server specific - database names property instead of dbname
        props.setProperty("database.names", databaseConfig.getDatabase());

        // Server identification for offset storage
        String serverName = generateServerName(databaseConfig);
        props.setProperty("database.server.name", serverName);

        // Topic prefix - required by Debezium
        props.setProperty("topic.prefix", serverName);

        // Table inclusion list (dbo.schema.table format for SQL Server)
        String tableIncludeList = buildTableIncludeList(tableConfigs, databaseConfig.getDatabase());
        if (!tableIncludeList.isEmpty()) {
            props.setProperty("table.include.list", tableIncludeList);
        }

        // Message key columns for tables with composite keys
        configureMessageKeyColumns(props, tableConfigs, databaseConfig.getDatabase());

        // SSL/TLS configuration if enabled
        if (databaseConfig.getSslConfig() != null && databaseConfig.getSslConfig().enabled()) {
            configureSsl(props, databaseConfig.getSslConfig());
        }

        // Snapshot mode - initial snapshot on first run
        props.setProperty("snapshot.mode", "initial");

        // Schema change events (track DDL changes)
        props.setProperty("include.schema.changes", "true");

        // Decimal handling - precise for financial data
        props.setProperty("decimal.handling.mode", "precise");

        // Time precision - adaptive for SQL Server datetime types
        props.setProperty("time.precision.mode", "adaptive");

        // SQL Server specific - handle database timezone
        props.setProperty("database.server.timezone", "UTC");

        // Additional database-specific properties
        databaseConfig.getAdditionalProperties().forEach(props::setProperty);

        // Offset storage (file-based for embedded engine)
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", offsetStoragePath);
        props.setProperty("offset.flush.interval.ms", "10000"); // Flush every 10 seconds

        // Schema history (required for SQL Server connector)
        props.setProperty("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory");
        props.setProperty("schema.history.internal.file.filename", offsetStoragePath + ".history");

        logger.debug("SQL Server connector configuration built successfully with {} tables",
            tableConfigs.size());

        return props;
    }

    @Override
    public String getDatabaseType() {
        return "SQLSERVER";
    }

    /**
     * Builds the table include list in database.schema.table format for SQL Server.
     *
     * Example: "mydb.dbo.orders,mydb.dbo.customers"
     */
    private String buildTableIncludeList(Set<TableConfig> tableConfigs, String database) {
        return tableConfigs.stream()
            .map(tc -> {
                String schema = tc.table().schema() != null ? tc.table().schema() : "dbo";
                return database + "\\." + schema + "\\." + tc.table().table();
            })
            .collect(Collectors.joining(","));
    }

    /**
     * Configures message key columns for tables with composite unique keys.
     *
     * For tables without primary keys, Debezium can use a composite key
     * specified in the configuration.
     */
    private void configureMessageKeyColumns(Properties props, Set<TableConfig> tableConfigs, String database) {
        // Find tables with composite keys
        String messageKeyColumns = tableConfigs.stream()
            .filter(tc -> tc.compositeKey().isPresent())
            .map(tc -> {
                String schema = tc.table().schema() != null ? tc.table().schema() : "dbo";
                String table = database + "." + schema + "." + tc.table().table();
                String columns = String.join(",", tc.compositeKey().get().columnNames());
                return table + ":" + columns;
            })
            .collect(Collectors.joining(";"));

        if (!messageKeyColumns.isEmpty()) {
            props.setProperty("message.key.columns", messageKeyColumns);
            logger.debug("Configured message key columns for {} tables", messageKeyColumns.split(";").length);
        }
    }

    /**
     * Configures SSL/TLS settings for SQL Server connection.
     */
    private void configureSsl(Properties props, SslConfig sslConfig) {
        // SQL Server uses encrypt and trustServerCertificate properties
        props.setProperty("database.encrypt", "true");

        switch (sslConfig.mode()) {
            case REQUIRE:
                // Trust the server certificate without validation (dev/test only)
                props.setProperty("database.trustServerCertificate", "true");
                break;
            case VERIFY_CA:
            case VERIFY_FULL:
                // Require proper certificate validation
                props.setProperty("database.trustServerCertificate", "false");

                // Set truststore for CA certificate validation
                sslConfig.caCertPath().ifPresent(path -> {
                    props.setProperty("database.trustStore", path);
                    sslConfig.truststorePassword().ifPresent(password ->
                        props.setProperty("database.trustStorePassword", password)
                    );
                });
                break;
        }

        logger.debug("SSL configuration enabled with mode: {}", sslConfig.mode());
    }

    /**
     * Generates a unique server name for offset tracking.
     *
     * The server name is used as part of the offset storage key to distinguish
     * between different database instances.
     */
    private String generateServerName(SourceDatabaseConfig config) {
        return String.format("sqlserver-%s-%s", config.getHost(), config.getDatabase())
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .toLowerCase();
    }
}