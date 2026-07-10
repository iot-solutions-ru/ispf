package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.plugin.blueprint.BlueprintApplicationService;
import com.ispf.plugin.blueprint.BlueprintRegistry;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
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
class AnalyticsDerivedTagServiceTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private AnalyticsDerivedTagService derivedTagService;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BlueprintApplicationService blueprintApplicationService;

    @Autowired
    private BlueprintRegistry blueprintRegistry;

    @Test
    void refreshWritesDerivedValueFromHistorianAggregate() {
        applyRollingAvgBlueprint();
        configureSource("temperature", "1h");

        double reading = 42.0;
        for (int i = 0; i < 3; i++) {
            objectManager.setVariableValue(
                    DEVICE,
                    "temperature",
                    DataRecord.single(
                            DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                            Map.of("value", reading)
                    )
            );
            variableHistoryService.recordVariableUpdate(DEVICE, "temperature");
        }

        AnalyticsDerivedTagService.DerivedTagRefreshResult result = derivedTagService.refreshDevice(DEVICE);

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.message()).contains("derivedValue=");

        PlatformObject device = objectManager.require(DEVICE);
        String derivedValue = device.getVariable("derivedValue")
                .flatMap(v -> v.value())
                .map(r -> String.valueOf(r.firstRow().get("value")))
                .orElse("");
        assertThat(derivedValue).isNotBlank();
        assertThat(Double.parseDouble(derivedValue)).isFinite();
    }

    @Test
    void refreshSkipsWhenSourceVariableMissing() {
        applyRollingAvgBlueprint();
        configureSource("", "5m");

        AnalyticsDerivedTagService.DerivedTagRefreshResult result = derivedTagService.refreshDevice(DEVICE);

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.message()).contains("sourceVariable");
    }

    private void applyRollingAvgBlueprint() {
        blueprintRegistry.findByName(AnalyticsBlueprintBootstrap.ROLLING_AVG_MODEL).ifPresent(model ->
                blueprintApplicationService.applyBlueprintWithRules(model, DEVICE, Map.of())
        );
    }

    private void configureSource(String sourceVariable, String windowBucket) {
        setString(DEVICE, "sourcePath", DEVICE);
        setString(DEVICE, "sourceVariable", sourceVariable);
        setString(DEVICE, "sourceField", "value");
        setString(DEVICE, "windowBucket", windowBucket);
    }

    private void setString(String path, String name, String value) {
        objectManager.setVariableValue(
                path,
                name,
                HistorianComputationTestSupport.stringRecord(value)
        );
    }
}
