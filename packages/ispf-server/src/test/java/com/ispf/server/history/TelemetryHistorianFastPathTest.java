package com.ispf.server.history;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.server.config.RuntimeTelemetryProperties;
import com.ispf.server.driver.DeviceTelemetryPolicyService;
import com.ispf.server.driver.TelemetryPublishMode;
import com.ispf.server.object.pubsub.ObjectChangePublicationService;
import com.ispf.server.object.pubsub.VariableChangeInterest;
import com.ispf.server.object.pubsub.VariableChangeSubscriptionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryHistorianFastPathTest {

    private static final String DEVICE = "root.platform.devices.sensor-a";
    private static final DataSchema TEMPERATURE = DataSchema.builder("temperature")
            .field("value", FieldType.DOUBLE)
            .build();

    @Mock
    private RuntimeTelemetryProperties runtimeTelemetryProperties;
    @Mock
    private DeviceTelemetryPolicyService telemetryPolicyService;
    @Mock
    private VariableChangeSubscriptionRegistry subscriptionRegistry;
    @Mock
    private VariableHistoryService variableHistoryService;
    @Mock
    private ObjectChangePublicationService publicationService;

    private TelemetryHistorianFastPath fastPath;

    @BeforeEach
    void setUp() {
        when(runtimeTelemetryProperties.isFastHistorianPath()).thenReturn(true);
        fastPath = new TelemetryHistorianFastPath(
                runtimeTelemetryProperties,
                telemetryPolicyService,
                subscriptionRegistry,
                variableHistoryService,
                publicationService,
                java.util.Optional.empty()
        );
    }

    @Test
    void historianOnlyWhenTelemetryOnlyWithoutUiInterest() {
        when(telemetryPolicyService.publishMode(DEVICE, "temperature"))
                .thenReturn(TelemetryPublishMode.TELEMETRY_ONLY);
        when(subscriptionRegistry.interest(DEVICE, "temperature"))
                .thenReturn(new VariableChangeInterest(true, false, false, false, false, false));

        assertThat(fastPath.isHistorianOnlyEligible(DEVICE, "temperature")).isTrue();
        assertThat(fastPath.tryPublish(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 21.5)),
                Instant.parse("2026-07-14T12:00:00Z")
        )).isTrue();
        verify(variableHistoryService).recordFromDataRecordTrusted(eq(DEVICE), eq("temperature"), any(), any());
    }

    @Test
    void leavesFastPathWhenUiIsWatchingTelemetryOnlyVariable() {
        when(telemetryPolicyService.publishMode(DEVICE, "temperature"))
                .thenReturn(TelemetryPublishMode.TELEMETRY_ONLY);
        when(subscriptionRegistry.interest(DEVICE, "temperature"))
                .thenReturn(new VariableChangeInterest(true, false, false, false, false, true));

        assertThat(fastPath.isHistorianOnlyEligible(DEVICE, "temperature")).isFalse();
        assertThat(fastPath.tryPublish(
                DEVICE,
                "temperature",
                DataRecord.single(TEMPERATURE, Map.of("value", 21.5)),
                Instant.parse("2026-07-14T12:00:00Z")
        )).isFalse();
        verify(variableHistoryService, never()).recordFromDataRecordTrusted(any(), any(), any(), any());
    }

    @Test
    void uiInterestBustsCachedHistorianOnlyEligibility() {
        when(telemetryPolicyService.publishMode(DEVICE, "temperature"))
                .thenReturn(TelemetryPublishMode.TELEMETRY_ONLY);
        when(subscriptionRegistry.interest(DEVICE, "temperature"))
                .thenReturn(new VariableChangeInterest(true, false, false, false, false, false))
                .thenReturn(new VariableChangeInterest(true, false, false, false, false, true));

        assertThat(fastPath.isHistorianOnlyEligible(DEVICE, "temperature")).isTrue();
        assertThat(fastPath.isHistorianOnlyEligible(DEVICE, "temperature")).isFalse();
    }
}
