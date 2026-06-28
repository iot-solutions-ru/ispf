package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shrinks bulky tool results before they are fed back into the LLM loop (full results stay in step audit).
 */
final class AgentToolResultCompactor {

    private static final int MAX_REPORT_ROWS_FOR_LLM = 12;
    private static final int MAX_HISTORY_SAMPLES_FOR_LLM = 24;

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

    private static void compactBffResult(Map<String, Object> toolResult) {
        Object data = toolResult.get("data");
        if (data instanceof List<?> rows && rows.size() > MAX_REPORT_ROWS_FOR_LLM) {
            toolResult.put("data", new ArrayList<>(rows.subList(0, MAX_REPORT_ROWS_FOR_LLM)));
            toolResult.put("dataTruncatedForLlm", true);
            toolResult.put("dataTotal", rows.size());
        }
    }
}
