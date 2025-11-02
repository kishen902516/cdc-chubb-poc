package com.chubb.cdc.debezium.contract.kafka;

import com.chubb.cdc.debezium.application.dto.ChangeEventDto;
import com.chubb.cdc.debezium.application.dto.ChangeEventDto.CdcPositionDto;
import com.chubb.cdc.debezium.application.dto.ChangeEventDto.TableIdentifierDto;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.chubb.cdc.debezium.domain.changecapture.model.OperationType;
import com.chubb.cdc.debezium.infrastructure.kafka.KafkaEventPublisher;
import com.chubb.cdc.debezium.infrastructure.kafka.TopicNameResolver;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contract test for EventPublisher implementations.
 *
 * This test ensures that any implementation of EventPublisher
 * (Kafka-based, in-memory for testing, etc.) adheres to the expected contract.
 *
 * TDD: This test is written FIRST and should FAIL until implementation exists.
 */
@DisplayName("EventPublisher Contract Test")
class EventPublisherContractTest {

    private EventPublisher eventPublisher;
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        // Mock KafkaTemplate for testing
        kafkaTemplate = mock(KafkaTemplate.class);

        // Mock successful send with proper SendResult
        SendResult<String, String> sendResult = mock(SendResult.class);
        ProducerRecord<String, String> producerRecord = mock(ProducerRecord.class);
        RecordMetadata recordMetadata = new RecordMetadata(
                new TopicPartition("test-topic", 0),
                0L, // base offset
                0, // batch index
                0L, // timestamp
                0L, // checksum (deprecated)
                0, // serialized key size
                0  // serialized value size
        );

        when(sendResult.getProducerRecord()).thenReturn(producerRecord);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Create the actual KafkaEventPublisher implementation
        eventPublisher = new KafkaEventPublisher(kafkaTemplate, new TopicNameResolver("cdc.{database}.{table}"));
    }

    @Test
    @DisplayName("Should publish INSERT event successfully")
    void shouldPublishInsertEvent() throws Exception {
        // Given
        ChangeEventDto event = createInsertEvent();

        // When
        eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS);

        // Then
        verify(kafkaTemplate).send(
            eq("cdc.testdb.orders"),
            anyString(),
            anyString()
        );
    }

    @Test
    @DisplayName("Should publish UPDATE event successfully")
    void shouldPublishUpdateEvent() throws Exception {
        // Given
        ChangeEventDto event = createUpdateEvent();

        // When
        eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS);

        // Then
        verify(kafkaTemplate).send(
            eq("cdc.testdb.orders"),
            anyString(),
            anyString()
        );
    }

    @Test
    @DisplayName("Should publish DELETE event successfully")
    void shouldPublishDeleteEvent() throws Exception {
        // Given
        ChangeEventDto event = createDeleteEvent();

        // When
        eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS);

        // Then
        verify(kafkaTemplate).send(
            eq("cdc.testdb.orders"),
            anyString(),
            anyString()
        );
    }

    @Test
    @DisplayName("Should resolve topic name from pattern")
    void shouldResolveTopicNameFromPattern() throws Exception {
        // Given
        TableIdentifierDto table = new TableIdentifierDto("mydb", "public", "customers");
        ChangeEventDto event = new ChangeEventDto(
            table,
            OperationType.INSERT,
            Instant.now(),
            new CdcPositionDto("server1.mydb", Map.of("lsn", "0/12345678")),
            null,
            Map.of("id", 1, "name", "John Doe"),
            Map.of()
        );

        // When
        eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS);

        // Then
        verify(kafkaTemplate).send(
            eq("cdc.mydb.customers"),
            anyString(),
            anyString()
        );
    }

    @Test
    @DisplayName("Should use table identifier as message key")
    void shouldUseTableIdentifierAsMessageKey() throws Exception {
        // Given
        ChangeEventDto event = createInsertEvent();

        // When
        eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS);

        // Then
        verify(kafkaTemplate).send(
            anyString(),
            contains("testdb.public.orders"),
            anyString()
        );
    }

    @Test
    @DisplayName("Should serialize event as JSON")
    void shouldSerializeEventAsJson() throws Exception {
        // Given
        ChangeEventDto event = createInsertEvent();

        // When
        eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS);

        // Then
        verify(kafkaTemplate).send(
            anyString(),
            anyString(),
            argThat(json ->
                json.contains("\"operation\":\"INSERT\"") &&
                json.contains("\"table\":{") &&
                json.contains("\"after\":{")
            )
        );
    }

    @Test
    @DisplayName("Should handle Kafka publish failure")
    void shouldHandleKafkaPublishFailure() {
        // Given
        // Reset the mock to return a failing future
        reset(kafkaTemplate);
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // Create a new event publisher with the reset mock
        eventPublisher = new KafkaEventPublisher(kafkaTemplate, new TopicNameResolver("cdc.{database}.{table}"));

        ChangeEventDto event = createInsertEvent();

        // When/Then
        assertThatThrownBy(() -> eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS))
            .hasCauseInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("Kafka unavailable");
    }

    @Test
    @DisplayName("Should return CompletableFuture for async publishing")
    void shouldReturnCompletableFutureForAsyncPublishing() {
        // Given
        ChangeEventDto event = createInsertEvent();

        // When
        CompletableFuture<Void> result = eventPublisher.publishAsync(event);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(CompletableFuture.class);
    }

    @Test
    @DisplayName("Should publish event synchronously")
    void shouldPublishEventSynchronously() throws Exception {
        // Given
        ChangeEventDto event = createInsertEvent();

        // When
        eventPublisher.publish(event);

        // Then
        verify(kafkaTemplate).send(
            eq("cdc.testdb.orders"),
            anyString(),
            anyString()
        );
    }

    // Helper methods to create test events

    private ChangeEventDto createInsertEvent() {
        TableIdentifierDto table = new TableIdentifierDto("testdb", "public", "orders");
        return new ChangeEventDto(
            table,
            OperationType.INSERT,
            Instant.now(),
            new CdcPositionDto("server1.testdb", Map.of("lsn", "0/12345678")),
            null,
            Map.of(
                "order_id", 123,
                "customer_id", 456,
                "total", 99.99,
                "status", "PENDING"
            ),
            Map.of("source", "debezium-cdc-app")
        );
    }

    private ChangeEventDto createUpdateEvent() {
        TableIdentifierDto table = new TableIdentifierDto("testdb", "public", "orders");
        return new ChangeEventDto(
            table,
            OperationType.UPDATE,
            Instant.now(),
            new CdcPositionDto("server1.testdb", Map.of("lsn", "0/12345679")),
            Map.of(
                "order_id", 123,
                "status", "PENDING"
            ),
            Map.of(
                "order_id", 123,
                "status", "CONFIRMED"
            ),
            Map.of("source", "debezium-cdc-app")
        );
    }

    private ChangeEventDto createDeleteEvent() {
        TableIdentifierDto table = new TableIdentifierDto("testdb", "public", "orders");
        return new ChangeEventDto(
            table,
            OperationType.DELETE,
            Instant.now(),
            new CdcPositionDto("server1.testdb", Map.of("lsn", "0/12345680")),
            Map.of(
                "order_id", 123,
                "customer_id", 456,
                "total", 99.99,
                "status", "CANCELLED"
            ),
            null,
            Map.of("source", "debezium-cdc-app")
        );
    }
}
