# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - 0.1.1-SNAPSHOT

### Added
- JUnit test results archiving as CI artifacts
- Preliminary Forage support for dependency downloading
- ServiceTarget in WanakuRegistrationInfo for service targeting

### Changed
- Configurable Keycloak realm (Fix #86)
- ExecutorService for registration management

### Fixed
- gRPC server now starts before registration to fix health check race condition (Fix #82)
- Ignoring subsequent lines in dependencies file that were incorrectly parsed

### Dependencies
- Bump org.slf4j:slf4j-bom from 2.0.17 to 2.0.18
- Bump com.diffplug.spotless:spotless-maven-plugin from 3.4.0 to 3.5.1
- Bump jackson-databind.version from 2.21.2 to 2.21.3

## [0.1.0] - 2026-04-10

### Added
- Service catalog support via `--service-catalog` and `--service-catalog-system` options
- Route loading error handling with fail-fast and log-and-continue policies
- Exponential backoff retry for resource downloads
- Health check support via gRPC
- Multi-module Maven project structure (common, plugin, main)
- Plugin mode via ContextServicePlugin SPI
- Spotless code formatting enforcement
- Structured error responses for better debugging

### Changed
- Optional client-secret (authentication can be disabled in Wanaku)
- Upgraded to Wanaku Capabilities SDK 0.1.0
- Upgraded to Apache Camel 4.18.x
- Artifact coordinates changed due to multi-module restructuring
  - Main JAR: `camel-integration-capability-main`
  - Plugin JAR: `camel-integration-capability-plugin`
  - Common library: `camel-integration-capability-common`

### Fixed
- Service catalog support with Docker (Fix #80)
- Route loading errors and confusing behavior
- Resource download retry logic to handle transient failures

## [0.0.9] - 2025-12-19

### Added
- Namespace configuration for tools and resources
- Explicit parameter mapping with custom header names via `mapping.type` and `mapping.name`
- Custom Maven repository support via `--repositories` option
- NPE fix in automatic parameter mapping

### Changed
- Version bump with SDK updates

## [0.0.8] - 2025-10-21

Initial release.

### Added
- Core Camel route execution via gRPC
- Route exposure as MCP tools and resources
- OAuth2/OIDC authentication support
- Dynamic dependency downloading
- Git repository initialization via `--init-from`
- DataStore and file URI schemes for route/rule loading
- Kubernetes/OpenShift deployment support
- Docker container support
- Automatic parameter mapping (MCP parameters → Camel headers with `Wanaku.` prefix)

[Unreleased]: https://github.com/wanaku-ai/camel-integration-capability/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/wanaku-ai/camel-integration-capability/compare/v0.0.9...v0.1.0
[0.0.9]: https://github.com/wanaku-ai/camel-integration-capability/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/wanaku-ai/camel-integration-capability/releases/tag/v0.0.8
