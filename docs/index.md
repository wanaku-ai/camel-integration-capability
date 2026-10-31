---
layout: home
title: "Camel Integration Capability: Camel routes as MCP tools"
description: The Camel Integration Capability runs Apache Camel routes and serves them as MCP tools, so AI agents can call backend systems through Wanaku.
hero:
  name: Camel Integration Capability
  text: Apache Camel routes as MCP tools for Wanaku
  tagline: Let AI agents reach databases, APIs, and message brokers through more than 300 Apache Camel components.
  actions:
    - theme: brand
      text: Quickstart
      link: ./get-started/quickstart
    - theme: alt
      text: Write and load routes
      link: ./build/routes
features:
  - title: Get started
    details: Learn what the capability does and run one route as an MCP tool.
    link: ./get-started/
  - title: Build routes and catalogs
    details: Write ai-tool routes, add dependencies, and serve service catalogs from Wanaku Barn.
    link: ./build/
  - title: Deploy and operate
    details: Deploy with the Wanaku Operator, register with Wanaku, and run in production.
    link: ./operate/
  - title: Reference
    details: Look up the command-line options and the environment variables.
    link: ./reference/
  - title: Contribute
    details: Build the capability from source and prepare a release.
    link: ./contributing/
---

# Camel Integration Capability: Camel routes as MCP tools

The Camel Integration Capability (CIC) runs [Apache Camel](https://camel.apache.org) routes and serves the `ai-tool:` routes as MCP tools. [Wanaku](https://wanaku.ai/docs/next/), the governed action proxy for AI agents, forwards tool calls to it. [Wanaku Barn](https://wanaku.ai/docs/barn/next/) stores the routes and the service catalogs that CIC downloads.

## Choose your path

| You want to | Start with | Then read |
|---|---|---|
| Try one route as an MCP tool | [Quickstart](get-started/quickstart.md) | [Write and load routes](build/routes.md) |
| Package routes for production | [Serve a service catalog](build/service-catalogs.md) | [Examples](build/examples.md) |
| Deploy on Kubernetes or OpenShift | [Deploy on Kubernetes](operate/kubernetes.md) | [Self-registration with Wanaku](operate/self-registration.md) |
| Upgrade from 0.1.x | [Migration guide](operate/migration-guide.md) | [CLI reference](reference/cli.md) |
| Fix a problem | [Troubleshooting](operate/troubleshooting.md) | [Operations](operate/production.md) |
| Contribute code or docs | [Contribute](contributing/index.md) | [Build the Camel Integration Capability](contributing/building.md) |

## Next steps

- [Get started with the Camel Integration Capability](get-started/index.md): what CIC does and how it connects to Wanaku.
- [Wanaku documentation](https://wanaku.ai/docs/next/): install and run Wanaku.
