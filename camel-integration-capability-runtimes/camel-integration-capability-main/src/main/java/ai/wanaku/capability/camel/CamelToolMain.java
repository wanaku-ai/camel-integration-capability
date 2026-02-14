package ai.wanaku.capability.camel;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
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
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.DownloaderFactory;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceDownloaderCallback;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceListBuilder;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceRefs;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import ai.wanaku.capabilities.sdk.runtime.camel.grpc.CamelResource;
import ai.wanaku.capabilities.sdk.runtime.camel.grpc.CamelTool;
import ai.wanaku.capabilities.sdk.runtime.camel.grpc.ProvisionBase;
import ai.wanaku.capabilities.sdk.runtime.camel.init.Initializer;
import ai.wanaku.capabilities.sdk.runtime.camel.init.InitializerFactory;
import ai.wanaku.capabilities.sdk.runtime.camel.model.McpSpec;
import ai.wanaku.capabilities.sdk.runtime.camel.spec.rules.resources.WanakuResourceRuleProcessor;
import ai.wanaku.capabilities.sdk.runtime.camel.spec.rules.resources.WanakuResourceTransformer;
import ai.wanaku.capabilities.sdk.runtime.camel.spec.rules.tools.WanakuToolRuleProcessor;
import ai.wanaku.capabilities.sdk.runtime.camel.spec.rules.tools.WanakuToolTransformer;
import ai.wanaku.capabilities.sdk.runtime.camel.util.McpRulesManager;
import ai.wanaku.capabilities.sdk.security.TokenEndpoint;
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
            description = "The registration URL to use",
            required = true)
    private String registrationUrl;

    @CommandLine.Option(
            names = {"--grpc-port"},
            description = "The gRPC port to use",
            defaultValue = "9190")
    private int grpcPort;

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
            description = "The maximum number of retries for registration",
            defaultValue = "12")
    private int retries;

    @CommandLine.Option(
            names = {"--wait-seconds"},
            description = "The retry wait seconds between attempts",
            defaultValue = "5")
    private int retryWaitSeconds;

    @CommandLine.Option(
            names = {"--initial-delay"},
            description = "Initial delay for registration attempts in seconds",
            defaultValue = "5")
    private long initialDelay;

    @CommandLine.Option(
            names = {"--period"},
            description = "Period between registration attempts in seconds",
            defaultValue = "5")
    private long period;

    @CommandLine.Option(
            names = {"--routes-ref"},
            description =
                    "The reference path to the Apache Camel routes YAML file. Supports datastore:// and file:// schemes (e.g., datastore://routes.camel.yaml or file:///path/to/routes.yaml)",
            required = true)
    private String routesRef;

    @CommandLine.Option(
            names = {"--rules-ref"},
            description =
                    "The path to the YAML file with route exposure rules. Supports datastore:// and file:// schemes (e.g., datastore://routes-expose.yaml or file:///path/to/rules.yaml)")
    private String rulesRef;

    @CommandLine.Option(
            names = {"--token-endpoint"},
            description = "The base URL for the authentication",
            required = false)
    private String tokenEndpoint;

    @CommandLine.Option(
            names = {"--client-id"},
            description = "The client ID authentication",
            required = true)
    private String clientId;

    @CommandLine.Option(
            names = {"--client-secret"},
            description = "The client secret authentication",
            required = true)
    private String clientSecret;

    @CommandLine.Option(
            names = {"--no-wait"},
            description = "Do not wait forever until the files are available",
            defaultValue = "false")
    private boolean noWait;

    @CommandLine.Option(
            names = {"-d", "--dependencies"},
            description =
                    "The dependencies to include in runtime. Supports datastore:// and file:// schemes (comma-separated)")
    private String dependenciesRef;

    @CommandLine.Option(
            names = {"--repositories"},
            description =
                    "Comma-separated list of additional repositories from which to download dependencies to include in runtime (i.e.: https://my-private-repo.com/) ")
    private String repositoriesList;

    @CommandLine.Option(
            names = {"--data-dir"},
            description = "The directory where downloaded files will be saved",
            defaultValue = "/tmp")
    private String dataDir;

    @CommandLine.Option(
            names = {"--init-from"},
            description =
                    "Git repository URL to clone during initialization. Cloned files can be referenced using file:// (e.g., git@github.com:wanaku-ai/wanaku-recipes.git)")
    private String initFrom;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CamelToolMain()).execute(args);

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

    @Override
    public Integer call() throws Exception {
        LOG.info("Camel Integration Capability {} is starting", VersionHelper.VERSION);

        // Create the data directory first (needed by initializers)
        Path dataDirPath = Paths.get(dataDir);
        Files.createDirectories(dataDirPath);
        LOG.info("Using data directory: {}", dataDirPath.toAbsolutePath());

        // Resource initialization / acquisition should happen here (except, of course, for the ones from the datastore)
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
                    .addService(new CamelTool(camelManager.getCamelContext(), mcpSpec))
                    .addService(new CamelResource(camelManager.getCamelContext(), mcpSpec))
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
