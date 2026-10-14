package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLiveFinishVerifierTest {

    @Test
    void missingClaimedPathsAreIssues() {
        List<String> issues = AgentLiveFinishVerifier.verify(
                Map.of(
                        "devices", List.of("root.platform.devices.virt-device-1"),
                        "dashboards", List.of("root.platform.dashboards.main"),
                        "completed", 18
                ),
                List.of(),
                "Выполни ВСЕ задания: дашборд и мнемосхема",
                path -> false,
                path -> Optional.empty(),
                path -> Optional.empty()
        );
        assertThat(issues).anyMatch(i -> i.startsWith("live.missing"));
        assertThat(issues).anyMatch(i -> i.startsWith("live.completed"));
    }

    @Test
    void liveDashboardAndMimicWithBindingsPassCourseGate() {
        String layout = """
                {"widgets":[
                  {"id":"a","type":"value","objectPath":"root.platform.devices.virt-device-1","variableName":"sineWave"},
                  {"id":"b","type":"chart","objectPath":"root.platform.devices.virt-device-1","variableName":"sineWave"},
                  {"id":"c","type":"scada-mimic","mimicPath":"root.platform.mimics.fan"}
                ]}
                """;
        String diagram = """
                {"elements":[
                  {"id":"1","symbolId":"pack.ispf-pid.fan","bindings":{"running":{"objectPath":"root.platform.devices.virt-device-1","variableName":"deviceActive"}}},
                  {"id":"2","symbolId":"pump.centrifugal","bindings":{"running":{"objectPath":"root.platform.devices.virt-device-1","variableName":"deviceActive"}}}
                ]}
                """;
        Set<String> live = Set.of(
                "root.platform.devices.virt-device-1",
                "root.platform.devices.virt-device-2",
                "root.platform.dashboards.main",
                "root.platform.mimics.fan",
                "root.platform.correlators.virt-event1-unlatch",
                "root.platform.singleton-blueprints.virt-calculator-hub"
        );
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "configure_correlator",
                        "result", Map.of("status", "OK", "path", "root.platform.correlators.virt-event1-unlatch")
                )
        );
        List<String> issues = AgentLiveFinishVerifier.verify(
                Map.of(
                        "devices", List.of(
                                "root.platform.devices.virt-device-1",
                                "root.platform.devices.virt-device-2"
                        ),
                        "dashboards", List.of("root.platform.dashboards.main"),
                        "mimic", "root.platform.mimics.fan",
                        "correlators", List.of("root.platform.correlators.virt-event1-unlatch"),
                        "hub", "root.platform.singleton-blueprints.virt-calculator-hub",
                        "completed", 18
                ),
                steps,
                "Выполни ВСЕ задания: дашборд и мнемосхема вентилятора",
                live::contains,
                path -> path.contains("dashboards") ? Optional.of(layout) : Optional.empty(),
                path -> path.contains("mimics") ? Optional.of(diagram) : Optional.empty()
        );
        assertThat(issues).isEmpty();
    }

    @Test
    void unrenderableMimicSymbolsFailLiveCheck() {
        String diagram = """
                {"elements":[
                  {"id":"1","symbolId":"button.start","bindings":{}},
                  {"id":"2","symbolId":"label","bindings":{}}
                ]}
                """;
        List<String> issues = AgentLiveFinishVerifier.verify(
                Map.of("mimics", List.of("root.platform.mimics.fan")),
                List.of(),
                "Создай мнемосхему вентилятора",
                path -> true,
                path -> Optional.empty(),
                path -> Optional.of(diagram)
        );
        assertThat(issues).anyMatch(i -> i.startsWith("live.mimic.symbols"));
    }

    @Test
    void courseRejectsHubOutsideSingletonBlueprints() {
        List<String> issues = AgentLiveFinishVerifier.verify(
                Map.of(
                        "devices", List.of(
                                "root.platform.devices.virt-device-1",
                                "root.platform.devices.virt-device-2"
                        ),
                        "dashboards", List.of("root.platform.dashboards.main"),
                        "hub", "root.platform.virt-hub",
                        "correlators", List.of("root.platform.correlators.x")
                ),
                List.of(
                        Map.of(
                                "type", "tool",
                                "tool", "configure_correlator",
                                "result", Map.of("status", "OK")
                        )
                ),
                "Выполни ВСЕ задания калькулятор",
                path -> true,
                path -> Optional.of("{\"widgets\":[{\"id\":\"a\",\"type\":\"value\",\"objectPath\":\"d\",\"variableName\":\"v\"}]}"),
                path -> Optional.empty()
        );
        assertThat(issues).anyMatch(i -> i.startsWith("live.course.hub"));
    }

    @Test
    void emptyDashboardFailsLiveCheck() {
        List<String> issues = AgentLiveFinishVerifier.verify(
                Map.of("dashboards", List.of("root.platform.dashboards.empty")),
                List.of(),
                "Создай дашборд",
                path -> true,
                path -> Optional.of("{\"widgets\":[]}"),
                path -> Optional.empty()
        );
        assertThat(issues).anyMatch(i -> i.startsWith("live.dashboard.empty"));
    }
}
