# Implementation Plan: Configurable Debezium CDC Application

**Branch**: `001-debezium-cdc-app` | **Date**: 2025-11-01 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-debezium-cdc-app/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a configurable Change Data Capture (CDC) application using Debezium that connects to source databases, captures row-level changes from configured tables, normalizes the data into a consistent format, and publishes events to Kafka topics. The solution must support "build once, deploy many" - the same application binary can be deployed to multiple source systems (e.g., different Genx instances) with environment-specific configuration files controlling database connections, table monitoring lists, and Kafka destinations. The application will use Clean Architecture with distinct bounded contexts for Change Capture, Configuration Management, and Health Monitoring, ensuring the core CDC logic remains independent of specific database types and deployment environments.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Framework**: Spring Boot 3.3.5
**Architecture Style**: Clean Architecture + DDD with bounded contexts: Change Capture, Configuration Management, Health Monitoring
**Database**: Multi-database support required - PostgreSQL, MySQL, SQL Server, Oracle (NEEDS CLARIFICATION: which database to prioritize for initial implementation and testing)
**Primary Dependencies**:
  - Spring Boot Starters: spring-boot-starter-web, spring-boot-starter-actuator, spring-boot-starter-validation
  - Debezium: debezium-embedded, debezium-connector-postgres, debezium-connector-mysql, debezium-connector-sqlserver, debezium-connector-oracle
  - Kafka: spring-kafka
  - API Documentation: springdoc-openapi-starter-webmvc-ui (OpenAPI 3.0/Swagger)
  - Logging: logback with logstash-logback-encoder for structured JSON logging
  - Additional: Lombok for boilerplate reduction, Micrometer for metrics
**Testing**: JUnit 5, AssertJ, Mockito, ArchUnit, Testcontainers (for testing with real databases and Kafka)
**Build Tool**: Maven with Spring Boot Maven Plugin
  - Maven Wrapper (mvnw) for reproducible builds
  - Maven profiles: dev, test, prod
**Containerization**: Docker
  - Base Image: eclipse-temurin:21-jre-alpine
  - Additional Tools: docker-compose for local development (with PostgreSQL, Kafka, Zookeeper)
**Orchestration**: Kubernetes
  - Deployment Strategy: Rolling Update
  - Ingress: NGINX or cloud-specific
**Target Platform**: JVM 21+ in Docker containers on Kubernetes
**Project Type**: Single project microservice with Clean Architecture
**Performance Goals**:
  - Capture and publish 1000 change events per second per monitored table (SC-009)
  - Event latency: changes appear in Kafka within 5 seconds under normal load (SC-002)
  - Recovery time: resume from correct position within 5 minutes after outage (SC-005)
  - Deployment time: configure new source system in under 15 minutes (SC-001)
**Constraints**:
  - 95% capture success rate without data loss (SC-004)
  - Zero data loss during planned restarts (SC-010)
  - 90% schema evolution handling without restart (SC-007)
  - 100% configuration validation on startup (SC-008)
**Scale/Scope**:
  - Support multiple concurrent source systems (minimum 3 simultaneous deployments - SC-003)
  - Handle database transaction loads of up to 1000 events/second per table
  - Support dynamic configuration updates every 5 minutes (FR-021)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] Library-first architecture verified - CDC engine will be built as a standalone library with clear contracts
- [x] CLI interface present and functional (Principle II) - management CLI for configuration validation, status checks, and manual triggers
- [x] Test-First Development plan in place (tests written before implementation) - TDD mandatory per constitution
- [x] Contract and integration tests planned (Principle IV) - contracts for CDC engine, repository interfaces, Kafka publishers
- [x] Clean Architecture layers defined (domain, application, infrastructure, presentation) - three bounded contexts with layer separation
- [x] Domain layer has no framework dependencies - CDC domain logic, configuration model, health status model pure Java
- [x] Design patterns identified for key components - Factory for Debezium connector creation, Strategy for normalization rules, Adapter for external databases, Repository for offset persistence, Observer for domain events
- [x] REST API conventions planned (if REST endpoints) - management API follows RESTful resource naming, standard HTTP methods, structured error responses
- [x] Logging standards defined - structured JSON with correlation IDs, MDC context for request tracing, connection events, capture statistics
- [x] ArchUnit tests planned for architecture validation - enforce layer dependencies, no framework dependencies in domain

## REST API Design (if applicable)

*Complete this section if the feature exposes REST APIs*

**Base Path**: `/api/v1/cdc`

**Resources & Endpoints**:
| Method | Path | Description | Request Body | Response | Status Codes |
|--------|------|-------------|--------------|----------|--------------|
| GET | `/health` | Get CDC application health status | None | `HealthStatusDTO` | 200 |
| GET | `/health/database` | Check database connection status | None | `DatabaseHealthDTO` | 200, 503 |
| GET | `/health/kafka` | Check Kafka connection status | None | `KafkaHealthDTO` | 200, 503 |
| GET | `/metrics` | Get CDC capture metrics and statistics | None | `MetricsDTO` | 200 |
| GET | `/metrics/tables` | Get per-table capture statistics | None | `List<TableMetricsDTO>` | 200 |
| GET | `/config/status` | Get current configuration status | None | `ConfigStatusDTO` | 200 |
| GET | `/config/tables` | List currently monitored tables | None | `List<TableConfigDTO>` | 200 |
| POST | `/config/refresh` | Trigger manual configuration refresh | None | `ConfigRefreshResultDTO` | 200, 500 |
| GET | `/offset/position` | Get current CDC position/offset | None | `OffsetPositionDTO` | 200 |

**Error Handling**:
- All errors return standard error response format with `correlationId`, `timestamp`, `status`, `error`, `message`, `path`
- Validation errors: 400 with field-level error details
- Service unavailable (database/Kafka down): 503 with retry-after header
- Configuration errors: 500 with detailed diagnostic message

**Security**:
- Authentication: JWT Bearer tokens or basic auth for management API (NEEDS CLARIFICATION: authentication mechanism)
- Authorization: Role-based (admin role required for config refresh endpoint)
- Rate limiting: 100 requests/minute per client IP

**OpenAPI Documentation**:
- Use `@Operation`, `@ApiResponse`, `@Schema` annotations on all controllers
- Include request/response examples for all endpoints
- Document all error scenarios with status codes and example responses

## Project Structure

### Documentation (this feature)

```text
specs/001-debezium-cdc-app/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Single project - Clean Architecture with CDC bounded contexts
src/main/java/com/chubb/cdc/debezium/
├── domain/                                      # Domain Layer (no external dependencies)
│   ├── changecapture/                           # Change Capture bounded context
│   │   ├── model/
│   │   │   ├── ChangeEvent.java                 # Value Object (Record) - represents captured change
│   │   │   ├── OperationType.java               # Enum: INSERT, UPDATE, DELETE
│   │   │   ├── TableIdentifier.java             # Value Object (Record) - database.schema.table
│   │   │   ├── CdcPosition.java                 # Value Object (Record) - current offset/position
│   │   │   └── RowData.java                     # Value Object (Record) - normalized row data
│   │   ├── event/
│   │   │   ├── CaptureStartedEvent.java         # Domain Event
│   │   │   ├── CaptureStoppedEvent.java         # Domain Event
│   │   │   └── SchemaChangedEvent.java          # Domain Event
│   │   └── repository/
│   │       └── OffsetRepository.java            # Port - persists CDC position
│   ├── configuration/                           # Configuration bounded context
│   │   ├── model/
│   │   │   ├── SourceDatabaseConfig.java        # Entity - database connection details
│   │   │   ├── TableConfig.java                 # Value Object (Record) - table monitoring rules
│   │   │   ├── KafkaConfig.java                 # Value Object (Record) - Kafka connection details
│   │   │   ├── CompositeUniqueKey.java          # Value Object (Record) - for tables without PKs
│   │   │   └── ConfigurationAggregate.java      # Aggregate Root - complete CDC configuration
│   │   ├── event/
│   │   │   ├── ConfigurationLoadedEvent.java    # Domain Event
│   │   │   └── ConfigurationChangedEvent.java   # Domain Event
│   │   └── repository/
│   │       └── ConfigurationRepository.java     # Port - loads configuration from files
│   └── healthmonitoring/                        # Health Monitoring bounded context
│       ├── model/
│       │   ├── HealthStatus.java                # Entity - overall health state
│       │   ├── DatabaseHealthCheck.java         # Value Object (Record) - DB connection status
│       │   ├── KafkaHealthCheck.java            # Value Object (Record) - Kafka connection status
│       │   └── CaptureMetrics.java              # Value Object (Record) - capture statistics
│       └── repository/
│           └── MetricsRepository.java           # Port - stores metrics history
├── application/                                 # Application Layer
│   ├── usecase/
│   │   ├── changecapture/
│   │   │   ├── StartCaptureUseCase.java         # Use Case - initialize CDC
│   │   │   ├── StopCaptureUseCase.java          # Use Case - graceful shutdown
│   │   │   └── ProcessChangeEventUseCase.java   # Use Case - handle captured event
│   │   ├── configuration/
│   │   │   ├── LoadConfigurationUseCase.java    # Use Case - load config from files
│   │   │   ├── ValidateConfigurationUseCase.java # Use Case - validate config
│   │   │   └── RefreshConfigurationUseCase.java # Use Case - detect and apply config changes
│   │   └── healthmonitoring/
│   │       ├── CheckHealthUseCase.java          # Use Case - verify system health
│   │       └── CollectMetricsUseCase.java       # Use Case - gather capture statistics
│   ├── port/
│   │   ├── input/                               # Driving ports (use case interfaces)
│   │   │   ├── CdcEngine.java                   # Port - CDC engine operations
│   │   │   └── ConfigurationService.java        # Port - configuration operations
│   │   └── output/                              # Driven ports (external services)
│   │       ├── EventPublisher.java              # Port - publish to Kafka
│   │       ├── DataNormalizer.java              # Port - normalize data types
│   │       └── SchemaRegistry.java              # Port - detect schema changes
│   └── dto/
│       ├── ChangeEventDto.java                  # Application DTO
│       ├── ConfigurationDto.java                # Application DTO
│       └── HealthStatusDto.java                 # Application DTO
├── infrastructure/                              # Infrastructure Layer
│   ├── persistence/
│   │   ├── offset/
│   │   │   ├── FileOffsetStore.java             # Repository impl - file-based offset storage
│   │   │   └── OffsetRepositoryAdapter.java     # Adapter - domain to storage
│   │   └── metrics/
│   │       └── InMemoryMetricsStore.java        # Repository impl - in-memory metrics
│   ├── debezium/
│   │   ├── DebeziumEngineAdapter.java           # Adapter - Debezium embedded engine wrapper
│   │   ├── ConnectorFactory.java                # Factory - create database-specific connectors
│   │   ├── PostgresConnectorStrategy.java       # Strategy - PostgreSQL-specific config
│   │   ├── MySqlConnectorStrategy.java          # Strategy - MySQL-specific config
│   │   ├── SqlServerConnectorStrategy.java      # Strategy - SQL Server-specific config
│   │   └── OracleConnectorStrategy.java         # Strategy - Oracle-specific config
│   ├── kafka/
│   │   ├── KafkaEventPublisher.java             # EventPublisher impl - Spring Kafka
│   │   └── TopicNameResolver.java               # Strategy - resolve Kafka topic names
│   ├── normalization/
│   │   ├── DataNormalizerImpl.java              # DataNormalizer impl
│   │   ├── TimestampNormalizer.java             # Strategy - ISO-8601 normalization
│   │   ├── NumericNormalizer.java               # Strategy - numeric type normalization
│   │   └── TextNormalizer.java                  # Strategy - UTF-8 text normalization
│   ├── configuration/
│   │   ├── FileConfigurationLoader.java         # ConfigurationRepository impl - YAML files
│   │   └── ConfigurationRefreshScheduler.java   # Scheduled task - check config every 5 min
│   └── config/
│       ├── DebeziumConfiguration.java           # Spring configuration for Debezium
│       ├── KafkaConfiguration.java              # Spring configuration for Kafka
│       └── SecurityConfiguration.java           # Spring Security configuration
└── presentation/                                # Presentation Layer
    ├── cli/
    │   ├── CdcCommand.java                      # CLI - main CDC commands (start, stop, status)
    │   └── ConfigCommand.java                   # CLI - configuration commands (validate, show)
    └── rest/
        ├── HealthController.java                # REST - health check endpoints
        ├── MetricsController.java               # REST - metrics endpoints
        ├── ConfigurationController.java         # REST - configuration status endpoints
        └── advice/
            └── GlobalExceptionHandler.java      # REST - standard error response formatting

src/test/java/com/chubb/cdc/debezium/
├── architecture/
│   └── ArchitectureTest.java                    # ArchUnit - layer dependency rules, no Spring in domain
├── unit/
│   ├── domain/
│   │   ├── changecapture/
│   │   │   ├── ChangeEventTest.java             # Unit test - change event validation
│   │   │   └── CdcPositionTest.java             # Unit test - position comparison
│   │   ├── configuration/
│   │   │   ├── ConfigurationAggregateTest.java  # Unit test - config validation rules
│   │   │   └── TableConfigTest.java             # Unit test - table config validation
│   │   └── healthmonitoring/
│   │       └── HealthStatusTest.java            # Unit test - health state transitions
│   └── application/
│       ├── LoadConfigurationUseCaseTest.java    # Unit test - use case logic
│       └── ProcessChangeEventUseCaseTest.java   # Unit test - event processing
├── integration/
│   ├── PostgresCdcIntegrationTest.java          # Integration test - Testcontainers PostgreSQL
│   ├── MySqlCdcIntegrationTest.java             # Integration test - Testcontainers MySQL
│   ├── KafkaPublishingIntegrationTest.java      # Integration test - Testcontainers Kafka
│   └── ConfigurationRefreshIntegrationTest.java # Integration test - config hot reload
└── contract/
    ├── rest/
    │   └── HealthApiContractTest.java           # Contract test - REST API contracts
    └── persistence/
        └── OffsetRepositoryContractTest.java    # Contract test - offset storage contract

# Repository Root (deployment files)
Dockerfile                                       # Multi-stage: Maven build + JRE runtime
docker-compose.yml                               # Local dev: app + PostgreSQL + Kafka + Zookeeper
.env.example                                     # Template: DB credentials, Kafka brokers, monitoring config
.dockerignore                                    # Exclude: target/, .git/, *.md

k8s/
├── base/
│   ├── deployment.yaml                          # CDC app deployment
│   ├── service.yaml                             # ClusterIP service
│   ├── configmap.yaml                           # Non-sensitive config (Kafka brokers, topic patterns)
│   └── secret.yaml                              # Sensitive config (DB credentials, Kafka SSL certs)
├── dev/
│   └── kustomization.yaml                       # Development overlays
├── staging/
│   └── kustomization.yaml                       # Staging overlays
└── prod/
    └── kustomization.yaml                       # Production overlays

# Build files
pom.xml                                          # Maven: Spring Boot 3.3.5, Debezium, Kafka, OpenAPI
mvnw, mvnw.cmd                                   # Maven Wrapper
.mvn/                                            # Maven Wrapper config

# Configuration files
src/main/resources/
├── application.yml                              # Main config: server port, Spring profiles
├── application-dev.yml                          # Dev: local Kafka, PostgreSQL, debug logging
├── application-test.yml                         # Test: embedded Kafka, H2, trace logging
├── application-prod.yml                         # Prod: external Kafka, SSL, JSON logging
├── logback-spring.xml                           # Logging: JSON encoder, correlation ID, MDC
└── db/migration/                                # Flyway migrations for offset storage (if needed)

README.md                                        # Project documentation: setup, configuration, deployment
```

**Structure Decision**: Single project Clean Architecture with three bounded contexts:

1. **Change Capture Context**: Core CDC domain logic - capturing change events, tracking position, handling schema evolution. This is the heart of the application and remains independent of specific database technologies.

2. **Configuration Context**: Manages database connections, table monitoring rules, Kafka destination configuration. Responsible for loading, validating, and refreshing configuration from external files. Enables "build once, deploy many" by externalizing all environment-specific settings.

3. **Health Monitoring Context**: Tracks system health (database connectivity, Kafka availability), collects capture metrics (events/sec, latency, errors), exposes status via REST API and CLI. Provides operational visibility for administrators.

The bounded contexts have explicit relationships:
- Configuration context is consumed by Change Capture (provides connection details and table rules)
- Change Capture publishes domain events consumed by Health Monitoring (capture statistics)
- All three contexts are exposed through the Presentation layer (REST API and CLI)

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations to justify. The design follows Clean Architecture with clear layer boundaries, uses established patterns (Factory for connector creation, Strategy for database-specific logic, Adapter for Debezium integration), and maintains domain purity per constitution principles.
