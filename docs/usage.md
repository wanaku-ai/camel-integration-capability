# Camel Core Downstream Service

A capability service for the [Wanaku MCP Router](https://wanaku.ai) that provides [Apache Camel](https://camel.apache.org) route
execution capabilities.

This service integrates with the Wanaku ecosystem to execute Camel routes dynamically through Wanaku's gRPC bridge. 

## Overview

This service implements a Wanaku capability that can:
- Execute Apache Camel routes defined in YAML format
- Register itself with a Wanaku discovery service
- Support service-to-router authentication via OAuth2/OIDC

> [TIP]
> Design your Camel routes with ease using the [Kaoto Integration Designer](http://kaoto.io) for Apache Camel.

## Requirements

- Java 21 or higher
- Maven 3.6+ for building
- Access to a Wanaku discovery service
- OAuth2/OIDC authentication provider

## Running this Capability

This capability service requires configuration parameters to connect to the Wanaku ecosystem. When launching it, you need to 
set them so that this capability service can talk to Wanaku and register itself. 

> [NOTE]
> Although this capability is intended to be run inside Kubernetes or OpenShift, it is entirely possible to execute it locally.

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

### Basic Example (Local)

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

## Deploying the Service

-- Deployment documentation goes here.

## Designing Routes

The easiest way to design the routes for this project, is to use a visual editor such as [Kaoto](http://kaoto.io) or 
[Camel Karavan](http://camel.apache.org/karavan) to design the routes. 

Those editors should allow you to visualize the route. For instance: 

![Kaoto Route Example](imgs/kaoto.png)

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

Routes can be exposed as MCP tools or resources by defining rules in a YAML file specified by `--routes-rules`.
This file maps route definitions to tool specifications:

```yaml
mcp:
  tools:
    - initiate-employee-promotion:
        route:
          id: "route-3103"
        description: "Initiate the promotion process for an employee"
        properties:
          - name: employee
            type: string
            description: The employee to confirm the promotion
            required: true
            mapping:
              type: header
              name: EMPLOYEE
    - confirm-employee-promotion:
        route:
          id: "route-3104"
        description: "Confirm the promotion of an an employee"
        properties:
          - name: employee
            type: string
            description: The employee to confirm the promotion
            required: true
            mapping:
              type: header
              name: EMPLOYEE
  resources:
    - employee-performance-history:
        route:
          id: "route-3105"
        description: "Obtain the employee performance history"
```

For resource routes, the code doesn't necessarily run the route. Instead, it
uses only the endpoint URI for accessing it via a consumer template. For resources
the data MUST be convertable to a Java String.

> [IMPORTANT]
> Routes serving resources MUST have their auto-start disabled.


#### Property Mapping

Properties can include an optional `mapping` element to specify how parameters should be passed to the Camel route:

- `type`: The mapping type (e.g., `header`, `body`)
- `name`: The target name in the Camel exchange (e.g., header name)

## Architecture

- **Main Class**: `ai.wanaku.capability.camel.CamelToolMain` - Entry point and configuration
- **gRPC Services**:
    - `CamelTool` - Handles route execution requests
    - `ProvisionBase` - Provides basic service information
- **Route Loading**: `WanakuRoutesLoader` - Loads and manages Camel routes
- **Authentication**: Integrated OAuth2/OIDC client for Wanaku ecosystem