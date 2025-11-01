package com.chubb.cdc.debezium.domain.configuration.model;

import java.util.Optional;

/**
 * SSL/TLS configuration for database connections.
 *
 * <p>Configures secure connections to source databases with optional client certificates.</p>
 *
 * @param enabled whether SSL is enabled
 * @param mode SSL verification mode
 * @param caCertPath path to CA certificate for server verification (optional)
 * @param clientCertPath path to client certificate for mutual TLS (optional)
 * @param clientKeyPath path to client private key for mutual TLS (optional)
 */
public record SslConfig(
    boolean enabled,
    SslMode mode,
    Optional<String> caCertPath,
    Optional<String> clientCertPath,
    Optional<String> clientKeyPath
) {

    /**
     * SSL verification modes.
     */
    public enum SslMode {
        /** Require SSL connection, but don't verify server certificate */
        REQUIRE,
        /** Require SSL and verify server certificate against CA */
        VERIFY_CA,
        /** Require SSL, verify server certificate, and verify hostname */
        VERIFY_FULL
    }

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if mode is null when SSL is enabled
     */
    public SslConfig {
        if (enabled && mode == null) {
            throw new IllegalArgumentException("SSL mode must be specified when SSL is enabled");
        }

        // Ensure optionals are present
        caCertPath = caCertPath != null ? caCertPath : Optional.empty();
        clientCertPath = clientCertPath != null ? clientCertPath : Optional.empty();
        clientKeyPath = clientKeyPath != null ? clientKeyPath : Optional.empty();
    }
}
