#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ERP-MES CMMS — full maintenance on top of erp-mes-core maint lite."""
import io
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
BUNDLE_OUT = os.path.join(ROOT, "bundle.json")
APP_ID = "erp-mes-cmms"
HUB = "root.platform.singleton-blueprints.erp-mes-cmms-hub-v1"
CORE_HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1"
CORE_SCHEMA = "app_erp_mes_core"


def F(name, typ="STRING"):
    return {"name": name, "type": typ}


def fn(name, inputs, outputs, steps):
    return {
        "objectPath": HUB,
        "functionName": name,
        "version": "1",
        "descriptor": {
            "inputSchema": {"name": "in", "fields": inputs},
            "outputSchema": {"name": "out", "fields": [
                {"name": "error_code", "type": "STRING"},
                {"name": "error_message", "type": "STRING"},
                *outputs,
            ]},
        },
        "source": {"type": "script", "body": json.dumps({"steps": steps}, ensure_ascii=False)},
    }


def OUT(*fields):
    return list(fields)


def RL(name, fields):
    return {"name": name, "type": "RECORD_LIST", "nestedSchema": {"name": name + "_row", "fields": fields}}


MIGRATIONS = [{
    "id": "cmms_m1",
    "sql": ";\n".join([
        """CREATE TABLE IF NOT EXISTS cmms_spare_part (
           part_id VARCHAR(64) PRIMARY KEY,
           name VARCHAR(256) NOT NULL,
           qty_on_hand NUMERIC(14,3) NOT NULL DEFAULT 0,
           uom VARCHAR(32),
           min_qty NUMERIC(14,3) NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS cmms_failure_code (
           code VARCHAR(64) PRIMARY KEY,
           description VARCHAR(256) NOT NULL,
           parent_code VARCHAR(64))""",
        """CREATE TABLE IF NOT EXISTS cmms_pm_plan (
           plan_id VARCHAR(64) PRIMARY KEY,
           equipment_id VARCHAR(64) NOT NULL,
           title VARCHAR(256) NOT NULL,
           interval_days INT NOT NULL DEFAULT 30,
           checklist_json VARCHAR(2000),
           next_due TIMESTAMP)""",
        """CREATE TABLE IF NOT EXISTS cmms_wo_labor (
           id VARCHAR(64) PRIMARY KEY,
           core_wo_id VARCHAR(64) NOT NULL,
           person_id VARCHAR(64),
           hours NUMERIC(14,3) NOT NULL DEFAULT 0,
           note VARCHAR(256))""",
        """CREATE TABLE IF NOT EXISTS cmms_wo_part_issue (
           id VARCHAR(64) PRIMARY KEY,
           core_wo_id VARCHAR(64) NOT NULL,
           part_id VARCHAR(64) NOT NULL,
           qty NUMERIC(14,3) NOT NULL,
           issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
        """INSERT INTO cmms_spare_part (part_id, name, qty_on_hand, uom, min_qty)
           SELECT 'SP-BEARING-01', 'Assembly bearing', 12, 'pcs', 2
           WHERE NOT EXISTS (SELECT 1 FROM cmms_spare_part WHERE part_id = 'SP-BEARING-01')""",
        """INSERT INTO cmms_failure_code (code, description, parent_code)
           SELECT 'FC-MECH', 'Mechanical', NULL
           WHERE NOT EXISTS (SELECT 1 FROM cmms_failure_code WHERE code = 'FC-MECH')""",
        """INSERT INTO cmms_failure_code (code, description, parent_code)
           SELECT 'FC-MECH-BEARING', 'Bearing wear', 'FC-MECH'
           WHERE NOT EXISTS (SELECT 1 FROM cmms_failure_code WHERE code = 'FC-MECH-BEARING')""",
        """INSERT INTO cmms_pm_plan (plan_id, equipment_id, title, interval_days, checklist_json)
           SELECT 'PM-WU-A01-M', 'WU-A01', 'Monthly assembly PM', 30, '["lube","inspect belt","torque check"]'
           WHERE NOT EXISTS (SELECT 1 FROM cmms_pm_plan WHERE plan_id = 'PM-WU-A01-M')""",
    ]),
}]

FUNCTIONS = [
    fn("cmms_listParts", [], OUT(RL("rows", [F("partId"), F("name"), F("qtyOnHand"), F("uom"), F("minQty")])), [
        {"type": "selectMany", "var": "rows_raw",
         "sql": "SELECT part_id, name, qty_on_hand, COALESCE(uom,'') AS uom, min_qty FROM cmms_spare_part ORDER BY part_id"},
        {"type": "map", "var": "rows", "source": "${rows_raw}", "fields": {
            "partId": "${item.part_id}", "name": "${item.name}", "qtyOnHand": "${item.qty_on_hand}",
            "uom": "${item.uom}", "minQty": "${item.min_qty}"}},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
    ]),
    fn("cmms_listPmPlans", [], OUT(RL("rows", [F("planId"), F("equipmentId"), F("title"), F("intervalDays")])), [
        {"type": "selectMany", "var": "rows_raw",
         "sql": "SELECT plan_id, equipment_id, title, interval_days FROM cmms_pm_plan ORDER BY plan_id"},
        {"type": "map", "var": "rows", "source": "${rows_raw}", "fields": {
            "planId": "${item.plan_id}", "equipmentId": "${item.equipment_id}",
            "title": "${item.title}", "intervalDays": "${item.interval_days}"}},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
    ]),
    fn("cmms_generatePmRequest", [F("planId"), F("requestId")], OUT(F("planId"), F("requestId")), [
        {"type": "selectOne", "var": "p",
         "sql": "SELECT plan_id, equipment_id, title FROM cmms_pm_plan WHERE plan_id = ?",
         "params": ["${input.planId}"]},
        {"type": "failIfNull", "var": "p", "message": "PM plan not found"},
        {"type": "invoke_function", "var": "req", "objectPath": CORE_HUB, "functionName": "emc_maint_createRequest",
         "input": {"requestId": "${input.requestId}", "equipmentId": "${p.equipment_id}",
                   "description": "PM: ${p.title}", "priority": "5"}},
        {"type": "return", "fields": {
            "error_code": "${req.error_code}", "error_message": "${req.error_message}",
            "planId": "${input.planId}", "requestId": "${input.requestId}"}},
    ]),
    fn("cmms_issuePart", [F("coreWoId"), F("partId"), F("qty")], OUT(F("partId")), [
        {"type": "exec", "sql":
         "INSERT INTO cmms_wo_part_issue (id, core_wo_id, part_id, qty) VALUES (gen_random_uuid(), ?, ?, CAST(? AS DOUBLE))",
         "params": ["${input.coreWoId}", "${input.partId}", "${input.qty}"]},
        {"type": "exec", "sql":
         "UPDATE cmms_spare_part SET qty_on_hand = qty_on_hand - CAST(? AS DOUBLE) WHERE part_id = ?",
         "params": ["${input.qty}", "${input.partId}"]},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "partId": "${input.partId}"}},
    ]),
    fn("cmms_bookLabor", [F("coreWoId"), F("personId"), F("hours"), F("note")], OUT(F("coreWoId")), [
        {"type": "exec", "sql":
         "INSERT INTO cmms_wo_labor (id, core_wo_id, person_id, hours, note) "
         "VALUES (gen_random_uuid(), ?, ?, CAST(? AS DOUBLE), ?)",
         "params": ["${input.coreWoId}", "${input.personId}", "${input.hours}", "${input.note}"]},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "coreWoId": "${input.coreWoId}"}},
    ]),
    fn("cmms_listFailureCodes", [], OUT(RL("rows", [F("code"), F("description"), F("parentCode")])), [
        {"type": "selectMany", "var": "rows_raw",
         "sql": "SELECT code, description, COALESCE(parent_code,'') AS parent_code FROM cmms_failure_code ORDER BY code"},
        {"type": "map", "var": "rows", "source": "${rows_raw}", "fields": {
            "code": "${item.code}", "description": "${item.description}", "parentCode": "${item.parent_code}"}},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
    ]),
    fn("cmms_bridgeListMaint", [], OUT(RL("rows", [F("requestId"), F("equipmentId"), F("status"), F("description")])), [
        {"type": "invoke_function", "var": "m", "objectPath": CORE_HUB, "functionName": "emc_maint_list",
         "input": {}},
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "", "rows": "${m.rows}"}},
    ]),
]

BLUEPRINTS = [{
    "name": "erp-mes-cmms-hub-v1",
    "description": "CMMS hub bridging core maintenance lite",
    "type": "SINGLETON",
    "variables": [],
}]

OBJECTS = []

REPORTS = [
    {"reportId": "cmms-spare-parts", "title": "Spare parts",
     "query": """
SELECT part_id, name, qty_on_hand, COALESCE(uom,'') AS uom, min_qty
FROM cmms_spare_part ORDER BY part_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("part_id", "Part"), ("name", "Name"), ("qty_on_hand", "On hand"),
         ("uom", "UOM"), ("min_qty", "Min")]]},
    {"reportId": "cmms-pm-plans", "title": "PM plans",
     "query": """
SELECT plan_id, equipment_id, title, interval_days, next_due
FROM cmms_pm_plan ORDER BY plan_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("plan_id", "Plan"), ("equipment_id", "Equipment"), ("title", "Title"),
         ("interval_days", "Days"), ("next_due", "Next due")]]},
    {"reportId": "cmms-core-maint", "title": "Core maintenance requests",
     "query": f"""
SELECT request_id, COALESCE(equipment_id,'') AS equipment_id,
       COALESCE(status,'') AS status, COALESCE(description,'') AS description
FROM {CORE_SCHEMA}.emc_maintenance_request
ORDER BY request_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("request_id", "Request"), ("equipment_id", "Equipment"),
         ("status", "Status"), ("description", "Description")]]},
]

DASHBOARDS = [{
    "path": "root.platform.dashboards.cmms-main",
    "title": "CMMS",
    "layoutJson": json.dumps({
        "columns": 84, "rowHeight": 8,
        "widgets": [
            {"id": "parts", "type": "report", "title": "Spare parts", "x": 0, "y": 0, "w": 42, "h": 24,
             "reportPath": "root.platform.reports.cmms-spare-parts"},
            {"id": "pm", "type": "report", "title": "PM plans", "x": 42, "y": 0, "w": 42, "h": 24,
             "reportPath": "root.platform.reports.cmms-pm-plans"},
            {"id": "gen", "type": "function-form", "title": "Generate PM request", "x": 0, "y": 24, "w": 42, "h": 18,
             "objectPath": HUB, "functionName": "cmms_generatePmRequest", "buttonLabel": "Create request",
             "fieldsJson": json.dumps([
                 {"name": "planId", "label": "Plan ID", "type": "text", "defaultValue": "PM-WU-A01-M"},
                 {"name": "requestId", "label": "Request ID", "type": "text", "defaultValue": "MR-DEMO-001"},
             ])},
            {"id": "maint", "type": "report", "title": "Core maintenance", "x": 42, "y": 24, "w": 42, "h": 18,
             "reportPath": "root.platform.reports.cmms-core-maint"},
            {"id": "issue", "type": "function-form", "title": "Issue part to WO", "x": 0, "y": 42, "w": 42, "h": 20,
             "objectPath": HUB, "functionName": "cmms_issuePart", "buttonLabel": "Issue",
             "fieldsJson": json.dumps([
                 {"name": "coreWoId", "label": "Core WO id", "type": "text"},
                 {"name": "partId", "label": "Part", "type": "text", "defaultValue": "SP-BEARING-01"},
                 {"name": "qty", "label": "Qty", "type": "text", "defaultValue": "1"},
             ])},
            {"id": "labor", "type": "function-form", "title": "Book labor", "x": 42, "y": 42, "w": 42, "h": 20,
             "objectPath": HUB, "functionName": "cmms_bookLabor", "buttonLabel": "Book",
             "fieldsJson": json.dumps([
                 {"name": "coreWoId", "label": "Core WO id", "type": "text"},
                 {"name": "personId", "label": "Person", "type": "text"},
                 {"name": "hours", "label": "Hours", "type": "text"},
                 {"name": "note", "label": "Note", "type": "text"},
             ])},
        ],
    }, ensure_ascii=False),
}]

bundle = {
    "version": "1.1.0",
    "displayName": "ERP-MES CMMS",
    "tablePrefix": "cmms_",
    "schemaName": "app_erp_mes_cmms",
    "requires": [{"appId": "erp-mes-core", "minVersion": "2.1.0"}],
    "migrations": MIGRATIONS,
    "objects": OBJECTS,
    "functions": FUNCTIONS,
    "blueprints": BLUEPRINTS,
    "reports": REPORTS,
    "dashboards": DASHBOARDS,
    "operatorUi": {
        "appId": APP_ID,
        "title": "MES CMMS",
        "defaultDashboard": "root.platform.dashboards.cmms-main",
        "dashboards": [{"path": "root.platform.dashboards.cmms-main", "title": "CMMS"}],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.cmms-spare-parts", "title": "Spare parts"},
            {"path": "root.platform.reports.cmms-pm-plans", "title": "PM plans"},
            {"path": "root.platform.reports.cmms-core-maint", "title": "Core maint"},
        ],
        "defaultReport": "root.platform.reports.cmms-spare-parts",
    },
    "metadata": {
        "product": "erp-mes-cmms",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "1.1.0 report tables for parts/PM/maint; prior invoke/hub fixes",
    },
}

if __name__ == "__main__":
    with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(bundle, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("Wrote", BUNDLE_OUT)
