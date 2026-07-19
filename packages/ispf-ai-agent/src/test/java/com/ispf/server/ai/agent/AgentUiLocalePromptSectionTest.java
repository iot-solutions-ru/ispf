package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentUiLocalePromptSectionTest {

    @Test
    void normalizesBcp47Tags() {
        assertThat(AgentUiLocalePromptSection.normalize("ru-RU")).isEqualTo("ru");
        assertThat(AgentUiLocalePromptSection.normalize("en")).isEqualTo("en");
        assertThat(AgentUiLocalePromptSection.normalize("zh-Hans")).isEqualTo("zh");
        assertThat(AgentUiLocalePromptSection.normalize("de_DE")).isEqualTo("de");
        assertThat(AgentUiLocalePromptSection.normalize("fr")).isBlank();
    }

    @Test
    void formatRequiresUiLocaleLanguage() {
        String block = AgentUiLocalePromptSection.format("ru");
        assertThat(block).contains("Russian");
        assertThat(block).contains("`ru`");
        assertThat(block).contains("Do NOT answer in another language");
    }

    @Test
    void formatWithoutLocaleFallsBackToMessageLanguage() {
        String block = AgentUiLocalePromptSection.format(null);
        assertThat(block).contains("Match the language of the user's latest message");
    }
}
