package com.chubb.cdc.debezium.unit.domain.configuration;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.configuration.model.CompositeUniqueKey;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TableConfig domain model.
 *
 * <p>Tests table configuration validation including composite key validation.</p>
 */
@DisplayName("TableConfig Domain Model Tests")
class TableConfigTest {

    private static final TableIdentifier TEST_TABLE =
        new TableIdentifier("testdb", "public", "orders");

    @Test
    @DisplayName("TableConfig should accept valid configuration with all columns")
    void testValidTableConfigWithAllColumns() {
        // When
        TableConfig config = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of(),
            Optional.empty()
        );

        // Then
        assertThat(config.table()).isEqualTo(TEST_TABLE);
        assertThat(config.includeMode()).isEqualTo(TableConfig.IncludeMode.INCLUDE_ALL);
        assertThat(config.columnFilter()).isEmpty();
        assertThat(config.compositeKey()).isEmpty();
    }

    @Test
    @DisplayName("TableConfig should accept valid configuration with column filter")
    void testValidTableConfigWithColumnFilter() {
        // When
        TableConfig config = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of("id", "status", "created_at"),
            Optional.empty()
        );

        // Then
        assertThat(config.columnFilter()).hasSize(3);
        assertThat(config.columnFilter()).contains("id", "status", "created_at");
    }

    @Test
    @DisplayName("TableConfig should accept composite unique key")
    void testValidTableConfigWithCompositeKey() {
        // Given
        CompositeUniqueKey compositeKey = new CompositeUniqueKey(
            List.of("email", "registration_date")
        );

        // When
        TableConfig config = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of(),
            Optional.of(compositeKey)
        );

        // Then
        assertThat(config.compositeKey()).isPresent();
        assertThat(config.compositeKey().get().columnNames()).hasSize(2);
        assertThat(config.compositeKey().get().columnNames())
            .containsExactly("email", "registration_date");
    }

    @Test
    @DisplayName("TableConfig should be immutable")
    void testTableConfigImmutability() {
        // Given
        Set<String> mutableColumns = new java.util.HashSet<>();
        mutableColumns.add("id");

        TableConfig config = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            mutableColumns,
            Optional.empty()
        );

        // When - try to modify original set
        mutableColumns.add("status");

        // Then - config should not be affected
        assertThat(config.columnFilter()).hasSize(1);
        assertThat(config.columnFilter()).containsOnly("id");
    }

    @Test
    @DisplayName("CompositeUniqueKey should validate at least one column")
    void testCompositeKeyMinimumColumns() {
        // When / Then - empty list should throw exception
        assertThatThrownBy(() ->
            new CompositeUniqueKey(List.of())
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one column");
    }

    @Test
    @DisplayName("CompositeUniqueKey should validate no duplicate columns")
    void testCompositeKeyNoDuplicates() {
        // When / Then - duplicate columns should throw exception
        assertThatThrownBy(() ->
            new CompositeUniqueKey(List.of("email", "email"))
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
    }

    @Test
    @DisplayName("CompositeUniqueKey should accept single column")
    void testCompositeKeySingleColumn() {
        // When
        CompositeUniqueKey key = new CompositeUniqueKey(List.of("email"));

        // Then
        assertThat(key.columnNames()).hasSize(1);
        assertThat(key.columnNames()).containsExactly("email");
    }

    @Test
    @DisplayName("CompositeUniqueKey should accept multiple columns")
    void testCompositeKeyMultipleColumns() {
        // When
        CompositeUniqueKey key = new CompositeUniqueKey(
            List.of("region_id", "customer_id", "order_date")
        );

        // Then
        assertThat(key.columnNames()).hasSize(3);
        assertThat(key.columnNames())
            .containsExactly("region_id", "customer_id", "order_date");
    }

    @Test
    @DisplayName("CompositeUniqueKey should preserve column order")
    void testCompositeKeyColumnOrder() {
        // Given
        List<String> columns = List.of("first", "second", "third");

        // When
        CompositeUniqueKey key = new CompositeUniqueKey(columns);

        // Then
        assertThat(key.columnNames()).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("TableConfig equality should be based on content")
    void testTableConfigEquality() {
        // Given
        TableConfig config1 = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of("id", "status"),
            Optional.empty()
        );

        TableConfig config2 = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of("id", "status"),
            Optional.empty()
        );

        TableConfig config3 = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.INCLUDE_ALL,
            Set.of("id"),
            Optional.empty()
        );

        // When / Then
        assertThat(config1).isEqualTo(config2);
        assertThat(config1).isNotEqualTo(config3);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    @DisplayName("TableConfig should support EXCLUDE_SPECIFIED mode")
    void testTableConfigExcludeMode() {
        // When
        TableConfig config = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.EXCLUDE_SPECIFIED,
            Set.of("internal_column", "audit_column"),
            Optional.empty()
        );

        // Then
        assertThat(config.includeMode()).isEqualTo(TableConfig.IncludeMode.EXCLUDE_SPECIFIED);
        assertThat(config.columnFilter()).contains("internal_column", "audit_column");
    }

    @Test
    @DisplayName("TableConfig with composite key should handle all fields correctly")
    void testTableConfigWithAllFields() {
        // Given
        CompositeUniqueKey compositeKey = new CompositeUniqueKey(
            List.of("tenant_id", "user_id")
        );

        // When
        TableConfig config = new TableConfig(
            TEST_TABLE,
            TableConfig.IncludeMode.EXCLUDE_SPECIFIED,
            Set.of("password_hash", "secret_key"),
            Optional.of(compositeKey)
        );

        // Then
        assertThat(config.table()).isEqualTo(TEST_TABLE);
        assertThat(config.includeMode()).isEqualTo(TableConfig.IncludeMode.EXCLUDE_SPECIFIED);
        assertThat(config.columnFilter()).hasSize(2);
        assertThat(config.compositeKey()).isPresent();
        assertThat(config.compositeKey().get().columnNames()).hasSize(2);
    }
}
