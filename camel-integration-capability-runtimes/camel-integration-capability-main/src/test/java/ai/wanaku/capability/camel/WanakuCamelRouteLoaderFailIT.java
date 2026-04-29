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
import java.nio.file.Paths;
import java.util.Map;
import ai.wanaku.capabilities.sdk.runtime.camel.downloader.ResourceType;
import ai.wanaku.capabilities.sdk.runtime.camel.exceptions.RouteLoadingException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WanakuCamelRouteLoaderFailIT {

    @Test
    void throwsErrorOnFailFast() {
        Path routesFile = Paths.get("src", "test", "resources", "test-routes-with-errors.camel.yaml");
        Path dependenciesFile = Paths.get("src", "test", "resources", "test-routes-dependencies.txt");

        Map<ResourceType, Path> downloadedResources = Map.of(
                ResourceType.ROUTES_REF, routesFile,
                ResourceType.DEPENDENCY_REF, dependenciesFile);

        assertThrows(RouteLoadingException.class, () -> new WanakuCamelManager(downloadedResources, null));
    }

    @Test
    void doesNotThrowErrorOnFailFast() {
        Path routesFile = Paths.get("src", "test", "resources", "test-routes-with-errors.camel.yaml");
        Path dependenciesFile = Paths.get("src", "test", "resources", "test-routes-dependencies.txt");

        Map<ResourceType, Path> downloadedResources = Map.of(
                ResourceType.ROUTES_REF, routesFile,
                ResourceType.DEPENDENCY_REF, dependenciesFile);

        assertDoesNotThrow(() -> new WanakuCamelManager(
                downloadedResources, null, WanakuCamelManager.RouteLoadingFailurePolicy.LOG_AND_CONTINUE));
    }
}
