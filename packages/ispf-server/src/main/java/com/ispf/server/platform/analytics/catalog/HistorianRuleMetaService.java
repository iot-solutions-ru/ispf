package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.binding.HistorianRuleMetaConstants;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Per-rule historian metadata stored in {@link HistorianRuleMetaConstants#META_VARIABLE} (ADR-0041).
 */
@Service
public class HistorianRuleMetaService {

    public static final String QUALITY_OK = AnalyticsTagMetadataService.QUALITY_OK;
    public static final String QUALITY_UNCERTAIN = AnalyticsTagMetadataService.QUALITY_UNCERTAIN;
    public static final String QUALITY_ERROR = AnalyticsTagMetadataService.QUALITY_ERROR;
    public static final String QUALITY_DISABLED = AnalyticsTagMetadataService.QUALITY_DISABLED;

    private static final DataSchema STRING_SCHEMA = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public HistorianRuleMetaService(ObjectManager objectManager, ObjectMapper objectMapper) {
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    public record RuleMeta(String quality, String lastEvalStatus, Instant lastEvalAt) {
        static RuleMeta empty() {
            return new RuleMeta(QUALITY_OK, "", null);
        }
    }

    @Transactional(readOnly = true)
    public RuleMeta readRuleMeta(PlatformObject node, String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return RuleMeta.empty();
        }
        Map<String, Map<String, String>> all = readAll(node);
        Map<String, String> entry = all.get(ruleId);
        if (entry == null) {
            return RuleMeta.empty();
        }
        return new RuleMeta(
                blankToDefault(entry.get("quality"), QUALITY_OK),
                blankToDefault(entry.get("lastEvalStatus"), ""),
                parseInstant(entry.get("lastEvalAt"))
        );
    }

    @Transactional
    public void recordEvaluation(String tagPath, String status, Instant observedAt) {
        String objectPath = HistorianTagPaths.objectPath(tagPath);
        String ruleId = HistorianTagPaths.ruleId(tagPath);
        if (ruleId.isBlank()) {
            return;
        }
        PlatformObject node = objectManager.require(objectPath);
        Map<String, Map<String, String>> all = readAll(node);
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("lastEvalAt", observedAt != null ? observedAt.toString() : "");
        entry.put("lastEvalStatus", status != null ? status : "");
        entry.put("quality", qualityForStatus(status));
        all.put(ruleId, entry);
        writeAll(objectPath, all);
    }

    @Transactional
    public void setQuality(String tagPath, String quality) {
        String objectPath = HistorianTagPaths.objectPath(tagPath);
        String ruleId = HistorianTagPaths.ruleId(tagPath);
        if (ruleId.isBlank()) {
            return;
        }
        Map<String, Map<String, String>> all = readAll(objectManager.require(objectPath));
        Map<String, String> entry = new LinkedHashMap<>(all.getOrDefault(ruleId, Map.of()));
        entry.put("quality", quality != null ? quality : QUALITY_OK);
        all.put(ruleId, entry);
        writeAll(objectPath, all);
    }

    public static List<String> upstreamTagPathsFromSources(
            List<AnalyticsTagDefinition> tags,
            List<AnalyticsSourceRef> sources
    ) {
        return AnalyticsTagMetadataService.upstreamTagPathsFromSources(tags, sources);
    }

    private Map<String, Map<String, String>> readAll(PlatformObject node) {
        Optional<String> json = node.getVariable(HistorianRuleMetaConstants.META_VARIABLE)
                .flatMap(Variable::value)
                .map(record -> String.valueOf(record.firstRow().get("value")));
        if (json.isEmpty() || json.get().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json.get(), new TypeReference<>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private void writeAll(String objectPath, Map<String, Map<String, String>> all) {
        try {
            String json = objectMapper.writeValueAsString(all);
            objectManager.upsertSystemVariable(
                    objectPath,
                    HistorianRuleMetaConstants.META_VARIABLE,
                    STRING_SCHEMA,
                    DataRecord.single(STRING_SCHEMA, Map.of("value", json))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist historian rule metadata for " + objectPath, ex);
        }
    }

    private static String qualityForStatus(String status) {
        if ("ok".equals(status)) {
            return QUALITY_OK;
        }
        if ("skipped".equals(status)) {
            return QUALITY_UNCERTAIN;
        }
        return QUALITY_ERROR;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}
