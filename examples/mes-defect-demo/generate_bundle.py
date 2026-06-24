#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DEPRECATED — use generate_bundle.mjs (canonical). This copy is not updated."""
import io
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
HUB = "root.platform.devices.mes-hub-01"

MIGRATION_SQL = """
CREATE TABLE IF NOT EXISTS mes_line (
  id UUID PRIMARY KEY,
  line_code VARCHAR(32) NOT NULL UNIQUE,
  line_type VARCHAR(8) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  dolphin_busy BOOLEAN NOT NULL DEFAULT false,
  dispenser_capacity_pct INTEGER NOT NULL DEFAULT 100,
  lpbs_load_pct INTEGER NOT NULL DEFAULT 0,
  machine_status VARCHAR(32) NOT NULL DEFAULT 'running'
);
CREATE TABLE IF NOT EXISTS mes_order (
  id UUID PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL UNIQUE,
  line_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  defect_kg NUMERIC(10,2) NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS mes_defect_event (
  id UUID PRIMARY KEY,
  line_code VARCHAR(32) NOT NULL,
  order_no VARCHAR(32),
  volume_kg NUMERIC(10,2) NOT NULL,
  is_hfa BOOLEAN NOT NULL DEFAULT false,
  is_transitional_remainder BOOLEAN NOT NULL DEFAULT false,
  order_scenario VARCHAR(32) NOT NULL DEFAULT 'active',
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_recommendation (
  id UUID PRIMARY KEY,
  event_id UUID NOT NULL,
  route_type VARCHAR(32) NOT NULL,
  target VARCHAR(32) NOT NULL,
  priority INTEGER NOT NULL,
  rationale VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'recommended'
);
CREATE TABLE IF NOT EXISTS mes_hub_context (
  id INTEGER PRIMARY KEY,
  current_event_id UUID,
  line_type VARCHAR(8),
  is_transitional BOOLEAN NOT NULL DEFAULT false,
  route_target VARCHAR(32),
  route_priority INTEGER,
  route_rationale VARCHAR(512)
);
INSERT INTO mes_hub_context (id) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM mes_hub_context WHERE id = 1);
INSERT INTO mes_line (id, line_code, line_type, display_name, dolphin_busy, dispenser_capacity_pct, lpbs_load_pct, machine_status)
SELECT 'a0000001-0000-0000-0000-000000000001', 'LINE-A01', 'A', 'Line A (Dolphin+Dispenser)', false, 80, 40, 'running'
WHERE NOT EXISTS (SELECT 1 FROM mes_line WHERE line_code = 'LINE-A01');
INSERT INTO mes_line (id, line_code, line_type, display_name, dolphin_busy, dispenser_capacity_pct, lpbs_load_pct, machine_status)
SELECT 'a0000002-0000-0000-0000-000000000002', 'LINE-B01', 'B', 'Line B (Dispenser only)', false, 60, 30, 'running'
WHERE NOT EXISTS (SELECT 1 FROM mes_line WHERE line_code = 'LINE-B01');
INSERT INTO mes_line (id, line_code, line_type, display_name, dolphin_busy, dispenser_capacity_pct, lpbs_load_pct, machine_status)
SELECT 'a0000003-0000-0000-0000-000000000003', 'LINE-D01', 'D', 'Line D (source only)', false, 0, 85, 'running'
WHERE NOT EXISTS (SELECT 1 FROM mes_line WHERE line_code = 'LINE-D01');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000001-0000-0000-0000-000000000001', 'ORD-A-099', 'LINE-A01', 'closing', 12.5
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'ORD-A-099');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000002-0000-0000-0000-000000000002', 'ORD-A-100', 'LINE-A01', 'open', 3.0
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'ORD-A-100');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000003-0000-0000-0000-000000000003', 'ORD-B-200', 'LINE-B01', 'open', 1.0
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'ORD-B-200');
INSERT INTO mes_order (id, order_no, line_code, status, defect_kg)
SELECT 'b0000004-0000-0000-0000-000000000004', 'ORD-D-300', 'LINE-D01', 'open', 0.5
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE order_no = 'ORD-D-300');
""".strip().replace("\n", " ")

SCRIPTS = {
    "mes_simulateDefect": {
        "steps": [
            {"type": "selectOne", "var": "line", "sql": "SELECT line_code, line_type FROM mes_line WHERE line_code = ?", "params": ["${input.lineCode}"]},
            {"type": "failIfNull", "var": "line", "message": "Line not found"},
            {"type": "exec", "sql": "INSERT INTO mes_defect_event (id, line_code, volume_kg, is_hfa, order_scenario, status) VALUES (gen_random_uuid(), ?, ?, CASE WHEN ? IN ('1','true','TRUE') THEN true ELSE false END, ?, 'pending')", "params": ["${input.lineCode}", "${input.volumeKg}", "${input.isHfa}", "${input.orderScenario}"]},
            {"type": "selectOne", "var": "event", "sql": "SELECT id::text AS event_id, line_code FROM mes_defect_event WHERE status = 'pending' ORDER BY created_at DESC LIMIT 1"},
            {"type": "exec", "sql": "UPDATE mes_hub_context SET current_event_id = ?::uuid, line_type = ?, is_transitional = false, route_target = NULL, route_priority = NULL, route_rationale = NULL WHERE id = 1", "params": ["${event.event_id}", "${line.line_type}"]},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "eventId": "${event.event_id}", "lineCode": "${event.line_code}"}},
        ]
    },
    "mes_receiveDefect": {
        "steps": [
            {"type": "selectOne", "var": "event", "sql": "SELECT e.id::text AS event_id, e.line_code, e.volume_kg, e.is_hfa, l.line_type FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE e.status = 'pending' ORDER BY e.created_at ASC LIMIT 1"},
            {"type": "failIfNull", "var": "event", "message": "No pending defect event"},
            {"type": "exec", "sql": "UPDATE mes_defect_event SET status = 'processing' WHERE id = ?::uuid", "params": ["${event.event_id}"]},
            {"type": "exec", "sql": "UPDATE mes_hub_context SET current_event_id = ?::uuid, line_type = ?, is_transitional = false WHERE id = 1", "params": ["${event.event_id}", "${event.line_type}"]},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "eventId": "${event.event_id}", "lineCode": "${event.line_code}", "lineType": "${event.line_type}", "volumeKg": "${event.volume_kg}"}},
        ]
    },
    "mes_resolveOrder": {
        "steps": [
            {"type": "exec", "sql": "UPDATE mes_defect_event e SET order_no = sub.order_no, is_transitional_remainder = sub.is_transitional FROM ( SELECT e2.id, CASE WHEN e2.order_scenario = 'closing' OR EXISTS (SELECT 1 FROM mes_order o WHERE o.line_code = e2.line_code AND o.status = 'closing') THEN COALESCE((SELECT order_no FROM mes_order WHERE line_code = e2.line_code AND status = 'closing' ORDER BY order_no DESC LIMIT 1), (SELECT order_no FROM mes_order WHERE line_code = e2.line_code AND status IN ('open','in_progress') ORDER BY order_no DESC LIMIT 1)) ELSE (SELECT order_no FROM mes_order WHERE line_code = e2.line_code AND status IN ('open','in_progress') ORDER BY order_no DESC LIMIT 1) END AS order_no, CASE WHEN (e2.order_scenario = 'closing' OR EXISTS (SELECT 1 FROM mes_order o WHERE o.line_code = e2.line_code AND o.status = 'closing')) AND (e2.volume_kg > 5 OR l.dolphin_busy = true) THEN true ELSE false END AS is_transitional FROM mes_defect_event e2 JOIN mes_line l ON l.line_code = e2.line_code WHERE e2.id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1) ) sub WHERE e.id = sub.id"},
            {"type": "exec", "sql": "UPDATE mes_hub_context h SET is_transitional = e.is_transitional_remainder, line_type = l.line_type FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE h.id = 1 AND e.id = h.current_event_id"},
            {"type": "selectOne", "var": "resolved", "sql": "SELECT e.id::text AS event_id, e.order_no, e.is_transitional_remainder::text AS is_transitional, l.line_type FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE e.id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1)"},
            {"type": "failIfNull", "var": "resolved", "message": "Defect event not found"},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "eventId": "${resolved.event_id}", "orderNo": "${resolved.order_no}", "isTransitionalRemainder": "${resolved.is_transitional}", "lineType": "${resolved.line_type}"}},
        ]
    },
    "mes_calculateRoute": {
        "steps": [
            {"type": "selectOne", "var": "route", "sql": "SELECT e.id::text AS event_id, CASE WHEN l.line_type = 'A' AND (l.dolphin_busy = true OR e.volume_kg > 15) THEN 'NEIGHBOR_LINE' WHEN l.line_type = 'A' THEN 'DOLPHIN' WHEN l.line_type = 'B' AND e.volume_kg > (l.dispenser_capacity_pct / 10.0) THEN 'DISPENSER_QUEUE' WHEN l.line_type = 'B' THEN 'DISPENSER' WHEN l.line_type = 'D' AND e.is_hfa = true THEN 'C48' ELSE 'LPBS' END AS route_type, CASE WHEN l.line_type = 'A' AND (l.dolphin_busy = true OR e.volume_kg > 15) THEN 'NEIGHBOR' WHEN l.line_type = 'A' THEN 'DOLPHIN' WHEN l.line_type = 'B' AND e.volume_kg > (l.dispenser_capacity_pct / 10.0) THEN 'DISPENSER_QUEUE' WHEN l.line_type = 'B' THEN 'DISPENSER' WHEN l.line_type = 'D' AND e.is_hfa = true THEN 'C48' ELSE 'LPBS' END AS target, CASE WHEN l.line_type = 'D' AND e.is_hfa = false AND l.lpbs_load_pct > 80 THEN 3 WHEN l.line_type = 'D' AND e.is_hfa = false AND l.lpbs_load_pct > 50 THEN 2 WHEN l.line_type = 'A' AND (l.dolphin_busy = true OR e.volume_kg > 15) THEN 2 WHEN l.line_type = 'B' AND e.volume_kg > (l.dispenser_capacity_pct / 10.0) THEN 2 ELSE 1 END AS priority, CASE WHEN l.line_type = 'D' AND e.is_hfa = true THEN 'HFA injection scrap — route to C48 boxes (not LPBS)' WHEN l.line_type = 'D' THEN 'Transport task to LPBS reprocessing line' WHEN l.line_type = 'B' AND e.volume_kg > (l.dispenser_capacity_pct / 10.0) THEN 'Dispenser capacity exceeded — place in feeder queue' WHEN l.line_type = 'B' THEN 'Feed through Dispenser — residual capacity available' WHEN l.line_type = 'A' AND (l.dolphin_busy = true OR e.volume_kg > 15) THEN 'Dolphin busy or batch too large — transfer to neighbor line' ELSE 'Reprocess on line Dolphin — efficiency optimal' END AS rationale FROM mes_defect_event e JOIN mes_line l ON l.line_code = e.line_code WHERE e.id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1)"},
            {"type": "failIfNull", "var": "route", "message": "Event not found for route calculation"},
            {"type": "exec", "sql": "INSERT INTO mes_recommendation (id, event_id, route_type, target, priority, rationale, status) VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, 'recommended')", "params": ["${route.event_id}", "${route.route_type}", "${route.target}", "${route.priority}", "${route.rationale}"]},
            {"type": "exec", "sql": "UPDATE mes_defect_event SET status = 'recommended' WHERE id = ?::uuid", "params": ["${route.event_id}"]},
            {"type": "exec", "sql": "UPDATE mes_hub_context SET route_target = ?, route_priority = ?, route_rationale = ? WHERE id = 1", "params": ["${route.target}", "${route.priority}", "${route.rationale}"]},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "eventId": "${route.event_id}", "routeType": "${route.route_type}", "target": "${route.target}", "priority": "${route.priority}", "rationale": "${route.rationale}"}},
        ]
    },
    "mes_confirmRoute": {
        "steps": [
            {"type": "selectOne", "var": "event", "sql": "SELECT id::text AS event_id, order_no, volume_kg FROM mes_defect_event WHERE id = (SELECT current_event_id FROM mes_hub_context WHERE id = 1) AND status = 'recommended'"},
            {"type": "failIfNull", "var": "event", "message": "No recommended defect to confirm"},
            {"type": "exec", "sql": "UPDATE mes_recommendation SET status = 'confirmed' WHERE event_id = ?::uuid AND status = 'recommended'", "params": ["${event.event_id}"]},
            {"type": "exec", "sql": "UPDATE mes_defect_event SET status = 'confirmed' WHERE id = ?::uuid", "params": ["${event.event_id}"]},
            {"type": "exec", "sql": "UPDATE mes_order SET defect_kg = defect_kg + ? WHERE order_no = ?", "params": ["${event.volume_kg}", "${event.order_no}"]},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "eventId": "${event.event_id}", "status": "confirmed"}},
        ]
    },
    "mes_finalizeDefect": {
        "steps": [
            {"type": "selectOne", "var": "event", "sql": "SELECT id::text AS event_id FROM mes_defect_event WHERE id = COALESCE((SELECT current_event_id FROM mes_hub_context WHERE id = 1), '00000000-0000-0000-0000-000000000000'::uuid)"},
            {"type": "failIfNull", "var": "event", "message": "Event not found"},
            {"type": "exec", "sql": "UPDATE mes_defect_event SET status = 'routed' WHERE id = ?::uuid", "params": ["${event.event_id}"]},
            {"type": "exec", "sql": "UPDATE mes_hub_context SET current_event_id = NULL, is_transitional = false, route_target = NULL, route_priority = NULL, route_rationale = NULL WHERE id = 1"},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "eventId": "${event.event_id}", "status": "routed"}},
        ]
    },
    "mes_listLines": {
        "steps": [
            {"type": "selectMany", "var": "rows", "sql": "SELECT l.line_code, l.line_type, l.display_name, l.machine_status, l.dolphin_busy, l.dispenser_capacity_pct, l.lpbs_load_pct, COALESCE(h.route_target, '') AS route_target, COALESCE(h.route_rationale, '') AS route_rationale FROM mes_line l LEFT JOIN mes_hub_context h ON h.id = 1 ORDER BY l.line_code"},
            {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
        ]
    },
}

FUNCTION_SPECS = {
    "mes_simulateDefect": {
        "in": [
            {"name": "lineCode", "type": "STRING"},
            {"name": "volumeKg", "type": "STRING"},
            {"name": "isHfa", "type": "STRING"},
            {"name": "orderScenario", "type": "STRING"},
        ],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {"name": "eventId", "type": "STRING"},
            {"name": "lineCode", "type": "STRING"},
        ],
    },
    "mes_receiveDefect": {
        "in": [],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {"name": "eventId", "type": "STRING"},
            {"name": "lineCode", "type": "STRING"},
            {"name": "lineType", "type": "STRING"},
            {"name": "volumeKg", "type": "STRING"},
        ],
    },
    "mes_resolveOrder": {
        "in": [{"name": "eventId", "type": "STRING"}],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {"name": "eventId", "type": "STRING"},
            {"name": "orderNo", "type": "STRING"},
            {"name": "isTransitionalRemainder", "type": "STRING"},
            {"name": "lineType", "type": "STRING"},
        ],
    },
    "mes_calculateRoute": {
        "in": [{"name": "eventId", "type": "STRING"}],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {"name": "eventId", "type": "STRING"},
            {"name": "routeType", "type": "STRING"},
            {"name": "target", "type": "STRING"},
            {"name": "priority", "type": "STRING"},
            {"name": "rationale", "type": "STRING"},
        ],
    },
    "mes_confirmRoute": {
        "in": [],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {"name": "eventId", "type": "STRING"},
            {"name": "status", "type": "STRING"},
        ],
    },
    "mes_finalizeDefect": {
        "in": [{"name": "eventId", "type": "STRING"}],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {"name": "eventId", "type": "STRING"},
            {"name": "status", "type": "STRING"},
        ],
    },
    "mes_listLines": {
        "in": [],
        "out": [
            {"name": "error_code", "type": "STRING"},
            {"name": "error_message", "type": "STRING"},
            {
                "name": "rows",
                "type": "RECORD_LIST",
                "nestedSchema": {
                    "name": "line_row",
                    "fields": [
                        {"name": "line_code", "type": "STRING"},
                        {"name": "line_type", "type": "STRING"},
                        {"name": "display_name", "type": "STRING"},
                        {"name": "machine_status", "type": "STRING"},
                        {"name": "dolphin_busy", "type": "STRING"},
                        {"name": "dispenser_capacity_pct", "type": "STRING"},
                        {"name": "lpbs_load_pct", "type": "STRING"},
                        {"name": "route_target", "type": "STRING"},
                        {"name": "route_rationale", "type": "STRING"},
                    ],
                },
            },
        ],
    },
}


def make_function(name):
    spec = FUNCTION_SPECS[name]
    return {
        "objectPath": HUB,
        "functionName": name,
        "version": "1",
        "descriptor": {
            "inputSchema": {"name": "in", "fields": spec["in"]},
            "outputSchema": {"name": "out", "fields": spec["out"]},
        },
        "source": {"type": "script", "body": json.dumps(SCRIPTS[name], ensure_ascii=False)},
    }


with io.open(os.path.join(ROOT, "bpmn", "defect-distribution.bpmn.xml"), encoding="utf-8") as f:
    bpmn_xml = f.read()

overview_layout = {
    "columns": 12,
    "rowHeight": 72,
    "widgets": [
        {
            "id": "lines-report",
            "type": "report",
            "title": "Линии и рекомендации",
            "x": 0,
            "y": 0,
            "w": 8,
            "h": 5,
            "reportPath": "root.platform.reports.mes-defect-lines-status",
            "emptyMessage": "Нет данных",
        },
        {
            "id": "work-queue",
            "type": "work-queue",
            "title": "Задачи диспетчера",
            "x": 8,
            "y": 0,
            "w": 4,
            "h": 5,
            "operatorId": "operator",
            "maxItems": 10,
        },
        {
            "id": "pending-rec",
            "type": "report",
            "title": "Ожидающие рекомендации",
            "x": 0,
            "y": 5,
            "w": 12,
            "h": 3,
            "reportPath": "root.platform.reports.mes-defect-pending-recommendations",
            "emptyMessage": "Нет активных рекомендаций",
        },
    ],
}

simulator_layout = {
    "columns": 12,
    "rowHeight": 72,
    "widgets": [
        {
            "id": "sim-a",
            "type": "function-form",
            "title": "LINE-A01 (Dolphin+Dispenser)",
            "x": 0,
            "y": 0,
            "w": 4,
            "h": 5,
            "objectPath": HUB,
            "functionName": "mes_simulateDefect",
            "buttonLabel": "Симулировать брак",
            "fieldsJson": json.dumps(
                [
                    {"name": "lineCode", "label": "Line", "type": "text", "default": "LINE-A01"},
                    {"name": "volumeKg", "label": "Volume kg", "type": "number", "default": "12"},
                    {"name": "isHfa", "label": "HFA (0/1)", "type": "number", "default": "0"},
                    {"name": "orderScenario", "label": "Scenario", "type": "text", "default": "active"},
                ],
                ensure_ascii=False,
            ),
        },
        {
            "id": "sim-b",
            "type": "function-form",
            "title": "LINE-B01 (Dispenser)",
            "x": 4,
            "y": 0,
            "w": 4,
            "h": 5,
            "objectPath": HUB,
            "functionName": "mes_simulateDefect",
            "buttonLabel": "Симулировать брак",
            "fieldsJson": json.dumps(
                [
                    {"name": "lineCode", "label": "Line", "type": "text", "default": "LINE-B01"},
                    {"name": "volumeKg", "label": "Volume kg", "type": "number", "default": "8"},
                    {"name": "isHfa", "label": "HFA (0/1)", "type": "number", "default": "0"},
                    {"name": "orderScenario", "label": "Scenario", "type": "text", "default": "active"},
                ],
                ensure_ascii=False,
            ),
        },
        {
            "id": "sim-d",
            "type": "function-form",
            "title": "LINE-D01 (LPBS / C48)",
            "x": 8,
            "y": 0,
            "w": 4,
            "h": 5,
            "objectPath": HUB,
            "functionName": "mes_simulateDefect",
            "buttonLabel": "Симулировать брак",
            "fieldsJson": json.dumps(
                [
                    {"name": "lineCode", "label": "Line", "type": "text", "default": "LINE-D01"},
                    {"name": "volumeKg", "label": "Volume kg", "type": "number", "default": "5"},
                    {"name": "isHfa", "label": "HFA (0/1)", "type": "number", "default": "0"},
                    {"name": "orderScenario", "label": "Scenario", "type": "text", "default": "active"},
                ],
                ensure_ascii=False,
            ),
        },
        {
            "id": "events-feed",
            "type": "event-feed",
            "title": "Журнал событий",
            "x": 0,
            "y": 5,
            "w": 12,
            "h": 4,
            "objectPath": HUB,
            "eventNamesJson": '["mesDefectDetected","mesDefectRouted"]',
            "maxItems": 30,
        },
    ],
}

orders_layout = {
    "columns": 12,
    "rowHeight": 72,
    "widgets": [
        {
            "id": "orders-report",
            "type": "report",
            "title": "Ордера на линиях",
            "x": 0,
            "y": 0,
            "w": 12,
            "h": 6,
            "reportPath": "root.platform.reports.mes-defect-orders-detail",
            "emptyMessage": "Нет ордеров",
        }
    ],
}

bundle = {
    "version": "1.0.0",
    "displayName": "MES Defect Distribution Demo",
    "tablePrefix": "",
    "schemaName": "app_mes_defect",
    "migrations": [{"id": "mes_defect_schema", "sql": MIGRATION_SQL}],
    "objects": [
        {
            "parentPath": "root.platform.devices",
            "name": "mes-hub-01",
            "type": "DEVICE",
            "displayName": "MES Hub",
            "description": "MES JIT hub for defect distribution demo",
        },
        {
            "parentPath": "root.platform.devices",
            "name": "mes-line-A01",
            "type": "DEVICE",
            "displayName": "Cigarette Line A01",
            "description": "Type A line (Dolphin + Dispenser)",
        },
        {
            "parentPath": "root.platform.devices",
            "name": "mes-line-B01",
            "type": "DEVICE",
            "displayName": "Cigarette Line B01",
            "description": "Type B line (Dispenser only)",
        },
        {
            "parentPath": "root.platform.devices",
            "name": "mes-line-D01",
            "type": "DEVICE",
            "displayName": "Cigarette Line D01",
            "description": "Type D line (defect source only)",
        },
    ],
    "functions": [make_function(n) for n in SCRIPTS],
    "bindings": [
        {
            "objectPath": HUB,
            "variable": "defectPending",
            "query": "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END AS v FROM mes_defect_event WHERE status = 'pending'",
            "refresh": "on_function_success",
            "refreshIntervalMs": 1000,
            "valueField": "v",
            "triggerObjectPath": HUB,
            "triggerFunctionName": "mes_simulateDefect",
            "enabled": True,
        },
        {
            "objectPath": HUB,
            "variable": "lineType",
            "query": "SELECT COALESCE(line_type, '') AS v FROM mes_hub_context WHERE id = 1",
            "refresh": "on_function_success",
            "refreshIntervalMs": 1000,
            "valueField": "v",
            "triggerObjectPath": HUB,
            "triggerFunctionName": "mes_resolveOrder",
            "enabled": True,
        },
        {
            "objectPath": HUB,
            "variable": "isTransitionalRemainder",
            "query": "SELECT COALESCE(is_transitional, false) AS v FROM mes_hub_context WHERE id = 1",
            "refresh": "on_function_success",
            "refreshIntervalMs": 1000,
            "valueField": "v",
            "triggerObjectPath": HUB,
            "triggerFunctionName": "mes_resolveOrder",
            "enabled": True,
        },
    ],
    "reports": [
        {
            "reportId": "mes-defect-lines-status",
            "title": "Статус линий",
            "description": "Линии с текущей рекомендацией маршрута",
            "query": "SELECT l.line_code, l.line_type, l.display_name, l.machine_status, COALESCE(c.route_target, '') AS route_target, COALESCE(c.route_rationale, '') AS route_rationale FROM mes_line l LEFT JOIN mes_hub_context c ON c.id = 1 ORDER BY l.line_code",
            "parameters": [],
            "columns": [
                {"field": "line_code", "label": "Линия"},
                {"field": "line_type", "label": "Тип"},
                {"field": "display_name", "label": "Название"},
                {"field": "machine_status", "label": "Статус машины"},
                {"field": "route_target", "label": "Маршрут"},
                {"field": "route_rationale", "label": "Рекомендация"},
            ],
            "maxRows": 50,
        },
        {
            "reportId": "mes-defect-orders-detail",
            "title": "Ордера",
            "description": "Ордера с объёмом брака",
            "query": "SELECT order_no, line_code, status, defect_kg FROM mes_order ORDER BY line_code, order_no",
            "parameters": [],
            "columns": [
                {"field": "order_no", "label": "Ордер"},
                {"field": "line_code", "label": "Линия"},
                {"field": "status", "label": "Статус"},
                {"field": "defect_kg", "label": "Брак кг"},
            ],
            "maxRows": 100,
        },
        {
            "reportId": "mes-defect-pending-recommendations",
            "title": "Рекомендации",
            "description": "Активные рекомендации маршрута",
            "query": "SELECT e.line_code, e.order_no, e.volume_kg, r.target, r.priority, r.rationale, r.status FROM mes_recommendation r JOIN mes_defect_event e ON e.id = r.event_id WHERE r.status IN ('recommended', 'confirmed') ORDER BY r.priority, e.created_at DESC",
            "parameters": [],
            "columns": [
                {"field": "line_code", "label": "Линия"},
                {"field": "order_no", "label": "Ордер"},
                {"field": "volume_kg", "label": "Объём"},
                {"field": "target", "label": "Цель"},
                {"field": "priority", "label": "Приоритет"},
                {"field": "rationale", "label": "Обоснование"},
                {"field": "status", "label": "Статус"},
            ],
            "maxRows": 50,
        },
    ],
    "alertRules": [
        {
            "name": "MES defect pending alert",
            "objectPath": HUB,
            "watchVariable": "defectPending",
            "conditionExpr": "self.defectPending[\"value\"] == true",
            "eventName": "mesDefectDetected",
            "payloadVariable": "defectPending",
            "enabled": True,
            "edgeTrigger": True,
            "delaySeconds": 0,
            "sustainWhileTrue": False,
        }
    ],
    "events": [
        {"id": "mesDefectDetected", "roles": ["operator", "admin"]},
        {"id": "mesDefectRouted", "roles": ["operator", "admin"]},
    ],
    "correlators": [
        {
            "name": "MES defect distribution workflow",
            "objectPath": HUB,
            "patternType": "COUNT",
            "eventName": "mesDefectDetected",
            "windowSeconds": 0,
            "minOccurrences": 1,
            "cooldownSeconds": 2,
            "sequenceGapSeconds": 0,
            "actionType": "RUN_WORKFLOW",
            "actionTarget": "root.platform.workflows.mes-defect-distribution",
            "enabled": True,
        }
    ],
    "workflows": [
        {
            "path": "root.platform.workflows.mes-defect-distribution",
            "bpmnXml": bpmn_xml,
            "status": "ACTIVE",
            "operatorAppId": "mes-defect-demo",
        }
    ],
    "dashboards": [
        {
            "path": "root.platform.dashboards.mes-defect-overview",
            "title": "MES Defect Overview",
            "refreshIntervalMs": 5000,
            "layoutJson": json.dumps(overview_layout, ensure_ascii=False),
        },
        {
            "path": "root.platform.dashboards.mes-defect-simulator",
            "title": "SCADA Simulator",
            "refreshIntervalMs": 3000,
            "layoutJson": json.dumps(simulator_layout, ensure_ascii=False),
        },
        {
            "path": "root.platform.dashboards.mes-defect-orders",
            "title": "Orders",
            "refreshIntervalMs": 10000,
            "layoutJson": json.dumps(orders_layout, ensure_ascii=False),
        },
    ],
    "operatorUi": {
        "appId": "mes-defect-demo",
        "title": "MES Defect Demo",
        "defaultDashboard": "root.platform.dashboards.mes-defect-overview",
        "dashboards": [
            {"path": "root.platform.dashboards.mes-defect-overview", "title": "Сводка"},
            {"path": "root.platform.dashboards.mes-defect-simulator", "title": "Симулятор SCADA"},
            {"path": "root.platform.dashboards.mes-defect-orders", "title": "Ордера"},
        ],
        "eventJournalObjectPath": HUB,
    },
}

out_path = os.path.join(ROOT, "bundle.json")
with io.open(out_path, "w", encoding="utf-8") as f:
    json.dump(bundle, f, ensure_ascii=False, indent=2)

print("Wrote", out_path)
