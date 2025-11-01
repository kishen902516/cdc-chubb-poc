package com.chubb.cdc.debezium.presentation.rest;

import com.chubb.cdc.debezium.application.usecase.configuration.LoadConfigurationUseCase;
import com.chubb.cdc.debezium.domain.configuration.model.ConfigurationAggregate;
import com.chubb.cdc.debezium.domain.configuration.model.TableConfig;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for configuration management endpoints.
 * <p>
 * Provides endpoints for:
 * - Current configuration status
 * - List of monitored tables
 * - Manual configuration refresh
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cdc/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final LoadConfigurationUseCase loadConfigurationUseCase;
    private final ConfigurationRepository configurationRepository;

    /**
     * GET /cdc/config/status
     * Get current configuration status.
     *
     * @return ConfigStatusDTO with configuration metadata
     */
    @GetMapping("/status")
    public ResponseEntity<ConfigStatusDTO> getConfigStatus() {
        log.debug("Configuration status requested");

        try {
            // Load current configuration
            ConfigurationAggregate config = loadConfigurationUseCase.execute();

            // Get last modified timestamp
            Instant lastModified = configurationRepository.lastModified();

            // Build DTO
            ConfigStatusDTO dto = new ConfigStatusDTO(
                    config.getLoadedAt(),
                    "file-system", // TODO: Make configurable
                    lastModified,
                    config.getTableConfigs().size(),
                    config.getDatabaseConfig().getType().name(),
                    config.getKafkaConfig().brokerAddresses()
            );

            log.debug("Configuration status retrieved: {} monitored tables", dto.monitoredTablesCount());

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Failed to retrieve configuration status", e);
            throw new RuntimeException("Failed to retrieve configuration status", e);
        }
    }

    /**
     * GET /cdc/config/tables
     * List currently monitored tables.
     *
     * @return List of TableConfigDTO with table monitoring configuration
     */
    @GetMapping("/tables")
    public ResponseEntity<List<TableConfigDTO>> getMonitoredTables() {
        log.debug("Monitored tables list requested");

        try {
            // Load current configuration
            ConfigurationAggregate config = loadConfigurationUseCase.execute();

            // Convert to DTOs
            List<TableConfigDTO> tableDtos = config.getTableConfigs().stream()
                    .map(this::mapToTableConfigDTO)
                    .collect(Collectors.toList());

            log.debug("Retrieved {} monitored table(s)", tableDtos.size());

            return ResponseEntity.ok(tableDtos);

        } catch (Exception e) {
            log.error("Failed to retrieve monitored tables", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * POST /cdc/config/refresh
     * Trigger manual configuration refresh.
     *
     * @return ConfigRefreshResultDTO with refresh results
     */
    @PostMapping("/refresh")
    public ResponseEntity<ConfigRefreshResultDTO> refreshConfiguration() {
        log.info("Manual configuration refresh requested");

        try {
            // Load current configuration before refresh
            ConfigurationAggregate oldConfig = loadConfigurationUseCase.execute();

            // Reload configuration
            ConfigurationAggregate newConfig = loadConfigurationUseCase.execute();

            // Detect changes
            boolean changesDetected = oldConfig.hasChangedSince(newConfig);

            ConfigRefreshResultDTO dto;
            if (changesDetected) {
                var addedTables = newConfig.addedTables(oldConfig).stream()
                        .map(this::mapToTableIdentifierDTO)
                        .collect(Collectors.toList());

                var removedTables = newConfig.removedTables(oldConfig).stream()
                        .map(this::mapToTableIdentifierDTO)
                        .collect(Collectors.toList());

                dto = new ConfigRefreshResultDTO(
                        true,
                        Instant.now(),
                        true,
                        addedTables,
                        removedTables,
                        null
                );

                log.info("Configuration refreshed: {} table(s) added, {} table(s) removed",
                        addedTables.size(), removedTables.size());
            } else {
                dto = new ConfigRefreshResultDTO(
                        true,
                        Instant.now(),
                        false,
                        List.of(),
                        List.of(),
                        null
                );

                log.info("Configuration refreshed: no changes detected");
            }

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("Configuration refresh failed", e);

            ConfigRefreshResultDTO errorDto = new ConfigRefreshResultDTO(
                    false,
                    Instant.now(),
                    false,
                    List.of(),
                    List.of(),
                    e.getMessage()
            );

            return ResponseEntity.status(500).body(errorDto);
        }
    }

    // ==================== Mapping Methods ====================

    private TableConfigDTO mapToTableConfigDTO(TableConfig tableConfig) {
        TableIdentifierDTO tableId = mapToTableIdentifierDTO(tableConfig.table());

        CompositeKeyDTO compositeKey = tableConfig.compositeKey()
                .map(ck -> new CompositeKeyDTO(ck.columnNames()))
                .orElse(null);

        return new TableConfigDTO(
                tableId,
                tableConfig.includeMode().name(),
                tableConfig.columnFilter() != null ? List.copyOf(tableConfig.columnFilter()) : List.of(),
                compositeKey
        );
    }

    private TableIdentifierDTO mapToTableIdentifierDTO(
            com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier table) {
        return new TableIdentifierDTO(
                table.database(),
                table.schema(),
                table.table()
        );
    }

    // ==================== DTOs ====================

    /**
     * Configuration status DTO matching OpenAPI contract.
     */
    public record ConfigStatusDTO(
            Instant loadedAt,
            String source,
            Instant lastModified,
            int monitoredTablesCount,
            String databaseType,
            List<String> kafkaBrokers
    ) {
    }

    /**
     * Table configuration DTO matching OpenAPI contract.
     */
    public record TableConfigDTO(
            TableIdentifierDTO table,
            String includeMode,
            List<String> columnFilter,
            CompositeKeyDTO compositeKey
    ) {
    }

    /**
     * Table identifier DTO matching OpenAPI contract.
     */
    public record TableIdentifierDTO(
            String database,
            String schema,
            String table
    ) {
    }

    /**
     * Composite key DTO matching OpenAPI contract.
     */
    public record CompositeKeyDTO(
            List<String> columnNames
    ) {
    }

    /**
     * Configuration refresh result DTO matching OpenAPI contract.
     */
    public record ConfigRefreshResultDTO(
            boolean success,
            Instant timestamp,
            boolean changesDetected,
            List<TableIdentifierDTO> addedTables,
            List<TableIdentifierDTO> removedTables,
            String errorMessage
    ) {
    }
}
