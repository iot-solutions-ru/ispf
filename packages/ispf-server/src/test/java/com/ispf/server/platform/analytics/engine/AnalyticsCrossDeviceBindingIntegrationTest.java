package com.ispf.server.platform.analytics.engine;

import com.ispf.core.binding.BindingActivators;
import com.ispf.core.binding.BindingRule;
import com.ispf.core.binding.BindingTarget;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.BindingDependencyIndex;
import com.ispf.server.object.BindingRuleEngine;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.AssetAnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsCrossDeviceBindingIntegrationTest {

    private static final String SENSOR = "root.platform.devices.demo-sensor-01";
    private static final String HUB = "root.platform.devices.test-analytics-bind.hub";
    private static final String ROLLING_AVG_TEMPLATE = "root.platform.analytics.rollingAvg";
    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    @Autowired
    private AssetAnalyticsService assetAnalyticsService;

    @Autowired
    private AnalyticsEngineService engineService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private BindingDependencyIndex dependencyIndex;

    @Autowired
    private BindingRuleEngine bindingRuleEngine;

    @BeforeEach
    void resetHubRules() {
        clearHubRules();
    }

    @AfterEach
    void cleanupHubRules() {
        clearHubRules();
    }

    @Test
    void bindingRuleReadsRemoteDerivedValueAfterEngineWriteBack() {
        assetAnalyticsService.ensureCatalog();
        seedHistorian(28.0);

        assetAnalyticsService.applyTemplateToDevice(new AssetAnalyticsService.ApplyTemplateCommand(
                ROLLING_AVG_TEMPLATE,
                SENSOR,
                SENSOR,
                "temperature",
                "value",
                "1h",
                null,
                null,
                null
        ));

        var tick = engineService.evaluateAllEnabled();
        assertThat(tick.updated()).isGreaterThanOrEqualTo(1);

        String derived = readDerived(SENSOR);
        assertThat(derived).isNotBlank();

        ensureHub(HUB);
        bindingRulesService.saveRules(HUB, List.of(
                new BindingRule(
                        "sensor-derived",
                        "Sensor derived value",
                        true,
                        10,
                        BindingActivators.onRemoteChange(SENSOR, "derivedValue"),
                        "",
                        "refAt(\"" + SENSOR + "\", derivedValue)",
                        new BindingTarget("sensorDerived", "value")
                )
        ));
        dependencyIndex.rebuild(HUB);

        bindingRuleEngine.onVariableChanged(HUB, SENSOR, "derivedValue");

        assertThat(objectManager.require(HUB).getVariable("sensorDerived"))
                .isPresent()
                .get()
                .extracting(v -> v.value().orElseThrow().firstRow().get("value"))
                .isEqualTo(derived);
    }

    private void seedHistorian(double value) {
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
        PlatformObject node = objectManager.require(path);
        return node.getVariable("derivedValue")
                .flatMap(v -> v.value())
                .map(r -> String.valueOf(r.firstRow().get("value")))
                .orElse("");
    }

    private void ensureHub(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            String folder = "root.platform.devices.test-analytics-bind";
            if (objectManager.tree().findByPath(folder).isEmpty()) {
                objectManager.create(
                        "root.platform.devices",
                        "test-analytics-bind",
                        ObjectType.CUSTOM,
                        "test-analytics-bind",
                        "",
                        null
                );
            }
            objectManager.create(folder, "hub", ObjectType.CUSTOM, "hub", "", null);
            objectManager.createVariable(
                    path,
                    "sensorDerived",
                    STRING_VALUE,
                    true,
                    false,
                    DataRecord.single(STRING_VALUE, Map.of("value", "")),
                    false,
                    null
            );
        }
    }

    private void clearHubRules() {
        if (objectManager.tree().findByPath(HUB).isEmpty()) {
            return;
        }
        for (BindingRule rule : bindingRulesService.listRules(HUB)) {
            bindingRulesService.deleteRule(HUB, rule.id());
        }
        dependencyIndex.rebuild(HUB);
    }
}
