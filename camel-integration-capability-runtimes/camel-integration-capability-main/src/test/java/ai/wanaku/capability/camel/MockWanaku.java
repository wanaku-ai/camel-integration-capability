package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A minimal mock of the Wanaku {@code /api/v1/forwards} management API.
 *
 * <p>Like Wanaku, a {@code POST} for an existing forward name returns 409 unless it has {@code replace=true}, and a
 * {@code DELETE} with {@code label.<key>=<value>} parameters returns 409 when a label does not match.
 */
class MockWanaku implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    final List<String> requests = new CopyOnWriteArrayList<>();
    final List<String> authorizations = new CopyOnWriteArrayList<>();
    final Map<String, ObjectNode> forwards = new ConcurrentHashMap<>();
    volatile JsonNode lastPostBody;
    volatile int postStatus = 200;
    volatile boolean available = true;
    volatile int toolsDiscovered = 1;

    private final HttpServer server;

    MockWanaku() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/forwards", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String name = path.substring("/api/v1/forwards".length()).replaceFirst("^/", "");
        requests.add(method + " " + path + (query == null ? "" : "?" + query));
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null) {
            authorizations.add(auth);
        }

        switch (method) {
            case "POST" -> {
                JsonNode body = MAPPER.readTree(exchange.getRequestBody());
                lastPostBody = body;
                if (postStatus != 200) {
                    respond(exchange, postStatus, "{\"error\":\"failure\"}");
                    return;
                }
                if (forwards.containsKey(body.path("name").asText()) && !"replace=true".equals(query)) {
                    respond(exchange, 409, "{\"data\":null,\"error\":\"forward already exists\"}");
                    return;
                }
                ObjectNode forward = ((ObjectNode) body.deepCopy()).put("available", available);
                forwards.put(body.path("name").asText(), forward);
                ObjectNode data = MAPPER.createObjectNode();
                data.set("forward", forward);
                data.put("tools_discovered", toolsDiscovered);
                respond(exchange, 200, wrap(data));
            }
            case "GET" -> {
                ObjectNode forward = forwards.get(name);
                if (forward == null) {
                    respond(exchange, 404, "{\"error\":\"forward not found\"}");
                } else {
                    respond(exchange, 200, wrap(forward));
                }
            }
            case "DELETE" -> {
                ObjectNode forward = forwards.get(name);
                if (forward == null) {
                    respond(exchange, 404, "{\"error\":\"forward not found\"}");
                } else if (!labelsMatch(forward, query)) {
                    respond(exchange, 409, "{\"error\":\"label precondition failed\"}");
                } else {
                    forwards.remove(name);
                    respond(exchange, 200, "{}");
                }
            }
            default -> respond(exchange, 405, "{}");
        }
    }

    /** Stores a forward owned by another instance. */
    void putForeignForward(String name, String instance) {
        ObjectNode forward = MAPPER.createObjectNode().put("name", name).put("address", "http://other:9090/mcp");
        forward.putObject("labels").put(WanakuForwardRegistration.INSTANCE_LABEL, instance);
        forwards.put(name, forward);
    }

    private static boolean labelsMatch(JsonNode forward, String query) {
        if (query == null) {
            return true;
        }
        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=", 2);
            if (!pair[0].startsWith("label.")) {
                continue;
            }
            String key = URLDecoder.decode(pair[0].substring("label.".length()), StandardCharsets.UTF_8);
            String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            if (!value.equals(forward.path("labels").path(key).asText(null))) {
                return false;
            }
        }
        return true;
    }

    private static String wrap(JsonNode data) {
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.set("data", data);
        wrapper.putNull("error");
        return wrapper.toString();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
