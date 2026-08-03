package ai.wanaku.capability.camel;

import java.io.IOException;
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

    // Camel's default management health path; sub-paths /live and /ready are added automatically
    private static final String HEALTH_PATH = "/observe/health";

    public enum RouteLoadingFailurePolicy {
        FAIL_FAST,
        LOG_AND_CONTINUE
    }

    private final CamelContext context;
    private final String routesPath;
    private final RouteLoadingFailurePolicy routeLoadingFailurePolicy;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public WanakuCamelManager(
            Map<ResourceType, Path> downloadedResources,
            String repositoriesList,
            String mcpTags,
            int mcpPort,
            RouteLoadingFailurePolicy routeLoadingFailurePolicy) {
        this(downloadedResources, repositoriesList, mcpTags, mcpPort, 0, routeLoadingFailurePolicy);
    }

    public WanakuCamelManager(
            Map<ResourceType, Path> downloadedResources,
            String repositoriesList,
            String mcpTags,
            int mcpPort,
            int healthPort,
            RouteLoadingFailurePolicy routeLoadingFailurePolicy) {
        this.routeLoadingFailurePolicy =
                Objects.requireNonNull(routeLoadingFailurePolicy, "RouteLoadingFailurePolicy must not be null");

        this.routesPath = downloadedResources.get(ResourceType.ROUTES_REF).toString();

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
        configureHealthChecks();

        if (healthPort > 0) {
            setupManagementServer(healthPort);
        }
    }

    private void configureHealthChecks() {
        HealthCheckRegistry healthCheckRegistry = HealthCheckRegistry.get(context);
        if (healthCheckRegistry == null) {
            LOG.warn("No HealthCheckRegistry available: health checks are disabled");
            return;
        }

        healthCheckRegistry.setEnabled(true);
        // With the "default" exposure level Camel collapses all-UP results into a single
        // summary entry; "full" reports every check individually
        healthCheckRegistry.setExposureLevel("full");

        // A plain CamelContext does not register the standard checks on its own (camel-main
        // does it during auto-configuration), so resolve and register them explicitly
        registerHealthCheck(healthCheckRegistry, "context");
        registerHealthCheck(healthCheckRegistry, "routes");
        registerHealthCheck(healthCheckRegistry, "consumers");
    }

    private static void registerHealthCheck(HealthCheckRegistry healthCheckRegistry, String id) {
        Object check = healthCheckRegistry.resolveById(id);
        if (check != null) {
            healthCheckRegistry.register(check);
        } else {
            LOG.warn("Unable to resolve health check '{}'", id);
        }
    }

    /**
     * Sets up Camel's built-in management HTTP server exposing the Camel Health Check API
     * at {@code /observe/health} (all checks), {@code /observe/health/live} (liveness) and
     * {@code /observe/health/ready} (readiness), for Kubernetes/container probes.
     */
    private void setupManagementServer(int healthPort) {
        try {
            ManagementHttpServer managementServer = new ManagementHttpServer();
            managementServer.setPort(healthPort);
            managementServer.setHealthCheckEnabled(true);
            managementServer.setHealthPath(HEALTH_PATH);
            context.addService(managementServer);
            LOG.info(
                    "HTTP health endpoints available on port {} ({}, {}/live, {}/ready)",
                    healthPort,
                    HEALTH_PATH,
                    HEALTH_PATH,
                    HEALTH_PATH);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup management HTTP server", e);
        }
    }

    private void setupMcpServer(String mcpTags, int mcpPort) {
        try {
            MainHttpServer httpServer = new MainHttpServer();
            httpServer.setPort(mcpPort);
            context.addService(httpServer);

            McpServerConfiguration mcpConfig = new McpServerConfiguration();
            if (mcpTags != null && !mcpTags.isEmpty()) {
                mcpConfig.setTags(mcpTags);
            }
            context.addService(new McpServerBridge(mcpConfig));
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup MCP server", e);
        }
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

    public void run() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            context.stop();
            shutdownLatch.countDown();
        }));

        shutdownLatch.await();
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
