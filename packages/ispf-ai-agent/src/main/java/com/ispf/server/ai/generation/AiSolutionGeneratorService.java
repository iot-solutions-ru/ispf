package com.ispf.server.ai.generation;

import com.ispf.ai.LlmMessage;
import com.ispf.ai.LlmRequest;
import com.ispf.ai.LlmResponse;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.ai.llm.LlmProviderRegistry;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.bundle.BundleManifestJsonSupport;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.config.AiProperties;
import com.ispf.server.object.ObjectTreePort;
import com.ispf.server.operator.OperatorAppUiService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BL-180: plant description в†’ blueprint draft and optional live apply (tree + dashboards + alerts).
 * Keyword / {@code DOMAIN_CATALOG} routing remains {@code mode=draft} offline fallback only.
 * Live apply ({@code apply=true}) composes platform primitives from an LLM plant spec
 * ({@code composition=primitives}) вЂ” not a vertical catalog template.
 */
@Service
public class AiSolutionGeneratorService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");
    private static final String COMPOSITION_PRIMITIVES = "primitives";
    private static final String COMPOSITION_CATALOG = "catalog-template";

    private static final List<Map<String, String>> DOMAIN_CATALOG = List.of(
            Map.of("domain", "mes", "appId", "mes-reference", "hint", "manufacturing MES OEE work orders"),
            Map.of("domain", "oil-gas", "appId", "simulator", "hint", "oil gas tank farm pump station"),
            Map.of("domain", "water", "appId", "simulator", "hint", "water wastewater treatment"),
            Map.of("domain", "energy", "appId", "mini-tec", "hint", "thermal power generation turbine"),
            Map.of("domain", "hvac", "appId", "building-hvac", "hint", "HVAC AHU comfort zones"),
            Map.of("domain", "building", "appId", "building-hvac", "hint", "smart building BMS lighting"),
            Map.of("domain", "warehouse", "appId", "warehouse", "hint", "warehouse logistics cold chain"),
            Map.of("domain", "lab", "appId", "lab-training", "hint", "lab training simulator"),
            Map.of("domain", "pipeline", "appId", "simulator", "hint", "pipeline midstream valve"),
            Map.of("domain", "scada", "appId", "simulator", "hint", "SCADA mimic historian pumps")
    );

    private final LlmProviderRegistry llmProviderRegistry;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationBundleDeployService bundleDeployService;
    private final OperatorAppUiService operatorAppUiService;
    private final ObjectTreePort ObjectTreePort;
    private final AutomationTreeService automationTreeService;

    public AiSolutionGeneratorService(
            LlmProviderRegistry llmProviderRegistry,
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppUiService operatorAppUiService,
            ObjectTreePort ObjectTreePort,
            AutomationTreeService automationTreeService
    ) {
        this.llmProviderRegistry = llmProviderRegistry;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.bundleDeployService = bundleDeployService;
        this.operatorAppUiService = operatorAppUiService;
        this.ObjectTreePort = ObjectTreePort;
        this.automationTreeService = automationTreeService;
    }

    /** Draft only (keyword fallback when LLM unavailable). */
    public Map<String, Object> generate(String prompt) {
        return generate(prompt, false, "system");
    }

    /**
     * @param apply when true, deploy tree/dashboards/operator UI and configure an alert (requires LLM)
     */
    public Map<String, Object> generate(String prompt, boolean apply, String actor) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        if (apply && !llmProviderRegistry.isGenerationAvailable()) {
            throw new IllegalStateException(
                    "Live solution apply requires a configured LLM. Set ispf.ai.enabled + provider/base-url/model/api-key."
            );
        }

        if (apply) {
            return generateLivePrimitives(prompt, actor);
        }

        DomainSelection selection = selectDomain(prompt, false);
        DomainTemplate template = templateFor(selection.domain());
        String slug = slugFromPrompt(prompt, selection.domain());
        if (!ID_PATTERN.matcher(slug).matches()) {
            slug = selection.domain() + "-draft";
        }

        Map<String, Object> bundleDraft = buildBundleDraft(slug, selection.domain(), template);
        Map<String, Object> blueprintDraft = buildBlueprintDraft(prompt, selection.domain(), template, slug, bundleDraft);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("mode", selection.mode());
        result.put("composition", COMPOSITION_CATALOG);
        result.put("domain", selection.domain());
        result.put("domainSelection", selection.source());
        result.put("bundleDraft", bundleDraft);
        result.put("blueprintDraft", blueprintDraft);
        result.put("playbook", "AgentSolutionGeneratorPlaybook");
        if (selection.llmPreview() != null) {
            result.put("llmPreview", selection.llmPreview());
        }
        return result;
    }

    private Map<String, Object> generateLivePrimitives(String prompt, String actor) {
        PlantSpec spec = composePlantSpecViaLlm(prompt);
        DomainTemplate template = templateFromPlantSpec(spec);
        String domain = "platform";
        String slug = spec.slug() != null && !spec.slug().isBlank()
                ? spec.slug()
                : slugFromPrompt(prompt, domain);
        slug = uniqueApplySlug(slug, domain);
        if (!ID_PATTERN.matcher(slug).matches()) {
            slug = "plant-live-" + Long.toString(System.currentTimeMillis() % 1_000_000, 36);
        }

        Map<String, Object> bundleDraft = buildBundleDraft(slug, domain, template);
        Map<String, Object> blueprintDraft = buildBlueprintDraft(prompt, domain, template, slug, bundleDraft);
        Map<String, Object> applied = applyLive(slug, domain, template, bundleDraft, actor);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("mode", "live");
        result.put("composition", COMPOSITION_PRIMITIVES);
        result.put("domain", domain);
        result.put("domainSelection", "llm-plant-spec");
        result.put("bundleDraft", bundleDraft);
        result.put("blueprintDraft", blueprintDraft);
        result.put("playbook", "AgentSolutionGeneratorPlaybook");
        if (spec.llmPreview() != null) {
            result.put("llmPreview", spec.llmPreview());
        }
        result.putAll(applied);
        return result;
    }

    private PlantSpec composePlantSpecViaLlm(String prompt) {
        try {
            String system = """
                    You extract a minimal ISPF platform plant spec from a free-form description.
                    Reply with ONLY a JSON object (no markdown):
                    {
                      "title":"<short plant title>",
                      "slug":"<optional kebab-case id>",
                      "devices":[{"name":"<kebab-case>","displayName":"<label>"}]
                    }
                    Rules:
                    - 1..6 DEVICE entries that should appear under root.platform.devices (besides the hub).
                    - name: lowercase kebab-case [a-z0-9-], no spaces.
                    - Do NOT pick industry catalog ids or reference bundle appIds.
                    - Prefer concrete equipment from the prompt; if vague, invent 2 generic devices.
                    """;
            LlmResponse response = llmProviderRegistry.complete(new LlmRequest(
                    aiProperties.getModel(),
                    List.of(
                            new LlmMessage("system", system),
                            new LlmMessage("user", prompt)
                    ),
                    Math.min(512, aiProperties.getMaxTokens()),
                    0.0
            ));
            String content = response.content() != null ? response.content().trim() : "";
            Matcher matcher = JSON_OBJECT.matcher(content);
            if (!matcher.find()) {
                throw new IllegalStateException("LLM plant spec missing JSON object");
            }
            JsonNode node = objectMapper.readTree(matcher.group());
            String title = textOrEmpty(node, "title");
            if (title.isBlank()) {
                title = "Generated plant";
            }
            String slug = textOrEmpty(node, "slug").toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9-]+", "-")
                    .replaceAll("^-+|-+$", "");
            List<Map<String, Object>> devices = new ArrayList<>();
            JsonNode devicesNode = node.get("devices");
            if (devicesNode != null && devicesNode.isArray()) {
                for (JsonNode device : devicesNode) {
                    String name = textOrEmpty(device, "name").toLowerCase(Locale.ROOT)
                            .replaceAll("[^a-z0-9-]+", "-")
                            .replaceAll("^-+|-+$", "");
                    String displayName = textOrEmpty(device, "displayName");
                    if (name.isBlank()) {
                        continue;
                    }
                    if (displayName.isBlank()) {
                        displayName = name;
                    }
                    devices.add(entity(name, displayName, "DEVICE"));
                    if (devices.size() >= 6) {
                        break;
                    }
                }
            }
            if (devices.isEmpty()) {
                devices.add(entity("device-01", "Primary device", "DEVICE"));
                devices.add(entity("device-02", "Secondary device", "DEVICE"));
            }
            devices.add(0, entity("process-area", title + " area", "CUSTOM"));
            return new PlantSpec(title, slug.isBlank() ? null : slug, devices, truncate(content, 500));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("LLM plant spec failed: " + ex.getMessage(), ex);
        }
    }

    private static DomainTemplate templateFromPlantSpec(PlantSpec spec) {
        return new DomainTemplate(
                spec.title(),
                "platform-primitives",
                "platform",
                spec.entities(),
                List.of(),
                List.of(),
                List.of(),
                "platform_plant_item",
                "platform_listItems"
        );
    }

    private Map<String, Object> applyLive(
            String slug,
            String domain,
            DomainTemplate template,
            Map<String, Object> bundleDraft,
            String actor
    ) {
        String hubPath = "root.platform.devices." + slug + "-hub";
        var parsed = BundleManifestJsonSupport.parse(objectMapper, bundleDraft);
        Map<String, Object> deployResult = bundleDeployService.deploy(slug, parsed);

        ensureMonitorVariable(hubPath);
        String alertName = slug + "-monitor";
        String alertPath = AutomationTreeService.rulePathForName(alertName);
        if (ObjectTreePort.tree().findByPath(alertPath).isEmpty()) {
            automationTreeService.createAlertRule(
                    alertName,
                    hubPath,
                    "status",
                    "self.status[\"value\"] > 90",
                    "solutionGeneratedAlarm",
                    "status",
                    true,
                    true,
                    0,
                    false,
                    "HIGH",
                    false,
                    null,
                    null,
                    null
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> operatorUi = (Map<String, Object>) bundleDraft.get("operatorUi");
        String title = String.valueOf(operatorUi.getOrDefault("title", template.title()));
        String defaultDashboard = String.valueOf(operatorUi.get("defaultDashboard"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> dashboards = ((List<Map<String, Object>>) operatorUi.get("dashboards")).stream()
                .map(row -> Map.of(
                        "path", String.valueOf(row.get("path")),
                        "title", String.valueOf(row.getOrDefault("title", title))
                ))
                .toList();
        try {
            operatorAppUiService.saveUi(slug, title, defaultDashboard, dashboards, null, null);
        } catch (Exception ex) {
            throw new IllegalStateException("operator UI save failed: " + ex.getMessage(), ex);
        }

        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("appId", slug);
        applied.put("hubPath", hubPath);
        applied.put("alertPath", alertPath);
        applied.put("dashboardPath", defaultDashboard);
        applied.put("operatorUrlHint", "?mode=operator&app=" + slug);
        applied.put("deploy", deployResult);
        applied.put("actor", actor != null ? actor : "system");
        applied.put("domain", domain);
        return applied;
    }

    private void ensureMonitorVariable(String hubPath) {
        if (ObjectTreePort.tree().findByPath(hubPath).isEmpty()) {
            throw new IllegalStateException("hub device missing after deploy: " + hubPath);
        }
        if (ObjectTreePort.require(hubPath).getVariable("status").isPresent()) {
            return;
        }
        DataSchema schema = DataSchema.builder("status").field("value", FieldType.DOUBLE).build();
        ObjectTreePort.createVariable(
                hubPath,
                "status",
                schema,
                true,
                true,
                DataRecord.single(schema, Map.of("value", 0.0)),
                false,
                null
        );
    }

    private DomainSelection selectDomain(String prompt, boolean requireLlm) {
        if (llmProviderRegistry.isGenerationAvailable()) {
            try {
                DomainSelection llm = selectDomainViaLlm(prompt);
                if (llm != null) {
                    return llm;
                }
            } catch (Exception ex) {
                if (requireLlm) {
                    throw new IllegalStateException("LLM domain selection failed: " + ex.getMessage(), ex);
                }
            }
        }
        if (requireLlm) {
            throw new IllegalStateException("LLM domain selection required for live apply");
        }
        return new DomainSelection(detectDomain(prompt), "draft", "keyword", null);
    }

    private DomainSelection selectDomainViaLlm(String prompt) throws Exception {
        String catalogJson = objectMapper.writeValueAsString(DOMAIN_CATALOG);
        String system = """
                You classify industrial plant descriptions for ISPF solution generator.
                Reply with ONLY a JSON object: {"domain":"<id>","appId":"<id>"}.
                Pick the best matching entry from this catalog:
                %s
                """.formatted(catalogJson);
        LlmResponse response = llmProviderRegistry.complete(new LlmRequest(
                aiProperties.getModel(),
                List.of(
                        new LlmMessage("system", system),
                        new LlmMessage("user", prompt)
                ),
                Math.min(256, aiProperties.getMaxTokens()),
                0.0
        ));
        String content = response.content() != null ? response.content().trim() : "";
        Matcher matcher = JSON_OBJECT.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        JsonNode node = objectMapper.readTree(matcher.group());
        String domain = textOrEmpty(node, "domain").toLowerCase(Locale.ROOT);
        if (domain.isBlank() || DOMAIN_CATALOG.stream().noneMatch(row -> domain.equals(row.get("domain")))) {
            return null;
        }
        return new DomainSelection(domain, "llm", "llm", truncate(content, 500));
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private static String uniqueApplySlug(String base, String domain) {
        String prefix = base;
        if (prefix.length() > 36) {
            prefix = prefix.substring(0, 36).replaceAll("-+$", "");
        }
        String suffix = Long.toString(System.currentTimeMillis() % 1_000_000, 36);
        String combined = prefix + "-" + suffix;
        if (!ID_PATTERN.matcher(combined).matches()) {
            return domain + "-live-" + suffix;
        }
        return combined;
    }

    static String detectDomain(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (containsKeyword(lower, "mes", "dispatch", "oee", "work order", "manufacturing", "shopfloor")) {
            return "mes";
        }
        if (containsKeyword(lower, "scada", "mimic", "historian", "plc", "РјРЅРµРјРѕ", "РјРёРјРёРє")) {
            return "scada";
        }
        if (containsKeyword(lower, "oil", "gas", "neft", "refinery", "upstream", "downstream",
                "tank farm", "pump station", "РЅРµС„С‚РµР±Р°Р·", "nps", "oil-gas")) {
            return "oil-gas";
        }
        if (containsKeyword(lower, "water", "wastewater", "treatment plant", "desalination",
                "irrigation", "flood", "utility water", "РІРѕРґРѕРєР°РЅР°Р»")) {
            return "water";
        }
        if (containsKeyword(lower, "energy", "power plant", "thermal", "turbine", "boiler",
                "kwh", "substation", "grid", "generation", "С‚СЌС†", "mini-tec")) {
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
                functionalRequirement("FR-3", "Configure alert thresholds from plant description")
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
                "POST /api/v1/ai/solutions/generate with apply=true for live tree+dashboards+alerts",
                "or validate_bundle on bundleDraft before import_package",
                "configure_alert already applied on live mode hub status variable"
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
                String deviceName = slug + "-" + entity.get("name");
                objects.add(objectNode(
                        "root.platform.devices",
                        deviceName,
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
                                Map.of(
                                        "name", "rows",
                                        "type", "RECORD_LIST",
                                        "nestedSchema", Map.of(
                                                "name", "row",
                                                "fields", List.of(
                                                        Map.of("name", "code", "type", "STRING"),
                                                        Map.of("name", "label", "type", "STRING")
                                                )
                                        )
                                )
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
        String layoutJson = "{\"columns\":84,\"rowHeight\":8,\"widgets\":["
                + "{\"id\":\"overview-kpi\",\"type\":\"function\",\"title\":\"Overview\","
                + "\"x\":0,\"y\":0,\"w\":28,\"h\":14,"
                + "\"objectPath\":\"" + hubPath + "\",\"functionName\":\"" + functionName
                + "\",\"buttonLabel\":\"Refresh\"},"
                + "{\"id\":\"overview-help\",\"type\":\"html-snippet\",\"title\":\"Status\","
                + "\"x\":28,\"y\":0,\"w\":56,\"h\":14,"
                + "\"htmlJson\":\"<p>Refresh Hub overview. Open Operator HMI for the live board.</p>\"}"
                + "]}";
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

    private record DomainSelection(String domain, String mode, String source, String llmPreview) {
    }

    private record PlantSpec(
            String title,
            String slug,
            List<Map<String, Object>> entities,
            String llmPreview
    ) {
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
