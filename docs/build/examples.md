---
title: Examples
description: Working examples for the Camel Integration Capability, from one greeting route to an employee system and a packaged service catalog.
---

# Examples

The [`examples/`](https://github.com/wanaku-ai/camel-integration-capability/tree/main/examples) folder of the repository has working routes and configurations. Each example has a README with the steps to run it.

## Available examples

| Example | What it shows |
|---|---|
| [hello-world](https://github.com/wanaku-ai/camel-integration-capability/tree/main/examples/hello-world) | One `ai-tool:` route that logs a greeting and returns a response. Start here. |
| [employee-system](https://github.com/wanaku-ai/camel-integration-capability/tree/main/examples/employee-system) | Three tools that call a backend employee service, with a dependencies file. |
| [service-catalog](https://github.com/wanaku-ai/camel-integration-capability/tree/main/examples/service-catalog) | The employee system routes and dependencies packaged as a service catalog, with `index.properties`. |
| [openshift](https://github.com/wanaku-ai/camel-integration-capability/tree/main/examples/openshift) | A ConfigMap, a Deployment and a Service that run the echo tool on OpenShift. See [Test the echo tool on OpenShift](../operate/openshift-echo-tool.md). |

## Next steps

- [Write and load routes](routes.md): the `ai-tool:` route format.
- [Serve a service catalog](service-catalogs.md): run CIC from a catalog.
