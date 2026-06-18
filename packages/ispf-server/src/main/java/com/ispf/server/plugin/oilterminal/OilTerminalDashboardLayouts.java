package com.ispf.server.plugin.oilterminal;

import com.ispf.plugin.oilterminal.OilTerminalConstants;

/**
 * HMI dashboard layouts for oil terminal reference stand (full widget set).
 */
public final class OilTerminalDashboardLayouts {

    private static final String ROOT = OilTerminalConstants.ROOT;
    private static final String ORDERS = OilTerminalConstants.ORDERS;
    private static final String TANKS = OilTerminalConstants.TANKS;
    private static final String RACKS = OilTerminalConstants.RACKS;
    private static final String SAMPLES = OilTerminalConstants.SAMPLES;
    private static final String RACK = OilTerminalConstants.rackPath(OilTerminalConstants.DEMO_RACK);
    private static final String DEMO_TANK = OilTerminalConstants.tankPath(OilTerminalConstants.DEMO_TANK);

    private static final String ORDER_COLUMNS =
            "[{\"variable\":\"orderNo\",\"label\":\"№\"},"
                    + "{\"variable\":\"status\",\"label\":\"Статус\"},"
                    + "{\"variable\":\"plannedLiters\",\"label\":\"План, л\"},"
                    + "{\"variable\":\"vehiclePlate\",\"label\":\"Авто\"}]";

    private static final String SAMPLE_COLUMNS =
            "[{\"variable\":\"sampleNo\",\"label\":\"Проба\"},"
                    + "{\"variable\":\"tankName\",\"label\":\"РВС\"},"
                    + "{\"variable\":\"approved\",\"label\":\"Одобрено\"}]";

    private static final String TANK_CARD_VARS = "[\"levelM3\",\"qualityOk\",\"levelLow\"]";

    private static final String OIL_EVENTS =
            "[\"dispatchStarted\",\"dispatchCompleted\",\"dispatchCancelled\",\"tankLevelLow\",\"labApproved\"]";

    private static final String DISPATCH_EVENTS = "[\"dispatchStarted\",\"dispatchCompleted\"]";

    private static final String LAB_EVENTS = "[\"labApproved\",\"tankLevelLow\"]";

    private static final String TANK_QUALITY_VARS = "[\"levelM3\",\"qualityOk\"]";

    private static String assignFieldsJson() {
        return "[{\"name\":\"tankName\",\"label\":\"РВС\",\"type\":\"select\",\"optionsFrom\":\""
                + TANKS + "\",\"defaultValue\":\"" + OilTerminalConstants.DEMO_TANK + "\"},"
                + "{\"name\":\"rackName\",\"label\":\"Эстакада\",\"type\":\"select\",\"optionsFrom\":\""
                + RACKS + "\",\"defaultValue\":\"" + OilTerminalConstants.DEMO_RACK + "\"}]";
    }

    public static final String DISPATCHER = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": [
                {
                  "id": "orders-table",
                  "type": "object-table",
                  "title": "Наряды на отгрузку",
                  "x": 0, "y": 0, "w": 8, "h": 5,
                  "parentPath": "%s",
                  "columnsJson": "%s",
                  "selectionKey": "order"
                },
                {
                  "id": "bpmn-queue",
                  "type": "work-queue",
                  "title": "Задачи BPMN",
                  "x": 8, "y": 0, "w": 4, "h": 5,
                  "operatorId": "admin",
                  "maxItems": 10
                },
                {
                  "id": "order-status",
                  "type": "status-badge",
                  "title": "Статус наряда",
                  "x": 0, "y": 5, "w": 3, "h": 2,
                  "selectionKey": "order",
                  "variableName": "status",
                  "valueField": "value"
                },
                {
                  "id": "fill-progress",
                  "type": "progress",
                  "title": "Ход налива",
                  "x": 3, "y": 5, "w": 5, "h": 2,
                  "selectionKey": "order",
                  "currentVariable": "actualLiters",
                  "maxVariable": "plannedLiters",
                  "unit": "л",
                  "decimals": 0
                },
                {
                  "id": "assign-form",
                  "type": "function-form",
                  "title": "Назначить РВС и эстакаду",
                  "x": 8, "y": 5, "w": 4, "h": 3,
                  "selectionKey": "order",
                  "functionName": "assign",
                  "buttonLabel": "Назначить",
                  "fieldsJson": "%s"
                },
                {
                  "id": "tanks-grid",
                  "type": "card-grid",
                  "title": "Резервуары",
                  "x": 0, "y": 7, "w": 6, "h": 4,
                  "parentPath": "%s",
                  "variablesJson": "%s"
                },
                {
                  "id": "oil-events",
                  "type": "event-feed",
                  "title": "События терминала",
                  "x": 6, "y": 7, "w": 6, "h": 4,
                  "objectPathPrefix": "%s",
                  "eventNamesJson": "%s",
                  "maxItems": 25
                },
                {
                  "id": "close-fn",
                  "type": "function",
                  "title": "Закрыть наряд",
                  "x": 8, "y": 8, "w": 4, "h": 2,
                  "selectionKey": "order",
                  "functionName": "close",
                  "buttonLabel": "Закрыть после ERP",
                  "confirmMessage": "Закрыть наряд?"
                },
                {
                  "id": "demo-tank-gauge",
                  "type": "gauge",
                  "title": "Уровень РВС-3",
                  "x": 0, "y": 11, "w": 4, "h": 3,
                  "objectPath": "%s",
                  "variableName": "levelM3",
                  "minVariable": "minLevelM3",
                  "maxVariable": "maxLevelM3",
                  "unit": "м³",
                  "decimals": 1
                }
              ]
            }
            """.formatted(
            ORDERS,
            escapeJsonString(ORDER_COLUMNS),
            escapeJsonString(assignFieldsJson()),
            TANKS,
            escapeJsonString(TANK_CARD_VARS),
            ROOT,
            escapeJsonString(OIL_EVENTS),
            DEMO_TANK
    );

    public static final String RACK_OPERATOR = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": [
                {
                  "id": "orders-table",
                  "type": "object-table",
                  "title": "Наряды",
                  "x": 0, "y": 0, "w": 5, "h": 4,
                  "parentPath": "%s",
                  "columnsJson": "%s",
                  "selectionKey": "order"
                },
                {
                  "id": "rack-busy",
                  "type": "indicator",
                  "title": "Эстакада rack2",
                  "x": 5, "y": 0, "w": 3, "h": 2,
                  "objectPath": "%s",
                  "variableName": "busy",
                  "valueField": "value",
                  "trueLabel": "Занята",
                  "falseLabel": "Свободна"
                },
                {
                  "id": "totalizer",
                  "type": "value",
                  "title": "Счётчик, л",
                  "x": 8, "y": 0, "w": 4, "h": 2,
                  "objectPath": "%s",
                  "variableName": "totalizerL",
                  "valueField": "value",
                  "decimals": 0
                },
                {
                  "id": "flow-rate",
                  "type": "value",
                  "title": "Расход, л/мин",
                  "x": 5, "y": 2, "w": 3, "h": 2,
                  "objectPath": "%s",
                  "variableName": "flowRateLpm",
                  "valueField": "value",
                  "decimals": 0
                },
                {
                  "id": "order-status",
                  "type": "status-badge",
                  "title": "Статус",
                  "x": 8, "y": 2, "w": 4, "h": 2,
                  "selectionKey": "order",
                  "variableName": "status",
                  "valueField": "value"
                },
                {
                  "id": "fill-progress",
                  "type": "progress",
                  "title": "Ход налива",
                  "x": 0, "y": 4, "w": 8, "h": 2,
                  "selectionKey": "order",
                  "currentVariable": "actualLiters",
                  "maxVariable": "plannedLiters",
                  "unit": "л",
                  "decimals": 0
                },
                {
                  "id": "start-fn",
                  "type": "function",
                  "title": "Старт",
                  "x": 8, "y": 4, "w": 4, "h": 2,
                  "selectionKey": "order",
                  "functionName": "start",
                  "buttonLabel": "Начать налив"
                },
                {
                  "id": "complete-fn",
                  "type": "function",
                  "title": "Стоп",
                  "x": 0, "y": 6, "w": 4, "h": 2,
                  "selectionKey": "order",
                  "functionName": "complete",
                  "buttonLabel": "Завершить налив"
                },
                {
                  "id": "rack-events",
                  "type": "event-feed",
                  "title": "События",
                  "x": 4, "y": 6, "w": 8, "h": 3,
                  "objectPathPrefix": "%s",
                  "eventNamesJson": "%s",
                  "maxItems": 15
                }
              ]
            }
            """.formatted(
            ORDERS,
            escapeJsonString(ORDER_COLUMNS),
            RACK,
            RACK,
            RACK,
            ROOT,
            escapeJsonString(DISPATCH_EVENTS)
    );

    public static final String LAB_OPERATOR = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": [
                {
                  "id": "samples-table",
                  "type": "object-table",
                  "title": "Пробы",
                  "x": 0, "y": 0, "w": 6, "h": 4,
                  "parentPath": "%s",
                  "columnsJson": "%s",
                  "selectionKey": "sample"
                },
                {
                  "id": "lab-queue",
                  "type": "work-queue",
                  "title": "Задачи лаборатории",
                  "x": 6, "y": 0, "w": 6, "h": 4,
                  "operatorId": "admin",
                  "maxItems": 10
                },
                {
                  "id": "sample-approved",
                  "type": "indicator",
                  "title": "Одобрение пробы",
                  "x": 0, "y": 4, "w": 4, "h": 2,
                  "selectionKey": "sample",
                  "variableName": "approved",
                  "valueField": "value",
                  "trueLabel": "Одобрено",
                  "falseLabel": "Ожидает"
                },
                {
                  "id": "approve-fn",
                  "type": "function",
                  "title": "Выпуск РВС",
                  "x": 4, "y": 4, "w": 4, "h": 2,
                  "selectionKey": "sample",
                  "functionName": "approve",
                  "buttonLabel": "Одобрить пробу",
                  "confirmMessage": "Одобрить пробу и выпустить РВС?"
                },
                {
                  "id": "tanks-quality",
                  "type": "card-grid",
                  "title": "Качество по РВС",
                  "x": 8, "y": 4, "w": 4, "h": 4,
                  "parentPath": "%s",
                  "variablesJson": "%s"
                },
                {
                  "id": "lab-events",
                  "type": "event-feed",
                  "title": "События лаборатории",
                  "x": 0, "y": 6, "w": 8, "h": 3,
                  "objectPathPrefix": "%s",
                  "eventNamesJson": "%s",
                  "maxItems": 20
                }
              ]
            }
            """.formatted(
            SAMPLES,
            escapeJsonString(SAMPLE_COLUMNS),
            TANKS,
            escapeJsonString(TANK_QUALITY_VARS),
            ROOT,
            escapeJsonString(LAB_EVENTS)
    );

    private static String escapeJsonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private OilTerminalDashboardLayouts() {
    }
}
