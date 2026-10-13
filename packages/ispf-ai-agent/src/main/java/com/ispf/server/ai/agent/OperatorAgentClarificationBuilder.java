package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds interactive clarification finishes when the operator request is ambiguous,
 * uses plant-specific terminology, or the model is stuck repeating catalog tools.
 * User-facing text follows {@code uiLocale}.
 */
final class OperatorAgentClarificationBuilder {

    record ClarificationFinish(String summary, Map<String, Object> result) {
    }

    private OperatorAgentClarificationBuilder() {
    }

    static Optional<ClarificationFinish> maybeAfterListReports(
            List<Map<String, Object>> steps,
            String userMessage,
            String uiLocale
    ) {
        if (!OperatorAgentTurnGuard.isReportRunIntent(userMessage)) {
            return Optional.empty();
        }
        List<OperatorAgentReportResolver.ReportEntry> catalog = catalogFromSteps(steps);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        OperatorAgentReportResolver.MatchAnalysis analysis =
                OperatorAgentReportResolver.analyze(userMessage, catalog, uiLocale);
        if (!analysis.needsClarification()
                && !OperatorAgentReportResolver.mentionsUnknownReportId(userMessage, catalog)) {
            return Optional.empty();
        }
        return Optional.of(buildReportClarification(userMessage, catalog, analysis, uiLocale));
    }

    static Optional<ClarificationFinish> onInvalidReportPath(
            List<Map<String, Object>> steps,
            String userMessage,
            String invalidPath,
            String uiLocale
    ) {
        List<OperatorAgentReportResolver.ReportEntry> catalog = catalogFromSteps(steps);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        String leaf = invalidPath.contains(".")
                ? invalidPath.substring(invalidPath.lastIndexOf('.') + 1)
                : invalidPath;
        OperatorAgentReportResolver.MatchAnalysis analysis =
                OperatorAgentReportResolver.analyze(userMessage, catalog, uiLocale);
        boolean ru = AgentStepHumanizer.isRussian(uiLocale);
        StringBuilder summary = new StringBuilder();
        summary.append(ru
                ? ("Отчёт «" + leaf + "» недоступен в этом приложении оператора.")
                : ("Report \"" + leaf + "\" is not available in this operator app."));
        if (analysis.mismatchReason() != null) {
            summary.append(' ').append(analysis.mismatchReason());
        } else if (analysis.bestTitle() != null && !analysis.bestTitle().isBlank()) {
            summary.append(ru
                    ? (" Ближе всего: «" + analysis.bestTitle() + "».")
                    : (" Closest match: \"" + analysis.bestTitle() + "\"."));
        }
        summary.append(ru
                ? "\n\nВыберите один из доступных отчётов:"
                : "\n\nPick one of the available reports:");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestions", buildSuggestions(catalog, analysis, uiLocale));
        result.put("interactive", true);
        result.put("rejectedPath", invalidPath);
        return Optional.of(new ClarificationFinish(summary.toString(), result));
    }

    private static List<Map<String, Object>> buildSuggestions(
            List<OperatorAgentReportResolver.ReportEntry> catalog,
            OperatorAgentReportResolver.MatchAnalysis analysis,
            String uiLocale
    ) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (!analysis.ranked().isEmpty()) {
            for (OperatorAgentReportResolver.ScoredEntry scored : analysis.ranked()) {
                if (suggestions.size() >= 4) {
                    break;
                }
                suggestions.add(reportSuggestion(scored.entry(), suggestions.isEmpty(), uiLocale));
            }
            return suggestions;
        }
        for (OperatorAgentReportResolver.ReportEntry entry : catalog) {
            if (suggestions.size() >= 4) {
                break;
            }
            suggestions.add(reportSuggestion(entry, suggestions.isEmpty(), uiLocale));
        }
        return suggestions;
    }

    static Optional<ClarificationFinish> onRepeatListReports(
            List<Map<String, Object>> steps,
            String userMessage,
            String uiLocale
    ) {
        List<OperatorAgentReportResolver.ReportEntry> catalog = catalogFromSteps(steps);
        if (catalog.isEmpty()) {
            return Optional.empty();
        }
        OperatorAgentReportResolver.MatchAnalysis analysis =
                OperatorAgentReportResolver.analyze(userMessage, catalog, uiLocale);
        return Optional.of(buildReportClarification(userMessage, catalog, analysis, uiLocale));
    }

    static Optional<ClarificationFinish> onStuckToolLoop(
            List<Map<String, Object>> steps,
            String userMessage,
            String uiLocale
    ) {
        if (OperatorAgentTurnGuard.isReportRunIntent(userMessage)) {
            return onRepeatListReports(steps, userMessage, uiLocale);
        }
        return Optional.empty();
    }

    private static ClarificationFinish buildReportClarification(
            String userMessage,
            List<OperatorAgentReportResolver.ReportEntry> catalog,
            OperatorAgentReportResolver.MatchAnalysis analysis,
            String uiLocale
    ) {
        boolean ru = AgentStepHumanizer.isRussian(uiLocale);
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (OperatorAgentReportResolver.ScoredEntry scored : analysis.ranked()) {
            if (suggestions.size() >= 4) {
                break;
            }
            suggestions.add(reportSuggestion(scored.entry(), suggestions.isEmpty(), uiLocale));
        }
        if (suggestions.isEmpty()) {
            for (OperatorAgentReportResolver.ReportEntry entry : catalog) {
                if (suggestions.size() >= 4) {
                    break;
                }
                suggestions.add(reportSuggestion(entry, suggestions.isEmpty(), uiLocale));
            }
        }

        StringBuilder summary = new StringBuilder();
        if (analysis.terminologyMismatch() && analysis.mismatchReason() != null) {
            summary.append(analysis.mismatchReason());
        } else if (analysis.bestTitle() != null && !analysis.bestTitle().isBlank()) {
            summary.append(ru
                    ? ("Уточните, пожалуйста: вы имели в виду отчёт «" + analysis.bestTitle() + "»?")
                    : ("Did you mean the report \"" + analysis.bestTitle() + "\"?"));
        } else {
            summary.append(ru
                    ? "Не удалось однозначно выбрать отчёт. Вот что доступно в этом приложении:"
                    : "Could not pick a single report. Available in this app:");
        }

        if (ru) {
            summary.append("\n\nЧто я могу сделать:\n");
            summary.append("• Запустить один из отчётов ниже и кратко описать цифры\n");
            summary.append("• Показать аварии, тренды или задачи смены — сформулируйте запрос иначе\n");
            summary.append("\nВыберите вариант или напишите своими словами.");
        } else {
            summary.append("\n\nWhat I can do:\n");
            summary.append("• Run one of the reports below and briefly describe the numbers\n");
            summary.append("• Show alarms, trends, or shift tasks — rephrase your request\n");
            summary.append("\nPick an option or write in your own words.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestions", suggestions);
        result.put("interactive", true);
        if (analysis.bestPath() != null) {
            result.put("suggestedPath", analysis.bestPath());
        }
        if (userMessage != null && !userMessage.isBlank()) {
            result.put("originalRequest", userMessage.trim());
        }
        return new ClarificationFinish(summary.toString(), result);
    }

    private static Map<String, Object> reportSuggestion(
            OperatorAgentReportResolver.ReportEntry entry,
            boolean primary,
            String uiLocale
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        String title = entry.title().isBlank() ? entry.path() : entry.title();
        boolean ru = AgentStepHumanizer.isRussian(uiLocale);
        item.put("label", primary
                ? (ru ? title + " (рекомендуется)" : title + " (recommended)")
                : title);
        item.put("message", ru
                ? ("Запусти отчёт «" + title + "» и кратко опиши цифры")
                : ("Run report \"" + title + "\" and briefly describe the numbers"));
        item.put("kind", "report");
        item.put("path", entry.path());
        if (primary) {
            item.put("primary", true);
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private static List<OperatorAgentReportResolver.ReportEntry> catalogFromSteps(
            List<Map<String, Object>> steps
    ) {
        if (steps == null) {
            return List.of();
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            Map<String, Object> step = steps.get(i);
            if (!"list_reports".equals(step.get("tool"))) {
                continue;
            }
            Object result = step.get("result");
            if (result instanceof Map<?, ?> map) {
                return OperatorAgentReportResolver.parseCatalog((Map<String, Object>) map);
            }
        }
        return List.of();
    }
}
