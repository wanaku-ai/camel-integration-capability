# Camel Integration Capability

A capability service for the [Wanaku MCP Router](https://wanaku.ai) that provides [Apache Camel](https://camel.apache.org) route execution capabilities.

This service integrates with the Wanaku ecosystem to execute Camel routes dynamically through Wanaku's gRPC bridge.

## Overview

This service implements a Wanaku capability that can:
- Execute Apache Camel routes defined in YAML format
- Register itself with a Wanaku discovery service
- Provide gRPC endpoints for route execution
- Support service-to-router authentication via OAuth2/OIDC


## Usage Guide

Please follow the [usage guide](docs/usage.md) to learn how to use this capability.

## Building The Project

If you want to build to the project, then read [building](docs/building.md) guide.