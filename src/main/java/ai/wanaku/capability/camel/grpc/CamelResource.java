package ai.wanaku.capability.camel.grpc;

import ai.wanaku.core.exchange.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.capability.camel.WanakuCamelManager;
import ai.wanaku.capability.camel.model.Definition;
import ai.wanaku.capability.camel.model.McpSpec;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.camel.ConsumerTemplate;
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
            LOG.error("No resource definition found for: {}", host);
            responseObserver.onNext(
                    ResourceReply.newBuilder()
                            .setIsError(true)
                            .addAllContent(List.of("No resource definition found for: " + host)).build());
            responseObserver.onCompleted();
            return;
        }

        final String routeId = definition.getRoute().getId();
        final Route route = camelManager.getCamelContext().getRoute(routeId);
        try {
            final String endpointUri = route.getEndpoint().getEndpointUri();
            LOG.info("Consuming from {} as {}", endpointUri, routeUri);
            final ConsumerTemplate consumerTemplate = camelManager.getCamelContext().createConsumerTemplate();
            Object ret = consumerTemplate.receiveBody(endpointUri, 5000, String.class);
            if (ret != null) {
                responseObserver.onNext(
                        ResourceReply.newBuilder()
                                .setIsError(false)
                                .addAllContent(List.of(ret.toString())).build());
            } else {
                responseObserver.onNext(
                        ResourceReply.newBuilder()
                                .setIsError(true)
                                .addAllContent(List.of("No response for the requested resource call")).build());
            }

            responseObserver.onCompleted();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
