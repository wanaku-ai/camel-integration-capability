package ai.wanaku.capability.camel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import ai.wanaku.capabilities.sdk.runtime.camel.util.WanakuRoutesLoader;

public class WanakuCamelManager {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuCamelManager.class);

    public enum RouteLoadingFailurePolicy {
        FAIL_FAST,
        LOG_AND_CONTINUE
    }

    private final CamelContext context;
    private final String routesPath;
    private final String dependenciesList;
    private final String repositoriesList;
    private final RouteLoadingFailurePolicy routeLoadingFailurePolicy;

    public WanakuCamelManager(Map<ResourceType, Path> downloadedResources, String repositoriesList) {
        this(downloadedResources, repositoriesList, RouteLoadingFailurePolicy.FAIL_FAST);
    }

    public WanakuCamelManager(
            Map<ResourceType, Path> downloadedResources,
            String repositoriesList,
            RouteLoadingFailurePolicy routeLoadingFailurePolicy) {
        this.repositoriesList = repositoriesList;
        this.routeLoadingFailurePolicy = Objects.requireNonNull(routeLoadingFailurePolicy);
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
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader(dependenciesList, repositoriesList);

        String routeFileUrl = String.format("file://%s", routesPath);
        int routesBeforeLoad = context.getRoutes().size();
        routesLoader.loadRoute(context, routeFileUrl);
        int loadedRoutes = context.getRoutes().size() - routesBeforeLoad;
        if (loadedRoutes <= 0) {
            String message = "No Camel routes were loaded from " + routeFileUrl;
            if (routeLoadingFailurePolicy == RouteLoadingFailurePolicy.FAIL_FAST) {
                throw new IllegalStateException(message);
            }

            LOG.warn("{} Continuing because route loading policy is LOG_AND_CONTINUE.", message);
        }
        context.start();
    }

    public CamelContext getCamelContext() {
        return context;
    }
}
