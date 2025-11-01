package com.chubb.cdc.debezium.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Spring configuration for Debezium CDC engine.
 *
 * <p>Configures the Debezium embedded engine with properties from application.yml.
 * This configuration is part of the infrastructure layer and sets up the
 * database-specific connectors and engine behavior.</p>
 *
 * <p>Configuration properties are loaded from the 'debezium' prefix in application.yml.</p>
 */
@Configuration
public class DebeziumConfiguration {

    /**
     * Creates Debezium engine properties from application configuration.
     *
     * <p>Example configuration in application.yml:</p>
     * <pre>
     * debezium:
     *   connector.class: io.debezium.connector.postgresql.PostgresConnector
     *   offset.storage: org.apache.kafka.connect.storage.FileOffsetBackingStore
     *   offset.storage.file.filename: /data/offsets.dat
     *   offset.flush.interval.ms: 10000
     *   database.hostname: localhost
     *   database.port: 5432
     *   database.user: cdcuser
     *   database.password: ${DB_PASSWORD}
     *   database.dbname: mydb
     *   database.server.name: cdc-server
     *   schema.include.list: public
     *   table.include.list: public.orders,public.customers
     * </pre>
     *
     * @return Debezium engine properties
     */
    @Bean
    @ConfigurationProperties(prefix = "debezium")
    public Properties debeziumProperties() {
        return new Properties();
    }

    /**
     * Creates connector configuration properties.
     *
     * <p>Provides default settings for Debezium connectors that can be
     * overridden via application.yml or environment variables.</p>
     *
     * @return connector properties with defaults
     */
    @Bean
    public DebeziumConnectorConfig connectorConfig() {
        return new DebeziumConnectorConfig(
            10000L,  // offset flush interval (10 seconds)
            2048,    // max batch size
            8192,    // max queue size
            5000L    // poll interval (5 seconds)
        );
    }

    /**
     * Configuration properties for Debezium connector behavior.
     */
    public record DebeziumConnectorConfig(
        long offsetFlushIntervalMs,
        int maxBatchSize,
        int maxQueueSize,
        long pollIntervalMs
    ) {
        /**
         * Converts to Properties for Debezium engine.
         */
        public Properties toProperties() {
            Properties props = new Properties();
            props.setProperty("offset.flush.interval.ms", String.valueOf(offsetFlushIntervalMs));
            props.setProperty("max.batch.size", String.valueOf(maxBatchSize));
            props.setProperty("max.queue.size", String.valueOf(maxQueueSize));
            props.setProperty("poll.interval.ms", String.valueOf(pollIntervalMs));
            return props;
        }
    }
}
