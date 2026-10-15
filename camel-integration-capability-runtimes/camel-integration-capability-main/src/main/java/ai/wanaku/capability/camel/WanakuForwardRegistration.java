package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registers this process as an MCP forward in the Wanaku management API and removes the forward on shutdown.
 *
 * <p>Every process uses a random {@code cic.instance} label. On shutdown, the forward is deleted only when the
 * label still matches, so a replaced instance does not remove the registration of a newer one.
 */
public class WanakuForwardRegistration {
    private static final Logger LOG = LoggerFactory.getLogger(WanakuForwardRegistration.class);

    static final String INSTANCE_LABEL = "cic.instance";
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Registration settings.
     *
     * @param wanakuUrl the Wanaku management API base URL
     * @param name the forward name
     * @param namespace the Wanaku namespace
     * @param advertisedAddress the MCP address that Wanaku uses to reach this process
     * @param token the bearer token, or {@code null} when no authentication is used
     * @param retries the maximum number of retries on connection errors and 5xx responses
     * @param retryWait the wait time between attempts
     */
    public record Settings(
            String wanakuUrl,
            String name,
            String namespace,
            String advertisedAddress,
            String token,
            int retries,
            Duration retryWait) {}

    private final Settings settings;
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final String instance = UUID.randomUUID().toString();
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private volatile boolean registered;

    public WanakuForwardRegistration(Settings settings, Map<String, String> labels) {
        this.settings = settings;
        this.labels.putAll(labels);
        this.labels.put(INSTANCE_LABEL, instance);
    }

    public String instance() {
        return instance;
    }

    public boolean isRegistered() {
        return registered;
    }

    /**
     * Registers the forward. Connection errors and 5xx responses are retried. Any other failure is final.
     *
     * @throws IOException if the forward cannot be registered or Wanaku does not discover a usable forward
     */
    public void register() throws IOException, InterruptedException {
        byte[] body = MAPPER.writeValueAsBytes(Map.of(
                "name", settings.name(),
                "address", settings.advertisedAddress(),
                "namespace", settings.namespace(),
                "labels", labels));
        HttpRequest request = request("")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        int maxAttempts = 1 + Math.max(0, settings.retries());
        int attempt = 1;
        while (true) {
            LOG.info(
                    "Registering forward '{}' ({}) with Wanaku at {} (attempt {}/{})",
                    settings.name(),
                    settings.advertisedAddress(),
                    settings.wanakuUrl(),
                    attempt,
                    maxAttempts);
            // An accepted POST can lose its response. Claim the registration before any network operation.
            registered = true;
            String retryableFailure = send(request);
            if (retryableFailure == null) {
                LOG.info("Forward '{}' registered with Wanaku (instance {})", settings.name(), instance);
                return;
            }
            if (attempt >= maxAttempts) {
                throw new IOException(retryableFailure);
            }
            LOG.warn(
                    "{}. Retrying in {} ms",
                    retryableFailure,
                    settings.retryWait().toMillis());
            Thread.sleep(settings.retryWait().toMillis());
            attempt++;
        }
    }

    /**
     * Sends one registration request.
     *
     * @return {@code null} on success, or the reason of a failure that can be retried
     * @throws IOException on a failure that cannot be retried
     */
    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return "Cannot reach Wanaku: " + e.getMessage();
        }
        if (response.statusCode() >= 500) {
            return "Wanaku returned HTTP " + response.statusCode();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Wanaku rejected the forward with HTTP " + response.statusCode() + ": " + response.body());
        }
        checkDiscovery(response.body());
        return null;
    }

    private static void checkDiscovery(String body) throws IOException {
        JsonNode data = unwrap(MAPPER.readTree(body));
        JsonNode forward = data.path("forward");
        if (!forward.path("available").asBoolean(false)) {
            throw new IOException("Wanaku could not discover the forward: "
                    + forward.path("statusMessage").asText("no status message"));
        }
        if (data.path("tools_discovered").asInt(0) < 1) {
            throw new IOException("Wanaku discovered no tools on the forward");
        }
    }

    /**
     * Deletes the forward if this process still owns it. A missing forward is not an error.
     *
     * @throws IOException if the ownership check or the delete fails
     */
    public void deregister() throws IOException, InterruptedException {
        if (!registered) {
            return;
        }
        String path = "/" + settings.name();
        HttpResponse<String> current = client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (current.statusCode() == 404) {
            LOG.info("Forward '{}' is not registered in Wanaku. Nothing to remove", settings.name());
            registered = false;
            return;
        }
        if (current.statusCode() != 200) {
            throw new IOException("Cannot verify forward ownership: Wanaku returned HTTP " + current.statusCode());
        }
        String owner = unwrap(MAPPER.readTree(current.body()))
                .path("labels")
                .path(INSTANCE_LABEL)
                .asText();
        if (!instance.equals(owner)) {
            LOG.info("Forward '{}' is owned by another instance ({}). Leaving it registered", settings.name(), owner);
            registered = false;
            return;
        }
        HttpResponse<String> deleted =
                client.send(request(path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        if (deleted.statusCode() != 404 && (deleted.statusCode() < 200 || deleted.statusCode() >= 300)) {
            throw new IOException("Cannot remove forward: Wanaku returned HTTP " + deleted.statusCode());
        }
        LOG.info("Forward '{}' removed from Wanaku", settings.name());
        registered = false;
    }

    private HttpRequest.Builder request(String suffix) {
        URI uri = URI.create(settings.wanakuUrl().replaceAll("/+$", "") + "/api/v1/forwards" + suffix);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(HTTP_TIMEOUT);
        if (settings.token() != null) {
            builder.header("Authorization", "Bearer " + settings.token());
        }
        return builder;
    }

    private static JsonNode unwrap(JsonNode node) {
        return node.has("data") ? node.get("data") : node;
    }
}
