package com.chubb.cdc.debezium.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Kafka event publishing.
 *
 * This test verifies that published CDC events:
 * - Match the structure defined in change-event-schema.json
 * - Contain all required fields
 * - Use correct data types and formats
 * - Follow the normalization rules (ISO-8601 timestamps, JSON numbers, UTF-8 text)
 *
 * Per TDD methodology, this test is written FIRST before implementation exists.
 * It should FAIL initially, then PASS once Kafka publishing is fully implemented.
 *
 * NOTE: This test requires Docker to be running.
 */
@Testcontainers
public class KafkaPublishingIntegrationTest {

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    private KafkaConsumer<String, String> consumer;
    private ObjectMapper objectMapper;

    // This will be the actual event publisher once implemented
    private Object eventPublisher;  // TODO: Replace with EventPublisher type

    @BeforeEach
    void setUp() {
        // Create Kafka consumer to verify published messages
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        consumer = new KafkaConsumer<>(props);
        objectMapper = new ObjectMapper();

        // TODO: Initialize event publisher once implemented
        // eventPublisher = createEventPublisher(kafka.getBootstrapServers());
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }

        // TODO: Close event publisher
    }

    @Test
    void shouldPublishInsertEventMatchingSchema() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Create and publish INSERT event using event publisher
        // When
        // ChangeEvent insertEvent = createInsertEvent();
        // eventPublisher.publish(insertEvent, topic).get();

        // Simulate published message for now (until implementation exists)
        // This test will FAIL until actual implementation is complete

        // Then - poll and verify message
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        List<JsonNode> messages = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            JsonNode jsonNode = objectMapper.readTree(record.value());
            messages.add(jsonNode);
        }

        // Verify at least one message received
        assertThat(messages).isNotEmpty();

        JsonNode message = messages.get(0);

        // Verify required fields per change-event-schema.json
        assertThat(message.has("table")).isTrue();
        assertThat(message.has("operation")).isTrue();
        assertThat(message.has("timestamp")).isTrue();
        assertThat(message.has("position")).isTrue();
        assertThat(message.has("metadata")).isTrue();

        // Verify table structure
        JsonNode table = message.get("table");
        assertThat(table.has("database")).isTrue();
        assertThat(table.has("table")).isTrue();
        assertThat(table.get("database").asText()).isEqualTo("testdb");
        assertThat(table.get("table").asText()).isEqualTo("orders");

        // Verify operation type
        assertThat(message.get("operation").asText()).isEqualTo("INSERT");

        // Verify timestamp is ISO-8601 format
        String timestamp = message.get("timestamp").asText();
        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z");

        // Verify position structure
        JsonNode position = message.get("position");
        assertThat(position.has("sourcePartition")).isTrue();
        assertThat(position.has("offset")).isTrue();
        assertThat(position.get("offset").isObject()).isTrue();

        // Verify INSERT: before null, after present
        assertThat(message.get("before").isNull()).isTrue();
        assertThat(message.has("after")).isTrue();
        assertThat(message.get("after").isObject()).isTrue();

        // Verify metadata
        JsonNode metadata = message.get("metadata");
        assertThat(metadata.has("source")).isTrue();
        assertThat(metadata.has("version")).isTrue();
        assertThat(metadata.has("connector")).isTrue();
        assertThat(metadata.get("version").asText()).matches("\\d+\\.\\d+\\.\\d+");
    }

    @Test
    void shouldPublishUpdateEventMatchingSchema() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Create and publish UPDATE event
        // When
        // ChangeEvent updateEvent = createUpdateEvent();
        // eventPublisher.publish(updateEvent, topic).get();

        // Then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        List<JsonNode> messages = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            JsonNode jsonNode = objectMapper.readTree(record.value());
            messages.add(jsonNode);
        }

        assertThat(messages).isNotEmpty();
        JsonNode message = messages.get(0);

        // Verify UPDATE: both before and after present
        assertThat(message.get("operation").asText()).isEqualTo("UPDATE");
        assertThat(message.has("before")).isTrue();
        assertThat(message.get("before").isObject()).isTrue();
        assertThat(message.has("after")).isTrue();
        assertThat(message.get("after").isObject()).isTrue();
    }

    @Test
    void shouldPublishDeleteEventMatchingSchema() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Create and publish DELETE event
        // When
        // ChangeEvent deleteEvent = createDeleteEvent();
        // eventPublisher.publish(deleteEvent, topic).get();

        // Then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        List<JsonNode> messages = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            JsonNode jsonNode = objectMapper.readTree(record.value());
            messages.add(jsonNode);
        }

        assertThat(messages).isNotEmpty();
        JsonNode message = messages.get(0);

        // Verify DELETE: before present, after null
        assertThat(message.get("operation").asText()).isEqualTo("DELETE");
        assertThat(message.has("before")).isTrue();
        assertThat(message.get("before").isObject()).isTrue();
        assertThat(message.get("after").isNull()).isTrue();
    }

    @Test
    void shouldNormalizeNumericValues() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Publish event with numeric values (decimal, integer, bigint)
        // When
        // ChangeEvent event = createEventWithNumericData();
        // eventPublisher.publish(event, topic).get();

        // Then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> record = records.iterator().next();
        JsonNode message = objectMapper.readTree(record.value());

        JsonNode after = message.get("after");
        assertThat(after).isNotNull();

        // Numeric values should be JSON numbers (not strings)
        if (after.has("order_id")) {
            assertThat(after.get("order_id").isNumber()).isTrue();
        }
        if (after.has("total_amount")) {
            assertThat(after.get("total_amount").isNumber()).isTrue();
        }
    }

    @Test
    void shouldNormalizeTimestampsToIso8601() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Publish event with timestamp values
        // When
        // ChangeEvent event = createEventWithTimestamps();
        // eventPublisher.publish(event, topic).get();

        // Then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> record = records.iterator().next();
        JsonNode message = objectMapper.readTree(record.value());

        // Event timestamp should be ISO-8601
        String eventTimestamp = message.get("timestamp").asText();
        assertThat(eventTimestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z");

        // Row data timestamps should also be ISO-8601
        JsonNode after = message.get("after");
        if (after != null && after.has("created_at")) {
            String createdAt = after.get("created_at").asText();
            assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z");
        }
    }

    @Test
    void shouldHandleUtf8TextCorrectly() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Publish event with UTF-8 text (special characters, emojis)
        // When
        // ChangeEvent event = createEventWithUtf8Text("Order with emoji 🎉 and special chars: äöü");
        // eventPublisher.publish(event, topic).get();

        // Then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> record = records.iterator().next();
        JsonNode message = objectMapper.readTree(record.value());

        JsonNode after = message.get("after");
        // Verify UTF-8 characters are preserved
        if (after != null && after.has("description")) {
            String description = after.get("description").asText();
            assertThat(description).contains("🎉");
            assertThat(description).contains("äöü");
        }
    }

    @Test
    void shouldPublishToCorrectTopicPattern() throws Exception {
        // Given - topic pattern: cdc.{database}.{table}
        String database = "testdb";
        String table = "customers";
        String expectedTopic = "cdc." + database + "." + table;

        consumer.subscribe(Collections.singletonList(expectedTopic));

        // TODO: Publish event to customers table
        // When
        // ChangeEvent event = createEventForTable(database, table);
        // eventPublisher.publish(event, expectedTopic).get();

        // Then - verify message appears in correct topic
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        for (ConsumerRecord<String, String> record : records) {
            assertThat(record.topic()).isEqualTo(expectedTopic);
        }
    }

    @Test
    void shouldIncludeSourcePartitionInPosition() throws Exception {
        // Given
        String topic = "cdc.testdb.orders";
        consumer.subscribe(Collections.singletonList(topic));

        // TODO: Publish event with specific source partition
        // When
        // ChangeEvent event = createEventWithSourcePartition("server1-testdb");
        // eventPublisher.publish(event, topic).get();

        // Then
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, String> record = records.iterator().next();
        JsonNode message = objectMapper.readTree(record.value());

        JsonNode position = message.get("position");
        assertThat(position.get("sourcePartition").asText()).isNotEmpty();
        assertThat(position.get("offset").isObject()).isTrue();
    }

    /**
     * Helper methods to create test events (to be implemented).
     */
    private Object createInsertEvent() {
        // TODO: Implement using domain model
        return null;
    }

    private Object createUpdateEvent() {
        // TODO: Implement using domain model
        return null;
    }

    private Object createDeleteEvent() {
        // TODO: Implement using domain model
        return null;
    }
}
