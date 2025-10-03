package ai.wanaku.tool.camel.util;

import ai.wanaku.tool.camel.model.Definition;
import ai.wanaku.tool.camel.model.Mapping;
import ai.wanaku.tool.camel.model.Property;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(McpUtil.class);

    private McpUtil() {}

    public static Map<String, Object> convertMcpMapToCamelHeaders(Definition toolDefinition, Map<String, String> argumentsMap) {
        final List<Property> properties = toolDefinition.getProperties();
        Map<String, Object> headers = new HashMap<>();

        for  (Property property : properties) {
            final Mapping mapping = property.getMapping();
            if (mapping != null) {
                final String mappingType = mapping.getType();

                if (mappingType.equals("header")) {
                    LOG.info("Adding header named {} with value {}", mapping.getName(), argumentsMap.get(property.getName()));
                    headers.put(mapping.getName(), argumentsMap.get(property.getName()));
                }
            }

        }
        return headers;
    }
}
