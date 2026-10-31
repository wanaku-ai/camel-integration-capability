---
title: Write and load routes
description: Write ai-tool routes that the Camel Integration Capability serves as MCP tools, add dependencies, and load routes from files, Barn, or Git.
---

# Write and load routes

This page shows how to write the Apache Camel routes that CIC serves as MCP tools, and how CIC gets the route files.

## Design routes

Use a visual editor to design the routes, for example [Kaoto](https://kaoto.io) or [Camel Karavan](https://github.com/apache/camel-karavan). Save the routes in YAML format.

## Write an ai-tool route

CIC serves a route as an MCP tool when the route starts with the `ai-tool:` URI. The tool name, the description, the parameters, and the tags are in the route:

```yaml
- route:
    id: get-employee-info
    from:
      uri: ai-tool:get-employee-info
      parameters:
        description: "Retrieve employee information by ID"
        tags: wanaku
        parameter.employeeId: string
        parameter.employeeId.description: "The employee ID"
      steps:
        - toD: https://api.example.com/employees/${header.employeeId}
```

- `ai-tool:<name>` sets the tool name.
- `description` is the tool description that the AI agent sees.
- Each `parameter.<name>: <type>` and `parameter.<name>.description` pair defines one tool parameter. The route reads the parameter from the header with the same name, for example `${header.employeeId}`.
- `tags` controls the exposure of the tool. CIC serves only the routes whose tags match `--mcp-tags`. The default value of `--mcp-tags` is `wanaku`. CIC never serves a route that has no tags.

## Use standard routes

Routes that start with standard Camel URIs, for example `direct:` or `timer:`, are not MCP tools. An `ai-tool:` route can call them.

## Add dependencies

CIC contains only a subset of the Apache Camel components. Put the other dependencies in a text file, as a comma-separated list of Maven coordinates:

```text
org.apache.camel:camel-http:4.22.0,org.apache.camel:camel-jackson:4.22.0
```

Then give the file to CIC with `--dependencies`:

- `--dependencies datastore://filename.txt` for a file in Wanaku Barn.
- `--dependencies file:///path/to/filename.txt` for a local file, for example a file from Git initialization.

To use more Maven repositories, set `--repositories` to a comma-separated list of repository URLs.

## Load routes

CIC gets the route files in one of these ways:

1. From a service catalog in Wanaku Barn. A catalog contains the routes and the dependencies in one versioned artifact. This is the recommended way. See [Service catalogs](service-catalogs.md).
2. From the Wanaku Barn data store, with `datastore://` references.
3. From a Git repository, with `--init-from`. CIC clones the repository when it starts.
4. From a local file, with `file://` references. In Kubernetes, mount the files from a ConfigMap or a persistent volume, or clone them with an init container.

`--registration-url` sets the base URL of Wanaku Barn for `datastore://` references and service catalogs.

### Load routes from a service catalog

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8180 \
  --name employee-system \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

`--service-catalog` cannot be used with `--routes-ref` or `--dependencies`. CIC gets all the resources from the catalog.

### Load routes from the data store

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8180 \
  --name camel-core \
  --routes-ref datastore://promote-employee.camel.yaml \
  --dependencies datastore://promote-employee-dependencies.txt \
  --data-dir /tmp/camel-data
```

### Load routes from Git

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --name camel-core \
  --init-from git@github.com:wanaku-ai/wanaku-recipes.git \
  --routes-ref file:///tmp/cloned-repo/routes/promote-employee.camel.yaml \
  --dependencies file:///tmp/cloned-repo/dependencies/promote-employee-dependencies.txt \
  --data-dir /tmp
```

## Next steps

- [Service catalogs](service-catalogs.md): package routes and dependencies as one versioned artifact.
- [CLI reference](../reference/cli.md): all the options of CIC.
- [Deploy on Kubernetes](../operate/kubernetes.md): run CIC with the Wanaku Operator.
