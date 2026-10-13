package com.ispf.server.ai.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic Copilot answer for explain-screen turns — no LLM tools, no discovery loop.
 */
final class AgentCopilotExplainResponder {

    private AgentCopilotExplainResponder() {
    }

    static String buildSummary(Map<String, Object> clientFocus, String uiLocale) {
        Map<?, ?> detail = detailMap(clientFocus);
        String screenTitle = stringVal(detail.get("screenTitle"));
        if (screenTitle.isBlank()) {
            screenTitle = stringVal(clientFocus != null ? clientFocus.get("surface") : null);
        }
        String systemTab = stringVal(detail.get("systemTab"));
        String settingsTab = stringVal(detail.get("settingsTab"));
        String studioTab = stringVal(detail.get("studioTab"));
        String hint = stringVal(detail.get("screenHint"));
        List<String> settingIds = stringList(detail.get("visibleSettingIds"));

        boolean russian = AgentStepHumanizer.isRussian(uiLocale);
        StringBuilder sb = new StringBuilder();
        if (russian) {
            sb.append("### Что на этом экране\n\n");
            sb.append("Открыт экран **").append(screenTitle.isBlank() ? "System / Settings" : screenTitle).append("**");
            if (!settingsTab.isBlank()) {
                sb.append(" (раздел `").append(settingsTab).append("`)");
            } else if (!systemTab.isBlank()) {
                sb.append(" (вкладка `").append(systemTab).append("`)");
            } else if (!studioTab.isBlank()) {
                sb.append(" (Studio: `").append(studioTab).append("`)");
            }
            sb.append(".\n\n");
            if (!hint.isBlank()) {
                sb.append(hint).append("\n\n");
            }
            sb.append("Здесь правятся **runtime-настройки** платформы (значения уходят в `ISPF_*` / overrides). ");
            sb.append("Кнопки **Refresh** / **Save changes** читают и сохраняют строки; ");
            sb.append("жёлтый ярлык **restart required** значит, что после Save нужен перезапуск сервера.\n\n");
            if (!settingIds.isEmpty()) {
                sb.append("### Видимые параметры\n\n");
                for (String id : settingIds) {
                    sb.append("- **`").append(id).append("`** — ").append(describeSetting(id, true)).append('\n');
                }
                sb.append('\n');
            }
            sb.append("Если нужно изменить значение — выберите строку, задайте значение, **Save changes**, ");
            sb.append("при необходимости **Restart server**.");
        } else {
            sb.append("### What this screen is\n\n");
            sb.append("You are on **").append(screenTitle.isBlank() ? "System / Settings" : screenTitle).append("**");
            if (!settingsTab.isBlank()) {
                sb.append(" (section `").append(settingsTab).append("`)");
            } else if (!systemTab.isBlank()) {
                sb.append(" (tab `").append(systemTab).append("`)");
            } else if (!studioTab.isBlank()) {
                sb.append(" (Studio: `").append(studioTab).append("`)");
            }
            sb.append(".\n\n");
            if (!hint.isBlank()) {
                sb.append(hint).append("\n\n");
            }
            sb.append("These are **runtime platform settings** (mapped to `ISPF_*` / override file). ");
            sb.append("Use **Refresh** / **Save changes** to read and persist rows; ");
            sb.append("a yellow **restart required** tag means a server restart is needed after Save.\n\n");
            if (!settingIds.isEmpty()) {
                sb.append("### Visible settings\n\n");
                for (String id : settingIds) {
                    sb.append("- **`").append(id).append("`** — ").append(describeSetting(id, false)).append('\n');
                }
                sb.append('\n');
            }
            sb.append("To change a value: edit the row → **Save changes** → **Restart server** if tagged.");
        }
        return sb.toString().trim();
    }

    static Map<String, Object> buildFinishResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("copilotExplainDirect", true);
        return result;
    }

    private static String describeSetting(String id, boolean russian) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "ai.enabled" -> russian
                    ? "включает AI-функции платформы (`ISPF_AI_ENABLED`)"
                    : "turns platform AI features on/off (`ISPF_AI_ENABLED`)";
            case "ai.provider" -> russian
                    ? "провайдер LLM (`openai-compatible`, и т.п.)"
                    : "LLM provider id (`openai-compatible`, etc.)";
            case "ai.base-url" -> russian
                    ? "базовый URL API провайдера"
                    : "provider API base URL";
            case "ai.model" -> russian
                    ? "имя модели у провайдера"
                    : "model name at the provider";
            case "ai.api-key" -> russian
                    ? "секрет API (не показывается в логах)"
                    : "API secret (never logged in clear text)";
            case "ai.timeout-seconds" -> russian
                    ? "таймаут запроса к LLM, секунды"
                    : "LLM request timeout in seconds";
            case "ai.max-tokens" -> russian
                    ? "лимит токенов ответа модели"
                    : "max completion tokens";
            case "ai.temperature" -> russian
                    ? "температура сэмплирования"
                    : "sampling temperature";
            case "ai.agent-max-steps" -> russian
                    ? "макс. число шагов агента за turn"
                    : "max agent tool steps per turn";
            case "ai.agent-max-history-turns" -> russian
                    ? "сколько прошлых turn держать в контексте Studio"
                    : "how many prior Studio turns stay in context";
            case "mcp.enabled" -> russian
                    ? "включает MCP-сервер платформы"
                    : "enables the platform MCP server";
            case "mcp.stdio-enabled" -> russian
                    ? "разрешает MCP через stdio"
                    : "allows MCP over stdio";
            default -> russian
                    ? "runtime-параметр (см. подпись строки и env `ISPF_*`)"
                    : "runtime setting (see row label / `ISPF_*` env)";
        };
    }

    private static Map<?, ?> detailMap(Map<String, Object> clientFocus) {
        if (clientFocus == null) {
            return Map.of();
        }
        Object detail = clientFocus.get("detail");
        return detail instanceof Map<?, ?> map ? map : Map.of();
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String value = String.valueOf(item).trim();
            if (!value.isBlank()) {
                out.add(value);
            }
        }
        return List.copyOf(out);
    }

    private static String stringVal(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }
}
