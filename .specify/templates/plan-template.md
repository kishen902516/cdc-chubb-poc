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

**Language/Version**: Java 21 (LTS) [default per constitution]
**Framework**: Spring Boot 3.x [default per constitution - specify version]
**Architecture Style**: Clean Architecture + DDD [default per constitution Principle VI]
**Database**: [PostgreSQL (preferred) | MongoDB | Microsoft SQL Server - choose based on data requirements]
**Primary Dependencies**:
  - Spring Boot Starters: [e.g., spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation]
  - API Documentation: springdoc-openapi (OpenAPI 3.0/Swagger) [mandatory for REST APIs]
  - Logging: logback with logstash-logback-encoder [for structured JSON logging]
  - Additional: [e.g., Spring Cloud, Lombok, MapStruct, Micrometer for metrics or NEEDS CLARIFICATION]
**Testing**: JUnit 5, AssertJ, Mockito, ArchUnit, Testcontainers [default per constitution]
**Build Tool**: Maven with Spring Boot Maven Plugin [mandatory per constitution]
  - Maven Wrapper (mvnw) for reproducible builds
  - Maven profiles: dev, test, prod
**Containerization**: Docker [mandatory per constitution]
  - Base Image: [e.g., eclipse-temurin:21-jre-alpine]
  - Additional Tools: [e.g., docker-compose for local development]
**Orchestration**: Kubernetes [mandatory for production per constitution]
  - Deployment Strategy: [e.g., Rolling Update, Blue-Green, Canary]
  - Ingress: [e.g., NGINX, Traefik, or cloud-specific]
**Target Platform**: JVM 21+ in Docker containers on Kubernetes [default per constitution]
**Project Type**: [single/web/mobile/microservices - determines source structure]
**Performance Goals**: [domain-specific, e.g., 1000 req/s, <100ms p95 latency, 10k concurrent users or NEEDS CLARIFICATION]
**Constraints**: [domain-specific, e.g., <200ms p95, <512MB memory per pod, offline-capable or NEEDS CLARIFICATION]
**Scale/Scope**: [domain-specific, e.g., 10k users, 100k transactions/day, 5 microservices or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [ ] Library-first architecture verified
- [ ] CLI interface present and functional (Principle II)
- [ ] Test-First Development plan in place (tests written before implementation)
- [ ] Contract and integration tests planned (Principle IV)
- [ ] Clean Architecture layers defined (domain, application, infrastructure, presentation)
- [ ] Domain layer has no framework dependencies
- [ ] Design patterns identified for key components (Factory, Strategy, Repository, etc.)
- [ ] REST API conventions planned (if REST endpoints) - resource naming, HTTP methods, status codes
- [ ] Logging standards defined - structured JSON, correlation IDs, MDC context
- [ ] ArchUnit tests planned for architecture validation

## REST API Design (if applicable)

*Complete this section if the feature exposes REST APIs*

**Base Path**: `/api/v1/[resource-name]` [follow URI versioning convention]

**Resources & Endpoints**:
| Method | Path | Description | Request Body | Response | Status Codes |
|--------|------|-------------|--------------|----------|--------------|
| GET | `/orders` | List all orders (paginated) | None | `Page<OrderDTO>` | 200 |
| GET | `/orders/{id}` | Get order by ID | None | `OrderDTO` | 200, 404 |
| POST | `/orders` | Create new order | `CreateOrderRequest` | `OrderDTO` | 201, 400 |
| PUT | `/orders/{id}` | Replace order | `UpdateOrderRequest` | `OrderDTO` | 200, 404 |
| PATCH | `/orders/{id}` | Partially update | `PatchOrderRequest` | `OrderDTO` | 200, 404 |
| DELETE | `/orders/{id}` | Delete order | None | None | 204, 404 |

**Error Handling**:
- All errors return standard error response format with `correlationId`
- Validation errors: 400 with field-level error details
- Not found errors: 404 with resource identifier
- Business rule violations: 422 with business error code

**Security**:
- Authentication: [JWT Bearer tokens | OAuth2 | API Keys]
- Authorization: [Role-based | Permission-based | Resource-based]
- Rate limiting: [e.g., 100 requests/minute per client]

**OpenAPI Documentation**:
- Use `@Operation`, `@ApiResponse`, `@Schema` annotations
- Include request/response examples
- Document all error scenarios

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
├── integration/                    # Integration tests (cross-layer, use Testcontainers)
│   └── usecase/
└── contract/                       # Contract tests (API, repositories)
    ├── rest/
    └── persistence/

# Repository Root (deployment files - mandatory per constitution)
Dockerfile                          # Multi-stage build for production image
docker-compose.yml                  # Local development environment (app + database)
.env.example                        # Template for environment variables
.dockerignore                       # Exclude unnecessary files from Docker context

k8s/                                # Kubernetes manifests
├── base/                           # Base manifests (common across environments)
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── dev/                            # Development-specific overlays
├── staging/                        # Staging-specific overlays
└── prod/                           # Production-specific overlays

# Build files (mandatory per constitution)
pom.xml                             # Maven build with Spring Boot plugin
mvnw, mvnw.cmd                      # Maven Wrapper for reproducible builds
.mvn/                               # Maven Wrapper configuration

# Configuration files
src/main/resources/
├── application.yml                 # Main configuration (use YAML over properties)
├── application-dev.yml             # Development profile
├── application-test.yml            # Test profile
├── application-prod.yml            # Production profile
├── logback-spring.xml              # Logging configuration (structured JSON for prod)
└── db/migration/                   # Database migrations (Flyway or Liquibase)

README.md                           # Project documentation (mandatory per constitution)

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
