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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.wanaku.capability.camel.downloader.ResourceType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WanakuCamelRouteLoaderIT {

    private static WanakuCamelManager camelManager;

    @BeforeAll
    static void setUp() throws Exception {
        Path routesFile = Paths.get("src", "test", "resources", "test-routes.camel.yaml");
        Path dependenciesFile = Paths.get("src", "test", "resources", "test-routes-dependencies.txt");

        Map<ResourceType, Path> downloadedResources = Map.of(
                ResourceType.ROUTES_REF, routesFile,
                ResourceType.DEPENDENCY_REF, dependenciesFile);

        camelManager = new WanakuCamelManager(downloadedResources);
    }

    @AfterAll
    static void tearDown() {
        if (camelManager != null) {
            camelManager.getCamelContext().stop();
        }
    }

    @Test
    void camelContextIsNotNull() {
        CamelContext context = camelManager.getCamelContext();
        assertNotNull(context, "CamelContext should not be null");
    }

    @Test
    void camelContextIsStarted() {
        CamelContext context = camelManager.getCamelContext();
        assertTrue(context.isStarted(), "CamelContext should be started");
    }

    @Test
    void routeStarted() {
        CamelContext context = camelManager.getCamelContext();
        ServiceStatus serviceStatus = context.getRouteController().getRouteStatus("test-component-resolver");
        assertEquals(true, serviceStatus.isStarted(), "route test-component-resolver not started");
    }

    @Test
    void httpsAndHttpComponentIsAvailableThroughComponentResolver() {
        CamelContext context = camelManager.getCamelContext();
        assertNotNull(
                context.getComponent("https"),
                "HTTPS component should be available through DependencyDownloaderComponentResolver");
        assertNotNull(
                context.getComponent("http"),
                "HTTP component should be available through DependencyDownloaderComponentResolver");
    }

    @Test
    void gsonDataFormatIsAvailableThroughDataFormatResolver() {
        CamelContext context = camelManager.getCamelContext();
        assertNotNull(
                context.resolveDataFormat("gson"),
                "JSON data format should be available through DependencyDownloaderDataFormatResolver");
    }

    @Test
    void jsonPathLanguageIsAvailableThroughLanguageResolver() {
        CamelContext context = camelManager.getCamelContext();
        assertNotNull(
                context.resolveLanguage("jsonpath"),
                "JSONPath language should be available through DependencyDownloaderLanguageResolver");
    }

    @Test
    void jqTransformerIsAvailableThroughLanguageResolver() {
        CamelContext context = camelManager.getCamelContext();
        assertNotNull(
                context.resolveLanguage("jq"),
                "JQ language should be available through DependencyDownloaderLanguageResolver");
    }

    @Test
    void kafkaComponentIsAvailableThroughUriFactoryResolver() {
        CamelContext context = camelManager.getCamelContext();
        assertNotNull(
                context.getComponent("kafka"),
                "Kafka component should be available through DependencyDownloaderComponentResolver");
    }

    @Test
    void allRoutesAreLoaded() {
        CamelContext context = camelManager.getCamelContext();
        assertEquals(6, context.getRoutes().size(), "Should have 6 routes loaded");
    }

    @Test
    void groovyRouteUsesExternalDependency() {
        CamelContext context = camelManager.getCamelContext();
        ProducerTemplate template = context.createProducerTemplate();

        String result = template.requestBody("direct:test-groovy", "test-input", String.class);

        assertNotNull(result, "Result should not be null");

        // The rgxgen library should generate a string matching the pattern: [a-z]{5}\d{3}
        // That's 5 lowercase letters followed by 3 digits (e.g., "hello123")
        Pattern expectedPattern = Pattern.compile("^[a-z]{5}\\d{3}$");
        assertTrue(
                expectedPattern.matcher(result).matches(),
                "Result should match pattern [a-z]{5}\\d{3}, but was: " + result);
    }
}
