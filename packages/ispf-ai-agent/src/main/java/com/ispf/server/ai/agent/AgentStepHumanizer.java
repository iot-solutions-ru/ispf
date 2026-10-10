package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Map;

/**
 * Human-readable agent step labels for the web console.
 * Language follows UI locale ({@code en|ru|de|zh}) — same set as the console LocaleSwitcher.
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
        if ("finish".equalsIgnoreCase(type)) {
            if (summary != null && !summary.isBlank()) {
                return summary;
            }
            return t(uiLocale,
                    "Task completed.",
                    "Задача выполнена.",
                    "Aufgabe erledigt.",
                    "任务已完成。");
        }
        if (tool == null) {
            return t(uiLocale, "Agent step", "Шаг агента", "Agentenschritt", "代理步骤");
        }
        return switch (tool) {
            case "search_context" -> t(uiLocale,
                    "Searching docs: " + q(uiLocale, arg(arguments, "query")),
                    "Ищу в документации: " + q(uiLocale, arg(arguments, "query")),
                    "Suche in Docs: " + q(uiLocale, arg(arguments, "query")),
                    "正在搜索文档：" + q(uiLocale, arg(arguments, "query")));
            case "list_objects" -> {
                String parent = orDefault(arg(arguments, "parent"), "root");
                yield t(uiLocale,
                        "Listing contents of " + q(uiLocale, parent),
                        "Смотрю содержимое " + q(uiLocale, parent),
                        "Liste Inhalt von " + q(uiLocale, parent),
                        "正在查看 " + q(uiLocale, parent) + " 的内容");
            }
            case "get_object" -> t(uiLocale,
                    "Opening object " + q(uiLocale, arg(arguments, "path")),
                    "Открываю объект " + q(uiLocale, arg(arguments, "path")),
                    "Öffne Objekt " + q(uiLocale, arg(arguments, "path")),
                    "正在打开对象 " + q(uiLocale, arg(arguments, "path")));
            case "create_object" -> t(uiLocale,
                    "Creating " + arg(arguments, "type") + " " + q(uiLocale, arg(arguments, "name")),
                    "Создаю " + arg(arguments, "type") + " " + q(uiLocale, arg(arguments, "name")),
                    "Erstelle " + arg(arguments, "type") + " " + q(uiLocale, arg(arguments, "name")),
                    "正在创建 " + arg(arguments, "type") + " " + q(uiLocale, arg(arguments, "name")));
            case "delete_object" -> t(uiLocale,
                    "Deleting object " + q(uiLocale, arg(arguments, "path")),
                    "Удаляю объект " + q(uiLocale, arg(arguments, "path")),
                    "Lösche Objekt " + q(uiLocale, arg(arguments, "path")),
                    "正在删除对象 " + q(uiLocale, arg(arguments, "path")));
            case "get_dashboard_layout" -> {
                String path = orDefault(arg(arguments, "path"), arg(arguments, "template"));
                yield t(uiLocale,
                        "Reading dashboard layout " + q(uiLocale, path),
                        "Читаю layout дашборда " + q(uiLocale, path),
                        "Lese Dashboard-Layout " + q(uiLocale, path),
                        "正在读取仪表板布局 " + q(uiLocale, path));
            }
            case "set_dashboard_layout" -> t(uiLocale,
                    "Updating layout " + q(uiLocale, arg(arguments, "path")),
                    "Обновляю layout " + q(uiLocale, arg(arguments, "path")),
                    "Aktualisiere Layout " + q(uiLocale, arg(arguments, "path")),
                    "正在更新布局 " + q(uiLocale, arg(arguments, "path")));
            case "add_dashboard_widget" -> t(uiLocale,
                    "Adding widget on " + q(uiLocale, arg(arguments, "path")),
                    "Добавляю виджет на " + q(uiLocale, arg(arguments, "path")),
                    "Füge Widget auf " + q(uiLocale, arg(arguments, "path")) + " hinzu",
                    "正在向 " + q(uiLocale, arg(arguments, "path")) + " 添加组件");
            case "get_widget_catalog" -> widgetCatalogLabel(arguments, uiLocale);
            case "get_automation_schema" -> t(uiLocale,
                    "Schema: " + orDefault(arg(arguments, "topic"), "all"),
                    "Справочник: " + orDefault(arg(arguments, "topic"), "all"),
                    "Schema: " + orDefault(arg(arguments, "topic"), "all"),
                    "架构：" + orDefault(arg(arguments, "topic"), "all"));
            case "list_variables" -> t(uiLocale,
                    "Listing variables on " + q(uiLocale, arg(arguments, "path")),
                    "Читаю переменные " + q(uiLocale, arg(arguments, "path")),
                    "Liste Variablen auf " + q(uiLocale, arg(arguments, "path")),
                    "正在列出 " + q(uiLocale, arg(arguments, "path")) + " 的变量");
            case "set_variable" -> t(uiLocale,
                    "Updating " + q(uiLocale, arg(arguments, "name")) + " on " + q(uiLocale, arg(arguments, "path")),
                    "Обновляю " + q(uiLocale, arg(arguments, "name")) + " на " + q(uiLocale, arg(arguments, "path")),
                    "Aktualisiere " + q(uiLocale, arg(arguments, "name")) + " auf " + q(uiLocale, arg(arguments, "path")),
                    "正在更新 " + q(uiLocale, arg(arguments, "path")) + " 上的 " + q(uiLocale, arg(arguments, "name")));
            case "configure_driver" -> t(uiLocale,
                    "Configuring driver on " + q(uiLocale, arg(arguments, "devicePath")),
                    "Настраиваю драйвер на " + q(uiLocale, arg(arguments, "devicePath")),
                    "Konfiguriere Treiber auf " + q(uiLocale, arg(arguments, "devicePath")),
                    "正在配置驱动 " + q(uiLocale, arg(arguments, "devicePath")));
            case "apply_mixin_blueprint" -> {
                String model = orDefault(arg(arguments, "blueprintName"), arg(arguments, "blueprintId"));
                String path = orDefault(arg(arguments, "objectPath"), arg(arguments, "path"));
                yield t(uiLocale,
                        "Applying model " + q(uiLocale, model) + " to " + q(uiLocale, path),
                        "Подключаю модель " + q(uiLocale, model) + " к " + q(uiLocale, path),
                        "Wende Modell " + q(uiLocale, model) + " auf " + q(uiLocale, path) + " an",
                        "正在将模型 " + q(uiLocale, model) + " 应用到 " + q(uiLocale, path));
            }
            case "list_mixin_blueprints" -> {
                String query = arg(arguments, "query");
                String suffix = query.isBlank() ? "" : ": " + q(uiLocale, query);
                yield t(uiLocale,
                        "Listing MIXIN models" + suffix,
                        "Смотрю MIXIN-модели" + suffix,
                        "Liste MIXIN-Modelle" + suffix,
                        "正在列出 MIXIN 模型" + suffix);
            }
            case "get_object_blueprint" -> {
                String model = orDefault(arg(arguments, "blueprintName"), arg(arguments, "blueprintId"));
                yield t(uiLocale,
                        "Model schema " + q(uiLocale, model),
                        "Схема модели " + q(uiLocale, model),
                        "Modellschema " + q(uiLocale, model),
                        "模型架构 " + q(uiLocale, model));
            }
            case "create_virtual_device" -> t(uiLocale,
                    "Creating virtual device " + q(uiLocale, arg(arguments, "name")),
                    "Создаю виртуальное устройство " + q(uiLocale, arg(arguments, "name")),
                    "Erstelle virtuelles Gerät " + q(uiLocale, arg(arguments, "name")),
                    "正在创建虚拟设备 " + q(uiLocale, arg(arguments, "name")));
            case "driver_control" -> driverControlLabel(arguments, result, uiLocale);
            case "save_mimic_diagram" -> t(uiLocale,
                    "Saving mimic elements on " + q(uiLocale, arg(arguments, "path")),
                    "Сохраняю элементы mimic " + q(uiLocale, arg(arguments, "path")),
                    "Speichere Mimic-Elemente auf " + q(uiLocale, arg(arguments, "path")),
                    "正在保存 mimic 元素 " + q(uiLocale, arg(arguments, "path")));
            case "add_mimic_elements" -> t(uiLocale,
                    "Adding symbols on mimic " + q(uiLocale, arg(arguments, "path")),
                    "Добавляю символы на mimic " + q(uiLocale, arg(arguments, "path")),
                    "Füge Symbole auf Mimic " + q(uiLocale, arg(arguments, "path")) + " hinzu",
                    "正在向 mimic " + q(uiLocale, arg(arguments, "path")) + " 添加符号");
            case "list_mimic_symbols" -> t(uiLocale,
                    "SCADA symbol catalog",
                    "Справочник SCADA-символов",
                    "SCADA-Symbolkatalog",
                    "SCADA 符号目录");
            case "get_workflow" -> t(uiLocale,
                    "Reading workflow " + q(uiLocale, arg(arguments, "path")),
                    "Читаю workflow " + q(uiLocale, arg(arguments, "path")),
                    "Lese Workflow " + q(uiLocale, arg(arguments, "path")),
                    "正在读取工作流 " + q(uiLocale, arg(arguments, "path")));
            case "save_workflow_bpmn" -> t(uiLocale,
                    "Saving BPMN " + q(uiLocale, arg(arguments, "path")),
                    "Сохраняю BPMN " + q(uiLocale, arg(arguments, "path")),
                    "Speichere BPMN " + q(uiLocale, arg(arguments, "path")),
                    "正在保存 BPMN " + q(uiLocale, arg(arguments, "path")));
            case "run_workflow" -> t(uiLocale,
                    "Starting workflow " + q(uiLocale, arg(arguments, "path")),
                    "Запускаю workflow " + q(uiLocale, arg(arguments, "path")),
                    "Starte Workflow " + q(uiLocale, arg(arguments, "path")),
                    "正在启动工作流 " + q(uiLocale, arg(arguments, "path")));
            case "configure_platform_context_rule" -> t(uiLocale,
                    "Dashboard context rule " + q(uiLocale, arg(arguments, "path")),
                    "Правило dashboard " + q(uiLocale, arg(arguments, "path")),
                    "Dashboard-Kontextregel " + q(uiLocale, arg(arguments, "path")),
                    "仪表板上下文规则 " + q(uiLocale, arg(arguments, "path")));
            case "configure_platform_schedule" -> {
                String id = orDefault(arg(arguments, "scheduleId"), arg(arguments, "path"));
                yield t(uiLocale,
                        "Configuring schedule " + q(uiLocale, id),
                        "Настраиваю расписание " + q(uiLocale, id),
                        "Konfiguriere Zeitplan " + q(uiLocale, id),
                        "正在配置计划 " + q(uiLocale, id));
            }
            case "deploy_tree_function" -> t(uiLocale,
                    "Deploying function " + q(uiLocale, arg(arguments, "functionName"))
                            + " on " + q(uiLocale, arg(arguments, "path")),
                    "Деплою функцию " + q(uiLocale, arg(arguments, "functionName"))
                            + " на " + q(uiLocale, arg(arguments, "path")),
                    "Deploye Funktion " + q(uiLocale, arg(arguments, "functionName"))
                            + " auf " + q(uiLocale, arg(arguments, "path")),
                    "正在部署函数 " + q(uiLocale, arg(arguments, "functionName"))
                            + " 到 " + q(uiLocale, arg(arguments, "path")));
            case "get_function_template" -> t(uiLocale,
                    "Function template: " + orDefault(arg(arguments, "topic"), "comparison"),
                    "Шаблон функции: " + orDefault(arg(arguments, "topic"), "comparison"),
                    "Funktionsvorlage: " + orDefault(arg(arguments, "topic"), "comparison"),
                    "函数模板：" + orDefault(arg(arguments, "topic"), "comparison"));
            case "validate_bundle" -> t(uiLocale,
                    "Validating bundle " + q(uiLocale, arg(arguments, "appId")),
                    "Проверяю bundle " + q(uiLocale, arg(arguments, "appId")),
                    "Prüfe Bundle " + q(uiLocale, arg(arguments, "appId")),
                    "正在校验 bundle " + q(uiLocale, arg(arguments, "appId")));
            case "dry_run_deploy" -> t(uiLocale,
                    "Dry-run deploy " + q(uiLocale, arg(arguments, "appId")),
                    "Dry-run деплоя " + q(uiLocale, arg(arguments, "appId")),
                    "Dry-Run Deploy " + q(uiLocale, arg(arguments, "appId")),
                    "试运行部署 " + q(uiLocale, arg(arguments, "appId")));
            case "import_package" -> {
                String id = orDefault(arg(arguments, "packageId"), arg(arguments, "appId"));
                yield t(uiLocale,
                        "Importing package " + q(uiLocale, id),
                        "Импортирую пакет " + q(uiLocale, id),
                        "Importiere Paket " + q(uiLocale, id),
                        "正在导入包 " + q(uiLocale, id));
            }
            case "get_variable_history" -> t(uiLocale,
                    "History of " + q(uiLocale, arg(arguments, "name")) + " on " + q(uiLocale, arg(arguments, "path")),
                    "История " + q(uiLocale, arg(arguments, "name")) + " на " + q(uiLocale, arg(arguments, "path")),
                    "Historie von " + q(uiLocale, arg(arguments, "name")) + " auf " + q(uiLocale, arg(arguments, "path")),
                    q(uiLocale, arg(arguments, "path")) + " 上 " + q(uiLocale, arg(arguments, "name")) + " 的历史");
            case "get_variable_trend" -> t(uiLocale,
                    "Trend of " + q(uiLocale, arg(arguments, "name")) + " on " + q(uiLocale, arg(arguments, "path")),
                    "Тренд " + q(uiLocale, arg(arguments, "name")) + " на " + q(uiLocale, arg(arguments, "path")),
                    "Trend von " + q(uiLocale, arg(arguments, "name")) + " auf " + q(uiLocale, arg(arguments, "path")),
                    q(uiLocale, arg(arguments, "path")) + " 上 " + q(uiLocale, arg(arguments, "name")) + " 的趋势");
            case "list_work_queue" -> t(uiLocale,
                    "Open operator work items",
                    "Открытые задачи оператора",
                    "Offene Operator-Aufgaben",
                    "未完成的操作员任务");
            case "list_app_memory" -> t(uiLocale,
                    "App memory" + memoryQuery(arguments, uiLocale),
                    "Память приложения" + memoryQuery(arguments, uiLocale),
                    "App-Speicher" + memoryQuery(arguments, uiLocale),
                    "应用记忆" + memoryQuery(arguments, uiLocale));
            case "remember_app_memory" -> t(uiLocale,
                    "Remembering for the app",
                    "Запоминаю для приложения",
                    "Merke für die App",
                    "正在为应用记住");
            case "run_report" -> t(uiLocale,
                    "Report " + q(uiLocale, arg(arguments, "path")),
                    "Отчёт " + q(uiLocale, arg(arguments, "path")),
                    "Bericht " + q(uiLocale, arg(arguments, "path")),
                    "报告 " + q(uiLocale, arg(arguments, "path")));
            case "get_mimic_diagram" -> t(uiLocale,
                    "Reading mimic diagram " + q(uiLocale, arg(arguments, "path")),
                    "Читаю схему mimic " + q(uiLocale, arg(arguments, "path")),
                    "Lese Mimic-Diagramm " + q(uiLocale, arg(arguments, "path")),
                    "正在读取 mimic 图 " + q(uiLocale, arg(arguments, "path")));
            case "list_events" -> {
                String scope = orDefault(arg(arguments, "objectPath"),
                        t(uiLocale, "platform", "платформа", "Plattform", "平台"));
                yield t(uiLocale,
                        "Events on " + q(uiLocale, scope),
                        "События " + q(uiLocale, scope),
                        "Ereignisse auf " + q(uiLocale, scope),
                        q(uiLocale, scope) + " 上的事件");
            }
            case "get_operator_scope" -> t(uiLocale,
                    "Calling get_operator_scope",
                    "Вызов get_operator_scope",
                    "Aufruf get_operator_scope",
                    "调用 get_operator_scope");
            default -> t(uiLocale,
                    "Calling " + tool,
                    "Вызов " + tool,
                    "Aufruf " + tool,
                    "调用 " + tool);
        };
    }

    public static String preparingRequest(String uiLocale) {
        return t(uiLocale,
                "Preparing request…",
                "Подготовка запроса…",
                "Anfrage wird vorbereitet…",
                "正在准备请求…");
    }

    public static String parseErrorLabel(String uiLocale, boolean truncated) {
        if (truncated) {
            return t(uiLocale,
                    "Response truncated",
                    "Ответ обрезан",
                    "Antwort abgeschnitten",
                    "响应被截断");
        }
        return t(uiLocale,
                "Failed to parse model response",
                "Ошибка разбора ответа модели",
                "Modellantwort konnte nicht gelesen werden",
                "无法解析模型响应");
    }

    public static String executionErrorLabel(String uiLocale) {
        return t(uiLocale, "Execution error", "Ошибка выполнения", "Ausführungsfehler", "执行错误");
    }

    public static String preFinishCheckLabel(String uiLocale) {
        return t(uiLocale,
                "Pre-finish check",
                "Проверка перед завершением",
                "Prüfung vor Abschluss",
                "完成前检查");
    }

    public static String sifPlanCheckLabel(String uiLocale) {
        return t(uiLocale, "SIF plan check", "Проверка SIF-плана", "SIF-Planprüfung", "SIF 计划检查");
    }

    public static String continueLabel(String uiLocale) {
        return t(uiLocale, "Continue", "Продолжить", "Weiter", "继续");
    }

    public static String continueMessage(String uiLocale) {
        return t(uiLocale,
                "Continue from where you left off",
                "Продолжай выполнение с того места, где остановился",
                "Setze die Ausführung fort, wo du aufgehört hast",
                "从停下的地方继续执行");
    }

    public static String cancelledByUser(String uiLocale, int stepsDone) {
        return t(uiLocale,
                "Stopped by user after " + stepsDone + " step(s).",
                "Выполнение остановлено пользователем после " + stepsDone + " шаг(ов).",
                "Vom Benutzer nach " + stepsDone + " Schritt(en) gestoppt.",
                "用户在 " + stepsDone + " 步后停止。");
    }

    public static String planApprovalRequiredLabel(String uiLocale) {
        return t(uiLocale,
                "Plan approval required",
                "Требуется утверждение плана",
                "Plangenehmigung erforderlich",
                "需要批准计划");
    }

    public static String askModeLabel(String uiLocale) {
        return t(uiLocale, "Ask mode", "Режим «Спросить»", "Ask-Modus", "询问模式");
    }

    public static String executeModeLabel(String uiLocale) {
        return t(uiLocale, "Execute mode", "Режим «Выполнить»", "Execute-Modus", "执行模式");
    }

    public static String planExecutionLabel(String uiLocale) {
        return t(uiLocale, "Plan execution", "Выполнение плана", "Planausführung", "执行计划");
    }

    public static String planRequiredLabel(String uiLocale) {
        return t(uiLocale, "Plan required", "Требуется план", "Plan erforderlich", "需要计划");
    }

    public static String litePlanReadySummary(String uiLocale) {
        return t(uiLocale,
                "Reference LITE plan is ready — approve it to start execution.",
                "Подготовлен эталонный LITE-план — утвердите и начнём выполнение.",
                "Referenz-LITE-Plan ist bereit — genehmigen Sie ihn zum Start.",
                "参考 LITE 计划已就绪 — 批准后开始执行。");
    }

    public static String platformGuardStuckSummary(String uiLocale) {
        return t(uiLocale,
                "Finish checks kept repeating — objects may already exist. "
                        + "Verify the platform manually and continue in a new message if needed.",
                "Проверка завершения повторялась — объекты, вероятно, уже созданы. "
                        + "Проверьте платформу вручную и при необходимости продолжите в новом сообщении.",
                "Abschlussprüfungen wiederholten sich — Objekte existieren möglicherweise schon. "
                        + "Prüfen Sie die Plattform manuell und setzen Sie ggf. in einer neuen Nachricht fort.",
                "完成检查反复出现 — 对象可能已存在。请手动核对平台，必要时在新消息中继续。");
    }

    public static String modelParseFailedSummary(String uiLocale, boolean truncated) {
        if (truncated) {
            return t(uiLocale,
                    """
                    Model response was truncated — the plan is too large for one message. \
                    A partial plan was kept if parsing succeeded. \
                    Send "continue plan" or "add the next sections" to extend it.""",
                    """
                    Ответ модели обрезан — план слишком большой для одного сообщения. \
                    Частичный план сохранён, если удалось извлечь данные. \
                    Напишите «продолжи план» или «добавь следующие разделы» — план достраивается поэтапно.""",
                    """
                    Modellantwort abgeschnitten — der Plan ist zu groß für eine Nachricht. \
                    Ein Teilplan wurde behalten, falls das Parsen gelang. \
                    Senden Sie „Plan fortsetzen“ oder „nächste Abschnitte hinzufügen“.""",
                    """
                    模型响应被截断 — 计划太大，无法放在一条消息中。 \
                    若解析成功则已保留部分计划。 \
                    请发送「继续计划」或「添加后续章节」以扩展。""");
        }
        return t(uiLocale,
                """
                Could not parse the model response after several attempts. \
                Try a shorter request or start a new chat.""",
                """
                Не удалось разобрать ответ модели после нескольких попыток. \
                Попробуйте переформулировать запрос короче или начните новый чат.""",
                """
                Modellantwort konnte nach mehreren Versuchen nicht gelesen werden. \
                Versuchen Sie eine kürzere Anfrage oder starten Sie einen neuen Chat.""",
                """
                多次尝试后仍无法解析模型响应。 \
                请缩短请求或开始新对话。""");
    }

    public static boolean isRussian(String uiLocale) {
        return AgentUiText.isRussian(uiLocale);
    }

    private static String t(String uiLocale, String en, String ru, String de, String zh) {
        return AgentUiText.t(uiLocale, en, ru, de, zh);
    }

    private static String q(String uiLocale, String value) {
        return AgentUiText.quote(uiLocale, value);
    }

    private static String memoryQuery(Map<String, Object> arguments, String uiLocale) {
        String query = arg(arguments, "query");
        return query.isBlank() ? "" : " (" + AgentUiText.quote(uiLocale, query) + ")";
    }

    private static String widgetCatalogLabel(Map<String, Object> arguments, String uiLocale) {
        String type = arg(arguments, "type");
        String binding = arg(arguments, "binding");
        String q = AgentUiText.quote(uiLocale, type.isBlank() ? binding : type);
        if (!type.isBlank()) {
            return t(uiLocale,
                    "Widget catalog: type " + q,
                    "Справочник виджетов: тип " + q,
                    "Widget-Katalog: Typ " + q,
                    "组件目录：类型 " + q);
        }
        if (!binding.isBlank()) {
            return t(uiLocale,
                    "Widget catalog: binding " + q,
                    "Справочник виджетов: привязка " + q,
                    "Widget-Katalog: Binding " + q,
                    "组件目录：绑定 " + q);
        }
        return t(uiLocale,
                "Full dashboard widget catalog",
                "Справочник всех виджетов дашборда",
                "Vollständiger Dashboard-Widget-Katalog",
                "完整仪表板组件目录");
    }

    private static String driverControlLabel(
            Map<String, Object> arguments,
            Map<String, Object> result,
            String uiLocale
    ) {
        String action = arg(arguments, "action").toLowerCase(Locale.ROOT);
        String path = arg(arguments, "devicePath");
        String q = AgentUiText.quote(uiLocale, path);
        String status = result != null ? String.valueOf(result.getOrDefault("connected", "")) : "";
        return switch (action) {
            case "start" -> t(uiLocale,
                    "Starting driver poll on " + q,
                    "Запускаю опрос драйвера " + q,
                    "Starte Treiber-Poll auf " + q,
                    "正在启动驱动轮询 " + q);
            case "stop" -> t(uiLocale,
                    "Stopping driver " + q,
                    "Останавливаю драйвер " + q,
                    "Stoppe Treiber " + q,
                    "正在停止驱动 " + q);
            case "poll" -> t(uiLocale,
                    "Requesting instant poll on " + q,
                    "Запрашиваю мгновенный poll " + q,
                    "Fordere Sofort-Poll auf " + q,
                    "正在请求即时轮询 " + q);
            default -> {
                String base = t(uiLocale,
                        "Driver status " + q,
                        "Статус драйвера " + q,
                        "Treiberstatus " + q,
                        "驱动状态 " + q);
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
