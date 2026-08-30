package com.ispf.server.ai.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: operator-visible Russian / punctuation literals must be real UTF-8,
 * not double-encoded CP1251 mojibake ({@code РџРѕвЂ¦} / {@code вЂ”}).
 */
class AgentOperatorVisibleUtf8Test {

    @Test
    void treeFirstAgentServiceUsesCyrillicNotMojibake() throws Exception {
        String src = readMainSource("com/ispf/server/ai/agent/TreeFirstAgentService.java");

        assertThat(src)
                .as("status label")
                .contains("Подготовка запроса…")
                .doesNotContain("РџРѕРґРіРѕС‚РѕРІРєР°");

        assertThat(src)
                .as("soft step-limit summary")
                .contains("мягкий лимит")
                .contains("Продолжай")
                .doesNotContain("РјСЏРіРєРёР№");

        assertThat(src)
                .as("no classic UTF-8-as-CP1251 punctuation mojibake")
                .doesNotContain("вЂ")
                .doesNotContain("в‰");
    }

    @Test
    void platformBriefingServiceUsesTypographicPunctuationNotMojibake() throws Exception {
        String src = readMainSource("com/ispf/server/ai/context/PlatformBriefingService.java");

        assertThat(src).contains(" — ").doesNotContain("вЂ”");
        assertThat(src).contains("… (truncated)").doesNotContain("вЂ¦");
    }

    private static String readMainSource(String relativeUnderMainJava) throws Exception {
        Path moduleRoot = Path.of(System.getProperty("user.dir"));
        Path candidate = moduleRoot.resolve("src/main/java").resolve(relativeUnderMainJava);
        if (!Files.isRegularFile(candidate)) {
            candidate = moduleRoot.resolve("packages/ispf-ai-agent/src/main/java").resolve(relativeUnderMainJava);
        }
        assertThat(candidate).as("source file %s", relativeUnderMainJava).exists();
        return Files.readString(candidate, StandardCharsets.UTF_8);
    }
}
