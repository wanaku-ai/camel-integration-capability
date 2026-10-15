package ai.wanaku.capability.camel;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WanakuSelfRegistrationIT {
    private static final Path ROUTES = Path.of("src", "test", "resources", "mcp-tool-routes.camel.yaml");

    private final HttpClient client = HttpClient.newHttpClient();
    private MockWanaku wanaku;
    private WanakuCamelManager camelManager;
    private int mcpPort;

    @BeforeEach
    void setUp() throws Exception {
        wanaku = new MockWanaku();
        try (ServerSocket socket = new ServerSocket(0)) {
            mcpPort = socket.getLocalPort();
        }
        camelManager = new WanakuCamelManager(
                Map.of(ResourceType.ROUTES_REF, ROUTES),
                null,
                "wanaku",
                mcpPort,
                WanakuCamelManager.RouteLoadingFailurePolicy.FAIL_FAST);
    }

    @AfterEach
    void tearDown() {
        camelManager.stop();
        wanaku.close();
    }

    private WanakuForwardRegistration registration() {
        return new WanakuForwardRegistration(
                new WanakuForwardRegistration.Settings(
                        wanaku.url(),
                        "echo-tool",
                        "default",
                        "http://127.0.0.1:" + mcpPort + "/mcp",
                        null,
                        0,
                        Duration.ofMillis(10)),
                Map.of());
    }

    private int readinessStatus() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + mcpPort + "/observe/health/ready"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void registersAfterMcpSelfCheckAndRemovesForwardOnShutdown() throws Exception {
        WanakuForwardRegistration registration = registration();
        assertEquals(503, readinessStatus(), "readiness must be DOWN before registration");

        assertTrue(camelManager.register(registration));

        assertTrue(camelManager.isReady());
        assertEquals(200, readinessStatus(), "readiness must be UP after registration");
        assertEquals(List.of("POST /api/v1/forwards"), wanaku.requests);

        camelManager.shutdown(registration);

        assertFalse(camelManager.isReady());
        assertEquals(
                List.of("POST /api/v1/forwards", "GET /api/v1/forwards/echo-tool", "DELETE /api/v1/forwards/echo-tool"),
                wanaku.requests);
    }

    @Test
    void failedRegistrationRemovesOwnedForwardAndStaysNotReady() throws Exception {
        wanaku.available = false;

        assertFalse(camelManager.register(registration()));

        assertFalse(camelManager.isReady());
        assertEquals(503, readinessStatus());
        assertFalse(wanaku.forwards.containsKey("echo-tool"));
    }

    @Test
    void disabledRegistrationMakesNoWanakuCalls() throws Exception {
        assertTrue(camelManager.register(null));

        assertTrue(camelManager.isReady());
        assertEquals(200, readinessStatus());

        camelManager.shutdown(null);

        assertTrue(wanaku.requests.isEmpty());
    }

    @Test
    void mcpSelfCheckFindsTheTool() throws Exception {
        assertEquals(1, McpSelfCheck.verify(URI.create("http://127.0.0.1:" + mcpPort + "/mcp")));
    }
}
