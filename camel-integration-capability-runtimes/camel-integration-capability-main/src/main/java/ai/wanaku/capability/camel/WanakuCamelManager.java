package ai.wanaku.capability.camel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.capabilities.sdk.maven.GAV;
import ai.wanaku.capabilities.sdk.maven.WanakuMavenDownloader;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import ai.wanaku.capabilities.sdk.runtime.camel.exceptions.RouteLoadingException;
import ai.wanaku.capabilities.sdk.runtime.camel.util.WanakuRoutesLoader;

public class WanakuCamelManager {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuCamelManager.class);

    public enum RouteLoadingFailurePolicy {
        FAIL_FAST,
        LOG_AND_CONTINUE
    }

    private final CamelContext context;
    private final String routesPath;
    private final RouteLoadingFailurePolicy routeLoadingFailurePolicy;
    private List<GAV> gavs = new ArrayList<>();

    public WanakuCamelManager(Map<ResourceType, Path> downloadedResources, String repositoriesList) {
        this(downloadedResources, repositoriesList, RouteLoadingFailurePolicy.FAIL_FAST);
    }

    public WanakuCamelManager(
            Map<ResourceType, Path> downloadedResources,
            String repositoriesList,
            RouteLoadingFailurePolicy routeLoadingFailurePolicy) {
        this.routeLoadingFailurePolicy =
                Objects.requireNonNull(routeLoadingFailurePolicy, "RouteLoadingFailurePolicy must not be null");

        this.routesPath = downloadedResources.get(ResourceType.ROUTES_REF).toString();
        if (downloadedResources.containsKey(ResourceType.DEPENDENCY_REF)) {
            String dependenciesPath =
                    downloadedResources.get(ResourceType.DEPENDENCY_REF).toString();
            try {
                final List<String> depLines = Files.readAllLines(Path.of(dependenciesPath));
                this.gavs = depLines.stream()
                        .filter(l -> !l.startsWith("#"))
                        .map(GAV::parse)
                        .collect(Collectors.toList());

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        WanakuMavenDownloader mavenDownloader = new WanakuMavenDownloader(WanakuCamelManager.class.getClassLoader());
        mavenDownloader.download(gavs);

        context = new DefaultCamelContext();
        context.setApplicationContextClassLoader(mavenDownloader.getClassLoader());
        loadRoutes();
    }

    private void loadRoutes() {
        WanakuRoutesLoader routesLoader = new WanakuRoutesLoader();

        String routeFileUrl = Path.of(routesPath).toUri().toString();

        try {
            routesLoader.loadRoute(context, routeFileUrl);
        } catch (RouteLoadingException e) {
            if (routeLoadingFailurePolicy == RouteLoadingFailurePolicy.FAIL_FAST) {
                throw e;
            } else {
                LOG.warn(
                        "Failed to load routes, but continuing because route loading policy is LOG_AND_CONTINUE: {}",
                        e.getMessage());
            }
        }

        context.start();
    }

    public CamelContext getCamelContext() {
        return context;
    }
}
