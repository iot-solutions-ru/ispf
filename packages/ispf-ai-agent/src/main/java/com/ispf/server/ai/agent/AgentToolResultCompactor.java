package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shrinks bulky tool results before they are fed back into the LLM loop (full results stay in step audit).
 */
final class AgentToolResultCompactor {

    private static final int MAX_REPORT_ROWS_FOR_LLM = 12;
    private static final int MAX_HISTORY_SAMPLES_FOR_LLM = 24;
    /** Cap keeps Jackson-serialized driver rows under TreeFirstAgentService's 14k mid-JSON cut. */
    private static final int MAX_CATALOG_ROWS_FOR_LLM = 50;
    private static final int MAX_DESC_CHARS = 60;
    private static final int MAX_SECTION_CHARS_FOR_LLM = 6_000;
    private static final int MAX_MANIFEST_CHARS_FOR_LLM = 11_000;

    private AgentToolResultCompactor() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> compactForLlm(String toolName, Map<String, Object> toolResult) {
        if (toolResult == null || toolResult.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(toolResult);
        if ("run_report".equals(toolName)) {
            compactRunReport(copy);
        } else if ("get_variable_history".equals(toolName)) {
            compactSamples(copy, "samples", MAX_HISTORY_SAMPLES_FOR_LLM);
        } else if ("get_variable_trend".equals(toolName)) {
            compactSamples(copy, "buckets", 48);
        } else if ("invoke_bff".equals(toolName) || "invoke_tree_function".equals(toolName)) {
            compactBffResult(copy);
        } else if ("get_example_bundle".equals(toolName)) {
            compactExampleBundle(copy);
        } else if ("list_drivers".equals(toolName)) {
            compactDriverRows(copy);
        } else if ("get_widget_catalog".equals(toolName)) {
            compactWidgetCatalog(copy);
        } else if ("list_object_blueprints".equals(toolName)) {
            compactBlueprintRows(copy);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void compactRunReport(Map<String, Object> toolResult) {
        Object inner = toolResult.get("result");
        if (!(inner instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> compactReport = new LinkedHashMap<>((Map<String, Object>) inner);
        Object rowsRaw = compactReport.get("rows");
        if (rowsRaw instanceof List<?> rows && rows.size() > MAX_REPORT_ROWS_FOR_LLM) {
            compactReport.put("rows", new ArrayList<>(rows.subList(0, MAX_REPORT_ROWS_FOR_LLM)));
            compactReport.put("rowCount", rows.size());
            compactReport.put("truncatedForLlm", true);
            compactReport.put(
                    "llmHint",
                    "Full table is shown to the operator in UI — summarize key numbers from these rows."
            );
        }
        toolResult.put("result", compactReport);
    }

    private static void compactSamples(Map<String, Object> toolResult, String field, int maxItems) {
        Object raw = toolResult.get(field);
        if (raw instanceof List<?> items && items.size() > maxItems) {
            toolResult.put(field, new ArrayList<>(items.subList(0, maxItems)));
            toolResult.put(field + "TruncatedForLlm", true);
            toolResult.put(field + "Total", items.size());
        }
    }

    @SuppressWarnings("unchecked")
    private static void compactExampleBundle(Map<String, Object> toolResult) {
        Object manifest = toolResult.get("manifest");
        if (!(manifest instanceof Map<?, ?> manifestMap)) {
            return;
        }
        boolean sectionsRequested = Boolean.TRUE.equals(toolResult.get("sectionsRequested"))
                || (toolResult.get("requestedSections") instanceof List<?> requested && !requested.isEmpty());
        if (sectionsRequested) {
            capManifestSections(toolResult, (Map<?, ?>) manifestMap);
            toolResult.put(
                    "llmHint",
                    "Manifest subset matches requestedSections — summarize those sections; do not re-dump huge JSON."
            );
            return;
        }
        Map<String, Object> sections = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : manifestMap.entrySet()) {
            String section = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                sections.put(section, Map.of("itemCount", list.size()));
            } else if (value instanceof Map<?, ?> map) {
                sections.put(section, Map.of("keyCount", map.size()));
            } else {
                sections.put(section, "present");
            }
        }
        toolResult.remove("manifest");
        toolResult.put("manifestSections", sections);
        toolResult.put("truncatedForLlm", true);
        toolResult.put(
                "llmHint",
                "Full manifest is in the tool step UI — finish with a Markdown summary of section names and purpose only; "
                        + "never re-dump manifest JSON in finish summary or result.plan. "
                        + "Re-call with sections=[...] to load specific section bodies."
        );
    }

    @SuppressWarnings("unchecked")
    private static void capManifestSections(Map<String, Object> toolResult, Map<?, ?> manifestMap) {
        int sectionCount = Math.max(1, manifestMap.size());
        int perSectionLimit = Math.min(
                MAX_SECTION_CHARS_FOR_LLM,
                Math.max(800, MAX_MANIFEST_CHARS_FOR_LLM / sectionCount)
        );
        Map<String, Object> capped = new LinkedHashMap<>();
        boolean truncated = false;
        for (Map.Entry<?, ?> entry : manifestMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            String asText = String.valueOf(value);
            if (asText.length() > perSectionLimit) {
                capped.put(key, asText.substring(0, perSectionLimit) + "…");
                truncated = true;
            } else {
                capped.put(key, value);
            }
        }
        toolResult.put("manifest", capped);
        if (truncated) {
            toolResult.put("truncatedForLlm", true);
            toolResult.put("sectionTruncatedForLlm", true);
        }
    }

    @SuppressWarnings("unchecked")
    private static void compactDriverRows(Map<String, Object> toolResult) {
        Object raw = toolResult.get("drivers");
        if (!(raw instanceof List<?> rows) || rows.isEmpty()) {
            return;
        }
        int total = rows.size();
        toolResult.put("count", total);
        List<Map<String, Object>> compact = new ArrayList<>();
        int limit = Math.min(total, MAX_CATALOG_ROWS_FOR_LLM);
        for (int i = 0; i < limit; i++) {
            Object item = rows.get(i);
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> slim = new LinkedHashMap<>();
            putIfPresent(slim, row, "driverId");
            putIfPresent(slim, row, "name");
            putIfPresent(slim, row, "maturity");
            putIfPresent(slim, row, "evidence");
            putIfPresent(slim, row, "readOnly");
            Object desc = row.get("description");
            if (desc != null) {
                String text = String.valueOf(desc);
                slim.put("description", text.length() > MAX_DESC_CHARS ? text.substring(0, MAX_DESC_CHARS) + "…" : text);
            }
            compact.add(slim);
        }
        toolResult.put("drivers", compact);
        if (total > MAX_CATALOG_ROWS_FOR_LLM) {
            toolResult.put("truncatedForLlm", true);
            toolResult.put(
                    "llmHint",
                    "Showing " + compact.size() + " of " + total
                            + " drivers — count is authoritative. For inventory Q&A finish from this list / Knowledge index; "
                            + "for a named protocol not shown, use get_driver_help with that driverId (do not re-call list_drivers)."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static void compactBlueprintRows(Map<String, Object> toolResult) {
        Object raw = toolResult.get("blueprints");
        if (!(raw instanceof List<?> rows) || rows.isEmpty()) {
            return;
        }
        int total = rows.size();
        toolResult.put("count", total);
        List<Map<String, Object>> compact = new ArrayList<>();
        int limit = Math.min(total, MAX_CATALOG_ROWS_FOR_LLM);
        for (int i = 0; i < limit; i++) {
            Object item = rows.get(i);
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> slim = new LinkedHashMap<>();
            putIfPresent(slim, row, "templateId");
            putIfPresent(slim, row, "blueprintId");
            putIfPresent(slim, row, "BlueprintType");
            putIfPresent(slim, row, "targetObjectType");
            Object desc = row.get("description");
            if (desc != null) {
                String text = String.valueOf(desc);
                slim.put("description", text.length() > MAX_DESC_CHARS ? text.substring(0, MAX_DESC_CHARS) + "…" : text);
            }
            compact.add(slim);
        }
        toolResult.put("blueprints", compact);
        if (total > MAX_CATALOG_ROWS_FOR_LLM) {
            toolResult.put("truncatedForLlm", true);
            toolResult.put(
                    "llmHint",
                    "Showing " + compact.size() + " of " + total
                            + " blueprints — filter with query for the rest; count is authoritative."
            );
        }
    }

    private static void putIfPresent(Map<String, Object> target, Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void compactWidgetCatalog(Map<String, Object> toolResult) {
        Object typeFilter = toolResult.get("typeFilter");
        boolean typed = typeFilter != null && !String.valueOf(typeFilter).isBlank();
        Object raw = toolResult.get("widgets");
        if (!(raw instanceof List<?> widgets) || widgets.isEmpty()) {
            return;
        }
        if (typed && widgets.size() == 1) {
            // Single-type lookup keeps propertySpec / full row for authoring.
            return;
        }
        List<Map<String, Object>> compact = new ArrayList<>();
        for (Object item : widgets) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("type", row.get("type"));
            slim.put("binding", row.get("binding"));
            slim.put("purpose", row.get("purpose"));
            compact.add(slim);
        }
        toolResult.put("widgets", compact);
        toolResult.put("count", compact.size());
        toolResult.remove("fieldSemantics");
        toolResult.remove("bindings");
        toolResult.remove("commonFields");
        toolResult.remove("propertySpec");
        toolResult.put("truncatedForLlm", true);
        toolResult.put(
                "llmHint",
                "Widget index only — call get_widget_catalog type=<widgetType> for fields before add_dashboard_widget."
        );
    }

    private static void compactBffResult(Map<String, Object> toolResult) {
        Object data = toolResult.get("data");
        if (data instanceof List<?> rows && rows.size() > MAX_REPORT_ROWS_FOR_LLM) {
            toolResult.put("data", new ArrayList<>(rows.subList(0, MAX_REPORT_ROWS_FOR_LLM)));
            toolResult.put("dataTruncatedForLlm", true);
            toolResult.put("dataTotal", rows.size());
        }
    }
}
