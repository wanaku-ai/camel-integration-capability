package ai.wanaku.capability.camel;

import ai.wanaku.capability.camel.util.WanakuRoutesLoader;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

public class WanakuCamelManager {
    private final CamelContext context;
    private final String routesPath;
    private final String dependenciesList;

    public WanakuCamelManager(String routesPath, String dependenciesList) {
        this.routesPath = routesPath;
        this.dependenciesList = dependenciesList;
        context = new DefaultCamelContext();

        loadRoutes();
    }

    private void loadRoutes() {
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader(dependenciesList);

        String routeFileUrl = String.format("file://%s", routesPath);
        routesLoader.loadRoute(context, routeFileUrl);
        context.start();
    }

    public CamelContext getCamelContext() {
        return context;
    }
}
