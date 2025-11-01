# Research: Debezium CDC Application

**Feature**: Configurable Debezium CDC Application
**Date**: 2025-11-01
**Purpose**: Resolve NEEDS CLARIFICATION items from Technical Context and research best practices for implementation

## Research Questions

### 1. Database Prioritization for Initial Implementation

**Question**: Which database to prioritize for initial implementation and testing?

**Research Findings**:
- **Debezium Connector Maturity**:
  - PostgreSQL: Most mature, well-documented, supports all Debezium features
  - MySQL: Well-supported, good documentation, widely used
  - SQL Server: Supported but requires specific SQL Server configuration (CDC feature)
  - Oracle: Requires LogMiner or XStream, more complex setup, licensing considerations

**Decision**: **PostgreSQL as primary target for MVP**

**Rationale**:
1. Most mature Debezium connector with comprehensive feature support
2. No licensing costs (unlike Oracle)
3. Excellent documentation and community support
4. Supports all required features (schema evolution, transactional semantics)
5. Easy local development setup with Docker/Testcontainers
6. Secondary support for MySQL in MVP due to similar maturity level

**Alternatives Considered**:
- MySQL as primary: Similar maturity but PostgreSQL has better JSON support for normalized output
- SQL Server: Requires enabling CDC feature on SQL Server, adds deployment complexity
- Oracle: Licensing costs and setup complexity make it unsuitable for MVP

### 2. Authentication Mechanism for Management API

**Question**: Which authentication mechanism for management API endpoints?

**Research Findings**:
- **Options**:
  - Basic Auth: Simple, suitable for internal monitoring tools
  - JWT Bearer Tokens: Standard for microservices, supports distributed authentication
  - API Keys: Simple, stateless, good for service-to-service communication
  - OAuth2/OIDC: Enterprise-grade, complex setup

**Decision**: **Basic Auth for development, JWT Bearer Tokens for production**

**Rationale**:
1. Basic Auth sufficient for internal monitoring during development
2. JWT tokens enable integration with enterprise identity providers in production
3. Spring Security provides built-in support for both
4. Can configure via profile (dev vs prod)
5. Aligns with microservices best practices

**Alternatives Considered**:
- API Keys: Simpler than JWT but less secure, no standard expiration mechanism
- OAuth2: Too complex for simple monitoring API, better suited for user-facing applications

### 3. Debezium Embedded Engine vs Kafka Connect

**Question**: Use Debezium Embedded Engine (library) or Debezium via Kafka Connect (separate service)?

**Research Findings**:
- **Embedded Engine**:
  - Pros: Application-controlled, simpler deployment, library-first architecture
  - Cons: Tightly coupled to application lifecycle, scaling requires scaling the app

- **Kafka Connect**:
  - Pros: Independent scaling, multi-tenant, Kafka ecosystem integration
  - Cons: Separate service to manage, config via REST API or files, violates library-first principle

**Decision**: **Debezium Embedded Engine**

**Rationale**:
1. Aligns with constitution Principle I (Library-First Architecture)
2. Simpler deployment model - single application to manage
3. "Build once, deploy many" model works better with embedded approach
4. Application can control lifecycle and error handling directly
5. Sufficient for stated performance goals (1000 events/sec per table)

**Alternatives Considered**:
- Kafka Connect: Better for very high throughput (10k+ events/sec) or multi-tenant scenarios, but adds operational complexity

### 4. Configuration Storage and Refresh Strategy

**Question**: How to store configuration and implement dynamic refresh every 5 minutes?

**Research Findings**:
- **Storage Options**:
  - YAML files: Human-readable, easy to edit, supports hierarchical config
  - Properties files: Simple key-value, limited structure
  - Database: Centralized, auditable, complex for "deploy many" model
  - Environment variables: Good for secrets, poor for complex structured config

- **Refresh Strategies**:
  - File watch (OS events): Immediate, OS-dependent, complex on Windows
  - Polling (scheduled check): Simple, predictable, 5-minute requirement fits well
  - Configuration server (Spring Cloud Config): Centralized, requires additional infrastructure

**Decision**: **YAML files with scheduled polling (every 5 minutes)**

**Rationale**:
1. YAML supports complex hierarchical configuration (database, tables, Kafka)
2. Git-friendly for version control and deployment automation
3. Scheduled polling (5 minutes) meets requirement (FR-021)
4. Spring `@Scheduled` provides simple, reliable polling mechanism
5. File-based config enables "deploy many" - each deployment has own config directory

**Alternatives Considered**:
- File watch: More responsive but adds complexity, OS-dependent behavior
- Database storage: Requires additional infrastructure, complicates multi-deployment model

### 5. Offset/Position Storage Strategy

**Question**: How to persist CDC offset/position for recovery?

**Research Findings**:
- **Options**:
  - File-based (Debezium FileOffsetBackingStore): Simple, local, no external dependencies
  - Kafka topic (Debezium KafkaOffsetBackingStore): Leverages Kafka, distributed
  - Database: Centralized, auditable, requires additional setup
  - In-memory: Lost on restart, only for testing

**Decision**: **File-based offset storage (FileOffsetBackingStore)**

**Rationale**:
1. Simplest option with no external dependencies
2. Sufficient for stated recovery requirements (5-minute recovery time)
3. Can be mounted on persistent volume in Kubernetes
4. Debezium provides built-in FileOffsetBackingStore
5. Each deployment instance manages own offset file

**Alternatives Considered**:
- Kafka topic: More complex, requires Kafka to be available before CDC can start
- Database: Adds dependency, requires schema management

### 6. Data Normalization Strategy

**Question**: How to implement data type normalization across different databases?

**Research Findings**:
- **Approaches**:
  - Debezium built-in converters: Automatic conversion to JSON Schema or Avro
  - Custom mapping layer: Full control, can enforce business rules
  - Schema Registry: Centralized schema management (Confluent Schema Registry)

- **Key Normalization Requirements** (from spec):
  - Timestamps → ISO-8601 format (FR-019)
  - Numeric types → JSON numbers (FR-020)
  - Text → UTF-8 encoding (FR-022)
  - Consistent null handling

**Decision**: **Custom normalization layer with Strategy pattern**

**Rationale**:
1. Requirement for consistent normalization across database types (FR-007)
2. Strategy pattern allows database-specific normalization logic
3. Full control over output format (not constrained by Debezium converters)
4. Testable in isolation (unit tests for each normalizer)
5. Can evolve normalization rules independently of Debezium

**Alternatives Considered**:
- Debezium JSON converter: Less control over format, some database types not handled consistently
- Schema Registry: Adds infrastructure dependency, more complex for simple normalization

### 7. Handling Tables Without Primary Keys

**Question**: How to handle CDC for tables without primary keys (edge case from spec)?

**Research Findings**:
- **Debezium Behavior**: Can capture changes but may struggle with UPDATE/DELETE identification
- **Approaches**:
  - Composite unique identifiers: Specify columns that together uniquely identify rows
  - Add surrogate keys: Modify database (violates non-invasive CDC principle)
  - Capture INSERT-only: Limit functionality for problematic tables

**Decision**: **Configuration-specified composite unique identifiers**

**Rationale**:
1. Spec requirement (FR-023): Allow administrators to configure composite keys
2. Non-invasive - no database schema changes required
3. Supported by Debezium via custom key configuration
4. Clear error reporting if composite key not configured for PK-less table

**Alternatives Considered**:
- Require primary keys: Too restrictive, legacy databases may lack PKs
- Automatic composite key detection: Unreliable, may miss intended unique constraints

## Technology Stack Summary

Based on research above, confirmed technology stack:

**Core Technologies**:
- Java 21 with virtual threads for concurrent change event processing
- Spring Boot 3.3.5 for dependency injection and configuration management
- Debezium Embedded Engine 2.5.x (latest stable)
- Spring Kafka for Kafka publishing
- Testcontainers for integration testing (PostgreSQL, MySQL, Kafka)

**Initial Database Support**:
- MVP: PostgreSQL (primary), MySQL (secondary)
- Future: SQL Server, Oracle (post-MVP based on customer demand)

**Configuration**:
- YAML files for application config (database, tables, Kafka)
- Scheduled polling every 5 minutes for config changes
- File-based offset storage for CDC position

**Authentication**:
- Development: HTTP Basic Auth
- Production: JWT Bearer tokens with Spring Security

**Normalization**:
- Custom Strategy-based normalization layer
- ISO-8601 timestamps, JSON-compatible numeric types, UTF-8 text

## Architecture Patterns Summary

**Debezium Integration**: Adapter pattern to wrap Debezium Embedded Engine, isolate CDC engine from domain logic

**Database-Specific Logic**: Strategy pattern for connector configuration (PostgresConnectorStrategy, MySqlConnectorStrategy, etc.)

**Data Normalization**: Strategy pattern for type-specific normalization (TimestampNormalizer, NumericNormalizer, TextNormalizer)

**Configuration**: Repository pattern for configuration loading, Factory pattern for creating Debezium connectors

**Error Handling**: Circuit breaker for Kafka unavailability, retry with exponential backoff for transient failures

## Performance Considerations

**Throughput**: Debezium Embedded Engine can handle 1000+ events/sec per table (meets SC-009)

**Latency**: Debezium typically captures changes within 1-2 seconds, Kafka publish adds 1-2 seconds (meets SC-002 target of <5 seconds)

**Memory**: Estimated 512MB-1GB per deployment depending on:
- Number of monitored tables (overhead per table)
- Debezium snapshot size (initial historical snapshot)
- Kafka buffering (configurable)

**Scalability**: Horizontal scaling via multiple deployments (one per source database), not designed for vertical scaling within single deployment

## Risk Mitigations

**Risk**: Debezium Embedded Engine failure brings down entire application
**Mitigation**: Health checks detect CDC engine failures, Kubernetes restarts pod, offset storage enables recovery from last position

**Risk**: Large database transactions overwhelm memory
**Mitigation**: Configure Debezium max.batch.size and max.queue.size, implement backpressure to Kafka producer

**Risk**: Schema changes during active capture
**Mitigation**: Debezium detects schema changes automatically, publish SchemaChangedEvent for monitoring, graceful handling in 90% of cases (SC-007)

**Risk**: Kafka broker unavailability
**Mitigation**: Buffering in Kafka producer, circuit breaker pattern, health check exposes Kafka status

## Next Steps (Phase 1)

1. Create data-model.md with domain entities, value objects, and aggregates
2. Generate API contracts (OpenAPI spec, change event JSON schema, config YAML schema)
3. Create quickstart.md with local development setup instructions
4. Update agent context with Debezium, Testcontainers technologies
