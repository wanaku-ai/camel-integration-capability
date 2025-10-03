package ai.wanaku.tool.camel;

import ai.wanaku.tool.camel.util.WanakuRoutesLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

public class WanakuCamelManager {
    private final CamelContext context;
    private final String routesPath;

    public WanakuCamelManager(String routesPath) {
        this.routesPath = routesPath;
        context = new DefaultCamelContext();

        loadRoutes();
    }

    private void loadRoutes() {
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader();

        String routeFileUrl = String.format("file://%s", routesPath);
        routesLoader.loadRoute(context, routeFileUrl);
        context.start();
    }

    public CamelContext getCamelContext() {
        return context;
    }
}
