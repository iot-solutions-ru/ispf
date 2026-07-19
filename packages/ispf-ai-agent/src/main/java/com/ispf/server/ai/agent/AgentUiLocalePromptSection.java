package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Map;

/**
 * Forces agent user-facing text to match the web-console UI locale (ADR-0051 / i18n).
 */
public final class AgentUiLocalePromptSection {

    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "en", "English",
            "ru", "Russian",
            "de", "German",
            "zh", "Chinese (Simplified)"
    );

    private AgentUiLocalePromptSection() {
    }

    /** Normalize BCP-47 / console locale to supported primary tag (en|ru|de|zh). */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim().replace('_', '-');
        String primary = trimmed.split("-", 2)[0].toLowerCase(Locale.ROOT);
        if (LANGUAGE_NAMES.containsKey(primary)) {
            return primary;
        }
        return "";
    }

    public static String languageName(String normalizedLocale) {
        String key = normalize(normalizedLocale);
        if (key.isBlank()) {
            return "";
        }
        return LANGUAGE_NAMES.getOrDefault(key, key);
    }

    /**
     * Hard rule for the top of the system prompt. Empty when locale unknown
     * (legacy clients) — then fall back to matching the user's message language.
     */
    public static String format(String uiLocale) {
        String locale = normalize(uiLocale);
        if (locale.isBlank()) {
            return """
                    ## Response language
                    Match the language of the user's latest message for finish summaries, questions, and suggestions.
                    Do not switch languages mid-conversation unless the user does.
                    """;
        }
        String name = languageName(locale);
        return """
                ## Response language (UI locale — mandatory)
                The web console is currently displayed in **%s** (locale code: `%s`).
                Write ALL user-facing text in **%s**: finish summary, clarifying questions, plan narrative,
                suggestion labels, and error explanations for the user.
                Do NOT answer in another language even if the user message or prior turns used a different one.
                Tool names, JSON keys, object paths, and code identifiers stay in English as usual.
                """.formatted(name, locale, name);
    }
}
