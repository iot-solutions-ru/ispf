package com.ispf.server.application.uipack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostedUiPackAgentHtmlTest {

    @Test
    void insertsBeforeBodyWithAppId() {
        String html = HostedUiPackAgentHtml.inject("<html><body>pack</body></html>", "oil-control", true);
        assertTrue(html.contains("data-app-id=\"oil-control\""));
        assertTrue(html.indexOf("operator-agent-fab.js") < html.indexOf("</body>"));
    }

    @Test
    void skipsWhenAlreadyPresentOrDisabled() {
        String once = "<html><script src=\"/ispf-embed/operator-agent-fab.js\"></script></html>";
        assertEquals(once, HostedUiPackAgentHtml.inject(once, "oil-control", true));
        assertEquals("<html></html>", HostedUiPackAgentHtml.inject("<html></html>", "oil-control", false));
    }
}
