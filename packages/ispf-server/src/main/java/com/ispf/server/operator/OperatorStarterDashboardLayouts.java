package com.ispf.server.operator;

/** Layout JSON for Wave 4 operator starter templates (dashboard-UI only). */
public final class OperatorStarterDashboardLayouts {

    public static final String ALARM_CONSOLE = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "alarm-help",
                  "type": "html-snippet",
                  "title": "Alarm Console",
                  "x": 0, "y": 0, "w": 84, "h": 10,
                  "htmlJson": "<p>Live events for <code>demo-sensor-01</code>. Use the alarm bar (top) to acknowledge. Golden path: fire <code>thresholdExceeded</code> then ack.</p>"
                },
                {
                  "id": "temp",
                  "type": "value",
                  "title": "Temperature",
                  "x": 0, "y": 10, "w": 21, "h": 14,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "temperature",
                  "valueField": "value",
                  "unitField": "unit",
                  "decimals": 1
                },
                {
                  "id": "alarm-active",
                  "type": "indicator",
                  "title": "Alarm active",
                  "x": 21, "y": 10, "w": 21, "h": 14,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "alarmActive",
                  "valueField": "value",
                  "trueLabel": "ACTIVE",
                  "falseLabel": "OK"
                },
                {
                  "id": "ack",
                  "type": "indicator",
                  "title": "Acknowledged",
                  "x": 42, "y": 10, "w": 21, "h": 14,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "variableName": "alarmAcknowledged",
                  "valueField": "value",
                  "trueLabel": "ACK",
                  "falseLabel": "—"
                },
                {
                  "id": "events",
                  "type": "event-feed",
                  "title": "Event journal",
                  "x": 0, "y": 24, "w": 84, "h": 42,
                  "objectPath": "root.platform.devices.demo-sensor-01",
                  "maxItems": 50
                }
              ]
            }
            """;

    public static final String WORK_QUEUE = """
            {
              "columns": 84,
              "rowHeight": 8,
              "widgets": [
                {
                  "id": "wq-help",
                  "type": "html-snippet",
                  "title": "Work Queue",
                  "x": 0, "y": 0, "w": 84, "h": 10,
                  "htmlJson": "<p>Operator work-queue for this app. Tasks appear when workflows with matching <code>operatorAppId</code> create user tasks.</p>"
                },
                {
                  "id": "work-queue",
                  "type": "work-queue",
                  "title": "Tasks",
                  "x": 0, "y": 10, "w": 84, "h": 56,
                  "operatorId": "operator",
                  "operatorAppId": "work-queue",
                  "maxItems": 25
                }
              ]
            }
            """;

    public static final String HMI_WALL = """
            {
              "columns": 84,
              "rowHeight": 8,
              "layoutPreset": "video-wall-2x2",
              "widgets": [
                {
                  "id": "wall-help",
                  "type": "html-snippet",
                  "title": "HMI Wall",
                  "x": 0, "y": 0, "w": 84, "h": 14,
                  "htmlJson": "<p>Video-wall host (2×2). Switch dashboards in the operator nav; mosaic uses the app dashboard list.</p>"
                }
              ]
            }
            """;

    private OperatorStarterDashboardLayouts() {
    }
}
