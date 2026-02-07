# Plugin SPI Contract

**Module**: `camel-integration-capability-plugin`
**Interface**: `org.apache.camel.spi.ContextServicePlugin`

## Discovery

File: `META-INF/services/org.apache.camel.spi.ContextServicePlugin`

Content:
```
ai.wanaku.capability.camel.plugin.CamelIntegrationPlugin
```

## Interface Implementation

```java
package ai.wanaku.capability.camel.plugin;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ContextServicePlugin;

public class CamelIntegrationPlugin implements ContextServicePlugin {

    /**
     * Called after CamelContext creation, before routes start.
     *
     * Responsibilities:
     * 1. Load configuration from properties/environment
     * 2. Initialize data directory
     * 3. Run initializers (e.g., git clone)
     * 4. Download resources if needed
     * 5. Start gRPC server
     * 6. Register with discovery service
     *
     * @param camelContext The Camel context being initialized
     */
    @Override
    public void load(CamelContext camelContext) {
        // Implementation
    }

    /**
     * Called during CamelContext shutdown.
     *
     * Responsibilities:
     * 1. Deregister from discovery service
     * 2. Stop gRPC server
     * 3. Release resources
     *
     * @param camelContext The Camel context being stopped
     */
    @Override
    public void unload(CamelContext camelContext) {
        // Implementation
    }
}
```

## Configuration Contract

### Properties File

Location: Classpath resource `camel-integration-capability.properties`

| Property | Type | Required | Default | Description |
|----------|------|----------|---------|-------------|
| `registration.url` | String | Yes | - | Wanaku registration endpoint URL |
| `registration.announce.address` | String | No | `auto` | Address announced to discovery service |
| `grpc.port` | Integer | No | `9190` | gRPC server port |
| `service.name` | String | No | `camel` | Service name for registration |
| `routes.ref` | String | Yes | - | Reference to Camel routes YAML |
| `rules.ref` | String | No | - | Reference to exposure rules YAML |
| `token.endpoint` | String | No | - | OAuth token endpoint |
| `client.id` | String | Yes | - | OAuth client ID |
| `client.secret` | String | Yes | - | OAuth client secret |
| `dependencies` | String | No | - | Comma-separated dependency refs |
| `repositories` | String | No | - | Comma-separated repository URLs |
| `init.from` | String | No | - | Git repository URL to clone |
| `data.dir` | String | No | `/tmp` | Data directory path |

### Environment Variable Override

Each property can be overridden by environment variable. Environment variables take precedence.

| Property | Environment Variable |
|----------|---------------------|
| `registration.url` | `REGISTRATION_URL` |
| `registration.announce.address` | `REGISTRATION_ANNOUNCE_ADDRESS` |
| `grpc.port` | `GRPC_PORT` |
| `service.name` | `SERVICE_NAME` |
| `routes.ref` | `ROUTES_PATH` |
| `rules.ref` | `ROUTES_RULES` |
| `token.endpoint` | `TOKEN_ENDPOINT` |
| `client.id` | `CLIENT_ID` |
| `client.secret` | `CLIENT_SECRET` |
| `dependencies` | `DEPENDENCIES` |
| `repositories` | `REPOSITORIES` |
| `init.from` | `INIT_FROM` |
| `data.dir` | `DATA_DIR` |

## Error Handling

| Condition | Behavior |
|-----------|----------|
| Missing required property | Throw `IllegalStateException` with clear message |
| Invalid property value | Throw `IllegalArgumentException` with validation details |
| Resource download failure | Log error, retry based on configuration |
| gRPC server start failure | Throw exception, prevent context start |
| Discovery registration failure | Log warning, continue (non-fatal) |

## Thread Safety

- `load()` and `unload()` are called from Camel context lifecycle thread
- Internal state must be thread-safe for concurrent gRPC requests
- Use `volatile` or synchronization for mutable state
