package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentProseToolSuggestGuardTest {

    @Test
    void rejectsRussianUseToolFinishWhenToolNotCalled() {
        String summary = "Для получения информации о конфигурации драйвера WirelessHART Gateway "
                + "используйте команду `list_drivers` с фильтром или `search_context topic=drivers "
                + "query=wireless-hart-gateway`.";
        var reject = AgentProseToolSuggestGuard.rejectFinish(summary, List.of());
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("Emit {\"type\":\"tool\"");
        assertThat(reject.get()).containsAnyOf("list_drivers", "search_context");
    }

    @Test
    void rejectsCapitalizedCyrillicInLongSummary() {
        String padding = "а".repeat(950);
        String summary = padding + " Используйте `get_driver_help` с параметром driverId=wirelesshart.";
        var reject = AgentProseToolSuggestGuard.rejectFinish(summary, List.of());
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("get_driver_help");
    }

    @Test
    void rejectsEnglishUseBacktickTool() {
        String summary = "Use `list_drivers` to see loaded drivers for WirelessHART.";
        var reject = AgentProseToolSuggestGuard.rejectFinish(summary, List.of());
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("list_drivers");
    }

    @Test
    void rejectsCliLikeToolLineInFinish() {
        String summary = "search_context topic=drivers query=wireless-hart-gateway";
        var reject = AgentProseToolSuggestGuard.rejectFinish(summary, List.of());
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("search_context");
    }

    @Test
    void rejectsBulletedBacktickedCliLine() {
        String summary = "- `search_context topic=drivers query=hart`";
        var reject = AgentProseToolSuggestGuard.rejectFinish(summary, List.of());
        assertThat(reject).isPresent();
        assertThat(reject.get()).contains("search_context");
    }

    @Test
    void doesNotMatchGetObjectInsideLongerToolName() {
        String summary = "Используйте `get_object_blueprint` для схемы узла.";
        assertThat(AgentProseToolSuggestGuard.suggestedDiscoveryTools(summary)).isEmpty();
        assertThat(AgentProseToolSuggestGuard.rejectFinish(summary, List.of())).isEmpty();
    }

    @Test
    void allowsFinishAfterSuccessfulDiscoveryTool() {
        String summary = "Используйте `get_driver_help` только как справку: ниже уже defaults из результата.";
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "get_driver_help",
                "result", Map.of("status", "OK", "driverId", "wirelesshart")
        ));
        assertThat(AgentProseToolSuggestGuard.rejectFinish(summary, steps)).isEmpty();
    }

    @Test
    void allowsBundleHowToMentioningValidateAndExport() {
        String summary = """
                1. **Pack**: используйте `ispf pack` или `export_application_bundle`
                2. **Validate**: `validate_bundle`, затем `dry_run_deploy`
                3. Deploy via Marketplace or `import_package` in Execute
                """;
        List<Map<String, Object>> steps = List.of(Map.of(
                "type", "tool",
                "tool", "search_context",
                "result", Map.of("status", "OK", "hitCount", 2)
        ));
        assertThat(AgentProseToolSuggestGuard.rejectFinish(summary, steps)).isEmpty();
        assertThat(AgentProseToolSuggestGuard.rejectFinish(summary, List.of())).isEmpty();
    }

    @Test
    void skipsToolsNotCallableOnCurrentSurface() {
        String summary = "Use `get_automation_schema` topic=dashboard for widget fields.";
        var reject = AgentProseToolSuggestGuard.rejectFinish(
                summary,
                List.of(),
                tool -> !"get_automation_schema".equals(tool)
        );
        assertThat(reject).isEmpty();
    }

    @Test
    void alreadyNudgedReadsFlag() {
        assertThat(AgentProseToolSuggestGuard.alreadyNudged(List.of())).isFalse();
        assertThat(AgentProseToolSuggestGuard.alreadyNudged(List.of(
                Map.of("type", "guard", "proseToolSuggestNudge", true)
        ))).isTrue();
    }
}
