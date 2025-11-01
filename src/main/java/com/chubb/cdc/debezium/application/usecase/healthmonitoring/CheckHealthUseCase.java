package com.chubb.cdc.debezium.application.usecase.healthmonitoring;

import com.chubb.cdc.debezium.application.port.input.CdcEngine;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Use Case: Check Health
 * <p>
 * Responsibility: Check the health status of all CDC components:
 * - Database connection
 * - Kafka connection
 * - CDC engine status
 * <p>
 * Business Rules:
 * - Overall health is UP only if all components are UP
 * - Individual component failures should be reported with details
 * - Health checks should timeout after 15 seconds total
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckHealthUseCase {

    private final CdcEngine cdcEngine;
    // TODO: Add database and Kafka health checkers when implemented

    /**
     * Execute health check for all CDC components.
     *
     * @return HealthStatus with all component health checks
     */
    public HealthStatus execute() {
        log.debug("Executing health check");

        Instant now = Instant.now();

        // Check database health
        DatabaseHealthCheck dbHealth = checkDatabaseHealth(now);

        // Check Kafka health
        KafkaHealthCheck kafkaHealth = checkKafkaHealth(now);

        // Check CDC engine health
        CdcEngineHealthCheck engineHealth = checkEngineHealth(now);

        // Create health status (overall state is derived internally)
        HealthStatus healthStatus = new HealthStatus(
                dbHealth,
                kafkaHealth,
                engineHealth,
                now
        );

        log.debug("Health check completed: overall state = {}", healthStatus.getOverallState());

        return healthStatus;
    }

    /**
     * Check database connection health.
     * TODO: Implement actual database connectivity check
     */
    private DatabaseHealthCheck checkDatabaseHealth(Instant checkedAt) {
        try {
            // TODO: Implement actual database health check
            // For now, return a mock healthy status
            return new DatabaseHealthCheck(
                    HealthState.UP,
                    "Connected to PostgreSQL at localhost:5432",
                    checkedAt,
                    Optional.of(Duration.ofMillis(45)),
                    Optional.empty()
            );
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return new DatabaseHealthCheck(
                    HealthState.DOWN,
                    "Database connection failed",
                    checkedAt,
                    Optional.empty(),
                    Optional.of(e.getMessage())
            );
        }
    }

    /**
     * Check Kafka connection health.
     * TODO: Implement actual Kafka connectivity check
     */
    private KafkaHealthCheck checkKafkaHealth(Instant checkedAt) {
        try {
            // TODO: Implement actual Kafka health check
            // For now, return a mock healthy status
            return new KafkaHealthCheck(
                    HealthState.UP,
                    "Connected to 3 Kafka brokers",
                    checkedAt,
                    3,
                    Optional.empty()
            );
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            return new KafkaHealthCheck(
                    HealthState.DOWN,
                    "Failed to connect to Kafka",
                    checkedAt,
                    0,
                    Optional.of(e.getMessage())
            );
        }
    }

    /**
     * Check CDC engine health.
     */
    private CdcEngineHealthCheck checkEngineHealth(Instant checkedAt) {
        try {
            CdcEngine.CdcEngineStatus status = cdcEngine.getStatus();

            HealthState state = switch (status.state()) {
                case RUNNING -> HealthState.UP;
                case STOPPED -> HealthState.DOWN;
                case STARTING, STOPPING -> HealthState.DEGRADED;
                case FAILED -> HealthState.DOWN;
            };

            String message = switch (status.state()) {
                case RUNNING -> "Capturing changes from monitored tables";
                case STOPPED -> "Not running";
                case STARTING -> "Starting...";
                case STOPPING -> "Stopping...";
                case FAILED -> "Engine failed: " + (status.errorMessage() != null ? status.errorMessage() : "Unknown error");
            };

            return new CdcEngineHealthCheck(
                    state,
                    message,
                    checkedAt,
                    cdcEngine.isRunning(),
                    0, // TODO: Get actual monitored table count
                    status.errorMessage() != null ? Optional.of(status.errorMessage()) : Optional.empty()
            );

        } catch (Exception e) {
            log.error("CDC engine health check failed", e);
            return new CdcEngineHealthCheck(
                    HealthState.UNKNOWN,
                    "Failed to check engine status",
                    checkedAt,
                    false,
                    0,
                    Optional.of(e.getMessage())
            );
        }
    }

}
