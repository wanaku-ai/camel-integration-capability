/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.capability.camel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelHealthEndpointsIT {

    private static WanakuCamelManager camelManager;
    private static int healthPort;
    private static HttpClient httpClient;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUp() throws Exception {
        Path routesFile = Path.of("src", "test", "resources", "test-routes.camel.yaml");
        Path dependenciesFile = Path.of("src", "test", "resources", "test-routes-dependencies.txt");

        Map<ResourceType, Path> downloadedResources = Map.of(
                ResourceType.ROUTES_REF, routesFile,
                ResourceType.DEPENDENCY_REF, dependenciesFile);

        healthPort = findFreePort();
        camelManager = new WanakuCamelManager(
                downloadedResources, null, null, 0, healthPort, WanakuCamelManager.RouteLoadingFailurePolicy.FAIL_FAST);
        camelManager.start();

        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void tearDown() {
        if (camelManager != null) {
            camelManager.stop();
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + healthPort + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/observe/health");

        assertEquals(200, response.statusCode(), "/observe/health should return 200 when the context is started");

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("UP", body.get("status").asText(), "Overall status should be UP");
        assertTrue(body.get("checks").isArray(), "Response should contain a checks array");
        assertTrue(body.get("checks").size() > 0, "At least one health check should be registered");
    }

    @Test
    void livenessEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/observe/health/live");

        assertEquals(200, response.statusCode(), "/observe/health/live should return 200 when the context is started");
        assertEquals("UP", MAPPER.readTree(response.body()).get("status").asText(), "Liveness status should be UP");
    }

    @Test
    void readinessEndpointReportsUpWithRouteChecks() throws Exception {
        HttpResponse<String> response = get("/observe/health/ready");

        assertEquals(200, response.statusCode(), "/observe/health/ready should return 200 when routes are started");

        JsonNode body = MAPPER.readTree(response.body());
        assertEquals("UP", body.get("status").asText(), "Readiness status should be UP");

        boolean hasRouteCheck = false;
        for (JsonNode check : body.get("checks")) {
            if (check.get("name").asText().startsWith("route:")) {
                hasRouteCheck = true;
                assertEquals("UP", check.get("status").asText(), "Route checks should be UP");
            }
        }
        assertTrue(hasRouteCheck, "Readiness should include route-level checks, got: " + response.body());
    }

    @Test
    void contextCheckIsReported() throws Exception {
        HttpResponse<String> response = get("/observe/health");

        JsonNode body = MAPPER.readTree(response.body());
        boolean hasContextCheck = false;
        for (JsonNode check : body.get("checks")) {
            if ("context".equals(check.get("name").asText())) {
                hasContextCheck = true;
                assertEquals("UP", check.get("status").asText(), "Context check should be UP");
            }
        }
        assertTrue(hasContextCheck, "Health should include the context check, got: " + response.body());
    }
}
