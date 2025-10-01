# Camel Core Downstream Service

A capability service for the Wanaku MCP router that provides Apache Camel route execution capabilities.

This service integrates with the Wanaku ecosystem to execute Camel routes dynamically through gRPC calls.

## Overview

This service implements a Wanaku capability that can:
- Execute Apache Camel routes defined in YAML format
- Register itself with a Wanaku discovery service
- Provide gRPC endpoints for route execution
- Support authentication via OAuth2/OIDC

## Requirements

- Java 21 or higher
- Maven 3.6+ for building
- Access to a Wanaku discovery service
- OAuth2/OIDC authentication provider

## Building

```bash
mvn clean compile
mvn package
```

## Configuration

The service requires several configuration parameters to connect to the Wanaku ecosystem:

### Required Parameters

- `--registration-url`: URL of the Wanaku discovery service
- `--registration-announce-address`: Address to announce for service discovery
- `--routes-path`: Path to the Apache Camel routes YAML file (e.g., `/path/to/routes.camel.yaml`)
- `--token-endpoint`: OAuth2/OIDC token endpoint base URL
- `--client-id`: OAuth2 client ID for authentication
- `--client-secret`: OAuth2 client secret for authentication

### Optional Parameters

- `--grpc-port`: gRPC server port (default: 9190)
- `--name`: Service name for registration (default: "camel")
- `--routes-rules`: Path to the YAML file with route exposure rules (e.g., `/path/to/routes-expose.yaml`)
- `--retries`: Maximum registration retries (default: 3)
- `--wait-seconds`: Wait time between retries (default: 1)
- `--initial-delay`: Initial registration delay in seconds (default: 0)
- `--period`: Period between registration attempts in seconds (default: 5)

## Running the Service

### Basic Usage

```bash
java -jar target/camel-core-downstream-service-1.0-SNAPSHOT.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name camel-core \
  --routes-path /path/to/routes.camel.yaml \
  --routes-rules /path/to/routes-rules.yaml \
  --token-endpoint http://localhost:8543/realms/wanaku/ \
  --client-id your-client-id \
  --client-secret your-client-secret
```

### Development Environment

For local development with a Wanaku stack:

```bash
java -jar target/camel-core-downstream-service-1.0-SNAPSHOT.jar \
  --registration-url http://localhost:8080 \
  --registration-announce-address localhost \
  --grpc-port 9190 \
  --name camel-core \
  --routes-path ./tests/data/routes/camel-core/promote-employee/promote-employee.camel.yaml \
  --routes-rules ./tests/data/routes/camel-core/promote-employee/promote-employee-rules.yaml \
  --token-endpoint http://localhost:8543/realms/wanaku/ \
  --client-id wanaku-service \
  --client-secret aBqsU3EzUPCHumf9sTK5sanxXkB0yFtv
```

## Route Definitions

### Camel Routes

Camel routes should be defined in YAML format in the file specified by `--routes-path`. Example route structure:

```yaml
- route:
    id: example-route
    from:
      uri: direct
      parameters:
        name: start
      steps:
        - log:
            message: Hello ${body}
        - setBody:
            simple: Hello Camel from ${routeId}
```

### Route Exposure Rules

Routes can be exposed as tools by defining rules in a YAML file specified by `--routes-rules`. 
This file maps route definitions to tool specifications:

```yaml
tools:
  - initiate-employee-promotion:
      route:
        uri: "direct:initiate-promotion"
      description: "Initiate the promotion process for an employee"
      properties:
        - name: employee
          type: string
          description: The employee to promote
          required: true
  - confirm-employee-promotion:
      route:
        uri: "direct:confirm-promotion"
      description: "Confirm the promotion of an employee"
      properties:
        - name: employee
          type: string
          description: The employee to confirm the promotion
          required: true
          mapping:
            type: header
            name: EMPLOYEE
```

#### Property Mapping

Properties can include an optional `mapping` element to specify how parameters should be passed to the Camel route:

- `type`: The mapping type (e.g., `header`, `body`)
- `name`: The target name in the Camel exchange (e.g., header name)

## Architecture

- **Main Class**: `ai.wanaku.tool.camel.CamelToolMain` - Entry point and configuration
- **gRPC Services**:
  - `CamelTool` - Handles route execution requests
  - `ProvisionBase` - Provides basic service information
- **Route Loading**: `WanakuRoutesLoader` - Loads and manages Camel routes
- **Authentication**: Integrated OAuth2/OIDC client for Wanaku ecosystem

## Dependencies

- Apache Camel 4.14.0
- Wanaku Capabilities SDK 0.0.8-SNAPSHOT
- gRPC for service communication
- PicoCLI for command-line interface
- SLF4J + Log4j2 for logging

## Testing

Use the included Makefile for testing:

```bash
make test-tools HOST=localhost API_ENDPOINT=http://localhost:8080
```

This registers a test tool with the Wanaku CLI.