package com.chubb.cdc.debezium.domain.healthmonitoring.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Overall health status entity aggregating all component health checks.
 *
 * <p>Combines database, Kafka, and CDC engine health checks to provide an overall system health status.</p>
 *
 * <p><b>Invariant:</b> Overall state is UP only if all component checks are UP.</p>
 */
public class HealthStatus {

    private final HealthState overallState;
    private final DatabaseHealthCheck databaseHealth;
    private final KafkaHealthCheck kafkaHealth;
    private final CdcEngineHealthCheck engineHealth;
    private final Instant lastChecked;

    /**
     * Creates a new health status.
     *
     * @param databaseHealth database health check result
     * @param kafkaHealth Kafka health check result
     * @param engineHealth CDC engine health check result
     * @param lastChecked timestamp of the most recent check
     * @throws IllegalArgumentException if any parameter is null
     */
    public HealthStatus(
        DatabaseHealthCheck databaseHealth,
        KafkaHealthCheck kafkaHealth,
        CdcEngineHealthCheck engineHealth,
        Instant lastChecked
    ) {
        if (databaseHealth == null) {
            throw new IllegalArgumentException("Database health check must not be null");
        }
        if (kafkaHealth == null) {
            throw new IllegalArgumentException("Kafka health check must not be null");
        }
        if (engineHealth == null) {
            throw new IllegalArgumentException("Engine health check must not be null");
        }
        if (lastChecked == null) {
            throw new IllegalArgumentException("Last checked timestamp must not be null");
        }

        this.databaseHealth = databaseHealth;
        this.kafkaHealth = kafkaHealth;
        this.engineHealth = engineHealth;
        this.lastChecked = lastChecked;
        this.overallState = deriveOverallState();
    }

    /**
     * Derives the overall health state from component health checks.
     *
     * <p>Overall state is:</p>
     * <ul>
     *   <li>UP if all components are UP</li>
     *   <li>DOWN if any component is DOWN</li>
     *   <li>DEGRADED if any component is DEGRADED and none are DOWN</li>
     *   <li>UNKNOWN otherwise</li>
     * </ul>
     *
     * @return the derived overall health state
     */
    private HealthState deriveOverallState() {
        // If any component is DOWN, overall is DOWN
        if (databaseHealth.state() == HealthState.DOWN ||
            kafkaHealth.state() == HealthState.DOWN ||
            engineHealth.state() == HealthState.DOWN) {
            return HealthState.DOWN;
        }

        // If any component is DEGRADED, overall is DEGRADED
        if (databaseHealth.state() == HealthState.DEGRADED ||
            kafkaHealth.state() == HealthState.DEGRADED ||
            engineHealth.state() == HealthState.DEGRADED) {
            return HealthState.DEGRADED;
        }

        // If any component is UNKNOWN, overall is UNKNOWN
        if (databaseHealth.state() == HealthState.UNKNOWN ||
            kafkaHealth.state() == HealthState.UNKNOWN ||
            engineHealth.state() == HealthState.UNKNOWN) {
            return HealthState.UNKNOWN;
        }

        // All components are UP
        return HealthState.UP;
    }

    /**
     * Gets the overall health state.
     *
     * @return the overall health state
     */
    public HealthState getOverallState() {
        return overallState;
    }

    /**
     * Checks if the system is healthy (overall state is UP).
     *
     * @return true if overall state is UP, false otherwise
     */
    public boolean isHealthy() {
        return overallState == HealthState.UP;
    }

    public DatabaseHealthCheck getDatabaseHealth() {
        return databaseHealth;
    }

    public KafkaHealthCheck getKafkaHealth() {
        return kafkaHealth;
    }

    public CdcEngineHealthCheck getEngineHealth() {
        return engineHealth;
    }

    public Instant getLastChecked() {
        return lastChecked;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthStatus that = (HealthStatus) o;
        return overallState == that.overallState &&
               Objects.equals(databaseHealth, that.databaseHealth) &&
               Objects.equals(kafkaHealth, that.kafkaHealth) &&
               Objects.equals(engineHealth, that.engineHealth) &&
               Objects.equals(lastChecked, that.lastChecked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(overallState, databaseHealth, kafkaHealth, engineHealth, lastChecked);
    }

    @Override
    public String toString() {
        return "HealthStatus{" +
               "overallState=" + overallState +
               ", databaseHealth=" + databaseHealth.state() +
               ", kafkaHealth=" + kafkaHealth.state() +
               ", engineHealth=" + engineHealth.state() +
               ", lastChecked=" + lastChecked +
               '}';
    }
}
