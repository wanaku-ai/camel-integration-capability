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

- The website publishes the `docs/` tree. Each folder of `docs/` is a section with an `index.md` hub.
- When you add a documentation page, add it to `docs/nav.json` and to the hub of its section.
- When you move or rename a page, add a line with the old and the new slug to `docs/redirects.txt`.
- Follow the page conventions in `docs/contributing/documentation.md`, and run `python3 .github/check-docs-nav.py` before you push.
- Changelogs are handled automatically by jreleaser — do not create or maintain a manual CHANGELOG.md.

## Acceptance Criteria

- No code quality degradation after running the static code analyzer

<!-- MANUAL ADDITIONS END -->
