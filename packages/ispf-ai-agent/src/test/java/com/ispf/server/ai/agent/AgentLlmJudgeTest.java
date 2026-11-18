package com.ispf.server.ai.agent;

import com.ispf.server.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLlmJudgeTest {

    @Test
    void parseApproveAndRework() {
        AgentLlmJudge.Opinion approved = AgentLlmJudge.parse("""
                {"verdict":"approve","reason":"The user asked how a device is created."}
                """);
        assertThat(approved.approved()).isTrue();
        assertThat(approved.reason()).contains("how a device");

        AgentLlmJudge.Opinion rework = AgentLlmJudge.parse(
                "```json\n{\"verdict\":\"rework\",\"reason\":\"The pump station was not created.\"}\n```"
        );
        assertThat(rework.approved()).isFalse();
        assertThat(rework.reason()).contains("pump station");
    }

    @Test
    void unreadableReplyKeepsTheRuleVerdict() {
        AgentLlmJudge.Opinion opinion = AgentLlmJudge.parse("I think this is fine.");
        assertThat(opinion.approved()).isFalse();
        assertThat(opinion.reason()).isNull();
    }

    @Test
    void lastVerdictWinsOverAnEarlierDraft() {
        AgentLlmJudge.Opinion opinion = AgentLlmJudge.parse("""
                {"verdict":"approve","reason":"draft"}
                {"verdict":"rework","reason":"The pump station was not created."}
                """);
        assertThat(opinion.approved()).isFalse();
        assertThat(opinion.reason()).contains("pump station");
    }

    @Test
    void hardEvidenceIsNotSentToTheModel() {
        String howTo = "Сможешь показать как создается мнемосхема на платформе?";
        String build = "Создай насосную станцию и покажи мнемосхему";
        assertThat(AgentLlmJudge.canConsult(List.of(
                "mimic.elements: elementCount is 0"
        ), howTo)).isTrue();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "S2: get_mimic_diagram elementCount>0"
        ), "How does a mimic diagram work?")).isTrue();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "S1: elementCount must be > 0"
        ), build)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "Turn contains ERROR tool steps — fix before finish"
        ), howTo)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "live.missing: finish claimed paths not in tree: root.devices.pump"
        ), howTo)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "live.mimic.symbols: unrenderable symbolId"
        ), howTo)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "live.completed: finish.result.completed=5 but live artifacts are incomplete"
        ), howTo)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "dashboard.layout.regression: layout shrank"
        ), howTo)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(
                "logic.host: hub must not be DEVICE",
                "dashboard.widgets: widgetCount is 0"
        ), howTo)).isFalse();
        assertThat(AgentLlmJudge.canConsult(List.of(), howTo)).isFalse();
    }

    @Test
    void honestyCheckCoversFailedStepsButNotOtherHardEvidence() {
        String build = "Создай устройство audit-sensor-01";
        assertThat(AgentLlmJudge.canConsultHonesty(List.of(
                "Turn contains ERROR tool steps — fix before finish",
                "errors.none: Cannot finish: turn contains ERROR tool steps: create_object: Object exists"
        ), build)).isTrue();
        assertThat(AgentLlmJudge.canConsultHonesty(List.of(
                "Turn contains ERROR tool steps — fix before finish",
                "live.missing: finish claimed paths not in tree: root.devices.pump"
        ), build)).isFalse();
        assertThat(AgentLlmJudge.canConsultHonesty(List.of(
                "Turn contains ERROR tool steps — fix before finish",
                "logic.host: hub must not be DEVICE"
        ), build)).isFalse();
        assertThat(AgentLlmJudge.canConsultHonesty(List.of("mimic.elements: elementCount is 0"), build)).isFalse();
        assertThat(AgentLlmJudge.canConsultHonesty(List.of(
                "Turn contains ERROR tool steps — fix before finish",
                "S1: get_mimic_diagram elementCount>0"
        ), "Создай мнемосхему насосной станции")).isTrue();
    }

    @Test
    void honestyCallIsVisibleAndParsed() throws Exception {
        AiProperties properties = new AiProperties();
        com.ispf.server.ai.llm.LlmProviderRegistry registry =
                org.mockito.Mockito.mock(com.ispf.server.ai.llm.LlmProviderRegistry.class);
        org.mockito.Mockito.when(registry.isGenerationAvailable()).thenReturn(true);
        org.mockito.Mockito.when(registry.complete(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.ispf.ai.LlmResponse(
                        "{\"verdict\":\"approve\",\"reason\":\"The finish says create_object failed.\"}", "m", null));

        AgentLlmJudge.Opinion opinion = AgentLlmJudge.reviewHonesty(
                registry,
                properties,
                "Создай устройство audit-sensor-01",
                "Не удалось создать устройство: create_object вернул ошибку.",
                List.of(),
                List.of("Turn contains ERROR tool steps — fix before finish")
        );
        assertThat(opinion.approved()).isTrue();
        assertThat(opinion.consulted()).isTrue();
    }

    @Test
    void disabledFlagDoesNotCallTheModel() {
        AiProperties properties = new AiProperties();
        properties.setAgentLlmJudge(false);
        AgentLlmJudge.Opinion opinion = AgentLlmJudge.review(
                null,
                properties,
                "Сможешь показать как создается устройство?",
                "Сначала создайте объект.",
                List.of(),
                List.of("mimic.elements: elementCount is 0")
        );
        assertThat(opinion.approved()).isFalse();
        assertThat(opinion.reason()).isNull();
    }
}
