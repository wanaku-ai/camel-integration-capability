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

package ai.wanaku.tool.camel.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@JsonDeserialize(using = McpEntityWrapper.McpEntityDeserializer.class)
public class McpEntityWrapper {
    private Map<String, Definition> tools = new HashMap<>();

    public McpEntityWrapper() {}

    public Map<String, Definition> getDefinitions() {
        return tools;
    }

    public void setTools(Map<String, Definition> tools) {
        this.tools = tools;
    }

    public static class McpEntityDeserializer extends JsonDeserializer<McpEntityWrapper> {
        @Override
        public McpEntityWrapper deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            McpEntityWrapper wrapper = new McpEntityWrapper();
            ObjectMapper mapper = (ObjectMapper) p.getCodec();

            if (p.currentToken() == JsonToken.START_ARRAY) {
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    if (p.currentToken() == JsonToken.START_OBJECT) {
                        p.nextToken(); // Move to field name
                        String toolName = p.getCurrentName();
                        p.nextToken(); // Move to tool definition object
                        Definition toolDef = mapper.readValue(p, Definition.class);
                        wrapper.tools.put(toolName, toolDef);
                        p.nextToken(); // Move past the tool definition object
                    }
                }
            }

            return wrapper;
        }
    }
}
