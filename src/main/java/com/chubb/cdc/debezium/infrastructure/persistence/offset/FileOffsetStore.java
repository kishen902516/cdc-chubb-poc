package com.chubb.cdc.debezium.infrastructure.persistence.offset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based storage for CDC offsets.
 *
 * This class provides thread-safe file I/O operations for persisting and loading CDC position offsets.
 * Offsets are stored in JSON format with the structure:
 * {
 *   "sourcePartition": "...",
 *   "offset": { ... }
 * }
 *
 * The implementation uses read-write locks to ensure thread safety for concurrent access.
 * File writes are atomic using a temporary file and rename strategy.
 *
 * Design Pattern: Repository (infrastructure implementation)
 */
public class FileOffsetStore {

    private static final Logger logger = LoggerFactory.getLogger(FileOffsetStore.class);

    private final Path offsetFilePath;
    private final ObjectMapper objectMapper;
    private final ReadWriteLock lock;

    /**
     * Creates a FileOffsetStore with the specified file path.
     *
     * @param offsetFilePath Path to the offset storage file
     */
    public FileOffsetStore(String offsetFilePath) {
        this.offsetFilePath = Paths.get(offsetFilePath);
        this.objectMapper = createObjectMapper();
        this.lock = new ReentrantReadWriteLock();

        ensureDirectoryExists();
        logger.info("FileOffsetStore initialized with path: {}", this.offsetFilePath.toAbsolutePath());
    }

    /**
     * Saves offset data to the file.
     *
     * @param sourcePartition The source partition identifier
     * @param offset The offset data as a map
     * @throws OffsetStorageException if save operation fails
     */
    public void save(String sourcePartition, Map<String, Object> offset) {
        lock.writeLock().lock();
        try {
            Map<String, Object> offsetData = new HashMap<>();
            offsetData.put("sourcePartition", sourcePartition);
            offsetData.put("offset", offset);

            // Write to temporary file first (atomic write)
            Path tempFile = offsetFilePath.getParent().resolve(offsetFilePath.getFileName() + ".tmp");
            objectMapper.writeValue(tempFile.toFile(), offsetData);

            // Atomic rename
            Files.move(tempFile, offsetFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            logger.debug("Offset saved successfully for partition: {}", sourcePartition);
        } catch (IOException e) {
            String errorMsg = String.format("Failed to save offset for partition %s to %s",
                sourcePartition, offsetFilePath);
            logger.error(errorMsg, e);
            throw new OffsetStorageException(errorMsg, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Loads offset data from the file.
     *
     * @param sourcePartition The source partition identifier to load
     * @return Map containing sourcePartition and offset data, or null if not found
     * @throws OffsetStorageException if load operation fails
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> load(String sourcePartition) {
        lock.readLock().lock();
        try {
            if (!Files.exists(offsetFilePath)) {
                logger.info("Offset file does not exist: {}. Starting from beginning.", offsetFilePath);
                return null;
            }

            Map<String, Object> offsetData = objectMapper.readValue(offsetFilePath.toFile(), Map.class);

            // Verify the partition matches
            String storedPartition = (String) offsetData.get("sourcePartition");
            if (!sourcePartition.equals(storedPartition)) {
                logger.warn("Requested partition {} does not match stored partition {}. Returning null.",
                    sourcePartition, storedPartition);
                return null;
            }

            logger.debug("Offset loaded successfully for partition: {}", sourcePartition);
            return offsetData;
        } catch (IOException e) {
            String errorMsg = String.format("Failed to load offset for partition %s from %s",
                sourcePartition, offsetFilePath);
            logger.error(errorMsg, e);
            throw new OffsetStorageException(errorMsg, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Deletes the offset file for the specified partition.
     *
     * @param sourcePartition The source partition identifier
     * @throws OffsetStorageException if delete operation fails
     */
    public void delete(String sourcePartition) {
        lock.writeLock().lock();
        try {
            if (Files.exists(offsetFilePath)) {
                Files.delete(offsetFilePath);
                logger.info("Offset file deleted for partition: {}", sourcePartition);
            } else {
                logger.debug("Offset file does not exist, nothing to delete for partition: {}", sourcePartition);
            }
        } catch (IOException e) {
            String errorMsg = String.format("Failed to delete offset for partition %s at %s",
                sourcePartition, offsetFilePath);
            logger.error(errorMsg, e);
            throw new OffsetStorageException(errorMsg, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if an offset exists for the specified partition.
     *
     * @param sourcePartition The source partition identifier
     * @return true if offset exists, false otherwise
     */
    public boolean exists(String sourcePartition) {
        lock.readLock().lock();
        try {
            if (!Files.exists(offsetFilePath)) {
                return false;
            }

            Map<String, Object> offsetData = load(sourcePartition);
            return offsetData != null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the path to the offset file.
     *
     * @return The absolute path to the offset file
     */
    public Path getOffsetFilePath() {
        return offsetFilePath.toAbsolutePath();
    }

    /**
     * Ensures the parent directory exists for the offset file.
     */
    private void ensureDirectoryExists() {
        try {
            Path parentDir = offsetFilePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("Created offset storage directory: {}", parentDir.toAbsolutePath());
            }
        } catch (IOException e) {
            String errorMsg = String.format("Failed to create offset storage directory at %s",
                offsetFilePath.getParent());
            logger.error(errorMsg, e);
            throw new OffsetStorageException(errorMsg, e);
        }
    }

    /**
     * Creates and configures the ObjectMapper for JSON serialization.
     */
    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Custom exception for offset storage operations.
     */
    public static class OffsetStorageException extends RuntimeException {
        public OffsetStorageException(String message) {
            super(message);
        }

        public OffsetStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
