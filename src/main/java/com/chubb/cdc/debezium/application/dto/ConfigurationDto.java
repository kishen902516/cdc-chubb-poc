package com.chubb.cdc.debezium.application.dto;

import com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.model.DatabaseType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for configuration status.
 *
 * <p>Provides a read-only view of the current CDC configuration status,
 * suitable for REST API responses. Excludes sensitive information like
 * database passwords.</p>
 */
public record ConfigurationDto(
    @JsonProperty("loadedAt")
    Instant loadedAt,

    @JsonProperty("databaseType")
    DatabaseType databaseType,

    @JsonProperty("databaseHost")
    String databaseHost,

    @JsonProperty("databaseName")
    String databaseName,

    @JsonProperty("monitoredTables")
    List<String> monitoredTables,

    @JsonProperty("kafkaBrokers")
    List<String> kafkaBrokers,

    @JsonProperty("kafkaTopicPattern")
    String kafkaTopicPattern,

    @JsonProperty("status")
    ConfigurationStatus status
) {
    /**
     * Converts a domain ConfigurationAggregate to a DTO.
     *
     * @param config the domain configuration aggregate
     * @param status the current status
     * @return the DTO representation
     */
    public static ConfigurationDto fromDomain(
        ConfigurationAggregate config,
        ConfigurationStatus status
    ) {
        return new ConfigurationDto(
            config.getLoadedAt(),
            config.getDatabaseConfig().getType(),
            config.getDatabaseConfig().getHost(),
            config.getDatabaseConfig().getDatabase(),
            config.getTableConfigs().stream()
                .map(tc -> tc.table().fullyQualifiedName())
                .sorted()
                .toList(),
            config.getKafkaConfig().brokerAddresses(),
            config.getKafkaConfig().topicNamePattern(),
            status
        );
    }

    /**
     * Configuration status enumeration.
     */
    public enum ConfigurationStatus {
        VALID,
        INVALID,
        NOT_LOADED
    }
}
