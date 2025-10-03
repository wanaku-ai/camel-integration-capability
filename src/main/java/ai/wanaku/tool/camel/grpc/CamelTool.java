package ai.wanaku.tool.camel.grpc;

import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
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
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelTool extends ToolInvokerGrpc.ToolInvokerImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(CamelTool.class);

    private final McpSpec mcpSpec;
    private final WanakuCamelManager camelManager;

    public CamelTool(WanakuCamelManager camelManager, McpSpec spec) {
        this.camelManager = camelManager;
        this.mcpSpec = spec;
    }


    public Map<String, Definition> getTools(McpSpec mcpSpec) {
        if (mcpSpec == null || mcpSpec.getMcp() == null || mcpSpec.getMcp().getTools() == null) {
            return Collections.emptyMap();
        }
        return mcpSpec.getMcp().getTools().getDefinitions();
    }

    @Override
    public void invokeTool(ToolInvokeRequest request, StreamObserver<ToolInvokeReply> responseObserver) {
        LOG.debug("About to run a Camel route as tool");

        final String uri = request.getUri();
        URI routeUri = URI.create(uri);
        final String host = routeUri.getHost();

        // Try to find the definition in tools first, then in resources
        Map<String, Definition> tools = getTools(mcpSpec);
        Definition toolDefinition = tools.get(host);

        if (toolDefinition == null) {
            LOG.error("No tool definition found for: {}", host);
            responseObserver.onNext(
                    ToolInvokeReply.newBuilder()
                            .setIsError(true)
                            .addAllContent(List.of("No tool or resource definition found for: " + host)).build());
            responseObserver.onCompleted();
            return;
        }

        final Map<String, Object> headers = extractHeaderParameters(request, toolDefinition);

        final ProducerTemplate producerTemplate = camelManager.getCamelContext().createProducerTemplate();

        final String routeId = toolDefinition.getRoute().getId();
        final Route route = camelManager.getCamelContext().getRoute(routeId);
        final String endpointUri = route.getEndpoint().getEndpointUri();

        final Object o;
        if (!headers.isEmpty()) {
            o = producerTemplate.requestBodyAndHeaders(endpointUri, request.getBody(), headers);
        } else {
            o = producerTemplate.requestBody(endpointUri, request.getBody());
        }

        responseObserver.onNext(
                ToolInvokeReply.newBuilder()
                        .setIsError(false)
                        .addAllContent(List.of(o.toString())).build());

        responseObserver.onCompleted();

    }

    private static Map<String, Object> extractHeaderParameters(ToolInvokeRequest request, Definition toolDefinition) {
        final Map<String, String> argumentsMap = request.getArgumentsMap();
        return McpUtil.convertMcpMapToCamelHeaders(toolDefinition, argumentsMap);
    }


}
