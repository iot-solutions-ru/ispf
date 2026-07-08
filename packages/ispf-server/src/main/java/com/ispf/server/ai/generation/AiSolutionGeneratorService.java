package com.ispf.server.ai.generation;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BL-179 stub: keyword-driven blueprint draft from a natural-language prompt (no LLM).
 */
@Service
public class AiSolutionGeneratorService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    public Map<String, Object> generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        String domain = detectDomain(prompt);
        DomainTemplate template = templateFor(domain);
        Map<String, Object> blueprintDraft = buildBlueprintDraft(prompt, domain, template);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("mode", "stub");
        result.put("domain", domain);
        result.put("blueprintDraft", blueprintDraft);
        result.put("playbook", "AgentSolutionGeneratorPlaybook");
        return result;
    }

    private static String detectDomain(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (containsKeyword(lower, "mes", "dispatch", "oee", "work order", "manufacturing", "shopfloor")) {
            return "mes";
        }
        if (containsKeyword(lower, "hvac", "building", "comfort", "zone", "ahu", "chiller", "setpoint")) {
            return "hvac";
        }
        if (containsKeyword(lower, "scada", "pump", "tank", "mimic", "plc", "modbus", "snmp", "historian")) {
            return "scada";
        }
        return "scada";
    }

    private static boolean containsKeyword(String lower, String... keywords) {
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static DomainTemplate templateFor(String domain) {
        return switch (domain) {
            case "mes" -> new DomainTemplate(
                    "MES dispatch and OEE",
                    "examples/mes-reference/bundle.json",
                    "mes-reference",
                    List.of(
                            entity("production-line", "Production line", "CUSTOM"),
                            entity("loading-rack", "Loading rack device", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.mes-overview"),
                    List.of("mes-high-downtime"),
                    List.of()
            );
            case "hvac" -> new DomainTemplate(
                    "Building HVAC comfort",
                    "examples/building-hvac-app/bundle.json",
                    "building-hvac",
                    List.of(
                            entity("floor-zone-hub", "Floor zone hub", "CUSTOM"),
                            entity("ahu-01", "Air handling unit", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.hvac-comfort"),
                    List.of("hvac-zone-comfort-deviation"),
                    List.of()
            );
            default -> new DomainTemplate(
                    "SCADA facility overview",
                    "examples/simulator-profiles/bundle.json",
                    "simulator",
                    List.of(
                            entity("process-area", "Process area", "CUSTOM"),
                            entity("pump-01", "Transfer pump", "DEVICE"),
                            entity("flow-meter-01", "Flow meter", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.facility-overview"),
                    List.of("scada-high-pressure"),
                    List.of("root.platform.mimics.facility-overview")
            );
        };
    }

    private static Map<String, Object> buildBlueprintDraft(String prompt, String domain, DomainTemplate template) {
        String slug = slugFromPrompt(prompt, domain);
        if (!ID_PATTERN.matcher(slug).matches()) {
            slug = domain + "-draft";
        }

        Map<String, Object> specBrief = new LinkedHashMap<>();
        specBrief.put("title", template.title());
        specBrief.put("businessGoal", truncate(prompt.trim(), 240));
        specBrief.put("entities", template.entities());
        specBrief.put("functionalRequirements", List.of(
                functionalRequirement("FR-1", "Provision tree structure for " + template.title()),
                functionalRequirement("FR-2", "Bind operator dashboards to live variables"),
                functionalRequirement("FR-3", "Configure alert thresholds from spec keywords")
        ));

        Map<String, Object> suggestedArtifacts = new LinkedHashMap<>();
        suggestedArtifacts.put("rootFolder", "root.platform." + slug);
        suggestedArtifacts.put("devices", template.entities().stream()
                .filter(entity -> "DEVICE".equals(entity.get("type")))
                .map(entity -> "root.platform.devices." + entity.get("name"))
                .toList());
        suggestedArtifacts.put("dashboards", template.dashboards());
        suggestedArtifacts.put("alerts", template.alerts());
        suggestedArtifacts.put("mimics", template.mimics());

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("appId", template.bundleAppId());
        bundle.put("manifestPath", template.bundleManifestPath());

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", slug);
        draft.put("title", template.title());
        draft.put("domain", domain);
        draft.put("specBrief", specBrief);
        draft.put("suggestedArtifacts", suggestedArtifacts);
        draft.put("referenceBundle", bundle);
        draft.put("nextSteps", List.of(
                "create_object CUSTOM folder from specBrief.entities[0]",
                "create_virtual_device for each DEVICE entity",
                "set_dashboard_layout with list_variables bindings",
                "configure_alert for threshold alarms",
                "validate_bundle before import_package"
        ));
        return draft;
    }

    private static Map<String, Object> entity(String name, String displayName, String type) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("name", name);
        entity.put("displayName", displayName);
        entity.put("type", type);
        return entity;
    }

    private static Map<String, Object> functionalRequirement(String id, String description) {
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("id", id);
        requirement.put("description", description);
        return requirement;
    }

    private static String slugFromPrompt(String prompt, String domain) {
        String normalized = prompt.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return domain + "-draft";
        }
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48).replaceAll("-+$", "");
        }
        return normalized;
    }

    private static String truncate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }

    private record DomainTemplate(
            String title,
            String bundleManifestPath,
            String bundleAppId,
            List<Map<String, Object>> entities,
            List<String> dashboards,
            List<String> alerts,
            List<String> mimics
    ) {
    }
}
