package com.ispf.server.driver;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.config.DriverPackProperties;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.spi.DeviceObjectAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriverRuntimeServicePollWithoutBindingTest {

    @Test
    void pollWithoutBindingNamesTheMissingBinding() {
        DeviceObjectAccess objects = mock(DeviceObjectAccess.class);
        String path = "root.platform.devices.poll-proof";
        when(objects.findByPath(path)).thenReturn(Optional.of(new PlatformObject(
                "id",
                path,
                ObjectType.DEVICE,
                "poll-proof",
                "",
                null
        )));
        DriverRuntimeService service = new DriverRuntimeService(
                objects,
                mock(DriverFactory.class),
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.pollNow(path));

        assertEquals("No driver binding for: " + path, ex.getMessage());
    }
}
