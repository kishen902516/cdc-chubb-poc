package com.chubb.cdc.debezium.integration;

import com.chubb.cdc.debezium.domain.changecapture.model.ChangeEvent;
import com.chubb.cdc.debezium.domain.changecapture.model.OperationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MSSQLServerContainer;
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
 * Integration test for SQL Server CDC functionality.
 *
 * This test uses Testcontainers to spin up real SQL Server and Kafka instances,
 * then verifies that the CDC application can:
 * - Connect to SQL Server database
 * - Enable CDC at database and table levels
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
public class SqlServerCdcIntegrationTest {

    @Container
    private static final MSSQLServerContainer<?> sqlserver = new MSSQLServerContainer<>(
        DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest")
    )
        .acceptLicense()
        .withEnv("MSSQL_AGENT_ENABLED", "true");  // Required for CDC

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
        // Connect to SQL Server
        dbConnection = DriverManager.getConnection(
            sqlserver.getJdbcUrl(),
            sqlserver.getUsername(),
            sqlserver.getPassword()
        );

        // Enable CDC at database level
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("EXEC sys.sp_cdc_enable_db");
        }

        // Create test table
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                CREATE TABLE orders (
                    order_id INT IDENTITY(1,1) PRIMARY KEY,
                    customer_id INT NOT NULL,
                    status NVARCHAR(50) NOT NULL,
                    total_amount DECIMAL(10, 2),
                    created_at DATETIME2 DEFAULT GETUTCDATE(),
                    updated_at DATETIME2 DEFAULT GETUTCDATE()
                )
                """);
        }

        // Enable CDC on the table
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                EXEC sys.sp_cdc_enable_table
                    @source_schema = N'dbo',
                    @source_name = N'orders',
                    @role_name = NULL,
                    @supports_net_changes = 1
                """);
        }

        // Initialize event capture queue
        capturedEvents = new LinkedBlockingQueue<>();

        // TODO: Initialize and start CDC engine once implemented
        // cdcEngine = createCdcEngine(sqlserver, kafka, capturedEvents);
        // cdcEngine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        // TODO: Stop CDC engine
        // if (cdcEngine != null) {
        //     cdcEngine.stop();
        // }

        // Clean up database connection
        if (dbConnection != null && !dbConnection.isClosed()) {
            // Disable CDC on table
            try (Statement stmt = dbConnection.createStatement()) {
                stmt.execute("""
                    IF EXISTS (SELECT * FROM sys.tables WHERE name = 'orders' AND is_tracked_by_cdc = 1)
                    EXEC sys.sp_cdc_disable_table
                        @source_schema = N'dbo',
                        @source_name = N'orders',
                        @capture_instance = N'all'
                    """);
            }

            // Disable CDC at database level
            try (Statement stmt = dbConnection.createStatement()) {
                stmt.execute("EXEC sys.sp_cdc_disable_db");
            }

            dbConnection.close();
        }
    }

    @Test
    void shouldCaptureInsertEvents() throws Exception {
        // Given: CDC is running and monitoring the orders table
        Thread.sleep(2000); // Wait for CDC to initialize

        // When: Insert a new order
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (123, 'PENDING', 99.99)
                """);
        }

        // Then: Should capture INSERT event within 5 seconds
        ChangeEvent event = capturedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.operationType()).isEqualTo(OperationType.INSERT);
        assertThat(event.table().table()).isEqualTo("orders");
        assertThat(event.after().fields().get("customer_id")).isEqualTo(123);
        assertThat(event.after().fields().get("status")).isEqualTo("PENDING");
    }

    @Test
    void shouldCaptureUpdateEvents() throws Exception {
        // Given: An existing order
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (456, 'PENDING', 199.99)
                """);
        }

        Thread.sleep(2000); // Wait for CDC to process insert
        capturedEvents.clear(); // Clear the insert event

        // When: Update the order status
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                UPDATE orders
                SET status = 'COMPLETED', updated_at = GETUTCDATE()
                WHERE customer_id = 456
                """);
        }

        // Then: Should capture UPDATE event within 5 seconds
        ChangeEvent event = capturedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.operationType()).isEqualTo(OperationType.UPDATE);
        assertThat(event.before().fields().get("status")).isEqualTo("PENDING");
        assertThat(event.after().fields().get("status")).isEqualTo("COMPLETED");
    }

    @Test
    void shouldCaptureDeleteEvents() throws Exception {
        // Given: An existing order
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (789, 'CANCELLED', 0.00)
                """);
        }

        Thread.sleep(2000); // Wait for CDC to process insert
        capturedEvents.clear(); // Clear the insert event

        // When: Delete the order
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                DELETE FROM orders
                WHERE customer_id = 789
                """);
        }

        // Then: Should capture DELETE event within 5 seconds
        ChangeEvent event = capturedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.operationType()).isEqualTo(OperationType.DELETE);
        assertThat(event.before().fields().get("customer_id")).isEqualTo(789);
        assertThat(event.before().fields().get("status")).isEqualTo("CANCELLED");
        assertThat(event.after()).isNull();
    }

    @Test
    void shouldHandleMultipleTables() throws Exception {
        // Create a second table
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                CREATE TABLE customers (
                    customer_id INT IDENTITY(1,1) PRIMARY KEY,
                    name NVARCHAR(100) NOT NULL,
                    email NVARCHAR(255) NOT NULL
                )
                """);

            // Enable CDC on customers table
            stmt.execute("""
                EXEC sys.sp_cdc_enable_table
                    @source_schema = N'dbo',
                    @source_name = N'customers',
                    @role_name = NULL,
                    @supports_net_changes = 1
                """);
        }

        Thread.sleep(2000); // Wait for CDC to recognize new table

        // When: Insert into both tables
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                INSERT INTO customers (name, email)
                VALUES ('John Doe', 'john@example.com')
                """);

            stmt.execute("""
                INSERT INTO orders (customer_id, status, total_amount)
                VALUES (1, 'NEW', 50.00)
                """);
        }

        // Then: Should capture events from both tables
        List<ChangeEvent> events = new ArrayList<>();
        ChangeEvent event1 = capturedEvents.poll(5, TimeUnit.SECONDS);
        ChangeEvent event2 = capturedEvents.poll(5, TimeUnit.SECONDS);

        assertThat(event1).isNotNull();
        assertThat(event2).isNotNull();

        events.add(event1);
        events.add(event2);

        // Verify we have events from both tables
        assertThat(events)
            .extracting(e -> e.table().table())
            .containsExactlyInAnyOrder("customers", "orders");
    }

    @Test
    void shouldHandleDataTypeNormalization() throws Exception {
        // Create table with various SQL Server data types
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                CREATE TABLE test_types (
                    id INT IDENTITY(1,1) PRIMARY KEY,
                    date_col DATE,
                    datetime_col DATETIME2,
                    decimal_col DECIMAL(18, 4),
                    nvarchar_col NVARCHAR(MAX),
                    bit_col BIT
                )
                """);

            // Enable CDC
            stmt.execute("""
                EXEC sys.sp_cdc_enable_table
                    @source_schema = N'dbo',
                    @source_name = N'test_types',
                    @role_name = NULL,
                    @supports_net_changes = 1
                """);
        }

        Thread.sleep(2000);

        // When: Insert data with various types
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("""
                INSERT INTO test_types
                (date_col, datetime_col, decimal_col, nvarchar_col, bit_col)
                VALUES
                ('2024-01-15', '2024-01-15 10:30:00', 1234.5678, N'Unicode text 文字', 1)
                """);
        }

        // Then: Should normalize data types correctly
        ChangeEvent event = capturedEvents.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();

        // Verify date/time normalized to ISO-8601
        String datetimeValue = (String) event.after().fields().get("datetime_col");
        assertThat(datetimeValue).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");

        // Verify decimal handled correctly
        assertThat(event.after().fields().get("decimal_col")).isEqualTo(1234.5678);

        // Verify Unicode text preserved
        assertThat(event.after().fields().get("nvarchar_col")).isEqualTo("Unicode text 文字");
    }
}