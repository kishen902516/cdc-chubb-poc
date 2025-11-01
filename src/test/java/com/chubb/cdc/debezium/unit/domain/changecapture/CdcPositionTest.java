package com.chubb.cdc.debezium.unit.domain.changecapture;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CdcPosition domain model.
 *
 * <p>Tests position comparison logic to ensure proper ordering
 * for recovery and position tracking.</p>
 */
@DisplayName("CdcPosition Domain Model Tests")
class CdcPositionTest {

    @Test
    @DisplayName("CdcPosition should implement compareTo correctly")
    void testCdcPositionComparison() {
        // Given - positions with different LSN values (PostgreSQL example)
        CdcPosition position1 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/10000000", "timestamp", 1000L)
        );

        CdcPosition position2 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/20000000", "timestamp", 2000L)
        );

        CdcPosition position3 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/20000000", "timestamp", 2000L)
        );

        // When / Then - comparison based on timestamp
        assertThat(position1.compareTo(position2))
            .as("Position1 should be before position2")
            .isLessThan(0);

        assertThat(position2.compareTo(position1))
            .as("Position2 should be after position1")
            .isGreaterThan(0);

        assertThat(position2.compareTo(position3))
            .as("Position2 should equal position3")
            .isEqualTo(0);
    }

    @Test
    @DisplayName("isAfter should return true for later positions")
    void testIsAfter() {
        // Given
        CdcPosition earlier = new CdcPosition(
            "server1-db1",
            Map.of("timestamp", 1000L)
        );

        CdcPosition later = new CdcPosition(
            "server1-db1",
            Map.of("timestamp", 2000L)
        );

        // When / Then
        assertThat(later.isAfter(earlier)).isTrue();
        assertThat(earlier.isAfter(later)).isFalse();
        assertThat(later.isAfter(later)).isFalse();
    }

    @Test
    @DisplayName("isBefore should return true for earlier positions")
    void testIsBefore() {
        // Given
        CdcPosition earlier = new CdcPosition(
            "server1-db1",
            Map.of("timestamp", 1000L)
        );

        CdcPosition later = new CdcPosition(
            "server1-db1",
            Map.of("timestamp", 2000L)
        );

        // When / Then
        assertThat(earlier.isBefore(later)).isTrue();
        assertThat(later.isBefore(earlier)).isFalse();
        assertThat(earlier.isBefore(earlier)).isFalse();
    }

    @Test
    @DisplayName("Positions from different partitions should be comparable")
    void testPositionsFromDifferentPartitions() {
        // Given - different source partitions
        CdcPosition partition1 = new CdcPosition(
            "server1-db1",
            Map.of("timestamp", 1000L)
        );

        CdcPosition partition2 = new CdcPosition(
            "server2-db2",
            Map.of("timestamp", 2000L)
        );

        // When / Then - comparison should still work based on timestamp
        assertThat(partition1.compareTo(partition2))
            .as("Should compare by timestamp even for different partitions")
            .isLessThan(0);

        assertThat(partition2.isAfter(partition1)).isTrue();
    }

    @Test
    @DisplayName("Positions without timestamp should handle comparison gracefully")
    void testPositionsWithoutTimestamp() {
        // Given - positions with only LSN (no timestamp)
        CdcPosition pos1 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/10000000")
        );

        CdcPosition pos2 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/20000000")
        );

        // When / Then - comparison should fall back to other fields or natural ordering
        assertThat(pos1.compareTo(pos2))
            .as("Should compare even without explicit timestamp")
            .isNotNull();
    }

    @Test
    @DisplayName("Position equality should be based on content")
    void testPositionEquality() {
        // Given
        CdcPosition pos1 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/12345678", "timestamp", 1000L)
        );

        CdcPosition pos2 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/12345678", "timestamp", 1000L)
        );

        CdcPosition pos3 = new CdcPosition(
            "server1-db1",
            Map.of("lsn", "0/99999999", "timestamp", 1000L)
        );

        // When / Then
        assertThat(pos1).isEqualTo(pos2);
        assertThat(pos1).isNotEqualTo(pos3);
        assertThat(pos1.hashCode()).isEqualTo(pos2.hashCode());
    }

    @Test
    @DisplayName("CdcPosition should create immutable offset map")
    void testPositionOffsetImmutability() {
        // Given
        Map<String, Object> mutableOffset = new java.util.HashMap<>();
        mutableOffset.put("lsn", "0/12345678");

        CdcPosition position = new CdcPosition("server1-db1", mutableOffset);

        // When - try to modify original offset
        mutableOffset.put("newKey", "newValue");

        // Then - position offset should not be affected
        assertThat(position.offset()).hasSize(1);
        assertThat(position.offset()).containsKey("lsn");
        assertThat(position.offset()).doesNotContainKey("newKey");
    }

    @Test
    @DisplayName("MySQL binlog positions should be comparable")
    void testMySqlBinlogPositions() {
        // Given - MySQL binlog positions
        CdcPosition pos1 = new CdcPosition(
            "mysql-server1",
            Map.of(
                "file", "mysql-bin.000001",
                "pos", 1000L,
                "timestamp", 1000L
            )
        );

        CdcPosition pos2 = new CdcPosition(
            "mysql-server1",
            Map.of(
                "file", "mysql-bin.000001",
                "pos", 2000L,
                "timestamp", 2000L
            )
        );

        // When / Then
        assertThat(pos1.isBefore(pos2)).isTrue();
        assertThat(pos2.isAfter(pos1)).isTrue();
    }

    @Test
    @DisplayName("Oracle SCN positions should be comparable")
    void testOracleScnPositions() {
        // Given - Oracle SCN (System Change Number) positions
        CdcPosition pos1 = new CdcPosition(
            "oracle-server1",
            Map.of("scn", 123456L, "timestamp", 1000L)
        );

        CdcPosition pos2 = new CdcPosition(
            "oracle-server1",
            Map.of("scn", 789012L, "timestamp", 2000L)
        );

        // When / Then
        assertThat(pos1.isBefore(pos2)).isTrue();
        assertThat(pos2.isAfter(pos1)).isTrue();
    }
}
