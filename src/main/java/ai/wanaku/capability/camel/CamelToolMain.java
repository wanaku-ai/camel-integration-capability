package ai.wanaku.capability.camel;

import ai.wanaku.api.discovery.RegistrationManager;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
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
import ai.wanaku.capability.camel.downloader.DataStoreDownloader;
import ai.wanaku.capability.camel.downloader.ResourceDownloaderCallback;
import ai.wanaku.capability.camel.downloader.ResourceRefs;
import ai.wanaku.capability.camel.downloader.ResourceType;
import ai.wanaku.capability.camel.grpc.CamelResource;
import ai.wanaku.capability.camel.grpc.CamelTool;
import ai.wanaku.capability.camel.grpc.ProvisionBase;
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
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class CamelToolMain implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(CamelToolMain.class);

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @CommandLine.Option(names = {"--registration-url"}, description = "The registration URL to use", required = true)
    private String registrationUrl;

    @CommandLine.Option(names = {"--grpc-port"}, description = "The gRPC port to use", defaultValue = "9190")
    private int grpcPort;

    @CommandLine.Option(names = {"--registration-announce-address"}, description = "The announce address to use when registering",
        defaultValue = "auto", required = true)
    private String registrationAnnounceAddress;

    @CommandLine.Option(names = {"--name"}, description = "The service name to use", defaultValue = "camel")
    private String name;

    @CommandLine.Option(names = {"--retries"}, description = "The maximum number of retries for registration", defaultValue = "12")
    private int retries;

    @CommandLine.Option(names = {"--wait-seconds"}, description = "The retry wait seconds between attempts", defaultValue = "5")
    private int retryWaitSeconds;

    @CommandLine.Option(names = {"--initial-delay"}, description = "Initial delay for registration attempts in seconds", defaultValue = "5")
    private long initialDelay;

    @CommandLine.Option(names = {"--period"}, description = "Period between registration attempts in seconds", defaultValue = "5")
    private long period;

    @CommandLine.Option(names = {"--routes-ref"}, description = "The reference path to the Apache Camel routes YAML file (i.e.: datastore://routes.camel.yaml)", required = true)
    private String routesRef;

    @CommandLine.Option(names = {"--rules-ref"}, description = "The path to the YAML file with route exposure rules (i.e.: datastore://routes-expose.yaml)")
    private String rulesRef;

    @CommandLine.Option(names = {"--token-endpoint"}, description = "The base URL for the authentication", required = true)
    private String tokenEndpoint;

    @CommandLine.Option(names = {"--client-id"}, description = "The client ID authentication", required = true)
    private String clientId;

    @CommandLine.Option(names = {"--client-secret"}, description = "The client secret authentication", required = true)
    private String clientSecret;

    @CommandLine.Option(names = {"--no-wait"}, description = "Do not wait forever until the files are available", defaultValue = "false")
    private boolean noWait;

    @CommandLine.Option(names = {"-d", "--dependencies"}, description = "The list of dependencies to include in runtime (comma-separated)")
    private String dependenciesList;

    @CommandLine.Option(names = {"--data-dir"}, description = "The directory where downloaded files will be saved", defaultValue = "/tmp")
    private String dataDir;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CamelToolMain()).execute(args);

        System.exit(exitCode);
    }

    public RegistrationManager newRegistrationManager(ServiceTarget serviceTarget, ResourceDownloaderCallback resourcesDownloaderCallback,
            ServiceConfig serviceConfig) {
        DiscoveryServiceHttpClient discoveryServiceHttpClient = new DiscoveryServiceHttpClient(serviceConfig);

        final DefaultRegistrationConfig registrationConfig = DefaultRegistrationConfig.Builder.newBuilder()
                .initialDelay(initialDelay)
                .period(period)
                .dataDir(ServicesHelper.getCanonicalServiceHome(name))
                .maxRetries(retries)
                .waitSeconds(retryWaitSeconds)
                .build();

        ZeroDepRegistrationManager
                registrationManager = new ZeroDepRegistrationManager(discoveryServiceHttpClient, serviceTarget, registrationConfig, new JacksonDeserializer());

        registrationManager.addCallBack(resourcesDownloaderCallback);
        registrationManager.start();

        return registrationManager;
    }

    private ServiceTarget newServiceTargetTarget() {
        String address = DiscoveryHelper.resolveRegistrationAddress(registrationAnnounceAddress);
        return ServiceTarget.newEmptyTarget(name, address, grpcPort, ServiceType.MULTI_CAPABILITY);
    }

    public ServicesHttpClient createClient(ServiceConfig serviceConfig) {
        return new ServicesHttpClient(serviceConfig);
    }

    @Override
    public Integer call() throws Exception {
        LOG.info("Camel Integration Capability {} is starting", VersionHelper.VERSION);

        final ResourceRefs<URI> pathResourceRefs =
                ResourceRefs.newRoutesRef(routesRef);

        final ResourceRefs<URI> pathRulesRefs1 =
                ResourceRefs.newRulesRef(rulesRef);

        final ResourceRefs<URI> depPath =
                ResourceRefs.newDependencyRef(dependenciesList);

        final ServiceConfig serviceConfig = DefaultServiceConfig.Builder.newBuilder()
                .baseUrl(registrationUrl)
                .serializer(new JacksonSerializer())
                .clientId(clientId)
                .tokenEndpoint(TokenEndpoint.fromBaseUrl(tokenEndpoint))
                .secret(clientSecret)
                .build();

        // Create the data directory if it doesn't exist
        Path dataDirPath = Paths.get(dataDir);
        Files.createDirectories(dataDirPath);
        LOG.info("Using data directory: {}", dataDirPath.toAbsolutePath());

        ServicesHttpClient httpClient = createClient(serviceConfig);
        DataStoreDownloader downloader = new DataStoreDownloader(httpClient, dataDirPath);
        ResourceDownloaderCallback resourcesDownloaderCallback = new ResourceDownloaderCallback(downloader,
                List.of(pathResourceRefs, pathRulesRefs1, depPath));

        final ServiceTarget toolInvokerTarget = newServiceTargetTarget();
        RegistrationManager registrationManager = newRegistrationManager(toolInvokerTarget, resourcesDownloaderCallback, serviceConfig);

        if (!resourcesDownloaderCallback.waitForDownloads()) {
            LOG.error("Failed to download required resources (check the logs)");
            return 1;
        }

        final Map<ResourceType, Path> downloadedResources =
                resourcesDownloaderCallback.getDownloadedResources();
        WanakuCamelManager camelManager = new WanakuCamelManager(downloadedResources);

        McpSpec mcpSpec = createMcpSpec(httpClient, downloadedResources);

        try {

            final ServerBuilder<?> serverBuilder = Grpc.newServerBuilderForPort(grpcPort, InsecureServerCredentials.create());
            final Server server = serverBuilder.addService(new CamelTool(camelManager, mcpSpec))
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

    public McpSpec createMcpSpec(ServicesHttpClient servicesClient,
            Map<ResourceType, Path> downloadedResources) {
        String rulesRef = downloadedResources.get(ResourceType.RULES_REF).toAbsolutePath().toString();
        McpRulesManager mcpRulesManager = new McpRulesManager(name, rulesRef);
        final WanakuToolTransformer toolTransformer =
                new WanakuToolTransformer(name, new WanakuToolRuleProcessor(servicesClient));
        final WanakuResourceTransformer resourceTransformer =
                new WanakuResourceTransformer(name, new WanakuResourceRuleProcessor(servicesClient));

        return mcpRulesManager.loadMcpSpecAndRegister(toolTransformer, resourceTransformer);
    }
}
