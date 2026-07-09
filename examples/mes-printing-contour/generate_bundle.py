#!/usr/bin/env python3
"""Generate examples/mes-printing-contour/bundle.json for ISPF docanima printing contour demo."""
from __future__ import annotations

import json
import re
from pathlib import Path

HUB = "root.platform.devices.printing-contour-hub"
MACHINE_DEVICE = "root.platform.devices.print-machine-pr120"

WA_PLANNED = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
WA_ACTIVE = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
WA_COMPLETED = "cccccccc-cccc-cccc-cccc-cccccccccccc"
ORDER_ID = "11111111-1111-1111-1111-111111111111"
JB_ID = "22222222-2222-2222-2222-222222222222"
ROLL_IN_1 = "d0000001-0000-0000-0000-000000000001"
ROLL_IN_2 = "d0000002-0000-0000-0000-000000000002"
ROLL_OUT_1 = "d0000003-0000-0000-0000-000000000003"


def fix_uuid_sql(sql: str) -> str:
    """Cast JDBC string parameters to uuid for PostgreSQL uuid columns."""
    s = sql
    s = re.sub(r"\bwork_area_id\s*=\s*\?(?!:)", "work_area_id = ?::uuid", s)
    s = re.sub(r"\bwork_area_id\s*=\s*\?,", "work_area_id = ?::uuid,", s)
    s = re.sub(r"FROM work_area WHERE id = \?(?!:)", "FROM work_area WHERE id = ?::uuid", s)
    s = re.sub(r"WHERE w\.id = \?(?!:)", "WHERE w.id = ?::uuid", s)
    s = re.sub(
        r"UPDATE work_area\b([^;]*?)WHERE id = \?(?!:)",
        r"UPDATE work_area\1WHERE id = ?::uuid",
        s,
        flags=re.DOTALL,
    )
    s = re.sub(r"\bid <> \?(?!:)", "id <> ?::uuid", s)
    s = re.sub(r"FROM material_roll WHERE id = \?(?!:)", "FROM material_roll WHERE id = ?::uuid", s)
    s = re.sub(
        r"UPDATE material_roll\b([^;]*?)WHERE id = \?(?!:)",
        r"UPDATE material_roll\1WHERE id = ?::uuid",
        s,
        flags=re.DOTALL,
    )
    s = re.sub(r"job_bag_id = \?(?!:)", "job_bag_id = ?::uuid", s)
    s = s.replace(
        "work_area_execution_interval (id, work_area_id, interval_type, started_at) VALUES (gen_random_uuid(), ?,",
        "work_area_execution_interval (id, work_area_id, interval_type, started_at) VALUES (gen_random_uuid(), ?::uuid,",
    )
    s = s.replace(
        "job_bag (id, work_area_id, bag_no) VALUES (gen_random_uuid(), ?,",
        "job_bag (id, work_area_id, bag_no) VALUES (gen_random_uuid(), ?::uuid,",
    )
    s = s.replace(
        "production_event (id, work_area_id, event_code, event_name, comment_text) VALUES (gen_random_uuid(), ?,",
        "production_event (id, work_area_id, event_code, event_name, comment_text) VALUES (gen_random_uuid(), ?::uuid,",
    )
    s = s.replace(
        "meter_interval (id, work_area_id, roll_id, meters, interval_type) VALUES (gen_random_uuid(), ?, ?,",
        "meter_interval (id, work_area_id, roll_id, meters, interval_type) VALUES (gen_random_uuid(), ?::uuid, ?::uuid,",
    )
    s = s.replace(
        "job_bag_document (id, job_bag_id, section_key, section_title, content_json) VALUES (gen_random_uuid(), ?,",
        "job_bag_document (id, job_bag_id, section_key, section_title, content_json) VALUES (gen_random_uuid(), ?::uuid,",
    )
    s = s.replace("'ON_STAGE', ?, ?)", "'ON_STAGE', ?::uuid, ?)")
    s = re.sub(
        r"work_area \(id, mes_order_id, machine_code, area_id, operation_name, status, planned_start\)\s+VALUES \(gen_random_uuid\(\), \?(?!:)",
        "work_area (id, mes_order_id, machine_code, area_id, operation_name, status, planned_start) VALUES (gen_random_uuid(), ?::uuid,",
        s,
    )
    s = re.sub(
        r"INSERT INTO mes_order \(id, order_no, project_name, product_name, customer_name\) VALUES \(\?::uuid,",
        "INSERT INTO mes_order (id, order_no, project_name, product_name, customer_name) VALUES (?::uuid,",
        s,
    )
    s = re.sub(
        r"INSERT INTO work_area \(id, mes_order_id, machine_code, area_id, operation_name, status, planned_start\) VALUES \(\?::uuid, \?(?!:)",
        "INSERT INTO work_area (id, mes_order_id, machine_code, area_id, operation_name, status, planned_start) VALUES (?::uuid, ?::uuid,",
        s,
    )
    s = re.sub(
        r"INSERT INTO job_bag \(id, work_area_id, bag_no\) VALUES \(\?::uuid, \?(?!:)",
        "INSERT INTO job_bag (id, work_area_id, bag_no) VALUES (?::uuid, ?::uuid,",
        s,
    )
    return s


def patch_script_steps(node: object) -> None:
    if isinstance(node, dict):
        if node.get("type") in ("selectOne", "selectMany", "exec") and "sql" in node:
            node["sql"] = fix_uuid_sql(node["sql"])
        for value in node.values():
            patch_script_steps(value)
    elif isinstance(node, list):
        for item in node:
            patch_script_steps(item)


def patch_bundle(bundle: dict) -> None:
    for function in bundle.get("functions", []):
        body = json.loads(function["source"]["body"])
        patch_script_steps(body)
        function["source"]["body"] = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    for report in bundle.get("reports", []):
        if "query" in report:
            report["query"] = fix_uuid_sql(report["query"])


def script(body: dict) -> dict:
    return {"type": "script", "body": json.dumps(body, ensure_ascii=False, separators=(",", ":"))}


def fn(name: str, input_fields: list, output_fields: list, steps: list) -> dict:
    return {
        "objectPath": HUB,
        "functionName": name,
        "version": "1",
        "descriptor": {
            "inputSchema": {"name": "in", "fields": input_fields},
            "outputSchema": {"name": "out", "fields": output_fields},
        },
        "source": script({"steps": steps}),
    }


def std_out(extra: list | None = None) -> list:
    base = [
        {"name": "error_code", "type": "STRING"},
        {"name": "error_message", "type": "STRING"},
    ]
    return base + (extra or [])


MIGRATION_SQL = """
CREATE TABLE IF NOT EXISTS mes_machine (
  machine_code VARCHAR(32) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS mes_order (
  id UUID PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  customer_name VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS work_area (
  id UUID PRIMARY KEY,
  mes_order_id UUID NOT NULL,
  machine_code VARCHAR(32) NOT NULL,
  area_id VARCHAR(64) NOT NULL,
  operation_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  planned_start TIMESTAMP,
  actual_start TIMESTAMP,
  actual_end TIMESTAMP
);
CREATE TABLE IF NOT EXISTS work_area_execution_interval (
  id UUID PRIMARY KEY,
  work_area_id UUID NOT NULL,
  interval_type VARCHAR(16) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS material_roll (
  id UUID PRIMARY KEY,
  barcode VARCHAR(64) NOT NULL UNIQUE,
  roll_kind VARCHAR(8) NOT NULL,
  length_m INTEGER NOT NULL DEFAULT 0,
  current_length_m INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  work_area_id UUID,
  machine_code VARCHAR(32)
);
CREATE TABLE IF NOT EXISTS meter_interval (
  id UUID PRIMARY KEY,
  work_area_id UUID NOT NULL,
  roll_id UUID NOT NULL,
  meters INTEGER NOT NULL,
  interval_type VARCHAR(32) NOT NULL DEFAULT 'CONSUMPTION',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS roll_composition (
  id UUID PRIMARY KEY,
  input_roll_id UUID NOT NULL,
  output_roll_id UUID NOT NULL
);
CREATE TABLE IF NOT EXISTS job_bag (
  id UUID PRIMARY KEY,
  work_area_id UUID NOT NULL UNIQUE,
  bag_no VARCHAR(32) NOT NULL
);
CREATE TABLE IF NOT EXISTS job_bag_document (
  id UUID PRIMARY KEY,
  job_bag_id UUID NOT NULL,
  section_key VARCHAR(64) NOT NULL,
  section_title VARCHAR(128) NOT NULL,
  content_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS machine_stock (
  id UUID PRIMARY KEY,
  machine_code VARCHAR(32) NOT NULL,
  material_name VARCHAR(128) NOT NULL,
  qty_kg INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS production_event (
  id UUID PRIMARY KEY,
  work_area_id UUID NOT NULL,
  event_code VARCHAR(16) NOT NULL,
  event_name VARCHAR(128) NOT NULL,
  comment_text VARCHAR(512),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS erp_outbox (
  id UUID PRIMARY KEY,
  entity_type VARCHAR(32) NOT NULL,
  entity_id VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP
);
INSERT INTO mes_machine (machine_code, display_name)
SELECT 'PR120', 'Flexo press PR120' WHERE NOT EXISTS (SELECT 1 FROM mes_machine WHERE machine_code = 'PR120');
INSERT INTO mes_order (id, order_no, project_name, product_name, customer_name)
SELECT '11111111-1111-1111-1111-111111111111', 'ORD-2026-042', 'Labels Q2', 'Shrink sleeve batch A', 'Demo Customer LLC'
WHERE NOT EXISTS (SELECT 1 FROM mes_order WHERE id = '11111111-1111-1111-1111-111111111111');
INSERT INTO work_area (id, mes_order_id, machine_code, area_id, operation_name, status, planned_start)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'PR120', 'PRINT-OP-001', 'Flexo print — job 1', 'PLANNED', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM work_area WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
INSERT INTO work_area (id, mes_order_id, machine_code, area_id, operation_name, status, planned_start, actual_start)
SELECT 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'PR120', 'PRINT-OP-002', 'Flexo print — job 2 (active demo)', 'IN_PROGRESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM work_area WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
INSERT INTO work_area (id, mes_order_id, machine_code, area_id, operation_name, status, planned_start, actual_start, actual_end)
SELECT 'cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111', 'PR120', 'PRINT-OP-000', 'Flexo print — completed ref', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM work_area WHERE id = 'cccccccc-cccc-cccc-cccc-cccccccccccc');
INSERT INTO work_area_execution_interval (id, work_area_id, interval_type, started_at)
SELECT 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'RUN', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM work_area_execution_interval WHERE id = 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee');
INSERT INTO job_bag (id, work_area_id, bag_no)
SELECT '22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'JB-PRINT-002'
WHERE NOT EXISTS (SELECT 1 FROM job_bag WHERE id = '22222222-2222-2222-2222-222222222222');
INSERT INTO job_bag_document (id, job_bag_id, section_key, section_title, content_json)
SELECT '33333333-3333-3333-3333-333333333331', '22222222-2222-2222-2222-222222222222', 'press', 'Press settings', '{"ink":"Cyan+Magenta","speed":120}'
WHERE NOT EXISTS (SELECT 1 FROM job_bag_document WHERE id = '33333333-3333-3333-3333-333333333331');
INSERT INTO job_bag_document (id, job_bag_id, section_key, section_title, content_json)
SELECT '33333333-3333-3333-3333-333333333332', '22222222-2222-2222-2222-222222222222', 'process', 'Process control', '{"tension":"1.2 bar","register":"OK"}'
WHERE NOT EXISTS (SELECT 1 FROM job_bag_document WHERE id = '33333333-3333-3333-3333-333333333332');
INSERT INTO material_roll (id, barcode, roll_kind, length_m, current_length_m, status, work_area_id, machine_code)
SELECT 'd0000001-0000-0000-0000-000000000001', 'IN-ROLL-1001', 'IN', 5000, 4200, 'ON_STAGE', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'PR120'
WHERE NOT EXISTS (SELECT 1 FROM material_roll WHERE id = 'd0000001-0000-0000-0000-000000000001');
INSERT INTO material_roll (id, barcode, roll_kind, length_m, current_length_m, status, work_area_id, machine_code)
SELECT 'd0000002-0000-0000-0000-000000000002', 'IN-ROLL-1002', 'IN', 3000, 3000, 'AVAILABLE', NULL, 'PR120'
WHERE NOT EXISTS (SELECT 1 FROM material_roll WHERE id = 'd0000002-0000-0000-0000-000000000002');
INSERT INTO material_roll (id, barcode, roll_kind, length_m, current_length_m, status, work_area_id, machine_code)
SELECT 'd0000003-0000-0000-0000-000000000003', 'OUT-ROLL-2001', 'OUT', 800, 800, 'ON_STAGE', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'PR120'
WHERE NOT EXISTS (SELECT 1 FROM material_roll WHERE id = 'd0000003-0000-0000-0000-000000000003');
INSERT INTO meter_interval (id, work_area_id, roll_id, meters, interval_type)
SELECT 'f0000001-0000-0000-0000-000000000001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'd0000001-0000-0000-0000-000000000001', 800, 'CONSUMPTION'
WHERE NOT EXISTS (SELECT 1 FROM meter_interval WHERE id = 'f0000001-0000-0000-0000-000000000001');
INSERT INTO roll_composition (id, input_roll_id, output_roll_id)
SELECT 'c1000001-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001', 'd0000003-0000-0000-0000-000000000003'
WHERE NOT EXISTS (SELECT 1 FROM roll_composition WHERE id = 'c1000001-0000-0000-0000-000000000001');
INSERT INTO machine_stock (id, machine_code, material_name, qty_kg)
SELECT 'c2000001-0000-0000-0000-000000000001', 'PR120', 'Cyan ink', 120 WHERE NOT EXISTS (SELECT 1 FROM machine_stock WHERE id = 'c2000001-0000-0000-0000-000000000001');
INSERT INTO machine_stock (id, machine_code, material_name, qty_kg)
SELECT 'c2000002-0000-0000-0000-000000000002', 'PR120', 'Magenta ink', 95 WHERE NOT EXISTS (SELECT 1 FROM machine_stock WHERE id = 'c2000002-0000-0000-0000-000000000002');
INSERT INTO production_event (id, work_area_id, event_code, event_name, comment_text)
SELECT 'c3000001-0000-0000-0000-000000000001', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '110', 'Speed loss', 'Brief slowdown at 400m'
WHERE NOT EXISTS (SELECT 1 FROM production_event WHERE id = 'c3000001-0000-0000-0000-000000000001');
INSERT INTO production_event (id, work_area_id, event_code, event_name, comment_text)
SELECT 'c3000002-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '120', 'Setup', 'Color match adjustment'
WHERE NOT EXISTS (SELECT 1 FROM production_event WHERE id = 'c3000002-0000-0000-0000-000000000002');
""".strip()


def build_functions() -> list:
    row_list = {
        "name": "rows",
        "type": "RECORD_LIST",
        "nestedSchema": {
            "name": "row",
            "fields": [],
        },
    }

    functions = [
        fn(
            "mes_printing_listStages",
            [{"name": "machineCode", "type": "STRING"}],
            std_out(
                [
                    {
                        "name": "rows",
                        "type": "RECORD_LIST",
                        "nestedSchema": {
                            "name": "stage_row",
                            "fields": [
                                {"name": "id", "type": "STRING"},
                                {"name": "areaId", "type": "STRING"},
                                {"name": "operationName", "type": "STRING"},
                                {"name": "status", "type": "STRING"},
                                {"name": "orderNo", "type": "STRING"},
                                {"name": "productName", "type": "STRING"},
                            ],
                        },
                    }
                ]
            ),
            [
                {
                    "type": "selectMany",
                    "var": "stages",
                    "sql": "SELECT w.id::text AS id, w.area_id, w.operation_name, w.status, o.order_no, o.product_name FROM work_area w JOIN mes_order o ON o.id = w.mes_order_id WHERE w.machine_code = ? ORDER BY w.planned_start NULLS LAST, w.area_id",
                    "params": ["${input.machineCode}"],
                },
                {
                    "type": "map",
                    "var": "rows",
                    "source": "${stages}",
                    "fields": {
                        "id": "${item.id}",
                        "areaId": "${item.area_id}",
                        "operationName": "${item.operation_name}",
                        "status": "${item.status}",
                        "orderNo": "${item.order_no}",
                        "productName": "${item.product_name}",
                    },
                },
                {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
            ],
        ),
        fn(
            "mes_printing_getStageHeader",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out(
                [
                    {"name": "projectName", "type": "STRING"},
                    {"name": "productName", "type": "STRING"},
                    {"name": "orderNo", "type": "STRING"},
                    {"name": "customerName", "type": "STRING"},
                    {"name": "areaId", "type": "STRING"},
                    {"name": "status", "type": "STRING"},
                    {"name": "machineCode", "type": "STRING"},
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "hdr",
                    "sql": "SELECT o.project_name, o.product_name, o.order_no, o.customer_name, w.area_id, w.status, w.machine_code FROM work_area w JOIN mes_order o ON o.id = w.mes_order_id WHERE w.id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "hdr", "message": "Work area not found"},
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "projectName": "${hdr.project_name}",
                        "productName": "${hdr.product_name}",
                        "orderNo": "${hdr.order_no}",
                        "customerName": "${hdr.customer_name}",
                        "areaId": "${hdr.area_id}",
                        "status": "${hdr.status}",
                        "machineCode": "${hdr.machine_code}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_startStage",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "startedBy", "type": "STRING"},
            ],
            std_out([{"name": "status", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT id::text AS id, machine_code, status FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "failIfNotEquals",
                    "var": "wa.status",
                    "equals": "PLANNED",
                    "message": "Stage is not PLANNED",
                },
                {
                    "type": "selectOne",
                    "var": "conflict",
                    "sql": "SELECT id::text AS id FROM work_area WHERE machine_code = ? AND status = 'IN_PROGRESS' AND id <> ?",
                    "params": ["${wa.machine_code}", "${input.workAreaId}"],
                },
                {
                    "type": "when",
                    "var": "conflict",
                    "notNull": True,
                    "then": [
                        {
                            "type": "return",
                            "fields": {
                                "error_code": "ERROR",
                                "error_message": "Machine already has an active stage",
                                "status": "",
                            },
                        }
                    ],
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area SET status = 'IN_PROGRESS', actual_start = CURRENT_TIMESTAMP WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO work_area_execution_interval (id, work_area_id, interval_type, started_at) VALUES (gen_random_uuid(), ?, 'RUN', CURRENT_TIMESTAMP)",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO erp_outbox (id, entity_type, entity_id, payload_json, idempotency_key, status) SELECT gen_random_uuid(), 'STAGE_STARTED', ?, CONCAT('{\"workAreaId\":\"', ?, '\"}'), CONCAT('STAGE_STARTED:', ?), 'pending' WHERE NOT EXISTS (SELECT 1 FROM erp_outbox WHERE idempotency_key = CONCAT('STAGE_STARTED:', ?))",
                    "params": [
                        "${input.workAreaId}",
                        "${input.workAreaId}",
                        "${input.workAreaId}",
                        "${input.workAreaId}",
                    ],
                },
                {
                    "type": "writeVariable",
                    "objectPath": MACHINE_DEVICE,
                    "variable": "status",
                    "fields": {"value": "RUNNING"},
                },
                {
                    "type": "writeVariable",
                    "objectPath": MACHINE_DEVICE,
                    "variable": "activeWorkAreaId",
                    "fields": {"value": "${input.workAreaId}"},
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "status": "IN_PROGRESS"},
                },
            ],
        ),
        fn(
            "mes_printing_pauseStage",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out([{"name": "status", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT status FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "failIfNotEquals",
                    "var": "wa.status",
                    "equals": "IN_PROGRESS",
                    "message": "Stage is not IN_PROGRESS",
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area SET status = 'PAUSED' WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area_execution_interval SET ended_at = CURRENT_TIMESTAMP WHERE work_area_id = ? AND interval_type = 'RUN' AND ended_at IS NULL",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO work_area_execution_interval (id, work_area_id, interval_type, started_at) VALUES (gen_random_uuid(), ?, 'PAUSE', CURRENT_TIMESTAMP)",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "writeVariable",
                    "objectPath": MACHINE_DEVICE,
                    "variable": "status",
                    "fields": {"value": "PAUSED"},
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "status": "PAUSED"},
                },
            ],
        ),
        fn(
            "mes_printing_resumeStage",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out([{"name": "status", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT status FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "failIfNotEquals",
                    "var": "wa.status",
                    "equals": "PAUSED",
                    "message": "Stage is not PAUSED",
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area SET status = 'IN_PROGRESS' WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area_execution_interval SET ended_at = CURRENT_TIMESTAMP WHERE work_area_id = ? AND interval_type = 'PAUSE' AND ended_at IS NULL",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO work_area_execution_interval (id, work_area_id, interval_type, started_at) VALUES (gen_random_uuid(), ?, 'RUN', CURRENT_TIMESTAMP)",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "writeVariable",
                    "objectPath": MACHINE_DEVICE,
                    "variable": "status",
                    "fields": {"value": "RUNNING"},
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "status": "IN_PROGRESS"},
                },
            ],
        ),
        fn(
            "mes_printing_getMonitoringSummary",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out(
                [
                    {"name": "inputTotalM", "type": "INTEGER"},
                    {"name": "inputRollM", "type": "INTEGER"},
                    {"name": "outputTotalM", "type": "INTEGER"},
                    {"name": "outputRollM", "type": "INTEGER"},
                    {"name": "inputRollBarcode", "type": "STRING"},
                    {"name": "outputRollBarcode", "type": "STRING"},
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "inSum",
                    "sql": "SELECT COALESCE(SUM(meters), 0) AS total_m FROM meter_interval WHERE work_area_id = ? AND interval_type = 'CONSUMPTION'",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "selectOne",
                    "var": "outSum",
                    "sql": "SELECT COALESCE(SUM(length_m), 0) AS total_m FROM material_roll WHERE work_area_id = ? AND roll_kind = 'OUT'",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "selectOne",
                    "var": "lastIn",
                    "sql": "SELECT barcode, (length_m - current_length_m) AS consumed FROM material_roll WHERE work_area_id = ? AND roll_kind = 'IN' ORDER BY barcode DESC LIMIT 1",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "selectOne",
                    "var": "lastOut",
                    "sql": "SELECT barcode, current_length_m AS len FROM material_roll WHERE work_area_id = ? AND roll_kind = 'OUT' ORDER BY barcode DESC LIMIT 1",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "inputTotalM": "${inSum.total_m}",
                        "inputRollM": "${lastIn.consumed}",
                        "outputTotalM": "${outSum.total_m}",
                        "outputRollM": "${lastOut.len}",
                        "inputRollBarcode": "${lastIn.barcode}",
                        "outputRollBarcode": "${lastOut.barcode}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_registerInputRoll",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "barcode", "type": "STRING"},
            ],
            std_out([{"name": "rollId", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT status, machine_code FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "failIfNotEquals",
                    "var": "wa.status",
                    "equals": "IN_PROGRESS",
                    "message": "Stage is not IN_PROGRESS",
                },
                {
                    "type": "selectOne",
                    "var": "existing",
                    "sql": "SELECT id::text AS id FROM material_roll WHERE barcode = ?",
                    "params": ["${input.barcode}"],
                },
                {
                    "type": "when",
                    "var": "existing",
                    "notNull": True,
                    "then": [
                        {
                            "type": "exec",
                            "sql": "UPDATE material_roll SET status = 'ON_STAGE', work_area_id = ?, machine_code = ? WHERE barcode = ?",
                            "params": [
                                "${input.workAreaId}",
                                "${wa.machine_code}",
                                "${input.barcode}",
                            ],
                        }
                    ],
                    "else": [
                        {
                            "type": "exec",
                            "sql": "INSERT INTO material_roll (id, barcode, roll_kind, length_m, current_length_m, status, work_area_id, machine_code) VALUES (gen_random_uuid(), ?, 'IN', 2000, 2000, 'ON_STAGE', ?, ?)",
                            "params": [
                                "${input.barcode}",
                                "${input.workAreaId}",
                                "${wa.machine_code}",
                            ],
                        },
                    ],
                },
                {
                    "type": "selectOne",
                    "var": "roll",
                    "sql": "SELECT id::text AS id FROM material_roll WHERE barcode = ?",
                    "params": ["${input.barcode}"],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "rollId": "${roll.id}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_recordConsumption",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "barcode", "type": "STRING"},
                {"name": "meters", "type": "INTEGER"},
            ],
            std_out([{"name": "remainingM", "type": "INTEGER"}]),
            [
                {
                    "type": "selectOne",
                    "var": "roll",
                    "sql": "SELECT id::text AS id, current_length_m FROM material_roll WHERE barcode = ? AND work_area_id = ?",
                    "params": ["${input.barcode}", "${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "roll", "message": "Input roll not found on stage"},
                {
                    "type": "exec",
                    "sql": "UPDATE material_roll SET current_length_m = GREATEST(0, current_length_m - ?) WHERE id = ?",
                    "params": ["${input.meters}", "${roll.id}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO meter_interval (id, work_area_id, roll_id, meters, interval_type) VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMPTION')",
                    "params": ["${input.workAreaId}", "${roll.id}", "${input.meters}"],
                },
                {
                    "type": "selectOne",
                    "var": "updated",
                    "sql": "SELECT current_length_m FROM material_roll WHERE id = ?",
                    "params": ["${roll.id}"],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "remainingM": "${updated.current_length_m}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_registerOutputRoll",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "barcode", "type": "STRING"},
                {"name": "lengthM", "type": "INTEGER"},
            ],
            std_out([{"name": "rollId", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT machine_code, status FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "exec",
                    "sql": "INSERT INTO material_roll (id, barcode, roll_kind, length_m, current_length_m, status, work_area_id, machine_code) VALUES (gen_random_uuid(), ?, 'OUT', ?, ?, 'ON_STAGE', ?, ?)",
                    "params": [
                        "${input.barcode}",
                        "${input.lengthM}",
                        "${input.lengthM}",
                        "${input.workAreaId}",
                        "${wa.machine_code}",
                    ],
                },
                {
                    "type": "selectOne",
                    "var": "roll",
                    "sql": "SELECT id::text AS id FROM material_roll WHERE barcode = ?",
                    "params": ["${input.barcode}"],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "rollId": "${roll.id}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_getJobBag",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out(
                [
                    {"name": "bagNo", "type": "STRING"},
                    {"name": "cylindersJson", "type": "STRING"},
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "jb",
                    "sql": "SELECT bag_no FROM job_bag WHERE work_area_id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "when",
                    "var": "jb",
                    "notNull": False,
                    "then": [
                        {
                            "type": "exec",
                            "sql": "INSERT INTO job_bag (id, work_area_id, bag_no) VALUES (gen_random_uuid(), ?, CONCAT('JB-', SUBSTRING(?::text, 1, 8)))",
                            "params": ["${input.workAreaId}", "${input.workAreaId}"],
                        },
                        {
                            "type": "selectOne",
                            "var": "jb",
                            "sql": "SELECT bag_no FROM job_bag WHERE work_area_id = ?",
                            "params": ["${input.workAreaId}"],
                        },
                    ],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "bagNo": "${jb.bag_no}",
                        "cylindersJson": "[{\"id\":\"CYL-01\",\"color\":\"Cyan\"}]",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_getJobBagSections",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out(
                [
                    {
                        "name": "rows",
                        "type": "RECORD_LIST",
                        "nestedSchema": {
                            "name": "section_row",
                            "fields": [
                                {"name": "sectionKey", "type": "STRING"},
                                {"name": "sectionTitle", "type": "STRING"},
                                {"name": "contentJson", "type": "STRING"},
                            ],
                        },
                    }
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "jb",
                    "sql": "SELECT id::text AS id FROM job_bag WHERE work_area_id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "when",
                    "var": "jb",
                    "notNull": False,
                    "then": [
                        {
                            "type": "exec",
                            "sql": "INSERT INTO job_bag (id, work_area_id, bag_no) VALUES (gen_random_uuid(), ?, 'JB-NEW')",
                            "params": ["${input.workAreaId}"],
                        },
                        {
                            "type": "selectOne",
                            "var": "jb",
                            "sql": "SELECT id::text AS id FROM job_bag WHERE work_area_id = ?",
                            "params": ["${input.workAreaId}"],
                        },
                    ],
                },
                {
                    "type": "selectMany",
                    "var": "sections",
                    "sql": "SELECT section_key, section_title, content_json FROM job_bag_document WHERE job_bag_id = ? ORDER BY section_key",
                    "params": ["${jb.id}"],
                },
                {
                    "type": "map",
                    "var": "rows",
                    "source": "${sections}",
                    "fields": {
                        "sectionKey": "${item.section_key}",
                        "sectionTitle": "${item.section_title}",
                        "contentJson": "${item.content_json}",
                    },
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"},
                },
            ],
        ),
        fn(
            "mes_printing_saveJobBagSection",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "sectionKey", "type": "STRING"},
                {"name": "sectionTitle", "type": "STRING"},
                {"name": "contentJson", "type": "STRING"},
            ],
            std_out([{"name": "saved", "type": "BOOLEAN"}]),
            [
                {
                    "type": "selectOne",
                    "var": "jb",
                    "sql": "SELECT id::text AS id FROM job_bag WHERE work_area_id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "jb", "message": "Job bag not found"},
                {
                    "type": "exec",
                    "sql": "DELETE FROM job_bag_document WHERE job_bag_id = ? AND section_key = ?",
                    "params": ["${jb.id}", "${input.sectionKey}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO job_bag_document (id, job_bag_id, section_key, section_title, content_json) VALUES (gen_random_uuid(), ?, ?, ?, ?)",
                    "params": [
                        "${jb.id}",
                        "${input.sectionKey}",
                        "${input.sectionTitle}",
                        "${input.contentJson}",
                    ],
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "saved": True},
                },
            ],
        ),
        fn(
            "mes_printing_writeOffReturn",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "barcode", "type": "STRING"},
                {"name": "meters", "type": "INTEGER"},
                {"name": "returnRoll", "type": "BOOLEAN"},
            ],
            std_out([{"name": "status", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "roll",
                    "sql": "SELECT id::text AS id, current_length_m FROM material_roll WHERE barcode = ?",
                    "params": ["${input.barcode}"],
                },
                {"type": "failIfNull", "var": "roll", "message": "Roll not found"},
                {
                    "type": "when",
                    "var": "input.returnRoll",
                    "equals": "true",
                    "then": [
                        {
                            "type": "exec",
                            "sql": "UPDATE material_roll SET status = 'RETURNED', work_area_id = NULL WHERE id = ?",
                            "params": ["${roll.id}"],
                        }
                    ],
                    "else": [
                        {
                            "type": "exec",
                            "sql": "UPDATE material_roll SET current_length_m = GREATEST(0, current_length_m - ?), work_area_id = ?, status = 'ON_STAGE' WHERE id = ?",
                            "params": [
                                "${input.meters}",
                                "${input.workAreaId}",
                                "${roll.id}",
                            ],
                        },
                        {
                            "type": "exec",
                            "sql": "INSERT INTO meter_interval (id, work_area_id, roll_id, meters, interval_type) VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMPTION')",
                            "params": [
                                "${input.workAreaId}",
                                "${roll.id}",
                                "${input.meters}",
                            ],
                        },
                    ],
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "status": "OK"},
                },
            ],
        ),
        fn(
            "mes_printing_listMachineStock",
            [{"name": "machineCode", "type": "STRING"}],
            std_out(
                [
                    {
                        "name": "rows",
                        "type": "RECORD_LIST",
                        "nestedSchema": {
                            "name": "stock_row",
                            "fields": [
                                {"name": "materialName", "type": "STRING"},
                                {"name": "qtyKg", "type": "INTEGER"},
                            ],
                        },
                    }
                ]
            ),
            [
                {
                    "type": "selectMany",
                    "var": "stock",
                    "sql": "SELECT material_name, qty_kg FROM machine_stock WHERE machine_code = ? ORDER BY material_name",
                    "params": ["${input.machineCode}"],
                },
                {
                    "type": "map",
                    "var": "rows",
                    "source": "${stock}",
                    "fields": {
                        "materialName": "${item.material_name}",
                        "qtyKg": "${item.qty_kg}",
                    },
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"},
                },
            ],
        ),
        fn(
            "mes_printing_listStageEvents",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out(
                [
                    {
                        "name": "rows",
                        "type": "RECORD_LIST",
                        "nestedSchema": {
                            "name": "event_row",
                            "fields": [
                                {"name": "eventCode", "type": "STRING"},
                                {"name": "eventName", "type": "STRING"},
                                {"name": "commentText", "type": "STRING"},
                                {"name": "createdAt", "type": "STRING"},
                            ],
                        },
                    }
                ]
            ),
            [
                {
                    "type": "selectMany",
                    "var": "events",
                    "sql": "SELECT event_code, event_name, COALESCE(comment_text, '') AS comment_text, created_at::text AS created_at FROM production_event WHERE work_area_id = ? ORDER BY created_at DESC",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "map",
                    "var": "rows",
                    "source": "${events}",
                    "fields": {
                        "eventCode": "${item.event_code}",
                        "eventName": "${item.event_name}",
                        "commentText": "${item.comment_text}",
                        "createdAt": "${item.created_at}",
                    },
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"},
                },
            ],
        ),
        fn(
            "mes_printing_registerProductionEvent",
            [
                {"name": "workAreaId", "type": "STRING"},
                {"name": "eventCode", "type": "STRING"},
                {"name": "eventName", "type": "STRING"},
                {"name": "commentText", "type": "STRING"},
            ],
            std_out([{"name": "eventId", "type": "STRING"}]),
            [
                {
                    "type": "exec",
                    "sql": "INSERT INTO production_event (id, work_area_id, event_code, event_name, comment_text) VALUES (gen_random_uuid(), ?, ?, ?, ?)",
                    "params": [
                        "${input.workAreaId}",
                        "${input.eventCode}",
                        "${input.eventName}",
                        "${input.commentText}",
                    ],
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "eventId": "OK"},
                },
            ],
        ),
        fn(
            "mes_printing_assessCompletion",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out(
                [
                    {"name": "ready", "type": "BOOLEAN"},
                    {"name": "message", "type": "STRING"},
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT status FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "selectOne",
                    "var": "outs",
                    "sql": "SELECT COUNT(*) AS cnt FROM material_roll WHERE work_area_id = ? AND roll_kind = 'OUT'",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "when",
                    "var": "outs.cnt",
                    "equals": "0",
                    "then": [
                        {
                            "type": "return",
                            "fields": {
                                "error_code": "OK",
                                "error_message": "",
                                "ready": False,
                                "message": "Register at least one output roll",
                            },
                        }
                    ],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "ready": True,
                        "message": "Ready to complete",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_completeStage",
            [{"name": "workAreaId", "type": "STRING"}],
            std_out([{"name": "status", "type": "STRING"}]),
            [
                {
                    "type": "selectOne",
                    "var": "wa",
                    "sql": "SELECT status FROM work_area WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {"type": "failIfNull", "var": "wa", "message": "Work area not found"},
                {
                    "type": "failIfNotEquals",
                    "var": "wa.status",
                    "equals": "IN_PROGRESS",
                    "message": "Stage is not IN_PROGRESS",
                },
                {
                    "type": "selectOne",
                    "var": "outs",
                    "sql": "SELECT COUNT(*) AS cnt FROM material_roll WHERE work_area_id = ? AND roll_kind = 'OUT'",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "when",
                    "var": "outs.cnt",
                    "equals": "0",
                    "then": [
                        {
                            "type": "return",
                            "fields": {
                                "error_code": "ERROR",
                                "error_message": "Register at least one output roll before complete",
                                "status": "",
                            },
                        }
                    ],
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area SET status = 'COMPLETED', actual_end = CURRENT_TIMESTAMP WHERE id = ?",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "UPDATE work_area_execution_interval SET ended_at = CURRENT_TIMESTAMP WHERE work_area_id = ? AND ended_at IS NULL",
                    "params": ["${input.workAreaId}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO erp_outbox (id, entity_type, entity_id, payload_json, idempotency_key, status) SELECT gen_random_uuid(), 'STAGE_COMPLETED', ?, CONCAT('{\"workAreaId\":\"', ?, '\"}'), CONCAT('STAGE_COMPLETED:', ?), 'pending' WHERE NOT EXISTS (SELECT 1 FROM erp_outbox WHERE idempotency_key = CONCAT('STAGE_COMPLETED:', ?))",
                    "params": [
                        "${input.workAreaId}",
                        "${input.workAreaId}",
                        "${input.workAreaId}",
                        "${input.workAreaId}",
                    ],
                },
                {
                    "type": "writeVariable",
                    "objectPath": MACHINE_DEVICE,
                    "variable": "status",
                    "fields": {"value": "IDLE"},
                },
                {
                    "type": "writeVariable",
                    "objectPath": MACHINE_DEVICE,
                    "variable": "activeWorkAreaId",
                    "fields": {"value": ""},
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "status": "COMPLETED"},
                },
            ],
        ),
        fn(
            "mes_printing_generateOrder",
            [
                {"name": "machineCode", "type": "STRING"},
                {"name": "projectName", "type": "STRING"},
                {"name": "productName", "type": "STRING"},
                {"name": "customerName", "type": "STRING"},
            ],
            std_out(
                [
                    {"name": "orderId", "type": "STRING"},
                    {"name": "orderNo", "type": "STRING"},
                    {"name": "workAreaId", "type": "STRING"},
                    {"name": "areaId", "type": "STRING"},
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "machine",
                    "sql": "SELECT machine_code FROM mes_machine WHERE machine_code = COALESCE(NULLIF(?, ''), 'PR120')",
                    "params": ["${input.machineCode}"],
                },
                {"type": "failIfNull", "var": "machine", "message": "Machine not found"},
                {
                    "type": "setVar",
                    "var": "mc",
                    "value": "${machine.machine_code}",
                },
                {
                    "type": "selectOne",
                    "var": "meta",
                    "sql": "SELECT CONCAT('ORD-2026-', LPAD((SELECT COUNT(*) + 1 FROM mes_order)::text, 3, '0')) AS order_no, CONCAT('PRINT-OP-', LPAD((SELECT COUNT(*) + 1 FROM work_area WHERE machine_code = ?)::text, 3, '0')) AS area_id",
                    "params": ["${mc}"],
                },
                {
                    "type": "selectOne",
                    "var": "ids",
                    "sql": "SELECT gen_random_uuid()::text AS order_id, gen_random_uuid()::text AS work_area_id, gen_random_uuid()::text AS job_bag_id",
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO mes_order (id, order_no, project_name, product_name, customer_name) VALUES (?::uuid, ?, COALESCE(NULLIF(?, ''), 'Production run'), COALESCE(NULLIF(?, ''), 'Flexo print job'), COALESCE(NULLIF(?, ''), 'Demo Customer LLC'))",
                    "params": [
                        "${ids.order_id}",
                        "${meta.order_no}",
                        "${input.projectName}",
                        "${input.productName}",
                        "${input.customerName}",
                    ],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO work_area (id, mes_order_id, machine_code, area_id, operation_name, status, planned_start) VALUES (?::uuid, ?::uuid, ?, ?, CONCAT('Flexo print — ', ?), 'PLANNED', CURRENT_TIMESTAMP)",
                    "params": [
                        "${ids.work_area_id}",
                        "${ids.order_id}",
                        "${mc}",
                        "${meta.area_id}",
                        "${meta.order_no}",
                    ],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO job_bag (id, work_area_id, bag_no) VALUES (?::uuid, ?::uuid, CONCAT('JB-', ?))",
                    "params": ["${ids.job_bag_id}", "${ids.work_area_id}", "${meta.area_id}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO job_bag_document (id, job_bag_id, section_key, section_title, content_json) VALUES (gen_random_uuid(), ?::uuid, 'press', 'Press settings', '{\"ink\":\"Cyan+Magenta+Yellow\",\"speed\":100}')",
                    "params": ["${ids.job_bag_id}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO job_bag_document (id, job_bag_id, section_key, section_title, content_json) VALUES (gen_random_uuid(), ?::uuid, 'process', 'Process control', '{\"tension\":\"1.4 bar\",\"register\":\"pending\"}')",
                    "params": ["${ids.job_bag_id}"],
                },
                {
                    "type": "exec",
                    "sql": "INSERT INTO erp_outbox (id, entity_type, entity_id, payload_json, idempotency_key, status) SELECT gen_random_uuid(), 'ORDER_CREATED', ?, CONCAT('{\"orderNo\":\"', ?, '\",\"workAreaId\":\"', ?, '\"}'), CONCAT('ORDER_CREATED:', ?), 'pending' WHERE NOT EXISTS (SELECT 1 FROM erp_outbox WHERE idempotency_key = CONCAT('ORDER_CREATED:', ?))",
                    "params": [
                        "${ids.work_area_id}",
                        "${meta.order_no}",
                        "${ids.work_area_id}",
                        "${ids.work_area_id}",
                        "${ids.work_area_id}",
                    ],
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "orderId": "${ids.order_id}",
                        "orderNo": "${meta.order_no}",
                        "workAreaId": "${ids.work_area_id}",
                        "areaId": "${meta.area_id}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_generateOrderAuto",
            [],
            std_out(
                [
                    {"name": "generated", "type": "STRING"},
                    {"name": "orderNo", "type": "STRING"},
                    {"name": "workAreaId", "type": "STRING"},
                    {"name": "areaId", "type": "STRING"},
                ]
            ),
            [
                {
                    "type": "selectOne",
                    "var": "queue",
                    "sql": "SELECT COUNT(*)::int AS planned_cnt FROM work_area WHERE machine_code = 'PR120' AND status = 'PLANNED'",
                },
                {
                    "type": "when",
                    "var": "queue.planned_cnt",
                    "gte": 2,
                    "then": [
                        {
                            "type": "return",
                            "fields": {
                                "error_code": "OK",
                                "error_message": "Planned queue full",
                                "generated": "false",
                                "orderNo": "",
                                "workAreaId": "",
                                "areaId": "",
                            },
                        }
                    ],
                },
                {
                    "type": "selectOne",
                    "var": "tpl",
                    "sql": "SELECT CASE (SELECT COUNT(*) FROM mes_order) % 4 WHEN 0 THEN 'Labels Q3' WHEN 1 THEN 'Packaging run' WHEN 2 THEN 'Food sleeves' ELSE 'Industrial wrap' END AS project_name, CASE (SELECT COUNT(*) FROM mes_order) % 4 WHEN 0 THEN 'Shrink sleeve batch B' WHEN 1 THEN 'Flexo labels 40µm' WHEN 2 THEN 'Laminated film 25µm' ELSE 'PE shrink film' END AS product_name, CASE (SELECT COUNT(*) FROM mes_order) % 4 WHEN 0 THEN 'Acme Print LLC' WHEN 1 THEN 'Beta Brands' WHEN 2 THEN 'FoodPack Co' ELSE 'InduWrap Ltd' END AS customer_name",
                },
                {
                    "type": "invoke_function",
                    "objectPath": HUB,
                    "functionName": "mes_printing_generateOrder",
                    "var": "created",
                    "input": {
                        "machineCode": "PR120",
                        "projectName": "${tpl.project_name}",
                        "productName": "${tpl.product_name}",
                        "customerName": "${tpl.customer_name}",
                    },
                },
                {
                    "type": "return",
                    "fields": {
                        "error_code": "OK",
                        "error_message": "",
                        "generated": "true",
                        "orderNo": "${created.orderNo}",
                        "workAreaId": "${created.workAreaId}",
                        "areaId": "${created.areaId}",
                    },
                },
            ],
        ),
        fn(
            "mes_printing_pollErpOutbox",
            [],
            std_out(
                [
                    {
                        "name": "rows",
                        "type": "RECORD_LIST",
                        "nestedSchema": {
                            "name": "outbox_row",
                            "fields": [
                                {"name": "entityType", "type": "STRING"},
                                {"name": "entityId", "type": "STRING"},
                                {"name": "status", "type": "STRING"},
                            ],
                        },
                    }
                ]
            ),
            [
                {
                    "type": "selectMany",
                    "var": "pending",
                    "sql": "SELECT entity_type, entity_id, idempotency_key FROM erp_outbox WHERE status = 'pending' ORDER BY created_at LIMIT 20",
                },
                {
                    "type": "exec",
                    "sql": "UPDATE erp_outbox SET status = 'sent', processed_at = CURRENT_TIMESTAMP WHERE status = 'pending'",
                },
                {
                    "type": "map",
                    "var": "rows",
                    "source": "${pending}",
                    "fields": {
                        "entityType": "${item.entity_type}",
                        "entityId": "${item.entity_id}",
                        "status": "sent",
                    },
                },
                {
                    "type": "return",
                    "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"},
                },
            ],
        ),
    ]
    return functions


def build_reports() -> list:
    mc_param = ["machineCode"]
    wa_param = ["workAreaId"]
    wa_default = WA_ACTIVE
    return [
        {
            "reportId": "mes-printing-stages",
            "title": "Stages for machine",
            "description": "SCR-01 stage list",
            "reportType": "sql",
            "query": "SELECT w.id::text AS id, w.area_id, w.operation_name, w.status, o.order_no, o.product_name FROM work_area w JOIN mes_order o ON o.id = w.mes_order_id WHERE w.machine_code = COALESCE(NULLIF(?, ''), 'PR120') ORDER BY CASE w.status WHEN 'IN_PROGRESS' THEN 0 WHEN 'PAUSED' THEN 1 WHEN 'PLANNED' THEN 2 ELSE 3 END, w.area_id",
            "parameters": mc_param,
            "columns": [
                {"field": "area_id", "label": "Stage"},
                {"field": "operation_name", "label": "Operation"},
                {"field": "status", "label": "Status"},
                {"field": "order_no", "label": "Order"},
                {"field": "product_name", "label": "Product"},
            ],
            "maxRows": 50,
        },
        {
            "reportId": "mes-printing-input-rolls",
            "title": "Input rolls",
            "reportType": "sql",
            "query": f"SELECT barcode, length_m, current_length_m, status FROM material_roll WHERE work_area_id::text = COALESCE(NULLIF(?, ''), '{wa_default}') AND roll_kind = 'IN' ORDER BY barcode",
            "parameters": wa_param,
            "columns": [
                {"field": "barcode", "label": "Barcode"},
                {"field": "length_m", "label": "Length m"},
                {"field": "current_length_m", "label": "Remaining m"},
                {"field": "status", "label": "Status"},
            ],
            "maxRows": 20,
        },
        {
            "reportId": "mes-printing-output-rolls",
            "title": "Output rolls",
            "reportType": "sql",
            "query": f"SELECT barcode, length_m, current_length_m, status FROM material_roll WHERE work_area_id::text = COALESCE(NULLIF(?, ''), '{wa_default}') AND roll_kind = 'OUT' ORDER BY barcode",
            "parameters": wa_param,
            "columns": [
                {"field": "barcode", "label": "Barcode"},
                {"field": "length_m", "label": "Length m"},
                {"field": "current_length_m", "label": "Current m"},
                {"field": "status", "label": "Status"},
            ],
            "maxRows": 20,
        },
        {
            "reportId": "mes-printing-jb-sections",
            "title": "Job bag sections",
            "reportType": "sql",
            "query": f"SELECT d.section_key, d.section_title, d.content_json FROM job_bag_document d JOIN job_bag j ON j.id = d.job_bag_id WHERE j.work_area_id::text = COALESCE(NULLIF(?, ''), '{wa_default}') ORDER BY d.section_key",
            "parameters": wa_param,
            "columns": [
                {"field": "section_key", "label": "Section"},
                {"field": "section_title", "label": "Title"},
                {"field": "content_json", "label": "Content"},
            ],
            "maxRows": 20,
        },
        {
            "reportId": "mes-printing-machine-stock",
            "title": "Machine stock",
            "reportType": "sql",
            "query": "SELECT material_name, qty_kg FROM machine_stock WHERE machine_code = COALESCE(NULLIF(?, ''), 'PR120') ORDER BY material_name",
            "parameters": mc_param,
            "columns": [
                {"field": "material_name", "label": "Material"},
                {"field": "qty_kg", "label": "Qty kg"},
            ],
            "maxRows": 20,
        },
        {
            "reportId": "mes-printing-stage-events",
            "title": "Stage events",
            "reportType": "sql",
            "query": f"SELECT event_code, event_name, COALESCE(comment_text, '') AS comment_text, created_at::text AS created_at FROM production_event WHERE work_area_id::text = COALESCE(NULLIF(?, ''), '{wa_default}') ORDER BY created_at DESC",
            "parameters": wa_param,
            "columns": [
                {"field": "event_code", "label": "Code"},
                {"field": "event_name", "label": "Event"},
                {"field": "comment_text", "label": "Comment"},
                {"field": "created_at", "label": "When"},
            ],
            "maxRows": 50,
        },
        {
            "reportId": "mes-printing-complete-summary",
            "title": "Completion summary",
            "reportType": "sql",
            "query": f"SELECT w.area_id, w.status, (SELECT COUNT(*) FROM material_roll r WHERE r.work_area_id = w.id AND r.roll_kind = 'IN') AS input_rolls, (SELECT COUNT(*) FROM material_roll r WHERE r.work_area_id = w.id AND r.roll_kind = 'OUT') AS output_rolls, (SELECT COUNT(*) FROM production_event e WHERE e.work_area_id = w.id) AS event_count FROM work_area w WHERE w.id::text = COALESCE(NULLIF(?, ''), '{wa_default}')",
            "parameters": wa_param,
            "columns": [
                {"field": "area_id", "label": "Stage"},
                {"field": "status", "label": "Status"},
                {"field": "input_rolls", "label": "Input rolls"},
                {"field": "output_rolls", "label": "Output rolls"},
                {"field": "event_count", "label": "Events"},
            ],
            "maxRows": 5,
        },
    ]


FINE_GRID_SCALE = 7
FINE_COLUMNS = 12 * FINE_GRID_SCALE  # 84 — runtime / Dashboard Builder
FINE_ROW_HEIGHT = 8


def grid(widget: dict) -> dict:
    """Place widget on 12-column draft grid; layout() emits fine grid 84×8."""
    scaled = dict(widget)
    for key in ("x", "y", "w", "h"):
        if key in scaled and isinstance(scaled[key], int):
            scaled[key] = scaled[key] * FINE_GRID_SCALE
    return scaled


def layout(widgets: list) -> str:
    """Serialize dashboard layout in platform fine grid (canonical for deploy)."""
    return json.dumps(
        {
            "columns": FINE_COLUMNS,
            "rowHeight": FINE_ROW_HEIGHT,
            "widgets": [grid(w) for w in widgets],
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )


def fields_json(fields: list) -> str:
    """Serialize function-form fields; use instead of hand-escaped JSON strings."""
    return json.dumps(fields, ensure_ascii=False, separators=(",", ":"))


def wa_hidden_field() -> dict:
    """Hidden work area id: session override from SCR-01, else demo active stage."""
    return {
        "name": "workAreaId",
        "type": "text",
        "hidden": True,
        "paramKey": "workAreaId",
        "defaultValue": WA_ACTIVE,
    }


def build_dashboards() -> list:
    stages_layout = layout(
        [
            {
                "id": "help",
                "type": "html-snippet",
                "title": "SCR-01 Stage list",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 2,
                "htmlJson": "<p><strong>docanima SCR-01:</strong> выберите этап в таблице, затем <em>Start</em> / <em>Pause</em> / <em>Resume</em>. Новые заказы создаются автоматически (планировщик, пока в очереди &lt;2 <code>PLANNED</code>) или вручную ниже.</p>",
            },
            {
                "id": "stages",
                "type": "report",
                "title": "Stages (PR120)",
                "x": 0,
                "y": 2,
                "w": 12,
                "h": 5,
                "reportPath": "root.platform.reports.mes-printing-stages",
                "parametersJson": '{"machineCode":"PR120"}',
                "selectable": True,
                "filterable": True,
                "columnFiltersJson": '["area_id","operation_name","status","order_no","product_name"]',
                "rowSelectionKey": "id",
                "rowParamsFromRowJson": '{"workAreaId":"id","machineCode":"PR120"}',
            },
            {
                "id": "start",
                "type": "function-form",
                "title": "Start stage",
                "x": 0,
                "y": 7,
                "w": 4,
                "h": 3,
                "objectPath": HUB,
                "functionName": "mes_printing_startStage",
                "buttonLabel": "В работу",
                "fieldsJson": fields_json(
                    [wa_hidden_field(), {"name": "startedBy", "label": "Operator", "type": "text", "defaultValue": "operator"}]
                ),
                "requireSessionParamsJson": '["workAreaId"]',
            },
            {
                "id": "pause",
                "type": "function-form",
                "title": "Pause",
                "x": 4,
                "y": 7,
                "w": 4,
                "h": 3,
                "objectPath": HUB,
                "functionName": "mes_printing_pauseStage",
                "buttonLabel": "Пауза",
                "fieldsJson": fields_json([wa_hidden_field()]),
                "requireSessionParamsJson": '["workAreaId"]',
            },
            {
                "id": "resume",
                "type": "function-form",
                "title": "Resume",
                "x": 8,
                "y": 7,
                "w": 4,
                "h": 3,
                "objectPath": HUB,
                "functionName": "mes_printing_resumeStage",
                "buttonLabel": "Продолжить",
                "fieldsJson": fields_json([wa_hidden_field()]),
                "requireSessionParamsJson": '["workAreaId"]',
            },
            {
                "id": "gen-order",
                "type": "function-form",
                "title": "New order / stage",
                "x": 0,
                "y": 10,
                "w": 8,
                "h": 4,
                "objectPath": HUB,
                "functionName": "mes_printing_generateOrder",
                "buttonLabel": "Создать заказ",
                "fieldsJson": fields_json(
                    [
                        {"name": "machineCode", "label": "Machine", "type": "text", "defaultValue": "PR120"},
                        {"name": "projectName", "label": "Project", "type": "text", "defaultValue": "Labels run"},
                        {"name": "productName", "label": "Product", "type": "text", "defaultValue": "Flexo print job"},
                        {"name": "customerName", "label": "Customer", "type": "text", "defaultValue": "Demo Customer LLC"},
                    ]
                ),
            },
        ]
    )

    arm_tabs = json.dumps(
        [
            {
                "id": "jb",
                "label": "Job bag",
                "children": [
                    {
                        "id": "jb-sub",
                        "type": "sub-dashboard",
                        "title": "",
                        "x": 0,
                        "y": 0,
                        "w": 12,
                        "h": 8,
                        "targetDashboardPath": "root.platform.dashboards.mes-printing-job-bag",
                        "inheritContext": True,
                    }
                ],
            },
            {
                "id": "mat",
                "label": "Materials",
                "children": [
                    {
                        "id": "mat-sub",
                        "type": "sub-dashboard",
                        "title": "",
                        "x": 0,
                        "y": 0,
                        "w": 12,
                        "h": 8,
                        "targetDashboardPath": "root.platform.dashboards.mes-printing-materials",
                        "inheritContext": True,
                    }
                ],
            },
            {
                "id": "ev",
                "label": "Events",
                "children": [
                    {
                        "id": "ev-sub",
                        "type": "sub-dashboard",
                        "title": "",
                        "x": 0,
                        "y": 0,
                        "w": 12,
                        "h": 8,
                        "targetDashboardPath": "root.platform.dashboards.mes-printing-events",
                        "inheritContext": True,
                    }
                ],
            },
            {
                "id": "done",
                "label": "Complete",
                "children": [
                    {
                        "id": "done-sub",
                        "type": "sub-dashboard",
                        "title": "",
                        "x": 0,
                        "y": 0,
                        "w": 12,
                        "h": 8,
                        "targetDashboardPath": "root.platform.dashboards.mes-printing-complete",
                        "inheritContext": True,
                    }
                ],
            },
        ],
        ensure_ascii=False,
        separators=(",", ":"),
    )

    arm_layout = layout(
        [
            {
                "id": "arm-help",
                "type": "html-snippet",
                "title": "SCR-00 ARM shell",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 2,
                "htmlJson": "<p><strong>Исполнение заказа</strong> — мониторинг рулонов и вкладки JB / Materials / Events / Complete. Контекст: <code>workAreaId</code> из SCR-01 или активный <code>PRINT-OP-002</code>.</p>",
            },
            {
                "id": "machine-status",
                "type": "value",
                "title": "Machine status",
                "x": 0,
                "y": 2,
                "w": 3,
                "h": 2,
                "objectPath": MACHINE_DEVICE,
                "variableName": "status",
                "valueField": "value",
            },
            {
                "id": "machine-speed",
                "type": "value",
                "title": "Speed m/min",
                "x": 3,
                "y": 2,
                "w": 3,
                "h": 2,
                "objectPath": MACHINE_DEVICE,
                "variableName": "speedMpm",
                "valueField": "value",
                "decimals": 0,
            },
            {
                "id": "active-wa",
                "type": "value",
                "title": "Active work area",
                "x": 6,
                "y": 2,
                "w": 6,
                "h": 2,
                "objectPath": MACHINE_DEVICE,
                "variableName": "activeWorkAreaId",
                "valueField": "value",
            },
            {
                "id": "input-rolls",
                "type": "report",
                "title": "Input rolls",
                "x": 0,
                "y": 4,
                "w": 6,
                "h": 3,
                "reportPath": "root.platform.reports.mes-printing-input-rolls",
                "contextParamsJson": '{"workAreaId":"workAreaId"}',
                "parametersJson": f'{{"workAreaId":"{WA_ACTIVE}"}}',
            },
            {
                "id": "output-rolls",
                "type": "report",
                "title": "Output rolls",
                "x": 6,
                "y": 4,
                "w": 6,
                "h": 3,
                "reportPath": "root.platform.reports.mes-printing-output-rolls",
                "contextParamsJson": '{"workAreaId":"workAreaId"}',
                "parametersJson": f'{{"workAreaId":"{WA_ACTIVE}"}}',
            },
            {
                "id": "arm-tabs",
                "type": "tab-panel",
                "title": "Operator panels",
                "x": 0,
                "y": 7,
                "w": 12,
                "h": 8,
                "tabsJson": arm_tabs,
            },
        ]
    )

    jb_layout = layout(
        [
            {
                "id": "jb-help",
                "type": "html-snippet",
                "title": "SCR-02 Job bag",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 2,
                "htmlJson": "<p>Job bag sections (docanima P4–P5). Выберите строку в таблице, отредактируйте поля ниже и нажмите <em>Сохранить раздел</em>. PDF print — stub в v1.</p>",
            },
            {
                "id": "jb-sections",
                "type": "report",
                "title": "JB sections",
                "x": 0,
                "y": 2,
                "w": 12,
                "h": 4,
                "reportPath": "root.platform.reports.mes-printing-jb-sections",
                "contextParamsJson": '{"workAreaId":"workAreaId"}',
                "parametersJson": f'{{"workAreaId":"{WA_ACTIVE}"}}',
                "selectable": True,
                "rowSelectionKey": "section_key",
                "rowParamsFromRowJson": fields_json(
                    {
                        "sectionKey": "section_key",
                        "sectionTitle": "section_title",
                        "contentJson": "content_json",
                    }
                ),
            },
            {
                "id": "jb-save",
                "type": "function-form",
                "title": "Save section",
                "x": 0,
                "y": 6,
                "w": 12,
                "h": 5,
                "objectPath": HUB,
                "functionName": "mes_printing_saveJobBagSection",
                "buttonLabel": "Сохранить раздел",
                "fieldsJson": fields_json(
                    [
                        wa_hidden_field(),
                        {
                            "name": "sectionKey",
                            "label": "Section key",
                            "type": "text",
                            "defaultValue": "process",
                        },
                        {
                            "name": "sectionTitle",
                            "label": "Title",
                            "type": "text",
                            "defaultValue": "Process control",
                        },
                        {
                            "name": "contentJson",
                            "label": "JSON",
                            "type": "textarea",
                            "defaultValue": '{"tension":"1.2 bar"}',
                        },
                    ]
                ),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
            },
        ]
    )

    materials_layout = layout(
        [
            {
                "id": "mat-help",
                "type": "html-snippet",
                "title": "SCR-04 Materials",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 2,
                "htmlJson": "<p>Скан рулона, списание метража, возврат на склад (stub UC-13).</p>",
            },
            {
                "id": "stock",
                "type": "report",
                "title": "Machine stock",
                "x": 0,
                "y": 2,
                "w": 6,
                "h": 4,
                "reportPath": "root.platform.reports.mes-printing-machine-stock",
                "parametersJson": '{"machineCode":"PR120"}',
            },
            {
                "id": "register-in",
                "type": "function-form",
                "title": "Register input roll",
                "x": 6,
                "y": 2,
                "w": 6,
                "h": 4,
                "objectPath": HUB,
                "functionName": "mes_printing_registerInputRoll",
                "buttonLabel": "Ввод рулона",
                "fieldsJson": fields_json([wa_hidden_field(), {"name": "barcode", "label": "Barcode", "type": "text", "defaultValue": "IN-ROLL-NEW"}]),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
            },
            {
                "id": "writeoff",
                "type": "function-form",
                "title": "Write-off / return",
                "x": 0,
                "y": 6,
                "w": 6,
                "h": 5,
                "objectPath": HUB,
                "functionName": "mes_printing_writeOffReturn",
                "buttonLabel": "Списать",
                "fieldsJson": fields_json(
                    [
                        wa_hidden_field(),
                        {"name": "barcode", "label": "Barcode", "type": "text"},
                        {"name": "meters", "label": "Meters", "type": "number", "defaultValue": "100"},
                        {
                            "name": "returnRoll",
                            "label": "Return",
                            "type": "select",
                            "defaultValue": "false",
                            "selectOptions": [
                                {"value": "false", "label": "Write-off"},
                                {"value": "true", "label": "Return"},
                            ],
                        },
                    ]
                ),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
            },
            {
                "id": "consumption",
                "type": "function-form",
                "title": "Record consumption",
                "x": 6,
                "y": 6,
                "w": 6,
                "h": 4,
                "objectPath": HUB,
                "functionName": "mes_printing_recordConsumption",
                "buttonLabel": "Списать метраж",
                "fieldsJson": fields_json(
                    [
                        wa_hidden_field(),
                        {"name": "barcode", "label": "Barcode", "type": "text", "defaultValue": "IN-ROLL-1001"},
                        {"name": "meters", "label": "Meters", "type": "number", "defaultValue": "50"},
                    ]
                ),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
            },
        ]
    )

    events_layout = layout(
        [
            {
                "id": "ev-help",
                "type": "html-snippet",
                "title": "SCR-07 Events",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 2,
                "htmlJson": "<p>Журнал производственных событий и регистрация простоя/брака (UC-25 stub).</p>",
            },
            {
                "id": "ev-feed",
                "type": "event-feed",
                "title": "Hub events",
                "x": 0,
                "y": 2,
                "w": 6,
                "h": 5,
                "objectPath": HUB,
                "limit": 20,
            },
            {
                "id": "ev-report",
                "type": "report",
                "title": "Stage events",
                "x": 6,
                "y": 2,
                "w": 6,
                "h": 5,
                "reportPath": "root.platform.reports.mes-printing-stage-events",
                "contextParamsJson": '{"workAreaId":"workAreaId"}',
                "parametersJson": f'{{"workAreaId":"{WA_ACTIVE}"}}',
            },
            {
                "id": "ev-form",
                "type": "function-form",
                "title": "Register event",
                "x": 0,
                "y": 7,
                "w": 12,
                "h": 5,
                "objectPath": HUB,
                "functionName": "mes_printing_registerProductionEvent",
                "buttonLabel": "Зарегистрировать",
                "fieldsJson": fields_json(
                    [
                        wa_hidden_field(),
                        {"name": "eventCode", "label": "Code", "type": "text", "defaultValue": "110"},
                        {"name": "eventName", "label": "Name", "type": "text", "defaultValue": "Speed loss"},
                        {"name": "commentText", "label": "Comment", "type": "textarea"},
                    ]
                ),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
            },
        ]
    )

    complete_layout = layout(
        [
            {
                "id": "cmp-help",
                "type": "html-snippet",
                "title": "SCR-08 Complete",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 2,
                "htmlJson": "<p>Итог этапа и завершение (UC-16). Нужен хотя бы один выходной рулон.</p>",
            },
            {
                "id": "cmp-summary",
                "type": "report",
                "title": "Summary",
                "x": 0,
                "y": 2,
                "w": 8,
                "h": 4,
                "reportPath": "root.platform.reports.mes-printing-complete-summary",
                "contextParamsJson": '{"workAreaId":"workAreaId"}',
                "parametersJson": f'{{"workAreaId":"{WA_ACTIVE}"}}',
            },
            {
                "id": "assess",
                "type": "function",
                "title": "Readiness",
                "x": 8,
                "y": 2,
                "w": 4,
                "h": 2,
                "objectPath": HUB,
                "functionName": "mes_printing_assessCompletion",
                "buttonLabel": "Check readiness",
                "inputJson": f'{{"workAreaId":"{WA_ACTIVE}"}}',
            },
            {
                "id": "out-roll",
                "type": "function-form",
                "title": "Register output roll",
                "x": 8,
                "y": 4,
                "w": 4,
                "h": 4,
                "objectPath": HUB,
                "functionName": "mes_printing_registerOutputRoll",
                "buttonLabel": "Output roll",
                "fieldsJson": fields_json(
                    [
                        wa_hidden_field(),
                        {"name": "barcode", "label": "Barcode", "type": "text", "defaultValue": "OUT-ROLL-NEW"},
                        {"name": "lengthM", "label": "Length m", "type": "number", "defaultValue": "500"},
                    ]
                ),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
            },
            {
                "id": "complete",
                "type": "function-form",
                "title": "Complete stage",
                "x": 0,
                "y": 8,
                "w": 6,
                "h": 3,
                "objectPath": HUB,
                "functionName": "mes_printing_completeStage",
                "buttonLabel": "Завершить этап",
                "fieldsJson": fields_json([wa_hidden_field()]),
                "paramBindingsJson": '{"workAreaId":"workAreaId"}',
                "confirmMessage": "Завершить этап печати?",
            },
            {
                "id": "outbox",
                "type": "function",
                "title": "1S outbox (stub)",
                "x": 6,
                "y": 8,
                "w": 6,
                "h": 3,
                "objectPath": HUB,
                "functionName": "mes_printing_pollErpOutbox",
                "buttonLabel": "Poll outbox",
            },
        ]
    )

    return [
        {
            "path": "root.platform.dashboards.mes-printing-stages",
            "title": "SCR-01 Stages",
            "refreshIntervalMs": 5000,
            "layoutJson": stages_layout,
        },
        {
            "path": "root.platform.dashboards.mes-printing-arm",
            "title": "SCR-00 ARM",
            "refreshIntervalMs": 5000,
            "layoutJson": arm_layout,
        },
        {
            "path": "root.platform.dashboards.mes-printing-job-bag",
            "title": "SCR-02 Job bag",
            "refreshIntervalMs": 5000,
            "layoutJson": jb_layout,
        },
        {
            "path": "root.platform.dashboards.mes-printing-materials",
            "title": "SCR-04 Materials",
            "refreshIntervalMs": 5000,
            "layoutJson": materials_layout,
        },
        {
            "path": "root.platform.dashboards.mes-printing-events",
            "title": "SCR-07 Events",
            "refreshIntervalMs": 5000,
            "layoutJson": events_layout,
        },
        {
            "path": "root.platform.dashboards.mes-printing-complete",
            "title": "SCR-08 Complete",
            "refreshIntervalMs": 5000,
            "layoutJson": complete_layout,
        },
    ]


def build_bundle() -> dict:
    string_value = {
        "name": "stringValue",
        "fields": [{"name": "value", "type": "STRING"}],
    }
    int_value = {
        "name": "integerValue",
        "fields": [{"name": "value", "type": "INTEGER"}],
    }
    double_value = {
        "name": "doubleValue",
        "fields": [{"name": "value", "type": "DOUBLE"}],
    }

    return {
        "version": "1.0.0",
        "displayName": "MES Printing Contour",
        "tablePrefix": "",
        "schemaName": "app_mes_printing_contour",
        "migrations": [{"id": "mes_printing_contour_v1", "sql": MIGRATION_SQL}],
        "blueprints": [
            {
                "name": "printing-contour-hub-v1",
                "description": "docanima printing contour BFF hub",
                "type": "RELATIVE",
                "targetObjectType": "DEVICE",
                "variables": [],
                "events": [],
                "functions": [],
                "bindings": [],
                "parameters": {},
            },
            {
                "name": "print-machine-v1",
                "description": "Flexo press machine telemetry",
                "type": "RELATIVE",
                "targetObjectType": "DEVICE",
                "variables": [
                    {
                        "name": "status",
                        "description": "Machine status",
                        "group": "runtime",
                        "schema": string_value,
                        "readable": True,
                        "writable": True,
                        "defaultValue": {"schema": string_value, "rows": [{"value": "RUNNING"}]},
                    },
                    {
                        "name": "speedMpm",
                        "description": "Speed m/min",
                        "group": "telemetry",
                        "schema": int_value,
                        "readable": True,
                        "writable": True,
                        "defaultValue": {"schema": int_value, "rows": [{"value": 120}]},
                    },
                    {
                        "name": "activeWorkAreaId",
                        "description": "Active work area UUID",
                        "group": "runtime",
                        "schema": string_value,
                        "readable": True,
                        "writable": True,
                        "defaultValue": {"schema": string_value, "rows": [{"value": WA_ACTIVE}]},
                    },
                ],
                "events": [],
                "functions": [],
                "bindings": [],
                "parameters": {},
            },
        ],
        "objects": [
            {
                "parentPath": "root.platform.devices",
                "name": "printing-contour-hub",
                "type": "DEVICE",
                "displayName": "Printing Contour Hub",
                "description": "docanima printing contour BFF hub",
                "templateId": "printing-contour-hub-v1",
            },
            {
                "parentPath": "root.platform.devices",
                "name": "print-machine-pr120",
                "type": "DEVICE",
                "displayName": "Flexo PR120",
                "description": "Demo flexo press machine",
                "templateId": "print-machine-v1",
            },
        ],
        "functions": build_functions(),
        "reports": build_reports(),
        "dashboards": build_dashboards(),
        "events": [
            {"id": "stageStarted", "roles": ["operator", "admin"]},
            {"id": "stageCompleted", "roles": ["operator", "admin"]},
        ],
        "schedules": [
            {
                "scheduleId": "printing-order-feed",
                "enabled": True,
                "intervalMs": 180000,
                "actionType": "invoke_function",
                "action": {
                    "objectPath": HUB,
                    "functionName": "mes_printing_generateOrderAuto",
                },
            }
        ],
        "operatorUi": {
            "appId": "mes-printing-contour",
            "title": "MES Printing Contour",
            "defaultDashboard": "root.platform.dashboards.mes-printing-stages",
            "dashboards": [
                {"path": "root.platform.dashboards.mes-printing-stages", "title": "SCR-01 Stages"},
                {"path": "root.platform.dashboards.mes-printing-arm", "title": "SCR-00 ARM"},
                {"path": "root.platform.dashboards.mes-printing-job-bag", "title": "SCR-02 Job bag"},
                {"path": "root.platform.dashboards.mes-printing-materials", "title": "SCR-04 Materials"},
                {"path": "root.platform.dashboards.mes-printing-events", "title": "SCR-07 Events"},
                {"path": "root.platform.dashboards.mes-printing-complete", "title": "SCR-08 Complete"},
            ],
            "eventJournalObjectPath": HUB,
        },
    }


def main() -> None:
    root = Path(__file__).resolve().parent
    bundle = build_bundle()
    patch_bundle(bundle)
    out = root / "bundle.json"
    out.write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    test_resource = (
        root.parent.parent
        / "packages"
        / "ispf-server"
        / "src"
        / "test"
        / "resources"
        / "mes-printing-contour-bundle.json"
    )
    test_resource.write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {out}")
    print(f"Wrote {test_resource}")


if __name__ == "__main__":
    main()
