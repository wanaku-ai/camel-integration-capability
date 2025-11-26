# Building Camel Integration Capability

## Prerequisites

* [Apache Maven](https://maven.apache.org) 3.x is required to build and package the project.

## Overview

```bash
mvn clean compile
mvn package
```

## Packaging as Containers

You can use the provided Dockerfile to o build a container for this project. 

## Architecture

- **Main Class**: `ai.wanaku.capability.camel.CamelToolMain` - Entry point and configuration
- **gRPC Services**:
    - `CamelTool` - Handles route execution requests
    - `ProvisionBase` - Provides basic service information
- **Route Loading**: `WanakuRoutesLoader` - Loads and manages Camel routes
- **Authentication**: Integrated OAuth2/OIDC client for Wanaku ecosystem