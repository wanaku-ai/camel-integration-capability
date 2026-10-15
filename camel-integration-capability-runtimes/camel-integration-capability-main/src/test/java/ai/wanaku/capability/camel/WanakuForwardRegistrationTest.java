package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WanakuForwardRegistrationTest {
    private static final String NAME = "echo-tool";
    private static final String ADDRESS = "http://echo-tool.tools.svc:9090/mcp";

    private MockWanaku wanaku;

    @BeforeEach
    void startWanaku() throws IOException {
        wanaku = new MockWanaku();
    }

    @AfterEach
    void stopWanaku() {
        wanaku.close();
    }

    private WanakuForwardRegistration registration(String url, String token, int retries) {
        return new WanakuForwardRegistration(
                new WanakuForwardRegistration.Settings(
                        url, NAME, "tools", ADDRESS, token, retries, Duration.ofMillis(10)),
                Map.of("cic.version", "1.0", "cic.routes-digest", "abc"));
    }

    @Test
    void registersForwardWithNameAddressNamespaceAndLabels() throws Exception {
        WanakuForwardRegistration registration = registration(wanaku.url(), "secret", 0);

        registration.register();

        assertTrue(registration.isRegistered());
        assertEquals(List.of("POST /api/v1/forwards"), wanaku.requests);
        assertEquals(List.of("Bearer secret"), wanaku.authorizations);
        JsonNode body = wanaku.lastPostBody;
        assertEquals(NAME, body.path("name").asText());
        assertEquals(ADDRESS, body.path("address").asText());
        assertEquals("tools", body.path("namespace").asText());
        JsonNode labels = body.path("labels");
        assertEquals(registration.instance(), labels.path("cic.instance").asText());
        assertEquals("1.0", labels.path("cic.version").asText());
        assertEquals("abc", labels.path("cic.routes-digest").asText());
    }

    @Test
    void unavailableForwardFailsAndOwnedEntryCanBeRemoved() throws Exception {
        wanaku.available = false;
        WanakuForwardRegistration registration = registration(wanaku.url(), null, 3);

        assertThrows(IOException.class, registration::register);
        // The failure is final: no retry
        assertEquals(List.of("POST /api/v1/forwards"), wanaku.requests);

        registration.deregister();

        assertFalse(wanaku.forwards.containsKey(NAME));
        assertFalse(registration.isRegistered());
    }

    @Test
    void noDiscoveredToolsFails() {
        wanaku.toolsDiscovered = 0;

        assertThrows(IOException.class, registration(wanaku.url(), null, 0)::register);
    }

    @Test
    void serverErrorsAreRetriedThenFail() {
        wanaku.postStatus = 503;

        assertThrows(IOException.class, registration(wanaku.url(), null, 2)::register);

        assertEquals(3, wanaku.requests.size());
    }

    @Test
    void clientErrorsAreNotRetried() {
        wanaku.postStatus = 400;

        assertThrows(IOException.class, registration(wanaku.url(), null, 2)::register);

        assertEquals(1, wanaku.requests.size());
    }

    @Test
    void connectionRefusedIsRetriedThenFails() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        WanakuForwardRegistration registration = registration("http://127.0.0.1:" + unusedPort, null, 1);

        IOException error = assertThrows(IOException.class, registration::register);

        assertTrue(error.getMessage().startsWith("Cannot reach Wanaku"), error.getMessage());
    }

    @Test
    void shutdownDeletesOwnedForward() throws Exception {
        WanakuForwardRegistration registration = registration(wanaku.url(), null, 0);
        registration.register();

        registration.deregister();

        assertEquals(
                List.of("POST /api/v1/forwards", "GET /api/v1/forwards/" + NAME, "DELETE /api/v1/forwards/" + NAME),
                wanaku.requests);
        assertFalse(wanaku.forwards.containsKey(NAME));
    }

    @Test
    void shutdownKeepsForwardOfReplacementInstance() throws Exception {
        WanakuForwardRegistration registration = registration(wanaku.url(), null, 0);
        registration.register();
        wanaku.putForeignForward(NAME, "newer-instance");

        registration.deregister();

        assertEquals(List.of("POST /api/v1/forwards", "GET /api/v1/forwards/" + NAME), wanaku.requests);
        assertTrue(wanaku.forwards.containsKey(NAME));
    }

    @Test
    void shutdownToleratesMissingForward() throws Exception {
        WanakuForwardRegistration registration = registration(wanaku.url(), null, 0);
        registration.register();
        wanaku.forwards.clear();

        registration.deregister();

        assertEquals(List.of("POST /api/v1/forwards", "GET /api/v1/forwards/" + NAME), wanaku.requests);
        assertFalse(registration.isRegistered());
    }

    @Test
    void shutdownWithoutRegistrationMakesNoCalls() throws Exception {
        registration(wanaku.url(), null, 0).deregister();

        assertTrue(wanaku.requests.isEmpty());
    }
}
