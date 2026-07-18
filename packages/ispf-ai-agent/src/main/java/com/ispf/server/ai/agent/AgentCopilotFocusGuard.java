package com.ispf.server.ai.agent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Blocks Copilot finish answers that ignore live UI focus (ask for path/expression
 * that is already present, or handbook about the editor instead of the draft CEL).
 */
public final class AgentCopilotFocusGuard {

    private AgentCopilotFocusGuard() {
    }

    public static boolean alreadyNudged(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        for (Map<String, Object> step : steps) {
            if (step != null && Boolean.TRUE.equals(step.get("copilotFocusNudge"))) {
                return true;
            }
        }
        return false;
    }

    public static Optional<String> rejectClarifyFinish(
            String clientChannel,
            Map<String, Object> clientFocus,
            String finishSummary
    ) {
        if (!"copilot".equalsIgnoreCase(stringVal(clientChannel))) {
            return Optional.empty();
        }
        if (clientFocus == null || clientFocus.isEmpty()) {
            return Optional.empty();
        }
        String summary = finishSummary == null ? "" : finishSummary;
        if (summary.isBlank()) {
            return Optional.empty();
        }
        String surface = stringVal(clientFocus.get("surface"));
        String objectPath = stringVal(clientFocus.get("objectPath"));
        Object detailRaw = clientFocus.get("detail");
        Map<?, ?> detail = detailRaw instanceof Map<?, ?> map ? map : Map.of();
        String expression = stringVal(detail.get("expression"));
        String ruleId = stringVal(detail.get("ruleId"));
        boolean hasRules = detail.get("rules") instanceof List<?> list && !list.isEmpty();
        boolean hasScreenMeta = !stringVal(detail.get("screenTitle")).isBlank()
                || !stringVal(detail.get("systemTab")).isBlank()
                || !stringVal(detail.get("settingsTab")).isBlank()
                || !stringVal(detail.get("studioTab")).isBlank()
                || "system".equalsIgnoreCase(surface)
                || "ai-studio".equalsIgnoreCase(surface);
        boolean hasFocusSubject = !objectPath.isBlank()
                || !expression.isBlank()
                || hasRules
                || !ruleId.isBlank()
                || hasScreenMeta;
        if (!hasFocusSubject) {
            return Optional.empty();
        }

        String lower = summary.toLowerCase(Locale.ROOT);
        boolean asksClarify = containsAny(
                lower,
                "не указали",
                "вы не указали",
                "уточните",
                "какой именно",
                "какое именно",
                "какой экран",
                "какое cel",
                "какое выражение",
                "опишите",
                "опишите его",
                "скопируйте содержимое",
                "скопируйте",
                "пришлите",
                "скриншот",
                "укажите путь",
                "укажите объект",
                "provide its path",
                "provide the path",
                "which object",
                "which screen",
                "which expression",
                "paste the expression",
                "send the expression",
                "root.platform.devices.*",
                "list_variables path",
                "list_binding_rules path",
                "describe_variables path",
                "режим execute",
                "режим ask",
                "ask-режим",
                "ask режиме",
                "ask mode",
                "execute mode",
                "режим выполнить",
                "режим спросить",
                "режим план",
                "переключитесь в режим",
                "переключиться в режим",
                "switch to execute",
                "switch to plan"
        );
        boolean genericEditorHandbook = containsAny(
                lower,
                "редактор выражений",
                "expression editor"
        ) && containsAny(
                lower,
                "синтакс",
                "syntax",
                "автодополнен",
                "autocomplete",
                "черновик хранится",
                "common expression language"
        );

        if (!asksClarify && !(genericEditorHandbook && !expression.isBlank()
                && !summaryMentionsExpression(summary, expression))) {
            return Optional.empty();
        }

        StringBuilder hint = new StringBuilder();
        hint.append("FORBIDDEN: you asked the user to clarify / paste what LIVE UI already has.\n");
        hint.append("Answer from this snapshot NOW — no clarifying questions:\n");
        if (!surface.isBlank()) {
            hint.append("surface=").append(surface).append('\n');
        }
        if (!objectPath.isBlank()) {
            hint.append("objectPath=").append(objectPath).append('\n');
        }
        for (String key : new String[]{"screenTitle", "systemTab", "settingsTab", "studioTab", "screenHint"}) {
            String value = stringVal(detail.get(key));
            if (!value.isBlank()) {
                hint.append(key).append("=").append(value).append('\n');
            }
        }
        if (!ruleId.isBlank()) {
            hint.append("ruleId=").append(ruleId).append('\n');
        }
        if (!expression.isBlank()) {
            hint.append("EXPRESSION:\n").append(expression).append('\n');
        }
        Object rules = detail.get("rules");
        if (rules instanceof List<?> ruleList && !ruleList.isEmpty()) {
            hint.append("rules (").append(ruleList.size()).append("):\n");
            int limit = Math.min(ruleList.size(), 12);
            for (int i = 0; i < limit; i++) {
                Object rule = ruleList.get(i);
                if (!(rule instanceof Map<?, ?> ruleMap)) {
                    continue;
                }
                hint.append("  - ")
                        .append(stringVal(ruleMap.get("id")))
                        .append(" → ")
                        .append(stringVal(ruleMap.get("target")))
                        .append(" :: ")
                        .append(stringVal(ruleMap.get("expression")))
                        .append('\n');
            }
        }
        hint.append(
                "Finish with Markdown that helps on the focused screen. "
                        + "Do NOT mention Ask/Plan/Execute modes. "
                        + "If the user wants changes applied, use tools on the focus path instead of asking them to switch modes."
        );
        return Optional.of(hint.toString());
    }

    private static boolean summaryMentionsExpression(String summary, String expression) {
        String compactExpr = expression.replaceAll("\\s+", "");
        if (compactExpr.length() < 8) {
            return summary.contains(expression.trim());
        }
        String needle = compactExpr.length() > 32 ? compactExpr.substring(0, 32) : compactExpr;
        return summary.replaceAll("\\s+", "").contains(needle);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
