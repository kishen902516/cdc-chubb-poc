package com.chubb.cdc.debezium.unit.application;

import com.chubb.cdc.debezium.application.port.output.DataNormalizer;
import com.chubb.cdc.debezium.application.port.output.EventPublisher;
import com.chubb.cdc.debezium.application.usecase.changecapture.ProcessChangeEventUseCase;
import com.chubb.cdc.debezium.domain.changecapture.model.*;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.chubb.cdc.debezium.domain.configuration.model.DatabaseType;
import com.chubb.cdc.debezium.application.dto.ChangeEventDto;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessChangeEventUseCase.
 * <p>
 * Tests the business logic of processing change events including
 * normalization, publishing, and offset persistence.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessChangeEventUseCase Unit Tests")
class ProcessChangeEventUseCaseTest {

    @Mock
    private DataNormalizer dataNormalizer;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private OffsetRepository offsetRepository;

    private ProcessChangeEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessChangeEventUseCase(dataNormalizer, eventPublisher, offsetRepository);
    }

    @Test
    @DisplayName("Should process INSERT event successfully")
    void shouldProcessInsertEventSuccessfully() throws Exception {
        // Given
        ChangeEvent insertEvent = createInsertEvent();
        RowData normalizedData = new RowData(Map.of(
                "order_id", 123,
                "status", "PENDING",
                "created_at", "2025-11-01T10:00:00Z"
        ));

        when(dataNormalizer.normalize(any(), any(DatabaseType.class))).thenReturn(normalizedData);

        // When
        useCase.execute(insertEvent, DatabaseType.POSTGRESQL);

        // Then
        verify(dataNormalizer).normalize(eq(insertEvent.after().fields()), any(DatabaseType.class));
        verify(eventPublisher).publish(any(ChangeEventDto.class));
        verify(offsetRepository).save(insertEvent.position());
    }

    @Test
    @DisplayName("Should process UPDATE event successfully")
    void shouldProcessUpdateEventSuccessfully() throws Exception {
        // Given
        ChangeEvent updateEvent = createUpdateEvent();
        RowData normalizedBefore = new RowData(Map.of("status", "PENDING"));
        RowData normalizedAfter = new RowData(Map.of("status", "CONFIRMED"));

        when(dataNormalizer.normalize(eq(updateEvent.before().fields()), any(DatabaseType.class))).thenReturn(normalizedBefore);
        when(dataNormalizer.normalize(eq(updateEvent.after().fields()), any(DatabaseType.class))).thenReturn(normalizedAfter);

        // When
        useCase.execute(updateEvent, DatabaseType.POSTGRESQL);

        // Then
        verify(dataNormalizer, times(2)).normalize(any(), any(DatabaseType.class));
        verify(eventPublisher).publish(any(ChangeEventDto.class));
        verify(offsetRepository).save(updateEvent.position());
    }

    @Test
    @DisplayName("Should process DELETE event successfully")
    void shouldProcessDeleteEventSuccessfully() throws Exception {
        // Given
        ChangeEvent deleteEvent = createDeleteEvent();
        RowData normalizedBefore = new RowData(Map.of("order_id", 123));

        when(dataNormalizer.normalize(eq(deleteEvent.before().fields()), any(DatabaseType.class))).thenReturn(normalizedBefore);

        // When
        useCase.execute(deleteEvent, DatabaseType.POSTGRESQL);

        // Then
        verify(dataNormalizer).normalize(eq(deleteEvent.before().fields()), any(DatabaseType.class));
        verify(eventPublisher).publish(any(ChangeEventDto.class));
        verify(offsetRepository).save(deleteEvent.position());
    }

    @Test
    @DisplayName("Should throw exception when normalization fails")
    void shouldThrowExceptionWhenNormalizationFails() throws Exception {
        // Given
        ChangeEvent event = createInsertEvent();
        when(dataNormalizer.normalize(any(), any(DatabaseType.class)))
                .thenThrow(new RuntimeException("Normalization failed"));

        // When / Then
        assertThatThrownBy(() -> useCase.execute(event, DatabaseType.POSTGRESQL))
                .isInstanceOf(ProcessChangeEventUseCase.ProcessingException.class)
                .hasMessageContaining("Failed to process change event");

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw exception when publishing fails")
    void shouldThrowExceptionWhenPublishingFails() throws Exception {
        // Given
        ChangeEvent event = createInsertEvent();
        RowData normalizedData = new RowData(Map.of("key", "value"));
        when(dataNormalizer.normalize(any(), any(DatabaseType.class))).thenReturn(normalizedData);
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(eventPublisher).publish(any(ChangeEventDto.class));

        // When / Then
        assertThatThrownBy(() -> useCase.execute(event, DatabaseType.POSTGRESQL))
                .isInstanceOf(ProcessChangeEventUseCase.ProcessingException.class)
                .hasMessageContaining("Failed to process change event");
    }

    @Test
    @DisplayName("Should continue processing even if offset save fails")
    void shouldContinueProcessingEvenIfOffsetSaveFails() throws Exception {
        // Given
        ChangeEvent event = createInsertEvent();
        RowData normalizedData = new RowData(Map.of("key", "value"));
        when(dataNormalizer.normalize(any(), any(DatabaseType.class))).thenReturn(normalizedData);
        doThrow(new RuntimeException("Offset save failed"))
                .when(offsetRepository).save(any());

        // When / Then - should not throw exception
        assertThatCode(() -> useCase.execute(event, DatabaseType.POSTGRESQL)).doesNotThrowAnyException();

        // Verify event was still published
        verify(eventPublisher).publish(any(ChangeEventDto.class));
    }

    @Test
    @DisplayName("Should process batch of events successfully")
    void shouldProcessBatchSuccessfully() throws Exception {
        // Given
        List<ChangeEvent> events = List.of(
                createInsertEvent(),
                createUpdateEvent(),
                createDeleteEvent()
        );

        RowData normalizedData = new RowData(Map.of("key", "value"));
        when(dataNormalizer.normalize(any(), any(DatabaseType.class))).thenReturn(normalizedData);

        // When
        ProcessChangeEventUseCase.ProcessingResult result = useCase.executeBatch(events, DatabaseType.POSTGRESQL);

        // Then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(0);
        assertThat(result.totalProcessed()).isEqualTo(3);
        assertThat(result.successRate()).isEqualTo(1.0);

        verify(eventPublisher, times(3)).publish(any(ChangeEventDto.class));
    }

    @Test
    @DisplayName("Should handle partial batch failures")
    void shouldHandlePartialBatchFailures() throws Exception {
        // Given
        List<ChangeEvent> events = List.of(
                createInsertEvent(),
                createUpdateEvent(),
                createDeleteEvent()
        );

        RowData normalizedData = new RowData(Map.of("key", "value"));
        // First event succeeds, second fails, third succeeds
        when(dataNormalizer.normalize(any(), any(DatabaseType.class)))
                .thenReturn(normalizedData)
                .thenThrow(new RuntimeException("Normalization failed"))
                .thenReturn(normalizedData);

        // When
        ProcessChangeEventUseCase.ProcessingResult result = useCase.executeBatch(events, DatabaseType.POSTGRESQL);

        // Then
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.totalProcessed()).isEqualTo(3);
        assertThat(result.successRate()).isCloseTo(0.667, within(0.001));

        verify(eventPublisher, times(2)).publish(any(ChangeEventDto.class));
    }

    @Test
    @DisplayName("Should preserve event metadata during processing")
    void shouldPreserveEventMetadata() throws Exception {
        // Given
        Map<String, Object> metadata = Map.of(
                "source", "debezium",
                "version", "1.0.0"
        );

        ChangeEvent event = new ChangeEvent(
                new TableIdentifier("testdb", "public", "orders"),
                OperationType.INSERT,
                Instant.now(),
                new CdcPosition("test-partition", Map.of("offset", 123)),
                null,
                new RowData(Map.of("order_id", 123)),
                metadata
        );

        RowData normalizedData = new RowData(Map.of("order_id", 123));
        when(dataNormalizer.normalize(any(), any(DatabaseType.class))).thenReturn(normalizedData);

        // When
        useCase.execute(event, DatabaseType.POSTGRESQL);

        // Then
        ArgumentCaptor<ChangeEventDto> eventCaptor = ArgumentCaptor.forClass(ChangeEventDto.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        ChangeEventDto publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.metadata()).isEqualTo(metadata);
    }

    // ==================== Helper Methods ====================

    private ChangeEvent createInsertEvent() {
        return new ChangeEvent(
                new TableIdentifier("testdb", "public", "orders"),
                OperationType.INSERT,
                Instant.now(),
                new CdcPosition("test-partition", Map.of("offset", 100)),
                null, // No 'before' for INSERT
                new RowData(Map.of(
                        "order_id", 123,
                        "status", "PENDING",
                        "amount", 99.99
                )),
                Map.of("source", "test")
        );
    }

    private ChangeEvent createUpdateEvent() {
        return new ChangeEvent(
                new TableIdentifier("testdb", "public", "orders"),
                OperationType.UPDATE,
                Instant.now(),
                new CdcPosition("test-partition", Map.of("offset", 101)),
                new RowData(Map.of("order_id", 123, "status", "PENDING")),
                new RowData(Map.of("order_id", 123, "status", "CONFIRMED")),
                Map.of("source", "test")
        );
    }

    private ChangeEvent createDeleteEvent() {
        return new ChangeEvent(
                new TableIdentifier("testdb", "public", "orders"),
                OperationType.DELETE,
                Instant.now(),
                new CdcPosition("test-partition", Map.of("offset", 102)),
                new RowData(Map.of("order_id", 123)),
                null, // No 'after' for DELETE
                Map.of("source", "test")
        );
    }
}