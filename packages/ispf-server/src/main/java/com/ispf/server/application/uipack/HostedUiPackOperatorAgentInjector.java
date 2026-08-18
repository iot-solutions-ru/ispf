package com.ispf.server.application.uipack;

import java.util.Locale;

/**
 * Injects the platform operator-agent widget into hosted UI pack HTML (ADR-0054).
 * Packs can opt out with {@code ispf-skip-operator-agent} in the document.
 */
public final class HostedUiPackOperatorAgentInjector {

    public static final String PLATFORM_APP_ID = "_platform";
    public static final String WIDGET_FILE = "operator-agent-widget.js";
    public static final String WIDGET_SRC = "/apps/_platform/" + WIDGET_FILE + "?v=2";
    public static final String SKIP_MARKER = "ispf-skip-operator-agent";

    private HostedUiPackOperatorAgentInjector() {
    }

    public static boolean isPlatformAsset(String appId, String relative) {
        return PLATFORM_APP_ID.equals(appId) && WIDGET_FILE.equals(relative);
    }

    public static boolean shouldInject(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        if (lower.contains(SKIP_MARKER) || lower.contains("operator-agent-widget.js")) {
            return false;
        }
        return lower.contains("<html") || lower.contains("<body") || lower.contains("<!doctype");
    }

    public static String inject(String html, String appId) {
        if (!shouldInject(html) || appId == null || appId.isBlank()) {
            return html;
        }
        String tag = "<script src=\"" + WIDGET_SRC + "\" data-ispf-app-id=\""
                + escapeAttr(appId.trim()) + "\" defer></script>\n";
        String lower = html.toLowerCase(Locale.ROOT);
        int body = lower.lastIndexOf("</body>");
        if (body >= 0) {
            return html.substring(0, body) + tag + html.substring(body);
        }
        return html + tag;
    }

    static String escapeAttr(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
