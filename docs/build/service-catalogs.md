---
title: Serve a service catalog
description: Run the Camel Integration Capability from a service catalog in Wanaku Barn, with one instance for each system, and version the catalogs.
---

# Serve a service catalog

A service catalog packages the routes and the dependencies of one or more systems as one versioned ZIP archive in Wanaku Barn. This page shows how CIC serves a catalog. To create a catalog and deploy it to Barn, see [Build and deploy a service catalog](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/manage/build-a-service-catalog.md) in the Barn documentation.

## Why use a service catalog

- **One update for routes and dependencies.** With individual files, CIC can get a new route and old dependencies. A catalog changes both at the same time.
- **Reproducible deployments.** `employee-system-v2` always contains the same routes and dependencies.
- **One reference.** Two options select a catalog and a system:

  ```bash
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
  ```

Use individual files during development, to change one route quickly. Use a catalog for versioned releases and for production.

## Catalog structure

A catalog ZIP has an `index.properties` file at the root and one folder for each system:

```text
hr-systems-v3.zip
├── index.properties
├── employee-system/
│   ├── routes.camel.yaml
│   └── dependencies.txt
└── payroll-system/
    ├── routes.camel.yaml
    └── dependencies.txt
```

`catalog.name` in `index.properties` is the value of `--service-catalog`. Each system in `catalog.services` is a possible value of `--service-catalog-system`. For all the properties, see [Service catalogs](https://github.com/wanaku-ai/wanaku-barn/blob/main/docs/manage/service-catalogs.md#the-manifest-file) in the Barn documentation.

The routes in a catalog use the `ai-tool:` format, with a `tags` parameter that matches `--mcp-tags`. See [Write and load routes](routes.md).

## Run CIC from a catalog

Give CIC the Barn base URL, the catalog name and the system name:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8180 \
  --name employee-system \
  --service-catalog employee-system-v2 \
  --service-catalog-system employee-system
```

CIC does these steps:

1. It downloads `employee-system-v2.zip` from Barn.
2. It extracts the routes and the dependencies of `employee-system`.
3. It loads the routes and starts the MCP server.

If the download fails, CIC tries again with exponential backoff, up to `--retries` times (default 12).

`--service-catalog` cannot be used with `--routes-ref` or `--dependencies`.

On Kubernetes, set `spec.routes.serviceCatalog` in a `WanakuIntegration` resource, or set `SERVICE_CATALOG` and `SERVICE_CATALOG_SYSTEM` in a Deployment. See [Deploy on Kubernetes](../operate/kubernetes.md).

## Serve a catalog with more than one system

One CIC instance serves one system. To serve all the systems of a catalog, start one instance for each system. All the instances download the same catalog and extract a different system:

```bash
java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8180 \
  --name employee-system \
  --service-catalog hr-systems-v3 \
  --service-catalog-system employee-system

java -jar camel-integration-capability-main-*-jar-with-dependencies.jar \
  --registration-url http://localhost:8180 \
  --name payroll-system \
  --mcp-port 9091 \
  --service-catalog hr-systems-v3 \
  --service-catalog-system payroll-system
```

## Version the catalogs

Do not change a catalog after you deploy it. `employee-system-v2` must always contain the same routes and dependencies.

Use names that contain a version, for example:

- `employee-system-v2`: version 2 of the employee system catalog.
- `payroll-system-2026-01`: the payroll catalog of the January 2026 release.
- `hr-systems-prod-2.3.1`: version 2.3.1 of the HR systems catalog for production.

Do not use generic names like `latest` or `current`.

Use these stages:

- **Development**: add a `-dev` or `-snapshot` suffix. You can overwrite these catalogs.
- **Staging**: promote the catalog to a release candidate, for example `v3-rc1`. Do not change it.
- **Production**: use the final version, for example `v3`. Do not change it. Make a new version, for example `v4`, for the next change.

## Next steps

- [Deploy on Kubernetes](../operate/kubernetes.md): serve a catalog with the Wanaku Operator.
- [Troubleshooting](../operate/troubleshooting.md#service-catalog-issues): fix catalog download and extraction problems.
- [CLI reference](../reference/cli.md): all the options of CIC.
