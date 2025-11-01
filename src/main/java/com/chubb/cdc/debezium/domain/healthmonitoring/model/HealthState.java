package com.chubb.cdc.debezium.domain.healthmonitoring.model;

/**
 * Health state enumeration for system components.
 *
 * <p>Represents the operational status of individual components or the overall system.</p>
 */
public enum HealthState {
    /**
     * Component is fully operational
     */
    UP,

    /**
     * Component is not operational
     */
    DOWN,

    /**
     * Component is operational but with reduced functionality or performance
     */
    DEGRADED,

    /**
     * Component health status cannot be determined
     */
    UNKNOWN
}
