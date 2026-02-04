package io.dscope.camel.mcp.processor;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

/**
 * Handles MCP resources/get requests by extracting the resource identifier
 * from request parameters and delegating to a configurable resource provider.
 * 
 * <p>By default, returns an empty result. Override {@link #setResourceProvider(Function)}
 * to supply custom resource loading logic.
 * 
 * <p>For convenience, use the static helper methods to build properly formatted
 * responses for different content types:
 * <ul>
 *   <li>{@link #blobResource(String, String, byte[])} - binary content (images, etc.)</li>
 *   <li>{@link #textResource(String, String, String)} - text content (html, js, css, etc.)</li>
 *   <li>{@link #jsonResource(String, Map)} - structured JSON data</li>
 * </ul>
 * 
 * <p>Usage in a route:
 * <pre>
 *   to("bean:mcpResourcesGet")
 * </pre>
 */
@BindToRegistry("mcpResourcesGet")
public class McpResourcesGetProcessor extends AbstractMcpResponseProcessor {

    /** Exchange property key for the resolved resource name. */
    public static final String PROPERTY_RESOURCE_NAME = "mcp.resources.get.name";

    private static final String PARAM_RESOURCE = "resource";

    // Binary file extensions
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp", "tiff", "tif",
        "pdf", "zip", "tar", "gz", "rar", "7z",
        "mp3", "mp4", "wav", "ogg", "webm", "avi", "mov",
        "woff", "woff2", "ttf", "otf", "eot"
    );

    // Text file extensions with their MIME types
    private static final Map<String, String> TEXT_MIME_TYPES = Map.ofEntries(
        Map.entry("html", "text/html"),
        Map.entry("htm", "text/html"),
        Map.entry("css", "text/css"),
        Map.entry("js", "application/javascript"),
        Map.entry("mjs", "application/javascript"),
        Map.entry("ts", "application/typescript"),
        Map.entry("tsx", "text/tsx"),
        Map.entry("jsx", "text/jsx"),
        Map.entry("json", "application/json"),
        Map.entry("xml", "application/xml"),
        Map.entry("yaml", "application/yaml"),
        Map.entry("yml", "application/yaml"),
        Map.entry("md", "text/markdown"),
        Map.entry("txt", "text/plain"),
        Map.entry("csv", "text/csv"),
        Map.entry("sql", "application/sql"),
        Map.entry("sh", "application/x-sh"),
        Map.entry("bash", "application/x-sh"),
        Map.entry("py", "text/x-python"),
        Map.entry("java", "text/x-java-source"),
        Map.entry("c", "text/x-c"),
        Map.entry("cpp", "text/x-c++"),
        Map.entry("h", "text/x-c"),
        Map.entry("go", "text/x-go"),
        Map.entry("rs", "text/x-rust"),
        Map.entry("rb", "text/x-ruby"),
        Map.entry("php", "text/x-php"),
        Map.entry("swift", "text/x-swift"),
        Map.entry("kt", "text/x-kotlin"),
        Map.entry("scala", "text/x-scala"),
        Map.entry("groovy", "text/x-groovy"),
        Map.entry("properties", "text/x-java-properties"),
        Map.entry("ini", "text/plain"),
        Map.entry("conf", "text/plain"),
        Map.entry("log", "text/plain")
    );

    // Binary MIME types
    private static final Map<String, String> BINARY_MIME_TYPES = Map.ofEntries(
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("svg", "image/svg+xml"),
        Map.entry("ico", "image/x-icon"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("tiff", "image/tiff"),
        Map.entry("tif", "image/tiff"),
        Map.entry("pdf", "application/pdf"),
        Map.entry("zip", "application/zip"),
        Map.entry("tar", "application/x-tar"),
        Map.entry("gz", "application/gzip"),
        Map.entry("rar", "application/vnd.rar"),
        Map.entry("7z", "application/x-7z-compressed"),
        Map.entry("mp3", "audio/mpeg"),
        Map.entry("mp4", "video/mp4"),
        Map.entry("wav", "audio/wav"),
        Map.entry("ogg", "audio/ogg"),
        Map.entry("webm", "video/webm"),
        Map.entry("avi", "video/x-msvideo"),
        Map.entry("mov", "video/quicktime"),
        Map.entry("woff", "font/woff"),
        Map.entry("woff2", "font/woff2"),
        Map.entry("ttf", "font/ttf"),
        Map.entry("otf", "font/otf"),
        Map.entry("eot", "application/vnd.ms-fontobject")
    );

    private Function<String, Map<String, Object>> resourceProvider = name -> Map.of();

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        // Extract resource identifier from request params
        Map<String, Object> params = getRequestParameters(exchange);
        Object rawResource = params != null ? params.get(PARAM_RESOURCE) : null;
        
        if (rawResource == null) {
            writeError(exchange, Map.of(
                "code", -32602,
                "message", "Invalid params: 'resource' parameter is required"
            ), 400);
            return;
        }
        
        String resourceName = rawResource.toString().trim();
        if (resourceName.isEmpty()) {
            writeError(exchange, Map.of(
                "code", -32602,
                "message", "Invalid params: 'resource' parameter must not be blank"
            ), 400);
            return;
        }
        
        // Store resource name for downstream processors if needed
        exchange.setProperty(PROPERTY_RESOURCE_NAME, resourceName);
        
        // Load resource via provider
        Map<String, Object> result = resourceProvider.apply(resourceName);
        writeResult(exchange, result);
    }

    /**
     * Sets the resource provider function that loads resource content by name.
     * 
     * @param provider function that takes a resource name and returns the resource payload
     */
    public void setResourceProvider(Function<String, Map<String, Object>> provider) {
        this.resourceProvider = provider != null ? provider : name -> Map.of();
    }

    /**
     * Gets the current resource provider.
     * 
     * @return the resource provider function
     */
    public Function<String, Map<String, Object>> getResourceProvider() {
        return resourceProvider;
    }

    // ==================== Static Helper Methods ====================

    /**
     * Checks if the given resource name has a binary file extension.
     * 
     * @param resourceName the resource name to check
     * @return true if the extension indicates binary content
     */
    public static boolean isBinaryResource(String resourceName) {
        String ext = getExtension(resourceName);
        return BINARY_EXTENSIONS.contains(ext.toLowerCase());
    }

    /**
     * Checks if the given resource name has a known text file extension.
     * 
     * @param resourceName the resource name to check
     * @return true if the extension indicates text content
     */
    public static boolean isTextResource(String resourceName) {
        String ext = getExtension(resourceName);
        return TEXT_MIME_TYPES.containsKey(ext.toLowerCase());
    }

    /**
     * Gets the MIME type for the given resource name based on its extension.
     * 
     * @param resourceName the resource name
     * @return the MIME type, or "application/octet-stream" if unknown
     */
    public static String getMimeType(String resourceName) {
        String ext = getExtension(resourceName).toLowerCase();
        if (BINARY_MIME_TYPES.containsKey(ext)) {
            return BINARY_MIME_TYPES.get(ext);
        }
        if (TEXT_MIME_TYPES.containsKey(ext)) {
            return TEXT_MIME_TYPES.get(ext);
        }
        return "application/octet-stream";
    }

    /**
     * Gets the file extension from a resource name.
     * 
     * @param resourceName the resource name
     * @return the extension without the dot, or empty string if none
     */
    public static String getExtension(String resourceName) {
        if (resourceName == null) {
            return "";
        }
        int dotIndex = resourceName.lastIndexOf('.');
        return dotIndex > 0 ? resourceName.substring(dotIndex + 1) : "";
    }

    /**
     * Creates a response map for binary content (images, PDFs, etc.).
     * The content is base64-encoded.
     * 
     * @param uri the resource URI
     * @param mimeType the MIME type (e.g., "image/jpeg")
     * @param content the binary content
     * @return a properly formatted response map
     */
    public static Map<String, Object> blobResource(String uri, String mimeType, byte[] content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("mimeType", mimeType);
        result.put("blob", Base64.getEncoder().encodeToString(content));
        return result;
    }

    /**
     * Creates a response map for text content (HTML, CSS, JS, etc.).
     * 
     * @param uri the resource URI
     * @param mimeType the MIME type (e.g., "text/html")
     * @param content the text content
     * @return a properly formatted response map
     */
    public static Map<String, Object> textResource(String uri, String mimeType, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("mimeType", mimeType);
        result.put("text", content);
        return result;
    }

    /**
     * Creates a response map for JSON/structured data.
     * 
     * @param uri the resource URI (optional, can be null)
     * @param data the structured data
     * @return a properly formatted response map
     */
    public static Map<String, Object> jsonResource(String uri, Map<String, Object> data) {
        if (uri == null) {
            return data;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("mimeType", "application/json");
        result.put("data", data);
        return result;
    }

    /**
     * Creates an error response map.
     * 
     * @param message the error message
     * @return an error response map
     */
    public static Map<String, Object> errorResource(String message) {
        return Map.of("error", message);
    }
}
