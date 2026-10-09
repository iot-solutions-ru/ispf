package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Map;

/**
 * Human-readable agent step labels for the web console.
 * Language follows UI locale ({@code en|ru|de|zh}); non-{@code ru} uses English.
 */
public final class AgentStepHumanizer {

    private AgentStepHumanizer() {
    }

    public static String label(
            String type,
            String tool,
            Map<String, Object> arguments,
            Map<String, Object> result,
            String summary,
            String uiLocale
    ) {
        boolean ru = isRussian(uiLocale);
        if ("finish".equalsIgnoreCase(type)) {
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
            return ru ? "Задача выполнена." : "Task completed.";
        }
        if (tool == null) {
            return ru ? "Шаг агента" : "Agent step";
        }
        return switch (tool) {
            case "search_context" -> ru
                    ? "Ищу в документации: «" + arg(arguments, "query") + "»"
                    : "Searching docs: \"" + arg(arguments, "query") + "\"";
            case "list_objects" -> ru
                    ? "Смотрю содержимое «" + orDefault(arg(arguments, "parent"), "root") + "»"
                    : "Listing contents of \"" + orDefault(arg(arguments, "parent"), "root") + "\"";
            case "get_object" -> ru
                    ? "Открываю объект «" + arg(arguments, "path") + "»"
                    : "Opening object \"" + arg(arguments, "path") + "\"";
            case "create_object" -> ru
                    ? "Создаю " + arg(arguments, "type") + " «" + arg(arguments, "name") + "»"
                    : "Creating " + arg(arguments, "type") + " \"" + arg(arguments, "name") + "\"";
            case "delete_object" -> ru
                    ? "Удаляю объект «" + arg(arguments, "path") + "»"
                    : "Deleting object \"" + arg(arguments, "path") + "\"";
            case "get_dashboard_layout" -> ru
                    ? "Читаю layout дашборда «" + orDefault(arg(arguments, "path"), arg(arguments, "template")) + "»"
                    : "Reading dashboard layout \""
                    + orDefault(arg(arguments, "path"), arg(arguments, "template")) + "\"";
            case "set_dashboard_layout" -> ru
                    ? "Обновляю layout «" + arg(arguments, "path") + "»"
                    : "Updating layout \"" + arg(arguments, "path") + "\"";
            case "add_dashboard_widget" -> ru
                    ? "Добавляю виджет на «" + arg(arguments, "path") + "»"
                    : "Adding widget on \"" + arg(arguments, "path") + "\"";
            case "get_widget_catalog" -> widgetCatalogLabel(arguments, ru);
            case "get_automation_schema" -> (ru ? "Справочник: " : "Schema: ")
                    + orDefault(arg(arguments, "topic"), "all");
            case "list_variables" -> ru
                    ? "Читаю переменные «" + arg(arguments, "path") + "»"
                    : "Listing variables on \"" + arg(arguments, "path") + "\"";
            case "set_variable" -> ru
                    ? "Обновляю «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»"
                    : "Updating \"" + arg(arguments, "name") + "\" on \"" + arg(arguments, "path") + "\"";
            case "configure_driver" -> ru
                    ? "Настраиваю драйвер на «" + arg(arguments, "devicePath") + "»"
                    : "Configuring driver on \"" + arg(arguments, "devicePath") + "\"";
            case "apply_mixin_blueprint" -> ru
                    ? "Подключаю модель «" + orDefault(arg(arguments, "blueprintName"), arg(arguments, "blueprintId"))
                    + "» к «" + orDefault(arg(arguments, "objectPath"), arg(arguments, "path")) + "»"
                    : "Applying model \"" + orDefault(arg(arguments, "blueprintName"), arg(arguments, "blueprintId"))
                    + "\" to \"" + orDefault(arg(arguments, "objectPath"), arg(arguments, "path")) + "\"";
            case "list_mixin_blueprints" -> {
                String q = arg(arguments, "query");
                if (ru) {
                    yield "Смотрю MIXIN-модели" + (q.isBlank() ? "" : ": «" + q + "»");
                }
                yield "Listing MIXIN models" + (q.isBlank() ? "" : ": \"" + q + "\"");
            }
            case "get_object_blueprint" -> ru
                    ? "Схема модели «" + orDefault(arg(arguments, "blueprintName"), arg(arguments, "blueprintId")) + "»"
                    : "Model schema \"" + orDefault(arg(arguments, "blueprintName"), arg(arguments, "blueprintId")) + "\"";
            case "create_virtual_device" -> ru
                    ? "Создаю виртуальное устройство «" + arg(arguments, "name") + "»"
                    : "Creating virtual device \"" + arg(arguments, "name") + "\"";
            case "driver_control" -> driverControlLabel(arguments, result, ru);
            case "save_mimic_diagram" -> ru
                    ? "Сохраняю элементы mimic «" + arg(arguments, "path") + "»"
                    : "Saving mimic elements on \"" + arg(arguments, "path") + "\"";
            case "add_mimic_elements" -> ru
                    ? "Добавляю символы на mimic «" + arg(arguments, "path") + "»"
                    : "Adding symbols on mimic \"" + arg(arguments, "path") + "\"";
            case "list_mimic_symbols" -> ru ? "Справочник SCADA-символов" : "SCADA symbol catalog";
            case "get_workflow" -> ru
                    ? "Читаю workflow «" + arg(arguments, "path") + "»"
                    : "Reading workflow \"" + arg(arguments, "path") + "\"";
            case "save_workflow_bpmn" -> ru
                    ? "Сохраняю BPMN «" + arg(arguments, "path") + "»"
                    : "Saving BPMN \"" + arg(arguments, "path") + "\"";
            case "run_workflow" -> ru
                    ? "Запускаю workflow «" + arg(arguments, "path") + "»"
                    : "Starting workflow \"" + arg(arguments, "path") + "\"";
            case "configure_platform_context_rule" -> ru
                    ? "Правило dashboard «" + arg(arguments, "path") + "»"
                    : "Dashboard context rule \"" + arg(arguments, "path") + "\"";
            case "configure_platform_schedule" -> ru
                    ? "Настраиваю расписание «" + orDefault(arg(arguments, "scheduleId"), arg(arguments, "path")) + "»"
                    : "Configuring schedule \"" + orDefault(arg(arguments, "scheduleId"), arg(arguments, "path")) + "\"";
            case "deploy_tree_function" -> ru
                    ? "Деплою функцию «" + arg(arguments, "functionName") + "» на «" + arg(arguments, "path") + "»"
                    : "Deploying function \"" + arg(arguments, "functionName") + "\" on \"" + arg(arguments, "path") + "\"";
            case "get_function_template" -> (ru ? "Шаблон функции: " : "Function template: ")
                    + orDefault(arg(arguments, "topic"), "comparison");
            case "validate_bundle" -> ru
                    ? "Проверяю bundle «" + arg(arguments, "appId") + "»"
                    : "Validating bundle \"" + arg(arguments, "appId") + "\"";
            case "dry_run_deploy" -> ru
                    ? "Dry-run деплоя «" + arg(arguments, "appId") + "»"
                    : "Dry-run deploy \"" + arg(arguments, "appId") + "\"";
            case "import_package" -> ru
                    ? "Импортирую пакет «" + orDefault(arg(arguments, "packageId"), arg(arguments, "appId")) + "»"
                    : "Importing package \"" + orDefault(arg(arguments, "packageId"), arg(arguments, "appId")) + "\"";
            case "get_variable_history" -> ru
                    ? "История «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»"
                    : "History of \"" + arg(arguments, "name") + "\" on \"" + arg(arguments, "path") + "\"";
            case "get_variable_trend" -> ru
                    ? "Тренд «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»"
                    : "Trend of \"" + arg(arguments, "name") + "\" on \"" + arg(arguments, "path") + "\"";
            case "list_work_queue" -> ru ? "Открытые задачи оператора" : "Open operator work items";
            case "list_app_memory" -> (ru ? "Память приложения" : "App memory") + memoryQuery(arguments);
            case "remember_app_memory" -> ru ? "Запоминаю для приложения" : "Remembering for the app";
            case "run_report" -> ru
                    ? "Отчёт «" + arg(arguments, "path") + "»"
                    : "Report \"" + arg(arguments, "path") + "\"";
            case "get_mimic_diagram" -> ru
                    ? "Читаю схему mimic «" + arg(arguments, "path") + "»"
                    : "Reading mimic diagram \"" + arg(arguments, "path") + "\"";
            case "list_events" -> ru
                    ? "События «" + orDefault(arg(arguments, "objectPath"), "платформа") + "»"
                    : "Events on \"" + orDefault(arg(arguments, "objectPath"), "platform") + "\"";
            case "get_operator_scope" -> ru ? "Вызов get_operator_scope" : "Calling get_operator_scope";
            default -> (ru ? "Вызов " : "Calling ") + tool;
        };
    }

    public static String preparingRequest(String uiLocale) {
        return isRussian(uiLocale) ? "Подготовка запроса…" : "Preparing request…";
    }

    public static String parseErrorLabel(String uiLocale, boolean truncated) {
        if (isRussian(uiLocale)) {
            return truncated ? "Ответ обрезан" : "Ошибка разбора ответа модели";
        }
        return truncated ? "Response truncated" : "Failed to parse model response";
    }

    public static String executionErrorLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Ошибка выполнения" : "Execution error";
    }

    public static String preFinishCheckLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Проверка перед завершением" : "Pre-finish check";
    }

    public static String sifPlanCheckLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Проверка SIF-плана" : "SIF plan check";
    }

    public static String continueLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Продолжить" : "Continue";
    }

    public static String continueMessage(String uiLocale) {
        return isRussian(uiLocale)
                ? "Продолжай выполнение с того места, где остановился"
                : "Continue from where you left off";
    }

    public static String cancelledByUser(String uiLocale, int stepsDone) {
        return isRussian(uiLocale)
                ? ("Выполнение остановлено пользователем после " + stepsDone + " шаг(ов).")
                : ("Stopped by user after " + stepsDone + " step(s).");
    }

    public static String planApprovalRequiredLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Требуется утверждение плана" : "Plan approval required";
    }

    public static String askModeLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Режим «Спросить»" : "Ask mode";
    }

    public static String executeModeLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Режим «Выполнить»" : "Execute mode";
    }

    public static String planExecutionLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Выполнение плана" : "Plan execution";
    }

    public static String planRequiredLabel(String uiLocale) {
        return isRussian(uiLocale) ? "Требуется план" : "Plan required";
    }

    public static String litePlanReadySummary(String uiLocale) {
        return isRussian(uiLocale)
                ? "Подготовлен эталонный LITE-план — утвердите и начнём выполнение."
                : "Reference LITE plan is ready — approve it to start execution.";
    }

    public static String platformGuardStuckSummary(String uiLocale) {
        return isRussian(uiLocale)
                ? ("Проверка завершения повторялась — объекты, вероятно, уже созданы. "
                + "Проверьте платформу вручную и при необходимости продолжите в новом сообщении.")
                : ("Finish checks kept repeating — objects may already exist. "
                + "Verify the platform manually and continue in a new message if needed.");
    }

    public static String modelParseFailedSummary(String uiLocale, boolean truncated) {
        if (isRussian(uiLocale)) {
            return truncated
                    ? """
                    Ответ модели обрезан — план слишком большой для одного сообщения. \
                    Частичный план сохранён, если удалось извлечь данные. \
                    Напишите «продолжи план» или «добавь следующие разделы» — план достраивается поэтапно."""
                    : """
                    Не удалось разобрать ответ модели после нескольких попыток. \
                    Попробуйте переформулировать запрос короче или начните новый чат.""";
        }
        return truncated
                ? """
                Model response was truncated — the plan is too large for one message. \
                A partial plan was kept if parsing succeeded. \
                Send "continue plan" or "add the next sections" to extend it."""
                : """
                Could not parse the model response after several attempts. \
                Try a shorter request or start a new chat.""";
    }

    public static boolean isRussian(String uiLocale) {
        return "ru".equals(AgentUiLocalePromptSection.normalize(uiLocale));
    }

    private static String memoryQuery(Map<String, Object> arguments) {
        String query = arg(arguments, "query");
        return query.isBlank() ? "" : " (\"" + query + "\")";
    }

    private static String widgetCatalogLabel(Map<String, Object> arguments, boolean ru) {
        String type = arg(arguments, "type");
        String binding = arg(arguments, "binding");
        if (!type.isBlank()) {
            return ru ? "Справочник виджетов: тип «" + type + "»" : "Widget catalog: type \"" + type + "\"";
        }
        if (!binding.isBlank()) {
            return ru
                    ? "Справочник виджетов: привязка «" + binding + "»"
                    : "Widget catalog: binding \"" + binding + "\"";
        }
        return ru ? "Справочник всех виджетов дашборда" : "Full dashboard widget catalog";
    }

    private static String driverControlLabel(Map<String, Object> arguments, Map<String, Object> result, boolean ru) {
        String action = arg(arguments, "action").toLowerCase(Locale.ROOT);
        String path = arg(arguments, "devicePath");
        String status = result != null ? String.valueOf(result.getOrDefault("connected", "")) : "";
        return switch (action) {
            case "start" -> ru
                    ? "Запускаю опрос драйвера «" + path + "»"
                    : "Starting driver poll on \"" + path + "\"";
            case "stop" -> ru
                    ? "Останавливаю драйвер «" + path + "»"
                    : "Stopping driver \"" + path + "\"";
            case "poll" -> ru
                    ? "Запрашиваю мгновенный poll «" + path + "»"
                    : "Requesting instant poll on \"" + path + "\"";
            default -> {
                String base = ru ? "Статус драйвера «" + path + "»" : "Driver status \"" + path + "\"";
                yield status.isBlank() ? base : base + " (connected=" + status + ")";
            }
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
