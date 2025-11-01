package com.chubb.cdc.debezium.unit.domain.changecapture;

import com.chubb.cdc.debezium.domain.changecapture.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ChangeEvent domain model.
 *
 * <p>Tests the invariants and validation rules for change events:</p>
 * <ul>
 *   <li>INSERT: before=null, after!=null</li>
 *   <li>UPDATE: before!=null, after!=null</li>
 *   <li>DELETE: before!=null, after=null</li>
 *   <li>Invalid combinations throw IllegalArgumentException</li>
 * </ul>
 */
@DisplayName("ChangeEvent Domain Model Tests")
class ChangeEventTest {

    private static final TableIdentifier TEST_TABLE =
        new TableIdentifier("testdb", "public", "orders");

    private static final CdcPosition TEST_POSITION =
        new CdcPosition("server1-testdb", Map.of("lsn", "0/12345678"));

    private static final RowData TEST_ROW_BEFORE =
        new RowData(Map.of("id", 1, "status", "PENDING"));

    private static final RowData TEST_ROW_AFTER =
        new RowData(Map.of("id", 1, "status", "CONFIRMED"));

    @Test
    @DisplayName("INSERT event should have null before and non-null after")
    void testInsertEventValidation() {
        // Given
        ChangeEvent insertEvent = new ChangeEvent(
            TEST_TABLE,
            OperationType.INSERT,
            Instant.now(),
            TEST_POSITION,
            null,  // before must be null
            TEST_ROW_AFTER,
            Map.of("source", "test")
        );

        // Then
        assertThat(insertEvent.isInsert()).isTrue();
        assertThat(insertEvent.before()).isNull();
        assertThat(insertEvent.after()).isNotNull();
    }

    @Test
    @DisplayName("INSERT event with non-null before should throw exception")
    void testInsertEventWithBeforeThrowsException() {
        // When / Then
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.INSERT,
                Instant.now(),
                TEST_POSITION,
                TEST_ROW_BEFORE,  // Invalid: before should be null
                TEST_ROW_AFTER,
                Map.of()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INSERT operation must have null 'before' data");
    }

    @Test
    @DisplayName("INSERT event with null after should throw exception")
    void testInsertEventWithoutAfterThrowsException() {
        // When / Then
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.INSERT,
                Instant.now(),
                TEST_POSITION,
                null,
                null,  // Invalid: after should not be null
                Map.of()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INSERT operation must have non-null 'after' data");
    }

    @Test
    @DisplayName("UPDATE event should have both before and after")
    void testUpdateEventValidation() {
        // Given
        ChangeEvent updateEvent = new ChangeEvent(
            TEST_TABLE,
            OperationType.UPDATE,
            Instant.now(),
            TEST_POSITION,
            TEST_ROW_BEFORE,
            TEST_ROW_AFTER,
            Map.of("source", "test")
        );

        // Then
        assertThat(updateEvent.isUpdate()).isTrue();
        assertThat(updateEvent.before()).isNotNull();
        assertThat(updateEvent.after()).isNotNull();
    }

    @Test
    @DisplayName("UPDATE event with null before should throw exception")
    void testUpdateEventWithoutBeforeThrowsException() {
        // When / Then
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.UPDATE,
                Instant.now(),
                TEST_POSITION,
                null,  // Invalid: before should not be null
                TEST_ROW_AFTER,
                Map.of()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UPDATE operation must have non-null 'before' data");
    }

    @Test
    @DisplayName("UPDATE event with null after should throw exception")
    void testUpdateEventWithoutAfterThrowsException() {
        // When / Then
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.UPDATE,
                Instant.now(),
                TEST_POSITION,
                TEST_ROW_BEFORE,
                null,  // Invalid: after should not be null
                Map.of()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UPDATE operation must have non-null 'after' data");
    }

    @Test
    @DisplayName("DELETE event should have non-null before and null after")
    void testDeleteEventValidation() {
        // Given
        ChangeEvent deleteEvent = new ChangeEvent(
            TEST_TABLE,
            OperationType.DELETE,
            Instant.now(),
            TEST_POSITION,
            TEST_ROW_BEFORE,
            null,  // after must be null
            Map.of("source", "test")
        );

        // Then
        assertThat(deleteEvent.isDelete()).isTrue();
        assertThat(deleteEvent.before()).isNotNull();
        assertThat(deleteEvent.after()).isNull();
    }

    @Test
    @DisplayName("DELETE event with null before should throw exception")
    void testDeleteEventWithoutBeforeThrowsException() {
        // When / Then
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.DELETE,
                Instant.now(),
                TEST_POSITION,
                null,  // Invalid: before should not be null
                null,
                Map.of()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DELETE operation must have non-null 'before' data");
    }

    @Test
    @DisplayName("DELETE event with non-null after should throw exception")
    void testDeleteEventWithAfterThrowsException() {
        // When / Then
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.DELETE,
                Instant.now(),
                TEST_POSITION,
                TEST_ROW_BEFORE,
                TEST_ROW_AFTER,  // Invalid: after should be null
                Map.of()
            )
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DELETE operation must have null 'after' data");
    }

    @Test
    @DisplayName("ChangeEvent should reject null required fields")
    void testChangeEventNullValidation() {
        // Test null table
        assertThatThrownBy(() ->
            new ChangeEvent(
                null,
                OperationType.INSERT,
                Instant.now(),
                TEST_POSITION,
                null,
                TEST_ROW_AFTER,
                Map.of()
            )
        )
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Table identifier cannot be null");

        // Test null operation
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                null,
                Instant.now(),
                TEST_POSITION,
                null,
                TEST_ROW_AFTER,
                Map.of()
            )
        )
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Operation type cannot be null");

        // Test null timestamp
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.INSERT,
                null,
                TEST_POSITION,
                null,
                TEST_ROW_AFTER,
                Map.of()
            )
        )
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Timestamp cannot be null");

        // Test null position
        assertThatThrownBy(() ->
            new ChangeEvent(
                TEST_TABLE,
                OperationType.INSERT,
                Instant.now(),
                null,
                null,
                TEST_ROW_AFTER,
                Map.of()
            )
        )
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("CDC position cannot be null");
    }

    @Test
    @DisplayName("ChangeEvent builder should create valid events")
    void testChangeEventBuilder() {
        // Given
        Instant timestamp = Instant.now();

        // When
        ChangeEvent event = ChangeEvent.builder()
            .table(TEST_TABLE)
            .operation(OperationType.UPDATE)
            .timestamp(timestamp)
            .position(TEST_POSITION)
            .before(TEST_ROW_BEFORE)
            .after(TEST_ROW_AFTER)
            .metadata(Map.of("source", "builder-test"))
            .build();

        // Then
        assertThat(event.table()).isEqualTo(TEST_TABLE);
        assertThat(event.operation()).isEqualTo(OperationType.UPDATE);
        assertThat(event.timestamp()).isEqualTo(timestamp);
        assertThat(event.position()).isEqualTo(TEST_POSITION);
        assertThat(event.before()).isEqualTo(TEST_ROW_BEFORE);
        assertThat(event.after()).isEqualTo(TEST_ROW_AFTER);
        assertThat(event.getMetadata("source")).isEqualTo("builder-test");
    }

    @Test
    @DisplayName("ChangeEvent metadata should be immutable")
    void testChangeEventMetadataImmutability() {
        // Given
        Map<String, Object> mutableMetadata = new java.util.HashMap<>();
        mutableMetadata.put("key", "value");

        ChangeEvent event = new ChangeEvent(
            TEST_TABLE,
            OperationType.INSERT,
            Instant.now(),
            TEST_POSITION,
            null,
            TEST_ROW_AFTER,
            mutableMetadata
        );

        // When - try to modify original metadata
        mutableMetadata.put("newKey", "newValue");

        // Then - event metadata should not be affected
        assertThat(event.metadata()).hasSize(1);
        assertThat(event.metadata()).containsKey("key");
        assertThat(event.metadata()).doesNotContainKey("newKey");
    }
}
