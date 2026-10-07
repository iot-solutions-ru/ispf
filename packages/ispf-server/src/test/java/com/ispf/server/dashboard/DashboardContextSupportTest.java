package com.ispf.server.dashboard;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardContextSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blankJsonIsAnEmptySession() {
        Map<String, Object> context = DashboardContextSupport.parseContextJson("  ", objectMapper);

        assertThat(context.get("selection")).isEqualTo(Map.of());
        assertThat(context.get("params")).isEqualTo(Map.of());
        assertThat(context.get("widgets")).isEqualTo(Map.of());
    }

    @Test
    void validJsonKeepsSelection() {
        Map<String, Object> context = DashboardContextSupport.parseContextJson(
                "{\"selection\":{\"id\":\"pump\"},\"params\":{},\"widgets\":{}}",
                objectMapper
        );

        assertThat(context.get("selection")).isEqualTo(Map.of("id", "pump"));
    }

    @Test
    void invalidJsonThrowsWithParserText() {
        assertThatThrownBy(() -> DashboardContextSupport.parseContextJson("{not-json", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> {
                    assertThat(ex.getCause()).isNotNull();
                    assertThat(ex.getCause().getMessage()).isNotBlank();
                    assertThat(ex.getMessage()).isEqualTo(
                            "Invalid dashboard context JSON: " + ex.getCause().getMessage()
                    );
                });
    }
}
