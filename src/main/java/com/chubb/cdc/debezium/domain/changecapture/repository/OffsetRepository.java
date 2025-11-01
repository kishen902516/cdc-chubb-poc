package com.chubb.cdc.debezium.domain.changecapture.repository;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;

import java.util.Optional;

/**
 * Repository port for CDC offset persistence.
 *
 * <p>Manages storage and retrieval of CDC positions to enable recovery and resumption
 * after restarts or failures.</p>
 *
 * <p>This is a domain port (interface) following the Hexagonal Architecture pattern.
 * Infrastructure adapters will provide concrete implementations.</p>
 */
public interface OffsetRepository {

    /**
     * Saves a CDC position for later recovery.
     *
     * @param position the CDC position to save
     * @throws IllegalArgumentException if position is null
     */
    void save(CdcPosition position);

    /**
     * Loads the last saved CDC position for a source partition.
     *
     * @param sourcePartition the source partition identifier
     * @return the last saved position, or empty if no position has been saved
     * @throws IllegalArgumentException if sourcePartition is null or blank
     */
    Optional<CdcPosition> load(String sourcePartition);

    /**
     * Deletes the saved CDC position for a source partition.
     *
     * <p>Used when a source is removed from monitoring or when resetting capture state.</p>
     *
     * @param sourcePartition the source partition identifier
     * @throws IllegalArgumentException if sourcePartition is null or blank
     */
    void delete(String sourcePartition);
}
