package ai.wanaku.capability.camel;

import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import ai.wanaku.capabilities.sdk.common.ServicesHelper;
import ai.wanaku.capabilities.sdk.common.config.DefaultServiceConfig;
import ai.wanaku.capabilities.sdk.common.config.ServiceConfig;
import ai.wanaku.capabilities.sdk.common.serializer.JacksonSerializer;
import ai.wanaku.capabilities.sdk.discovery.DiscoveryServiceHttpClient;
import ai.wanaku.capabilities.sdk.discovery.ZeroDepRegistrationManager;
import ai.wanaku.capabilities.sdk.discovery.config.DefaultRegistrationConfig;
import ai.wanaku.capabilities.sdk.discovery.deserializer.JacksonDeserializer;
import ai.wanaku.capabilities.sdk.discovery.util.DiscoveryHelper;
import ai.wanaku.capabilities.sdk.security.TokenEndpoint;
import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.capability.camel.downloader.DownloaderFactory;
import ai.wanaku.capability.camel.downloader.ResourceDownloaderCallback;
import ai.wanaku.capability.camel.downloader.ResourceListBuilder;
import ai.wanaku.capability.camel.downloader.ResourceRefs;
import ai.wanaku.capability.camel.downloader.ResourceType;
import ai.wanaku.capability.camel.grpc.CamelResource;
import ai.wanaku.capability.camel.grpc.CamelTool;
import ai.wanaku.capability.camel.grpc.ProvisionBase;
import ai.wanaku.capability.camel.init.Initializer;
import ai.wanaku.capability.camel.init.InitializerFactory;
import ai.wanaku.capability.camel.model.McpSpec;
import ai.wanaku.capability.camel.spec.rules.resources.WanakuResourceRuleProcessor;
import ai.wanaku.capability.camel.spec.rules.resources.WanakuResourceTransformer;
import ai.wanaku.capability.camel.spec.rules.tools.WanakuToolRuleProcessor;
import ai.wanaku.capability.camel.spec.rules.tools.WanakuToolTransformer;
import ai.wanaku.capability.camel.util.McpRulesManager;
import ai.wanaku.capability.camel.util.VersionHelper;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin runtime entry point that reads configuration from system properties and environment variables.
 *
 * <p>Configuration priority: System property > Environment variable > Default value
 *
 * <p>Supported configuration:
 * <ul>
 *   <li>wanaku.registration.url / WANAKU_REGISTRATION_URL - The registration URL (required)</li>
 *   <li>wanaku.routes.ref / WANAKU_ROUTES_REF - Reference path to routes YAML file (required)</li>
 *   <li>wanaku.rules.ref / WANAKU_RULES_REF - Reference path to rules YAML file</li>
 *   <li>wanaku.oauth.client-id / WANAKU_OAUTH_CLIENT_ID - OAuth client ID (required)</li>
 *   <li>wanaku.oauth.client-secret / WANAKU_OAUTH_CLIENT_SECRET - OAuth client secret (required)</li>
 *   <li>wanaku.grpc.port / WANAKU_GRPC_PORT - gRPC port (default: 9190)</li>
 *   <li>wanaku.service.name / WANAKU_SERVICE_NAME - Service name (default: camel)</li>
 *   <li>wanaku.oauth.token-endpoint / WANAKU_OAUTH_TOKEN_ENDPOINT - Token endpoint URL</li>
 *   <li>wanaku.data.dir / WANAKU_DATA_DIR - Data directory (default: /tmp)</li>
 *   <li>wanaku.dependencies / WANAKU_DEPENDENCIES - Dependencies reference</li>
 *   <li>wanaku.repositories / WANAKU_REPOSITORIES - Additional repositories</li>
 *   <li>wanaku.registration.announce-address / WANAKU_REGISTRATION_ANNOUNCE_ADDRESS - Announce address (default: auto)</li>
 *   <li>wanaku.registration.retries / WANAKU_REGISTRATION_RETRIES - Max retries (default: 12)</li>
 *   <li>wanaku.registration.wait-seconds / WANAKU_REGISTRATION_WAIT_SECONDS - Wait seconds (default: 5)</li>
 *   <li>wanaku.registration.initial-delay / WANAKU_REGISTRATION_INITIAL_DELAY - Initial delay (default: 5)</li>
 *   <li>wanaku.registration.period / WANAKU_REGISTRATION_PERIOD - Registration period (default: 5)</li>
 *   <li>wanaku.init.from / WANAKU_INIT_FROM - Git repository URL for initialization</li>
 * </ul>
 */
public class CamelPluginMain {
    private static final Logger LOG = LoggerFactory.getLogger(CamelPluginMain.class);

    private final String registrationUrl;
    private final int grpcPort;
    private final String registrationAnnounceAddress;
    private final String name;
    private final int retries;
    private final int retryWaitSeconds;
    private final long initialDelay;
    private final long period;
    private final String routesRef;
    private final String rulesRef;
    private final String tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String dependenciesRef;
    private final String repositoriesList;
    private final String dataDir;
    private final String initFrom;

    public CamelPluginMain() {
        this.registrationUrl = getConfig("wanaku.registration.url", "WANAKU_REGISTRATION_URL", null);
        this.grpcPort = Integer.parseInt(getConfig("wanaku.grpc.port", "WANAKU_GRPC_PORT", "9190"));
        this.registrationAnnounceAddress =
                getConfig("wanaku.registration.announce-address", "WANAKU_REGISTRATION_ANNOUNCE_ADDRESS", "auto");
        this.name = getConfig("wanaku.service.name", "WANAKU_SERVICE_NAME", "camel");
        this.retries = Integer.parseInt(getConfig("wanaku.registration.retries", "WANAKU_REGISTRATION_RETRIES", "12"));
        this.retryWaitSeconds = Integer.parseInt(
                getConfig("wanaku.registration.wait-seconds", "WANAKU_REGISTRATION_WAIT_SECONDS", "5"));
        this.initialDelay = Long.parseLong(
                getConfig("wanaku.registration.initial-delay", "WANAKU_REGISTRATION_INITIAL_DELAY", "5"));
        this.period = Long.parseLong(getConfig("wanaku.registration.period", "WANAKU_REGISTRATION_PERIOD", "5"));
        this.routesRef = getConfig("wanaku.routes.ref", "WANAKU_ROUTES_REF", null);
        this.rulesRef = getConfig("wanaku.rules.ref", "WANAKU_RULES_REF", null);
        this.tokenEndpoint = getConfig("wanaku.oauth.token-endpoint", "WANAKU_OAUTH_TOKEN_ENDPOINT", null);
        this.clientId = getConfig("wanaku.oauth.client-id", "WANAKU_OAUTH_CLIENT_ID", null);
        this.clientSecret = getConfig("wanaku.oauth.client-secret", "WANAKU_OAUTH_CLIENT_SECRET", null);
        this.dependenciesRef = getConfig("wanaku.dependencies", "WANAKU_DEPENDENCIES", null);
        this.repositoriesList = getConfig("wanaku.repositories", "WANAKU_REPOSITORIES", null);
        this.dataDir = getConfig("wanaku.data.dir", "WANAKU_DATA_DIR", "/tmp");
        this.initFrom = getConfig("wanaku.init.from", "WANAKU_INIT_FROM", null);
    }

    /**
     * Gets configuration value with priority: System property > Environment variable > Default value.
     */
    private static String getConfig(String systemProperty, String envVariable, String defaultValue) {
        String value = System.getProperty(systemProperty);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = System.getenv(envVariable);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return defaultValue;
    }

    public static void main(String[] args) {
        CamelPluginMain main = new CamelPluginMain();
        int exitCode = main.run();
        System.exit(exitCode);
    }

    public RegistrationManager newRegistrationManager(
            ServiceTarget serviceTarget,
            ResourceDownloaderCallback resourcesDownloaderCallback,
            ServiceConfig serviceConfig) {
        DiscoveryServiceHttpClient discoveryServiceHttpClient = new DiscoveryServiceHttpClient(serviceConfig);

        final DefaultRegistrationConfig registrationConfig = DefaultRegistrationConfig.Builder.newBuilder()
                .initialDelay(initialDelay)
                .period(period)
                .dataDir(ServicesHelper.getCanonicalServiceHome(name))
                .maxRetries(retries)
                .waitSeconds(retryWaitSeconds)
                .build();

        ZeroDepRegistrationManager registrationManager = new ZeroDepRegistrationManager(
                discoveryServiceHttpClient, serviceTarget, registrationConfig, new JacksonDeserializer());

        registrationManager.addCallBack(resourcesDownloaderCallback);
        registrationManager.start();

        return registrationManager;
    }

    private ServiceTarget newServiceTargetTarget() {
        String address = DiscoveryHelper.resolveRegistrationAddress(registrationAnnounceAddress);
        return ServiceTarget.newEmptyTarget(name, address, grpcPort, ServiceType.MULTI_CAPABILITY.asValue());
    }

    public ServicesHttpClient createClient(ServiceConfig serviceConfig) {
        return new ServicesHttpClient(serviceConfig);
    }

    public int run() {
        try {
            return doRun();
        } catch (Exception e) {
            LOG.error("Failed to start Camel Integration Capability: {}", e.getMessage(), e);
            return 1;
        }
    }

    private int doRun() throws Exception {
        // Validate required configuration
        if (registrationUrl == null || registrationUrl.isEmpty()) {
            LOG.error("Registration URL is required. Set wanaku.registration.url or WANAKU_REGISTRATION_URL");
            return 1;
        }
        if (routesRef == null || routesRef.isEmpty()) {
            LOG.error("Routes reference is required. Set wanaku.routes.ref or WANAKU_ROUTES_REF");
            return 1;
        }
        if (clientId == null || clientId.isEmpty()) {
            LOG.error("Client ID is required. Set wanaku.oauth.client-id or WANAKU_OAUTH_CLIENT_ID");
            return 1;
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            LOG.error("Client secret is required. Set wanaku.oauth.client-secret or WANAKU_OAUTH_CLIENT_SECRET");
            return 1;
        }

        LOG.info("Camel Integration Capability {} is starting (plugin mode)", VersionHelper.VERSION);

        // Create the data directory first (needed by initializers)
        Path dataDirPath = Paths.get(dataDir);
        Files.createDirectories(dataDirPath);
        LOG.info("Using data directory: {}", dataDirPath.toAbsolutePath());

        // Resource initialization / acquisition should happen here
        Initializer initializer = InitializerFactory.createInitializer(initFrom, dataDirPath);
        initializer.initialize();

        final ServiceConfig serviceConfig = DefaultServiceConfig.Builder.newBuilder()
                .baseUrl(registrationUrl)
                .serializer(new JacksonSerializer())
                .clientId(clientId)
                .tokenEndpoint(TokenEndpoint.autoResolve(registrationUrl, tokenEndpoint))
                .secret(clientSecret)
                .build();

        ServicesHttpClient httpClient = createClient(serviceConfig);
        DownloaderFactory downloaderFactory = new DownloaderFactory(httpClient, dataDirPath);

        List<ResourceRefs<URI>> resources = ResourceListBuilder.newBuilder()
                .addRoutesRef(routesRef)
                .addRulesRef(rulesRef)
                .addDependenciesRef(dependenciesRef)
                .build();

        ResourceDownloaderCallback resourcesDownloaderCallback =
                new ResourceDownloaderCallback(downloaderFactory, resources);

        final ServiceTarget toolInvokerTarget = newServiceTargetTarget();
        RegistrationManager registrationManager =
                newRegistrationManager(toolInvokerTarget, resourcesDownloaderCallback, serviceConfig);

        if (!resourcesDownloaderCallback.waitForDownloads()) {
            LOG.error("Failed to download required resources (check the logs)");
            return 1;
        }

        final Map<ResourceType, Path> downloadedResources = resourcesDownloaderCallback.getDownloadedResources();
        WanakuCamelManager camelManager = new WanakuCamelManager(downloadedResources, repositoriesList);

        McpSpec mcpSpec = createMcpSpec(httpClient, downloadedResources);

        try {
            final ServerBuilder<?> serverBuilder =
                    Grpc.newServerBuilderForPort(grpcPort, InsecureServerCredentials.create());
            final Server server = serverBuilder
                    .addService(new CamelTool(camelManager, mcpSpec))
                    .addService(new CamelResource(camelManager, mcpSpec))
                    .addService(new ProvisionBase(name))
                    .build();

            server.start();
            server.awaitTermination();
        } finally {
            registrationManager.deregister();
        }

        return 0;
    }

    public McpSpec createMcpSpec(ServicesHttpClient servicesClient, Map<ResourceType, Path> downloadedResources) {
        String rulesRef =
                downloadedResources.get(ResourceType.RULES_REF).toAbsolutePath().toString();
        McpRulesManager mcpRulesManager = new McpRulesManager(name, rulesRef);
        final WanakuToolTransformer toolTransformer =
                new WanakuToolTransformer(name, new WanakuToolRuleProcessor(servicesClient));
        final WanakuResourceTransformer resourceTransformer =
                new WanakuResourceTransformer(name, new WanakuResourceRuleProcessor(servicesClient));

        return mcpRulesManager.loadMcpSpecAndRegister(toolTransformer, resourceTransformer);
    }
}
