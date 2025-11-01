package com.chubb.cdc.debezium.domain.configuration.model;

import java.util.List;
import java.util.Map;

/**
 * Kafka destination configuration.
 *
 * <p>Configures Kafka producer settings and topic naming for CDC events.</p>
 *
 * <p><b>Invariants:</b></p>
 * <ul>
 *   <li>Broker addresses list must not be empty</li>
 *   <li>Topic pattern must contain {database} and {table} placeholders</li>
 * </ul>
 *
 * @param brokerAddresses list of Kafka broker addresses in "host:port" format
 * @param topicNamePattern topic naming pattern (e.g., "cdc.{database}.{table}")
 * @param security optional Kafka security configuration
 * @param producerProperties additional Kafka producer properties
 */
public record KafkaConfig(
    List<String> brokerAddresses,
    String topicNamePattern,
    KafkaSecurity security,
    Map<String, Object> producerProperties
) {

    /**
     * Kafka security configuration for authentication and encryption.
     *
     * @param protocol the security protocol
     * @param mechanism the SASL mechanism (if using SASL)
     * @param username SASL username
     * @param password SASL password
     * @param truststore SSL truststore configuration
     */
    public record KafkaSecurity(
        SecurityProtocol protocol,
        SaslMechanism mechanism,
        String username,
        String password,
        SslTruststore truststore
    ) {
        public enum SecurityProtocol {
            SSL,
            SASL_SSL,
            SASL_PLAINTEXT
        }

        public enum SaslMechanism {
            PLAIN,
            SCRAM_SHA_256,
            SCRAM_SHA_512
        }

        public record SslTruststore(
            String location,
            String password
        ) {
            public SslTruststore {
                if (location == null || location.isBlank()) {
                    throw new IllegalArgumentException("Truststore location must not be null or blank");
                }
            }
        }
    }

    /**
     * Compact constructor with validation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public KafkaConfig {
        if (brokerAddresses == null || brokerAddresses.isEmpty()) {
            throw new IllegalArgumentException("Broker addresses list must not be empty");
        }

        if (topicNamePattern == null || topicNamePattern.isBlank()) {
            throw new IllegalArgumentException("Topic name pattern must not be null or blank");
        }

        // Validate topic pattern contains required placeholders
        if (!topicNamePattern.contains("{database}") || !topicNamePattern.contains("{table}")) {
            throw new IllegalArgumentException(
                "Topic name pattern must contain {database} and {table} placeholders, got: " + topicNamePattern
            );
        }

        // Make immutable copies
        brokerAddresses = List.copyOf(brokerAddresses);
        producerProperties = producerProperties != null
            ? Map.copyOf(producerProperties)
            : Map.of();
    }
}
