---
title: Build the Camel Integration Capability
description: Build the Camel Integration Capability from source with Maven, run the standalone application, and package it as a container image.
---

# Build the Camel Integration Capability

## Prerequisites

- Java 21 or higher
- [Apache Maven](https://maven.apache.org) 3.x is required to build and package the project.

## Project Structure

This is a multi-module Maven project:

```text
pom.xml                                    # Parent aggregator
├── camel-integration-capability-common/   # Shared library
└── camel-integration-capability-runtimes/
    └── camel-integration-capability-main/    # Standalone CLI app
```

## Building All Modules

```bash
mvn clean compile
mvn package
```

## Building Individual Modules

```bash
# Common module only
mvn -pl camel-integration-capability-common clean install

# Main module (requires common)
mvn -pl camel-integration-capability-runtimes/camel-integration-capability-main -am clean package
```

## Running the Standalone Application

```bash
java -jar camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar --help
```

## Packaging as Containers

You can use the provided Dockerfile to build a container for this project:

```bash
mvn clean package -DskipTests
podman build -t camel-integration-capability:latest .
```

## Architecture

- **Common Module** (`camel-integration-capability-common`):
  - Shared utilities, models, and gRPC services
  - No CLI or logging dependencies
  - Can be used as a library

- **Main Module** (`camel-integration-capability-main`):
  - `CamelToolMain` - CLI entry point (picocli)
  - Standalone application with embedded logging
  - Fat jar packaging for deployment

## Next steps

- [Release guide](release-guide.md): prepare and trigger a release.
- [Architecture](../get-started/architecture.md): how the components work together.
