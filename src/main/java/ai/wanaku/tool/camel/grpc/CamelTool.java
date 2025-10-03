package ai.wanaku.tool.camel.grpc;

import ai.wanaku.capabilities.sdk.services.ServicesHttpClient;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
import ai.wanaku.tool.camel.model.Definition;
import ai.wanaku.tool.camel.model.Mapping;
import ai.wanaku.tool.camel.model.McpSpec;
import ai.wanaku.tool.camel.model.Property;
import ai.wanaku.tool.camel.spec.rules.resources.WanakuResourceRuleProcessor;
import ai.wanaku.tool.camel.util.ToolRulesManager;
import ai.wanaku.tool.camel.spec.rules.resources.WanakuResourceTransformer;
import ai.wanaku.tool.camel.util.WanakuRoutesLoader;
import ai.wanaku.tool.camel.spec.rules.tools.WanakuToolRuleProcessor;
import ai.wanaku.tool.camel.spec.rules.tools.WanakuToolTransformer;
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
    private final McpSpec mcpSpec;
    private final ToolRulesManager toolRulesManager;

    public CamelTool(String routesPath, String routesRules, String name, ServicesHttpClient servicesClient) {
        this.routesPath = routesPath;

        this.toolRulesManager = new ToolRulesManager(name, routesRules);
        final WanakuToolTransformer toolTransformer =
                new WanakuToolTransformer(name, new WanakuToolRuleProcessor(servicesClient));
        final WanakuResourceTransformer resourceTransformer =
                new WanakuResourceTransformer(name, new WanakuResourceRuleProcessor(servicesClient));

        this.mcpSpec = toolRulesManager.loadMcpSpecAndRegister(toolTransformer, resourceTransformer);

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

        // Try to find the definition in tools first, then in resources
        Map<String, Definition> tools = toolRulesManager.getTools(mcpSpec);
        Definition toolDefinition = tools.get(host);

        if (toolDefinition == null) {
            LOG.error("No tool or resource definition found for: {}", host);
            responseObserver.onNext(
                    ToolInvokeReply.newBuilder()
                            .setIsError(true)
                            .addAllContent(List.of("No tool or resource definition found for: " + host)).build());
            responseObserver.onCompleted();
            return;
        }

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

    private static Map<String, Object> extractHeaderParameters(ToolInvokeRequest request, Definition toolDefinition) {
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
