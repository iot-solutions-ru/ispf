package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptanceVerdictTest {

    @Test
    void unverifiedDeviceIsPartialUntilListVariablesShowsAShortfall() {
        var missing = AcceptanceVerdict.evaluate(List.of(device("root.platform.devices.pump-01")), "", null);
        assertThat(missing.status()).isEqualTo(AcceptanceVerdict.Status.PARTIAL);
        assertThat(missing.firstBlocking().id()).isEqualTo("device.telemetry");

        var shortfall = AcceptanceVerdict.evaluate(List.of(
                device("root.platform.devices.pump-01"),
                Map.of(
                        "type", "tool",
                        "tool", "list_variables",
                        "arguments", Map.of("path", "root.platform.devices.pump-01"),
                        "result", Map.of("status", "OK", "count", 1)
                )
        ), "", null);
        assertThat(shortfall.status()).isEqualTo(AcceptanceVerdict.Status.FAIL);
        assertThat(shortfall.firstBlocking().id()).isEqualTo("device.telemetry");
    }

    @Test
    void emptyMimicIsFailAndMissingDiagramIsPartial() {
        var empty = AcceptanceVerdict.evaluate(List.of(Map.of(
                "type", "tool",
                "tool", "get_mimic_diagram",
                "result", Map.of("status", "OK", "elementCount", 0)
        )), "SCADA", null);
        assertThat(empty.status()).isEqualTo(AcceptanceVerdict.Status.FAIL);
        assertThat(empty.firstBlocking().id()).isEqualTo("mimic.elements");

        var missing = AcceptanceVerdict.evaluate(List.of(Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of("type", "MIMIC"),
                "result", Map.of("status", "OK", "path", "root.platform.mimics.plant")
        )), "SCADA", null);
        assertThat(missing.status()).isEqualTo(AcceptanceVerdict.Status.PARTIAL);
        assertThat(missing.firstBlocking().hint()).contains("docRef=0051");
    }

    @Test
    void dashboardOnlyDoesNotRequireAlert() {
        var verdict = AcceptanceVerdict.evaluate(List.of(
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "result", Map.of("status", "OK", "widgetCount", 2)
                )
        ), "Создай дашборд для устройства", null);
        assertThat(verdict.allowsFinish()).isTrue();
        assertThat(verdict.checks()).extracting(AcceptanceVerdict.Check::id)
                .contains("dashboard.widgets")
                .doesNotContain("alert.configured");
    }

    @Test
    void applicationBundleWithoutTestsIsPartial() {
        var verdict = AcceptanceVerdict.evaluate(List.of(Map.of(
                "type", "tool",
                "tool", "validate_bundle",
                "result", Map.of("status", "OK")
        )), "deploy", "application_bundle");
        assertThat(verdict.status()).isEqualTo(AcceptanceVerdict.Status.PARTIAL);
        assertThat(verdict.firstBlocking().id()).isEqualTo("bundle.tests");
        assertThat(verdict.toMap()).containsEntry("status", "PARTIAL");
    }

    @Test
    void passRecordsTheChecksThatWereInScope() {
        var verdict = AcceptanceVerdict.evaluate(List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_virtual_device",
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01", "telemetryVariableCount", 4)
                )
        ), "", null);
        assertThat(verdict.status()).isEqualTo(AcceptanceVerdict.Status.PASS);
        assertThat(verdict.checks()).extracting(AcceptanceVerdict.Check::id).containsExactly("errors.none");
    }

    private static Map<String, Object> device(String path) {
        return Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of("type", "DEVICE"),
                "result", Map.of("status", "OK", "path", path)
        );
    }
}
