package com.ispf.server.platform.analytics.catalog;

import com.ispf.analytics.engine.AnalyticsSourceRef;
import com.ispf.analytics.engine.AnalyticsTagDefinition;
import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.HistorianComputationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsTagMetadataServiceIntegrationTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private AnalyticsTagMetadataService metadataService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void syncHaystackUpdatesReadOnlyDriverPointMappings() {
        objectManager.setSystemVariableValue(DEVICE, "driverPointMappingsJson", stringRecord("{}"));
        markDriverPointMappingsReadOnly();
        HistorianComputationTestSupport.upsertRollingAvgRule(
                bindingRulesService,
                DEVICE,
                "avg-temp",
                DEVICE,
                "temperature",
                "avgValue",
                "5m"
        );

        AnalyticsTagDefinition tag = new AnalyticsTagDefinition(
                HistorianTagPaths.encode(DEVICE, "avg-temp"),
                "avg",
                List.of(new AnalyticsSourceRef(DEVICE, "temperature", "value")),
                "5m",
                List.of("5m"),
                60_000L,
                true,
                true,
                "avgValue"
        );

        assertThatCode(() -> metadataService.syncHaystackForTag(tag)).doesNotThrowAnyException();

        PlatformObject refreshed = objectManager.require(DEVICE);
        String mappingsJson = refreshed.getVariable("driverPointMappingsJson")
                .flatMap(Variable::value)
                .map(record -> String.valueOf(record.firstRow().get("value")))
                .orElse("");
        assertThat(mappingsJson).contains("avgValue");
        assertThat(mappingsJson).contains("point");
    }

    private void markDriverPointMappingsReadOnly() {
        PlatformObject device = objectManager.require(DEVICE);
        Variable mappings = device.getVariable("driverPointMappingsJson").orElseThrow();
        device.removeVariable("driverPointMappingsJson");
        device.addVariable(mappings.withDefinition(
                mappings.readable(),
                false,
                mappings.historyEnabled(),
                mappings.historyRetentionDays().orElse(null)
        ));
        objectManager.persistNodeTree(DEVICE);
    }

    private static DataRecord stringRecord(String value) {
        return DataRecord.single(
                DataSchema.builder("stringValue").field("value", FieldType.STRING).build(),
                Map.of("value", value)
        );
    }
}
