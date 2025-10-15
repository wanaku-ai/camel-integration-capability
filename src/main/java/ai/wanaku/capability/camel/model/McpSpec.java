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

package ai.wanaku.capability.camel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McpSpec {
    @JsonProperty("mcp")
    private McpContent mcp;

    public McpSpec() {}

    public McpContent getMcp() {
        return mcp;
    }

    public void setMcp(McpContent mcp) {
        this.mcp = mcp;
    }

    public static class McpContent {
        @JsonProperty("tools")
        private McpEntityWrapper tools;

        @JsonProperty("resources")
        private McpEntityWrapper resources;

        public McpContent() {}

        public McpEntityWrapper getTools() {
            return tools;
        }

        public void setTools(McpEntityWrapper tools) {
            this.tools = tools;
        }

        public McpEntityWrapper getResources() {
            return resources;
        }

        public void setResources(McpEntityWrapper resources) {
            this.resources = resources;
        }
    }
}
