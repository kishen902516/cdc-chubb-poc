package com.chubb.cdc.debezium.infrastructure.persistence.offset;

import com.chubb.cdc.debezium.domain.changecapture.model.CdcPosition;
import com.chubb.cdc.debezium.domain.changecapture.repository.OffsetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * Adapter that bridges the domain OffsetRepository port to the file-based offset storage implementation.
 *
 * This adapter converts between domain model objects (CdcPosition) and the infrastructure
 * layer's file storage format.
 *
 * Design Pattern: Adapter - Adapts FileOffsetStore to the domain's OffsetRepository interface
 */
@Repository
public class OffsetRepositoryAdapter implements OffsetRepository {

    private static final Logger logger = LoggerFactory.getLogger(OffsetRepositoryAdapter.class);

    private final FileOffsetStore fileOffsetStore;

    /**
     * Creates an OffsetRepositoryAdapter with the specified offset file path.
     *
     * @param offsetFilePath Path to the offset storage file (injected from application.yml)
     */
    public OffsetRepositoryAdapter(
        @Value("${cdc.offset.storage.file.path:data/offsets/cdc-offsets.dat}") String offsetFilePath
    ) {
        this.fileOffsetStore = new FileOffsetStore(offsetFilePath);
        logger.info("OffsetRepositoryAdapter initialized with file path: {}", offsetFilePath);
    }

    /**
     * Constructor for testing with explicit FileOffsetStore.
     *
     * @param fileOffsetStore The file offset store instance
     */
    OffsetRepositoryAdapter(FileOffsetStore fileOffsetStore) {
        this.fileOffsetStore = fileOffsetStore;
        logger.info("OffsetRepositoryAdapter initialized with provided FileOffsetStore");
    }

    /**
     * Saves the CDC position to persistent storage.
     *
     * @param position The CDC position to save
     */
    @Override
    public void save(CdcPosition position) {
        if (position == null) {
            logger.warn("Attempted to save null CdcPosition. Ignoring.");
            return;
        }

        try {
            fileOffsetStore.save(position.sourcePartition(), position.offset());
            logger.debug("Saved CDC position for partition: {}", position.sourcePartition());
        } catch (FileOffsetStore.OffsetStorageException e) {
            logger.error("Failed to save CDC position for partition: {}", position.sourcePartition(), e);
            throw new OffsetRepositoryException("Failed to save CDC position", e);
        }
    }

    /**
     * Loads the CDC position from persistent storage.
     *
     * @param sourcePartition The source partition identifier
     * @return Optional containing the CDC position if found, empty otherwise
     */
    @Override
    public Optional<CdcPosition> load(String sourcePartition) {
        if (sourcePartition == null || sourcePartition.isBlank()) {
            logger.warn("Attempted to load with null or blank source partition. Returning empty.");
            return Optional.empty();
        }

        try {
            Map<String, Object> offsetData = fileOffsetStore.load(sourcePartition);

            if (offsetData == null) {
                logger.info("No offset found for partition: {}", sourcePartition);
                return Optional.empty();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> offset = (Map<String, Object>) offsetData.get("offset");

            CdcPosition position = new CdcPosition(sourcePartition, offset);
            logger.debug("Loaded CDC position for partition: {}", sourcePartition);

            return Optional.of(position);
        } catch (FileOffsetStore.OffsetStorageException e) {
            logger.error("Failed to load CDC position for partition: {}", sourcePartition, e);
            throw new OffsetRepositoryException("Failed to load CDC position", e);
        }
    }

    /**
     * Deletes the CDC position from persistent storage.
     *
     * @param sourcePartition The source partition identifier
     */
    @Override
    public void delete(String sourcePartition) {
        if (sourcePartition == null || sourcePartition.isBlank()) {
            logger.warn("Attempted to delete with null or blank source partition. Ignoring.");
            return;
        }

        try {
            fileOffsetStore.delete(sourcePartition);
            logger.info("Deleted CDC position for partition: {}", sourcePartition);
        } catch (FileOffsetStore.OffsetStorageException e) {
            logger.error("Failed to delete CDC position for partition: {}", sourcePartition, e);
            throw new OffsetRepositoryException("Failed to delete CDC position", e);
        }
    }

    /**
     * Custom exception for offset repository operations.
     */
    public static class OffsetRepositoryException extends RuntimeException {
        public OffsetRepositoryException(String message) {
            super(message);
        }

        public OffsetRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
