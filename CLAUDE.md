# camel-core-downstream-service Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-07

## Active Technologies

- Java 21 + Apache Camel 4.18.2, Wanaku SDK 0.1.1, picocli 4.7.7, gRPC (001-multi-module-split)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Java 21

## Code Style

Java 21: Follow standard conventions

## Recent Changes

- 001-multi-module-split: Added Java 21 + Apache Camel 4.18.2, Wanaku SDK 0.1.1, picocli 4.7.7, gRPC

<!-- MANUAL ADDITIONS START -->

## Documentation

- The primary user-facing documentation is `docs/usage.md` — this is the file published to the documentation website.
- When adding new documentation files, always link them from `docs/usage.md` (in the "Related Guides" section), not just from `README.md`.
- Changelogs are handled automatically by jreleaser — do not create or maintain a manual CHANGELOG.md.

## Acceptance Criteria

- No code quality degradation after running the static code analyzer

<!-- MANUAL ADDITIONS END -->
