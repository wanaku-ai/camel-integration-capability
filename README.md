# Integration Capability for Apache Camel

![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/java-21%2B-orange.svg)
![Camel](https://img.shields.io/badge/Apache%20Camel-4.22-red.svg)

A capability service for the [Wanaku Governed Action Proxy](https://wanaku.ai) that enables AI agents to interact with
backend systems through dynamically executed [Apache Camel](https://camel.apache.org) routes.

## What is This?

The Integration Capability for Apache Camel bridges AI agents with enterprise integration patterns.

It downloads Apache Camel routes and runs them using Camel's built-in MCP server. Routes that use the `ai-tool:` URI format are automatically exposed as MCP tools via HTTP/SSE transport, allowing AI agents to perform complex backend operations.

**Key Use Cases:**

- Enable AI agents to query databases, CRMs, or inventory systems
- Orchestrate multi-step business workflows through natural language
- Integrate AI capabilities with existing enterprise service buses

## Project Structure

```text
camel-integration-capability/
└── camel-integration-capability-runtimes/
    └── camel-integration-capability-main/         # Standalone CLI application
```

## Architecture Overview

```mermaid
graph TB
    A[AI Agent/LLM] -->|MCP Protocol| B[Wanaku]
    B -->|HTTP/SSE| C[Camel Integration Capability]
    C -->|Execute| E[Apache Camel Routes]
    E -->|HTTP/REST| F[Backend APIs]
    E -->|Database| G[Data Sources]
    E -->|Message Queue| H[Messaging Systems]

    I[Service Catalog] -.->|Routes & Dependencies| C
```

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6+ (for building from source)
- Access to a Wanaku server instance

### 5-Minute Setup

1. **Download the latest release** or build from source:

   ```bash
   git clone https://github.com/wanaku-ai/camel-integration-capability.git
   cd camel-integration-capability
   mvn clean package
   ```

2. **Prepare your Camel routes** using the `ai-tool:` format (example `my-routes.camel.yaml`):

   ```yaml
   - route:
       id: get-employee-info
       from:
         uri: ai-tool:get-employee-info
         parameters:
           description: "Fetches core profile data for a specific employee"
           tags: wanaku
         steps:
           - toD: https://api.example.com/employees/${header.employeeId}
   ```

3. **Run the capability**:

   The recommended approach is to use a **service catalog**, which bundles routes and dependencies into a single versioned artifact:

   ```bash
   java -jar camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
     --registration-url http://localhost:8080 \
     --name employee-system \
     --service-catalog employee-system-v2 \
     --service-catalog-system employee-system
   ```

   You can also reference individual files instead of a catalog (useful during development):

   ```bash
   java -jar camel-integration-capability-runtimes/camel-integration-capability-main/target/camel-integration-capability-main-*-jar-with-dependencies.jar \
     --registration-url http://localhost:8080 \
     --routes-ref file:///path/to/routes.camel.yaml
   ```

> [!TIP]
> Design your Camel routes visually using the [Kaoto Integration Designer](http://kaoto.io) for Apache Camel.

<!-- -->

> [!NOTE]
> For detailed configuration options and deployment scenarios, see [Get started](https://wanaku.ai/docs/camel-integration-capability/next/get-started/) and the [CLI reference](https://wanaku.ai/docs/camel-integration-capability/next/reference/cli.html).

## Documentation

The [Camel Integration Capability documentation](https://wanaku.ai/docs/camel-integration-capability/next/) covers:

- [Get started](https://wanaku.ai/docs/camel-integration-capability/next/get-started/): the quickstart, the architecture, and how the capability connects to Wanaku.
- [Build tools](https://wanaku.ai/docs/camel-integration-capability/next/build/): write `ai-tool:` routes, serve a service catalog, and use the examples.
- [Deploy and operate](https://wanaku.ai/docs/camel-integration-capability/next/operate/): deploy on Kubernetes, self-register with Wanaku, run in production, and troubleshoot.
- [Reference](https://wanaku.ai/docs/camel-integration-capability/next/reference/): all command-line options and environment variables.
- [Contribute](https://wanaku.ai/docs/camel-integration-capability/next/contributing/): build from source, prepare a release, and write documentation.

For Wanaku itself, see the [Wanaku documentation](https://wanaku.ai/docs/next/). For the 0.2 release, see the [Classic documentation](https://wanaku.ai/docs/camel-integration-capability/camel-integration-capability-current/).

See [Contributing](CONTRIBUTING.md) and [Security](SECURITY.md) for contribution guidelines and vulnerability reporting.

## Deployment

### Docker

```bash
mvn clean package -DskipTests
docker build -t camel-integration-capability .
docker run -p 9090:9090 camel-integration-capability [options]
```

### Kubernetes/OpenShift

Deploy using the Wanaku operator. See [Deploy on Kubernetes](https://wanaku.ai/docs/camel-integration-capability/next/operate/kubernetes.html) for examples.

## Support

- **Issues**: [GitHub Issues](https://github.com/wanaku-ai/camel-integration-capability/issues)
- **Community**: [Wanaku](https://wanaku.ai)
- **Email**: <contact@wanaku.ai>

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

## Related Projects

- [Wanaku](https://wanaku.ai) - The governed action proxy for AI agents that this capability integrates with
- [Apache Camel](https://camel.apache.org) - The integration framework powering route execution
- [Kaoto](http://kaoto.io) - Visual designer for Camel routes
