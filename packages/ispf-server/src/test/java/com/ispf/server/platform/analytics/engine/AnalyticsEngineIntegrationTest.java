package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import com.ispf.server.platform.analytics.HistorianComputationTestSupport;
import com.ispf.server.history.VariableHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsEngineIntegrationTest {

    private static final String SENSOR = "root.platform.devices.demo-sensor-01";

    @Autowired
    private AssetAnalyticsService assetAnalyticsService;

    @Autowired
    private AnalyticsEngineService engineService;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Test
    void threeTagChainCompletesWithinPeriodicTick() {
        assetAnalyticsService.ensureCatalog();
        seedHistorianSamples(24.0);

        String tagA = createHistorianTag("analytics-chain-a", SENSOR, "temperature", "derived-a");
        String tagB = createHistorianTag("analytics-chain-b", HistorianTagPaths.objectPath(tagA), "derived-a", "derived-b");
        String tagC = createHistorianTag("analytics-chain-c", HistorianTagPaths.objectPath(tagB), "derived-b", "derived-c");

        long started = System.currentTimeMillis();
        var result = engineService.evaluateAllEnabled();
        long elapsed = System.currentTimeMillis() - started;

        assertThat(result.ran()).isTrue();
        assertThat(result.updated()).isGreaterThanOrEqualTo(3);
        assertThat(elapsed).isLessThan(5_000L);

        assertThat(readOutput(HistorianTagPaths.objectPath(tagA), "derived-a")).isNotBlank();
        assertThat(readOutput(HistorianTagPaths.objectPath(tagB), "derived-b")).isNotBlank();
        assertThat(readOutput(HistorianTagPaths.objectPath(tagC), "derived-c")).isNotBlank();
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

    private String createHistorianTag(
            String nodeName,
            String sourcePath,
            String sourceVariable,
            String outputVariable
    ) {
        String devicePath = HistorianComputationTestSupport.ensureDevice(
                objectManager,
                "root.platform.devices",
                nodeName
        );
        String ruleId = nodeName + "-rule";
        HistorianComputationTestSupport.upsertRollingAvgRule(
                bindingRulesService,
                devicePath,
                ruleId,
                sourcePath,
                sourceVariable,
                outputVariable,
                "1h"
        );
        return HistorianTagPaths.encode(devicePath, ruleId);
    }

    private String readOutput(String path, String variableName) {
        PlatformObject node = objectManager.require(path);
        return node.getVariable(variableName)
                .flatMap(v -> v.value())
                .map(r -> String.valueOf(r.firstRow().get("value")))
                .orElse("");
    }
}
