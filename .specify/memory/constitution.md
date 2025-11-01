<!--
SYNC IMPACT REPORT
==================

Version Change: TEMPLATE → 1.0.0
Change Type: INITIAL RATIFICATION
Date: 2025-11-01

Modified Principles:
  - NEW: I. Library-First Architecture
  - NEW: II. CLI-Driven Interfaces
  - NEW: III. Test-First Development (NON-NEGOTIABLE)
  - NEW: IV. Contract & Integration Testing
  - NEW: V. Observability & Simplicity

Added Sections:
  - Core Principles (5 principles)
  - Development Standards
  - Quality Gates
  - Governance

Removed Sections:
  - None (initial version)

Templates Status:
  ✅ plan-template.md - Constitution Check gate validated (no changes needed)
  ✅ spec-template.md - User scenarios align with testing principles (no changes needed)
  ✅ tasks-template.md - UPDATED to enforce mandatory TDD (tests now required, not optional)
  ✅ Commands - Validated, no agent-specific references found

Follow-up TODOs:
  - None at this time
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

## Development Standards

### Code Organization

- **Single Project Structure**: Use `src/` for implementation, `tests/` for test code (unless web/mobile app requirements dictate frontend/backend separation)
- **Namespace Clarity**: File and module names must clearly indicate their purpose and layer (models, services, cli, lib)
- **Dependency Direction**: Dependencies flow inward - libraries depend on nothing, services depend on libraries, CLI depends on services

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

2. **Test Success**:
   - All unit tests pass
   - All contract tests pass
   - All integration tests pass
   - Code coverage does not decrease (baseline: 80% for libraries, 70% for services)

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

**Version**: 1.0.0 | **Ratified**: 2025-11-01 | **Last Amended**: 2025-11-01
