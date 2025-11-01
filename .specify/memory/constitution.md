<!--
SYNC IMPACT REPORT
==================

Version Change: 1.1.1 → 1.1.2
Change Type: PATCH (build tool standardization, design patterns, logging, REST conventions)
Date: 2025-11-01

Modified Sections:
  - Development Standards > Technology Stack (UPDATED):
    - **Build Tool**: Maven specified as MANDATORY (was Maven or Gradle)
      - Added Maven-specific requirements: profiles, dependencyManagement, Maven Wrapper
      - Removed Gradle as an option for consistency across projects

    - **Logging Standards**: MAJOR EXPANSION from brief mention to comprehensive standards
      - Added detailed log level definitions (ERROR, WARN, INFO, DEBUG, TRACE)
      - Added structured logging requirements (JSON format, mandatory fields, MDC)
      - Added explicit "What to Log" guidance (business events, API calls, slow queries)
      - Added explicit "What NOT to Log" (sensitive data, large payloads)
      - Added log context requirements (correlation IDs, TraceId/SpanId)
      - Added configuration standards (file separation, rotation, retention)

  - Development Standards > NEW SECTIONS:
    - **Design Patterns for Maintainability & Extensibility** (NEW):
      - Creational patterns: Factory, Builder (with Lombok), avoid Singleton
      - Structural patterns: Adapter (mandatory for external integrations), Decorator, Facade, Repository (mandatory)
      - Behavioral patterns: Strategy, Template Method, Observer, Chain of Responsibility, Command
      - DDD Tactical Patterns: Entity, Value Object, Aggregate, Repository, Domain/Application Services, Domain Events
      - Anti-patterns to avoid: God Object, Anemic Domain Model, Transaction Script, Big Ball of Mud

    - **REST API Conventions** (NEW):
      - Resource naming conventions (plural nouns, hierarchical paths, lowercase-with-hyphens)
      - HTTP method semantics (GET, POST, PUT, PATCH, DELETE with idempotency rules)
      - HTTP status code standards (2xx, 4xx, 5xx with specific codes)
      - Request/response format standards (application/json, error response structure with correlationId)
      - Pagination conventions (query parameters, metadata in response)
      - Filtering & searching patterns
      - API versioning strategy (URI versioning: /api/v1/)
      - Security requirements (JWT, CORS, rate limiting)
      - OpenAPI 3.0 documentation requirements

  - Quality Gates > Code Review (EXPANDED):
    - Added design pattern review (appropriate application, no anti-patterns)
    - Added REST API conventions check (if REST endpoints present)
    - Added logging standards check (proper levels, structured logging, no sensitive data)

Modified Templates:
  - plan-template.md (MAJOR UPDATE):
    - Build Tool: Changed from "[Maven | Gradle - document choice]" to "Maven [mandatory]"
    - Added Maven Wrapper and profiles to requirements
    - Added API documentation dependencies (springdoc-openapi mandatory for REST)
    - Added structured logging dependencies (logstash-logback-encoder)
    - Added "Constitution Check" section with detailed checklist (10 items)
    - Added "REST API Design" section (NEW) with:
      - Endpoint table template (method, path, description, request/response, status codes)
      - Error handling conventions
      - Security configuration
      - OpenAPI documentation requirements
    - Expanded project structure to include:
      - Maven Wrapper files (mvnw, mvnw.cmd, .mvn/)
      - Configuration files (application.yml per profile, logback-spring.xml)
      - Database migration directory

Principles Changed: None
Added Sections:
  - Design Patterns for Maintainability & Extensibility
  - REST API Conventions
  - Logging Standards (major expansion)

Removed Sections: None

Templates Status:
  ✅ plan-template.md - UPDATED with Maven, REST API design section, Constitution Check checklist, Maven Wrapper, config files
  ✅ spec-template.md - User scenarios align with testing principles (no changes needed)
  ✅ tasks-template.md - Previously updated for mandatory TDD (no additional changes)
  ✅ Commands - Validated, no agent-specific references found

Follow-up TODOs:
  - Create example pom.xml with Spring Boot, dependencies, profiles
  - Create example logback-spring.xml with JSON encoder for production
  - Create example REST error handler with standard error response format
  - Create design pattern examples for common scenarios (Factory, Strategy, Repository)
  - Create OpenAPI configuration class example
  - Create example ArchUnit tests for pattern enforcement
  - Document correlation ID propagation across services
-->

# CDC Chubb POC Constitution

## Core Principles

### I. Library-First Architecture

Every feature MUST start as a standalone library with the following requirements:

- **Self-Contained**: Libraries must be independently buildable, testable, and deployable without external project dependencies
- **Clear Purpose**: Each library must solve one well-defined problem - no organizational-only or multi-purpose libraries
- **Documented Contracts**: All public interfaces must have explicit documentation defining inputs, outputs, error conditions, and usage examples
- **Reusability**: Design for reuse across multiple contexts, not just the immediate use case

**Rationale**: Library-first design enforces modularity, prevents tight coupling, and ensures components remain maintainable and testable as the system grows.

### II. CLI-Driven Interfaces

Every library MUST expose its core functionality through a command-line interface:

- **Text Protocol**: Standard input/arguments → standard output for data, standard error for diagnostics
- **Multiple Formats**: Support both JSON (for programmatic use) and human-readable formats (for debugging)
- **Composability**: CLI tools must follow Unix philosophy - do one thing well, chainable via pipes
- **No GUI Dependencies**: Core functionality must be accessible without graphical interfaces

**Rationale**: CLI interfaces enable automation, testing, debugging, and integration without coupling to specific UI frameworks or deployment environments.

### III. Test-First Development (NON-NEGOTIABLE)

Test-Driven Development is MANDATORY with strict enforcement:

- **Tests First**: Tests MUST be written before implementation code
- **User Approval**: Test scenarios MUST be reviewed and approved by stakeholders before implementation begins
- **Red-Green-Refactor**: Tests MUST fail initially (RED), then pass after implementation (GREEN), then code can be refactored
- **No Implementation Without Tests**: Any code committed without corresponding tests that were written first MUST be rejected in code review

**Rationale**: TDD ensures requirements are understood before coding, prevents scope creep, provides living documentation, and creates a safety net for refactoring.

### IV. Contract & Integration Testing

Beyond unit tests, the following testing layers are MANDATORY:

- **Contract Tests Required For**:
  - New library public interfaces
  - Any changes to existing library contracts (inputs, outputs, error codes)
  - API endpoints exposed to external consumers
  - Shared data schemas and message formats

- **Integration Tests Required For**:
  - Inter-library communication paths
  - Cross-service workflows
  - Data persistence and retrieval
  - External system integrations

**Rationale**: Contract tests prevent breaking changes and ensure compatibility. Integration tests validate that independently developed components work together correctly in realistic scenarios.

### V. Observability & Simplicity

All code MUST prioritize debuggability and maintainability:

- **Observability**:
  - Text-based I/O ensures all data flows are inspectable
  - Structured logging required for all error paths and critical operations
  - Logs must include context (request IDs, user IDs, timestamps) for traceability
  - Performance metrics must be collectible without code changes

- **Simplicity**:
  - Start with the simplest solution that could work
  - YAGNI (You Aren't Gonna Need It) - no speculative features
  - Complexity must be explicitly justified in design reviews
  - Prefer boring, proven technology over novel approaches

**Rationale**: Simple, observable systems are easier to debug, maintain, and evolve. Complexity should only be introduced when measurably necessary.

### VI. Clean Architecture & Domain-Driven Design

Architecture and domain modeling MUST follow these principles:

- **Clean Architecture Layers** (dependency rule: outer layers depend on inner, never reverse):
  - **Domain Layer** (innermost): Entities, Value Objects, Domain Events - pure business logic, no framework dependencies
  - **Application Layer**: Use Cases, Application Services - orchestrates domain logic, defines application workflows
  - **Infrastructure Layer**: Repositories, External Services, Persistence - implements interfaces defined by domain/application
  - **Presentation Layer** (outermost): CLI, REST APIs, Web UI - adapters that expose application functionality

- **Domain-Driven Design**:
  - **Ubiquitous Language**: Domain terms in code must match business terminology exactly - no technical jargon for business concepts
  - **Bounded Contexts**: Explicitly define context boundaries - same term can mean different things in different contexts
  - **Aggregates**: Group related entities under an aggregate root - enforce invariants, control transactional boundaries
  - **Value Objects**: Immutable objects defined by their attributes (e.g., Money, Address) - no identity, compared by value
  - **Domain Events**: Model significant business events explicitly - enable loose coupling between bounded contexts
  - **Anti-Corruption Layer**: When integrating external systems, translate their model to your domain model

- **Dependency Injection**: Use constructor injection for dependencies - makes testing easier, dependencies explicit

- **Interface Segregation**: Define interfaces in the layer that uses them (not where they're implemented) - domain defines repository interfaces, infrastructure implements them

**Rationale**: Clean Architecture ensures the core business logic remains independent of frameworks, databases, and UI. DDD ensures the code reflects the business domain accurately, making it easier for domain experts and developers to collaborate.

## Development Standards

### Technology Stack

- **Language**: Java 21 (LTS) - leverage modern Java features when they improve code clarity or safety
- **Java 21 Features to Prefer**:
  - **Virtual Threads** (Project Loom): Use for high-concurrency scenarios instead of traditional thread pools
  - **Record Patterns**: Use for pattern matching in switch expressions and instanceof checks
  - **Pattern Matching for switch**: Prefer over traditional switch or if-else chains when handling polymorphic types
  - **Sequenced Collections**: Use for collections where order matters (SequencedSet, SequencedMap)
  - **String Templates** (Preview): Use when stable for safer string interpolation
  - **Records**: Use for immutable data carriers, especially Value Objects in DDD
  - **Sealed Classes**: Use to restrict inheritance hierarchies, especially for domain modeling

- **Framework**: Spring Boot 3.x (requires Java 17+, fully supports Java 21)
  - Use Spring Boot starters for infrastructure concerns (data, web, security)
  - Keep domain layer free of Spring annotations (Principle VI - Clean Architecture)
  - Use Spring's dependency injection only in infrastructure and presentation layers
  - Leverage Spring Boot Actuator for observability (metrics, health checks)

- **Database Options** (choose based on feature requirements):
  - **PostgreSQL**: Preferred for relational data, ACID transactions, complex queries
  - **MongoDB**: Use for document-oriented data, flexible schemas, high write throughput
  - **Microsoft SQL Server**: Use when integration with existing MSSQL infrastructure required
  - **Multi-Database**: Polyglot persistence allowed per bounded context if justified

- **Build & Deployment**:
  - **Build Tool**: Maven (mandatory) - use Spring Boot Maven plugin
    - Standard directory structure: `src/main/java`, `src/main/resources`, `src/test/java`
    - Use Maven profiles for different environments (dev, test, prod)
    - Dependency management via `<dependencyManagement>` for version consistency
    - Use Maven Wrapper (mvnw) for build reproducibility
  - **Containerization**: Docker mandatory - all services must be containerizable
    - Multi-stage builds to minimize image size
    - Use official Java 21 base images (eclipse-temurin:21-jre-alpine or similar)
    - Externalize configuration for different environments
  - **Orchestration**: Kubernetes for production deployment
    - Define Kubernetes manifests (Deployments, Services, ConfigMaps, Secrets)
    - Use Kubernetes ConfigMaps/Secrets for environment-specific configuration
    - Health checks (liveness, readiness) required for all services
    - Resource limits (CPU, memory) must be defined

- **Testing**: JUnit 5, AssertJ, Mockito, ArchUnit (for architecture validation), Testcontainers (for integration tests with real databases)

- **Logging Standards** (SLF4J with Logback - Spring Boot default):
  - **Log Levels**:
    - **ERROR**: System errors, exceptions requiring immediate attention
    - **WARN**: Unexpected situations that don't prevent operation (deprecated API usage, poor configuration)
    - **INFO**: Significant business events, startup/shutdown, configuration changes
    - **DEBUG**: Detailed flow information for troubleshooting (disabled in production)
    - **TRACE**: Very detailed diagnostic information (disabled in production)

  - **Structured Logging**:
    - Use **JSON format** in production for log aggregation (Logstash, ELK, Splunk)
    - Include mandatory fields: `timestamp`, `level`, `logger`, `message`, `thread`, `correlationId`
    - Use MDC (Mapped Diagnostic Context) for request-scoped data (userId, sessionId, traceId)
    - Example: `log.info("Order placed successfully", kv("orderId", orderId), kv("userId", userId))`

  - **What to Log**:
    - MUST log: API requests/responses (with correlation ID), business events, exceptions with stack traces
    - MUST NOT log: Sensitive data (passwords, tokens, PII), large payloads (>1KB - log reference/summary)
    - Database queries: Log slow queries (>100ms) at WARN level
    - External service calls: Log all outbound requests with latency

  - **Log Context**:
    - Every log entry must have a correlation ID for request tracing
    - Use Spring Boot's `TraceId`/`SpanId` from Micrometer Tracing (Sleuth successor)
    - Log request entry/exit at service boundaries (controllers, message consumers)

  - **Configuration**:
    - Separate log files by concern: `application.log`, `error.log`, `audit.log`
    - Configure log rotation (daily or 100MB max per file)
    - Retain logs for minimum 30 days (adjust per compliance requirements)
    - Console output for local development, JSON file output for production

### Code Organization

- **Clean Architecture Structure** (following Principle VI):
  ```
  src/
  ├── domain/                    # Domain Layer (no external dependencies)
  │   ├── model/                 # Entities, Value Objects, Aggregates
  │   ├── event/                 # Domain Events
  │   └── repository/            # Repository interfaces (not implementations)
  ├── application/               # Application Layer
  │   ├── usecase/               # Use Cases / Application Services
  │   ├── port/                  # Input/Output ports (interfaces)
  │   └── dto/                   # Data Transfer Objects
  ├── infrastructure/            # Infrastructure Layer
  │   ├── persistence/           # Repository implementations, JPA, etc.
  │   ├── messaging/             # Event publishers, message brokers
  │   ├── external/              # External service clients
  │   └── config/                # Framework configuration
  └── presentation/              # Presentation Layer
      ├── cli/                   # Command-line interfaces
      ├── rest/                  # REST API controllers (if applicable)
      └── dto/                   # API-specific DTOs

  tests/
  ├── unit/                      # Unit tests (domain, application logic)
  ├── integration/               # Integration tests (cross-layer)
  ├── contract/                  # Contract tests (API, repository contracts)
  └── architecture/              # ArchUnit tests (dependency rules)
  ```

- **Dependency Direction**: STRICTLY enforce - outer layers depend on inner, never reverse
  - Domain depends on: NOTHING (pure Java, no frameworks)
  - Application depends on: Domain
  - Infrastructure depends on: Domain, Application (implements interfaces defined there)
  - Presentation depends on: Application (not Infrastructure directly)

- **Namespace Clarity**: Package names must reflect both layer and purpose (e.g., `domain.model.order`, `application.usecase.placeorder`)

### Design Patterns for Maintainability & Extensibility

Code MUST apply established design patterns to ensure maintainability and extensibility:

- **Creational Patterns**:
  - **Factory Pattern**: Use for complex object creation, especially Aggregates with validation rules
  - **Builder Pattern**: Use for objects with many optional parameters (prefer Records with `@Builder` from Lombok)
  - **Singleton**: Avoid - use Spring's dependency injection with `@Bean` scopes instead

- **Structural Patterns**:
  - **Adapter Pattern**: MANDATORY for external integrations (Anti-Corruption Layer in DDD)
  - **Decorator Pattern**: Use for cross-cutting concerns when AOP is not appropriate
  - **Facade Pattern**: Use to simplify complex subsystem interactions (e.g., payment processing with multiple steps)
  - **Repository Pattern**: MANDATORY for data access (defined in domain, implemented in infrastructure)

- **Behavioral Patterns**:
  - **Strategy Pattern**: Use for interchangeable algorithms (e.g., pricing strategies, payment methods)
  - **Template Method Pattern**: Use for invariant workflows with customizable steps
  - **Observer Pattern**: Use for domain events (Spring's ApplicationEventPublisher)
  - **Chain of Responsibility**: Use for validation pipelines, request processing
  - **Command Pattern**: Use for use cases in application layer (Command/CommandHandler)

- **DDD Tactical Patterns** (see Principle VI):
  - **Entity**: Objects with identity and lifecycle
  - **Value Object**: Immutable objects compared by value (use Java Records)
  - **Aggregate**: Consistency boundary with root entity
  - **Repository**: Persistence abstraction for Aggregates
  - **Domain Service**: Stateless operations that don't belong to a single Entity
  - **Application Service**: Use case orchestration (coordinates domain objects)
  - **Domain Event**: Publish significant business occurrences

- **Anti-Patterns to Avoid**:
  - **God Object**: Classes with too many responsibilities (use SRP - Single Responsibility Principle)
  - **Anemic Domain Model**: Domain objects with only getters/setters, no behavior
  - **Transaction Script**: All logic in service methods without domain model
  - **Big Ball of Mud**: No clear architecture boundaries (enforce with ArchUnit)

**Rationale**: Consistent use of proven patterns makes code predictable, easier to understand, test, and extend. Patterns provide a shared vocabulary for the team.

### REST API Conventions

All REST APIs MUST follow these conventions:

- **Resource Naming**:
  - Use plural nouns for collections: `/orders`, `/customers`, `/products`
  - Use hierarchical paths for relationships: `/orders/{orderId}/items`
  - Use lowercase with hyphens for multi-word resources: `/shipping-addresses`
  - Avoid verbs in URIs (use HTTP methods instead): ❌ `/createOrder` → ✅ `POST /orders`

- **HTTP Methods** (standard CRUD operations):
  - **GET**: Retrieve resource(s) - must be idempotent and safe (no side effects)
  - **POST**: Create new resource - returns `201 Created` with `Location` header
  - **PUT**: Replace entire resource - must be idempotent
  - **PATCH**: Partial update of resource - use for selective field updates
  - **DELETE**: Remove resource - must be idempotent, returns `204 No Content` on success

- **HTTP Status Codes** (use standard codes):
  - **2xx Success**: `200 OK`, `201 Created`, `204 No Content`
  - **4xx Client Errors**: `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`
  - **5xx Server Errors**: `500 Internal Server Error`, `503 Service Unavailable`

- **Request/Response Format**:
  - **Content-Type**: Use `application/json` for request/response bodies
  - **Request Validation**: Return `400 Bad Request` with detailed error messages for invalid input
  - **Error Response Format**:
    ```json
    {
      "timestamp": "2025-11-01T10:30:00Z",
      "status": 400,
      "error": "Bad Request",
      "message": "Validation failed",
      "path": "/orders",
      "correlationId": "abc-123-def",
      "errors": [
        {"field": "quantity", "message": "must be greater than 0"}
      ]
    }
    ```

- **Pagination** (for collections):
  - Use query parameters: `?page=0&size=20&sort=createdAt,desc`
  - Response must include metadata: `totalElements`, `totalPages`, `size`, `number`
  - Use Spring Data's `Pageable` and `Page<T>` abstractions

- **Filtering & Searching**:
  - Use query parameters for filtering: `/orders?status=PENDING&customerId=123`
  - Use `/search` endpoint for complex queries: `POST /orders/search` with search criteria in body

- **Versioning**:
  - Use URI versioning: `/api/v1/orders`, `/api/v2/orders`
  - Never break backward compatibility within a version
  - Deprecation notice required before removing endpoints (one version warning period)

- **Security**:
  - All endpoints require authentication (except public endpoints explicitly documented)
  - Use JWT tokens with Bearer scheme: `Authorization: Bearer <token>`
  - CORS configuration must be explicit (no wildcard `*` in production)
  - Rate limiting must be implemented (e.g., 100 requests/minute per client)

- **Documentation**:
  - Use OpenAPI 3.0 (Swagger) for API documentation
  - Include examples for all request/response payloads
  - Document error responses with status codes
  - Use `@Operation`, `@ApiResponse` annotations in controllers

**Rationale**: Consistent REST conventions make APIs predictable and easy to consume. Following HTTP standards enables proper caching, error handling, and client tooling.

### Versioning & Breaking Changes

- **Semantic Versioning**: MAJOR.MINOR.PATCH format strictly enforced
  - MAJOR: Breaking changes to public contracts
  - MINOR: New features, backward-compatible additions
  - PATCH: Bug fixes, performance improvements, internal refactoring
- **Deprecation Policy**: Breaking changes require one MINOR version deprecation warning before removal in next MAJOR version
- **Changelog Required**: All version bumps must document changes in CHANGELOG.md

### Documentation Requirements

- **README.md**: Every library/service must have a README with:
  - Purpose and business context
  - Installation and build instructions
  - Local development setup (including database)
  - Quick start guide
  - Links to detailed documentation

- **API Documentation**: All public functions/classes must have docstrings with parameter descriptions, return types, and examples

- **Architecture Decision Records (ADRs)**: Significant design decisions must be documented with context, decision, and consequences (e.g., database choice, framework selection, bounded context boundaries)

- **Deployment Documentation**:
  - **Dockerfile**: Must be present at repository root with comments explaining build stages
  - **docker-compose.yml**: For local development with all required services (database, message broker, etc.)
  - **Kubernetes Manifests**: In `k8s/` directory with separate files for dev/staging/prod environments
  - **Environment Variables**: Document all required environment variables with examples in `.env.example`

## Quality Gates

All code changes MUST pass these gates before merging:

1. **Constitution Compliance Check**:
   - Library-first structure verified
   - CLI interface present and functional
   - Tests written before implementation (git history or explicit confirmation)
   - Contract and integration tests present for applicable changes
   - Clean Architecture layer boundaries respected (ArchUnit tests pass)
   - Domain layer has no framework dependencies (ArchUnit validation)

2. **Test Success**:
   - All unit tests pass (domain and application logic)
   - All contract tests pass (API and repository contracts)
   - All integration tests pass (cross-layer workflows)
   - All architecture tests pass (ArchUnit dependency rules)
   - Code coverage does not decrease (baseline: 80% for domain/application, 70% for infrastructure/presentation)

3. **Code Review**:
   - At least one reviewer approval required
   - Complexity justifications reviewed and approved
   - Documentation completeness verified
   - No security vulnerabilities (OWASP Top 10 checks)
   - Design patterns appropriately applied (no anti-patterns)
   - REST API conventions followed (if REST endpoints present)
   - Logging standards adhered to (proper levels, structured logging, no sensitive data)

4. **Build & Integration**:
   - Clean build with no warnings
   - Integration with existing components verified
   - Performance benchmarks within acceptable thresholds

5. **Containerization & Deployment**:
   - Docker image builds successfully
   - Docker image size optimized (use multi-stage builds)
   - Container runs and passes health checks
   - Kubernetes manifests validated (kubectl dry-run or kubeval)
   - All environment variables documented

## Governance

### Authority & Amendments

- This constitution supersedes all other development practices and guidelines
- Amendments require:
  1. Written proposal with rationale
  2. Team discussion and consensus
  3. Documentation of impact on existing code
  4. Migration plan if changes affect current projects
  5. Version bump following semantic versioning rules

### Enforcement

- All pull requests MUST verify constitution compliance via automated checks where possible
- Code reviewers MUST reject non-compliant changes with citation to specific principle violated
- Complexity introduced in violation of Principle V (Simplicity) MUST be explicitly justified in PR description and approved by tech lead

### Living Document

- This constitution is a living document that evolves with the project
- Review constitution relevance quarterly
- Outdated or impractical principles must be amended, not ignored
- Principles found to harm productivity must be revised, not worked around

**Version**: 1.1.2 | **Ratified**: 2025-11-01 | **Last Amended**: 2025-11-01
