package com.chubb.cdc.debezium.presentation.cli;

import com.chubb.cdc.debezium.application.port.input.CdcEngine;
import com.chubb.cdc.debezium.application.usecase.changecapture.StartCaptureUseCase;
import com.chubb.cdc.debezium.application.usecase.changecapture.StopCaptureUseCase;
import com.chubb.cdc.debezium.application.usecase.configuration.LoadConfigurationUseCase;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * CLI commands for CDC operations.
 * <p>
 * Supports:
 * - start: Start CDC capture
 * - stop: Stop CDC capture gracefully
 * - status: Show current CDC status (optionally as JSON)
 * <p>
 * Usage:
 * - java -jar cdc-app.jar start
 * - java -jar cdc-app.jar stop
 * - java -jar cdc-app.jar status
 * - java -jar cdc-app.jar status --json
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdcCommand implements CommandLineRunner {

    private final CdcEngine cdcEngine;
    private final StartCaptureUseCase startCaptureUseCase;
    private final StopCaptureUseCase stopCaptureUseCase;
    private final LoadConfigurationUseCase loadConfigurationUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    @Override
    public void run(String... args) throws Exception {
        // If no arguments, run as service (don't exit)
        if (args.length == 0) {
            log.info("CDC application starting in service mode");
            return;
        }

        // Parse command
        String command = args[0].toLowerCase();

        try {
            switch (command) {
                case "start":
                    handleStart();
                    System.exit(0);
                    break;

                case "stop":
                    handleStop();
                    System.exit(0);
                    break;

                case "status":
                    boolean jsonOutput = Arrays.asList(args).contains("--json");
                    handleStatus(jsonOutput);
                    System.exit(0);
                    break;

                case "help":
                case "--help":
                case "-h":
                    printHelp();
                    System.exit(0);
                    break;

                default:
                    // Unknown command - run as service
                    log.info("Unknown command '{}', starting in service mode", command);
                    break;
            }

        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
            log.error("CLI command failed", e);
            System.exit(1);
        }
    }

    /**
     * Handle 'start' command.
     */
    private void handleStart() {
        System.out.println("Starting CDC capture...");

        try {
            // Load configuration
            ConfigurationAggregate config = loadConfigurationUseCase.execute();
            System.out.println("Configuration loaded: " + config.getTableConfigs().size() + " table(s) to monitor");

            // Start capture
            startCaptureUseCase.execute(config);

            System.out.println("✓ CDC capture started successfully");
            System.out.println("Monitoring tables:");
            config.getTableConfigs().forEach(tc ->
                    System.out.println("  - " + tc.table().fullyQualifiedName())
            );

        } catch (Exception e) {
            System.err.println("✗ Failed to start CDC capture: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Handle 'stop' command.
     */
    private void handleStop() {
        System.out.println("Stopping CDC capture...");

        try {
            if (!cdcEngine.isRunning()) {
                System.out.println("CDC engine is not running");
                return;
            }

            // Load current configuration
            ConfigurationAggregate config = loadConfigurationUseCase.execute();

            // Stop capture
            stopCaptureUseCase.execute(config, StopCaptureUseCase.StopReason.MANUAL_STOP);

            System.out.println("✓ CDC capture stopped successfully");

        } catch (Exception e) {
            System.err.println("✗ Failed to stop CDC capture: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Handle 'status' command.
     *
     * @param jsonOutput If true, output status as JSON
     */
    private void handleStatus(boolean jsonOutput) {
        try {
            CdcEngine.CdcEngineStatus status = cdcEngine.getStatus();

            if (jsonOutput) {
                // Output as JSON
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("state", status.state().name());
                statusMap.put("running", cdcEngine.isRunning());
                statusMap.put("startedAt", status.startedAt());
                statusMap.put("stoppedAt", status.stoppedAt());
                statusMap.put("eventsCaptured", status.eventsCaptured());
                statusMap.put("currentPosition", status.currentPosition());
                statusMap.put("errorMessage", status.errorMessage());

                String json = objectMapper.writeValueAsString(statusMap);
                System.out.println(json);

            } else {
                // Output as human-readable text
                System.out.println("CDC Engine Status:");
                System.out.println("  State: " + status.state().name());
                System.out.println("  Running: " + (cdcEngine.isRunning() ? "Yes" : "No"));

                if (status.startedAt() != null) {
                    System.out.println("  Started At: " + status.startedAt());
                }

                if (status.stoppedAt() != null) {
                    System.out.println("  Stopped At: " + status.stoppedAt());
                }

                System.out.println("  Events Captured: " + status.eventsCaptured());

                if (status.currentPosition() != null) {
                    System.out.println("  Current Position: " + status.currentPosition());
                }

                if (status.errorMessage() != null) {
                    System.out.println("  Error: " + status.errorMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("✗ Failed to retrieve status: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Print help information.
     */
    private void printHelp() {
        System.out.println("Debezium CDC Application - Command Line Interface");
        System.out.println();
        System.out.println("Usage: java -jar cdc-app.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  start              Start CDC capture");
        System.out.println("  stop               Stop CDC capture gracefully");
        System.out.println("  status             Show current CDC status");
        System.out.println("  status --json      Show status in JSON format");
        System.out.println("  help               Show this help message");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --json             Output in JSON format (for status command)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar cdc-app.jar start");
        System.out.println("  java -jar cdc-app.jar status");
        System.out.println("  java -jar cdc-app.jar status --json");
        System.out.println("  java -jar cdc-app.jar stop");
        System.out.println();
        System.out.println("Service Mode:");
        System.out.println("  Run without arguments to start as a background service:");
        System.out.println("  java -jar cdc-app.jar");
    }
}
