package ai.wanaku.capability.camel.health;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CamelHealthServerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CamelContext camelContext;
    private CamelHealthServer healthServer;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();

        port = 19876;
        healthServer = new CamelHealthServer(camelContext, port);
        healthServer.start();
    }

    @AfterEach
    void tearDown() {
        healthServer.stop();
        camelContext.stop();
    }

    private HttpResponse<String> fetch(String path) throws IOException, InterruptedException {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> fetchHealthJson(String path) throws Exception {
        HttpResponse<String> response = fetch(path);
        Map<String, Object> result = MAPPER.readValue(response.body(), Map.class);
        result.put("_statusCode", response.statusCode());
        result.put("_contentType", response.headers().firstValue("Content-Type").orElse(""));
        return result;
    }

    @Test
    void testHealthEndpointReturnsUp() throws Exception {
        Map<String, Object> result = fetchHealthJson("/health");
        assertEquals(200, result.get("_statusCode"));
        assertEquals("UP", result.get("status"));
    }

    @Test
    void testLivenessEndpoint() throws Exception {
        Map<String, Object> result = fetchHealthJson("/health/live");
        assertEquals(200, result.get("_statusCode"));
        assertEquals("UP", result.get("status"));
    }

    @Test
    void testReadinessEndpoint() throws Exception {
        Map<String, Object> result = fetchHealthJson("/health/ready");
        assertEquals(200, result.get("_statusCode"));
        assertEquals("UP", result.get("status"));
    }

    @Test
    void testHealthEndpointReturnsDownWhenStopped() throws Exception {
        camelContext.stop();

        Map<String, Object> result = fetchHealthJson("/health");
        assertEquals(503, result.get("_statusCode"));
        assertEquals("DOWN", result.get("status"));
    }

    @Test
    void testResponseContentType() throws Exception {
        Map<String, Object> result = fetchHealthJson("/health");
        assertEquals("application/json", result.get("_contentType"));
    }
}
