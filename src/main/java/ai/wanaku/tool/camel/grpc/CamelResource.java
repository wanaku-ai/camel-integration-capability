package ai.wanaku.tool.camel.grpc;

import ai.wanaku.core.exchange.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.tool.camel.WanakuCamelManager;
import ai.wanaku.tool.camel.model.Definition;
import ai.wanaku.tool.camel.model.Mapping;
import ai.wanaku.tool.camel.model.McpSpec;
import ai.wanaku.tool.camel.model.Property;
import ai.wanaku.tool.camel.util.McpUtil;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelResource extends ResourceAcquirerGrpc.ResourceAcquirerImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(CamelResource.class);

    private final McpSpec mcpSpec;
    private final WanakuCamelManager camelManager;

    public CamelResource(WanakuCamelManager camelManager, McpSpec mcpSpec) {
        this.mcpSpec = mcpSpec;
        this.camelManager = camelManager;
    }

    public Map<String, Definition> getResources(McpSpec mcpSpec) {
        if (mcpSpec == null || mcpSpec.getMcp() == null || mcpSpec.getMcp().getResources() == null) {
            return Collections.emptyMap();
        }
        return mcpSpec.getMcp().getResources().getDefinitions();
    }

    @Override
    public void resourceAcquire(ResourceRequest request, StreamObserver<ResourceReply> responseObserver) {
        LOG.debug("About to run a Camel route as resource provider");

        final String uri = request.getLocation();
        URI routeUri = URI.create(uri);
        final String host = routeUri.getHost();

        Map<String, Definition> resources = getResources(mcpSpec);
        Definition definition = resources.get(host);

        if (definition == null) {
            LOG.error("No tool or resource definition found for: {}", host);
            responseObserver.onNext(
                    ResourceReply.newBuilder()
                            .setIsError(true)
                            .addAllContent(List.of("No resource definition found for: " + host)).build());
            responseObserver.onCompleted();
            return;
        }

        final Route route = camelManager.getCamelContext().getRoute("route-3105");
        try {
            final String endpointUri = route.getEndpoint().getEndpointUri();
            LOG.info("Consuming from {} as {}", endpointUri, routeUri);
            final ConsumerTemplate consumerTemplate = camelManager.getCamelContext().createConsumerTemplate();

            Object ret = consumerTemplate.receiveBody(endpointUri, 5000);
            responseObserver.onNext(
                    ResourceReply.newBuilder()
                            .setIsError(false)
                            .addAllContent(List.of(ret.toString())).build());

            responseObserver.onCompleted();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
