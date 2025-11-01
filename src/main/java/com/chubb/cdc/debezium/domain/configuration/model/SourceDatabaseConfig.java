package com.chubb.cdc.debezium.domain.configuration.model;

import java.util.Map;
import java.util.Objects;

/**
 * Source database connection configuration.
 *
 * <p>Entity containing all necessary information to connect to a source database for CDC.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Host, database, username, and password are required</li>
 *   <li>Port must be in valid range (1-65535)</li>
 *   <li>SSL config is validated if present</li>
 *   <li>Password should be encrypted in storage</li>
 * </ul>
 */
public class SourceDatabaseConfig {

    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;  // encrypted in storage
    private final SslConfig sslConfig;  // may be null
    private final Map<String, String> additionalProperties;

    /**
     * Creates a new source database configuration.
     *
     * @param type the database type
     * @param host the database host (hostname or IP address)
     * @param port the database port (1-65535)
     * @param database the database name
     * @param username the connection username
     * @param password the connection password (should be encrypted in storage)
     * @param sslConfig optional SSL configuration
     * @param additionalProperties additional database-specific properties
     * @throws IllegalArgumentException if validation fails
     */
    public SourceDatabaseConfig(
        DatabaseType type,
        String host,
        int port,
        String database,
        String username,
        String password,
        SslConfig sslConfig,
        Map<String, String> additionalProperties
    ) {
        // Validate required fields
        if (type == null) {
            throw new IllegalArgumentException("Database type must not be null");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host must not be null or blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be in range 1-65535, got: " + port);
        }
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Database name must not be null or blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be null or blank");
        }

        this.type = type;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.sslConfig = sslConfig;
        this.additionalProperties = additionalProperties != null
            ? Map.copyOf(additionalProperties)
            : Map.of();
    }

    public DatabaseType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public SslConfig getSslConfig() {
        return sslConfig;
    }

    public Map<String, String> getAdditionalProperties() {
        return additionalProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceDatabaseConfig that = (SourceDatabaseConfig) o;
        return port == that.port &&
               type == that.type &&
               Objects.equals(host, that.host) &&
               Objects.equals(database, that.database) &&
               Objects.equals(username, that.username) &&
               Objects.equals(password, that.password) &&
               Objects.equals(sslConfig, that.sslConfig) &&
               Objects.equals(additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, host, port, database, username, password, sslConfig, additionalProperties);
    }

    @Override
    public String toString() {
        return "SourceDatabaseConfig{" +
               "type=" + type +
               ", host='" + host + '\'' +
               ", port=" + port +
               ", database='" + database + '\'' +
               ", username='" + username + '\'' +
               ", sslEnabled=" + (sslConfig != null && sslConfig.enabled()) +
               '}';
    }
}
