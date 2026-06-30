package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPlatformTurnGuardTest {

    @Test
    void blocksFinishWhenDeviceCreatedWithoutVariableVerification() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DEVICE", "name", "pump-01"),
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01")
                )
        );

        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps);
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("pump-01");
    }

    @Test
    void allowsFinishWhenListVariablesVerifiedDevice() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DEVICE"),
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "list_variables",
                        "arguments", Map.of("path", "root.platform.devices.pump-01"),
                        "result", Map.of("status", "OK", "count", 8)
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps)).isEmpty();
    }

    @Test
    void allowsFinishWhenCreateVirtualDeviceReturnedTelemetry() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_virtual_device",
                        "arguments", Map.of("profile", "lab"),
                        "result", Map.of(
                                "status", "OK",
                                "path", "root.platform.devices.pump-01",
                                "telemetryVariableCount", 4
                        )
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps)).isEmpty();
    }

    @Test
    void blocksFinishWhenMonitoringIntentWithoutConfigureAlert() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DASHBOARD", "name", "monitoring-main"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.monitoring-main")
                )
        );

        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps, "Сделай мониторинг с дашбордом");
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("configure_alert");
    }

    @Test
    void allowsFinishWhenMonitoringIntentAndAlertConfigured() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("type", "DASHBOARD"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.monitoring-main")
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "arguments", Map.of("name", "temperature-high"),
                        "result", Map.of("status", "OK", "path", "root.platform.alert-rules.temperature-high")
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "monitoring dashboard with alerts")).isEmpty();
    }

    @Test
    void blocksFinishWhenScadaIntentAndMimicDiagramIsEmpty() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "arguments", Map.of("path", "root.platform.mimics.pump-station"),
                        "result", Map.of("status", "OK", "path", "root.platform.mimics.pump-station", "elementCount", 0)
                )
        );

        var block = AgentPlatformTurnGuard.checkBeforeFinish(steps, "Нужна SCADA mimic диаграмма");
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("elementCount=0");
    }

    @Test
    void allowsFinishWhenScadaIntentAndMimicDiagramHasElements() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "get_mimic_diagram",
                        "arguments", Map.of("path", "root.platform.mimics.pump-station"),
                        "result", Map.of("status", "OK", "path", "root.platform.mimics.pump-station", "elementCount", 3)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "configure_alert",
                        "arguments", Map.of("name", "pump-alert"),
                        "result", Map.of("status", "OK", "path", "root.platform.alert-rules.pump-alert")
                )
        );

        assertThat(AgentPlatformTurnGuard.checkBeforeFinish(steps, "build scada mimic dashboard")).isEmpty();
    }
}
