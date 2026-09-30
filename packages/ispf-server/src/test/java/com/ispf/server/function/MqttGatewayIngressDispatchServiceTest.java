package com.ispf.server.function;

import com.ispf.core.model.DataRecord;
import com.ispf.server.config.MqttGatewayProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class MqttGatewayIngressDispatchServiceTest {

    @Test
    void failedFunctionDispatchIsCountedAndDoesNotEscape() {
        FunctionService functionService = mock(FunctionService.class);
        doThrow(new IllegalStateException("boom"))
                .when(functionService).invoke(anyString(), anyString(), any(DataRecord.class));
        MqttGatewayIngressDispatchService service = new MqttGatewayIngressDispatchService(
                new MqttGatewayProperties(), null, functionService, null, null, null);

        assertThatCode(() -> service.dispatchIngress("gateways.gw1", "onReading", "line/1", "{}"))
                .doesNotThrowAnyException();

        assertThat(service.dispatchFailureCount()).isEqualTo(1);
        assertThat(service.lastDispatchFailure())
                .hasValueSatisfying(failure -> assertThat(failure)
                        .contains("gateways.gw1")
                        .contains("onReading")
                        .contains("boom"));
    }

    @Test
    void failedGatewayHandlerDispatchIsCounted() {
        MqttGatewayFunctionHandler handler = mock(MqttGatewayFunctionHandler.class);
        doThrow(new IllegalStateException("handler boom"))
                .when(handler).dispatchIngress(anyString(), anyString(), anyString(), anyBoolean());
        MqttGatewayIngressDispatchService service = new MqttGatewayIngressDispatchService(
                new MqttGatewayProperties(), handler, null, null, null, null);

        service.dispatchIngress("gateways.gw1", MqttGatewayFunctionHandler.FUNCTION_NAME, "line/1", "{}");

        assertThat(service.dispatchFailureCount()).isEqualTo(1);
        assertThat(service.lastDispatchFailure())
                .hasValueSatisfying(failure -> assertThat(failure).contains("handler boom"));
    }
}
