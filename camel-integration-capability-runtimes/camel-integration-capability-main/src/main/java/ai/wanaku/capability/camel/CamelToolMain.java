package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
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
    private static final Pattern FORWARD_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "display a help message")
    private boolean helpRequested = false;

    @CommandLine.Option(
            names = {"--registration-url"},
            description =
                    "The Wanaku Barn base URL used to download datastore:// resources and service catalogs. Not used for registration",
            defaultValue = "http://localhost:8080",
            required = true)
    private String registrationUrl;

    @CommandLine.Option(
            names = {"--name"},
            description = "The service name. Used as the forward name when --register is set",
            defaultValue = "camel")
    private String name;

    @CommandLine.Option(
            names = {"--retries"},
            description = "The maximum number of retries for downloads and Wanaku registration",
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

    @CommandLine.Option(
            names = {"--register"},
            arity = "0..1",
            description = "Register this capability as a forward with Wanaku on startup and remove it on shutdown",
            defaultValue = "false")
    private boolean register;

    @CommandLine.Option(
            names = {"--wanaku-url"},
            description = "The Wanaku management API URL (for example, http://wanaku:8080). Required with --register")
    private String wanakuUrl;

    @CommandLine.Option(
            names = {"--namespace"},
            description = "The Wanaku namespace for the forward",
            defaultValue = "default")
    private String namespace;

    @CommandLine.Option(
            names = {"--advertised-address"},
            description =
                    "The MCP address that Wanaku uses to reach this capability (for example, http://my-tool:9090/mcp). Required with --register")
    private String advertisedAddress;

    @CommandLine.Option(
            names = {"--wanaku-token-env"},
            description = "The name of the environment variable that contains a bearer token for the Wanaku API")
    private String wanakuTokenEnv;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CamelToolMain()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        validateRegistrationOptions();
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

        WanakuForwardRegistration registration = createRegistration(downloadedResources.get(ResourceType.ROUTES_REF));

        WanakuCamelManager camelManager =
                new WanakuCamelManager(downloadedResources, repositoriesList, mcpTags, mcpPort, policy);
        return camelManager.run(registration);
    }

    void validateRegistrationOptions() {
        if (!register) {
            return;
        }
        if (isBlank(wanakuUrl)) {
            throw usageError("--wanaku-url is required with --register");
        }
        if (isBlank(advertisedAddress)) {
            throw usageError("--advertised-address is required with --register");
        }
        if (!isHttpUrl(wanakuUrl)) {
            throw usageError("--wanaku-url must be an http or https URL: " + wanakuUrl);
        }
        if (!isHttpUrl(advertisedAddress)) {
            throw usageError("--advertised-address must be an http or https URL: " + advertisedAddress);
        }
        if (name == null || !FORWARD_NAME.matcher(name).matches()) {
            throw usageError("--name must contain only letters, digits, '.', '_' and '-' with --register: " + name);
        }
        if (mcpPort <= 0) {
            throw usageError("--register requires the MCP server (--mcp-port greater than 0)");
        }
        if (wanakuTokenEnv != null && isBlank(System.getenv(wanakuTokenEnv))) {
            throw usageError("The environment variable named by --wanaku-token-env is not set: " + wanakuTokenEnv);
        }
    }

    WanakuForwardRegistration createRegistration(Path routesFile) throws IOException {
        if (!register) {
            return null;
        }
        String token = wanakuTokenEnv == null ? null : System.getenv(wanakuTokenEnv);
        WanakuForwardRegistration.Settings settings = new WanakuForwardRegistration.Settings(
                wanakuUrl, name, namespace, advertisedAddress, token, retries, Duration.ofSeconds(retryWaitSeconds));
        return new WanakuForwardRegistration(
                settings, Map.of("cic.version", VersionHelper.VERSION.trim(), "cic.routes-digest", sha256(routesFile)));
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private CommandLine.ParameterException usageError(String message) {
        return new CommandLine.ParameterException(spec.commandLine(), message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
