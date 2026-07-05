package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.script.PlatformScriptBridge;
import com.ispf.server.bootstrap.FixtureBlueprintBootstrap;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttGatewayFunctionHandlerTest {

    private static final String GATEWAY = "root.gateway";
    private static final String CHILD = GATEWAY + ".sensors.loadtest-mqtt-sensor-00001";
    private static final String DEFAULT_PATTERN = "ispf/loadtest/(\\d+)/temperature";

    @Mock
    private ObjectManager objectManager;

    @Mock
    private DeviceTelemetryPolicyService telemetryPolicyService;

    @Mock
    private ObjectChangePublicationService publicationService;

    @Mock
    private PlatformScriptBridge platformScriptBridge;

    @Test
    void extractIndexFromLoadtestTopic() {
        Optional<String> index = MqttGatewayFunctionHandler.extractIndex(
                "ispf/loadtest/00042/temperature",
                MqttGatewayFunctionHandlerTest.DEFAULT_PATTERN
        );
        assertThat(index).contains("00042");
    }

    @Test
    void extractIndexRejectsUnmatchedTopic() {
        Optional<String> index = MqttGatewayFunctionHandler.extractIndex(
                "esp2/other/temperature",
                MqttGatewayFunctionHandlerTest.DEFAULT_PATTERN
        );
        assertThat(index).isEmpty();
    }

    @Test
    void parseNumericAcceptsFiniteDoubles() {
        assertThat(MqttGatewayFunctionHandler.parseNumeric("23.7")).contains(23.7);
        assertThat(MqttGatewayFunctionHandler.parseNumeric(" 42 ")).contains(42.0);
        assertThat(MqttGatewayFunctionHandler.parseNumeric("not-a-number")).isEmpty();
    }

    @Test
    void bypassesChildCoalesceForTelemetryOnlyChild() {
        MqttGatewayFunctionHandler handler = newHandlerWithTree();
        when(telemetryPolicyService.automationEligible(CHILD)).thenReturn(false);

        DataRecord result = handler.dispatchIngress(GATEWAY, "ispf/loadtest/00001/temperature", "23.7", true);

        assertThat(result.firstRow().get("ok")).isEqualTo(true);
        verify(objectManager).setDriverTelemetryValueDirect(eq(CHILD), eq("temperature"), any(DataRecord.class));
        verify(objectManager, never()).setDriverTelemetryValue(eq(CHILD), eq("temperature"), any(DataRecord.class));
        verify(publicationService).publishVariableChange(CHILD, "temperature", null);
    }

    @Test
    void keepsCoalescerForAutomationEligibleChild() {
        MqttGatewayFunctionHandler handler = newHandlerWithTree();
        when(telemetryPolicyService.automationEligible(CHILD)).thenReturn(true);

        DataRecord result = handler.dispatchIngress(GATEWAY, "ispf/loadtest/00001/temperature", "23.7", true);

        assertThat(result.firstRow().get("ok")).isEqualTo(true);
        verify(objectManager).setDriverTelemetryValue(eq(CHILD), eq("temperature"), any(DataRecord.class));
        verify(objectManager, never()).setDriverTelemetryValueDirect(eq(CHILD), eq("temperature"), any(DataRecord.class));
        verify(publicationService, never()).publishVariableChange(any(), any(), any());
    }

    private MqttGatewayFunctionHandler newHandlerWithTree() {
        PlatformObject gateway = new PlatformObject("gateway", GATEWAY, ObjectType.DEVICE, "Gateway", "", null);
        when(objectManager.require(GATEWAY)).thenReturn(gateway);
        when(platformScriptBridge.instantiateModelIfMissing(
                eq(FixtureBlueprintBootstrap.MQTT_GATEWAY_SENSOR_MODEL),
                eq(GATEWAY + ".sensors"),
                eq("loadtest-mqtt-sensor-00001")
        )).thenReturn(CHILD);
        return new MqttGatewayFunctionHandler(
                objectManager,
                telemetryPolicyService,
                publicationService,
                platformScriptBridge
        );
    }
}
