package com.ispf.server.ai.mcp;

import com.ispf.server.ai.context.ContextPackService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exposes ContextPack slices as MCP resources (ADR-0013 follow-up).
 */
@Service
@ConditionalOnProperty(prefix = "ispf.mcp", name = "enabled", havingValue = "true")
public class McpResourceAdapter {

    static final String URI_PREFIX = "contextpack://";

    private static final List<ResourceDescriptor> DESCRIPTORS = List.of(
            new ResourceDescriptor("info", "ContextPack version and counts"),
            new ResourceDescriptor("bundle-manifest", "Bundle manifest fields and generation rules"),
            new ResourceDescriptor("script-steps", "Application function script step names"),
            new ResourceDescriptor("widget-types", "Dashboard widget type catalog"),
            new ResourceDescriptor("driver-catalog", "Driver catalog index from ContextPack"),
            new ResourceDescriptor("feature-index", "Platform feature index"),
            new ResourceDescriptor("example-summaries", "Reference bundle example summaries"),
            new ResourceDescriptor("doc-chunks", "Documentation chunks for platform topics")
    );

    private final ContextPackService contextPackService;
    private final ObjectMapper objectMapper;

    public McpResourceAdapter(ContextPackService contextPackService, ObjectMapper objectMapper) {
        this.contextPackService = contextPackService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listResources() {
        List<Map<String, Object>> resources = new ArrayList<>();
        for (ResourceDescriptor descriptor : DESCRIPTORS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uri", URI_PREFIX + descriptor.slice());
            row.put("name", descriptor.slice());
            row.put("description", descriptor.description());
            row.put("mimeType", "application/json");
            resources.add(row);
        }
        return resources;
    }

    public Map<String, Object> readResource(String uri) {
        if (uri == null || !uri.startsWith(URI_PREFIX)) {
            throw new IllegalArgumentException("Unsupported resource URI: " + uri);
        }
        String slice = uri.substring(URI_PREFIX.length()).trim();
        if (slice.isBlank()) {
            throw new IllegalArgumentException("Resource slice is required");
        }
        Object payload = resolveSlice(slice)
                .orElseThrow(() -> new IllegalArgumentException("Unknown resource slice: " + slice));
        return Map.of(
                "contents", List.of(Map.of(
                        "uri", URI_PREFIX + slice,
                        "mimeType", "application/json",
                        "text", writeJson(payload)
                ))
        );
    }

    private Optional<Object> resolveSlice(String slice) {
        Map<String, Object> pack = contextPackService.loadPack();
        return switch (slice) {
            case "info" -> Optional.of(contextPackService.info());
            case "bundle-manifest" -> optionalPackKey(pack, "bundleManifest");
            case "script-steps" -> optionalPackKey(pack, "scriptSteps");
            case "widget-types" -> optionalPackKey(pack, "widgetTypes");
            case "driver-catalog" -> optionalPackKey(pack, "driverCatalog");
            case "feature-index" -> optionalPackKey(pack, "featureIndex");
            case "example-summaries" -> {
                if (pack.containsKey("exampleSummaries")) {
                    yield optionalPackKey(pack, "exampleSummaries");
                }
                yield optionalPackKey(pack, "examples");
            }
            case "doc-chunks" -> optionalPackKey(pack, "docChunks");
            default -> Optional.empty();
        };
    }

    private static Optional<Object> optionalPackKey(Map<String, Object> pack, String key) {
        return pack.containsKey(key) ? Optional.ofNullable(pack.get(key)) : Optional.empty();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize resource: " + ex.getMessage(), ex);
        }
    }

    private record ResourceDescriptor(String slice, String description) {
    }
}
