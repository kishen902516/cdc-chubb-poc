# Tasks: Configurable Debezium CDC Application

**Input**: Design documents from `/specs/001-debezium-cdc-app/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Per constitution Principle III (Test-First Development), tests are MANDATORY. All test tasks must be completed and FAIL before implementation tasks begin. Contract and integration tests are required per Principle IV.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `- [ ] [ID] [P?] [Story?] Description with file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Single project structure (Clean Architecture with DDD):
- Domain: `src/main/java/com/chubb/cdc/debezium/domain/`
- Application: `src/main/java/com/chubb/cdc/debezium/application/`
- Infrastructure: `src/main/java/com/chubb/cdc/debezium/infrastructure/`
- Presentation: `src/main/java/com/chubb/cdc/debezium/presentation/`
- Tests: `src/test/java/com/chubb/cdc/debezium/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create Maven project structure with Spring Boot 3.3.5 parent in pom.xml
- [X] T002 Add core dependencies to pom.xml (Spring Boot starters, Debezium Embedded, Spring Kafka, Lombok, validation)
- [X] T003 Add testing dependencies to pom.xml (JUnit 5, AssertJ, Mockito, ArchUnit, Testcontainers)
- [X] T004 [P] Configure Maven profiles (dev, test, prod) in pom.xml
- [X] T005 [P] Create Clean Architecture directory structure (domain, application, infrastructure, presentation layers)
- [X] T006 [P] Configure logback-spring.xml for structured JSON logging with correlation IDs
- [X] T007 [P] Create application.yml with base Spring Boot configuration (server port, profiles)
- [X] T008 [P] Create application-dev.yml for local development (PostgreSQL localhost:5432, Kafka localhost:9092)
- [X] T009 [P] Create application-test.yml for testing (Testcontainers configuration)
- [X] T010 [P] Create application-prod.yml for production (externalized config, SSL settings)
- [X] T011 [P] Create Dockerfile with multi-stage build (Maven build + eclipse-temurin:21-jre-alpine runtime)
- [X] T012 [P] Create docker-compose.yml for local dev (PostgreSQL, Kafka, Zookeeper services)
- [X] T013 [P] Create .env.example with template environment variables
- [X] T014 [P] Create README.md with project overview and setup instructions
- [X] T015 Create ArchUnit test in src/test/java/com/chubb/cdc/debezium/architecture/ArchitectureTest.java to enforce layer dependencies

**Checkpoint**: Project structure complete, build runs successfully

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Model (Framework-Independent)

- [X] T016 [P] Create OperationType enum in src/main/java/com/chubb/cdc/debezium/domain/changecapture/model/OperationType.java
- [X] T017 [P] Create TableIdentifier record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/model/TableIdentifier.java
- [X] T018 [P] Create CdcPosition record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/model/CdcPosition.java with Comparable implementation
- [X] T019 [P] Create RowData record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/model/RowData.java
- [X] T020 Create ChangeEvent record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/model/ChangeEvent.java (depends on T016-T019)
- [X] T021 [P] Create DatabaseType enum in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/DatabaseType.java
- [X] T022 [P] Create CompositeUniqueKey record in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/CompositeUniqueKey.java
- [X] T023 [P] Create TableConfig record in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/TableConfig.java
- [X] T024 [P] Create SslConfig record in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/SslConfig.java
- [X] T025 Create SourceDatabaseConfig entity in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/SourceDatabaseConfig.java (depends on T021, T024)
- [X] T026 [P] Create KafkaConfig record in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/KafkaConfig.java
- [X] T027 Create ConfigurationAggregate in src/main/java/com/chubb/cdc/debezium/domain/configuration/model/ConfigurationAggregate.java (depends on T023, T025, T026)
- [X] T028 [P] Create HealthState enum in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/HealthState.java
- [X] T029 [P] Create DatabaseHealthCheck record in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/DatabaseHealthCheck.java
- [X] T030 [P] Create KafkaHealthCheck record in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/KafkaHealthCheck.java
- [X] T031 [P] Create CdcEngineHealthCheck record in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/CdcEngineHealthCheck.java
- [X] T032 Create HealthStatus entity in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/HealthStatus.java (depends on T028-T031)
- [X] T033 [P] Create CaptureMetrics record in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/CaptureMetrics.java
- [X] T034 [P] Create TableMetrics record in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/model/TableMetrics.java

### Domain Events

- [X] T035 [P] Create CaptureStartedEvent record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/event/CaptureStartedEvent.java
- [X] T036 [P] Create CaptureStoppedEvent record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/event/CaptureStoppedEvent.java
- [X] T037 [P] Create SchemaChangedEvent record in src/main/java/com/chubb/cdc/debezium/domain/changecapture/event/SchemaChangedEvent.java
- [X] T038 [P] Create ConfigurationLoadedEvent record in src/main/java/com/chubb/cdc/debezium/domain/configuration/event/ConfigurationLoadedEvent.java
- [X] T039 [P] Create ConfigurationChangedEvent record in src/main/java/com/chubb/cdc/debezium/domain/configuration/event/ConfigurationChangedEvent.java

### Repository Ports

- [X] T040 [P] Create OffsetRepository interface in src/main/java/com/chubb/cdc/debezium/domain/changecapture/repository/OffsetRepository.java
- [X] T041 [P] Create ConfigurationRepository interface in src/main/java/com/chubb/cdc/debezium/domain/configuration/repository/ConfigurationRepository.java
- [X] T042 [P] Create MetricsRepository interface in src/main/java/com/chubb/cdc/debezium/domain/healthmonitoring/repository/MetricsRepository.java

### Application Layer Ports

- [X] T043 [P] Create EventPublisher port in src/main/java/com/chubb/cdc/debezium/application/port/output/EventPublisher.java
- [X] T044 [P] Create DataNormalizer port in src/main/java/com/chubb/cdc/debezium/application/port/output/DataNormalizer.java
- [X] T045 [P] Create SchemaRegistry port in src/main/java/com/chubb/cdc/debezium/application/port/output/SchemaRegistry.java
- [X] T046 [P] Create CdcEngine port in src/main/java/com/chubb/cdc/debezium/application/port/input/CdcEngine.java
- [X] T047 [P] Create ConfigurationService port in src/main/java/com/chubb/cdc/debezium/application/port/input/ConfigurationService.java

### Application DTOs

- [X] T048 [P] Create ChangeEventDto in src/main/java/com/chubb/cdc/debezium/application/dto/ChangeEventDto.java
- [X] T049 [P] Create ConfigurationDto in src/main/java/com/chubb/cdc/debezium/application/dto/ConfigurationDto.java
- [X] T050 [P] Create HealthStatusDto in src/main/java/com/chubb/cdc/debezium/application/dto/HealthStatusDto.java

### Spring Configuration

- [X] T051 [P] Create DebeziumConfiguration in src/main/java/com/chubb/cdc/debezium/infrastructure/config/DebeziumConfiguration.java
- [X] T052 [P] Create KafkaConfiguration in src/main/java/com/chubb/cdc/debezium/infrastructure/config/KafkaConfiguration.java
- [X] T053 [P] Create SecurityConfiguration in src/main/java/com/chubb/cdc/debezium/infrastructure/config/SecurityConfiguration.java (Basic Auth dev, JWT prod)

### Unit Tests for Domain Model

- [X] T054 [P] Unit test for ChangeEvent validation in src/test/java/com/chubb/cdc/debezium/unit/domain/changecapture/ChangeEventTest.java
- [X] T055 [P] Unit test for CdcPosition comparison in src/test/java/com/chubb/cdc/debezium/unit/domain/changecapture/CdcPositionTest.java
- [X] T056 [P] Unit test for ConfigurationAggregate validation in src/test/java/com/chubb/cdc/debezium/unit/domain/configuration/ConfigurationAggregateTest.java
- [X] T057 [P] Unit test for TableConfig validation in src/test/java/com/chubb/cdc/debezium/unit/domain/configuration/TableConfigTest.java
- [X] T058 [P] Unit test for HealthStatus state transitions in src/test/java/com/chubb/cdc/debezium/unit/domain/healthmonitoring/HealthStatusTest.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Initial CDC Setup for New Source System (Priority: P1) 🎯 MVP

**Goal**: Deploy CDC application to a new PostgreSQL source system, configure database connection and table monitoring, capture INSERT/UPDATE/DELETE events, normalize data, and publish to Kafka.

**Independent Test**: Deploy to single PostgreSQL database, configure connection and table whitelist via YAML, execute INSERT/UPDATE/DELETE operations, verify normalized change events appear in configured Kafka topics within 5 seconds.

### Contract Tests for User Story 1 (TDD - WRITE FIRST, ENSURE FAILURE) ⚠️

- [X] T059 [P] [US1] Contract test for OffsetRepository in src/test/java/com/chubb/cdc/debezium/contract/persistence/OffsetRepositoryContractTest.java (test save/load/delete operations)
- [X] T060 [P] [US1] Contract test for ConfigurationRepository in src/test/java/com/chubb/cdc/debezium/contract/persistence/ConfigurationRepositoryContractTest.java (test YAML loading)
- [X] T061 [P] [US1] Contract test for EventPublisher in src/test/java/com/chubb/cdc/debezium/contract/kafka/EventPublisherContractTest.java (test Kafka publishing)

### Integration Tests for User Story 1 (TDD - WRITE FIRST, ENSURE FAILURE) ⚠️

- [X] T062 [US1] PostgreSQL CDC integration test in src/test/java/com/chubb/cdc/debezium/integration/PostgresCdcIntegrationTest.java (Testcontainers: PostgreSQL + Kafka, test INSERT/UPDATE/DELETE capture)
- [X] T063 [US1] Kafka publishing integration test in src/test/java/com/chubb/cdc/debezium/integration/KafkaPublishingIntegrationTest.java (Testcontainers: verify event format matches change-event-schema.json)

### Implementation for User Story 1

#### Infrastructure - Persistence (File-based Offset Storage)

- [X] T064 [P] [US1] Implement FileOffsetStore in src/main/java/com/chubb/cdc/debezium/infrastructure/persistence/offset/FileOffsetStore.java
- [X] T065 [US1] Implement OffsetRepositoryAdapter in src/main/java/com/chubb/cdc/debezium/infrastructure/persistence/offset/OffsetRepositoryAdapter.java (depends on T064)

#### Infrastructure - Configuration (YAML-based Configuration)

- [X] T066 [P] [US1] Implement FileConfigurationLoader in src/main/java/com/chubb/cdc/debezium/infrastructure/configuration/FileConfigurationLoader.java (load from YAML per configuration-schema.yaml)
- [X] T067 [US1] Add configuration validation logic to FileConfigurationLoader (database connection, Kafka brokers, table names, SSL paths)

#### Infrastructure - Debezium (PostgreSQL Connector)

- [X] T068 [P] [US1] Implement ConnectorFactory in src/main/java/com/chubb/cdc/debezium/infrastructure/debezium/ConnectorFactory.java (Factory pattern)
- [X] T069 [US1] Implement PostgresConnectorStrategy in src/main/java/com/chubb/cdc/debezium/infrastructure/debezium/PostgresConnectorStrategy.java (PostgreSQL-specific Debezium config)
- [X] T070 [US1] Implement DebeziumEngineAdapter in src/main/java/com/chubb/cdc/debezium/infrastructure/debezium/DebeziumEngineAdapter.java (wrap Debezium Embedded Engine, depends on T068, T069)

#### Infrastructure - Kafka (Event Publishing)

- [X] T071 [P] [US1] Implement TopicNameResolver in src/main/java/com/chubb/cdc/debezium/infrastructure/kafka/TopicNameResolver.java (resolve topic from pattern)
- [X] T072 [US1] Implement KafkaEventPublisher in src/main/java/com/chubb/cdc/debezium/infrastructure/kafka/KafkaEventPublisher.java (Spring Kafka producer, depends on T071)

#### Infrastructure - Normalization (Data Type Normalization)

- [X] T073 [P] [US1] Implement TimestampNormalizer in src/main/java/com/chubb/cdc/debezium/infrastructure/normalization/TimestampNormalizer.java (convert to ISO-8601)
- [X] T074 [P] [US1] Implement NumericNormalizer in src/main/java/com/chubb/cdc/debezium/infrastructure/normalization/NumericNormalizer.java (JSON number format)
- [X] T075 [P] [US1] Implement TextNormalizer in src/main/java/com/chubb/cdc/debezium/infrastructure/normalization/TextNormalizer.java (UTF-8 encoding)
- [X] T076 [US1] Implement DataNormalizerImpl in src/main/java/com/chubb/cdc/debezium/infrastructure/normalization/DataNormalizerImpl.java (orchestrate normalizers, depends on T073-T075)

#### Application - Use Cases

- [X] T077 [P] [US1] Implement LoadConfigurationUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/configuration/LoadConfigurationUseCase.java
- [X] T078 [P] [US1] Implement ValidateConfigurationUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/configuration/ValidateConfigurationUseCase.java
- [X] T079 [US1] Implement StartCaptureUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/changecapture/StartCaptureUseCase.java (initialize Debezium, depends on T070)
- [X] T080 [US1] Implement ProcessChangeEventUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/changecapture/ProcessChangeEventUseCase.java (normalize + publish, depends on T072, T076)
- [X] T081 [US1] Implement StopCaptureUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/changecapture/StopCaptureUseCase.java (graceful shutdown, save offset)

#### Unit Tests for Use Cases

- [X] T082 [P] [US1] Unit test for LoadConfigurationUseCase in src/test/java/com/chubb/cdc/debezium/unit/application/LoadConfigurationUseCaseTest.java
- [X] T083 [P] [US1] Unit test for ProcessChangeEventUseCase in src/test/java/com/chubb/cdc/debezium/unit/application/ProcessChangeEventUseCaseTest.java

#### Presentation - CLI (Management Commands)

- [X] T084 [P] [US1] Implement CdcCommand in src/main/java/com/chubb/cdc/debezium/presentation/cli/CdcCommand.java (start, stop, status commands)
- [X] T085 [P] [US1] Implement ConfigCommand in src/main/java/com/chubb/cdc/debezium/presentation/cli/ConfigCommand.java (validate, show commands)

#### Presentation - REST API (Health & Metrics Endpoints for US1)

- [X] T086 [US1] Implement GlobalExceptionHandler in src/main/java/com/chubb/cdc/debezium/presentation/rest/advice/GlobalExceptionHandler.java (standard error responses per ErrorResponse schema)
- [X] T087 [P] [US1] Implement HealthController in src/main/java/com/chubb/cdc/debezium/presentation/rest/HealthController.java (GET /cdc/health, /cdc/health/database endpoints)
- [X] T088 [P] [US1] Implement ConfigurationController in src/main/java/com/chubb/cdc/debezium/presentation/rest/ConfigurationController.java (GET /cdc/config/status, /cdc/config/tables endpoints)

#### Contract Tests for REST API (TDD - WRITE FIRST, ENSURE FAILURE) ⚠️

- [X] T089 [US1] REST API contract test in src/test/java/com/chubb/cdc/debezium/contract/rest/HealthApiContractTest.java (verify all endpoints match openapi.yaml, use Spring MockMvc)

#### Application Wiring

- [X] T090 [US1] Wire all components in Spring Boot main application class src/main/java/com/chubb/cdc/debezium/DebeziumCdcApplication.java
- [X] T091 [US1] Create sample cdc-config.yml in src/main/resources/ for local development (PostgreSQL localhost, tables: public.orders, public.customers)

#### End-to-End Validation

- [X] T092 [US1] Run integration tests (T062, T063) and verify they PASS
- [ ] T093 [US1] Manual test: Start docker-compose, run application with dev profile, execute quickstart.md steps 1-5, verify INSERT/UPDATE/DELETE events in Kafka

**Checkpoint**: User Story 1 complete - CDC captures PostgreSQL changes and publishes to Kafka

---

## Phase 4: User Story 2 - Multi-Source System Deployment (Priority: P2)

**Goal**: Deploy same application binary to multiple source systems with different YAML configurations, verify each instance operates independently and correctly monitors its configured source.

**Independent Test**: Deploy same JAR to two different environments with different cdc-config.yml files (different database hosts, table lists, Kafka topics), make changes in both databases simultaneously, verify each instance publishes to its own Kafka topic without interference.

### Integration Tests for User Story 2 (TDD - WRITE FIRST, ENSURE FAILURE) ⚠️

- [ ] T094 [US2] Multi-instance configuration test in src/test/java/com/chubb/cdc/debezium/integration/MultiInstanceConfigurationTest.java (Testcontainers: 2 PostgreSQL + 2 Kafka topics, verify isolation)

### Implementation for User Story 2

#### Infrastructure - Configuration (Environment-specific Config Loading)

- [ ] T095 [US2] Add CDC_CONFIG_PATH environment variable support to FileConfigurationLoader (allow external config file path)
- [ ] T096 [P] [US2] Create example deployment configs in k8s/dev/, k8s/staging/ with different ConfigMaps
- [ ] T097 [P] [US2] Create example cdc-config-genx-region1.yml demonstrating first deployment configuration
- [ ] T098 [P] [US2] Create example cdc-config-genx-region2.yml demonstrating second deployment configuration

#### Application - Configuration Management

- [ ] T099 [US2] Enhance ConfigurationAggregate to include deployment identifier (source system name)
- [ ] T100 [US2] Update LoadConfigurationUseCase to validate unique topic names across deployments (prevent collision)

#### Presentation - REST API (Configuration Status)

- [ ] T101 [US2] Update ConfigurationController to expose deployment identifier in GET /cdc/config/status response

#### Documentation

- [ ] T102 [P] [US2] Update README.md with multi-deployment instructions (Kubernetes ConfigMap examples)
- [ ] T103 [P] [US2] Create k8s/README.md explaining how to deploy multiple instances with Kustomize

#### End-to-End Validation

- [ ] T104 [US2] Run integration test T094 and verify it PASSES
- [ ] T105 [US2] Manual test: Deploy 2 instances locally with different configs, verify independent operation

**Checkpoint**: User Story 2 complete - Same binary deploys to multiple sources with different configs

---

## Phase 5: User Story 4 - Data Normalization and Schema Evolution (Priority: P2)

**Goal**: Normalize captured data (timestamps→ISO-8601, numerics→JSON, UTF-8 text) across database types, detect and handle schema changes (column add/remove/rename) without stopping capture.

**Independent Test**: Capture changes from tables with different data types, verify Kafka messages use consistent normalized formats. Execute ALTER TABLE ADD COLUMN, verify CDC continues and new schema is detected.

### Integration Tests for User Story 4 (TDD - WRITE FIRST, ENSURE FAILURE) ⚠️

- [ ] T106 [P] [US4] MySQL CDC integration test in src/test/java/com/chubb/cdc/debezium/integration/MySqlCdcIntegrationTest.java (Testcontainers: MySQL + Kafka, verify normalization)
- [ ] T107 [US4] Schema evolution integration test in src/test/java/com/chubb/cdc/debezium/integration/SchemaEvolutionIntegrationTest.java (Testcontainers: ALTER TABLE scenarios, verify continued capture)

### Implementation for User Story 4

#### Infrastructure - Debezium (MySQL Connector)

- [ ] T108 [US4] Implement MySqlConnectorStrategy in src/main/java/com/chubb/cdc/debezium/infrastructure/debezium/MySqlConnectorStrategy.java (MySQL-specific Debezium config)
- [ ] T109 [US4] Update ConnectorFactory to support MySQL (add MySqlConnectorStrategy)

#### Infrastructure - Schema Detection

- [ ] T110 [US4] Implement SchemaRegistryImpl in src/main/java/com/chubb/cdc/debezium/infrastructure/debezium/SchemaRegistryImpl.java (detect schema changes from Debezium events)
- [ ] T111 [US4] Update DebeziumEngineAdapter to publish SchemaChangedEvent when schema changes detected

#### Application - Use Cases

- [ ] T112 [US4] Implement HandleSchemaChangeUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/changecapture/HandleSchemaChangeUseCase.java (log schema change, continue capture)

#### Infrastructure - Normalization (Enhanced)

- [ ] T113 [P] [US4] Enhance TimestampNormalizer to handle MySQL DATETIME vs PostgreSQL TIMESTAMP
- [ ] T114 [P] [US4] Enhance NumericNormalizer to handle database-specific numeric types (DECIMAL, BIGINT, etc.)
- [ ] T115 [P] [US4] Enhance TextNormalizer to handle special characters and emojis (full UTF-8 support)

#### Unit Tests

- [ ] T116 [P] [US4] Unit test for TimestampNormalizer edge cases in src/test/java/com/chubb/cdc/debezium/unit/infrastructure/TimestampNormalizerTest.java
- [ ] T117 [P] [US4] Unit test for NumericNormalizer edge cases in src/test/java/com/chubb/cdc/debezium/unit/infrastructure/NumericNormalizerTest.java
- [ ] T118 [P] [US4] Unit test for TextNormalizer UTF-8 edge cases in src/test/java/com/chubb/cdc/debezium/unit/infrastructure/TextNormalizerTest.java

#### Presentation - REST API (Health for MySQL)

- [ ] T119 [US4] Update HealthController to support MySQL database health check

#### End-to-End Validation

- [ ] T120 [US4] Run integration tests T106, T107 and verify they PASS
- [ ] T121 [US4] Manual test: Connect to MySQL, capture changes, verify normalization. Execute ALTER TABLE ADD COLUMN, verify continued capture.

**Checkpoint**: User Story 4 complete - Normalization works across PostgreSQL and MySQL, schema changes handled

---

## Phase 6: User Story 3 - Change Table Configuration Without Redeployment (Priority: P3)

**Goal**: Add or remove tables from monitoring list by updating YAML config file, application detects changes every 5 minutes and adjusts monitoring without restart or data loss.

**Independent Test**: Start CDC monitoring tables A and B, update cdc-config.yml to add table C, wait 5 minutes, verify table C monitoring starts. Remove table B from config, wait 5 minutes, verify table B monitoring stops.

### Integration Tests for User Story 3 (TDD - WRITE FIRST, ENSURE FAILURE) ⚠️

- [ ] T122 [US3] Configuration refresh integration test in src/test/java/com/chubb/cdc/debezium/integration/ConfigurationRefreshIntegrationTest.java (Testcontainers: modify config file, verify hot reload)

### Implementation for User Story 3

#### Infrastructure - Configuration (Scheduled Refresh)

- [ ] T123 [US3] Implement ConfigurationRefreshScheduler in src/main/java/com/chubb/cdc/debezium/infrastructure/configuration/ConfigurationRefreshScheduler.java (Spring @Scheduled every 5 minutes)
- [ ] T124 [US3] Enhance FileConfigurationLoader to detect file modification (check lastModified timestamp)

#### Application - Use Cases

- [ ] T125 [US3] Implement RefreshConfigurationUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/configuration/RefreshConfigurationUseCase.java (compare configs, detect added/removed tables)
- [ ] T126 [US3] Update StartCaptureUseCase to support dynamic table addition (restart Debezium with new table list)
- [ ] T127 [US3] Update StopCaptureUseCase to support selective table removal (stop monitoring specific tables)

#### Domain Events

- [ ] T128 [US3] Enhance ConfigurationChangedEvent to include list of added and removed tables

#### Presentation - REST API (Manual Refresh Trigger)

- [ ] T129 [US3] Implement POST /cdc/config/refresh endpoint in ConfigurationController (manual refresh trigger, requires auth)

#### End-to-End Validation

- [ ] T130 [US3] Run integration test T122 and verify it PASSES
- [ ] T131 [US3] Manual test: Start CDC with 2 tables, modify config to add 1 table, wait 6 minutes, verify 3 tables monitored

**Checkpoint**: User Story 3 complete - Configuration hot reload works without restart

---

## Phase 7: Health Monitoring & Metrics (Cross-Cutting)

**Purpose**: Complete health monitoring and metrics collection infrastructure for all user stories

### Infrastructure - Metrics

- [ ] T132 [P] Implement InMemoryMetricsStore in src/main/java/com/chubb/cdc/debezium/infrastructure/persistence/metrics/InMemoryMetricsStore.java
- [ ] T133 Implement MetricsRepositoryAdapter in src/main/java/com/chubb/cdc/debezium/infrastructure/persistence/metrics/MetricsRepositoryAdapter.java (depends on T132)

### Application - Use Cases

- [ ] T134 [P] Implement CheckHealthUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/healthmonitoring/CheckHealthUseCase.java
- [ ] T135 [P] Implement CollectMetricsUseCase in src/main/java/com/chubb/cdc/debezium/application/usecase/healthmonitoring/CollectMetricsUseCase.java

### Presentation - REST API

- [ ] T136 [P] Complete MetricsController in src/main/java/com/chubb/cdc/debezium/presentation/rest/MetricsController.java (GET /cdc/metrics, /cdc/metrics/tables, /cdc/offset/position)
- [ ] T137 Complete HealthController Kafka health endpoint (GET /cdc/health/kafka)

### Integration Tests

- [ ] T138 Health monitoring integration test in src/test/java/com/chubb/cdc/debezium/integration/HealthMonitoringIntegrationTest.java (verify all health checks)
- [ ] T139 Metrics collection integration test in src/test/java/com/chubb/cdc/debezium/integration/MetricsCollectionIntegrationTest.java (verify metrics accuracy)

**Checkpoint**: Full health monitoring and metrics operational

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final improvements affecting all user stories

### OpenAPI Documentation

- [ ] T140 [P] Add @Operation, @ApiResponse, @Schema annotations to all REST controllers
- [ ] T141 [P] Add springdoc-openapi dependency to pom.xml and configure Swagger UI
- [ ] T142 Verify generated OpenAPI spec matches contracts/openapi.yaml

### Error Handling & Logging

- [ ] T143 [P] Enhance GlobalExceptionHandler with field-level validation error responses
- [ ] T144 [P] Add correlation ID filter for MDC logging context in src/main/java/com/chubb/cdc/debezium/infrastructure/logging/CorrelationIdFilter.java
- [ ] T145 Add structured logging for all CDC operations (capture start/stop, config load, errors)

### Security

- [ ] T146 [P] Implement JWT authentication for production profile in SecurityConfiguration
- [ ] T147 Add rate limiting (100 req/min) to management API endpoints

### Performance & Resilience

- [ ] T148 [P] Configure Debezium max.batch.size and max.queue.size for backpressure handling
- [ ] T149 [P] Add circuit breaker for Kafka unavailability (Resilience4j)
- [ ] T150 Add retry with exponential backoff for transient failures

### Kubernetes Deployment

- [ ] T151 [P] Create k8s/base/deployment.yaml (CDC app deployment with rolling update)
- [ ] T152 [P] Create k8s/base/service.yaml (ClusterIP service)
- [ ] T153 [P] Create k8s/base/configmap.yaml (non-sensitive config template)
- [ ] T154 [P] Create k8s/base/secret.yaml (sensitive config template: DB credentials, Kafka SSL)
- [ ] T155 [P] Create k8s/dev/kustomization.yaml (development overlays)
- [ ] T156 [P] Create k8s/prod/kustomization.yaml (production overlays)

### Documentation

- [ ] T157 Update README.md with complete deployment instructions (Docker, Kubernetes)
- [ ] T158 [P] Validate quickstart.md instructions work end-to-end
- [ ] T159 [P] Add troubleshooting section to README.md based on quickstart.md

### Final Validation

- [ ] T160 Run all tests (mvn test) and verify 100% pass
- [ ] T161 Run ArchUnit test and verify architecture rules enforced
- [ ] T162 Build Docker image and verify multi-stage build works
- [ ] T163 Deploy to local Kubernetes (kind/minikube) and verify all user stories work

**Checkpoint**: Production-ready application complete

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup (Phase 1) completion - BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational (Phase 2) completion - MVP delivery
- **User Story 2 (Phase 4)**: Depends on User Story 1 (Phase 3) completion - reuses US1 infrastructure
- **User Story 4 (Phase 5)**: Depends on User Story 1 (Phase 3) completion - extends normalization from US1
- **User Story 3 (Phase 6)**: Depends on User Story 1 (Phase 3) completion - extends configuration from US1
- **Health Monitoring (Phase 7)**: Can start after Foundational (Phase 2), parallel with user stories
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories ✅ MVP
- **User Story 2 (P2)**: Reuses all US1 infrastructure, adds multi-config support - Depends on US1
- **User Story 4 (P2)**: Extends US1 normalization and adds MySQL support - Depends on US1
- **User Story 3 (P3)**: Extends US1 configuration loading with hot reload - Depends on US1

### Within Each User Story

**TDD Flow (CRITICAL per Constitution)**:
1. Contract tests FIRST → Write and verify they FAIL
2. Integration tests FIRST → Write and verify they FAIL
3. Implementation → Make tests PASS
4. Unit tests for coverage → Verify edge cases

**Layer Dependencies**:
1. Domain models first (no external dependencies)
2. Repository ports (interfaces)
3. Infrastructure implementations (adapters)
4. Application use cases (orchestration)
5. Presentation layer (controllers, CLI)

### Parallel Opportunities

**Within Setup (Phase 1)**: All tasks marked [P] can run in parallel (T004, T006-T014)

**Within Foundational (Phase 2)**:
- All domain models marked [P] in parallel (T016-T019, T021-T024, T028-T031, T033-T034)
- All domain events in parallel (T035-T039)
- All repository ports in parallel (T040-T042)
- All application ports in parallel (T043-T047)
- All DTOs in parallel (T048-T050)
- All unit tests in parallel (T054-T058)

**Within User Story 1**:
- All contract tests in parallel (T059-T061)
- Infrastructure components by layer:
  - T064 parallel with T066, T068, T071, T073-T075, T084-T085, T087-T088
  - Use cases: T077-T078 parallel

**User Stories in Parallel** (if team capacity):
- After Foundational complete: US1, US2, US4, US3 can all start (if 4 developers available)
- Recommended sequential: US1 → (US2 + US4 parallel) → US3

---

## Parallel Example: User Story 1 - Initial CDC Setup

### Parallel Test Writing (TDD - Do First!)
```bash
# Launch all contract tests for US1 together:
Task T059: Contract test for OffsetRepository
Task T060: Contract test for ConfigurationRepository
Task T061: Contract test for EventPublisher

# Launch integration tests:
Task T062: PostgreSQL CDC integration test
Task T063: Kafka publishing integration test
```

### Parallel Infrastructure Implementation
```bash
# After tests written and FAILING, implement infrastructure in parallel:
Task T064: FileOffsetStore
Task T066: FileConfigurationLoader
Task T068: ConnectorFactory
Task T071: TopicNameResolver
Task T073: TimestampNormalizer
Task T074: NumericNormalizer
Task T075: TextNormalizer
Task T084: CdcCommand CLI
Task T085: ConfigCommand CLI
Task T087: HealthController
Task T088: ConfigurationController
```

### Sequential Dependencies within US1
```
T064 → T065 (OffsetRepositoryAdapter needs FileOffsetStore)
T068, T069 → T070 (DebeziumEngineAdapter needs ConnectorFactory + PostgresStrategy)
T071 → T072 (KafkaEventPublisher needs TopicNameResolver)
T073, T074, T075 → T076 (DataNormalizerImpl orchestrates normalizers)
T070, T072, T076 → T079, T080, T081 (Use cases need adapters)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only) - Recommended Initial Delivery

1. Complete **Phase 1: Setup** (T001-T015) → ~2 days
2. Complete **Phase 2: Foundational** (T016-T058) → ~3 days
3. Complete **Phase 3: User Story 1** (T059-T093) → ~5 days
4. **STOP and VALIDATE**:
   - Run all tests (integration tests T062, T063 must PASS)
   - Execute quickstart.md validation (T093)
   - Verify INSERT/UPDATE/DELETE capture works end-to-end
5. **Deploy/Demo**: Functional CDC for PostgreSQL ✅

**Estimated MVP Timeline**: 10 days (with TDD)

### Incremental Delivery (Recommended Production Path)

1. **Sprint 1**: Setup + Foundational → Foundation ready (5 days)
2. **Sprint 2**: User Story 1 → MVP ready, deploy to staging (5 days)
3. **Sprint 3**: User Story 2 + User Story 4 in parallel → Multi-source + MySQL support (4 days)
4. **Sprint 4**: User Story 3 → Hot reload configuration (3 days)
5. **Sprint 5**: Health Monitoring + Polish → Production ready (3 days)

**Total Timeline**: ~20 days (4 sprints)

### Parallel Team Strategy (3 Developers)

**Week 1** (All developers):
- Day 1-2: Setup (Phase 1) together
- Day 3-5: Foundational (Phase 2) together - pair on complex domain models

**Week 2** (After Foundational complete):
- Developer A: User Story 1 (MVP) - full focus
- Developer B: Health Monitoring infrastructure (parallel)
- Developer C: Polish tasks (Kubernetes, documentation)

**Week 3** (After US1 complete):
- Developer A: User Story 2 (multi-source)
- Developer B: User Story 4 (normalization + MySQL)
- Developer C: User Story 3 (hot reload)

**Week 4** (Integration):
- All: Final integration testing, polish, production deployment

---

## Validation Checklist

### After Each User Story
- [ ] All contract tests PASS (verify API contracts)
- [ ] All integration tests PASS (Testcontainers scenarios)
- [ ] ArchUnit test PASS (architecture rules enforced)
- [ ] Manual end-to-end test per user story acceptance scenarios
- [ ] Code coverage >80% for use cases and domain logic

### Before Production Deployment
- [ ] All 4 user stories independently validated
- [ ] Security audit (JWT auth, rate limiting, input validation)
- [ ] Performance test (1000 events/sec per table target)
- [ ] Disaster recovery test (database down, Kafka down, restart scenarios)
- [ ] Documentation complete (README, quickstart.md, API docs)

---

## Notes

- **[P]** = Parallelizable (different files, no sequential dependencies)
- **[US1]**, **[US2]**, **[US3]**, **[US4]** = User story labels for traceability
- **TDD CRITICAL**: Per constitution, all tests marked "WRITE FIRST" must be completed and verified FAILING before implementation
- Each user story is independently deployable and testable
- Commit after each completed task or logical task group
- Stop at any checkpoint to validate independently
- Avoid: vague tasks, file conflicts, circular dependencies

---

## Total Task Count: 163 tasks

**By User Story**:
- Setup: 15 tasks
- Foundational: 43 tasks (blocks all stories)
- User Story 1 (P1 - MVP): 35 tasks
- User Story 2 (P2): 12 tasks
- User Story 4 (P2): 16 tasks
- User Story 3 (P3): 10 tasks
- Health Monitoring: 8 tasks
- Polish: 24 tasks

**Parallel Opportunities**: 67 tasks marked [P] (41% of total can run in parallel with proper task assignment)

**Independent Test Criteria**:
- **US1**: Single PostgreSQL → Kafka pipeline functional
- **US2**: Same binary, different configs, isolated operation
- **US3**: Config file update → automatic table monitoring adjustment
- **US4**: Cross-database normalization + schema change handling

**Suggested MVP Scope**: User Story 1 only (Setup + Foundational + US1 = 93 tasks, ~10 days)


---

## Additional Implementation: SQL Server Support

**Implemented**: 2025-11-11

In addition to the planned MySQL support, SQL Server (MSSQL) connector has been implemented:

### Completed SQL Server Tasks

- [X] Added SQL Server dependencies to pom.xml (debezium-connector-sqlserver, mssql-jdbc, testcontainers-mssqlserver)
- [X] Created SqlServerConnectorStrategy in src/main/java/com/chubb/cdc/debezium/infrastructure/debezium/SqlServerConnectorStrategy.java
- [X] Updated ConnectorFactory to register SQL Server strategy
- [X] Created SQL Server integration test in src/test/java/com/chubb/cdc/debezium/integration/SqlServerCdcIntegrationTest.java
- [X] Added SQL Server service to docker-compose.yml
- [X] Created sample configuration in src/main/resources/cdc-config-sqlserver.yml
- [X] Created CDC setup script in scripts/setup-sqlserver-cdc.sql
- [X] Created comprehensive documentation in connectors/MSSQL_CONNECTOR_README.md

### SQL Server Connector Features

- Full support for SQL Server 2016+ (tested with 2022)
- CDC enablement at database and table levels
- SSL/TLS support for secure connections
- Data type normalization (DATETIME2 → ISO-8601, NVARCHAR → UTF-8)
- Composite key support for tables without primary keys
- Schema change detection and handling
- Integration with existing Kafka publishing pipeline

### Testing SQL Server Connector

```bash
# Start SQL Server in Docker
docker-compose up -d sqlserver

# Run setup script
docker exec -i cdc-sqlserver /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P "SqlServer2022!" -i /scripts/setup-sqlserver-cdc.sql

# Run integration tests
mvn test -Dtest=SqlServerCdcIntegrationTest
```
