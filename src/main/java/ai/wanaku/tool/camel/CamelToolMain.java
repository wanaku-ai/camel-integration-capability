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

import ai.wanaku.tool.camel.grpc.CamelTool;
import ai.wanaku.tool.camel.grpc.InquireBase;
import ai.wanaku.tool.camel.registry.ServiceTarget;
import ai.wanaku.tool.camel.registry.ServiceType;
import ai.wanaku.tool.camel.registry.ValkeyRegistry;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class CamelToolMain implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(CamelToolMain.class);
    private static final String DEFAULT_ROUTE_FILE = "routes.yaml";

    @CommandLine.Option(names = {"--valkey-host"}, description = "The valkey server to use", required = true)
    private String valkeyHost;

    @CommandLine.Option(names = {"--valkey-port"}, description = "The valkey port to use", defaultValue = "9092")
    private int valkeyPort;

    @CommandLine.Option(names = {"--register-host"}, description = "The address to register", required = true)
    private String registerHost;

    @CommandLine.Option(names = {"--register-port"}, description = "The gRPC port to use", defaultValue = "9190")
    private int grpcPort;

    @CommandLine.Option(names = {"--routes-path"}, description = "The path where the routes are stored")
    private String routesPath;


//    @CommandLine.Option(names = {"--consumes-from"}, description = "The Kafka topic from which to consume the trigger event")
//    private String consumesFrom;
//


//    @CommandLine.Option(names = {"--step"}, description = "The step to run on event (should contain a file named " + DEFAULT_ROUTE_FILE + ")", required = true)
//    private String step;

//    @CommandLine.Option(names = {"-d", "--dependencies"}, description = "The list of dependencies to include in runtime (comma-separated)")
//    private String dependenciesList;
//
//    @CommandLine.Option(names = {"--wait"}, description = "Wait forever until a file is created", defaultValue = "false")
//    private boolean waitForever;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CamelToolMain()).execute(args);

        System.exit(exitCode);
    }

    private JedisPool redisClient() {
        JedisPoolConfig config = new JedisPoolConfig();

        // It is recommended that you set maxTotal = maxIdle = 2*minIdle for best performance
        config.setMaxTotal(32);
        config.setMaxIdle(32);
        config.setMinIdle(16);

        return new JedisPool(config, valkeyHost, valkeyPort, 10, null);
    }

    @Override
    public Integer call() throws Exception {
        ValkeyRegistry registry = new ValkeyRegistry(redisClient());

        try {

            final ServerBuilder<?> serverBuilder = Grpc.newServerBuilderForPort(grpcPort, InsecureServerCredentials.create());
            final Server server = serverBuilder.addService(new CamelTool(routesPath))
                    .addService(new InquireBase())
                    .build();

            registry.register(ServiceTarget.toolInvoker("camel-core", registerHost, grpcPort), Map.of());

            server.start();
            server.awaitTermination();
        } finally {
            registry.deregister("camel-core", ServiceType.TOOL_INVOKER);
        }

        return 0;
    }
}
