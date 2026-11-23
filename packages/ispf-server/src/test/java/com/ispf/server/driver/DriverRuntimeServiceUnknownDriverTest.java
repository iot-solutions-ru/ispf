package com.ispf.server.driver;

import com.ispf.server.config.DriverPackProperties;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.spi.DeviceObjectAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverRuntimeServiceUnknownDriverTest {

    @Test
    void unknownDriverIsRejectedBeforeItIsStored() {
        DeviceObjectAccess objects = mock(DeviceObjectAccess.class);
        DriverFactory driverFactory = mock(DriverFactory.class);
        when(driverFactory.create("no-such-driver"))
                .thenThrow(new IllegalArgumentException("Unknown driver: no-such-driver"));
        DriverRuntimeService service = new DriverRuntimeService(
                objects,
                driverFactory,
                new ObjectMapper(),
                mock(Environment.class),
                mock(DeviceTelemetryPolicyService.class),
                mock(DriverPackProperties.class),
                mock(RuntimeTelemetryProperties.class),
                mock(ObjectProvider.class),
                mock(DriverOwnershipService.class),
                mock(DriverErrorMetrics.class),
                mock(ObjectProvider.class)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.configure(
                        "root.platform.devices.unknown-driver",
                        DriverBinding.of("no-such-driver", 2000, Map.of(), Map.of())
                ));

        assertEquals("Unknown driver: no-such-driver", ex.getMessage());
        verify(objects, never()).setSystemVariableValue(any(), any(), any());
    }
}
