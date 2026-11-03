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

import java.nio.file.Path;
import java.util.Map;
import org.apache.camel.CamelContext;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the lenient-mode test in its own class. Each context in the JVM shares the Camel CLI connector lock
 * file (one per PID). When a second context stops, it deletes that file, and the CLI connector of a context
 * that still runs then stops it. Keep this context out of {@link WanakuCamelRouteLoaderIT}.
 */
class WanakuCamelRouteLoaderLenientIT {

    @Test
    void emptyRouteDefinitionsCanContinueInLenientMode() throws Exception {
        Path routesFile = Path.of("src", "test", "resources", "empty-routes.camel.yaml");
        Path dependenciesFile = Path.of("src", "test", "resources", "test-routes-dependencies.txt");

        Map<ResourceType, Path> downloadedResources = Map.of(
                ResourceType.ROUTES_REF, routesFile,
                ResourceType.DEPENDENCY_REF, dependenciesFile);

        WanakuCamelManager lenientCamelManager = new WanakuCamelManager(
                downloadedResources, null, null, 0, WanakuCamelManager.RouteLoadingFailurePolicy.LOG_AND_CONTINUE);

        try {
            lenientCamelManager.start();
            CamelContext context = lenientCamelManager.getCamelContext();
            assertNotNull(context, "CamelContext should still be created in lenient mode");
            assertTrue(context.isStarted(), "CamelContext should start in lenient mode");
            assertEquals(0, context.getRoutes().size(), "No routes should be loaded from an empty file");
        } finally {
            lenientCamelManager.stop();
        }
    }
}
