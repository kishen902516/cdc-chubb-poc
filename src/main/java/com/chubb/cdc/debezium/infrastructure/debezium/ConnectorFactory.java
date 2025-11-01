package com.chubb.cdc.debezium.infrastructure.debezium;

import com.chubb.cdc.debezium.domain.configuration.model.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating database-specific Debezium connector strategies.
 *
 * <p>This factory creates the appropriate ConnectorStrategy implementation based on
 * the database type (PostgreSQL, MySQL, SQL Server, Oracle).</p>
 *
 * <p>Design Pattern: Factory</p>
 */
@Component
public class ConnectorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorFactory.class);

    private final Map<DatabaseType, ConnectorStrategy> strategies;

    /**
     * Creates the ConnectorFactory with all available connector strategies.
     */
    public ConnectorFactory() {
        this.strategies = new HashMap<>();
        registerStrategies();
        logger.info("ConnectorFactory initialized with {} strategies", strategies.size());
    }

    /**
     * Creates a connector strategy for the specified database type.
     *
     * @param databaseType the database type
     * @return the connector strategy
     * @throws UnsupportedDatabaseException if the database type is not supported
     */
    public ConnectorStrategy createStrategy(DatabaseType databaseType) throws UnsupportedDatabaseException {
        ConnectorStrategy strategy = strategies.get(databaseType);

        if (strategy == null) {
            throw new UnsupportedDatabaseException(
                "No connector strategy available for database type: " + databaseType
            );
        }

        logger.debug("Created connector strategy for database type: {}", databaseType);
        return strategy;
    }

    /**
     * Registers all available connector strategies.
     */
    private void registerStrategies() {
        // Register PostgreSQL strategy (primary for MVP)
        strategies.put(DatabaseType.POSTGRESQL, new PostgresConnectorStrategy());

        // MySQL strategy would be registered here when implemented
        // strategies.put(DatabaseType.MYSQL, new MysqlConnectorStrategy());

        // SQL Server and Oracle strategies would be added later
        // strategies.put(DatabaseType.SQLSERVER, new SqlServerConnectorStrategy());
        // strategies.put(DatabaseType.ORACLE, new OracleConnectorStrategy());
    }

    /**
     * Checks if a database type is supported.
     *
     * @param databaseType the database type
     * @return true if supported, false otherwise
     */
    public boolean isSupported(DatabaseType databaseType) {
        return strategies.containsKey(databaseType);
    }

    /**
     * Exception thrown when an unsupported database type is requested.
     */
    public static class UnsupportedDatabaseException extends Exception {
        public UnsupportedDatabaseException(String message) {
            super(message);
        }
    }
}
