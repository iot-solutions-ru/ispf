package com.ispf.server.ai.agent;

import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.dashboard.DashboardWidgetPlacement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step-by-step dashboard workflow and anti-patterns for the tree-first agent.
 * <p>
 * Complements {@link AgentWidgetCatalog} (types/fields) and playbooks (recipes).
 */
public final class AgentDashboardGuide {

    private AgentDashboardGuide() {
    }

    public static String referenceText() {
        return """
                ## Дашборды — как работать (обязательный порядок)
                
                Дашборд = объект type=DASHBOARD, templateId=dashboard-v1, parentPath=root.platform.dashboards.
                Виджеты хранятся **только** в переменной `layout` (JSON-строка: {columns, rowHeight, widgets[]}).
                
                ### Алгоритм создания
                
                1. **Устройство и данные готовы** — драйвер запущен, list_variables path=<device> показывает имена переменных.
                2. **create_object** parentPath=root.platform.dashboards name=<slug> type=DASHBOARD templateId=dashboard-v1
                3. **Выбрать способ layout** (см. ниже) — НЕ собирать дашборд из 10+ вызовов add_dashboard_widget.
                4. Опционально: set_variable path=<dashboard> name=title value="..."
                5. Опционально: configure_operator_ui defaultDashboard + dashboards[] для Operator HMI
                6. finish с dashboardPath и подсказкой где открыть в UI
                
                ### Три способа задать layout
                
                | Сценарий | Инструмент | Пример |
                |----------|------------|--------|
                | Готовый layout JSON по имени шаблона | set_dashboard_layout template=... | template=snmp-host-monitoring (пути из list_variables) |
                | Один датчик, статический objectPath | template=empty + 1–3 виджета | objectPath на DEVICE из tools |
                | Добавить 1–2 виджета к существующему | add_dashboard_widget | после get_dashboard_layout |
                | Полностью свой экран | get_dashboard_layout template=empty → правка → set_dashboard_layout layoutJson=... | редко |
                
                Шаблоны template=: """
                + String.join(", ", DashboardService.layoutTemplateNames())
                + """
                
                Перед правкой незнакомого дашборда: get_dashboard_layout path=... (прочитать widgets[]).
                Перед новым типом виджета: get_widget_catalog type=<type>.
                
                ### Привязка данных (критично)
                
                **value / indicator / chart / sparkline / gauge / progress / status-badge:**
                - Один объект: `objectPath` = полный путь DEVICE/CUSTOM + `variableName` + `valueField` (обычно "value")
                - Несколько объектов на экране: `object-table` с `parentPath` + `selectionKey` (напр. "device");
                  потребители с **тем же** selectionKey, objectPath у них НЕ нужен
                - НЕ ставь parentPath на value/chart — parentPath только у object-table, card-grid, map, object-tree
                
                **object-table:**
                - `parentPath` — папка, чьих **детей** показывать (напр. root.platform.devices)
                - `columnsJson` — **строка** JSON-массива: [{"variable":"sysName","label":"Имя"},...]
                - `selectionKey` — при клике публикует путь строки в контекст дашборда
                - Drill-down: `rowTargetDashboard` + `rowOpenMode` navigate|modal на overview;
                  detail-дашборд: виджеты только с selectionKey=device (путь приходит из клика)
                
                **selectionKey — это имя слота, не путь.** Совпадение строки связывает таблицу и виджеты.
                Таблица "device" + виджет "order" = НЕ работают.
                
                ### Данные на экране
                
                Дашборд НЕ опрашивает SNMP/Modbus. Цепочка: драйвер poll → переменные на сервере → виджет читает API.
                variableName в layout должен **точно** совпадать с list_variables (регистр важен).
                chart/sparkline/history-table: сначала configure_variable_history historyEnabled=true на переменной.
                SNMP сетевые графики: ifInOctetsRate / ifOutOctetsRate (не сырые Counter32 ifInOctets).
                
                ### Частые ошибки (НЕ ДЕЛАТЬ)
                
                - set_variable name=widgets или name=layout вручную — только set_dashboard_layout / add_dashboard_widget
                - Выдумывать variableName без list_variables
                - 5+ подряд add_dashboard_widget для целого экрана — взять template=
                - columnsJson/fieldsJson/stylesJson как вложенный объект в tool args — это строки внутри widget JSON
                - objectPath="root.platform.devices" на value (нужен конкретный DEVICE, не папка)
                - selectionKey на виджете без object-table-источника с тем же ключом
                - chart без historyEnabled → пустой график
                - create_object DASHBOARD без templateId=dashboard-v1
                - Просить пользователя «настроить дашборд в UI» когда есть инструменты
                
                ### Минимальные примеры widget (add_dashboard_widget)
                
                Статический value:
                {"id":"temp","type":"value","title":"Температура","x":0,"y":0,"w":28,"h":14,
                 "objectPath":"<devicePath>","variableName":"<variableName>","valueField":"value","decimals":1}
                
                Таблица устройств + выбор:
                {"id":"dev-table","type":"object-table","title":"Устройства","x":0,"y":0,"w":84,"h":28,
                 "parentPath":"root.platform.devices","selectionKey":"device",
                 "columnsJson":"[{\\"variable\\":\\"sysName\\",\\"label\\":\\"Имя\\"}]"}
                
                Потребитель выбора (после клика в таблице):
                {"id":"cpu","type":"value","title":"CPU","x":0,"y":28,"w":21,"h":14,
                 "selectionKey":"device","variableName":"hrProcessorLoad","valueField":"value","unit":"%"}
                
                SCADA mimic (полный экран):
                {"id":"mimic","type":"scada-mimic","title":"Мнемосхема","x":0,"y":0,"w":84,"h":63,
                 "mimicPath":"<mimicPath>","panEnabled":true}
                
                ### Визуальная композиция (ОБЯЗАТЕЛЬНО — дашборд должен выглядеть презентабельно)
                
                Сетка: **columns=84, rowHeight=8**. Это fine grid: 1 «старая» колонка = **7** ячеек.
                Пиши размеры СРАЗУ в fine-единицах. **ЗАПРЕЩЕНО** w=2…6 / h=1…3 — на 84-колоночной сетке
                это крошечные «крошки», а не карточки.
                
                **Кванты размеров (держись их):**
                | Роль | w | h | Примечание |
                |------|---|---|------------|
                | KPI tile (value/indicator/status/gauge) | **21** (¼) или **28** (⅓) | **14** | ряд из 3–4 плиток |
                | Компактный chip (NAV/badge) | 9–14 | **7** | только верхняя полоса |
                | Chart / sparkline / pie | **42–84** | **28–35** | график ≥ половины ширины |
                | object-table / report / event-feed | **42–84** | **28–42** | таблица не «ленточка» |
                | function / function-form | **28–42** | **21–28** | форма читаемая |
                | scada-mimic | **84** | **56–70** | почти full-bleed |
                
                x,y,w,h — предпочтительно **кратные 7** (0,7,14,21,28,35,42,49,56,63,70,77,84).
                
                **Паттерны экрана (копируй структуру, подставляй variableName из list_variables):**
                
                1) *Overview KPI + trend* (датчик / устройство):
                   - y=0: 3–4 KPI в ряд, сумма w = **84**, одинаковый h=14
                   - y=14: chart w=56–84 h=28 (+ sparkline/gauge сбоку)
                   - y=42+: действия (function) w=28 h=14 — не выше KPI
                
                2) *Master–detail* (парк устройств):
                   - object-table x=0 y=0 w=35–42 h=56, selectionKey=device
                   - справа столбик KPI/chart с тем же selectionKey, выровненный по y
                
                3) *Ops board*:
                   - верх: nav-menu / dashboard-link chips h=7
                   - ниже: KPI ряд h=14
                   - ниже: основной контент (chart/table) h≥28
                
                **Правила «как HTML-лендинг, только сетка»:**
                - Одна задача на экран → ясная иерархия: KPI сверху → главный виджет → вторичное
                - Ряд выровнен: одинаковые `y` и `h`; без дыр — сумма w в ряду = 84 (или осознанный отступ справа)
                - Без наложений; без «лесенки» из 8 узких виджетов друг под другом на всю ширину
                - 4–10 виджетов на overview; не 20 мини-плиток
                - Читаемые title; decimals/unit на метриках; chart color (#2f81f7 / #3fb950)
                - Предпочитай set_dashboard_layout одним layoutJson (весь экран) или template=
                
                **Антипаттерны (ломают презентабельность):**
                - размеры «12-колоночной» эпохи: w=3,h=2 / w=6,h=4 на columns=84
                - все виджеты в столбик x=0,y+=h с w=12 или w=84 и крошечным h
                - gaps: виджеты с разными h в одном ряду → «зубцы»
                - chart без historyEnabled (пустая рамка)
                - title «Widget 1» / пустые заголовки
                """
                ;
    }

    public static Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dashboardRoot", "root.platform.dashboards");
        summary.put("model", "dashboard-v1");
        summary.put("layoutVariable", "layout");
        summary.put("layoutShape", Map.of(
                "columns", DashboardWidgetPlacement.DEFAULT_COLUMNS,
                "rowHeight", DashboardWidgetPlacement.DEFAULT_ROW_HEIGHT,
                "widgets", "array"
        ));
        summary.put("templates", DashboardService.layoutTemplateNames());
        summary.put("tools", List.of(
                "get_widget_catalog",
                "get_dashboard_layout",
                "set_dashboard_layout",
                "add_dashboard_widget"
        ));
        summary.put("workflow", List.of(
                "list_variables on target device(s) first",
                "create_object DASHBOARD under root.platform.dashboards",
                "prefer set_dashboard_layout template= or one full layoutJson over many add_dashboard_widget",
                "compose presentable layout: columns=84 rowHeight=8; KPI tiles w=21/28 h=14; charts w≥42 h≥28",
                "align rows (same y/h); fill width (sum w≈84); sizes multiples of 7",
                "match selectionKey between object-table and consumers",
                "configure_variable_history before chart widgets"
        ));
        summary.put("layoutQuanta", Map.of(
                "columns", 84,
                "rowHeight", 8,
                "kpiTile", "w=21|28, h=14",
                "chart", "w=42..84, h=28..35",
                "table", "w=42..84, h=28..42",
                "form", "w=28..42, h=21..28",
                "navChip", "w=9..14, h=7"
        ));
        summary.put("antiPatterns", List.of(
                "set_variable name=widgets or manual layout variable",
                "invent variableName without list_variables",
                "parentPath on value/chart widgets",
                "mismatched selectionKey strings",
                "many add_dashboard_widget calls for full screen",
                "legacy 12-col sizes on fine grid (w=2..6, h=1..3) — tiny crumbs",
                "vertical stack of skinny full-width strips",
                "uneven heights in the same row (jagged layout)",
                "generic titles like Widget 1"
        ));
        return summary;
    }
}
