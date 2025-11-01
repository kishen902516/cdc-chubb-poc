# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21 (LTS) [default per constitution, or specify alternative with justification]
**Architecture Style**: Clean Architecture + DDD [default per constitution Principle VI]
**Primary Dependencies**: [e.g., Spring Boot 3.x, Quarkus, Micronaut, or plain Java or NEEDS CLARIFICATION]
**Storage**: [if applicable, e.g., PostgreSQL, MongoDB, H2, files or N/A]
**Testing**: JUnit 5, AssertJ, Mockito, ArchUnit [default per constitution]
**Build Tool**: [Maven or Gradle - document choice]
**Target Platform**: [e.g., JVM 21+, Docker containers, Kubernetes, serverless or NEEDS CLARIFICATION]
**Project Type**: [single/web/mobile - determines source structure]
**Performance Goals**: [domain-specific, e.g., 1000 req/s, <100ms p95 latency, 60 fps or NEEDS CLARIFICATION]
**Constraints**: [domain-specific, e.g., <200ms p95, <512MB memory, offline-capable or NEEDS CLARIFICATION]
**Scale/Scope**: [domain-specific, e.g., 10k users, 100k transactions/day, 50 microservices or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

[Gates determined based on constitution file]

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths and bounded contexts. The delivered plan must not include Option labels.

  DEFAULT: Use Clean Architecture structure per constitution Principle VI.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project - Clean Architecture (DEFAULT per constitution)
src/main/java/com/[company]/[project]/
├── domain/                         # Domain Layer (no external dependencies)
│   ├── model/                      # Entities, Value Objects, Aggregates
│   │   ├── [boundedcontext1]/      # e.g., order/, customer/, payment/
│   │   │   ├── [Aggregate].java    # Aggregate root
│   │   │   ├── [Entity].java       # Entity within aggregate
│   │   │   └── [ValueObject].java  # Value objects (Records recommended)
│   │   └── [boundedcontext2]/
│   ├── event/                      # Domain Events
│   │   └── [Event].java            # e.g., OrderPlacedEvent
│   └── repository/                 # Repository interfaces (ports)
│       └── [Aggregate]Repository.java
├── application/                    # Application Layer
│   ├── usecase/                    # Use Cases
│   │   └── [UseCase]UseCase.java   # e.g., PlaceOrderUseCase
│   ├── port/                       # Input/Output ports
│   │   ├── input/                  # Driving ports (use case interfaces)
│   │   └── output/                 # Driven ports (external services)
│   └── dto/                        # Application DTOs
├── infrastructure/                 # Infrastructure Layer
│   ├── persistence/                # Repository implementations
│   │   ├── jpa/                    # JPA entities & repositories
│   │   └── mapper/                 # Entity ↔ Domain model mappers
│   ├── messaging/                  # Event publishing, message brokers
│   ├── external/                   # External service clients
│   └── config/                     # Spring/framework configuration
└── presentation/                   # Presentation Layer
    ├── cli/                        # CLI commands (per constitution Principle II)
    │   └── [Feature]Command.java
    └── rest/                       # REST controllers (if applicable)
        └── [Resource]Controller.java

src/test/java/com/[company]/[project]/
├── architecture/                   # ArchUnit tests (mandatory per constitution)
│   └── ArchitectureTest.java       # Layer dependency rules
├── unit/                           # Unit tests (domain, application)
│   ├── domain/
│   └── application/
├── integration/                    # Integration tests (cross-layer)
│   └── usecase/
└── contract/                       # Contract tests (API, repositories)
    ├── rest/
    └── persistence/

# [REMOVE IF UNUSED] Option 2: Web application with separate frontend/backend
backend/
└── [Use Clean Architecture structure from Option 1 above]

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Microservices (multiple bounded contexts as separate services)
[service-name]/                     # One directory per bounded context/service
└── [Use Clean Architecture structure from Option 1 above]

# [REMOVE IF UNUSED] Option 4: Mobile + API
api/
└── [Use Clean Architecture structure from Option 1 above]

mobile/
├── ios/ or android/
└── [platform-specific structure]
```

**Structure Decision**: [Document the selected structure, bounded contexts identified,
and how they map to the directory structure above. For DDD, explicitly list bounded
contexts and their relationships.]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
