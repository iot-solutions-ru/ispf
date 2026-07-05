package com.ispf.server.object;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.function.MqttGatewayIngressDispatchService;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuntimeTelemetryCoalescerTest {

    @Mock
    private ObjectChangePublicationService publicationService;

    @Mock
    private MqttGatewayIngressDispatchService gatewayIngressDispatch;

    @Mock
    private com.ispf.server.history.TelemetryHistorianFastPath historianFastPath;

    private RuntimeTelemetryCoalescer coalescer;

    @AfterEach
    void tearDown() {
        if (coalescer != null) {
            coalescer.shutdown();
        }
    }

    @Test
    void publishesImmediatelyWhenCoalesceDisabled() {
        coalescer = newCoalescer(true, 1_000, false, false, false);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));
        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 2.0));
        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 3.0));

        verify(publicationService, times(3)).publishVariableChange("root.dev.sensor", "temperature", null);
    }

    @Test
    void coalescesMultipleUpdatesIntoSingleEvent() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));
        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 2.0));
        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 3.0));

        verifyNoInteractions(publicationService);

        coalescer.flushNow();

        verify(publicationService, times(1)).publishVariableChange("root.dev.sensor", "temperature", null);
    }

    @Test
    void marksTelemetryOnlyDevicesAsNotAutomationEligible() {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setEnabled(false);
        DeviceTelemetryPolicyService policyService = org.mockito.Mockito.mock(DeviceTelemetryPolicyService.class);
        org.mockito.Mockito.when(policyService.automationEligible("root.dev.sensor")).thenReturn(false);
        org.mockito.Mockito.when(historianFastPath.isHistorianOnlyEligible(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(false);
        org.mockito.Mockito.when(historianFastPath.tryPublish(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(false);
        coalescer = new RuntimeTelemetryCoalescer(
                properties, policyService, publicationService, gatewayIngressDispatch, historianFastPath
        );
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));

        verify(publicationService).publishVariableChange("root.dev.sensor", "temperature", null);
    }

    @Test
    void skipsUnchangedValues() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema schema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        DataRecord value = record(schema, 42.0);

        coalescer.recordUpdate("root.dev.sensor", "temperature", value);
        coalescer.flushNow();
        clearInvocations(publicationService);

        coalescer.recordUpdate("root.dev.sensor", "temperature", value);
        coalescer.flushNow();

        verifyNoInteractions(publicationService);
    }

    @Test
    void publishesDistinctVariablesSeparately() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema schema = DataSchema.builder("metric").field("value", FieldType.DOUBLE).build();

        coalescer.recordUpdate("root.dev.sensor", "temperature", record(schema, 1.0));
        coalescer.recordUpdate("root.dev.sensor", "humidity", record(schema, 55.0));
        coalescer.flushNow();

        verify(publicationService, times(2)).publishVariableChange(eq("root.dev.sensor"), any(), any());
    }

    @Test
    void valuesEqualComparesRowContent() {
        DataSchema schema = DataSchema.builder("metric").field("value", FieldType.DOUBLE).build();
        DataRecord left = record(schema, 10.0);
        DataRecord right = record(schema, 10.0);
        DataRecord different = record(schema, 11.0);

        assertThat(RuntimeTelemetryCoalescer.valuesEqual(left, right)).isTrue();
        assertThat(RuntimeTelemetryCoalescer.valuesEqual(left, different)).isFalse();
        assertThat(RuntimeTelemetryCoalescer.valuesEqual(null, left)).isFalse();
    }

    @Test
    void coalescesIngressTopicsOnSeparateLanes() {
        coalescer = newCoalescer(true, 1_000);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "ispf/loadtest/00001/temperature", "raw", "1.0"))
        );
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "ispf/loadtest/00002/temperature", "raw", "2.0"))
        );
        coalescer.flushNow();

        verify(publicationService, times(2)).publishVariableChange(eq("root.dev.gateway"), eq("lastIngress"), any());
    }

    @Test
    void coalescesIngressPayloadsOnSeparateLanesWhenEnabled() {
        coalescer = newCoalescer(true, 1_000, false, true);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0001\",\"temperature\":\"1\"}"))
        );
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0002\",\"temperature\":\"2\"}"))
        );
        coalescer.flushNow();

        verify(publicationService, times(2)).publishVariableChange(eq("root.dev.gateway"), eq("lastIngress"), any());
    }

    @Test
    void coalescesSameTopicWithoutPayloadLanesIntoOneLane() {
        clearInvocations(publicationService);
        coalescer = newCoalescer(true, 1_000, false, false);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();

        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0001\",\"temperature\":\"1\"}"))
        );
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "meter", "raw", "{\"id\":\"meter-0002\",\"temperature\":\"2\"}"))
        );
        coalescer.flushNow();

        verify(publicationService, times(1)).publishVariableChange("root.dev.gateway", "lastIngress", null);
    }

    @Test
    void ingressBatchRecordsHistorianDespiteDuplicatePayload() {
        coalescer = newCoalescer(true, 1_000);
        org.mockito.Mockito.when(historianFastPath.isHistorianOnlyEligible("root.dev.sensor", "temperature"))
                .thenReturn(true);
        DataSchema schema = DataSchema.builder("temperature").field("raw", FieldType.STRING).build();
        DataRecord value = DataRecord.single(schema, Map.of("raw", "1734567890123"));

        coalescer.publishCoalescedBatch(List.of(
                new CoalescedTelemetryUpdate("root.dev.sensor", "temperature", value, null),
                new CoalescedTelemetryUpdate("root.dev.sensor", "temperature", value, null)
        ));

        verify(historianFastPath).publishBatch(org.mockito.ArgumentMatchers.argThat(batch -> batch.size() == 2));
        verifyNoInteractions(publicationService);
    }

    @Test
    void parallelGatewayDispatchSkipsBindingEvent() {
        coalescer = newCoalescer(true, 1_000);
        org.mockito.Mockito.when(gatewayIngressDispatch.tryScheduleDispatch(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("lastIngress"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(true);
        DataSchema ingress = DataSchema.builder("mqttIngress")
                .field("topic", FieldType.STRING)
                .field("raw", FieldType.STRING)
                .build();
        coalescer.recordUpdate(
                "root.dev.gateway",
                "lastIngress",
                DataRecord.single(ingress, Map.of("topic", "ispf/loadtest/00001/temperature", "raw", "3.0"))
        );
        coalescer.flushNow();
        verifyNoInteractions(publicationService);
    }

    private RuntimeTelemetryCoalescer newCoalescer(boolean enabled, long coalesceMs) {
        return newCoalescer(enabled, coalesceMs, false, false, true);
    }

    private RuntimeTelemetryCoalescer newCoalescer(
            boolean enabled,
            long coalesceMs,
            boolean ingressTopicLanes,
            boolean ingressPayloadLanes
    ) {
        return newCoalescer(enabled, coalesceMs, ingressTopicLanes, ingressPayloadLanes, true);
    }

    private RuntimeTelemetryCoalescer newCoalescer(
            boolean enabled,
            long coalesceMs,
            boolean ingressTopicLanes,
            boolean ingressPayloadLanes,
            boolean coalesceEnabled
    ) {
        RuntimeTelemetryProperties properties = new RuntimeTelemetryProperties();
        properties.setEnabled(enabled);
        properties.setCoalesceMs(coalesceMs);
        properties.setCoalesceEnabled(coalesceEnabled);
        DeviceTelemetryPolicyService policyService = org.mockito.Mockito.mock(DeviceTelemetryPolicyService.class);
        org.mockito.Mockito.when(policyService.coalesceMs(org.mockito.ArgumentMatchers.anyString())).thenReturn(coalesceMs);
        org.mockito.Mockito.when(policyService.automationEligible(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        org.mockito.Mockito.when(policyService.ingressTopicLanes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ingressTopicLanes);
        org.mockito.Mockito.when(policyService.ingressPayloadLanes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ingressPayloadLanes);
        org.mockito.Mockito.when(policyService.parallelIngressDispatch(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ingressTopicLanes || ingressPayloadLanes);
        org.mockito.Mockito.when(historianFastPath.isHistorianOnlyEligible(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(false);
        org.mockito.Mockito.when(historianFastPath.tryPublish(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(false);
        return new RuntimeTelemetryCoalescer(
                properties, policyService, publicationService, gatewayIngressDispatch, historianFastPath
        ) {{
            startScheduler();
        }};
    }

    private static DataRecord record(DataSchema schema, double value) {
        return DataRecord.single(schema, Map.of("value", value));
    }
}
