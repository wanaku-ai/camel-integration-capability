# Quickstart: Multi-Module Project Split

**Feature Branch**: `001-multi-module-split`
**Date**: 2026-02-07

## Prerequisites

- JDK 21+
- Maven 3.9+
- Git

## Build Commands

### Build All Modules

```bash
mvn clean install
```

### Build Individual Modules

```bash
# Common module only
mvn -pl camel-integration-capability-common clean install

# Plugin module (requires common)
mvn -pl runtimes/camel-integration-capability-plugin -am clean install

# Main module (requires common)
mvn -pl runtimes/camel-integration-capability-main -am clean install
```

### Skip Tests

```bash
mvn clean install -DskipTests
```

## Development Workflow

### 1. Working on Common Module

Changes to shared code in `camel-integration-capability-common`:

```bash
# Edit files in camel-integration-capability-common/src/main/java/
# Run tests
mvn -pl camel-integration-capability-common test

# Install to local repo for dependent modules
mvn -pl camel-integration-capability-common install
```

### 2. Working on Plugin Module

```bash
# Edit files in runtimes/camel-integration-capability-plugin/src/main/java/
# Build with dependencies
mvn -pl runtimes/camel-integration-capability-plugin -am clean package
```

### 3. Working on Main Module

```bash
# Edit files in runtimes/camel-integration-capability-main/src/main/java/
# Build fat jar
mvn -pl runtimes/camel-integration-capability-main -am clean package

# Run standalone
java -jar runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar --help
```

## Testing

### Run All Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify
```

### Run Specific Module Tests

```bash
mvn -pl camel-integration-capability-common test
mvn -pl runtimes/camel-integration-capability-plugin test
mvn -pl runtimes/camel-integration-capability-main test
```

## Using the Plugin

### 1. Add Dependency

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Properties

Create `camel-integration-capability.properties` in classpath:

```properties
registration.url=http://localhost:8080
service.name=my-camel-service
routes.ref=file:///path/to/routes.yaml
client.id=my-client
client.secret=my-secret
```

### 3. Or Use Environment Variables

```bash
export REGISTRATION_URL=http://localhost:8080
export SERVICE_NAME=my-camel-service
export ROUTES_PATH=file:///path/to/routes.yaml
export CLIENT_ID=my-client
export CLIENT_SECRET=my-secret
```

### 4. Plugin Auto-Discovery

The plugin is automatically discovered via SPI when the jar is on classpath. No additional configuration needed in Camel application.

## Using the Common Library

### Add Dependency

```xml
<dependency>
    <groupId>ai.wanaku</groupId>
    <artifactId>camel-integration-capability-common</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Example Usage

```java
import ai.wanaku.capability.camel.WanakuCamelManager;
import ai.wanaku.capability.camel.util.McpRulesManager;

// Use shared utilities
McpRulesManager rulesManager = new McpRulesManager("service-name", "/path/to/rules.yaml");

// Use Camel manager
WanakuCamelManager camelManager = new WanakuCamelManager(downloadedResources, repositories);
```

## Container Build

```bash
# Build all modules first
mvn clean package -DskipTests

# Build container (uses main module jar)
podman build -t camel-integration-capability:latest .
```

## Verification Checklist

After restructuring, verify:

- [ ] `mvn clean install` completes successfully
- [ ] All existing tests pass
- [ ] Standalone app works: `java -jar runtimes/camel-integration-capability-main/target/*-jar-with-dependencies.jar --help`
- [ ] Container builds successfully
- [ ] Plugin can be added as dependency and compiles
- [ ] Common module can be added as dependency and compiles
