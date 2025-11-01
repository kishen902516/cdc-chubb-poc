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
 * PostgreSQL-specific connector strategy for Debezium.
 *
 * <p>Builds connector configuration for PostgreSQL databases using the
 * Debezium PostgreSQL connector with pgoutput logical decoding plugin.</p>
 *
 * <p>Key PostgreSQL-specific configuration:</p>
 * <ul>
 *   <li>Connector class: io.debezium.connector.postgresql.PostgresConnector</li>
 *   <li>Plugin: pgoutput (built-in PostgreSQL logical decoding)</li>
 *   <li>Replication slot: debezium_cdc_slot</li>
 *   <li>Publication: debezium_publication</li>
 * </ul>
 *
 * <p>Design Pattern: Strategy (concrete implementation)</p>
 *
 * @see <a href="https://debezium.io/documentation/reference/stable/connectors/postgresql.html">Debezium PostgreSQL Connector</a>
 */
public class PostgresConnectorStrategy implements ConnectorStrategy {

    private static final Logger logger = LoggerFactory.getLogger(PostgresConnectorStrategy.class);

    private static final String CONNECTOR_CLASS = "io.debezium.connector.postgresql.PostgresConnector";
    private static final String DEFAULT_PLUGIN_NAME = "pgoutput";
    private static final String DEFAULT_SLOT_NAME = "debezium_cdc_slot";
    private static final String DEFAULT_PUBLICATION_NAME = "debezium_publication";

    @Override
    public Properties buildConnectorConfig(
        SourceDatabaseConfig databaseConfig,
        Set<TableConfig> tableConfigs,
        String offsetStoragePath
    ) {
        logger.info("Building PostgreSQL connector configuration for database: {}",
            databaseConfig.getDatabase());

        Properties props = new Properties();

        // Connector identification
        props.setProperty("name", "postgres-cdc-connector");
        props.setProperty("connector.class", CONNECTOR_CLASS);

        // Database connection
        props.setProperty("database.hostname", databaseConfig.getHost());
        props.setProperty("database.port", String.valueOf(databaseConfig.getPort()));
        props.setProperty("database.user", databaseConfig.getUsername());
        props.setProperty("database.password", databaseConfig.getPassword());
        props.setProperty("database.dbname", databaseConfig.getDatabase());

        // PostgreSQL logical decoding
        props.setProperty("plugin.name", DEFAULT_PLUGIN_NAME);
        props.setProperty("slot.name", DEFAULT_SLOT_NAME);
        props.setProperty("publication.name", DEFAULT_PUBLICATION_NAME);

        // Server identification for offset storage
        props.setProperty("database.server.name", generateServerName(databaseConfig));

        // Table inclusion list (schema.table format for PostgreSQL)
        String tableIncludeList = buildTableIncludeList(tableConfigs);
        if (!tableIncludeList.isEmpty()) {
            props.setProperty("table.include.list", tableIncludeList);
        }

        // Message key columns for tables with composite keys
        configureMessageKeyColumns(props, tableConfigs);

        // SSL configuration if enabled
        if (databaseConfig.getSslConfig() != null && databaseConfig.getSslConfig().enabled()) {
            configureSsl(props, databaseConfig.getSslConfig());
        }

        // Snapshot mode - initial snapshot on first run
        props.setProperty("snapshot.mode", "initial");

        // Schema change events (track DDL changes)
        props.setProperty("include.schema.changes", "true");

        // Time precision - microseconds for PostgreSQL
        props.setProperty("time.precision.mode", "adaptive");

        // Additional database-specific properties
        databaseConfig.getAdditionalProperties().forEach(props::setProperty);

        // Offset storage (file-based for embedded engine)
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        props.setProperty("offset.storage.file.filename", offsetStoragePath);
        props.setProperty("offset.flush.interval.ms", "10000"); // Flush every 10 seconds

        logger.debug("PostgreSQL connector configuration built successfully with {} tables",
            tableConfigs.size());

        return props;
    }

    @Override
    public String getDatabaseType() {
        return "POSTGRESQL";
    }

    /**
     * Builds the table include list in schema.table format.
     *
     * Example: "public.orders,public.customers"
     */
    private String buildTableIncludeList(Set<TableConfig> tableConfigs) {
        return tableConfigs.stream()
            .map(tc -> {
                String schema = tc.table().schema() != null ? tc.table().schema() : "public";
                return schema + "\\." + tc.table().table();
            })
            .collect(Collectors.joining(","));
    }

    /**
     * Configures message key columns for tables with composite unique keys.
     *
     * For tables without primary keys, Debezium can use a composite key
     * specified in the configuration.
     */
    private void configureMessageKeyColumns(Properties props, Set<TableConfig> tableConfigs) {
        // Find tables with composite keys
        String messageKeyColumns = tableConfigs.stream()
            .filter(tc -> tc.compositeKey().isPresent())
            .map(tc -> {
                String schema = tc.table().schema() != null ? tc.table().schema() : "public";
                String table = schema + "." + tc.table().table();
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
     * Configures SSL/TLS settings for PostgreSQL connection.
     */
    private void configureSsl(Properties props, SslConfig sslConfig) {
        // PostgreSQL SSL mode
        switch (sslConfig.mode()) {
            case REQUIRE:
                props.setProperty("database.sslmode", "require");
                break;
            case VERIFY_CA:
                props.setProperty("database.sslmode", "verify-ca");
                break;
            case VERIFY_FULL:
                props.setProperty("database.sslmode", "verify-full");
                break;
        }

        // CA certificate for server verification
        sslConfig.caCertPath().ifPresent(path ->
            props.setProperty("database.sslrootcert", path)
        );

        // Client certificate and key for mutual TLS
        sslConfig.clientCertPath().ifPresent(path ->
            props.setProperty("database.sslcert", path)
        );

        sslConfig.clientKeyPath().ifPresent(path ->
            props.setProperty("database.sslkey", path)
        );

        logger.debug("SSL configuration enabled with mode: {}", sslConfig.mode());
    }

    /**
     * Generates a unique server name for offset tracking.
     *
     * The server name is used as part of the offset storage key to distinguish
     * between different database instances.
     */
    private String generateServerName(SourceDatabaseConfig config) {
        return String.format("postgres-%s-%s", config.getHost(), config.getDatabase())
            .replaceAll("[^a-zA-Z0-9_-]", "_")
            .toLowerCase();
    }
}
