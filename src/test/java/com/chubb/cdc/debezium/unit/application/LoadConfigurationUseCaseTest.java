package com.chubb.cdc.debezium.unit.application;

import com.chubb.cdc.debezium.application.port.output.DomainEventPublisher;
import com.chubb.cdc.debezium.application.usecase.configuration.LoadConfigurationUseCase;
import com.chubb.cdc.debezium.domain.configuration.event.ConfigurationLoadedEvent;
import com.chubb.cdc.debezium.domain.configuration.model.*;
import com.chubb.cdc.debezium.domain.configuration.repository.ConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LoadConfigurationUseCase.
 * <p>
 * Tests the business logic of loading and validating configuration,
 * ensuring proper event publishing and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoadConfigurationUseCase Unit Tests")
class LoadConfigurationUseCaseTest {

    @Mock
    private ConfigurationRepository configurationRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private LoadConfigurationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoadConfigurationUseCase(configurationRepository, domainEventPublisher);
    }

    @Test
    @DisplayName("Should load configuration successfully and publish event")
    void shouldLoadConfigurationSuccessfully() throws Exception {
        // Given
        ConfigurationAggregate config = createValidConfiguration();
        when(configurationRepository.load()).thenReturn(config);

        // When
        ConfigurationAggregate result = useCase.execute();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTableConfigs()).hasSize(2);
        assertThat(result.getDatabaseConfig().getType()).isEqualTo(DatabaseType.POSTGRESQL);

        // Verify event published
        ArgumentCaptor<ConfigurationLoadedEvent> eventCaptor =
                ArgumentCaptor.forClass(ConfigurationLoadedEvent.class);
        verify(domainEventPublisher).publish(eventCaptor.capture());

        ConfigurationLoadedEvent event = eventCaptor.getValue();
        assertThat(event.configuration()).isEqualTo(config);
        assertThat(event.source()).isEqualTo("file-system");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when repository fails to load")
    void shouldThrowExceptionWhenRepositoryFails() throws Exception {
        // Given
        when(configurationRepository.load())
                .thenThrow(new RuntimeException("File not found"));

        // When / Then
        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(LoadConfigurationUseCase.ConfigurationLoadException.class)
                .hasMessageContaining("Failed to load CDC configuration")
                .hasCauseInstanceOf(RuntimeException.class);

        // Verify no event published on failure
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should throw exception when configuration is invalid")
    void shouldThrowExceptionWhenConfigurationIsInvalid() throws Exception {
        // Given
        ConfigurationAggregate invalidConfig = createInvalidConfiguration();
        when(configurationRepository.load()).thenReturn(invalidConfig);

        // When / Then
        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(LoadConfigurationUseCase.ConfigurationLoadException.class);

        // Verify no event published on validation failure
        verify(domainEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Should reject null or empty source path")
    void shouldRejectNullOrEmptySourcePath() {
        // When / Then - null path
        assertThatThrownBy(() -> useCase.executeFromPath(null))
                .isInstanceOf(LoadConfigurationUseCase.ConfigurationLoadException.class)
                .hasMessageContaining("cannot be null or empty");

        // When / Then - empty path
        assertThatThrownBy(() -> useCase.executeFromPath(""))
                .isInstanceOf(LoadConfigurationUseCase.ConfigurationLoadException.class)
                .hasMessageContaining("cannot be null or empty");

        // When / Then - blank path
        assertThatThrownBy(() -> useCase.executeFromPath("   "))
                .isInstanceOf(LoadConfigurationUseCase.ConfigurationLoadException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle event publisher failures gracefully")
    void shouldHandleEventPublisherFailures() throws Exception {
        // Given
        ConfigurationAggregate config = createValidConfiguration();
        when(configurationRepository.load()).thenReturn(config);
        doThrow(new RuntimeException("Event bus down"))
                .when(domainEventPublisher).publish(any());

        // When / Then
        // Note: Current implementation doesn't handle event publisher failures
        // This test documents the expected behavior
        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(LoadConfigurationUseCase.ConfigurationLoadException.class);
    }

    @Test
    @DisplayName("Should load configuration with minimal valid setup")
    void shouldLoadConfigurationWithMinimalSetup() throws Exception {
        // Given
        ConfigurationAggregate minimalConfig = createMinimalConfiguration();
        when(configurationRepository.load()).thenReturn(minimalConfig);

        // When
        ConfigurationAggregate result = useCase.execute();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTableConfigs()).hasSize(1);
        verify(domainEventPublisher).publish(any(ConfigurationLoadedEvent.class));
    }

    // ==================== Helper Methods ====================

    private ConfigurationAggregate createValidConfiguration() {
        SourceDatabaseConfig dbConfig = new SourceDatabaseConfig(
                DatabaseType.POSTGRESQL,
                "localhost",
                5432,
                "testdb",
                "testuser",
                "testpass",
                null,
                Map.of()
        );

        var tableId1 = new com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier(
                "testdb", "public", "orders"
        );
        var tableId2 = new com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier(
                "testdb", "public", "customers"
        );

        TableConfig table1 = new TableConfig(
                tableId1,
                com.chubb.cdc.debezium.domain.configuration.model.TableConfig.IncludeMode.INCLUDE_ALL,
                Set.of(),
                java.util.Optional.empty()
        );

        TableConfig table2 = new TableConfig(
                tableId2,
                com.chubb.cdc.debezium.domain.configuration.model.TableConfig.IncludeMode.EXCLUDE_SPECIFIED,
                Set.of("password"),
                java.util.Optional.empty()
        );

        KafkaConfig kafkaConfig = new KafkaConfig(
                List.of("localhost:9092"),
                "cdc.{database}.{table}",
                null,
                Map.of()
        );

        return new ConfigurationAggregate(
                dbConfig,
                Set.of(table1, table2),
                kafkaConfig,
                Instant.now()
        );
    }

    private ConfigurationAggregate createInvalidConfiguration() {
        // Configuration with no tables (invalid)
        SourceDatabaseConfig dbConfig = new SourceDatabaseConfig(
                DatabaseType.POSTGRESQL,
                "localhost",
                5432,
                "testdb",
                "testuser",
                "testpass",
                null,
                Map.of()
        );

        KafkaConfig kafkaConfig = new KafkaConfig(
                List.of("localhost:9092"),
                "cdc.{database}.{table}",
                null,
                Map.of()
        );

        return new ConfigurationAggregate(
                dbConfig,
                Set.of(), // No tables - this should fail validation
                kafkaConfig,
                Instant.now()
        );
    }

    private ConfigurationAggregate createMinimalConfiguration() {
        SourceDatabaseConfig dbConfig = new SourceDatabaseConfig(
                DatabaseType.POSTGRESQL,
                "localhost",
                5432,
                "testdb",
                "testuser",
                "testpass",
                null,
                Map.of()
        );

        var tableId = new com.chubb.cdc.debezium.domain.changecapture.model.TableIdentifier(
                "testdb", "public", "orders"
        );

        TableConfig table = new TableConfig(
                tableId,
                com.chubb.cdc.debezium.domain.configuration.model.TableConfig.IncludeMode.INCLUDE_ALL,
                Set.of(),
                java.util.Optional.empty()
        );

        KafkaConfig kafkaConfig = new KafkaConfig(
                List.of("localhost:9092"),
                "cdc.{database}.{table}",
                null,
                Map.of()
        );

        return new ConfigurationAggregate(
                dbConfig,
                Set.of(table),
                kafkaConfig,
                Instant.now()
        );
    }
}
