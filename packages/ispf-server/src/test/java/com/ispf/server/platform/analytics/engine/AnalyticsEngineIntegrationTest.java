package com.ispf.server.platform.analytics.engine;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AnalyticsBlueprintBootstrap;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.plugin.blueprint.BlueprintRegistry;
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
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Autowired
    private BlueprintApplicationService blueprintApplicationService;

    @Test
    void threeTagChainCompletesWithinPeriodicTick() {
        assetAnalyticsService.ensureCatalog();
        seedHistorianSamples(24.0);

        String tagA = createDerivedTag("analytics-chain-a", SENSOR, "temperature");
        String tagB = createDerivedTag("analytics-chain-b", tagA, "derivedValue");
        String tagC = createDerivedTag("analytics-chain-c", tagB, "derivedValue");

        long started = System.currentTimeMillis();
        var result = engineService.evaluateAllEnabled();
        long elapsed = System.currentTimeMillis() - started;

        assertThat(result.ran()).isTrue();
        assertThat(result.updated()).isGreaterThanOrEqualTo(3);
        assertThat(elapsed).isLessThan(5_000L);

        assertThat(readDerived(tagA)).isNotBlank();
        assertThat(readDerived(tagB)).isNotBlank();
        assertThat(readDerived(tagC)).isNotBlank();
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

    private String createDerivedTag(String nodeName, String sourcePath, String sourceVariable) {
        String parent = "root.platform.devices";
        String path = parent + "." + nodeName;
        if (objectManager.tree().findByPath(path).isEmpty()) {
            objectManager.create(parent, nodeName, ObjectType.DEVICE, nodeName, "analytics chain", null);
        }
        var model = blueprintRegistry.findByName(AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL).orElseThrow();
        blueprintApplicationService.applyBlueprintWithRules(model, path, Map.of());
        objectManager.setVariableValue(path, "sourcePath", stringRecord(sourcePath));
        objectManager.setVariableValue(path, "sourceVariable", stringRecord(sourceVariable));
        objectManager.setVariableValue(path, "sourceField", stringRecord("value"));
        objectManager.setVariableValue(path, "windowBucket", stringRecord("1h"));
        return path;
    }

    private static DataRecord stringRecord(String value) {
        return DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }

    private String readDerived(String path) {
        PlatformObject node = objectManager.require(path);
        return node.getVariable("derivedValue")
                .flatMap(v -> v.value())
                .map(r -> String.valueOf(r.firstRow().get("value")))
                .orElse("");
    }
}
