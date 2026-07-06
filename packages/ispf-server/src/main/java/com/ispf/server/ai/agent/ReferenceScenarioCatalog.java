package com.ispf.server.ai.agent;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * BL-108: reference spec→validate→deploy scenarios for regression tests and AI Studio help.
 */
public final class ReferenceScenarioCatalog {

    private static final String CATALOG_RESOURCE = "agent-scenarios/catalog.json";
    private static volatile List<ReferenceScenario> cached;

    private ReferenceScenarioCatalog() {
    }

    public record ReferenceScenario(
            String id,
            String title,
            String prompt,
            String assignmentType,
            String planGoal,
            List<String> planSteps,
            List<String> validateTools
    ) {
        public Map<String, Object> planFinishResult() {
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("goal", planGoal);
            plan.put("steps", planSteps);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("phase", "plan");
            result.put("interactive", true);
            result.put("plan", plan);
            result.put("suggestions", List.of(Map.of(
                    "label", "Утвердить полный план",
                    "message", "Утверждаю план, начинай выполнение",
                    "primary", true
            )));
            return result;
        }

        public Map<String, Object> toHelpEntry() {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("title", title);
            entry.put("prompt", prompt);
            entry.put("assignmentType", assignmentType);
            entry.put("planSteps", planSteps);
            return entry;
        }
    }

    public static List<ReferenceScenario> all() {
        List<ReferenceScenario> local = cached;
        if (local != null) {
            return local;
        }
        synchronized (ReferenceScenarioCatalog.class) {
            if (cached != null) {
                return cached;
            }
            cached = loadCatalog();
            return cached;
        }
    }

    public static Optional<ReferenceScenario> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return all().stream().filter(item -> item.id().equals(normalized)).findFirst();
    }

    public static List<Map<String, Object>> helpEntries() {
        return all().stream().map(ReferenceScenario::toHelpEntry).toList();
    }

    /**
     * Best-effort match of a user prompt to a reference scenario (all catalog entries, not SNMP-only).
     */
    public static Optional<ReferenceScenario> matchBest(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        ReferenceScenario best = null;
        int bestScore = 0;
        for (ReferenceScenario scenario : all()) {
            int score = scoreScenario(text, scenario);
            if (score > bestScore) {
                bestScore = score;
                best = scenario;
            }
        }
        return bestScore >= 2 ? Optional.ofNullable(best) : Optional.empty();
    }

    private static int scoreScenario(String text, ReferenceScenario scenario) {
        int score = 0;
        for (String token : scenarioKeywords(scenario)) {
            if (text.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private static List<String> scenarioKeywords(ReferenceScenario scenario) {
        List<String> keywords = new ArrayList<>();
        addTokens(keywords, scenario.id().replace('-', ' '));
        addTokens(keywords, scenario.prompt());
        addTokens(keywords, scenario.title());
        addTokens(keywords, scenario.planGoal());
        switch (scenario.id()) {
            case "snmp-monitoring-lab" -> keywords.addAll(List.of(
                    "snmp", "localhost", "161", "мониторинг", "monitoring", "cpu", "ram", "метрик"));
            case "virtual-device-lab" -> keywords.addAll(List.of(
                    "virtual", "насос", "pump", "lab-pump", "давлен", "pressure"));
            case "mes-bundle-deploy" -> keywords.addAll(List.of(
                    "mes", "mes-reference", "bundle", "orders", "разверни"));
            case "pump-station-scada" -> keywords.addAll(List.of(
                    "насосн", "pump station", "scada", "мимик", "мнемо", "hmi"));
            case "workflow-hydro-impact" -> keywords.addAll(List.of(
                    "workflow", "bpmn", "гидроудар", "hydro"));
            case "alert-automation" -> keywords.addAll(List.of(
                    "alert", "алерт", "pressure", "давлен", "threshold"));
            case "dashboard-monitoring" -> keywords.addAll(List.of(
                    "dashboard", "дашборд", "overview", "devices"));
            case "bundle-validate-dry-run" -> keywords.addAll(List.of(
                    "validate", "dry", "провер", "без деплоя"));
            case "operator-report-readonly" -> keywords.addAll(List.of(
                    "report", "отчёт", "отчет", "yarg", "kpi", "опэ"));
            case "tree-function-deploy" -> keywords.addAll(List.of(
                    "modbus", "function", "tree function", "calc-kpi", "skeleton"));
            default -> {
            }
        }
        return keywords.stream()
                .map(token -> token.toLowerCase(Locale.ROOT).trim())
                .filter(token -> token.length() >= 3)
                .distinct()
                .toList();
    }

    private static void addTokens(List<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String token : raw.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}_-]+")) {
            if (!token.isBlank()) {
                target.add(token);
            }
        }
    }

    private static List<ReferenceScenario> loadCatalog() {
        try (InputStream stream = ReferenceScenarioCatalog.class.getClassLoader().getResourceAsStream(CATALOG_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing classpath resource: " + CATALOG_RESOURCE);
            }
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> raw = mapper.readValue(stream, new TypeReference<>() {});
            return raw.stream().map(ReferenceScenarioCatalog::toScenario).toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load agent scenario catalog", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static ReferenceScenario toScenario(Map<String, Object> raw) {
        return new ReferenceScenario(
                String.valueOf(raw.get("id")),
                String.valueOf(raw.get("title")),
                String.valueOf(raw.get("prompt")),
                String.valueOf(raw.get("assignmentType")),
                String.valueOf(raw.get("planGoal")),
                (List<String>) raw.get("planSteps"),
                (List<String>) raw.get("validateTools")
        );
    }
}
