package io.dscope.camel.mcp.catalog;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an MCP tool definition loaded from configuration.
 */
public class McpMethodDefinition {

    private String name;
    private String title;
    private String description;
    private Map<String, Object> annotations = Map.of();
    private Map<String, Object> inputSchema = Map.of();
    private Map<String, Object> outputSchema = Map.of();
    private List<String> requiredArguments = List.of();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        if (title == null || title.isBlank()) {
            return name;
        }
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(Map<String, Object> annotations) {
        this.annotations = annotations == null ? Map.of() : annotations;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema == null ? Map.of() : inputSchema;
    }

    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }

    public void setOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema == null ? Map.of() : outputSchema;
    }

    public List<String> getRequiredArguments() {
        if (requiredArguments != null && !requiredArguments.isEmpty()) {
            return requiredArguments;
        }
        if (inputSchema == null) {
            return List.of();
        }
        Object required = inputSchema.get("required");
        if (required instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    public void setRequiredArguments(List<String> requiredArguments) {
        if (requiredArguments == null || requiredArguments.isEmpty()) {
            this.requiredArguments = List.of();
        } else {
            this.requiredArguments = List.copyOf(requiredArguments.stream().filter(Objects::nonNull).map(Object::toString).toList());
        }
    }

    public Map<String, Object> toToolEntry() {
        return Map.of(
                "name", getName(),
                "title", Objects.requireNonNullElse(getTitle(), getName()),
                "description", Objects.requireNonNullElse(getDescription(), ""),
                "inputSchema", Collections.unmodifiableMap(getInputSchema()),
                "outputSchema", Collections.unmodifiableMap(getOutputSchema()),
                "annotations", Collections.unmodifiableMap(getAnnotations()));
    }
}
