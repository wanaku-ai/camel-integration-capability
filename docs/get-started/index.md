---
title: Get started with the Camel Integration Capability
description: Learn what the Camel Integration Capability does, how it connects to Wanaku and Wanaku Barn, and how to run it for the first time.
---

# Get started with the Camel Integration Capability

The Camel Integration Capability (CIC) lets AI agents call backend systems through [Apache Camel](https://camel.apache.org) routes. It is a capability service for [Wanaku](https://wanaku.ai), the governed action proxy for AI agents.

## What CIC does

CIC does these tasks:

- It downloads Apache Camel routes in YAML format, from a local file, from Wanaku Barn, or from a service catalog.
- It runs the routes with the Apache Camel Main runtime.
- It serves each `ai-tool:` route as an MCP tool, through the built-in MCP server of Camel at the `/mcp` path.

Wanaku forwards tool calls from AI agents to CIC. You add CIC to Wanaku as an MCP forward, or CIC registers itself when it starts.

> [!TIP]
> Design your Camel routes with the [Kaoto Integration Designer](https://kaoto.io) for Apache Camel.

## Pages in this section

- [Quickstart](quickstart.md): run one `ai-tool:` route from a local file and add it to Wanaku.
- [Architecture](architecture.md): the components, the data flow, and the design decisions.

## Next steps

- [Build routes and catalogs](../build/index.md): write routes and package them as a service catalog.
- [Deploy and operate](../operate/index.md): run CIC on Kubernetes and in production.
