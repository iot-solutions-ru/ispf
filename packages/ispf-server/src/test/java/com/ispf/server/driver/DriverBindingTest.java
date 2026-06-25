package com.ispf.server.driver;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DriverBindingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesTelemetryPublishModeFromConfigurationJson() {
        DriverBinding binding = DriverBinding.parse(
                "virtual",
                1000,
                "{\"profile\":\"lab\",\"telemetryPublishMode\":\"TELEMETRY_ONLY\",\"telemetryCoalesceMs\":\"500\"}",
                "{}",
                objectMapper
        );

        assertThat(binding.telemetryPublishMode()).isEqualTo(TelemetryPublishMode.TELEMETRY_ONLY);
        assertThat(binding.telemetryCoalesceMs()).isEqualTo(500);
        assertThat(binding.configuration()).isEqualTo(Map.of("profile", "lab"));
        assertThat(binding.configurationWithPolicy()).containsEntry("telemetryPublishMode", "TELEMETRY_ONLY")
                .containsEntry("telemetryCoalesceMs", "500")
                .containsEntry("profile", "lab");
    }

    @Test
    void defaultsToFullPublishMode() {
        DriverBinding binding = DriverBinding.of("virtual", 1000, Map.of("profile", "lab"), Map.of());

        assertThat(binding.telemetryPublishMode()).isEqualTo(TelemetryPublishMode.FULL);
        assertThat(TelemetryPublishMode.FULL.automationEligible()).isTrue();
    }
}
