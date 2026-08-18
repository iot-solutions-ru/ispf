package com.ispf.server.application.uipack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HostedUiPackOperatorAgentInjectorTest {

    @Test
    void injectsWidgetBeforeBodyClose() {
        String html = "<!doctype html><html><body><h1>Oil Control</h1></body></html>";
        String out = HostedUiPackOperatorAgentInjector.inject(html, "oil-control");
        assertThat(out).contains("data-ispf-app-id=\"oil-control\"");
        assertThat(out).contains("/apps/_platform/operator-agent-widget.js");
        assertThat(out).contains("</script>\n</body>");
    }

    @Test
    void skipsWhenPackOptsOut() {
        String html = "<html><!-- ispf-skip-operator-agent --><body>ok</body></html>";
        assertThat(HostedUiPackOperatorAgentInjector.inject(html, "demo")).isEqualTo(html);
    }

    @Test
    void skipsWhenAlreadyInjected() {
        String html = "<html><body><script src=\"/apps/_platform/operator-agent-widget.js\"></script></body></html>";
        assertThat(HostedUiPackOperatorAgentInjector.inject(html, "demo")).isEqualTo(html);
    }

    @Test
    void platformAssetOnlyAllowsWidgetFile() {
        assertThat(HostedUiPackOperatorAgentInjector.isPlatformAsset("_platform", "operator-agent-widget.js")).isTrue();
        assertThat(HostedUiPackOperatorAgentInjector.isPlatformAsset("_platform", "evil.js")).isFalse();
        assertThat(HostedUiPackOperatorAgentInjector.isPlatformAsset("oil-control", "operator-agent-widget.js")).isFalse();
    }

    @Test
    void widgetDiscoversPackLoginTokens() throws Exception {
        byte[] bytes;
        try (var in = HostedUiPackOperatorAgentInjector.class.getResourceAsStream(
                "/uipack-platform/operator-agent-widget.js"
        )) {
            assertThat(in).isNotNull();
            bytes = in.readAllBytes();
        }
        String js = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(js).contains("oca_token");
        assertThat(js).contains("ispf-auth-session");
        assertThat(js).contains("looksLikeSessionToken");
    }
}
