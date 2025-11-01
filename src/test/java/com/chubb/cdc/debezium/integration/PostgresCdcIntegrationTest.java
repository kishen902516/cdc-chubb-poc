package com.chubb.cdc.debezium.integration;

import com.chubb.cdc.debezium.domain.changecapture.model.ChangeEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.OperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for PostgreSQL CDC functionality.
 *
 * This test uses Testcontainers to spin up real PostgreSQL and Kafka instances,
 * then verifies that the CDC application can:
 * - Connect to PostgreSQL database
 * - Capture INSERT events
 * - Capture UPDATE events
 * - Capture DELETE events
 * - Publish normalized events to Kafka
 *
 * Per TDD methodology, this test is written FIRST before implementation exists.
 * It should FAIL initially, then PASS once the full CDC pipeline is implemented.
 *
 * NOTE: This test requires Docker to be running.
 */
@Testcontainers
public class PostgresCdcIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:15-alpine")
    )
        .withDatabaseName("testdb")
        .withUsername("testuser")
        .withPassword("testpass")
        .withCommand("postgres", "-c", "wal_level=logical");  // Required for CDC

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    private Connection dbConnection;
    private BlockingQueue<ChangeEvent> capturedEvents;

    // This will be injected by the actual CDC engine implementation
    private Object cdcEngine;  // TODO: Replace with actual CdcEngine type once implemented

    @BeforeEach
    void setUp() throws Exception {
        // Connect to PostgreSQL
        dbConnection = DriverManager.getConnection(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );

        // Create test table
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                CREATE TABLE orders (
                    order_id SERIAL PRIMARY KEY,
                    customer_id INTEGER NOT NULL,
                    status VARCHAR(50) NOT NULL,
                    total_amount DECIMAL(10, 2),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        // Initialize event capture queue
        capturedEvents = new LinkedBlockingQueue<>();

        // TODO: Initialize and start CDC engine once implemented
        // cdcEngine = createCdcEngine(postgres, kafka, capturedEvents);
        // cdcEngine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        // TODO: Stop CDC engine
        // if (cdcEngine != null) {
        //     cdcEngine.stop();
        // }

        if (dbConnection != null && !dbConnection.isClosed()) {
            dbConnection.close();
        }
    }

    @Test
    void shouldCaptureInsertEvent() throws Exception {
        // Given - CDC is running and monitoring the orders table

        // When - INSERT a new row
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (456, 'PENDING', 99.99)
                """);
        }

        // Then - should capture INSERT event
        ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.operation()).isEqualTo(OperationType.INSERT);
        assertThat(event.table().table()).isEqualTo("orders");
        assertThat(event.table().schema()).isEqualTo("public");
        assertThat(event.before()).isNull();
        assertThat(event.after()).isNotNull();
        assertThat(event.after().fields())
            .containsEntry("customer_id", 456)
            .containsEntry("status", "PENDING")
            .containsEntry("total_amount", 99.99);
    }

    @Test
    void shouldCaptureUpdateEvent() throws Exception {
        // Given - an existing row
        int orderId;
        try (Statement stmt = dbConnection.createStatement()) {
            var rs = stmt.executeQuery("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (456, 'PENDING', 99.99)
                RETURNING order_id
                """);
            rs.next();
            orderId = rs.getInt(1);
        }

        // Drain the INSERT event
        capturedEvents.poll(10, TimeUnit.SECONDS);

        // When - UPDATE the row
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate(
                "UPDATE orders SET status = 'CONFIRMED' WHERE order_id = " + orderId
            );
        }

        // Then - should capture UPDATE event
        ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.operation()).isEqualTo(OperationType.UPDATE);
        assertThat(event.table().table()).isEqualTo("orders");
        assertThat(event.before()).isNotNull();
        assertThat(event.before().fields()).containsEntry("status", "PENDING");
        assertThat(event.after()).isNotNull();
        assertThat(event.after().fields()).containsEntry("status", "CONFIRMED");
    }

    @Test
    void shouldCaptureDeleteEvent() throws Exception {
        // Given - an existing row
        int orderId;
        try (Statement stmt = dbConnection.createStatement()) {
            var rs = stmt.executeQuery("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (456, 'CANCELLED', 99.99)
                RETURNING order_id
                """);
            rs.next();
            orderId = rs.getInt(1);
        }

        // Drain the INSERT event
        capturedEvents.poll(10, TimeUnit.SECONDS);

        // When - DELETE the row
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("DELETE FROM orders WHERE order_id = " + orderId);
        }

        // Then - should capture DELETE event
        ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.operation()).isEqualTo(OperationType.DELETE);
        assertThat(event.table().table()).isEqualTo("orders");
        assertThat(event.before()).isNotNull();
        assertThat(event.before().fields())
            .containsEntry("order_id", orderId)
            .containsEntry("status", "CANCELLED");
        assertThat(event.after()).isNull();
    }

    @Test
    void shouldCaptureMultipleEventsInOrder() throws Exception {
        // Given - CDC is running

        // When - perform multiple operations
        int orderId1, orderId2;
        try (Statement stmt = dbConnection.createStatement()) {
            // INSERT #1
            var rs1 = stmt.executeQuery("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (100, 'PENDING', 50.00)
                RETURNING order_id
                """);
            rs1.next();
            orderId1 = rs1.getInt(1);

            // INSERT #2
            var rs2 = stmt.executeQuery("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (200, 'PENDING', 75.00)
                RETURNING order_id
                """);
            rs2.next();
            orderId2 = rs2.getInt(1);

            // UPDATE #1
            stmt.executeUpdate(
                "UPDATE orders SET status = 'CONFIRMED' WHERE order_id = " + orderId1
            );
        }

        // Then - should capture all events in order
        List<ChangeEvent> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);
            if (event != null) {
                events.add(event);
            }
        }

        assertThat(events).hasSize(3);
        assertThat(events.get(0).operation()).isEqualTo(OperationType.INSERT);
        assertThat(events.get(1).operation()).isEqualTo(OperationType.INSERT);
        assertThat(events.get(2).operation()).isEqualTo(OperationType.UPDATE);
    }

    @Test
    void shouldTrackCdcPosition() throws Exception {
        // Given - CDC is running

        // When - insert a row
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (456, 'PENDING', 99.99)
                """);
        }

        // Then - captured event should have position information
        ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.position()).isNotNull();
        assertThat(event.position().sourcePartition()).isNotEmpty();
        assertThat(event.position().offset()).isNotEmpty();
        // PostgreSQL uses LSN (Log Sequence Number)
        assertThat(event.position().offset()).containsKey("lsn");
    }

    @Test
    void shouldHandleNullValues() throws Exception {
        // Given - CDC is running

        // When - INSERT with NULL total_amount
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (456, 'PENDING', NULL)
                """);
        }

        // Then - should capture event with null value
        ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.after()).isNotNull();
        assertThat(event.after().fields())
            .containsEntry("customer_id", 456)
            .containsEntry("status", "PENDING")
            .containsEntry("total_amount", null);
    }

    @Test
    void shouldNormalizeTimestampsToIso8601() throws Exception {
        // Given - CDC is running

        // When - INSERT with timestamp
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.executeUpdate("""
                INSERT INTO orders (customer_id, status, total_amount, created_at)
                VALUES (456, 'PENDING', 99.99, '2025-11-01 10:30:00')
                """);
        }

        // Then - timestamp should be normalized to ISO-8601 format
        ChangeEvent event = capturedEvents.poll(10, TimeUnit.SECONDS);

        assertThat(event).isNotNull();
        assertThat(event.timestamp()).isNotNull();
        assertThat(event.timestamp().toString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
    }

    /**
     * Helper method to create and configure CDC engine (to be implemented).
     */
    private Object createCdcEngine(PostgreSQLContainer<?> postgres,
                                   KafkaContainer kafka,
                                   BlockingQueue<ChangeEvent> eventQueue) {
        // TODO: Implement once infrastructure classes are available
        // return new DebeziumEngineAdapter(
        //     createPostgresConfig(postgres),
        //     createKafkaConfig(kafka),
        //     event -> eventQueue.offer(event)
        // );
        return null;
    }
}
