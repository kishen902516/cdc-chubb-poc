package com.chubb.cdc.debezium.unit.domain.healthmonitoring;

import com.chubb.cdc.debezium.domain.healthmonitoring.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HealthStatus domain model.
 *
 * <p>Tests health state transitions and overall state derivation logic.</p>
 */
@DisplayName("HealthStatus Domain Model Tests")
class HealthStatusTest {

    @Test
    @DisplayName("getOverallState should return UP when all components are UP")
    void testOverallStateAllUp() {
        // Given
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.UP,
            "Database connection healthy",
            Instant.now(),
            Optional.of(Duration.ofMillis(50)),
            Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.UP,
            "Kafka connection healthy",
            Instant.now(),
            3,
            Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UP,
            "CDC engine running",
            Instant.now(),
            true,
            2,
            Optional.empty()
        );

        HealthStatus healthStatus = new HealthStatus(
            dbHealth,
            kafkaHealth,
            engineHealth,
            Instant.now()
        );

        // When
        HealthState overallState = healthStatus.getOverallState();

        // Then
        assertThat(overallState).isEqualTo(HealthState.UP);
        assertThat(healthStatus.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("getOverallState should return DOWN when any component is DOWN")
    void testOverallStateAnyDown() {
        // Given - Database is DOWN
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.DOWN,
            "Database connection failed",
            Instant.now(),
            Optional.empty(),
            Optional.of("Connection timeout")
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.UP,
            "Kafka connection healthy",
            Instant.now(),
            3,
            Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UP,
            "CDC engine running",
            Instant.now(),
            true,
            2,
            Optional.empty()
        );

        HealthStatus healthStatus = new HealthStatus(
            dbHealth,
            kafkaHealth,
            engineHealth,
            Instant.now()
        );

        // When
        HealthState overallState = healthStatus.getOverallState();

        // Then
        assertThat(overallState).isEqualTo(HealthState.DOWN);
        assertThat(healthStatus.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("getOverallState should return DEGRADED when any component is DEGRADED")
    void testOverallStateDegraded() {
        // Given - Kafka is DEGRADED
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.UP,
            "Database connection healthy",
            Instant.now(),
            Optional.of(Duration.ofMillis(50)),
            Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.DEGRADED,
            "Only 1 of 3 brokers available",
            Instant.now(),
            1,
            Optional.of("2 brokers unreachable")
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UP,
            "CDC engine running",
            Instant.now(),
            true,
            2,
            Optional.empty()
        );

        HealthStatus healthStatus = new HealthStatus(
            dbHealth,
            kafkaHealth,
            engineHealth,
            Instant.now()
        );

        // When
        HealthState overallState = healthStatus.getOverallState();

        // Then
        assertThat(overallState).isEqualTo(HealthState.DEGRADED);
        assertThat(healthStatus.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("getOverallState should return UNKNOWN when any component is UNKNOWN")
    void testOverallStateUnknown() {
        // Given - Engine state is UNKNOWN
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.UP,
            "Database connection healthy",
            Instant.now(),
            Optional.of(Duration.ofMillis(50)),
            Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.UP,
            "Kafka connection healthy",
            Instant.now(),
            3,
            Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UNKNOWN,
            "CDC engine state unknown",
            Instant.now(),
            false,
            0,
            Optional.of("Engine not initialized")
        );

        HealthStatus healthStatus = new HealthStatus(
            dbHealth,
            kafkaHealth,
            engineHealth,
            Instant.now()
        );

        // When
        HealthState overallState = healthStatus.getOverallState();

        // Then
        assertThat(overallState).isEqualTo(HealthState.UNKNOWN);
        assertThat(healthStatus.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("isHealthy should return true only when overall state is UP")
    void testIsHealthyLogic() {
        // Given - All components UP
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.UP,
            "Database connection healthy",
            Instant.now(),
            Optional.of(Duration.ofMillis(50)),
            Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.UP,
            "Kafka connection healthy",
            Instant.now(),
            3,
            Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UP,
            "CDC engine running",
            Instant.now(),
            true,
            2,
            Optional.empty()
        );

        HealthStatus healthyStatus = new HealthStatus(
            dbHealth,
            kafkaHealth,
            engineHealth,
            Instant.now()
        );

        // Given - One component DEGRADED
        KafkaHealthCheck degradedKafka = new KafkaHealthCheck(
            HealthState.DEGRADED,
            "Degraded",
            Instant.now(),
            1,
            Optional.empty()
        );

        HealthStatus degradedStatus = new HealthStatus(
            dbHealth,
            degradedKafka,
            engineHealth,
            Instant.now()
        );

        // When / Then
        assertThat(healthyStatus.isHealthy()).isTrue();
        assertThat(degradedStatus.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("HealthStatus should track last checked timestamp")
    void testLastCheckedTimestamp() {
        // Given
        Instant now = Instant.now();

        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.UP,
            "Healthy",
            now,
            Optional.of(Duration.ofMillis(50)),
            Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.UP,
            "Healthy",
            now,
            3,
            Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UP,
            "Running",
            now,
            true,
            2,
            Optional.empty()
        );

        HealthStatus healthStatus = new HealthStatus(
            dbHealth,
            kafkaHealth,
            engineHealth,
            Instant.now()
        );

        // When / Then
        assertThat(healthStatus.getLastChecked()).isNotNull();
        assertThat(healthStatus.getLastChecked()).isAfterOrEqualTo(now);
    }

    @Test
    @DisplayName("DatabaseHealthCheck should include connection time")
    void testDatabaseHealthCheckWithConnectionTime() {
        // Given
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.UP,
            "Connected successfully",
            Instant.now(),
            Optional.of(Duration.ofMillis(100)),
            Optional.empty()
        );

        // When / Then
        assertThat(dbHealth.state()).isEqualTo(HealthState.UP);
        assertThat(dbHealth.connectionTime()).isPresent();
        assertThat(dbHealth.connectionTime().get()).isEqualTo(Duration.ofMillis(100));
        assertThat(dbHealth.errorMessage()).isEmpty();
    }

    @Test
    @DisplayName("DatabaseHealthCheck should include error message when DOWN")
    void testDatabaseHealthCheckWithError() {
        // Given
        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
            HealthState.DOWN,
            "Connection failed",
            Instant.now(),
            Optional.empty(),
            Optional.of("Connection refused: localhost:5432")
        );

        // When / Then
        assertThat(dbHealth.state()).isEqualTo(HealthState.DOWN);
        assertThat(dbHealth.connectionTime()).isEmpty();
        assertThat(dbHealth.errorMessage()).isPresent();
        assertThat(dbHealth.errorMessage().get()).contains("Connection refused");
    }

    @Test
    @DisplayName("KafkaHealthCheck should track available brokers")
    void testKafkaHealthCheckWithBrokers() {
        // Given
        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
            HealthState.UP,
            "All brokers available",
            Instant.now(),
            3,
            Optional.empty()
        );

        // When / Then
        assertThat(kafkaHealth.state()).isEqualTo(HealthState.UP);
        assertThat(kafkaHealth.availableBrokers()).isEqualTo(3);
        assertThat(kafkaHealth.errorMessage()).isEmpty();
    }

    @Test
    @DisplayName("CdcEngineHealthCheck should track capture status")
    void testCdcEngineHealthCheckWithCaptureStatus() {
        // Given
        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
            HealthState.UP,
            "Capturing from 5 tables",
            Instant.now(),
            true,
            5,
            Optional.empty()
        );

        // When / Then
        assertThat(engineHealth.state()).isEqualTo(HealthState.UP);
        assertThat(engineHealth.isCapturing()).isTrue();
        assertThat(engineHealth.monitoredTables()).isEqualTo(5);
        assertThat(engineHealth.errorMessage()).isEmpty();
    }

    @Test
    @DisplayName("Overall state priority: DOWN > DEGRADED > UNKNOWN > UP")
    void testHealthStatePriority() {
        // Given - DOWN has highest priority
        DatabaseHealthCheck dbDown = new DatabaseHealthCheck(
            HealthState.DOWN,
            "DB Down",
            Instant.now(),
            Optional.empty(),
            Optional.of("Error")
        );

        KafkaHealthCheck kafkaDegraded = new KafkaHealthCheck(
            HealthState.DEGRADED,
            "Degraded",
            Instant.now(),
            1,
            Optional.empty()
        );

        CdcEngineHealthCheck engineUp = new CdcEngineHealthCheck(
            HealthState.UP,
            "Running",
            Instant.now(),
            true,
            2,
            Optional.empty()
        );

        HealthStatus healthStatus = new HealthStatus(
            dbDown,
            kafkaDegraded,
            engineUp,
            Instant.now()
        );

        // When / Then - DOWN should take priority
        assertThat(healthStatus.getOverallState()).isEqualTo(HealthState.DOWN);
    }
}
