package com.chubb.cdc.debezium.contract.kafka;

import com.chubb.cdc.debezium.application.dto.ChangeEventDto;
import com.chubb.cdc.debezium.application.dto.ChangeEventDto.CdcPositionDto;
import com.chubb.cdc.debezium.application.dto.ChangeEventDto.TableIdentifierDto;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.chubb.cdc.debezium.domain.changecapture.model.OperationType;
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

        // Mock successful send
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        // This will fail until we create KafkaEventPublisher
        // eventPublisher = new KafkaEventPublisher(kafkaTemplate, new TopicNameResolver("cdc.{database}.{table}"));

        // TODO: Uncomment above line after implementation (T071, T072)
        throw new UnsupportedOperationException("KafkaEventPublisher not yet implemented - this test should FAIL");
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
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        ChangeEventDto event = createInsertEvent();

        // When/Then
        assertThatThrownBy(() -> eventPublisher.publishAsync(event).get(5, TimeUnit.SECONDS))
            .hasCauseInstanceOf(RuntimeException.class)
            .hasMessageContaining("Kafka unavailable");
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
