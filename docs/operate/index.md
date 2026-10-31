---
title: Deploy and operate the Camel Integration Capability
description: Run the Camel Integration Capability in production, upgrade it from older versions, test it on OpenShift, and fix common problems.
---

# Deploy and operate the Camel Integration Capability

These pages are for operators who deploy and run CIC on Kubernetes and in production.

## Pages in this section

- [Deploy on Kubernetes](kubernetes.md): run CIC with the `WanakuIntegration` resource of the Wanaku Operator, or with a Deployment.
- [Self-registration with Wanaku](self-registration.md): let CIC add and remove its MCP forward in Wanaku.
- [Operations](production.md): health checks, resource sizing, logging, scaling, and container practices.
- [Migration guide](migration-guide.md): upgrade between CIC versions.
- [Test the echo tool on OpenShift](openshift-echo-tool.md): deploy one route on OpenShift to test the deployment.
- [Troubleshooting](troubleshooting.md): find the cause and the fix for common problems.

## Next steps

- [Reference](../reference/index.md): look up the command-line options and environment variables.
- [Write and load routes](../build/routes.md): write the routes that CIC serves.
