package com.chubb.cdc.debezium.contract.rest;

import com.chubb.cdc.debezium.application.usecase.healthmonitoring.CheckHealthUseCase;
import com.chubb.cdc.debezium.config.TestKafkaConfiguration;
import com.chubb.cdc.debezium.domain.healthmonitoring.model.*;
import com.chubb.cdc.debezium.presentation.rest.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contract tests for Health API endpoints.
 * <p>
 * Verifies that REST API endpoints match the OpenAPI specification:
 * - Correct HTTP methods and paths
 * - Correct request/response content types
 * - Correct response structure and field names
 * - Correct HTTP status codes
 * <p>
 * This test uses Spring MockMvc to verify controller behavior
 * without starting a full HTTP server.
 */
@WebMvcTest(HealthController.class)
@Import(TestKafkaConfiguration.class)
@DisplayName("Health API Contract Tests")
class HealthApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckHealthUseCase checkHealthUseCase;

    @Test
    @DisplayName("GET /cdc/health should return overall health status with correct structure")
    void getHealthShouldReturnCorrectStructure() throws Exception {
        // Given
        HealthStatus healthStatus = createHealthyStatus();
        when(checkHealthUseCase.execute()).thenReturn(healthStatus);

        // When / Then
        mockMvc.perform(get("/api/v1/cdc/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.overallState").value("UP"))
                .andExpect(jsonPath("$.databaseHealth").exists())
                .andExpect(jsonPath("$.databaseHealth.state").value("UP"))
                .andExpect(jsonPath("$.databaseHealth.message").exists())
                .andExpect(jsonPath("$.databaseHealth.checkedAt").exists())
                .andExpect(jsonPath("$.kafkaHealth").exists())
                .andExpect(jsonPath("$.kafkaHealth.state").value("UP"))
                .andExpect(jsonPath("$.kafkaHealth.message").exists())
                .andExpect(jsonPath("$.kafkaHealth.checkedAt").exists())
                .andExpect(jsonPath("$.kafkaHealth.availableBrokers").value(3))
                .andExpect(jsonPath("$.engineHealth").exists())
                .andExpect(jsonPath("$.engineHealth.state").value("UP"))
                .andExpect(jsonPath("$.engineHealth.message").exists())
                .andExpect(jsonPath("$.engineHealth.checkedAt").exists())
                .andExpect(jsonPath("$.engineHealth.isCapturing").value(true))
                .andExpect(jsonPath("$.engineHealth.monitoredTables").value(5))
                .andExpect(jsonPath("$.lastChecked").exists());
    }

    @Test
    @DisplayName("GET /cdc/health should return degraded state when components are down")
    void getHealthShouldReturnDegradedState() throws Exception {
        // Given
        HealthStatus degradedStatus = createDegradedStatus();
        when(checkHealthUseCase.execute()).thenReturn(degradedStatus);

        // When / Then
        mockMvc.perform(get("/api/v1/cdc/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.overallState").value("DEGRADED"))
                .andExpect(jsonPath("$.databaseHealth.state").value("UP"))
                .andExpect(jsonPath("$.kafkaHealth.state").value("DOWN"))
                .andExpect(jsonPath("$.kafkaHealth.errorMessage").exists());
    }

    @Test
    @DisplayName("GET /cdc/health/database should return database health with 200 when UP")
    void getDatabaseHealthShouldReturn200WhenUp() throws Exception {
        // Given
        HealthStatus healthStatus = createHealthyStatus();
        when(checkHealthUseCase.execute()).thenReturn(healthStatus);

        // When / Then
        mockMvc.perform(get("/api/v1/cdc/health/database")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value("UP"))
                .andExpect(jsonPath("$.message").value("Connected to PostgreSQL at localhost:5432"))
                .andExpect(jsonPath("$.checkedAt").exists())
                .andExpect(jsonPath("$.connectionTime").exists())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    @DisplayName("GET /cdc/health/database should return 503 when database is DOWN")
    void getDatabaseHealthShouldReturn503WhenDown() throws Exception {
        // Given
        HealthStatus unhealthyStatus = createUnhealthyDatabaseStatus();
        when(checkHealthUseCase.execute()).thenReturn(unhealthyStatus);

        // When / Then
        mockMvc.perform(get("/api/v1/cdc/health/database")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value("DOWN"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    @DisplayName("GET /cdc/health/kafka should return kafka health with 200 when UP")
    void getKafkaHealthShouldReturn200WhenUp() throws Exception {
        // Given
        HealthStatus healthStatus = createHealthyStatus();
        when(checkHealthUseCase.execute()).thenReturn(healthStatus);

        // When / Then
        mockMvc.perform(get("/api/v1/cdc/health/kafka")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value("UP"))
                .andExpect(jsonPath("$.message").value("Connected to 3 Kafka brokers"))
                .andExpect(jsonPath("$.checkedAt").exists())
                .andExpect(jsonPath("$.availableBrokers").value(3))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    @DisplayName("GET /cdc/health/kafka should return 503 when Kafka is DOWN")
    void getKafkaHealthShouldReturn503WhenDown() throws Exception {
        // Given
        HealthStatus unhealthyStatus = createUnhealthyKafkaStatus();
        when(checkHealthUseCase.execute()).thenReturn(unhealthyStatus);

        // When / Then
        mockMvc.perform(get("/api/v1/cdc/health/kafka")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value("DOWN"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.availableBrokers").value(0))
                .andExpect(jsonPath("$.errorMessage").exists());
    }

    @Test
    @DisplayName("All health endpoints should accept application/json")
    void allEndpointsShouldAcceptJson() throws Exception {
        // Given
        HealthStatus healthStatus = createHealthyStatus();
        when(checkHealthUseCase.execute()).thenReturn(healthStatus);

        // When / Then - test all endpoints
        mockMvc.perform(get("/api/v1/cdc/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/cdc/health/database")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/cdc/health/kafka")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Health endpoints should handle failures gracefully")
    void healthEndpointsShouldHandleFailuresGracefully() throws Exception {
        // Given
        when(checkHealthUseCase.execute()).thenThrow(new RuntimeException("Health check failed"));

        // When / Then - should return degraded status, not 500
        mockMvc.perform(get("/api/v1/cdc/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.overallState").exists());
    }

    // ==================== Helper Methods ====================

    private HealthStatus createHealthyStatus() {
        Instant now = Instant.now();

        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
                HealthState.UP,
                "Connected to PostgreSQL at localhost:5432",
                now,
                Optional.of(Duration.ofMillis(45)),
                Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
                HealthState.UP,
                "Connected to 3 Kafka brokers",
                now,
                3,
                Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
                HealthState.UP,
                "Capturing changes from 5 tables",
                now,
                true,
                5,
                Optional.empty()
        );

        return new HealthStatus(
                dbHealth,
                kafkaHealth,
                engineHealth,
                now
        );
    }

    private HealthStatus createDegradedStatus() {
        Instant now = Instant.now();

        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
                HealthState.UP,
                "Connected to PostgreSQL at localhost:5432",
                now,
                Optional.of(Duration.ofMillis(45)),
                Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
                HealthState.DOWN,
                "Failed to connect to Kafka",
                now,
                0,
                Optional.of("Connection refused")
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
                HealthState.UP,
                "Capturing changes from 5 tables",
                now,
                true,
                5,
                Optional.empty()
        );

        return new HealthStatus(
                dbHealth,
                kafkaHealth,
                engineHealth,
                now
        );
    }

    private HealthStatus createUnhealthyDatabaseStatus() {
        Instant now = Instant.now();

        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
                HealthState.DOWN,
                "Database connection failed",
                now,
                Optional.empty(),
                Optional.of("Connection timeout after 10 seconds")
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
                HealthState.UP,
                "Connected to 3 Kafka brokers",
                now,
                3,
                Optional.empty()
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
                HealthState.DOWN,
                "Not capturing",
                now,
                false,
                0,
                Optional.of("Database unavailable")
        );

        return new HealthStatus(
                dbHealth,
                kafkaHealth,
                engineHealth,
                now
        );
    }

    private HealthStatus createUnhealthyKafkaStatus() {
        Instant now = Instant.now();

        DatabaseHealthCheck dbHealth = new DatabaseHealthCheck(
                HealthState.UP,
                "Connected to PostgreSQL at localhost:5432",
                now,
                Optional.of(Duration.ofMillis(45)),
                Optional.empty()
        );

        KafkaHealthCheck kafkaHealth = new KafkaHealthCheck(
                HealthState.DOWN,
                "Failed to connect to Kafka",
                now,
                0,
                Optional.of("All brokers unavailable")
        );

        CdcEngineHealthCheck engineHealth = new CdcEngineHealthCheck(
                HealthState.DEGRADED,
                "Capturing but cannot publish",
                now,
                true,
                5,
                Optional.of("Kafka unavailable")
        );

        return new HealthStatus(
                dbHealth,
                kafkaHealth,
                engineHealth,
                now
        );
    }
}