package com.ispf.server.platform.analytics;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.bootstrap.LabBlueprintBootstrap;
import com.ispf.server.history.VariableHistoryService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AnalyticsMultiTagQueryIntegrationTest {

    private static final String SENSOR = "root.platform.devices.demo-sensor-01";
    private static final String REMOTE_A = "root.platform.devices.test-analytics-query.remote-a";
    private static final String REMOTE_B = "root.platform.devices.test-analytics-query.remote-b";
    private static final DataSchema DOUBLE_VALUE = DataSchema.builder("doubleValue")
            .field("value", FieldType.DOUBLE)
            .build();

    @Autowired
    private AnalyticsQueryService analyticsQueryService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private VariableHistoryService variableHistoryService;

    @Test
    void queryAlignsFiveTagsAcrossLocalAndRemoteDevices() {
        ensureRemoteDevice(REMOTE_A);
        ensureRemoteDevice(REMOTE_B);
        ensureHistorizedVariable(SENSOR, "metricA");
        ensureHistorizedVariable(SENSOR, "metricB");
        ensureHistorizedVariable(SENSOR, "metricC");
        ensureHistorizedVariable(REMOTE_A, "temperature");
        ensureHistorizedVariable(REMOTE_B, "temperature");

        seed(SENSOR, "temperature", 10.0);
        seed(SENSOR, "metricA", 11.0);
        seed(SENSOR, "metricB", 12.0);
        seed(SENSOR, "metricC", 13.0);
        seed(REMOTE_A, "temperature", 20.0);
        seed(REMOTE_B, "temperature", 30.0);

        Instant to = Instant.now();
        Instant from = to.minus(6, ChronoUnit.HOURS);
        AnalyticsQueryResponse response = analyticsQueryService.query(new AnalyticsQueryRequest(
                List.of(
                        new AnalyticsQueryRequest.AnalyticsQueryTag(SENSOR, "temperature", "value", "local-temp"),
                        new AnalyticsQueryRequest.AnalyticsQueryTag(SENSOR, "metricA", "value", "local-a"),
                        new AnalyticsQueryRequest.AnalyticsQueryTag(SENSOR, "metricC", "value", "local-c"),
                        new AnalyticsQueryRequest.AnalyticsQueryTag(REMOTE_A, "temperature", "value", "remote-a"),
                        new AnalyticsQueryRequest.AnalyticsQueryTag(REMOTE_B, "temperature", "value", "remote-b")
                ),
                from,
                to,
                "1h",
                "avg",
                100,
                null
        ));

        assertThat(response.series()).hasSize(5);
        assertThat(response.timestamps()).isNotEmpty();
        assertThat(response.series())
                .extracting(AnalyticsQueryResponse.AnalyticsQuerySeries::id)
                .containsExactly("local-temp", "local-a", "local-c", "remote-a", "remote-b");
        assertThat(response.series())
                .allMatch(series -> series.values().size() == response.timestamps().size());
        assertThat(response.latencyMs()).isLessThan(3_000L);
    }

    private void seed(String path, String variable, double value) {
        for (int i = 0; i < 3; i++) {
            objectManager.setVariableValue(
                    path,
                    variable,
                    DataRecord.single(
                            DataSchema.builder(variable).field("value", FieldType.DOUBLE).build(),
                            Map.of("value", value + i)
                    )
            );
            variableHistoryService.recordVariableUpdate(path, variable);
        }
    }

    private void ensureHistorizedVariable(String path, String variable) {
        if (objectManager.require(path).getVariable(variable).isEmpty()) {
            objectManager.createVariable(
                    path,
                    variable,
                    DOUBLE_VALUE,
                    true,
                    true,
                    DataRecord.single(DOUBLE_VALUE, Map.of("value", 0.0)),
                    true,
                    null
            );
        }
    }

    private void ensureRemoteDevice(String path) {
        if (objectManager.tree().findByPath(path).isEmpty()) {
            String folder = "root.platform.devices.test-analytics-query";
            if (objectManager.tree().findByPath(folder).isEmpty()) {
                objectManager.create(
                        "root.platform.devices",
                        "test-analytics-query",
                        ObjectType.CUSTOM,
                        "test-analytics-query",
                        "",
                        null
                );
            }
            String name = path.substring(path.lastIndexOf('.') + 1);
            objectManager.create(
                    folder,
                    name,
                    ObjectType.DEVICE,
                    name,
                    "",
                    LabBlueprintBootstrap.VIRTUAL_LAB_MODEL
            );
        }
    }
}
