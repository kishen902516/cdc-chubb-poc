<!--
SYNC IMPACT REPORT
==================

Version Change: 1.0.0 → 1.1.0
Change Type: MINOR (new principle and technology guidance added)
Date: 2025-11-01

Modified Principles:
  - NEW: VI. Clean Architecture & Domain-Driven Design
    - Added Clean Architecture layer structure (Domain, Application, Infrastructure, Presentation)
    - Added DDD concepts (Ubiquitous Language, Bounded Contexts, Aggregates, Value Objects, Domain Events)
    - Added dependency rules and interface segregation guidance

Modified Sections:
  - Development Standards > Technology Stack (NEW):
    - Java 21 (LTS) designated as primary language
    - Java 21 feature guidance (Virtual Threads, Records, Pattern Matching, Sealed Classes, etc.)
    - Build tools, testing frameworks, logging standards specified

  - Development Standards > Code Organization (EXPANDED):
    - Updated from generic structure to Clean Architecture layers
    - Added detailed package structure following DDD/Clean Architecture
    - Strengthened dependency direction rules with layer-specific constraints

  - Quality Gates > Compliance & Testing (EXPANDED):
    - Added ArchUnit tests for architecture validation
    - Added domain layer framework independence check
    - Updated code coverage baselines per layer

Added Sections:
  - Technology Stack (Java 21 focus with modern feature guidance)

Removed Sections:
  - None

Templates Status:
  ✅ plan-template.md - UPDATED with Java 21 defaults and Clean Architecture structure
  ✅ spec-template.md - User scenarios align with testing principles (no changes needed)
  ✅ tasks-template.md - Previously updated for mandatory TDD (no additional changes)
  ✅ Commands - Validated, no agent-specific references found

Follow-up TODOs:
  - Consider creating ArchUnit test examples in templates
  - Document DDD tactical patterns (Entity, Value Object, Aggregate) in developer guide when created
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

- **Build Tool**: Maven or Gradle (document choice in README)
- **Testing**: JUnit 5, AssertJ, Mockito, ArchUnit (for architecture validation)
- **Logging**: SLF4J with Logback or Log4j2

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

- **README.md**: Every library must have a README with purpose, installation, quick start, and links to detailed docs
- **API Documentation**: All public functions/classes must have docstrings with parameter descriptions, return types, and examples
- **Architecture Decision Records (ADRs)**: Significant design decisions must be documented with context, decision, and consequences

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

**Version**: 1.1.0 | **Ratified**: 2025-11-01 | **Last Amended**: 2025-11-01
