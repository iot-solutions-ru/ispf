package com.ispf.server.ai.generation;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BL-179/BL-180: keyword-driven blueprint draft from a natural-language prompt (no LLM).
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
        String slug = slugFromPrompt(prompt, domain);
        if (!ID_PATTERN.matcher(slug).matches()) {
            slug = domain + "-draft";
        }

        Map<String, Object> bundleDraft = buildBundleDraft(slug, domain, template);
        Map<String, Object> blueprintDraft = buildBlueprintDraft(prompt, domain, template, slug, bundleDraft);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("mode", "stub");
        result.put("domain", domain);
        result.put("bundleDraft", bundleDraft);
        result.put("blueprintDraft", blueprintDraft);
        result.put("playbook", "AgentSolutionGeneratorPlaybook");
        return result;
    }

    private static String detectDomain(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (containsKeyword(lower, "mes", "dispatch", "oee", "work order", "manufacturing", "shopfloor")) {
            return "mes";
        }
        if (containsKeyword(lower, "scada", "mimic", "historian", "plc", "мнемо", "мимик")) {
            return "scada";
        }
        if (containsKeyword(lower, "oil", "gas", "neft", "refinery", "upstream", "downstream",
                "tank farm", "pump station", "нефтебаз", "nps", "oil-gas")) {
            return "oil-gas";
        }
        if (containsKeyword(lower, "water", "wastewater", "treatment plant", "desalination",
                "irrigation", "flood", "utility water", "водоканал")) {
            return "water";
        }
        if (containsKeyword(lower, "energy", "power plant", "thermal", "turbine", "boiler",
                "kwh", "substation", "grid", "generation", "тэц", "mini-tec")) {
            return "energy";
        }
        if (containsKeyword(lower, "hvac", "comfort", "zone", "ahu", "chiller", "setpoint")) {
            return "hvac";
        }
        if (containsKeyword(lower, "smart building", "bms", "facility management", "lighting",
                "access control", "elevator", "campus", "building automation")) {
            return "building";
        }
        if (containsKeyword(lower, "warehouse", "logistics", "cold chain", "inventory", "fulfillment")) {
            return "warehouse";
        }
        if (containsKeyword(lower, "lab", "training", "virtual cluster", "simulator profile", "snmp lab")) {
            return "lab";
        }
        if (containsKeyword(lower, "pipeline", "linepack", "midstream", "transmission line", "valve station")) {
            return "pipeline";
        }
        if (containsKeyword(lower, "pump", "tank", "modbus", "snmp")) {
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
                    List.of(),
                    "mes_dispatch_order",
                    "mes_listOrders"
            );
            case "oil-gas" -> new DomainTemplate(
                    "Oil & gas facility overview",
                    "examples/simulator-profiles/bundle.json",
                    "simulator",
                    List.of(
                            entity("process-area", "Process area", "CUSTOM"),
                            entity("pump-station", "Transfer pump station", "DEVICE"),
                            entity("tank-farm", "Storage tank", "DEVICE"),
                            entity("pressure-transmitter", "Line pressure transmitter", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.facility-overview"),
                    List.of("scada-high-pressure"),
                    List.of("root.platform.mimics.facility-overview"),
                    "scada_process_reading",
                    "scada_listReadings"
            );
            case "water" -> new DomainTemplate(
                    "Water treatment plant",
                    "examples/simulator-profiles/bundle.json",
                    "simulator",
                    List.of(
                            entity("treatment-plant", "Treatment plant", "CUSTOM"),
                            entity("intake-pump", "Intake pump", "DEVICE"),
                            entity("clarifier", "Clarifier basin", "DEVICE"),
                            entity("flow-meter", "Effluent flow meter", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.facility-overview"),
                    List.of("scada-high-pressure"),
                    List.of("root.platform.mimics.facility-overview"),
                    "water_flow_reading",
                    "water_listReadings"
            );
            case "energy" -> new DomainTemplate(
                    "Thermal power generation",
                    "examples/mini-tec/bundle.json",
                    "mini-tec",
                    List.of(
                            entity("generation-unit", "Generation unit", "CUSTOM"),
                            entity("boiler-01", "Boiler aggregate", "DEVICE"),
                            entity("turbine-01", "Steam turbine", "DEVICE"),
                            entity("substation-feeder", "Substation feeder", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.mini-tec-overview"),
                    List.of("tec-high-load"),
                    List.of("root.platform.mimics.mini-tec-single-line"),
                    "tec_generation_reading",
                    "tec_listReadings"
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
                    List.of(),
                    "hvac_zone",
                    "hvac_listZones"
            );
            case "building" -> new DomainTemplate(
                    "Smart building operations",
                    "examples/building-hvac-app/bundle.json",
                    "building-hvac",
                    List.of(
                            entity("building-hub", "Building operations hub", "CUSTOM"),
                            entity("lighting-panel", "Lighting panel", "DEVICE"),
                            entity("access-controller", "Access controller", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.hvac-comfort"),
                    List.of("hvac-zone-comfort-deviation"),
                    List.of(),
                    "building_zone",
                    "building_listZones"
            );
            case "warehouse" -> new DomainTemplate(
                    "Warehouse logistics overview",
                    "examples/warehouse-app/bundle.json",
                    "warehouse",
                    List.of(
                            entity("storage-zone", "Storage zone", "CUSTOM"),
                            entity("conveyor-01", "Conveyor line", "DEVICE"),
                            entity("rfid-gate", "RFID gate reader", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.warehouse-overview"),
                    List.of("warehouse-zone-blocked"),
                    List.of(),
                    "warehouse_lane",
                    "warehouse_listLanes"
            );
            case "lab" -> new DomainTemplate(
                    "Lab training environment",
                    "examples/lab-training/bundle.json",
                    "lab-training",
                    List.of(
                            entity("lab-bench", "Lab bench", "CUSTOM"),
                            entity("training-device", "Training device", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.lab-overview"),
                    List.of("lab-sum-range"),
                    List.of(),
                    "lab_reading",
                    "lab_listReadings"
            );
            case "pipeline" -> new DomainTemplate(
                    "Pipeline SCADA segment",
                    "examples/simulator-profiles/bundle.json",
                    "simulator",
                    List.of(
                            entity("pipeline-segment", "Pipeline segment", "CUSTOM"),
                            entity("valve-station", "Valve station", "DEVICE"),
                            entity("pressure-sensor", "Segment pressure sensor", "DEVICE")
                    ),
                    List.of("root.platform.dashboards.facility-overview"),
                    List.of("scada-high-pressure"),
                    List.of("root.platform.mimics.facility-overview"),
                    "pipeline_pressure_reading",
                    "pipeline_listReadings"
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
                    List.of("root.platform.mimics.facility-overview"),
                    "scada_process_reading",
                    "scada_listReadings"
            );
        };
    }

    private static Map<String, Object> buildBlueprintDraft(
            String prompt,
            String domain,
            DomainTemplate template,
            String slug,
            Map<String, Object> bundleDraft
    ) {
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

        Map<String, Object> referenceBundle = new LinkedHashMap<>();
        referenceBundle.put("appId", template.bundleAppId());
        referenceBundle.put("manifestPath", template.bundleManifestPath());

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("id", slug);
        draft.put("title", template.title());
        draft.put("domain", domain);
        draft.put("specBrief", specBrief);
        draft.put("suggestedArtifacts", suggestedArtifacts);
        draft.put("bundleDraft", bundleDraft);
        draft.put("referenceBundle", referenceBundle);
        draft.put("nextSteps", List.of(
                "validate_bundle on bundleDraft before import_package",
                "create_object CUSTOM folder from specBrief.entities[0]",
                "create_virtual_device for each DEVICE entity",
                "set_dashboard_layout with list_variables bindings",
                "configure_alert for threshold alarms",
                "configure_operator_ui from bundleDraft.operatorUi"
        ));
        return draft;
    }

    private static Map<String, Object> buildBundleDraft(String slug, String domain, DomainTemplate template) {
        String hubName = slug + "-hub";
        String hubPath = "root.platform.devices." + hubName;
        String schemaName = "app_" + slug.replace('-', '_');
        String overviewDashboard = "root.platform.dashboards." + slug + "-overview";

        List<Map<String, Object>> objects = new ArrayList<>();
        for (Map<String, Object> entity : template.entities()) {
            if ("DEVICE".equals(entity.get("type"))) {
                objects.add(objectNode(
                        "root.platform.devices",
                        String.valueOf(entity.get("name")),
                        "DEVICE",
                        String.valueOf(entity.get("displayName"))
                ));
            }
        }
        objects.add(objectNode(
                "root.platform.devices",
                hubName,
                "DEVICE",
                template.title() + " hub"
        ));

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("version", "1.0.0");
        draft.put("displayName", template.title() + " (draft)");
        draft.put("tablePrefix", "");
        draft.put("schemaName", schemaName);
        draft.put("metadata", Map.of("domain", domain, "generatedFrom", "AiSolutionGeneratorService"));
        draft.put("migrations", List.of(migration(
                slug.replace('-', '_') + "_schema",
                "CREATE TABLE IF NOT EXISTS " + template.tableName() + " ("
                        + "id UUID PRIMARY KEY, code VARCHAR(64) NOT NULL, label VARCHAR(128) NOT NULL);"
        )));
        draft.put("objects", objects);
        draft.put("functions", List.of(listFunction(hubPath, template.listFunctionName(), template.tableName())));
        draft.put("dashboards", List.of(dashboard(
                overviewDashboard,
                template.title() + " overview",
                hubPath,
                template.listFunctionName()
        )));
        draft.put("operatorUi", operatorUi(slug, template.title(), overviewDashboard, hubPath));
        return draft;
    }

    private static Map<String, Object> migration(String id, String sql) {
        Map<String, Object> migration = new LinkedHashMap<>();
        migration.put("id", id);
        migration.put("sql", sql);
        return migration;
    }

    private static Map<String, Object> objectNode(
            String parentPath,
            String name,
            String type,
            String displayName
    ) {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("parentPath", parentPath);
        object.put("name", name);
        object.put("type", type);
        object.put("displayName", displayName);
        return object;
    }

    private static Map<String, Object> listFunction(String objectPath, String functionName, String tableName) {
        String body = "{\"steps\":["
                + "{\"type\":\"selectMany\",\"var\":\"rows\",\"sql\":\"SELECT code, label FROM " + tableName
                + " ORDER BY code\"},"
                + "{\"type\":\"return\",\"fields\":{\"error_code\":\"OK\",\"error_message\":\"\",\"rows\":\"${rows}\"}}"
                + "]}";
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "script");
        source.put("body", body);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("objectPath", objectPath);
        function.put("functionName", functionName);
        function.put("version", "1");
        function.put("descriptor", Map.of(
                "inputSchema", Map.of("name", "in", "fields", List.of()),
                "outputSchema", Map.of(
                        "name", "out",
                        "fields", List.of(
                                Map.of("name", "error_code", "type", "STRING"),
                                Map.of("name", "error_message", "type", "STRING"),
                                Map.of("name", "rows", "type", "RECORD_LIST")
                        )
                )
        ));
        function.put("source", source);
        return function;
    }

    private static Map<String, Object> dashboard(
            String path,
            String title,
            String hubPath,
            String functionName
    ) {
        String layoutJson = "{\"columns\":12,\"rowHeight\":72,\"widgets\":[{\"id\":\"overview-list\","
                + "\"type\":\"function\",\"title\":\"Overview\",\"x\":0,\"y\":0,\"w\":12,\"h\":4,"
                + "\"objectPath\":\"" + hubPath + "\",\"functionName\":\"" + functionName
                + "\",\"buttonLabel\":\"Refresh\"}]}";
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("path", path);
        dashboard.put("title", title);
        dashboard.put("refreshIntervalMs", 5000);
        dashboard.put("layoutJson", layoutJson);
        return dashboard;
    }

    private static Map<String, Object> operatorUi(
            String appId,
            String title,
            String defaultDashboard,
            String eventJournalObjectPath
    ) {
        Map<String, Object> operatorUi = new LinkedHashMap<>();
        operatorUi.put("appId", appId);
        operatorUi.put("title", title);
        operatorUi.put("defaultDashboard", defaultDashboard);
        operatorUi.put("dashboards", List.of(Map.of(
                "path", defaultDashboard,
                "title", title + " overview"
        )));
        operatorUi.put("eventJournalObjectPath", eventJournalObjectPath);
        return operatorUi;
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
            List<String> mimics,
            String tableName,
            String listFunctionName
    ) {
    }
}
