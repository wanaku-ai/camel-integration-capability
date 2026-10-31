---
title: Deploy on Kubernetes
description: Deploy the Camel Integration Capability on Kubernetes or OpenShift with the WanakuIntegration resource of the Wanaku Operator, or with a Deployment.
---

# Deploy on Kubernetes

This page shows how to run CIC on Kubernetes or OpenShift. Use the `WanakuIntegration` resource of the Wanaku Operator. If you do not use the operator, use a `Deployment`.

## Prerequisites

- The Wanaku Operator and a `WanakuRouter`. See [Deploy with the Wanaku Operator](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/operate/operator.md).
- For `datastore://` references and service catalogs: Wanaku Barn on the router (`spec.barn.enabled: true`).

## Deploy with the Wanaku Operator

A `WanakuIntegration` resource runs one CIC runtime. The operator creates a Deployment and a Service for it. The runtime registers itself with the router as an MCP forward, and removes the forward when it stops.

```yaml
apiVersion: "wanaku.ai/v1alpha1"
kind: WanakuIntegration
metadata:
  name: employee-system
spec:
  # The WanakuRouter that the runtime registers with
  routerRef: wanaku-dev
  # Set exactly one routes source
  routes:
    serviceCatalog:
      name: employee-system-v2
      system: employee-system
  mcp:
    port: 9090
    tags: wanaku
  registration:
    enabled: true
```

The routes source is one of these fields:

- `spec.routes.serviceCatalog`: a service catalog in Wanaku Barn (`name` and `system`).
- `spec.routes.routesRef`: a reference that the runtime downloads, for example `datastore://employee-backend.camel.yaml`.
- `spec.routes.configMapRef`: route files in a ConfigMap (`name` and `main`, the key of the main route file).

For all the fields and the status conditions, see [WanakuIntegration](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/reference/operator-crds.md#wanakuintegration-wanakuaiv1alpha1) in the Wanaku Operator reference.

## Deploy with a Deployment

Without the operator, run the `quay.io/wanaku/camel-integration-capability` image in a `Deployment`. The image reads its options from environment variables. For the list, see the [CLI reference](../reference/cli.md).

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: camel-integration-capability
spec:
  replicas: 1
  selector:
    matchLabels:
      app: camel-integration-capability
  template:
    metadata:
      labels:
        app: camel-integration-capability
    spec:
      containers:
        - name: cic
          image: quay.io/wanaku/camel-integration-capability:latest
          env:
            # Wanaku Barn base URL for data store and service catalog downloads
            - name: REGISTRATION_URL
              value: http://barn-wanaku-dev:8080
            - name: SERVICE_CATALOG
              value: employee-system-v2
            - name: SERVICE_CATALOG_SYSTEM
              value: employee-system
          ports:
            - name: mcp
              containerPort: 9090
```

To load individual files instead of a catalog, set `ROUTES_PATH` (for example `datastore://employee-backend.camel.yaml`) and `DEPENDENCIES` instead of `SERVICE_CATALOG` and `SERVICE_CATALOG_SYSTEM`.

Then add the capability to Wanaku in one of these ways:

- Add a forward with the Wanaku CLI:

  ```bash
  wanaku forwards add --service="http://camel-integration-capability:9090/mcp" --name cic --namespace default
  ```

- Let CIC register itself. See [Self-registration with Wanaku](self-registration.md), which has a full Kubernetes example.

For the health probes, the resource sizing, and the persistent data directory, see [Operations](production.md).

To test a single route on OpenShift without the operator, see [Test the echo tool on OpenShift](openshift-echo-tool.md).

## Next steps

- [Self-registration with Wanaku](self-registration.md): let CIC add and remove its forward.
- [Operations](production.md): health probes, sizing, logging, and scaling.
- [Troubleshooting](troubleshooting.md): find the cause and the fix for common problems.
