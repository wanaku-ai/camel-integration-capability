package ai.wanaku.tool.camel.grpc;

import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
import ai.wanaku.tool.camel.model.Mapping;
import ai.wanaku.tool.camel.model.Property;
import ai.wanaku.tool.camel.model.Tool;
import ai.wanaku.tool.camel.model.ToolDefinition;
import ai.wanaku.tool.camel.util.ToolRulesManager;
import ai.wanaku.tool.camel.util.WanakuRoutesLoader;
import ai.wanaku.tool.camel.util.WanakuRuleProcessor;
import ai.wanaku.tool.camel.util.WanakuToolTransformer;
import io.grpc.stub.StreamObserver;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelTool extends ToolInvokerGrpc.ToolInvokerImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(CamelTool.class);

    private final String routesPath;

    private final CamelContext context;
    private final Tool tool;

    public CamelTool(String routesPath, String routesRules, String name, ServicesHttpClient servicesClient) {
        this.routesPath = routesPath;

        ToolRulesManager toolRulesManager = new ToolRulesManager(name, routesRules);
        tool = toolRulesManager.loadTools(new WanakuToolTransformer(name), new WanakuRuleProcessor(servicesClient));

        context = new DefaultCamelContext();
        loadRoutes();
    }

    private void loadRoutes() {
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader();

        String routeFileUrl = String.format("file://%s", routesPath);
        routesLoader.loadRoute(context, routeFileUrl);
        context.start();
    }

    @Override
    public void invokeTool(ToolInvokeRequest request, StreamObserver<ToolInvokeReply> responseObserver) {

        LOG.debug("About to load a Camel route");

        final String uri = request.getUri();
        URI routeUri = URI.create(uri);
        final String host = routeUri.getHost();

        final ToolDefinition toolDefinition = tool.getTools().get(host);
        final Map<String, Object> headers = extractHeaderParameters(request, toolDefinition);

        final ProducerTemplate producerTemplate = context.createProducerTemplate();

        final Object o;
        if (!headers.isEmpty()) {
            o = producerTemplate.requestBodyAndHeaders(toolDefinition.getRoute().getUri(), request.getBody(), headers);
        } else {
            o = producerTemplate.requestBody(toolDefinition.getRoute().getUri(), request.getBody());
        }

        responseObserver.onNext(
                ToolInvokeReply.newBuilder()
                        .setIsError(false)
                        .addAllContent(List.of(o.toString())).build());

        responseObserver.onCompleted();

    }

    private static Map<String, Object> extractHeaderParameters(ToolInvokeRequest request, ToolDefinition toolDefinition) {
        final Map<String, String> argumentsMap = request.getArgumentsMap();
        final List<Property> properties = toolDefinition.getProperties();
        Map<String, Object> headers = new HashMap<>();

        for  (Property property : properties) {
            final Mapping mapping = property.getMapping();
            if (mapping != null) {
                final String mappingType = mapping.getType();

                if (mappingType.equals("header")) {
                    LOG.info("Adding header named {} with value {}", mapping.getName(), argumentsMap.get(property.getName()));
                    headers.put(mapping.getName(), argumentsMap.get(property.getName()));
                }
            }

        }
        return headers;
    }
}
