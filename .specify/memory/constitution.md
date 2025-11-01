<!--
SYNC IMPACT REPORT
==================

Version Change: 1.1.0 → 1.1.1
Change Type: PATCH (technology stack clarification and deployment requirements)
Date: 2025-11-01

Modified Sections:
  - Development Standards > Technology Stack (CLARIFIED):
    - **Framework**: Spring Boot 3.x specified as mandatory framework (was generic)
      - Added Spring-specific guidance (starters, DI boundaries, Actuator)
      - Emphasized domain layer must remain Spring-annotation-free
    - **Database Options**: Specified three approved databases with guidance:
      - PostgreSQL (preferred for relational data)
      - MongoDB (for document-oriented data)
      - Microsoft SQL Server (for MSSQL infrastructure integration)
      - Polyglot persistence allowed per bounded context if justified
    - **Build & Deployment**: Added comprehensive containerization and orchestration requirements:
      - Docker mandatory with multi-stage builds
      - Java 21 base images specified (eclipse-temurin:21-jre-alpine)
      - Kubernetes mandatory for production deployment
      - ConfigMaps/Secrets, health checks, resource limits required
    - **Testing**: Added Testcontainers for database integration tests
    - **Logging**: Specified Logback as Spring Boot default with structured JSON for production

  - Development Standards > Documentation Requirements (EXPANDED):
    - Added deployment documentation requirements:
      - Dockerfile with commented build stages (mandatory)
      - docker-compose.yml for local development (mandatory)
      - Kubernetes manifests in k8s/ directory (mandatory)
      - .env.example for environment variables (mandatory)

  - Quality Gates (EXPANDED):
    - Added Gate 5: Containerization & Deployment
      - Docker image builds successfully
      - Image size optimization verified
      - Container health checks validated
      - Kubernetes manifests validated (kubectl dry-run)
      - Environment variables documented

Modified Templates:
  - plan-template.md (UPDATED):
    - Spring Boot 3.x specified as default framework
    - Database selection with three approved options
    - Containerization details (Docker base image, docker-compose)
    - Kubernetes orchestration requirements (deployment strategy, ingress)
    - Added deployment files to project structure (Dockerfile, k8s/, .env.example)
    - Added Testcontainers to testing stack

Principles Changed: None
Added Sections: None
Removed Sections: None

Templates Status:
  ✅ plan-template.md - UPDATED with Spring Boot, database options, Docker/Kubernetes
  ✅ spec-template.md - User scenarios align with testing principles (no changes needed)
  ✅ tasks-template.md - Previously updated for mandatory TDD (no additional changes)
  ✅ Commands - Validated, no agent-specific references found

Follow-up TODOs:
  - Consider creating example Dockerfile with multi-stage build
  - Consider creating example docker-compose.yml with PostgreSQL/MongoDB/MSSQL options
  - Consider creating example Kubernetes manifests (base + overlays)
  - Consider creating ArchUnit test examples
  - Document Spring Boot + Clean Architecture integration patterns
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
  - **Build Tool**: Maven or Gradle (document choice in README, include Spring Boot plugin)
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
- **Logging**: SLF4J with Logback (Spring Boot default) - structured JSON logging for production

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

**Version**: 1.1.1 | **Ratified**: 2025-11-01 | **Last Amended**: 2025-11-01
