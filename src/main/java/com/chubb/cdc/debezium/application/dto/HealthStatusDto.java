package com.chubb.cdc.debezium.application.dto;

import com.chubb.cdc.debezium.domain.healthmonitoring.model.HealthState;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.HealthStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;

/**
 * Data Transfer Object for health status.
 *
 * <p>Maps from the domain HealthStatus entity to a serializable format
 * suitable for REST API responses. Includes all component health checks
 * and overall system status.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthStatusDto(
    @JsonProperty("overallState")
    HealthState overallState,

    @JsonProperty("lastChecked")
    Instant lastChecked,

    @JsonProperty("database")
    ComponentHealthDto database,

    @JsonProperty("kafka")
    ComponentHealthDto kafka,

    @JsonProperty("cdcEngine")
    ComponentHealthDto cdcEngine
) {
    /**
     * Converts a domain HealthStatus to a DTO.
     *
     * @param healthStatus the domain health status
     * @return the DTO representation
     */
    public static HealthStatusDto fromDomain(HealthStatus healthStatus) {
        return new HealthStatusDto(
            healthStatus.getOverallState(),
            healthStatus.getLastChecked(),
            ComponentHealthDto.fromDatabaseHealth(healthStatus.getDatabaseHealth()),
            ComponentHealthDto.fromKafkaHealth(healthStatus.getKafkaHealth()),
            ComponentHealthDto.fromEngineHealth(healthStatus.getEngineHealth())
        );
    }

    /**
     * DTO for individual component health.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ComponentHealthDto(
        @JsonProperty("state")
        HealthState state,

        @JsonProperty("message")
        String message,

        @JsonProperty("checkedAt")
        Instant checkedAt,

        @JsonProperty("connectionTime")
        Long connectionTimeMs,

        @JsonProperty("availableBrokers")
        Integer availableBrokers,

        @JsonProperty("isCapturing")
        Boolean isCapturing,

        @JsonProperty("monitoredTables")
        Integer monitoredTables,

        @JsonProperty("errorMessage")
        String errorMessage
    ) {
        /**
         * Creates a DTO from a DatabaseHealthCheck.
         */
        public static ComponentHealthDto fromDatabaseHealth(
            com.chubb.cdc.debezium.domain.healthmonitoring.model.DatabaseHealthCheck health
        ) {
            return new ComponentHealthDto(
                health.state(),
                health.message(),
                health.checkedAt(),
                health.connectionTime().map(Duration::toMillis).orElse(null),
                null,
                null,
                null,
                health.errorMessage().orElse(null)
            );
        }

        /**
         * Creates a DTO from a KafkaHealthCheck.
         */
        public static ComponentHealthDto fromKafkaHealth(
            com.chubb.cdc.debezium.domain.healthmonitoring.model.KafkaHealthCheck health
        ) {
            return new ComponentHealthDto(
                health.state(),
                health.message(),
                health.checkedAt(),
                null,
                health.availableBrokers(),
                null,
                null,
                health.errorMessage().orElse(null)
            );
        }

        /**
         * Creates a DTO from a CdcEngineHealthCheck.
         */
        public static ComponentHealthDto fromEngineHealth(
            com.chubb.cdc.debezium.domain.healthmonitoring.model.CdcEngineHealthCheck health
        ) {
            return new ComponentHealthDto(
                health.state(),
                health.message(),
                health.checkedAt(),
                null,
                null,
                health.isCapturing(),
                health.monitoredTables(),
                health.errorMessage().orElse(null)
            );
        }
    }
}
