package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.component.platform.http.main.ManagementHttpServer;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultCamelContextNameStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.capabilities.sdk.maven.GAV;
import ai.wanaku.capabilities.sdk.maven.WanakuMavenDownloader;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import ai.wanaku.capabilities.sdk.runtime.camel.exceptions.RouteLoadingException;
import ai.wanaku.capabilities.sdk.runtime.camel.util.WanakuRoutesLoader;
import ai.wanaku.capabilities.sdk.runtime.camel.versions.RuntimeVersionHelper;

public class WanakuCamelManager {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuCamelManager.class);

    public enum RouteLoadingFailurePolicy {
        FAIL_FAST,
        LOG_AND_CONTINUE
    }

    private final CamelContext context;
    private final String routesPath;
    private final RouteLoadingFailurePolicy routeLoadingFailurePolicy;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final int mcpPort;
    private volatile boolean ready;

    public WanakuCamelManager(
            Map<ResourceType, Path> downloadedResources,
            String repositoriesList,
            String mcpTags,
            int mcpPort,
            RouteLoadingFailurePolicy routeLoadingFailurePolicy) {
        this.routeLoadingFailurePolicy =
                Objects.requireNonNull(routeLoadingFailurePolicy, "RouteLoadingFailurePolicy must not be null");

        this.routesPath = downloadedResources.get(ResourceType.ROUTES_REF).toString();
        this.mcpPort = mcpPort;

        List<GAV> gavs;
        if (downloadedResources.containsKey(ResourceType.DEPENDENCY_REF)) {
            Path dependenciesPath = downloadedResources.get(ResourceType.DEPENDENCY_REF);
            try {
                final List<String> depLines = Files.readAllLines(dependenciesPath);
                gavs = depLines.stream()
                        .filter(l -> !l.startsWith("#"))
                        .map(g -> GAV.parse(g, RuntimeVersionHelper.getVersions()))
                        .toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            gavs = List.of();
        }

        WanakuMavenDownloader mavenDownloader = new WanakuMavenDownloader(WanakuCamelManager.class.getClassLoader());
        mavenDownloader.download(gavs);

        context = new DefaultCamelContext();
        context.setApplicationContextClassLoader(mavenDownloader.getClassLoader());

        if (mcpPort > 0) {
            setupMcpServer(mcpTags, mcpPort);
        }

        loadRoutes();
    }

    private void setupMcpServer(String mcpTags, int mcpPort) {
        try {
            MainHttpServer httpServer = new MainHttpServer();
            httpServer.setPort(mcpPort);
            context.addService(httpServer);
            context.setNameStrategy(new DefaultCamelContextNameStrategy("wanaku"));

            McpServerConfiguration mcpConfig = new McpServerConfiguration();
            if (mcpTags != null && !mcpTags.isEmpty()) {
                mcpConfig.setTags(mcpTags);
            }
            context.addService(new McpServerBridge(mcpConfig));

            // Health endpoints share the MCP server port: /observe/health, /observe/health/live, /observe/health/ready
            ManagementHttpServer managementServer = new ManagementHttpServer();
            managementServer.setPort(mcpPort);
            managementServer.setHealthCheckEnabled(true);
            managementServer.setHealthPath("/observe/health");
            context.addService(managementServer);
            configureHealthChecks();
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup MCP server", e);
        }
    }

    private void configureHealthChecks() {
        HealthCheckRegistry registry = HealthCheckRegistry.get(context);
        registry.setEnabled(true);
        // The "default" exposure level collapses all-UP results into one entry; "full" lists every check
        registry.setExposureLevel("full");

        // A plain DefaultCamelContext does not register the standard checks (camel-main does it on
        // auto-configuration), so resolve and register them here
        for (String id : List.of("context", "routes", "consumers")) {
            Object check = registry.resolveById(id);
            if (check != null) {
                registry.register(check);
            } else {
                LOG.warn("Unable to resolve the Camel health check '{}'", id);
            }
        }
        registry.register(new WanakuRegistrationHealthCheck(() -> ready));
    }

    private void loadRoutes() {
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader();
        String routeFileUrl = Path.of(routesPath).toUri().toString();

        try {
            routesLoader.loadRoute(context, routeFileUrl);
        } catch (RouteLoadingException e) {
            if (routeLoadingFailurePolicy == RouteLoadingFailurePolicy.FAIL_FAST) {
                throw e;
            } else {
                LOG.warn(
                        "Failed to load routes, but continuing because route loading policy is LOG_AND_CONTINUE: {}",
                        e.getMessage());
            }
        }

        context.start();
    }

    /**
     * Registers with Wanaku (when a registration is given), then blocks until the JVM shuts down.
     *
     * @param registration the forward registration, or {@code null} when self-registration is off
     * @return the process exit code
     */
    public int run(WanakuForwardRegistration registration) throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown(registration);
            shutdownLatch.countDown();
        }));

        if (!register(registration)) {
            shutdown(registration);
            return 1;
        }

        shutdownLatch.await();
        return 0;
    }

    /**
     * Checks the local MCP server and registers the forward. On failure, removes an owned forward.
     *
     * @param registration the forward registration, or {@code null} when self-registration is off
     * @return {@code true} when the capability is ready
     */
    boolean register(WanakuForwardRegistration registration) throws InterruptedException {
        if (registration != null) {
            try {
                URI endpoint = URI.create("http://127.0.0.1:" + mcpPort + "/mcp");
                int tools = McpSelfCheck.verify(endpoint);
                LOG.info("Local MCP server at {} exposes {} tool(s)", endpoint, tools);
                registration.register();
            } catch (IOException e) {
                LOG.error("Failed to register with Wanaku: {}", e.getMessage());
                deregister(registration);
                return false;
            }
        }
        ready = true;
        return true;
    }

    /**
     * Marks the capability as not ready, removes an owned forward, and stops Camel.
     *
     * @param registration the forward registration, or {@code null} when self-registration is off
     */
    void shutdown(WanakuForwardRegistration registration) {
        ready = false;
        deregister(registration);
        context.stop();
    }

    private static void deregister(WanakuForwardRegistration registration) {
        if (registration == null) {
            return;
        }
        try {
            registration.deregister();
        } catch (IOException e) {
            LOG.warn("Failed to remove the forward from Wanaku: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while removing the forward from Wanaku");
        }
    }

    boolean isReady() {
        return ready;
    }

    public void start() {
        // context already started in constructor via loadRoutes()
    }

    public void stop() {
        context.stop();
    }

    public CamelContext getCamelContext() {
        return context;
    }
}
