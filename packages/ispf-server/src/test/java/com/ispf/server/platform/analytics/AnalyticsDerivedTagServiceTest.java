package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
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
    private static final String ROLLING_AVG_PATH = "root.platform.analytics.rollingAvg";

    @Autowired
    private AssetAnalyticsService assetAnalyticsService;

    @Autowired
    private AnalyticsDerivedTagService derivedTagService;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Autowired
    private ObjectManager objectManager;

    @Test
    void refreshWritesDerivedValueFromHistorianAggregate() {
        assetAnalyticsService.ensureCatalog();

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

        assetAnalyticsService.applyTemplateToDevice(new AssetAnalyticsService.ApplyTemplateCommand(
                ROLLING_AVG_PATH,
                DEVICE,
                DEVICE,
                "temperature",
                "value",
                "1h",
                null,
                null,
                null
        ));

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
        assetAnalyticsService.ensureCatalog();

        assetAnalyticsService.applyTemplateToDevice(new AssetAnalyticsService.ApplyTemplateCommand(
                ROLLING_AVG_PATH,
                DEVICE,
                DEVICE,
                "temperature",
                "value",
                "5m",
                null,
                null,
                null
        ));

        objectManager.setVariableValue(
                DEVICE,
                "sourceVariable",
                DataRecord.single(
                        DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                        Map.of("value", "")
                )
        );

        AnalyticsDerivedTagService.DerivedTagRefreshResult result = derivedTagService.refreshDevice(DEVICE);

        assertThat(result.status()).isEqualTo("skipped");
        assertThat(result.message()).contains("sourceVariable");
    }
}
