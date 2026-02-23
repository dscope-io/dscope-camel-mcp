package io.dscope.tools.karavan;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Generates Apache Karavan metadata for the Camel MCP component.
 *
 * <p>Produces:
 * <ul>
 *   <li>{@code karavan/metadata/component/mcp.json} — component descriptor
 *       with endpoint properties and method enums</li>
 *   <li>{@code karavan/metadata/kamelet/mcp-rest-service.json} — REST kamelet descriptor</li>
 *   <li>{@code karavan/metadata/kamelet/mcp-ws-service.json} — WebSocket kamelet descriptor</li>
 *   <li>{@code karavan/metadata/model-labels.json} — human-friendly labels</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * mvn -Pkaravan-metadata compile exec:java
 * </pre>
 */
public class McpKaravanMetadataGenerator {

    private static final String BASE_DIR = "src/main/resources/karavan/metadata";
    private static final String COMPONENT_DIR = BASE_DIR + "/component";
    private static final String KAMELET_DIR = BASE_DIR + "/kamelet";
    private static final String LABELS_FILE = BASE_DIR + "/model-labels.json";

    // MCP JSON-RPC methods supported by the component
    private static final String[][] MCP_METHODS = {
        {"initialize",              "Core",     "Initialize the MCP session and negotiate capabilities."},
        {"ping",                    "Core",     "Health-check ping; the server replies immediately."},
        {"tools/list",              "Tools",    "List all tools the server exposes."},
        {"tools/call",              "Tools",    "Invoke a named tool with arguments."},
        {"resources/list",          "Resources","List available resources."},
        {"resources/read",          "Resources","Read the content of a specific resource."},
        {"resources/get",           "Resources","Stream or fetch a resource."},
        {"health",                  "Core",     "Return overall health/status of the server."},
        {"stream",                  "Core",     "Open a bidirectional streaming channel."},
        {"ui/initialize",           "UI Bridge","Initialize an MCP Apps Bridge UI session."},
        {"ui/message",              "UI Bridge","Send a message through the UI bridge."},
        {"ui/update-model-context", "UI Bridge","Push updated model context to the UI."},
        {"ui/tools/call",           "UI Bridge","Call a tool within a UI session."},
    };

    // Notification methods (no JSON-RPC id)
    private static final String[][] NOTIFICATION_METHODS = {
        {"notifications/initialized", "Notifications", "Sent by client after initialize handshake."},
        {"notifications/cancelled",   "Notifications", "Cancel a running operation."},
        {"notifications/progress",    "Notifications", "Report progress for a long-running operation."},
    };

    public static void main(String[] args) throws Exception {
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        // Ensure output directories exist
        new File(COMPONENT_DIR).mkdirs();
        new File(KAMELET_DIR).mkdirs();

        Map<String, String> allLabels = new TreeMap<>();

        // 1. Generate component descriptor
        generateComponentDescriptor(om);

        // 2. Generate method catalog metadata
        generateMethodCatalog(om, allLabels);

        // 3. Generate kamelet descriptors
        generateKameletDescriptor(om, "mcp-rest-service",
                "MCP REST Service",
                "Exposes an MCP-compliant JSON-RPC server over HTTP using Undertow.",
                false, allLabels);
        generateKameletDescriptor(om, "mcp-ws-service",
                "MCP WebSocket Service",
                "Exposes an MCP-compliant JSON-RPC server over WebSocket using Undertow.",
                true, allLabels);

        // 4. Write labels file
        om.writeValue(new File(LABELS_FILE), allLabels);
        System.out.println("Wrote " + LABELS_FILE + " (" + allLabels.size() + " labels)");

        System.out.println("\nKaravan metadata generation complete.");
    }

    // ---- component descriptor ------------------------------------------------

    private static void generateComponentDescriptor(ObjectMapper om) throws Exception {
        ObjectNode root = om.createObjectNode();

        // Component section
        ObjectNode comp = root.putObject("component");
        comp.put("kind", "component");
        comp.put("name", "mcp");
        comp.put("title", "MCP");
        comp.put("description",
                "Model Context Protocol (MCP) component for AI agent integration. "
                + "Supports JSON-RPC 2.0 over HTTP and WebSocket. "
                + "Producer sends requests to an MCP server; Consumer exposes an MCP server endpoint.");
        comp.put("scheme", "mcp");
        comp.put("syntax", "mcp:uri");
        comp.put("alternativeSyntax", "mcp:uri?method=initialize");
        comp.put("firstVersion", "1.0.0");
        comp.put("groupId", "io.dscope.camel");
        comp.put("artifactId", "camel-mcp");
        comp.put("version", "1.4.0");
        comp.put("producerOnly", false);
        comp.put("consumerOnly", false);
        comp.put("lenientProperties", true);
        ArrayNode labels = comp.putArray("label");
        labels.add("ai");
        labels.add("mcp");
        labels.add("rpc");

        // Properties section
        ObjectNode props = root.putObject("properties");

        addProperty(props, "uri", "path", true,
                "string", null,
                "The target MCP server URI (e.g. http://localhost:8080/mcp).",
                "common");
        addProperty(props, "method", "parameter", false,
                "string", "tools/list",
                "The MCP JSON-RPC method to invoke.",
                "producer");
        addProperty(props, "websocket", "parameter", false,
                "boolean", "false",
                "When true the consumer creates a WebSocket endpoint instead of HTTP.",
                "consumer");
        addProperty(props, "sendToAll", "parameter", false,
                "boolean", "false",
                "For WebSocket consumers, broadcast messages to all connected clients.",
                "consumer");
        addProperty(props, "allowedOrigins", "parameter", false,
                "string", "*",
                "Comma-separated list of allowed CORS origins.",
                "consumer");
        addProperty(props, "httpMethodRestrict", "parameter", false,
                "string", "POST",
                "HTTP methods allowed by the consumer endpoint.",
                "consumer");

        // Add method enum values
        ArrayNode methodEnums = ((ObjectNode) props.get("method")).putArray("enum");
        for (String[] m : MCP_METHODS) {
            methodEnums.add(m[0]);
        }

        String file = COMPONENT_DIR + "/mcp.json";
        om.writeValue(new File(file), root);
        System.out.println("Wrote " + file);
    }

    private static void addProperty(ObjectNode props, String name, String kind, boolean required,
                                    String type, String defaultValue, String description, String label) {
        ObjectNode p = props.putObject(name);
        p.put("kind", kind);
        p.put("displayName", generateLabel(name));
        p.put("group", label);
        p.put("label", label);
        p.put("required", required);
        p.put("type", type);
        if (defaultValue != null) {
            p.put("defaultValue", defaultValue);
        }
        p.put("description", description);
    }

    // ---- method catalog metadata ------------------------------------------

    private static void generateMethodCatalog(ObjectMapper om, Map<String, String> allLabels) throws Exception {
        ObjectNode root = om.createObjectNode();
        root.put("kind", "mcp-methods");
        root.put("title", "MCP Methods");

        ArrayNode methods = root.putArray("methods");
        for (String[] m : MCP_METHODS) {
            ObjectNode method = methods.addObject();
            method.put("name", m[0]);
            method.put("group", m[1]);
            method.put("description", m[2]);
            method.put("type", "request");
            allLabels.put("method." + m[0], m[2]);
        }
        for (String[] n : NOTIFICATION_METHODS) {
            ObjectNode method = methods.addObject();
            method.put("name", n[0]);
            method.put("group", n[1]);
            method.put("description", n[2]);
            method.put("type", "notification");
            allLabels.put("method." + n[0], n[2]);
        }

        String file = BASE_DIR + "/mcp-methods.json";
        om.writeValue(new File(file), root);
        System.out.println("Wrote " + file + " (" + (MCP_METHODS.length + NOTIFICATION_METHODS.length) + " methods)");
    }

    // ---- kamelet descriptors -----------------------------------------------

    private static void generateKameletDescriptor(ObjectMapper om, String kameletId, String title,
                                                  String description, boolean ws,
                                                  Map<String, String> allLabels) throws Exception {
        ObjectNode root = om.createObjectNode();
        root.put("kind", "kamelet");
        root.put("name", kameletId);
        root.put("title", title);
        root.put("description", description);

        // Kamelet properties extracted from YAML
        ObjectNode props = root.putObject("properties");
        addKameletProp(props, "port",
                ws ? "8090" : "8080",
                "integer",
                ws ? "WebSocket listen port" : "HTTP listen port");
        addKameletProp(props, "host", "0.0.0.0", "string", "Listen address");
        addKameletProp(props, "path", "/mcp", "string", "Context path for the MCP endpoint");

        ArrayNode labels = root.putArray("labels");
        labels.add("ai");
        labels.add("mcp");
        if (ws) labels.add("websocket");

        // Supported methods
        ArrayNode supportedMethods = root.putArray("supportedMethods");
        for (String[] m : MCP_METHODS) {
            supportedMethods.add(m[0]);
        }

        allLabels.put("kamelet." + kameletId + ".title", title);
        allLabels.put("kamelet." + kameletId + ".description", description);

        String file = KAMELET_DIR + "/" + kameletId + ".json";
        om.writeValue(new File(file), root);
        System.out.println("Wrote " + file);
    }

    private static void addKameletProp(ObjectNode props, String name, String defaultValue,
                                       String type, String description) {
        ObjectNode p = props.putObject(name);
        p.put("title", generateLabel(name));
        p.put("type", type);
        p.put("default", defaultValue);
        p.put("description", description);
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Generate a human-friendly label from a camelCase field name.
     * e.g. "httpMethodRestrict" → "Http Method Restrict"
     */
    private static String generateLabel(String fieldName) {
        String[] words = fieldName.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (label.length() > 0) label.append(" ");
            if (!word.isEmpty()) {
                label.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) label.append(word.substring(1));
            }
        }
        return label.toString();
    }
}
