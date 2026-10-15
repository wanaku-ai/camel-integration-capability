package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Checks the local MCP server before registration: runs the MCP {@code initialize} handshake and {@code tools/list}
 * over the streamable HTTP transport and requires at least one tool.
 */
final class McpSelfCheck {
    private static final Logger LOG = LoggerFactory.getLogger(McpSelfCheck.class);
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final URI endpoint;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private String sessionId;
    private String protocolVersion = PROTOCOL_VERSION;

    private McpSelfCheck(URI endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Verifies the MCP server at the given endpoint.
     *
     * @return the number of tools that the server exposes
     * @throws IOException if the server does not answer, fails the handshake, or exposes no tools
     */
    static int verify(URI endpoint) throws IOException, InterruptedException {
        McpSelfCheck check = new McpSelfCheck(endpoint);
        try {
            JsonNode init = check.call(
                    1,
                    "initialize",
                    Map.of(
                            "protocolVersion", PROTOCOL_VERSION,
                            "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "cic-self-check", "version", "1")));
            check.protocolVersion = init.path("protocolVersion").asText(PROTOCOL_VERSION);
            check.notifyInitialized();
            int tools = check.call(2, "tools/list", Map.of()).path("tools").size();
            if (tools < 1) {
                throw new IOException("The MCP server at " + endpoint + " exposes no tools");
            }
            return tools;
        } finally {
            check.closeSession();
        }
    }

    private JsonNode call(int id, String method, Map<String, Object> params) throws IOException, InterruptedException {
        HttpResponse<String> response = post(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
        if (response.statusCode() != 200) {
            throw new IOException("MCP " + method + " failed with HTTP " + response.statusCode());
        }
        response.headers().firstValue(SESSION_HEADER).ifPresent(value -> sessionId = value);

        JsonNode message = readMessage(response);
        if (message.has("error")) {
            throw new IOException("MCP " + method + " failed: " + message.get("error"));
        }
        return message.path("result");
    }

    private void notifyInitialized() throws IOException, InterruptedException {
        HttpResponse<String> response = post(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MCP notifications/initialized failed with HTTP " + response.statusCode());
        }
    }

    private HttpResponse<String> post(Map<String, Object> message) throws IOException, InterruptedException {
        HttpRequest.Builder builder = request()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(message)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Reads a JSON-RPC message from a JSON body or from the first SSE {@code data:} event. */
    private static JsonNode readMessage(HttpResponse<String> response) throws IOException {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.startsWith("text/event-stream")) {
            return MAPPER.readTree(response.body());
        }
        for (String line : response.body().split("\\R")) {
            if (line.startsWith("data:")) {
                return MAPPER.readTree(line.substring("data:".length()).trim());
            }
        }
        throw new IOException("MCP response stream contains no data event");
    }

    private void closeSession() {
        if (sessionId == null) {
            return;
        }
        try {
            client.send(request().DELETE().build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // Best effort. The server expires idle sessions.
            LOG.debug("Cannot close the MCP self-check session: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpRequest.Builder request() {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(endpoint).timeout(TIMEOUT).header("MCP-Protocol-Version", protocolVersion);
        if (sessionId != null) {
            builder.header(SESSION_HEADER, sessionId);
        }
        return builder;
    }
}
