package com.chubb.cdc.debezium.infrastructure.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration for Kafka producer.
 *
 * <p>Configures the Kafka producer for publishing CDC events to Kafka topics.
 * This configuration is part of the infrastructure layer and sets up
 * serialization, delivery guarantees, and performance tuning.</p>
 *
 * <p>Configuration properties are loaded from application.yml under the 'kafka' prefix.</p>
 */
@Configuration
public class KafkaConfiguration {

    @Value("${cdc.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${cdc.kafka.producer.acks:all}")
    private String acks;

    @Value("${cdc.kafka.producer.retries:3}")
    private int retries;

    @Value("${cdc.kafka.producer.batch-size:16384}")
    private int batchSize;

    @Value("${cdc.kafka.producer.linger-ms:10}")
    private int lingerMs;

    @Value("${cdc.kafka.producer.compression-type:snappy}")
    private String compressionType;

    /**
     * Creates the Kafka producer factory with proper serialization.
     *
     * <p>Configured for reliable delivery with:</p>
     * <ul>
     *   <li>acks=all: Wait for all replicas to acknowledge</li>
     *   <li>retries=3: Retry failed sends up to 3 times</li>
     *   <li>idempotence=true: Prevent duplicate messages</li>
     *   <li>String serialization: For change event DTOs</li>
     *   <li>Compression: Reduce network bandwidth (snappy)</li>
     * </ul>
     *
     * @return producer factory for change events
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Connection settings
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serialization
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Delivery guarantees
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Performance tuning
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);

        // Buffer memory (32 MB)
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        // Request timeout (30 seconds)
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Creates the Kafka template for sending messages.
     *
     * <p>The template provides a high-level API for publishing messages
     * and handles serialization automatically.</p>
     *
     * @return Kafka template
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Kafka producer configuration properties.
     */
    @Bean
    public KafkaProducerConfig kafkaProducerConfig() {
        return new KafkaProducerConfig(
            bootstrapServers,
            acks,
            retries,
            batchSize,
            lingerMs,
            compressionType
        );
    }

    /**
     * Configuration properties for Kafka producer.
     */
    public record KafkaProducerConfig(
        String bootstrapServers,
        String acks,
        int retries,
        int batchSize,
        int lingerMs,
        String compressionType
    ) {}
}
