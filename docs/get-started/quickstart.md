---
title: Quickstart
description: Run one ai-tool route from a local file with the Camel Integration Capability, add the capability to Wanaku, and call the tool.
---

# Quickstart

This page shows how to run one `ai-tool:` route from a local file, and how to add the capability to Wanaku as an MCP forward.

## Prerequisites

- Java 21 or higher.
- The CIC JAR file. Download it from the [releases page](https://github.com/wanaku-ai/camel-integration-capability/releases), or build it from source (see [Building](../contributing/building.md)).
- A running Wanaku server and the Wanaku CLI, if you want to add the capability to Wanaku.

Routes from a local file do not need Wanaku Barn. You need Wanaku Barn only for `datastore://` references and service catalogs.

## Write a route

Create the file `my-routes.camel.yaml`:

```yaml
- route:
    id: get-employee-info
    from:
      uri: ai-tool:get-employee-info
      parameters:
        description: "Fetches core profile data for a specific employee"
        tags: wanaku
        parameter.employeeId: string
        parameter.employeeId.description: "The employee ID"
      steps:
        - toD: https://api.example.com/employees/${header.employeeId}
```

The `tags` parameter must match `--mcp-tags`. The default value of `--mcp-tags` is `wanaku`. CIC does not serve a route without a matching tag. See [Write and load routes](../build/routes.md).

## Run the capability

Start CIC with an absolute `file://` reference to the route file:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --name camel-core \
  --routes-ref file:///path/to/my-routes.camel.yaml \
  --mcp-port 9090
```

The MCP server listens on `http://localhost:9090/mcp`.

## Add the capability to Wanaku

Add CIC to Wanaku as an MCP forward:

```bash
wanaku forwards add --service="http://localhost:9090/mcp" --name camel-core --namespace default
```

Wanaku discovers the `get-employee-info` tool. AI agents that connect to Wanaku can now call it.

To let CIC add and remove the forward itself, see [Self-registration with Wanaku](../operate/self-registration.md).

## Next steps

- [Write and load routes](../build/routes.md): route parameters, dependencies, and other route sources.
- [Service catalogs](../build/service-catalogs.md): package routes and dependencies as one versioned artifact.
- [CLI reference](../reference/cli.md): all the options of CIC.
