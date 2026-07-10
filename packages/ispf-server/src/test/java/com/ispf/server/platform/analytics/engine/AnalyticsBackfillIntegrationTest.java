package com.ispf.server.platform.analytics.engine;

import com.ispf.analytics.engine.HistorianTagPaths;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.BindingRulesService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.platform.analytics.HistorianComputationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsBackfillIntegrationTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String RULE_ID = "backfill-rolling-avg";

    @Autowired
    private AnalyticsBackfillService backfillService;

    @Autowired
    private BindingRulesService bindingRulesService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Test
    void backfillRecomputesDerivedValueAfterHistorianGap() {
        seedHistorian(30.0);

        HistorianComputationTestSupport.upsertRollingAvgRule(
                bindingRulesService,
                DEVICE,
                RULE_ID,
                DEVICE,
                "temperature",
                "derivedValue",
                "1h"
        );
        String tagPath = HistorianTagPaths.encode(DEVICE, RULE_ID);

        Instant to = Instant.now();
        Instant from = to.minus(6, ChronoUnit.HOURS);
        var result = backfillService.backfill(tagPath, from, to);

        assertThat(result.updated()).isGreaterThanOrEqualTo(1);
        PlatformObject device = objectManager.require(DEVICE);
        String derived = device.getVariable("derivedValue")
                .flatMap(v -> v.value())
                .map(r -> String.valueOf(r.firstRow().get("value")))
                .orElse("");
        assertThat(derived).isNotBlank();
    }

    private void seedHistorian(double value) {
        for (int i = 0; i < 3; i++) {
            objectManager.setVariableValue(
                    DEVICE,
                    "temperature",
                    DataRecord.single(
                            DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build(),
                            Map.of("value", value)
                    )
            );
            variableHistoryService.recordVariableUpdate(DEVICE, "temperature");
        }
    }
}
