package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolResultMetricsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void boundWidgetCountRequiresObjectPathAndVariableNameForValueWidgets() {
        String layout = """
                {"widgets":[
                  {"id":"a","type":"value","objectPath":"root.platform.devices.d1","variableName":"sineWave"},
                  {"id":"b","type":"value","title":"orphan"},
                  {"id":"c","type":"scada-mimic","mimicPath":"root.platform.mimics.fan"}
                ]}
                """;
        assertThat(AgentToolResultMetrics.widgetCountFromLayoutJson(MAPPER, layout)).isEqualTo(3);
        assertThat(AgentToolResultMetrics.bindableWidgetCountFromLayoutJson(MAPPER, layout)).isEqualTo(3);
        assertThat(AgentToolResultMetrics.boundWidgetCountFromLayoutJson(MAPPER, layout)).isEqualTo(2);
    }

    @Test
    void dashboardPeaksTrackMaxVersusLastPerPath() {
        Map<String, int[]> peaks = AgentToolResultMetrics.dashboardWidgetCountPeaks(List.of(
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "arguments", Map.of("path", "root.platform.dashboards.a"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.a", "widgetCount", 9)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "arguments", Map.of("path", "root.platform.dashboards.a"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.a", "widgetCount", 1)
                ),
                Map.of(
                        "type", "tool",
                        "tool", "set_dashboard_layout",
                        "arguments", Map.of("path", "root.platform.dashboards.b"),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.b", "widgetCount", 4)
                )
        ));
        assertThat(peaks.get("root.platform.dashboards.a")).containsExactly(9, 1);
        assertThat(peaks.get("root.platform.dashboards.b")).containsExactly(4, 4);
    }
}
