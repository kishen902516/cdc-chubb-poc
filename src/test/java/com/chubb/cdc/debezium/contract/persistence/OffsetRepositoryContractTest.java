package com.chubb.cdc.debezium.contract.persistence;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for OffsetRepository implementations.
 *
 * This test ensures that any implementation of OffsetRepository
 * (file-based, database-based, etc.) adheres to the expected contract.
 *
 * TDD: This test is written FIRST and should FAIL until implementation exists.
 */
@DisplayName("OffsetRepository Contract Test")
class OffsetRepositoryContractTest {

    @TempDir
    Path tempDir;

    private OffsetRepository offsetRepository;

    @BeforeEach
    void setUp() {
        // This will fail until we create the FileOffsetStore and OffsetRepositoryAdapter
        // offsetRepository = new OffsetRepositoryAdapter(new FileOffsetStore(tempDir.toString()));

        // TODO: Uncomment above line after implementation (T064, T065)
        throw new UnsupportedOperationException("OffsetRepositoryAdapter not yet implemented - this test should FAIL");
    }

    @Test
    @DisplayName("Should save and load CDC position successfully")
    void shouldSaveAndLoadPosition() {
        // Given
        String sourcePartition = "test-server.test-db";
        CdcPosition position = new CdcPosition(
            sourcePartition,
            Map.of(
                "lsn", "0/12345678",
                "timestamp", 1234567890L
            )
        );

        // When
        offsetRepository.save(position);
        Optional<CdcPosition> loaded = offsetRepository.load(sourcePartition);

        // Then
        assertThat(loaded).isPresent();
        assertThat(loaded.get().sourcePartition()).isEqualTo(sourcePartition);
        assertThat(loaded.get().offset()).containsEntry("lsn", "0/12345678");
        assertThat(loaded.get().offset()).containsEntry("timestamp", 1234567890L);
    }

    @Test
    @DisplayName("Should return empty when loading non-existent partition")
    void shouldReturnEmptyForNonExistentPartition() {
        // When
        Optional<CdcPosition> loaded = offsetRepository.load("non-existent-partition");

        // Then
        assertThat(loaded).isEmpty();
    }

    @Test
    @DisplayName("Should overwrite position when saving same partition twice")
    void shouldOverwritePositionForSamePartition() {
        // Given
        String sourcePartition = "test-server.test-db";
        CdcPosition position1 = new CdcPosition(
            sourcePartition,
            Map.of("lsn", "0/11111111")
        );
        CdcPosition position2 = new CdcPosition(
            sourcePartition,
            Map.of("lsn", "0/22222222")
        );

        // When
        offsetRepository.save(position1);
        offsetRepository.save(position2);
        Optional<CdcPosition> loaded = offsetRepository.load(sourcePartition);

        // Then
        assertThat(loaded).isPresent();
        assertThat(loaded.get().offset()).containsEntry("lsn", "0/22222222");
    }

    @Test
    @DisplayName("Should delete position successfully")
    void shouldDeletePosition() {
        // Given
        String sourcePartition = "test-server.test-db";
        CdcPosition position = new CdcPosition(
            sourcePartition,
            Map.of("lsn", "0/12345678")
        );
        offsetRepository.save(position);

        // When
        offsetRepository.delete(sourcePartition);
        Optional<CdcPosition> loaded = offsetRepository.load(sourcePartition);

        // Then
        assertThat(loaded).isEmpty();
    }

    @Test
    @DisplayName("Should handle multiple partitions independently")
    void shouldHandleMultiplePartitionsIndependently() {
        // Given
        CdcPosition position1 = new CdcPosition(
            "server1.db1",
            Map.of("lsn", "0/11111111")
        );
        CdcPosition position2 = new CdcPosition(
            "server2.db2",
            Map.of("lsn", "0/22222222")
        );

        // When
        offsetRepository.save(position1);
        offsetRepository.save(position2);
        Optional<CdcPosition> loaded1 = offsetRepository.load("server1.db1");
        Optional<CdcPosition> loaded2 = offsetRepository.load("server2.db2");

        // Then
        assertThat(loaded1).isPresent();
        assertThat(loaded1.get().offset()).containsEntry("lsn", "0/11111111");
        assertThat(loaded2).isPresent();
        assertThat(loaded2.get().offset()).containsEntry("lsn", "0/22222222");
    }

    @Test
    @DisplayName("Should persist position across repository instances")
    void shouldPersistAcrossInstances() {
        // Given
        String sourcePartition = "test-server.test-db";
        CdcPosition position = new CdcPosition(
            sourcePartition,
            Map.of("lsn", "0/12345678")
        );

        // When
        offsetRepository.save(position);

        // Create new repository instance pointing to same storage
        // OffsetRepository newRepository = new OffsetRepositoryAdapter(new FileOffsetStore(tempDir.toString()));
        // Optional<CdcPosition> loaded = newRepository.load(sourcePartition);

        // Then
        // assertThat(loaded).isPresent();
        // assertThat(loaded.get().offset()).containsEntry("lsn", "0/12345678");

        // TODO: Uncomment above when implementation exists
    }
}
