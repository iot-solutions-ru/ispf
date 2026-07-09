package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.AnalyticsEvaluationOptions;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import com.ispf.server.platform.analytics.HistorianComputationTestSupport;
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
    private static final String DEVICE = "root.platform.devices.analytics-cel-demo";
    private static final String RULE_ID = "cel-live-plus-five";

    @Autowired
    private AnalyticsTagCatalogService catalogService;

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

        HistorianComputationTestSupport.ensureDevice(objectManager, "root.platform.devices", "analytics-cel-demo");
        HistorianComputationTestSupport.upsertCelHistorianRule(
                bindingRulesService,
                DEVICE,
                RULE_ID,
                "hist.live('" + SENSOR + "', 'temperature') + 5",
                SENSOR,
                "temperature",
                "derivedValue",
                "5m"
        );
        String tagPath = HistorianTagPaths.encode(DEVICE, RULE_ID);

        var tags = catalogService.listEnabledTags().stream()
                .filter(tag -> tagPath.equals(tag.tagPath()))
                .toList();
        var results = engineService.evaluateTags(tags, AnalyticsEvaluationOptions.now(), Instant.now());

        assertThat(results).anyMatch(result -> "ok".equals(result.status()) && tagPath.equals(result.tagPath()));
        assertThat(readDerived(DEVICE)).isNotBlank();
        assertThat(Double.parseDouble(readDerived(DEVICE))).isGreaterThanOrEqualTo(35.0);
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

    private String readDerived(String path) {
        return objectManager.tree().findByPath(path)
                .flatMap(node -> node.getVariable("derivedValue"))
                .flatMap(variable -> variable.value())
                .map(record -> String.valueOf(record.firstRow().get("value")))
                .orElse("");
    }
}
