package com.ispf.server.application.uipack;

/**
 * Inserts the platform operator-agent FAB into hosted ui-pack HTML.
 * The script is a same-origin file (CSP {@code script-src 'self'}); no inline script.
 */
public final class HostedUiPackAgentHtml {

    public static final String SCRIPT_MARKER = "operator-agent-fab.js";

    private HostedUiPackAgentHtml() {
    }

    public static String inject(String html, String appId, boolean enabled) {
        if (!enabled || html == null || html.isEmpty() || appId == null || appId.isBlank()) {
            return html;
        }
        if (html.contains(SCRIPT_MARKER)) {
            return html;
        }
        String tag = "<script src=\"/ispf-embed/operator-agent-fab.js\" data-app-id=\""
                + appId
                + "\" data-api-base=\"/api/v1\" defer></script>";
        int body = indexOfIgnoreCase(html, "</body>");
        if (body >= 0) {
            return html.substring(0, body) + tag + html.substring(body);
        }
        return html + tag;
    }

    private static int indexOfIgnoreCase(String html, String needle) {
        return html.toLowerCase(java.util.Locale.ROOT).indexOf(needle.toLowerCase(java.util.Locale.ROOT));
    }
}
