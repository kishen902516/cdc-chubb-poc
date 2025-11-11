package com.chubb.cdc.debezium.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;

/**
 * Test configuration that provides mock beans for tests that require Kafka dependencies.
 * This configuration is used to prevent Spring context failures when KafkaTemplate is required
 * but we don't want to connect to an actual Kafka broker during testing.
 */
@TestConfiguration
public class TestKafkaConfiguration {

    @Bean
    @Primary
    public KafkaTemplate<String, String> mockKafkaTemplate() {
        return mock(KafkaTemplate.class);
    }
}