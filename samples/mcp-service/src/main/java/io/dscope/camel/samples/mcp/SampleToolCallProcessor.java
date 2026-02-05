package io.dscope.camel.samples.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

import io.dscope.camel.mcp.processor.AbstractMcpResponseProcessor;

/**
 * Sample processor that handles tools/call method.
 * 
 * <p>Supports two built-in demo tools:
 * <ul>
 *   <li><b>echo</b> - Returns the input text unchanged</li>
 *   <li><b>summarize</b> - Truncates text to a maximum number of words</li>
 * </ul>
 * 
 * <p>This demonstrates the recommended pattern: extend {@link AbstractMcpResponseProcessor}
 * and use {@link #writeResult(Exchange, Object)} to write JSON-RPC responses.
 */
@BindToRegistry("sampleToolCallProcessor")
public class SampleToolCallProcessor extends AbstractMcpResponseProcessor {

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        String toolName = getToolName(exchange);
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required for tools/call");
        }

        Map<String, Object> params = getRequestParameters(exchange);

        switch (toolName) {
            case "echo" -> writeResult(exchange, handleEcho(params));
            case "summarize", "summary" -> writeResult(exchange, handleSummarize(params));
            case "chart-editor" -> writeResult(exchange, handleChartEditor(params));
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
    }

    private Map<String, Object> handleEcho(Map<String, Object> arguments) {
        Object text = arguments == null ? "" : arguments.getOrDefault("text", "");
        Map<String, Object> content = Map.of(
                "type", "text",
                "text", Objects.toString(text, ""));
        return Map.of("content", List.of(content));
    }

    private Map<String, Object> handleSummarize(Map<String, Object> arguments) {
        String text = Objects.toString(arguments == null ? null : arguments.get("text"), "");
        int maxWords = Integer.parseInt(Objects.toString(arguments == null ? null : arguments.get("maxWords"), "50"));
        String summary = summarize(text, maxWords);
        Map<String, Object> content = Map.of(
                "type", "text",
                "text", summary);
        return Map.of("content", List.of(content));
    }

    private String summarize(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "(empty input)";
        }
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) {
            return text.trim();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, maxWords); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        builder.append(" …");
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleChartEditor(Map<String, Object> arguments) {
        // Extract chart configuration
        String chartType = Objects.toString(arguments == null ? null : arguments.get("chartType"), "bar");
        String title = Objects.toString(arguments == null ? null : arguments.get("title"), "Untitled Chart");
        
        List<Number> data;
        Object dataObj = arguments == null ? null : arguments.get("data");
        if (dataObj instanceof List) {
            data = (List<Number>) dataObj;
        } else {
            data = List.of(10, 20, 30, 40, 50); // default sample data
        }
        
        List<String> labels;
        Object labelsObj = arguments == null ? null : arguments.get("labels");
        if (labelsObj instanceof List) {
            labels = (List<String>) labelsObj;
        } else {
            labels = List.of("A", "B", "C", "D", "E"); // default labels
        }
        
        // Build response with embedded resource reference for MCP Apps
        Map<String, Object> chartConfig = Map.of(
                "chartType", chartType,
                "title", title,
                "data", data,
                "labels", labels);
        
        Map<String, Object> content = Map.of(
                "type", "resource",
                "resource", Map.of(
                        "uri", "mcp://resource/chart-editor.html",
                        "mimeType", "text/html",
                        "metadata", Map.of(
                                "displayHint", "embedded",
                                "initialData", chartConfig)));
        
        return Map.of(
                "content", List.of(content),
                "_meta", Map.of("ui", Map.of(
                        "outputUri", "mcp://resource/chart-editor.html",
                        "displayHint", "embedded")));
    }
}
