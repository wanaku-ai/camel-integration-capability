package ai.wanaku.tool.camel.grpc;

import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
import ai.wanaku.tool.camel.util.WanakuRoutesLoader;
import io.grpc.stub.StreamObserver;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelTool extends ToolInvokerGrpc.ToolInvokerImplBase {
    private static final Logger LOG = LoggerFactory.getLogger(CamelTool.class);

    private final String routesPath;

    public CamelTool(String routesPath) {
        this.routesPath = routesPath;
    }

    @Override
    public void invokeTool(ToolInvokeRequest request, StreamObserver<ToolInvokeReply> responseObserver) {

        LOG.debug("About to load a Camel route");

        final String yamlFile = request.getArgumentsMap().get("yaml-file");

        final Path routeFile = Paths.get(routesPath, yamlFile).toAbsolutePath();

        CamelContext context = new DefaultCamelContext();
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader();

        String routeFileUrl = String.format("file://%s", routeFile);
        routesLoader.loadRoute(context, routeFileUrl);

        try {
            context.start();

            final ProducerTemplate producerTemplate = context.createProducerTemplate();
            final Object o = producerTemplate.requestBody("direct:start", request.getBody());

            responseObserver.onNext(
                    ToolInvokeReply.newBuilder()
                            .setIsError(false)
                            .addAllContent(List.of(o.toString())).build());

            responseObserver.onCompleted();
        } finally {
            context.stop();
        }

    }
}
