package com.ispf.server.ai.agent;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
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
