package com.ispf.server.regression;

import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingRuleKind;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.binding.BindingTargetKind;
import com.ispf.core.binding.BindingVariableRef;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.test.FunctionTestRunner;
import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.binding.SqlBindingObjectService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.engine.AnalyticsEngineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unified end-to-end platform regression suite ("golden path") verifying that all core ISPF
 * engines work together without regressions while obeying P1-P10 and canon constraints:
 *
 * <ul>
 *   <li><b>SHAPE</b>: SINGLETON orchestrator hub ({@code != DEVICE}), INSTANCE digital twin, MIXIN
 *       attach/diff/detach, and DEVICE telemetry child</li>
 *   <li><b>SHIP</b>: strict ship-canon validation ({@code LOGIC_HOST_DEVICE} guard), dry-run,
 *       deploy, v1-&gt;v2 reconcile, W6 rollback-only {@code tests[]}, isolated SQL schema, script
 *       functions over {@code /api/v1/bff/invoke}, and {@code sqlBinding} refresh</li>
 *   <li><b>REACTIVE LOGIC</b>: CEL formal verification, cross-object {@code BindingRule}s on the
 *       SINGLETON hub, twin local rules, and {@code @dashboardContext} HMI visibility/mode rules</li>
 *   <li><b>HISTORIAN &amp; ANALYTICS</b>: sample persistence, changes-only deduplication, and
 *       windowed {@code HISTORIAN} tag evaluation writing derived KPIs back to the hub</li>
 *   <li><b>CLOSED-LOOP AUTOMATION</b>: {@code AlertRule} -&gt; Event Journal -&gt;
 *       {@code EventCorrelator} ({@code RUN_WORKFLOW}) -&gt; BPMN {@code serviceTask} +
 *       {@code userTask} -&gt; Operator {@code WorkQueue} claim/complete -&gt; hub resolution</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformGoldenPathRegressionTest {

    private static final String APP_ID = "golden-regression";
    private static final String SINGLETON_NAME = "golden-cell-hub-v1";
    private static final String SINGLETON_HUB_PATH = "root.platform.singleton-blueprints." + SINGLETON_NAME;
    private static final String INSTANCE_TYPE_NAME = "golden-unit-twin-v1";
    private static final String MIXIN_NAME = "golden-maintenance-mixin-v1";
    private static final String CELL_FOLDER = "root.platform.devices.golden-cell";
    private static final String TWIN_PATH = CELL_FOLDER + ".unit-01";
    private static final String SENSOR_DEVICE_PATH = CELL_FOLDER + ".sensor-01";
    private static final String DASHBOARD_PATH = "root.platform.dashboards.golden-cell-overview";
    private static final String WORKFLOW_PATH = "root.platform.workflows.golden-incident-handler";
    private static final String ALERT_RULE_NAME = "Golden cell overheat alert";
    private static final String CORRELATOR_NAME = "Golden cell overheat correlator";
    private static final Authentication ADMIN = UsernamePasswordAuthenticationToken.authenticated(
            "admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("admin"))
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingDependencyIndex dependencyIndex;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private AnalyticsEngineService analyticsEngineService;

    @Autowired
    private ApplicationBundleDeployService bundleDeployService;

    @AfterEach
    void cleanupArtifacts() throws Exception {
        deleteAlertRuleIfExists(ALERT_RULE_NAME);
        deleteCorrelatorIfExists(CORRELATOR_NAME);
        drainOpenWorkQueueForApp();
    }

    @Test
    void fullPlatformGoldenPathScenarioAcrossAllEngines() throws Exception {
        // =========================================================================
        // STAGE 1: SHAPE — SINGLETON hub + INSTANCE twin + MIXIN + DEVICE telemetry
        // =========================================================================
        String singletonId = ensureSingletonHubBlueprint();
        MvcResult singletonInstRes = mockMvc.perform(get("/api/v1/singleton-blueprints/{id}/instance", singletonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(SINGLETON_HUB_PATH))
                .andExpect(jsonPath("$.type").value(not("DEVICE")))
                .andReturn();
        JsonNode singletonNode = objectMapper.readTree(singletonInstRes.getResponse().getContentAsString());
        assertThat(singletonNode.path("path").asString()).isEqualTo(SINGLETON_HUB_PATH);
        assertThat(objectManager.require(SINGLETON_HUB_PATH).type()).isNotEqualTo(ObjectType.DEVICE);

        // SINGLETON cannot be instantiated via INSTANCE endpoint (strict role separation)
        mockMvc.perform(post("/api/v1/instance-types/{id}/instantiate", singletonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.devices",
                                  "instanceName": "illegal-copy",
                                  "parameters": {}
                                }
                                """))
                .andExpect(status().isNotFound());

        ensureFolder(CELL_FOLDER, "Golden Cell Folder");

        String instanceTypeId = ensureUnitTwinInstanceType();
        if (objectManager.tree().findByPath(TWIN_PATH).isEmpty()) {
            mockMvc.perform(post("/api/v1/instance-types/{id}/instantiate", instanceTypeId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "parentPath": "%s",
                                      "instanceName": "unit-01",
                                      "parameters": {}
                                    }
                                    """.formatted(CELL_FOLDER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.path").value(TWIN_PATH))
                    .andExpect(jsonPath("$.type").value("CUSTOM"));
        }
        assertThat(objectManager.require(TWIN_PATH).type()).isEqualTo(ObjectType.CUSTOM);

        // MIXIN attach -> diff -> detach on the digital twin
        String mixinId = ensureMaintenanceMixinBlueprint();
        mockMvc.perform(get("/api/v1/blueprints/{id}/diff", mixinId)
                        .param("objectPath", TWIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variablesToAdd", hasItem("maintenanceNote")));

        mockMvc.perform(post("/api/v1/mixin-blueprints/{id}/apply", mixinId)
                        .param("objectPath", TWIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(TWIN_PATH));

        mockMvc.perform(get("/api/v1/objects/by-path/variables").param("path", TWIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='maintenanceNote')]").exists());

        mockMvc.perform(post("/api/v1/mixin-blueprints/{id}/detach", mixinId)
                        .param("objectPath", TWIN_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detached").value(true))
                .andExpect(jsonPath("$.removedVariables", hasItem("maintenanceNote")));

        // DEVICE child strictly for raw driver telemetry (no logic hosted on DEVICE)
        ensureTelemetrySensorDevice();
        assertThat(objectManager.require(SENSOR_DEVICE_PATH).type()).isEqualTo(ObjectType.DEVICE);

        // =========================================================================
        // STAGE 2: SHIP — Ship-canon guard + validate + dry-run + deploy + reconcile
        //                 + W6 solution tests + BFF invoke + sqlBinding refresh
        // =========================================================================
        String badDeviceHostManifest = """
                {
                  "version": "1.0.0",
                  "displayName": "Invalid Device Logic Host",
                  "schemaName": "app_golden_bad",
                  "objects": [{"parentPath": "root.platform.devices", "name": "bad-logic-dev", "type": "DEVICE"}],
                  "functions": [{
                    "objectPath": "root.platform.devices.bad-logic-dev",
                    "functionName": "illegalFn",
                    "source": {"type": "script", "body": "{\\"steps\\":[{\\"type\\":\\"return\\",\\"fields\\":{\\"error_code\\":\\"OK\\"}}]}"}
                  }]
                }
                """;
        mockMvc.perform(post("/api/v1/applications/golden-bad-check/bundle/validate")
                        .param("strict", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badDeviceHostManifest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.issues[*].code", hasItem("LOGIC_HOST_DEVICE")));

        String bundleV1 = buildGoldenBundleManifest("1.0.0", "Golden Cell Overview v1");

        // Gate 1: strict validate (clean OK with zero warnings)
        mockMvc.perform(post("/api/v1/applications/{appId}/bundle/validate", APP_ID)
                        .param("strict", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleV1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.errors").isEmpty());

        // Gate 2: dry-run validate lists sections that would be applied without mutating state
        mockMvc.perform(post("/api/v1/applications/{appId}/bundle/validate", APP_ID)
                        .param("dryRun", "true")
                        .param("strict", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleV1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.wouldApply", hasItem("migrations")))
                .andExpect(jsonPath("$.wouldApply", hasItem("functions")))
                .andExpect(jsonPath("$.wouldApply", hasItem("bindings")))
                .andExpect(jsonPath("$.wouldApply", hasItem("workflows")));

        // Gate 3: strict deploy v1.0.0
        mockMvc.perform(post("/api/v1/applications/{appId}/deploy", APP_ID)
                        .param("strict", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleV1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.snapshotActive").value(true));

        // Operator UI contract materialized from bundle
        mockMvc.perform(get("/api/v1/applications/{appId}/operator-ui", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultDashboardPath").value(DASHBOARD_PATH));

        // W6 solution tests[] run in rollback-only transaction (PASS without polluting SQL table)
        List<FunctionTestRunner.TestResult> testResults = bundleDeployService.runTests(APP_ID);
        assertThat(testResults).hasSize(1);
        assertThat(testResults.getFirst().status()).isEqualTo("PASS");

        // Verify rollback left 0 rows in golden_incident
        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "golden_listIncidents",
                                  "input": { "schema": { "name": "in", "fields": [] }, "rows": [{}] }
                                }
                                """.formatted(SINGLETON_HUB_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.rows", hasSize(0)));

        // Reconcile (P8): redeploy v1.1.0 updates existing artifacts cleanly without errors
        String bundleV2 = buildGoldenBundleManifest("1.1.0", "Golden Cell Overview v2");
        mockMvc.perform(post("/api/v1/applications/{appId}/deploy", APP_ID)
                        .param("strict", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleV2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.version").value("1.1.0"));

        mockMvc.perform(get("/api/v1/dashboards/by-path").param("path", DASHBOARD_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Golden Cell Overview v2"));

        mockMvc.perform(get("/api/v1/applications/{appId}/deploy/history", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

        // BFF invoke on SINGLETON hub -> isolated SQL write + sqlBinding refresh -> live variable
        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "golden_recordIncident",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "incidentId", "type": "STRING" },
                                        { "name": "unitCode", "type": "STRING" },
                                        { "name": "peakTemp", "type": "DOUBLE" }
                                      ]
                                    },
                                    "rows": [{
                                      "incidentId": "INC-GOLDEN-001",
                                      "unitCode": "UNIT-01",
                                      "peakTemp": 93.5
                                    }]
                                  }
                                }
                                """.formatted(SINGLETON_HUB_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.status").value("OPEN"));

        // Automatic on_function_success SQL binding refresh updates openIncidentCount to 1.0 immediately
        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", SINGLETON_HUB_PATH)
                        .param("name", "openIncidentCount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(1.0));

        String sqlBindingPath = SqlBindingObjectService.BINDINGS_ROOT + "."
                + SqlBindingObjectService.sanitizeNodeName(
                        SINGLETON_HUB_PATH.replace('.', '-') + "-openIncidentCount");

        // =========================================================================
        // STAGE 3: REACTIVE LOGIC — CEL formal verify + Hub/Twin bindings + @dashboardContext
        // =========================================================================
        mockMvc.perform(post("/api/v1/expressions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "expression": "self.liveCellTemp[\\"value\\"] > 80.0" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(post("/api/v1/expressions/verify-equivalence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "left": "!(self.liveCellTemp[\\"value\\"] <= 80.0)",
                                  "right": "self.liveCellTemp[\\"value\\"] > 80.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        // Contradictory CEL condition is blocked by Z3 formal verification on apply
        BindingRule contradictoryRule = new BindingRule(
                "contradiction-check",
                "Impossible condition",
                true,
                1,
                BindingActivators.onLocalChange(),
                "self.liveCellTemp[\"value\"] > 100.0 && self.liveCellTemp[\"value\"] < 50.0",
                "true",
                new BindingTarget("cellAlarmActive", "value")
        );
        mockMvc.perform(put("/api/v1/objects/by-path/binding-rules")
                        .param("path", SINGLETON_HUB_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(contradictoryRule))))
                .andExpect(status().isBadRequest());

        // Valid ordered multi-pass reactive rules on SINGLETON hub (one owner per variable)
        List<BindingRule> hubReactiveRules = List.of(
                new BindingRule(
                        "ingest-sensor-temp",
                        "Read raw telemetry from DEVICE child",
                        true,
                        10,
                        BindingActivators.onRemoteChange(SENSOR_DEVICE_PATH, "temperature"),
                        "",
                        "read(\"" + SENSOR_DEVICE_PATH + "/temperature\")",
                        new BindingTarget("liveCellTemp", "value")
                ),
                new BindingRule(
                        "compute-cell-alarm",
                        "Compute hub alarm flag from hub liveCellTemp",
                        true,
                        20,
                        new BindingActivators(false, List.of(BindingVariableRef.local("liveCellTemp")), null, 0),
                        "",
                        "self.liveCellTemp[\"value\"] > 80.0",
                        new BindingTarget("cellAlarmActive", "value")
                ),
                new BindingRule(
                        "mirror-temp-to-twin",
                        "Propagate cell temperature to INSTANCE twin",
                        true,
                        30,
                        new BindingActivators(false, List.of(BindingVariableRef.local("liveCellTemp")), null, 0),
                        "",
                        "self.liveCellTemp[\"value\"]",
                        new BindingTarget(
                                BindingTargetKind.VARIABLE,
                                null,
                                "value",
                                null,
                                null,
                                TWIN_PATH + "/tempC"
                        )
                )
        );
        mockMvc.perform(put("/api/v1/objects/by-path/binding-rules")
                        .param("path", SINGLETON_HUB_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hubReactiveRules)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Push telemetry on DEVICE -> verify SINGLETON hub & INSTANCE twin update in cascade
        DataSchema tempSchema = DataSchema.builder("temperature")
                .field("value", FieldType.DOUBLE)
                .field("unit", FieldType.STRING)
                .build();
        objectManager.setDriverTelemetryValue(
                SENSOR_DEVICE_PATH,
                "temperature",
                DataRecord.single(tempSchema, Map.of("value", 91.5, "unit", "C"))
        );
        bindingRuleEngine.onVariableChanged(SINGLETON_HUB_PATH, SENSOR_DEVICE_PATH, "temperature");
        bindingRuleEngine.runRulesForObject(TWIN_PATH);

        assertThat(readScalar(SINGLETON_HUB_PATH, "liveCellTemp")).isEqualTo(91.5);
        assertThat(readScalar(SINGLETON_HUB_PATH, "cellAlarmActive")).isEqualTo(true);
        assertThat(readScalar(TWIN_PATH, "tempC")).isEqualTo(91.5);
        assertThat(readScalar(TWIN_PATH, "overTemp")).isEqualTo(true);

        // Platform Rule on DASHBOARD (@dashboardContext) -> mode & widget visibility
        bindingRulesService.saveRules(DASHBOARD_PATH, List.of(
                new BindingRule(
                        "ctx-incident-mode",
                        "Switch mode to incident when twin selected",
                        true,
                        10,
                        new BindingActivators(false, List.of(), null, 0, false, true),
                        "context.selection.unit != \"\"",
                        "\"incident\"",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "params.mode", null)
                ),
                new BindingRule(
                        "ctx-incident-panel-vis",
                        "Show incident panel in incident mode",
                        true,
                        20,
                        new BindingActivators(false, List.of(), null, 0, false, true),
                        "context.params.mode == \"incident\"",
                        "true",
                        new BindingTarget(BindingTargetKind.CONTEXT, null, null, "widgets.incident-panel.visible", null)
                )
        ));
        mockMvc.perform(put("/api/v1/dashboards/by-path/context")
                        .param("path", DASHBOARD_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "context": {
                                    "selection": { "unit": "%s" },
                                    "params": { "mode": "normal" },
                                    "widgets": {}
                                  },
                                  "updatedBy": "golden-test"
                                }
                                """.formatted(TWIN_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.params.mode").value("incident"))
                .andExpect(jsonPath("$.context.widgets.incident-panel.visible").value(true));

        // =========================================================================
        // STAGE 4: HISTORIAN & ANALYTICS ENGINE — Opt-in policy + samples + KPI tag
        // =========================================================================
        // Before enabling historian, samples are empty even though telemetry was updated in Stage 3
        mockMvc.perform(get("/api/v1/objects/by-path/variables/history")
                        .param("path", SENSOR_DEVICE_PATH)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples", hasSize(0)));

        mockMvc.perform(patch("/api/v1/objects/by-path/variables/history")
                        .param("path", SENSOR_DEVICE_PATH)
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "historyEnabled": true,
                                  "historyRetentionDays": 30,
                                  "historySampleMode": "CHANGES_ONLY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyEnabled").value(true))
                .andExpect(jsonPath("$.historySampleMode").value("CHANGES_ONLY"));

        // Record distinct samples (80, 90, 100) then repeat 100 -> CHANGES_ONLY skips the duplicate
        for (double sample : List.of(80.0, 90.0, 100.0)) {
            objectManager.setVariableValue(
                    SENSOR_DEVICE_PATH,
                    "temperature",
                    DataRecord.single(tempSchema, Map.of("value", sample, "unit", "C"))
            );
        }
        int countAfterDistinct = variableHistoryService
                .query(SENSOR_DEVICE_PATH, "temperature", "value", null, null, 20)
                .samples()
                .size();
        assertThat(countAfterDistinct).isGreaterThanOrEqualTo(3);

        // Duplicate 100.0 write must be deduplicated by CHANGES_ONLY
        objectManager.setVariableValue(
                SENSOR_DEVICE_PATH,
                "temperature",
                DataRecord.single(tempSchema, Map.of("value", 100.0, "unit", "C"))
        );
        mockMvc.perform(get("/api/v1/objects/by-path/variables/history")
                        .param("path", SENSOR_DEVICE_PATH)
                        .param("name", "temperature")
                        .param("field", "value")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(SENSOR_DEVICE_PATH))
                .andExpect(jsonPath("$.samples", hasSize(countAfterDistinct)));

        // Attach HISTORIAN rolling average rule on SINGLETON hub (not DEVICE!)
        List<BindingRule> mergedHubRules = new ArrayList<>(bindingRulesService.listRules(SINGLETON_HUB_PATH));
        String histRuleId = "golden-rolling-temp-1h";
        mergedHubRules.removeIf(r -> histRuleId.equals(r.id()));
        mergedHubRules.add(new BindingRule(
                histRuleId,
                "1h rolling average cell temperature",
                true,
                40,
                BindingRuleKind.HISTORIAN,
                new BindingActivators(
                        false,
                        List.of(new BindingVariableRef(SENSOR_DEVICE_PATH, "temperature")),
                        null,
                        60_000L,
                        false,
                        false
                ),
                "",
                "avg(" + SENSOR_DEVICE_PATH + "/temperature, 1h)",
                new BindingTarget("variable", "rollingTempAvg1h", "value", null, null),
                "1h",
                null
        ));
        bindingRulesService.saveRules(SINGLETON_HUB_PATH, mergedHubRules);
        dependencyIndex.rebuild(SINGLETON_HUB_PATH);

        String tagPath = HistorianTagPaths.encode(SINGLETON_HUB_PATH, histRuleId);
        mockMvc.perform(get("/api/v1/platform/analytics/tags/evaluate")
                        .with(authentication(ADMIN))
                        .param("path", tagPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.outputs.rollingTempAvg1h").isNumber());

        var evalTick = analyticsEngineService.evaluateAllEnabled();
        assertThat(evalTick.updated()).isGreaterThanOrEqualTo(1);
        assertThat(String.valueOf(readScalar(SINGLETON_HUB_PATH, "rollingTempAvg1h"))).isNotBlank();

        // =========================================================================
        // STAGE 5: CLOSED-LOOP AUTOMATION — AlertRule -> Journal -> Correlator
        //          -> BPMN Workflow -> Work Queue Claim & Complete -> Hub Ack
        // =========================================================================
        // Reset sensor temperature below threshold so edge-triggered AlertRule arms cleanly
        putSensorTemperatureViaHttp(24.0);

        String alertRulePath = AutomationTreeService.rulePathForName(ALERT_RULE_NAME);
        mockMvc.perform(post("/api/v1/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "objectPath": "%s",
                                  "watchVariable": "temperature",
                                  "conditionExpr": "self.temperature[\\"value\\"] > 85.0",
                                  "eventName": "cellOverheated",
                                  "payloadVariable": "temperature",
                                  "enabled": true,
                                  "edgeTrigger": true
                                }
                                """.formatted(ALERT_RULE_NAME, SENSOR_DEVICE_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertRulePath));

        String correlatorPath = AutomationTreeService.correlatorPathForName(CORRELATOR_NAME);
        mockMvc.perform(post("/api/v1/correlators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "objectPath": "%s",
                                  "patternType": "COUNT",
                                  "eventName": "cellOverheated",
                                  "windowSeconds": 300,
                                  "minOccurrences": 1,
                                  "cooldownSeconds": 0,
                                  "sequenceGapSeconds": 0,
                                  "actionType": "RUN_WORKFLOW",
                                  "actionTarget": "%s",
                                  "payloadFilterExpr": "double(payload[\\"value\\"]) > 85.0 && payload[\\"alertPriority\\"] == \\"HIGH\\"",
                                  "enabled": true
                                }
                                """.formatted(CORRELATOR_NAME, alertRulePath, WORKFLOW_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(correlatorPath));

        // Cross threshold (24.0 -> 94.0): triggers AlertRule -> Event Journal -> Correlator -> BPMN Workflow
        putSensorTemperatureViaHttp(94.0);

        // 5a. Event recorded in Event Journal on the alert rule node
        mockMvc.perform(get("/api/v1/events")
                        .param("objectPath", alertRulePath)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventName", hasItem("cellOverheated")));

        // 5b. Correlator triggered and started the BPMN workflow
        mockMvc.perform(get("/api/v1/correlators/by-path").param("path", correlatorPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastTriggeredAt").isNotEmpty());

        // 5c. BPMN workflow paused at userTask and populated app-scoped Operator Work Queue
        MvcResult queueRes = mockMvc.perform(get("/api/v1/work-queue")
                        .param("operatorAppId", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem("Acknowledge Golden Cell Overheat")))
                .andReturn();

        // Isolation check: another app's operator queue does not see this task
        mockMvc.perform(get("/api/v1/work-queue")
                        .param("operatorAppId", "other-unrelated-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='Acknowledge Golden Cell Overheat')]").isEmpty());

        JsonNode openTasks = objectMapper.readTree(queueRes.getResponse().getContentAsString());
        String taskId = null;
        for (JsonNode item : openTasks) {
            if ("Acknowledge Golden Cell Overheat".equals(item.path("title").asString())) {
                taskId = item.path("id").asString();
                break;
            }
        }
        assertThat(taskId).isNotBlank();

        // 5d. Claim task as operator-alice -> status CLAIMED -> complete task -> status COMPLETED
        mockMvc.perform(post("/api/v1/work-queue/claim")
                        .param("taskId", taskId)
                        .param("operatorId", "operator-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.assignee").value("operator-alice"));

        mockMvc.perform(post("/api/v1/work-queue/complete")
                        .param("taskId", taskId)
                        .param("operatorId", "operator-alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Open work queue for app is now drained
        mockMvc.perform(get("/api/v1/work-queue").param("operatorAppId", APP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + taskId + "')]").isEmpty());

        // 5e. Operator resolves incident via BFF on SINGLETON hub -> openIncidentCount returns to 0
        mockMvc.perform(post("/api/v1/bff/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectPath": "%s",
                                  "functionName": "golden_ackIncident",
                                  "input": {
                                    "schema": {
                                      "name": "in",
                                      "fields": [
                                        { "name": "incidentId", "type": "STRING" },
                                        { "name": "operatorId", "type": "STRING" }
                                      ]
                                    },
                                    "rows": [{
                                      "incidentId": "INC-GOLDEN-001",
                                      "operatorId": "operator-alice"
                                    }]
                                  }
                                }
                                """.formatted(SINGLETON_HUB_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error_code").value("OK"))
                .andExpect(jsonPath("$.result.resolved").value(true));

        mockMvc.perform(post("/api/v1/sql-bindings/by-path/refresh")
                        .param("path", sqlBindingPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", SINGLETON_HUB_PATH)
                        .param("name", "openIncidentCount"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(0.0));
    }

    private String ensureSingletonHubBlueprint() throws Exception {
        MvcResult existing = mockMvc.perform(get("/api/v1/singleton-blueprints/by-name/{name}", SINGLETON_NAME))
                .andReturn();
        if (existing.getResponse().getStatus() == 200) {
            return objectMapper.readTree(existing.getResponse().getContentAsString()).path("id").asString();
        }
        MvcResult created = mockMvc.perform(post("/api/v1/singleton-blueprints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Singleton orchestrator hub for golden-path platform regression",
                                  "targetObjectType": "CUSTOM",
                                  "variables": [
                                    {
                                      "name": "liveCellTemp",
                                      "description": "Latest aggregated cell temperature",
                                      "group": "kpi",
                                      "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                        "rows": [{ "value": 0.0 }]
                                      }
                                    },
                                    {
                                      "name": "cellAlarmActive",
                                      "description": "Cell overheat alarm state",
                                      "group": "kpi",
                                      "schema": { "name": "boolValue", "fields": [{ "name": "value", "type": "BOOLEAN" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "boolValue", "fields": [{ "name": "value", "type": "BOOLEAN" }] },
                                        "rows": [{ "value": false }]
                                      }
                                    },
                                    {
                                      "name": "openIncidentCount",
                                      "description": "Live SQL binding count of OPEN incidents",
                                      "group": "kpi",
                                      "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                        "rows": [{ "value": 0.0 }]
                                      }
                                    },
                                    {
                                      "name": "rollingTempAvg1h",
                                      "description": "Historian rolling 1h average temperature",
                                      "group": "analytics",
                                      "schema": { "name": "stringValue", "fields": [{ "name": "value", "type": "STRING" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "stringValue", "fields": [{ "name": "value", "type": "STRING" }] },
                                        "rows": [{ "value": "" }]
                                      }
                                    }
                                  ],
                                  "events": [],
                                  "functions": [],
                                  "bindings": [],
                                  "parameters": {}
                                }
                                """.formatted(SINGLETON_NAME)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();
    }

    private String ensureUnitTwinInstanceType() throws Exception {
        MvcResult existing = mockMvc.perform(get("/api/v1/instance-types/by-name/{name}", INSTANCE_TYPE_NAME))
                .andReturn();
        if (existing.getResponse().getStatus() == 200) {
            return objectMapper.readTree(existing.getResponse().getContentAsString()).path("id").asString();
        }
        MvcResult created = mockMvc.perform(post("/api/v1/instance-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Digital twin for thermal production unit",
                                  "targetObjectType": "CUSTOM",
                                  "variables": [
                                    {
                                      "name": "tempC",
                                      "description": "Unit temperature",
                                      "group": "telemetry",
                                      "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                        "rows": [{ "value": 20.0 }]
                                      }
                                    },
                                    {
                                      "name": "limitC",
                                      "description": "Unit thermal limit",
                                      "group": "config",
                                      "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "doubleValue", "fields": [{ "name": "value", "type": "DOUBLE" }] },
                                        "rows": [{ "value": 85.0 }]
                                      }
                                    },
                                    {
                                      "name": "overTemp",
                                      "description": "Computed twin over-temperature flag",
                                      "group": "status",
                                      "schema": { "name": "boolValue", "fields": [{ "name": "value", "type": "BOOLEAN" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "boolValue", "fields": [{ "name": "value", "type": "BOOLEAN" }] },
                                        "rows": [{ "value": false }]
                                      }
                                    }
                                  ],
                                  "bindings": [
                                    {
                                      "id": "twin-overtemp-rule",
                                      "name": "Twin overTemp calculation",
                                      "enabled": true,
                                      "order": 10,
                                      "activators": {
                                        "onStartup": true,
                                        "onVariableChange": [{ "ref": "@/tempC" }, { "ref": "@/limitC" }],
                                        "periodicMs": 0
                                      },
                                      "condition": "",
                                      "expression": "self.tempC[\\"value\\"] > self.limitC[\\"value\\"]",
                                      "target": { "variableName": "overTemp", "field": "value" }
                                    }
                                  ],
                                  "events": [],
                                  "functions": [],
                                  "parameters": {}
                                }
                                """.formatted(INSTANCE_TYPE_NAME)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();
    }

    private String ensureMaintenanceMixinBlueprint() throws Exception {
        MvcResult existing = mockMvc.perform(get("/api/v1/mixin-blueprints/by-name/{name}", MIXIN_NAME))
                .andReturn();
        if (existing.getResponse().getStatus() == 200) {
            return objectMapper.readTree(existing.getResponse().getContentAsString()).path("id").asString();
        }
        MvcResult created = mockMvc.perform(post("/api/v1/mixin-blueprints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Optional maintenance metadata mixin",
                                  "targetObjectType": "CUSTOM",
                                  "variables": [
                                    {
                                      "name": "maintenanceNote",
                                      "description": "Operator maintenance note",
                                      "group": "maintenance",
                                      "schema": { "name": "stringValue", "fields": [{ "name": "value", "type": "STRING" }] },
                                      "readable": true,
                                      "writable": true,
                                      "defaultValue": {
                                        "schema": { "name": "stringValue", "fields": [{ "name": "value", "type": "STRING" }] },
                                        "rows": [{ "value": "nominal" }]
                                      }
                                    }
                                  ],
                                  "events": [],
                                  "functions": [],
                                  "bindings": [],
                                  "parameters": {}
                                }
                                """.formatted(MIXIN_NAME)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();
    }

    private void ensureFolder(String folderPath, String displayName) throws Exception {
        if (objectManager.tree().findByPath(folderPath).isPresent()) {
            return;
        }
        int dot = folderPath.lastIndexOf('.');
        String parent = folderPath.substring(0, dot);
        String name = folderPath.substring(dot + 1);
        mockMvc.perform(post("/api/v1/objects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "%s",
                                  "name": "%s",
                                  "type": "CUSTOM",
                                  "displayName": "%s",
                                  "description": "Golden path cell container"
                                }
                                """.formatted(parent, name, displayName)))
                .andExpect(status().isOk());
    }

    private void ensureTelemetrySensorDevice() throws Exception {
        if (objectManager.tree().findByPath(SENSOR_DEVICE_PATH).isEmpty()) {
            mockMvc.perform(post("/api/v1/objects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "parentPath": "%s",
                                      "name": "sensor-01",
                                      "type": "DEVICE",
                                      "displayName": "Golden Cell Sensor 01",
                                      "description": "Raw telemetry sensor endpoint",
                                      "driverId": "virtual",
                                      "driverPollIntervalMs": 5000,
                                      "autoStartDriver": false
                                    }
                                    """.formatted(CELL_FOLDER)))
                    .andExpect(status().isOk());
        }
        if (objectManager.require(SENSOR_DEVICE_PATH).getVariable("temperature").isEmpty()) {
            DataSchema tempSchema = DataSchema.builder("temperature")
                    .field("value", FieldType.DOUBLE)
                    .field("unit", FieldType.STRING)
                    .build();
            objectManager.createVariable(
                    SENSOR_DEVICE_PATH,
                    "temperature",
                    tempSchema,
                    true,
                    true,
                    DataRecord.single(tempSchema, Map.of("value", 22.0, "unit", "C")),
                    false,
                    null
            );
            objectManager.persistNodeTree(SENSOR_DEVICE_PATH);
        }
    }

    private void putSensorTemperatureViaHttp(double value) throws Exception {
        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", SENSOR_DEVICE_PATH)
                        .param("name", "temperature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "temperature",
                                    "fields": [
                                      { "name": "value", "type": "DOUBLE" },
                                      { "name": "unit", "type": "STRING" }
                                    ]
                                  },
                                  "rows": [{ "value": %s, "unit": "C" }]
                                }
                                """.formatted(value)))
                .andExpect(status().isOk());
    }

    private String buildGoldenBundleManifest(String version, String dashboardTitle) {
        String bpmnXml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>"
                + "<definitions xmlns=\\\"http://www.omg.org/spec/BPMN/20100524/MODEL\\\" "
                + "xmlns:ispf=\\\"http://ispf.io/bpmn\\\" targetNamespace=\\\"http://ispf.io/bpmn\\\">"
                + "<process id=\\\"golden-incident-handler\\\" name=\\\"Golden Incident Handler\\\" isExecutable=\\\"true\\\">"
                + "<startEvent id=\\\"start\\\" name=\\\"Start\\\"/>"
                + "<serviceTask id=\\\"logIncident\\\" name=\\\"Log overheat\\\" ispf:action=\\\"log\\\" ispf:message=\\\"golden-cell: overheat detected\\\"/>"
                + "<userTask id=\\\"ackOverheat\\\" name=\\\"Acknowledge Golden Cell Overheat\\\" "
                + "ispf:title=\\\"Acknowledge Golden Cell Overheat\\\" "
                + "ispf:instructions=\\\"Inspect thermal unit and acknowledge overheat\\\" "
                + "ispf:assigneeRole=\\\"operator\\\"/>"
                + "<endEvent id=\\\"end\\\" name=\\\"End\\\"/>"
                + "<sequenceFlow id=\\\"f1\\\" sourceRef=\\\"start\\\" targetRef=\\\"logIncident\\\"/>"
                + "<sequenceFlow id=\\\"f2\\\" sourceRef=\\\"logIncident\\\" targetRef=\\\"ackOverheat\\\"/>"
                + "<sequenceFlow id=\\\"f3\\\" sourceRef=\\\"ackOverheat\\\" targetRef=\\\"end\\\"/>"
                + "</process></definitions>";

        String recordScript = "{\\\"steps\\\":["
                + "{\\\"type\\\":\\\"exec\\\",\\\"sql\\\":\\\"INSERT INTO golden_incident (incident_id, unit_code, peak_temp, status) VALUES (?, ?, ?, 'OPEN')\\\",\\\"params\\\":[\\\"${input.incidentId}\\\",\\\"${input.unitCode}\\\",\\\"${input.peakTemp}\\\"]},"
                + "{\\\"type\\\":\\\"return\\\",\\\"fields\\\":{\\\"error_code\\\":\\\"OK\\\",\\\"error_message\\\":\\\"\\\",\\\"status\\\":\\\"OPEN\\\"}}"
                + "]}";

        String listScript = "{\\\"steps\\\":["
                + "{\\\"type\\\":\\\"selectMany\\\",\\\"var\\\":\\\"items\\\",\\\"sql\\\":\\\"SELECT incident_id AS incidentId, unit_code AS unitCode, status FROM golden_incident ORDER BY incident_id\\\"},"
                + "{\\\"type\\\":\\\"return\\\",\\\"fields\\\":{\\\"error_code\\\":\\\"OK\\\",\\\"error_message\\\":\\\"\\\",\\\"rows\\\":\\\"${items}\\\"}}"
                + "]}";

        String ackScript = "{\\\"steps\\\":["
                + "{\\\"type\\\":\\\"exec\\\",\\\"sql\\\":\\\"UPDATE golden_incident SET status = 'RESOLVED', resolved_by = ? WHERE incident_id = ?\\\",\\\"params\\\":[\\\"${input.operatorId}\\\",\\\"${input.incidentId}\\\"]},"
                + "{\\\"type\\\":\\\"return\\\",\\\"fields\\\":{\\\"error_code\\\":\\\"OK\\\",\\\"error_message\\\":\\\"\\\",\\\"resolved\\\":true}}"
                + "]}";

        return """
                {
                  "version": "%s",
                  "displayName": "Golden Regression Solution",
                  "tablePrefix": "",
                  "schemaName": "app_golden_regression",
                  "migrations": [
                    {
                      "id": "golden_incident_v1",
                      "sql": "CREATE TABLE IF NOT EXISTS golden_incident (incident_id VARCHAR(64) PRIMARY KEY, unit_code VARCHAR(64) NOT NULL, peak_temp DOUBLE PRECISION NOT NULL, status VARCHAR(32) NOT NULL, resolved_by VARCHAR(64));"
                    }
                  ],
                  "dashboards": [
                    {
                      "path": "%s",
                      "title": "%s",
                      "layoutJson": "{\\"widgets\\":[{\\"id\\":\\"incident-panel\\",\\"type\\":\\"value\\",\\"title\\":\\"Open Incidents\\",\\"ref\\":\\"%s/openIncidentCount\\"}]}",
                      "refreshIntervalMs": 5000
                    }
                  ],
                  "workflows": [
                    {
                      "path": "%s",
                      "title": "Golden Incident Handler",
                      "status": "ACTIVE",
                      "operatorAppId": "%s",
                      "bpmnXml": "%s"
                    }
                  ],
                  "functions": [
                    {
                      "objectPath": "%s",
                      "functionName": "golden_recordIncident",
                      "version": "1",
                      "descriptor": {
                        "inputSchema": {
                          "name": "in",
                          "fields": [
                            { "name": "incidentId", "type": "STRING" },
                            { "name": "unitCode", "type": "STRING" },
                            { "name": "peakTemp", "type": "DOUBLE" }
                          ]
                        },
                        "outputSchema": {
                          "name": "out",
                          "fields": [
                            { "name": "error_code", "type": "STRING" },
                            { "name": "error_message", "type": "STRING" },
                            { "name": "status", "type": "STRING" }
                          ]
                        }
                      },
                      "source": { "type": "script", "body": "%s" }
                    },
                    {
                      "objectPath": "%s",
                      "functionName": "golden_listIncidents",
                      "version": "1",
                      "descriptor": {
                        "inputSchema": { "name": "in", "fields": [] },
                        "outputSchema": {
                          "name": "out",
                          "fields": [
                            { "name": "error_code", "type": "STRING" },
                            { "name": "error_message", "type": "STRING" },
                            {
                              "name": "rows",
                              "type": "RECORD_LIST",
                              "nestedSchema": {
                                "name": "incident_row",
                                "fields": [
                                  { "name": "incidentId", "type": "STRING" },
                                  { "name": "unitCode", "type": "STRING" },
                                  { "name": "status", "type": "STRING" }
                                ]
                              }
                            }
                          ]
                        }
                      },
                      "source": { "type": "script", "body": "%s" }
                    },
                    {
                      "objectPath": "%s",
                      "functionName": "golden_ackIncident",
                      "version": "1",
                      "descriptor": {
                        "inputSchema": {
                          "name": "in",
                          "fields": [
                            { "name": "incidentId", "type": "STRING" },
                            { "name": "operatorId", "type": "STRING" }
                          ]
                        },
                        "outputSchema": {
                          "name": "out",
                          "fields": [
                            { "name": "error_code", "type": "STRING" },
                            { "name": "error_message", "type": "STRING" },
                            { "name": "resolved", "type": "BOOLEAN" }
                          ]
                        }
                      },
                      "source": { "type": "script", "body": "%s" }
                    }
                  ],
                  "bindings": [
                    {
                      "objectPath": "%s",
                      "variable": "openIncidentCount",
                      "query": "SELECT COUNT(*) AS cnt FROM golden_incident WHERE status = 'OPEN'",
                      "valueField": "cnt",
                      "refresh": "on_function_success",
                      "refreshIntervalMs": 30000,
                      "triggerObjectPath": "%s",
                      "triggerFunctionName": "golden_recordIncident",
                      "enabled": true
                    }
                  ],
                  "events": [
                    {
                      "id": "cellOverheated",
                      "roles": ["operator", "admin"]
                    }
                  ],
                  "tests": [
                    {
                      "id": "smoke-record-incident-rollback",
                      "kind": "function",
                      "objectPath": "%s",
                      "functionName": "golden_recordIncident",
                      "input": {
                        "incidentId": "INC-TEST-RB",
                        "unitCode": "UNIT-01",
                        "peakTemp": 88.0
                      },
                      "expect": {
                        "errorCode": "OK",
                        "fields": { "status": "OPEN" }
                      },
                      "rollback": true
                    }
                  ],
                  "operatorUi": {
                    "defaultDashboardPath": "%s"
                  }
                }
                """.formatted(
                version,
                DASHBOARD_PATH,
                dashboardTitle,
                SINGLETON_HUB_PATH,
                WORKFLOW_PATH,
                APP_ID,
                bpmnXml,
                SINGLETON_HUB_PATH,
                recordScript,
                SINGLETON_HUB_PATH,
                listScript,
                SINGLETON_HUB_PATH,
                ackScript,
                SINGLETON_HUB_PATH,
                SINGLETON_HUB_PATH,
                SINGLETON_HUB_PATH,
                DASHBOARD_PATH
        );
    }

    private Object readScalar(String objectPath, String variableName) {
        return objectManager.require(objectPath)
                .getVariable(variableName)
                .flatMap(v -> v.value())
                .map(r -> r.firstRow().get("value"))
                .orElse(null);
    }

    private void deleteAlertRuleIfExists(String name) throws Exception {
        String path = AutomationTreeService.rulePathForName(name);
        if (mockMvc.perform(get("/api/v1/alert-rules/by-path").param("path", path))
                .andReturn()
                .getResponse()
                .getStatus() == 200) {
            mockMvc.perform(delete("/api/v1/alert-rules/by-path").param("path", path))
                    .andExpect(status().isOk());
        }
    }

    private void deleteCorrelatorIfExists(String name) throws Exception {
        String path = AutomationTreeService.correlatorPathForName(name);
        if (mockMvc.perform(get("/api/v1/correlators/by-path").param("path", path))
                .andReturn()
                .getResponse()
                .getStatus() == 200) {
            mockMvc.perform(delete("/api/v1/correlators/by-path").param("path", path))
                    .andExpect(status().isOk());
        }
    }

    private void drainOpenWorkQueueForApp() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/work-queue").param("operatorAppId", APP_ID))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            return;
        }
        JsonNode open = objectMapper.readTree(result.getResponse().getContentAsString());
        if (!open.isArray()) {
            return;
        }
        for (JsonNode item : open) {
            String taskId = item.path("id").asString("");
            if (!taskId.isBlank()) {
                mockMvc.perform(post("/api/v1/work-queue/complete")
                        .param("taskId", taskId)
                        .param("operatorId", "cleanup-operator"));
            }
        }
    }
}
