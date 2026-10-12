package ai.wanaku.capability.camel;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.common.config.DefaultServiceConfig;
import ai.wanaku.capabilities.sdk.common.config.ServiceConfig;
import ai.wanaku.capabilities.sdk.common.serializer.JacksonSerializer;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.Downloader;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.DownloaderConfiguration;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.DownloaderFactory;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ExponentialBackoffRetryPolicy;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceListBuilder;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceRefs;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.RetryPolicy;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ServiceCatalogExtractor;
import ai.wanaku.capabilities.sdk.runtime.camel.init.Initializer;
import ai.wanaku.capabilities.sdk.runtime.camel.init.InitializerFactory;
import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.capability.camel.util.VersionHelper;
import picocli.CommandLine;

public class CamelToolMain implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(CamelToolMain.class);

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "display a help message")
    private boolean helpRequested = false;

    @CommandLine.Option(
            names = {"--registration-url"},
            description = "The Wanaku server URL to use for downloading resources",
            defaultValue = "http://localhost:8080",
            required = true)
    private String registrationUrl;

    @CommandLine.Option(
            names = {"--health-port"},
            description =
                    "The HTTP port for the health check endpoints (/observe/health, /observe/health/live, /observe/health/ready)",
            defaultValue = "8081")
    private int healthPort;

    @CommandLine.Option(
            names = {"--no-health"},
            description = "Disable the HTTP health check endpoints",
            defaultValue = "false")
    private boolean noHealth;

    @CommandLine.Option(
            names = {"--registration-announce-address"},
            description = "The announce address to use when registering",
            defaultValue = "auto",
            required = true)
    private String registrationAnnounceAddress;

    @CommandLine.Option(
            names = {"--name"},
            description = "The service name to use",
            defaultValue = "camel")
    private String name;

    @CommandLine.Option(
            names = {"--retries"},
            description = "The maximum number of retries for downloads",
            defaultValue = "12")
    private int retries;

    @CommandLine.Option(
            names = {"--wait-seconds"},
            description = "The retry wait seconds between attempts",
            defaultValue = "5")
    private int retryWaitSeconds;

    static class RouteRefOptions {
        @CommandLine.Option(
                names = {"--routes-ref"},
                required = true,
                description =
                        "The reference path to the Apache Camel routes YAML file. Supports datastore:// and file:// schemes")
        private String routesRef;

        @CommandLine.Option(
                names = {"-d", "--dependencies"},
                description =
                        "The dependencies to include in runtime. Supports datastore:// and file:// schemes (comma-separated)")
        private String dependenciesRef;
    }

    @CommandLine.Option(
            names = {"--repositories"},
            description = "Comma-separated list of additional Maven repositories for dependency resolution")
    private String repositoriesList;

    @CommandLine.Option(
            names = {"--data-dir"},
            description = "The directory where downloaded files will be saved",
            defaultValue = "/tmp")
    private String dataDir;

    @CommandLine.Option(
            names = {"--init-from"},
            description =
                    "Git repository URL to clone during initialization. Cloned files can be referenced using file://")
    private String initFrom;

    static class ServiceCatalogOptions {
        @CommandLine.Option(
                names = {"--service-catalog"},
                required = true,
                description = "The name of the service catalog to use")
        private String serviceCatalog;

        @CommandLine.Option(
                names = {"--service-catalog-system"},
                required = true,
                description = "The system name within the service catalog to use (e.g., employee-check)")
        private String serviceCatalogSystem;
    }

    static class ResourceSourceOptions {
        @CommandLine.ArgGroup(exclusive = false)
        RouteRefOptions routeRefOptions;

        @CommandLine.ArgGroup(exclusive = false)
        ServiceCatalogOptions serviceCatalogOptions;
    }

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    private ResourceSourceOptions resourceSourceOptions;

    @CommandLine.Option(
            names = {"--fail-fast"},
            description = "Fail fast if route loading fails. If false, log and continue.",
            defaultValue = "false")
    private boolean failFast;

    @CommandLine.Option(
            names = {"--mcp-tags"},
            description = "Comma-separated tags for MCP tool filtering (selects which ai-tool routes to expose)",
            defaultValue = "wanaku")
    private String mcpTags;

    @CommandLine.Option(
            names = {"--mcp-port"},
            description = "Port for the MCP server (HTTP/SSE transport)",
            defaultValue = "9090")
    private int mcpPort;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CamelToolMain()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        LOG.info("Camel Integration Capability {} is starting", VersionHelper.VERSION);

        Path dataDirPath = Path.of(dataDir);
        Files.createDirectories(dataDirPath);
        LOG.info("Using data directory: {}", dataDirPath.toAbsolutePath());

        Initializer initializer = InitializerFactory.createInitializer(initFrom, dataDirPath);
        initializer.initialize();

        final ServiceConfig serviceConfig = DefaultServiceConfig.Builder.newBuilder()
                .baseUrl(registrationUrl)
                .serializer(new JacksonSerializer())
                .build();

        Map<ResourceType, Path> downloadedResources = downloadExternalResources(serviceConfig, dataDirPath);
        if (downloadedResources == null) {
            LOG.error("Failed to download external resources");
            return 1;
        }

        final WanakuCamelManager.RouteLoadingFailurePolicy policy = failFast
                ? WanakuCamelManager.RouteLoadingFailurePolicy.FAIL_FAST
                : WanakuCamelManager.RouteLoadingFailurePolicy.LOG_AND_CONTINUE;

        WanakuCamelManager camelManager = new WanakuCamelManager(
                downloadedResources, repositoriesList, mcpTags, mcpPort, noHealth ? 0 : healthPort, policy);
        camelManager.run();

        return 0;
    }

    private Map<ResourceType, Path> downloadExternalResources(ServiceConfig serviceConfig, Path dataDirPath) {
        ServicesHttpClient httpClient = new ServicesHttpClient(serviceConfig);
        DownloaderFactory downloaderFactory = new DownloaderFactory(httpClient, dataDirPath);

        DownloaderConfiguration downloaderConfig = DownloaderConfiguration.newBuilder()
                .retryPolicy(ExponentialBackoffRetryPolicy.newBuilder()
                        .maxRetries(retries)
                        .initialDelayMillis(retryWaitSeconds * 1000L)
                        .build())
                .build();
        RetryPolicy retryPolicy = downloaderConfig.getRetryPolicy();

        if (resourceSourceOptions.serviceCatalogOptions != null) {
            return downloadServiceCatalog(httpClient, dataDirPath, retryPolicy);
        } else {
            return downloadResources(downloaderFactory, retryPolicy);
        }
    }

    private Map<ResourceType, Path> downloadServiceCatalog(
            ServicesHttpClient httpClient, Path dataDirPath, RetryPolicy retryPolicy) {
        String catalogName = resourceSourceOptions.serviceCatalogOptions.serviceCatalog;
        String systemName = resourceSourceOptions.serviceCatalogOptions.serviceCatalogSystem;
        int maxAttempts = 1 + retryPolicy.maxRetries();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LOG.info(
                        "Downloading service catalog '{}' for system '{}' (attempt {}/{})",
                        catalogName,
                        systemName,
                        attempt,
                        maxAttempts);

                WanakuResponse<DataStore> response = httpClient.getServiceCatalog(catalogName);
                if (response == null || response.data() == null) {
                    LOG.error("Service catalog '{}' not found", catalogName);
                    return null;
                }

                DataStore catalog = response.data();
                if (catalog.getData() == null || catalog.getData().isBlank()) {
                    LOG.error("Service catalog '{}' contains no data", catalogName);
                    return null;
                }

                Map<ResourceType, Path> result =
                        ServiceCatalogExtractor.extract(catalog.getData(), systemName, dataDirPath);
                LOG.info("Service catalog extracted successfully ({} resource type(s) mapped)", result.size());
                return result;
            } catch (Exception e) {
                if (attempt >= maxAttempts || !retryPolicy.isRetryable(e)) {
                    LOG.error("Failed to download service catalog '{}': {}", catalogName, e.getMessage(), e);
                    return null;
                }

                long delay = retryPolicy.getDelayMillis(attempt);
                LOG.warn(
                        "Download attempt {}/{} failed for service catalog '{}': {}. Retrying in {} ms",
                        attempt,
                        maxAttempts,
                        catalogName,
                        e.getMessage(),
                        delay);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private Map<ResourceType, Path> downloadResources(DownloaderFactory downloaderFactory, RetryPolicy retryPolicy) {
        ResourceListBuilder builder =
                ResourceListBuilder.newBuilder().addRoutesRef(resourceSourceOptions.routeRefOptions.routesRef);

        if (resourceSourceOptions.routeRefOptions.dependenciesRef != null) {
            builder.addDependenciesRef(resourceSourceOptions.routeRefOptions.dependenciesRef);
        }

        List<ResourceRefs<URI>> resources = builder.build();

        Map<ResourceType, Path> downloadedResources = new HashMap<>();
        int maxAttempts = 1 + retryPolicy.maxRetries();

        for (ResourceRefs<URI> ref : resources) {
            boolean downloaded = false;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    Downloader downloader = downloaderFactory.getDownloader(ref.ref());
                    downloader.downloadResource(ref, downloadedResources);
                    downloaded = true;
                    break;
                } catch (Exception e) {
                    if (attempt >= maxAttempts || !retryPolicy.isRetryable(e)) {
                        LOG.error("Failed to download resource '{}': {}", ref, e.getMessage(), e);
                        break;
                    }

                    long delay = retryPolicy.getDelayMillis(attempt);
                    LOG.warn(
                            "Download attempt {}/{} failed for '{}': {}. Retrying in {} ms",
                            attempt,
                            maxAttempts,
                            ref,
                            e.getMessage(),
                            delay);

                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            if (!downloaded) {
                LOG.error("Failed to download required resource: {}", ref);
                return null;
            }
        }

        return downloadedResources;
    }
}
