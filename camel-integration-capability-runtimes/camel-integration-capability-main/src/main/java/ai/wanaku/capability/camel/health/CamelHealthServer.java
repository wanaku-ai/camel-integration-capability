package ai.wanaku.capability.camel.health;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class CamelHealthServer {
    private static final Logger LOG = LoggerFactory.getLogger(CamelHealthServer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final CamelContext camelContext;
    private final int port;
    private HttpServer server;

    public CamelHealthServer(CamelContext camelContext, int port) {
        this.camelContext = camelContext;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleAll);
        server.createContext("/health/live", this::handleLiveness);
        server.createContext("/health/ready", this::handleReadiness);
        server.start();
        LOG.info("Camel Health endpoint started on port {}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOG.info("Camel Health endpoint stopped");
        }
    }

    private void handleAll(HttpExchange exchange) throws IOException {
        Collection<HealthCheck.Result> results = HealthCheckHelper.invoke(camelContext);
        sendResponse(exchange, results);
    }

    private void handleLiveness(HttpExchange exchange) throws IOException {
        Collection<HealthCheck.Result> results = HealthCheckHelper.invokeLiveness(camelContext);
        sendResponse(exchange, results);
    }

    private void handleReadiness(HttpExchange exchange) throws IOException {
        Collection<HealthCheck.Result> results = HealthCheckHelper.invokeReadiness(camelContext);
        sendResponse(exchange, results);
    }

    private void sendResponse(HttpExchange exchange, Collection<HealthCheck.Result> results) throws IOException {
        boolean contextRunning = camelContext.getStatus() == ServiceStatus.Started;
        boolean checksUp = results.stream().allMatch(r -> r.getState() == HealthCheck.State.UP);
        boolean isUp = contextRunning && checksUp;

        String json = toJson(results, isUp);
        int statusCode = isUp ? 200 : 503;

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    static String toJson(Collection<HealthCheck.Result> results, boolean isUp) throws IOException {
        List<Map<String, String>> checks = results.stream()
                .map(result -> {
                    Map<String, String> check = new LinkedHashMap<>();
                    check.put("name", result.getCheck().getId());
                    check.put("status", result.getState().name());
                    result.getMessage().ifPresent(msg -> check.put("message", msg));
                    result.getError().ifPresent(err -> check.put("error", err.getMessage()));
                    return check;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", isUp ? "UP" : "DOWN");
        response.put("checks", checks);

        return MAPPER.writeValueAsString(response);
    }
}
