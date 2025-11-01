package com.chubb.cdc.debezium;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Debezium CDC Application - Main Entry Point
 * <p>
 * A configurable Change Data Capture (CDC) application using Debezium that:
 * - Connects to source databases (PostgreSQL, MySQL, SQL Server, Oracle)
 * - Captures row-level changes from configured tables
 * - Normalizes data into consistent format
 * - Publishes events to Kafka topics
 * <p>
 * The application follows Clean Architecture with distinct bounded contexts:
 * - Change Capture: Core CDC logic
 * - Configuration Management: Dynamic configuration loading
 * - Health Monitoring: System health and metrics
 * <p>
 * Features:
 * - Build once, deploy many: Same binary with different configurations
 * - Hot reload: Configuration changes detected every 5 minutes
 * - Multi-database support: PostgreSQL, MySQL, SQL Server, Oracle
 * - Data normalization: ISO-8601 timestamps, JSON numbers, UTF-8 text
 * - Schema evolution: Handle schema changes without restart
 * - REST API: Health checks, metrics, configuration status
 * - CLI: Command-line interface for management operations
 * <p>
 * Usage:
 * - Service mode: java -jar cdc-app.jar
 * - CLI mode: java -jar cdc-app.jar <command> [options]
 * <p>
 * Configuration:
 * - Primary: YAML file (cdc-config.yml)
 * - Environment: application.yml, application-{profile}.yml
 * - Variables: Environment variables for sensitive data
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.chubb.cdc.debezium")
public class DebeziumCdcApplication {

    public static void main(String[] args) {
        log.info("═══════════════════════════════════════════════════════");
        log.info("     Debezium CDC Application Starting");
        log.info("═══════════════════════════════════════════════════════");

        try {
            SpringApplication.run(DebeziumCdcApplication.class, args);

            log.info("═══════════════════════════════════════════════════════");
            log.info("     Debezium CDC Application Started Successfully");
            log.info("═══════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════");
            log.error("     Debezium CDC Application Failed to Start");
            log.error("═══════════════════════════════════════════════════════");
            log.error("Startup error", e);
            System.exit(1);
        }
    }
}
