package com.ispf.server.event;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.TelemetryPublishMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryEventJournalFastPathTest {

    private static final String DEVICE = "root.platform.devices.mqtt-device-01";
    private static final DataSchema TEMP_SCHEMA = DataSchema.builder("temperature")
            .field("raw", FieldType.STRING)
            .build();

    @Mock
    private DeviceTelemetryPolicyService telemetryPolicyService;

    @Mock
    private EventService eventService;

    @InjectMocks
    private TelemetryEventJournalFastPath fastPath;

    @Test
    void firesIngressEventWhenModeIsEventJournalOnly() {
        when(telemetryPolicyService.publishMode(DEVICE)).thenReturn(TelemetryPublishMode.EVENT_JOURNAL_ONLY);
        when(telemetryPolicyService.bindingFor(DEVICE)).thenReturn(Optional.of(
                DriverBinding.of("mqtt", 5000, Map.of(), Map.of("temperature", "t"), TelemetryPublishMode.EVENT_JOURNAL_ONLY, 1)
        ));

        Instant observed = Instant.parse("2026-07-04T06:00:00Z");
        DataRecord value = DataRecord.single(TEMP_SCHEMA, Map.of("raw", "24.5"));
        assertThat(fastPath.tryFire(DEVICE, "temperature", value, observed)).isTrue();

        verify(eventService).fireIngress(
                eq(DEVICE),
                eq(DriverBinding.DEFAULT_INGRESS_EVENT_NAME),
                any(DataRecord.class),
                eq(observed)
        );
    }

    @Test
    void skipsWhenModeIsTelemetryOnly() {
        when(telemetryPolicyService.publishMode(DEVICE)).thenReturn(TelemetryPublishMode.TELEMETRY_ONLY);

        assertThat(fastPath.tryFire(DEVICE, "temperature", DataRecord.single(TEMP_SCHEMA, Map.of("raw", "1")), null))
                .isFalse();
        verify(eventService, never()).fireIngress(any(), any(), any(), any());
    }
}
