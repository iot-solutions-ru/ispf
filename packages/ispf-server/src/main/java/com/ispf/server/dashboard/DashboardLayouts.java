package com.ispf.server.dashboard;

/**
 * Default dashboard layout JSON for demo and new dashboards.
 */
public final class DashboardLayouts {

    public static final String DEMO_SENSOR_DASHBOARD = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": [
                {
                  "id": "temp-value",
                  "type": "value",
                  "title": "Температура",
                  "x": 0,
                  "y": 0,
                  "w": 4,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "unitField": "unit"
                },
                {
                  "id": "threshold-value",
                  "type": "value",
                  "title": "Порог",
                  "x": 4,
                  "y": 0,
                  "w": 3,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "threshold",
                  "valueField": "value",
                  "unit": "°C"
                },
                {
                  "id": "alarm-indicator",
                  "type": "indicator",
                  "title": "Тревога",
                  "x": 7,
                  "y": 0,
                  "w": 3,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "alarmActive",
                  "valueField": "value",
                  "trueLabel": "Активна",
                  "falseLabel": "Норма"
                },
                {
                  "id": "online-indicator",
                  "type": "indicator",
                  "title": "Связь",
                  "x": 10,
                  "y": 0,
                  "w": 2,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "status",
                  "valueField": "online",
                  "trueLabel": "Онлайн",
                  "falseLabel": "Офлайн"
                },
                {
                  "id": "temp-trend",
                  "type": "chart",
                  "title": "Тренд температуры",
                  "x": 0,
                  "y": 2,
                  "w": 8,
                  "h": 4,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "unitField": "unit",
                  "chartStyle": "area",
                  "maxPoints": 120,
                  "color": "#2f81f7",
                  "decimals": 1
                },
                {
                  "id": "temp-sparkline",
                  "type": "sparkline",
                  "title": "Спарклайн",
                  "x": 8,
                  "y": 2,
                  "w": 4,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "maxPoints": 40,
                  "color": "#3fb950",
                  "decimals": 1
                },
                {
                  "id": "ack-alarm-fn",
                  "type": "function",
                  "title": "Сброс тревоги",
                  "x": 0,
                  "y": 6,
                  "w": 4,
                  "h": 2,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "alarmActive",
                  "functionName": "acknowledgeAlarm",
                  "buttonLabel": "Подтвердить тревогу",
                  "confirmMessage": "Подтвердить сброс активной тревоги?"
                }
              ]
            }
            """;

    public static final String EMPTY_DASHBOARD = """
            {
              "columns": 12,
              "rowHeight": 72,
              "widgets": []
            }
            """;

    private DashboardLayouts() {
    }
}
