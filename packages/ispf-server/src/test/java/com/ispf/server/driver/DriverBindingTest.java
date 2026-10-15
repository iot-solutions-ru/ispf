package com.ispf.server.driver;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverBindingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesTelemetryPublishModeFromConfigurationJson() {
        DriverBinding binding = DriverBinding.parse(
                "virtual",
                1000,
                "{\"sineAmplitude\":\"10.0\",\"telemetryPublishMode\":\"TELEMETRY_ONLY\",\"telemetryCoalesceMs\":\"500\"}",
                "{}",
                objectMapper
        );

        assertThat(binding.telemetryPublishMode()).isEqualTo(TelemetryPublishMode.TELEMETRY_ONLY);
        assertThat(binding.telemetryCoalesceMs()).isEqualTo(500);
        assertThat(binding.configuration()).isEqualTo(Map.of("sineAmplitude", "10.0"));
        assertThat(binding.configurationWithPolicy()).containsEntry("telemetryPublishMode", "TELEMETRY_ONLY")
                .containsEntry("telemetryCoalesceMs", "500")
                .containsEntry("sineAmplitude", "10.0");
    }

    @Test
    void defaultsToFullPublishMode() {
        DriverBinding binding = DriverBinding.of("virtual", 1000, Map.of("periodSec", "60"), Map.of());

        assertThat(binding.telemetryPublishMode()).isEqualTo(TelemetryPublishMode.FULL);
        assertThat(TelemetryPublishMode.FULL.automationEligible()).isTrue();
    }

    @Test
    void rejectsUnparseableTelemetryCoalesceMsInJson() {
        assertThatThrownBy(() -> DriverBinding.parse(
                "virtual",
                1000,
                "{\"telemetryCoalesceMs\":\"soon\"}",
                "{}",
                objectMapper
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telemetryCoalesceMs");
    }

    @Test
    void rejectsUnparseableTelemetryCoalesceMsInConfiguration() {
        assertThatThrownBy(() -> DriverBinding.of(
                "virtual",
                1000,
                Map.of("telemetryCoalesceMs", "abc"),
                Map.of()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("telemetryCoalesceMs");
    }

    @Test
    void keepsExplicitZeroTelemetryCoalesceMsDisabled() {
        DriverBinding binding = DriverBinding.of(
                "virtual",
                1000,
                Map.of("telemetryCoalesceMs", "0"),
                Map.of()
        );

        assertThat(binding.telemetryCoalesceMs()).isZero();
    }

    @Test
    void invalidConfigurationJsonFailsInsteadOfBecomingEmpty() {
        assertThatThrownBy(() -> DriverBinding.parse(
                "virtual",
                1000,
                "{broken",
                "{}",
                objectMapper
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Driver configuration JSON is not parseable");
    }

    @Test
    void blankConfigurationJsonStillMeansEmpty() {
        DriverBinding binding = DriverBinding.parse("virtual", 1000, " ", "{}", objectMapper);

        assertThat(binding.configuration()).isEmpty();
    }
}
