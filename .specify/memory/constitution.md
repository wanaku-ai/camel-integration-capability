<!--
  Sync Impact Report
  ==================
  Version: 0.0.0 → 1.0.0 (MAJOR - initial constitution ratification)

  Modified Principles:
  - N/A (new constitution)

  Added Sections:
  - I. Code Quality & Maintainability
  - II. Testing Standards
  - III. User Experience Consistency
  - IV. Performance Requirements
  - Quality Gates
  - Development Workflow
  - Governance

  Removed Sections:
  - N/A

  Templates Requiring Updates:
  - .specify/templates/plan-template.md ✅ (Constitution Check section already present)
  - .specify/templates/spec-template.md ✅ (Success Criteria aligns with performance principles)
  - .specify/templates/tasks-template.md ✅ (Test phases align with testing principles)

  Follow-up TODOs:
  - None
-->
# Camel Integration Capability Constitution

## Core Principles

### I. Code Quality & Maintainability

All code MUST adhere to consistent quality standards ensuring long-term maintainability and team productivity.

**Non-Negotiable Rules**:
- All production code MUST pass static analysis without warnings (Checkstyle, SpotBugs)
- Public APIs MUST include Javadoc documentation with @param, @return, and @throws tags
- Methods MUST NOT exceed 50 lines; classes MUST NOT exceed 500 lines
- Cyclomatic complexity MUST NOT exceed 10 per method
- Code duplication MUST be eliminated; DRY principle strictly enforced
- All changes MUST pass code review by at least one maintainer
- MUST follow existing patterns in the codebase; new patterns require justification

**Rationale**: Consistent code quality reduces onboarding time, minimizes bugs, and enables sustainable velocity.

### II. Testing Standards

Testing is MANDATORY for all production code. Quality gates MUST NOT be bypassed.

**Non-Negotiable Rules**:
- Unit test coverage MUST be at least 80% for new code
- Integration tests MUST cover all gRPC endpoints and Camel route executions
- Contract tests MUST verify MCP protocol compliance
- Tests MUST be deterministic; flaky tests MUST be fixed or quarantined immediately
- Tests MUST NOT depend on external services; use WireMock/testcontainers for isolation
- All tests MUST pass before merge; no exceptions
- Performance-critical paths MUST include benchmark tests

**Rationale**: Comprehensive testing prevents regressions, enables confident refactoring, and ensures production reliability.

### III. User Experience Consistency

All user-facing interfaces MUST provide predictable, intuitive, and well-documented behavior.

**Non-Negotiable Rules**:
- CLI arguments MUST follow POSIX conventions; long options MUST be descriptive
- Error messages MUST include: what went wrong, why, and how to fix it
- All user-facing changes MUST update corresponding documentation
- Breaking changes to CLI or configuration MUST be announced via deprecation warnings for at least one release
- Logs MUST use structured format (JSON) for machine parseability; human-readable mode available for development
- Exit codes MUST be consistent: 0 for success, non-zero for failures with documented meanings
- Startup messages MUST include version and essential configuration summary

**Rationale**: Predictable UX reduces support burden and improves adoption for AI agent integrations.

### IV. Performance Requirements

The capability service MUST meet performance targets to support production AI workloads.

**Non-Negotiable Rules**:
- Route execution latency MUST NOT exceed 200ms p95 for passthrough routes (excluding backend time)
- Memory footprint MUST NOT exceed 512MB heap under normal operation
- Service startup MUST complete within 30 seconds including route loading
- gRPC endpoints MUST handle at least 100 concurrent requests
- No blocking calls on event loop threads; async I/O MUST be used for network operations
- Performance regressions >10% MUST be investigated and justified before merge
- Resource cleanup MUST be verified; no memory/connection leaks

**Rationale**: AI agent workflows depend on low-latency, reliable integrations; poor performance directly impacts AI response quality.

## Quality Gates

**GATE: Must pass before any PR merge**

| Check | Requirement | Tool |
|-------|-------------|------|
| Compilation | Zero errors, zero warnings | Maven |
| Static Analysis | Zero violations | Checkstyle, SpotBugs |
| Unit Tests | 100% pass, ≥80% coverage (new code) | JUnit 5, JaCoCo |
| Integration Tests | 100% pass | Testcontainers |
| Documentation | Updated for user-facing changes | Manual review |
| Performance | No regressions >10% | JMH benchmarks |

## Development Workflow

**Code Review Requirements**:
- At least one approval from a maintainer
- All CI checks MUST pass
- No unresolved conversations
- Commit messages MUST follow Conventional Commits format

**Branch Strategy**:
- `main` branch MUST always be deployable
- Feature branches MUST be prefixed with issue number: `ci-issue-[number]`
- Rebase before merge to maintain linear history

**Release Process**:
- Semantic versioning MUST be followed
- CHANGELOG MUST be updated with user-facing changes
- Breaking changes MUST increment MAJOR version

## Governance

This Constitution is the authoritative source for development practices. All PRs and reviews MUST verify compliance.

**Amendment Procedure**:
1. Propose change via PR to constitution.md
2. Require approval from at least two maintainers
3. Document rationale for change
4. Update version according to semantic rules below

**Versioning Policy**:
- MAJOR: Backward-incompatible principle changes or removals
- MINOR: New principles or materially expanded guidance
- PATCH: Clarifications, typo fixes, non-semantic refinements

**Compliance Review**:
- Constitution compliance MUST be verified in code review
- Violations MUST be documented with justification or rejected
- Quarterly review of principles for relevance

**Version**: 1.0.0 | **Ratified**: 2026-02-07 | **Last Amended**: 2026-02-07
