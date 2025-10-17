/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.capability.camel;

import ai.wanaku.api.discovery.RegistrationManager;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import ai.wanaku.capabilities.sdk.common.ServicesHelper;
import ai.wanaku.capabilities.sdk.common.serializer.JacksonSerializer;
import ai.wanaku.capabilities.sdk.discovery.DiscoveryServiceHttpClient;
import ai.wanaku.capabilities.sdk.discovery.ZeroDepRegistrationManager;
import ai.wanaku.capabilities.sdk.discovery.config.DefaultDiscoveryServiceConfig;
import ai.wanaku.capabilities.sdk.discovery.config.DefaultRegistrationConfig;
import ai.wanaku.capabilities.sdk.discovery.config.TokenEndpoint;
import ai.wanaku.capabilities.sdk.discovery.deserializer.JacksonDeserializer;
import ai.wanaku.capabilities.sdk.discovery.util.DiscoveryHelper;
import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.capabilities.sdk.services.config.DefaultServicesClientConfig;
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

    @CommandLine.Option(names = {"--routes-path"}, description = "The path to the Apache Camel routes YAML file (i.e.: /path/to/routes.camel.yaml)", required = true)
    private String routesPath;

    @CommandLine.Option(names = {"--routes-rules"}, description = "The path to the YAML file with route exposure rules (i.e.: /path/to/routes-expose.yaml)")
    private String routesRules;

    @CommandLine.Option(names = {"--token-endpoint"}, description = "The base URL for the authentication", required = true)
    private String tokenEndpoint;

    @CommandLine.Option(names = {"--client-id"}, description = "The client ID authentication", required = true)
    private String clientId;

    @CommandLine.Option(names = {"--client-secret"}, description = "The client secret authentication", required = true)
    private String clientSecret;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CamelToolMain()).execute(args);

        System.exit(exitCode);
    }

    public RegistrationManager newRegistrationManager(ServiceTarget serviceTarget) {
        final DefaultDiscoveryServiceConfig serviceConfig = DefaultDiscoveryServiceConfig.Builder.newBuilder()
                .baseUrl(registrationUrl)
                .serializer(new JacksonSerializer())
                .clientId(clientId)
                .tokenEndpoint(TokenEndpoint.fromBaseUrl(tokenEndpoint))
                .secret(clientSecret)
                .build();

        DiscoveryServiceHttpClient discoveryServiceHttpClient = new DiscoveryServiceHttpClient(serviceConfig);

        final DefaultRegistrationConfig registrationConfig = DefaultRegistrationConfig.Builder.newBuilder()
                .initialDelay(initialDelay)
                .period(period)
                .dataDir(ServicesHelper.getCanonicalServiceHome(name))
                .maxRetries(retries)
                .waitSeconds(retryWaitSeconds)
                .build();

        ZeroDepRegistrationManager
                toolRegistrationManager = new ZeroDepRegistrationManager(discoveryServiceHttpClient, serviceTarget, registrationConfig, new JacksonDeserializer());
        toolRegistrationManager.start();

        return toolRegistrationManager;
    }

    private ServiceTarget newServiceTargetTarget() {
        String address = DiscoveryHelper.resolveRegistrationAddress(registrationAnnounceAddress);
        return ServiceTarget.newEmptyTarget(name, address, grpcPort, ServiceType.MULTI_CAPABILITY);
    }

    public ServicesHttpClient createClient() {
        DefaultServicesClientConfig config = DefaultServicesClientConfig
                .builder()
                .baseUrl(registrationUrl)
                .serializer(new JacksonSerializer())
                .build();

        return new ServicesHttpClient(config);
    }

    @Override
    public Integer call() throws Exception {
        LOG.info("Camel Integration Capability {} is starting", VersionHelper.VERSION);
        final ServiceTarget toolInvokerTarget = newServiceTargetTarget();
        RegistrationManager registrationManager = newRegistrationManager(toolInvokerTarget);

        ServicesHttpClient httpClient = createClient();
        WanakuCamelManager camelManager = new WanakuCamelManager(routesPath);

        McpSpec mcpSpec = createMcpSpec(httpClient);

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

    public McpSpec createMcpSpec(ServicesHttpClient servicesClient) {
        McpRulesManager mcpRulesManager = new McpRulesManager(name, routesRules);
        final WanakuToolTransformer toolTransformer =
                new WanakuToolTransformer(name, new WanakuToolRuleProcessor(servicesClient));
        final WanakuResourceTransformer resourceTransformer =
                new WanakuResourceTransformer(name, new WanakuResourceRuleProcessor(servicesClient));

        return mcpRulesManager.loadMcpSpecAndRegister(toolTransformer, resourceTransformer);
    }
}
