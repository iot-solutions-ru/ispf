package com.ispf.server.application.bundle;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BundleSchemaValidator {

    public static final String SCHEMA_RESOURCE = "schema/bundle.schema.json";
    public static final String DOC_REF = "docs/en/decisions/0060-solution-authoring-constraints.md";

    private final ObjectMapper objectMapper;
    private final Schema schema;

    public BundleSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    private static Schema loadSchema() {
        try {
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
            Schema loaded = registry.getSchema(SchemaLocation.of("classpath:" + SCHEMA_RESOURCE));
            loaded.initializeValidators();
            return loaded;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load " + SCHEMA_RESOURCE, ex);
        }
    }

    public void validate(ApplicationBundleDeployService.BundleManifest manifest, BundleValidationResult.Builder builder) {
        if (manifest == null) {
            builder.addIssue(BundleValidationIssue.error(
                    "BUNDLE_SCHEMA", "$", "manifest is null",
                    "Provide a JSON bundle body matching bundle.schema.json", DOC_REF));
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> asMap = objectMapper.convertValue(manifest, Map.class);
            stripNulls(asMap);
            JsonNode node = objectMapper.valueToTree(asMap);
            List<Error> messages = schema.validate(node);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            List<Error> sorted = new ArrayList<>(messages);
            sorted.sort((a, b) -> String.valueOf(a.getInstanceLocation())
                    .compareTo(String.valueOf(b.getInstanceLocation())));
            for (Error message : sorted) {
                String path = message.getInstanceLocation() != null
                        ? message.getInstanceLocation().toString() : "$";
                if (path.isBlank()) {
                    path = "$";
                }
                builder.addIssue(BundleValidationIssue.error(
                        "BUNDLE_SCHEMA", path, message.getMessage(),
                        "Fix the field to match schema/bundle.schema.json", DOC_REF));
            }
        } catch (Exception ex) {
            builder.addIssue(BundleValidationIssue.error(
                    "BUNDLE_SCHEMA", "$", "schema validation failed: " + ex.getMessage(),
                    "Ensure the manifest is JSON-serializable as BundleManifest", DOC_REF));
        }
    }

    @SuppressWarnings("unchecked")
    private static void stripNulls(Map<String, Object> map) {
        map.entrySet().removeIf(e -> e.getValue() == null);
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?> nested) {
                stripNulls((Map<String, Object>) nested);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem) {
                        stripNulls((Map<String, Object>) nestedItem);
                    }
                }
            }
        }
    }
}
