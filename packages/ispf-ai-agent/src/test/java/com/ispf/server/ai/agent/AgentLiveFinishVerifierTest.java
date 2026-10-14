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
                  {"id":"1","symbolId":"motor","bindings":{"running":{"objectPath":"root.platform.devices.virt-device-1","variableName":"deviceActive"}}},
                  {"id":"2","symbolId":"label","bindings":{"text":{"value":"Fan"}}}
                ]}
                """;
        Set<String> live = Set.of(
                "root.platform.devices.virt-device-1",
                "root.platform.devices.virt-device-2",
                "root.platform.dashboards.main",
                "root.platform.mimics.fan"
        );
        List<String> issues = AgentLiveFinishVerifier.verify(
                Map.of(
                        "devices", List.of(
                                "root.platform.devices.virt-device-1",
                                "root.platform.devices.virt-device-2"
                        ),
                        "dashboards", List.of("root.platform.dashboards.main"),
                        "mimic", "root.platform.mimics.fan",
                        "completed", 18
                ),
                List.of(),
                "Выполни ВСЕ задания: дашборд и мнемосхема вентилятора",
                live::contains,
                path -> path.contains("dashboards") ? Optional.of(layout) : Optional.empty(),
                path -> path.contains("mimics") ? Optional.of(diagram) : Optional.empty()
        );
        assertThat(issues).isEmpty();
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
