package com.ispf.server.platform.analytics.engine;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectManager;
import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
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

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CelExpressionIntegrationTest {

    private static final String SENSOR = "root.platform.devices.demo-sensor-01";
    private static final String TAG = "root.platform.devices.analytics-cel-demo";

    @Autowired
    private AnalyticsTagCatalogService catalogService;

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
    void celExpressionDerivedTagEvaluatesFromHistorian() {
        assetAnalyticsService.ensureCatalog();
        objectManager.setVariableValue(
                SENSOR,
                "temperature",
                DataRecord.single(
                        DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                        Map.of("value", 30.0)
                )
        );
        seedHistorianSamples(30.0);
        createCelTag("hist.live('" + SENSOR + "', 'temperature') + 5");

        var tags = catalogService.listEnabledTags().stream()
                .filter(tag -> TAG.equals(tag.tagPath()))
                .toList();
        var results = engineService.evaluateTags(tags, AnalyticsEvaluationOptions.now(), Instant.now());

        assertThat(results).anyMatch(result -> "ok".equals(result.status()) && TAG.equals(result.tagPath()));
        assertThat(readDerived(TAG)).isNotBlank();
        assertThat(Double.parseDouble(readDerived(TAG))).isGreaterThanOrEqualTo(35.0);
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

    private void createCelTag(String expression) {
        String parent = "root.platform.devices";
        if (objectManager.tree().findByPath(TAG).isEmpty()) {
            objectManager.create(parent, "analytics-cel-demo", ObjectType.DEVICE, "CEL demo", "BL-211", null);
        }
        var rollingAvg = blueprintRegistry.findByName(AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL).orElseThrow();
        var analyticsTag = blueprintRegistry.findByName(AnalyticsBlueprintBootstrap.ANALYTICS_TAG_MODEL).orElseThrow();
        blueprintApplicationService.applyBlueprintWithRules(rollingAvg, TAG, Map.of());
        blueprintApplicationService.applyBlueprintWithRules(analyticsTag, TAG, Map.of());
        objectManager.setVariableValue(TAG, "analyticsHelper", stringRecord("cel"));
        objectManager.setVariableValue(TAG, "analyticsExpression", stringRecord(expression));
        objectManager.setVariableValue(TAG, "analyticsTagEnabled", boolRecord(true));
    }

    private static DataRecord stringRecord(String value) {
        return DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }

    private static DataRecord boolRecord(boolean value) {
        return DataRecord.single(
                DataSchema.builder("booleanValue").field("value", FieldType.BOOLEAN).build(),
                Map.of("value", value)
        );
    }

    private String readDerived(String path) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> node.getVariable("derivedValue"))
                .flatMap(variable -> variable.value())
                .map(record -> String.valueOf(record.firstRow().get("value")))
                .orElse("");
    }
}
