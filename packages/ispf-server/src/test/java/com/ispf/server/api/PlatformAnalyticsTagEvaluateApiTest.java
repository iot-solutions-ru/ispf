package com.ispf.server.api;

import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.HistorianComputationTestSupport;
import com.ispf.server.history.VariableHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class PlatformAnalyticsTagEvaluateApiTest {

    private static final String SENSOR = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Test
    void evaluateTagProbeReturnsResultForValidHistorianRule() throws Exception {
        seedHistorianSamples(20.0);
        String chainA = HistorianComputationTestSupport.ensureDevice(objectManager, "root.platform.devices", "probe-chain-a");
        String ruleId = "probe-chain-a-rule";
        HistorianComputationTestSupport.upsertRollingAvgRule(
                bindingRulesService,
                chainA,
                ruleId,
                SENSOR,
                "temperature",
                "derived-a",
                "5m"
        );
        String tagPath = HistorianTagPaths.encode(chainA, ruleId);

        mockMvc.perform(get("/api/v1/platform/analytics/tags/evaluate")
                        .param("path", tagPath)
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.outputs['derived-a']").isNumber());
    }

    @Test
    void evaluateTagProbeSurvivesMissingSourceVariable() throws Exception {
        String chainB = HistorianComputationTestSupport.ensureDevice(objectManager, "root.platform.devices", "probe-chain-b");
        String ruleId = "probe-chain-b-rule";
        HistorianComputationTestSupport.upsertRollingAvgRule(
                bindingRulesService,
                chainB,
                ruleId,
                SENSOR,
                "derived-a",
                "derived-b",
                "5m"
        );
        String tagPath = HistorianTagPaths.encode(chainB, ruleId);

        mockMvc.perform(get("/api/v1/platform/analytics/tags/evaluate")
                        .param("path", tagPath)
                        .header("X-ISPF-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("skipped"));
    }

    private void seedHistorianSamples(double value) {
        for (int i = 0; i < 3; i++) {
            objectManager.setVariableValue(
                    SENSOR,
                    "temperature",
                    DataRecord.single(
                            DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                            Map.of("value", value)
                    )
            );
            variableHistoryService.recordVariableUpdate(SENSOR, "temperature");
        }
    }
}
