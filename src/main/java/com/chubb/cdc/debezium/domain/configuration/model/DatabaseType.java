package com.chubb.cdc.debezium.domain.configuration.model;

/**
 * Enumeration of supported database types for CDC capture.
 *
 * <p>Each database type has specific connector implementations and configuration requirements.</p>
 *
 * @see SourceDatabaseConfig
 */
public enum DatabaseType {
    /**
     * PostgreSQL database (version 10+)
     */
    POSTGRESQL,

    /**
     * MySQL database (version 5.7+)
     */
    MYSQL,

    /**
     * Microsoft SQL Server database
     */
    SQLSERVER,

    /**
     * Oracle database
     */
    ORACLE
}
