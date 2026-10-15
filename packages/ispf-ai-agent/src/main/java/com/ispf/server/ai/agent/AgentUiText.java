package com.ispf.server.ai.agent;

/**
 * Picks a UI string for the web-console locale ({@code en|ru|de|zh}).
 * Same set as {@code apps/web-console/src/i18n/locales.ts} {@code SUPPORTED_LOCALES}.
 */
public final class AgentUiText {

    public enum Lang {
        EN, RU, DE, ZH
    }

    private AgentUiText() {
    }

    public static Lang lang(String uiLocale) {
        return switch (AgentUiLocalePromptSection.normalize(uiLocale)) {
            case "ru" -> Lang.RU;
            case "de" -> Lang.DE;
            case "zh" -> Lang.ZH;
            default -> Lang.EN;
        };
    }

    public static boolean isRussian(String uiLocale) {
        return lang(uiLocale) == Lang.RU;
    }

    /** en / ru / de / zh — missing slots fall back to English. */
    public static String t(String uiLocale, String en, String ru, String de, String zh) {
        return switch (lang(uiLocale)) {
            case RU -> ru != null && !ru.isBlank() ? ru : en;
            case DE -> de != null && !de.isBlank() ? de : en;
            case ZH -> zh != null && !zh.isBlank() ? zh : en;
            case EN -> en;
        };
    }

    public static String t(String uiLocale, String en, String ru) {
        return t(uiLocale, en, ru, en, en);
    }

    public static String quote(String uiLocale, String value) {
        return switch (lang(uiLocale)) {
            case RU -> "«" + value + "»";
            case DE, EN -> "\"" + value + "\"";
            case ZH -> "「" + value + "」";
        };
    }
}
