package com.ispf.server.application.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BundleSchemaValidator {

    public static final String SCHEMA_RESOURCE = "schema/bundle.schema.json";
    public static final String DOC_REF = "docs/en/decisions/0060-solution-authoring-constraints.md";

    private final ObjectMapper objectMapper;
    private final JsonSchema schema;
    private final com.fasterxml.jackson.databind.ObjectMapper jackson2 =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public BundleSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    private static JsonSchema loadSchema() {
        try (InputStream in = new ClassPathResource(SCHEMA_RESOURCE).getInputStream()) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in);
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
            JsonNode node = jackson2.readTree(objectMapper.writeValueAsString(asMap));
            Set<ValidationMessage> messages = schema.validate(node);
            if (messages == null || messages.isEmpty()) {
                return;
            }
            List<ValidationMessage> sorted = new ArrayList<>(messages);
            sorted.sort((a, b) -> String.valueOf(a.getInstanceLocation())
                    .compareTo(String.valueOf(b.getInstanceLocation())));
            for (ValidationMessage message : sorted) {
                String path = message.getInstanceLocation() != null
                        ? message.getInstanceLocation().toString() : "$";
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

    public JsonSchema schema() {
        return schema;
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
