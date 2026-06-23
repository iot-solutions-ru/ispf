package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Map;

public final class AgentStepHumanizer {

    private AgentStepHumanizer() {
    }

    public static String label(
            String type,
            String tool,
            Map<String, Object> arguments,
            Map<String, Object> result,
            String summary
    ) {
        if ("finish".equalsIgnoreCase(type)) {
            return summary != null && !summary.isBlank()
                    ? summary
                    : "Задача выполнена.";
        }
        if (tool == null) {
            return "Шаг агента";
        }
        return switch (tool) {
            case "search_context" -> "Ищу в документации: «" + arg(arguments, "query") + "»";
            case "list_objects" -> "Смотрю содержимое «" + orDefault(arg(arguments, "parent"), "root") + "»";
            case "get_object" -> "Открываю объект «" + arg(arguments, "path") + "»";
            case "create_object" -> "Создаю " + arg(arguments, "type") + " «" + arg(arguments, "name") + "»";
            case "delete_object" -> "Удаляю объект «" + arg(arguments, "path") + "»";
            case "get_dashboard_layout" -> "Читаю layout дашборда «" + orDefault(arg(arguments, "path"), arg(arguments, "template")) + "»";
            case "set_dashboard_layout" -> "Обновляю layout «" + arg(arguments, "path") + "»";
            case "add_dashboard_widget" -> "Добавляю виджет на «" + arg(arguments, "path") + "»";
            case "get_widget_catalog" -> widgetCatalogLabel(arguments);
            case "get_automation_schema" -> "Справочник: " + orDefault(arg(arguments, "topic"), "all");
            case "list_variables" -> "Читаю переменные «" + arg(arguments, "path") + "»";
            case "set_variable" -> "Обновляю «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»";
            case "configure_driver" -> "Настраиваю драйвер на «" + arg(arguments, "devicePath") + "»";
            case "driver_control" -> driverControlLabel(arguments, result);
            case "validate_bundle" -> "Проверяю bundle «" + arg(arguments, "appId") + "»";
            case "dry_run_deploy" -> "Dry-run деплоя «" + arg(arguments, "appId") + "»";
            case "import_package" -> "Импортирую пакет «" + orDefault(arg(arguments, "packageId"), arg(arguments, "appId")) + "»";
            default -> "Вызов " + tool;
        };
    }

    private static String widgetCatalogLabel(Map<String, Object> arguments) {
        String type = arg(arguments, "type");
        String binding = arg(arguments, "binding");
        if (!type.isBlank()) {
            return "Справочник виджетов: тип «" + type + "»";
        }
        if (!binding.isBlank()) {
            return "Справочник виджетов: привязка «" + binding + "»";
        }
        return "Справочник всех виджетов дашборда";
    }

    private static String driverControlLabel(Map<String, Object> arguments, Map<String, Object> result) {
        String action = arg(arguments, "action").toLowerCase(Locale.ROOT);
        String path = arg(arguments, "devicePath");
        String status = result != null ? String.valueOf(result.getOrDefault("connected", "")) : "";
        return switch (action) {
            case "start" -> "Запускаю опрос драйвера «" + path + "»";
            case "stop" -> "Останавливаю драйвер «" + path + "»";
            case "poll" -> "Запрашиваю мгновенный poll «" + path + "»";
            default -> "Статус драйвера «" + path + "»" + (status.isBlank() ? "" : " (connected=" + status + ")");
        };
    }

    private static String arg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String orDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
