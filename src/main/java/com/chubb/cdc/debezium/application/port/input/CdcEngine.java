package com.chubb.cdc.debezium.application.port.input;

import java.time.Instant;

/**
 * Input port for CDC engine operations.
 *
 * <p>Defines the contract for controlling the CDC engine lifecycle (start, stop, status).
 * This is a driving port in Clean Architecture - it defines operations that can be invoked
 * from the presentation layer (REST API, CLI) to control the CDC process.</p>
 *
 * <p>Implementations are responsible for:</p>
 * <ul>
 *   <li>Starting and stopping the CDC capture process</li>
 *   <li>Managing connection to the source database</li>
 *   <li>Tracking capture status and position</li>
 *   <li>Handling graceful shutdown with offset persistence</li>
 * </ul>
 */
public interface CdcEngine {

    /**
     * Starts the CDC capture process.
     *
     * <p>Initializes the Debezium engine, connects to the source database,
     * and begins capturing change events from configured tables.</p>
     *
     * @throws CdcEngineException if the engine cannot be started
     */
    void start() throws CdcEngineException;

    /**
     * Stops the CDC capture process gracefully.
     *
     * <p>Flushes any pending events, persists the current offset position,
     * and closes database connections cleanly.</p>
     *
     * @throws CdcEngineException if the engine cannot be stopped cleanly
     */
    void stop() throws CdcEngineException;

    /**
     * Checks if the CDC engine is currently running.
     *
     * @return true if the engine is actively capturing changes
     */
    boolean isRunning();

    /**
     * Gets the current status of the CDC engine.
     *
     * @return the engine status
     */
    CdcEngineStatus getStatus();

    /**
     * Represents the current status of the CDC engine.
     */
    record CdcEngineStatus(
        EngineState state,
        Instant startedAt,
        Instant stoppedAt,
        long eventsCaptured,
        String currentPosition,
        String errorMessage
    ) {
        /**
         * Creates a status for a running engine.
         */
        public static CdcEngineStatus running(
            Instant startedAt,
            long eventsCaptured,
            String currentPosition
        ) {
            return new CdcEngineStatus(
                EngineState.RUNNING,
                startedAt,
                null,
                eventsCaptured,
                currentPosition,
                null
            );
        }

        /**
         * Creates a status for a stopped engine.
         */
        public static CdcEngineStatus stopped() {
            return new CdcEngineStatus(
                EngineState.STOPPED,
                null,
                null,
                0L,
                null,
                null
            );
        }

        /**
         * Creates a status for a failed engine.
         */
        public static CdcEngineStatus failed(String errorMessage) {
            return new CdcEngineStatus(
                EngineState.FAILED,
                null,
                Instant.now(),
                0L,
                null,
                errorMessage
            );
        }

        /**
         * Creates a status for a starting engine.
         */
        public static CdcEngineStatus starting() {
            return new CdcEngineStatus(
                EngineState.STARTING,
                null,
                null,
                0L,
                null,
                null
            );
        }

        /**
         * Creates a status for a stopping engine.
         */
        public static CdcEngineStatus stopping(Instant startedAt, long eventsCaptured) {
            return new CdcEngineStatus(
                EngineState.STOPPING,
                startedAt,
                null,
                eventsCaptured,
                null,
                null
            );
        }
    }

    /**
     * Possible states of the CDC engine.
     */
    enum EngineState {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        FAILED
    }

    /**
     * Exception thrown when CDC engine operations fail.
     */
    class CdcEngineException extends Exception {
        public CdcEngineException(String message) {
            super(message);
        }

        public CdcEngineException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
