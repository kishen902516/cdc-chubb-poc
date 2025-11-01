package com.chubb.cdc.debezium.presentation.rest;

import com.chubb.cdc.debezium.application.dto.HealthStatusDto;
import com.chubb.cdc.debezium.application.usecase.healthmonitoring.CheckHealthUseCase;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.HealthStatus;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.DatabaseHealthCheck;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.KafkaHealthCheck;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.CdcEngineHealthCheck;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.HealthState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for health check endpoints.
 * <p>
 * Provides monitoring endpoints for:
 * - Overall CDC application health
 * - Database connection health
 * - Kafka connection health
 * - CDC engine health
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cdc/health")
@RequiredArgsConstructor
public class HealthController {

    private final CheckHealthUseCase checkHealthUseCase;

    /**
     * GET /cdc/health
     * Get overall CDC application health status.
     *
     * @return HealthStatusDTO with overall health and all component health checks
     */
    @GetMapping
    public ResponseEntity<HealthStatusDTO> getHealth() {
        log.debug("Health check requested");

        try {
            // Execute health check use case
            HealthStatus healthStatus = checkHealthUseCase.execute();

            // Convert to DTO
            HealthStatusDTO dto = mapToHealthStatusDTO(healthStatus);

            log.debug("Health check completed: overall state = {}", dto.overallState());

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Health check failed", e);

            // Return a degraded health status
            HealthStatusDTO degradedDto = HealthStatusDTO.degraded(
                    "Health check failed: " + e.getMessage()
            );

            return ResponseEntity.ok(degradedDto);
        }
    }

    /**
     * GET /cdc/health/database
     * Check database connection health.
     *
     * @return DatabaseHealthDTO or 503 if database is unavailable
     */
    @GetMapping("/database")
    public ResponseEntity<DatabaseHealthDTO> getDatabaseHealth() {
        log.debug("Database health check requested");

        try {
            // Execute health check use case
            HealthStatus healthStatus = checkHealthUseCase.execute();
            DatabaseHealthCheck dbHealth = healthStatus.getDatabaseHealth();

            // Convert to DTO
            DatabaseHealthDTO dto = mapToDatabaseHealthDTO(dbHealth);

            // Return 503 if database is DOWN
            if (dbHealth.state() == HealthState.DOWN) {
                log.warn("Database health check failed: {}", dbHealth.errorMessage().orElse("Unknown error"));
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(dto);
            }

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Database health check failed", e);

            DatabaseHealthDTO errorDto = new DatabaseHealthDTO(
                    "DOWN",
                    "Database health check failed",
                    Instant.now(),
                    null,
                    e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorDto);
        }
    }

    /**
     * GET /cdc/health/kafka
     * Check Kafka connection health.
     *
     * @return KafkaHealthDTO or 503 if Kafka is unavailable
     */
    @GetMapping("/kafka")
    public ResponseEntity<KafkaHealthDTO> getKafkaHealth() {
        log.debug("Kafka health check requested");

        try {
            // Execute health check use case
            HealthStatus healthStatus = checkHealthUseCase.execute();
            KafkaHealthCheck kafkaHealth = healthStatus.getKafkaHealth();

            // Convert to DTO
            KafkaHealthDTO dto = mapToKafkaHealthDTO(kafkaHealth);

            // Return 503 if Kafka is DOWN
            if (kafkaHealth.state() == HealthState.DOWN) {
                log.warn("Kafka health check failed: {}", kafkaHealth.errorMessage().orElse("Unknown error"));
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(dto);
            }

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Kafka health check failed", e);

            KafkaHealthDTO errorDto = new KafkaHealthDTO(
                    "DOWN",
                    "Kafka health check failed",
                    Instant.now(),
                    0,
                    e.getMessage()
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorDto);
        }
    }

    // ==================== Mapping Methods ====================

    private HealthStatusDTO mapToHealthStatusDTO(HealthStatus healthStatus) {
        return new HealthStatusDTO(
                healthStatus.getOverallState().name(),
                mapToDatabaseHealthDTO(healthStatus.getDatabaseHealth()),
                mapToKafkaHealthDTO(healthStatus.getKafkaHealth()),
                mapToEngineHealthDTO(healthStatus.getEngineHealth()),
                healthStatus.getLastChecked()
        );
    }

    private DatabaseHealthDTO mapToDatabaseHealthDTO(DatabaseHealthCheck dbHealth) {
        return new DatabaseHealthDTO(
                dbHealth.state().name(),
                dbHealth.message(),
                dbHealth.checkedAt(),
                dbHealth.connectionTime().map(Object::toString).orElse(null),
                dbHealth.errorMessage().orElse(null)
        );
    }

    private KafkaHealthDTO mapToKafkaHealthDTO(KafkaHealthCheck kafkaHealth) {
        return new KafkaHealthDTO(
                kafkaHealth.state().name(),
                kafkaHealth.message(),
                kafkaHealth.checkedAt(),
                kafkaHealth.availableBrokers(),
                kafkaHealth.errorMessage().orElse(null)
        );
    }

    private CdcEngineHealthDTO mapToEngineHealthDTO(CdcEngineHealthCheck engineHealth) {
        return new CdcEngineHealthDTO(
                engineHealth.state().name(),
                engineHealth.message(),
                engineHealth.checkedAt(),
                engineHealth.isCapturing(),
                engineHealth.monitoredTables(),
                engineHealth.errorMessage().orElse(null)
        );
    }

    // ==================== DTOs ====================

    /**
     * Overall health status DTO matching OpenAPI contract.
     */
    public record HealthStatusDTO(
            String overallState,
            DatabaseHealthDTO databaseHealth,
            KafkaHealthDTO kafkaHealth,
            CdcEngineHealthDTO engineHealth,
            Instant lastChecked
    ) {
        public static HealthStatusDTO degraded(String message) {
            Instant now = Instant.now();
            return new HealthStatusDTO(
                    "DEGRADED",
                    new DatabaseHealthDTO("UNKNOWN", message, now, null, null),
                    new KafkaHealthDTO("UNKNOWN", message, now, 0, null),
                    new CdcEngineHealthDTO("UNKNOWN", message, now, false, 0, null),
                    now
            );
        }
    }

    /**
     * Database health DTO matching OpenAPI contract.
     */
    public record DatabaseHealthDTO(
            String state,
            String message,
            Instant checkedAt,
            String connectionTime,
            String errorMessage
    ) {
    }

    /**
     * Kafka health DTO matching OpenAPI contract.
     */
    public record KafkaHealthDTO(
            String state,
            String message,
            Instant checkedAt,
            int availableBrokers,
            String errorMessage
    ) {
    }

    /**
     * CDC engine health DTO matching OpenAPI contract.
     */
    public record CdcEngineHealthDTO(
            String state,
            String message,
            Instant checkedAt,
            boolean isCapturing,
            int monitoredTables,
            String errorMessage
    ) {
    }
}
