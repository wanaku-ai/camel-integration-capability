package ai.wanaku.capability.camel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Definition {
    @JsonProperty("route")
    private Route route;

    @JsonProperty("description")
    private String description;

    @JsonProperty("properties")
    private List<Property> properties;

    public Definition() {}

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }
}