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

package ai.wanaku.tool.camel;

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
import ai.wanaku.tool.camel.grpc.CamelTool;
import ai.wanaku.tool.camel.grpc.ProvisionBase;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.util.concurrent.Callable;
import picocli.CommandLine;

public class CamelToolMain implements Callable<Integer> {
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

    @CommandLine.Option(names = {"--retries"}, description = "The maximum number of retries for registration", defaultValue = "3")
    private int retries;

    @CommandLine.Option(names = {"--wait-seconds"}, description = "The retry wait seconds between attempts", defaultValue = "1")
    private int retryWaitSeconds;

    @CommandLine.Option(names = {"--initial-delay"}, description = "Initial delay for registration attempts in seconds", defaultValue = "0")
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

    public RegistrationManager newRegistrationManager() {
        String address = DiscoveryHelper.resolveRegistrationAddress(registrationAnnounceAddress);
        final ServiceTarget serviceTarget = ServiceTarget.newEmptyTarget(name, address, grpcPort, ServiceType.TOOL_INVOKER);

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
                registrationManager = new ZeroDepRegistrationManager(discoveryServiceHttpClient, serviceTarget, registrationConfig, new JacksonDeserializer());
        registrationManager.start();

        return registrationManager;
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
        RegistrationManager registry = newRegistrationManager();
        ServicesHttpClient httpClient = createClient();

        try {

            final ServerBuilder<?> serverBuilder = Grpc.newServerBuilderForPort(grpcPort, InsecureServerCredentials.create());
            final Server server = serverBuilder.addService(new CamelTool(routesPath, routesRules, name, httpClient))
                    .addService(new ProvisionBase(name))
                    .build();

            server.start();
            server.awaitTermination();
        } finally {
            registry.deregister();
        }

        return 0;
    }
}
