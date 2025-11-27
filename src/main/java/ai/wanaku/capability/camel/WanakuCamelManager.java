package ai.wanaku.capability.camel;

import ai.wanaku.capability.camel.downloader.ResourceType;
import ai.wanaku.capability.camel.util.WanakuRoutesLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

public class WanakuCamelManager {
    private final CamelContext context;
    private final String routesPath;
    private final String dependenciesList;

    public WanakuCamelManager(Map<ResourceType, Path> downloadedResources) {
        context = new DefaultCamelContext();

        this.routesPath = downloadedResources.get(ResourceType.ROUTES_REF).toString();
        if (downloadedResources.containsKey(ResourceType.DEPENDENCY_REF)) {
            String dependenciesPath =
                    downloadedResources.get(ResourceType.DEPENDENCY_REF).toString();
            try {
                this.dependenciesList = Files.readString(Path.of(dependenciesPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            this.dependenciesList = null;
        }

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
