#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ERP-MES APS — finite planning UI on top of erp-mes-core (≥2.1)."""
import io
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
BUNDLE_OUT = os.path.join(ROOT, "bundle.json")
APP_ID = "erp-mes-aps"
HUB = "root.platform.singleton-blueprints.erp-mes-aps-hub-v1"
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


MIGRATIONS = [
    {"id": "aps_m1", "sql": ";\n".join([
        """CREATE TABLE IF NOT EXISTS aps_plan_freeze (
           freeze_id VARCHAR(64) PRIMARY KEY,
           equipment_id VARCHAR(64),
           frozen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
           note VARCHAR(256))""",
        """INSERT INTO aps_plan_freeze (freeze_id, equipment_id, note)
           SELECT 'FREEZE-DEMO', 'WU-A01', 'Demo freeze placeholder'
           WHERE NOT EXISTS (SELECT 1 FROM aps_plan_freeze WHERE freeze_id = 'FREEZE-DEMO')""",
    ])},
]

FUNCTIONS = [
    fn("aps_listBoard", [], OUT(RL("rows", [
        F("jobNo"), F("equipmentId"), F("dispatchStatus"), F("plannedStart"), F("plannedEnd"), F("quantity")
    ])), [
        {"type": "invoke_function", "var": "board", "objectPath": CORE_HUB, "functionName": "emc_joborder_listBoard",
         "input": {"equipmentId": ""}},
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "", "rows": "${board.rows}"}},
    ]),
    fn("aps_listConflicts", [], OUT(RL("rows", [
        F("jobNo"), F("equipmentId"), F("dispatchStatus"), F("conflictKind"), F("detail")
    ])), [
        {"type": "invoke_function", "var": "c", "objectPath": CORE_HUB, "functionName": "emc_aps_listConflicts",
         "input": {}},
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "", "rows": "${c.rows}"}},
    ]),
    fn("aps_replanJob", [F("jobNo"), F("plannedStart"), F("plannedEnd")], OUT(F("jobNo")), [
        {"type": "invoke_function", "var": "r", "objectPath": CORE_HUB, "functionName": "emc_joborder_updatePlan",
         "input": {"jobNo": "${input.jobNo}", "plannedStart": "${input.plannedStart}",
                   "plannedEnd": "${input.plannedEnd}"}},
        {"type": "return", "fields": {
            "error_code": "${r.error_code}", "error_message": "${r.error_message}",
            "jobNo": "${input.jobNo}"}},
    ]),
    fn("aps_freezeEquipment", [F("equipmentId"), F("note")], OUT(F("freezeId")), [
        {"type": "exec", "sql":
         "INSERT INTO aps_plan_freeze (freeze_id, equipment_id, note) VALUES (gen_random_uuid(), ?, ?)",
         "params": ["${input.equipmentId}", "${input.note}"]},
        {"type": "return", "fields": {
            "error_code": "OK", "error_message": "", "freezeId": "CREATED"}},
    ]),
    fn("aps_listFreezes", [], OUT(RL("rows", [F("freezeId"), F("equipmentId"), F("frozenAt"), F("note")])), [
        {"type": "selectMany", "var": "rows_raw",
         "sql": "SELECT freeze_id, COALESCE(equipment_id,'') AS equipment_id, frozen_at, COALESCE(note,'') AS note "
                "FROM aps_plan_freeze ORDER BY frozen_at DESC"},
        {"type": "map", "var": "rows", "source": "${rows_raw}", "fields": {
            "freezeId": "${item.freeze_id}", "equipmentId": "${item.equipment_id}",
            "frozenAt": "${item.frozen_at}", "note": "${item.note}"}},
        {"type": "return", "fields": {"error_code": "OK", "error_message": "", "rows": "${rows}"}},
    ]),
]

BLUEPRINTS = [{
    "name": "erp-mes-aps-hub-v1",
    "description": "APS planning hub (requires erp-mes-core)",
    "type": "SINGLETON",
    "variables": [],
}]

OBJECTS = []

REPORTS = [
    {"reportId": "aps-job-board", "title": "APS Job Board",
     "description": "Active job orders from erp-mes-core (cross-schema).",
     "query": f"""
SELECT o.job_no, o.dispatch_status, COALESCE(o.equipment_id,'') AS equipment_id,
       COALESCE(o.priority, 0) AS priority, o.planned_start, o.planned_end,
       COALESCE(wr.quantity, 0) AS quantity, COALESCE(wr.uom,'') AS uom
FROM {CORE_SCHEMA}.emc_job_order o
JOIN {CORE_SCHEMA}.emc_work_request wr ON wr.request_id = o.request_id
WHERE o.dispatch_status NOT IN ('ENDED', 'ABORTED', 'CANCELLED')
ORDER BY o.priority, o.planned_start
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("job_no", "Job #"), ("dispatch_status", "Status"), ("equipment_id", "Equipment"),
         ("priority", "Pri"), ("planned_start", "Start"), ("planned_end", "End"),
         ("quantity", "Qty"), ("uom", "UOM")]]},
    {"reportId": "aps-conflicts", "title": "APS Conflicts",
     "description": "Resource busy + missing capability window.",
     "query": f"""
SELECT o.job_no, COALESCE(o.equipment_id,'') AS equipment_id, o.dispatch_status,
       'RESOURCE_BUSY' AS conflict_kind,
       CONCAT('Another RUNNING job on ', o.equipment_id) AS detail
FROM {CORE_SCHEMA}.emc_job_order o
WHERE o.dispatch_status IN ('ALLOWED','NOT_ALLOWED')
AND EXISTS (
  SELECT 1 FROM {CORE_SCHEMA}.emc_job_order r
  WHERE r.equipment_id = o.equipment_id AND r.dispatch_status = 'RUNNING' AND r.job_no <> o.job_no
)
UNION ALL
SELECT o.job_no, COALESCE(o.equipment_id,'') AS equipment_id, o.dispatch_status,
       'CAPABILITY_WINDOW' AS conflict_kind,
       'No AVAILABLE operations capability window for equipment' AS detail
FROM {CORE_SCHEMA}.emc_job_order o
WHERE o.dispatch_status IN ('ALLOWED','NOT_ALLOWED')
AND o.equipment_id IS NOT NULL
AND NOT EXISTS (
  SELECT 1 FROM {CORE_SCHEMA}.emc_operations_capability c
  JOIN {CORE_SCHEMA}.emc_ops_capability_equipment ce ON ce.capability_id = c.capability_id
  WHERE ce.equipment_id = o.equipment_id AND c.status = 'AVAILABLE'
  AND (c.available_from IS NULL OR c.available_from <= CURRENT_TIMESTAMP)
  AND (c.available_to IS NULL OR c.available_to >= CURRENT_TIMESTAMP)
)
ORDER BY job_no
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("job_no", "Job #"), ("equipment_id", "Equipment"), ("dispatch_status", "Status"),
         ("conflict_kind", "Kind"), ("detail", "Detail")]]},
    {"reportId": "aps-freezes", "title": "Plan freezes",
     "description": "Frozen equipment windows.",
     "query": """
SELECT freeze_id, COALESCE(equipment_id,'') AS equipment_id, frozen_at, COALESCE(note,'') AS note
FROM aps_plan_freeze ORDER BY frozen_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("freeze_id", "Freeze"), ("equipment_id", "Equipment"),
         ("frozen_at", "Frozen at"), ("note", "Note")]]},
]

DASHBOARDS = [{
    "path": "root.platform.dashboards.aps-board",
    "title": "APS Planner",
    "layoutJson": json.dumps({
        "columns": 84, "rowHeight": 8,
        "widgets": [
            {"id": "board", "type": "report", "title": "Job board", "x": 0, "y": 0, "w": 56, "h": 32,
             "reportPath": "root.platform.reports.aps-job-board"},
            {"id": "conflicts", "type": "report", "title": "Conflicts", "x": 56, "y": 0, "w": 28, "h": 32,
             "reportPath": "root.platform.reports.aps-conflicts"},
            {"id": "replan", "type": "function-form", "title": "Replan job", "x": 0, "y": 32, "w": 28, "h": 22,
             "objectPath": HUB, "functionName": "aps_replanJob", "buttonLabel": "Replan",
             "fieldsJson": json.dumps([
                 {"name": "jobNo", "label": "Job #", "type": "text"},
                 {"name": "plannedStart", "label": "Planned start", "type": "text"},
                 {"name": "plannedEnd", "label": "Planned end", "type": "text"},
             ])},
            {"id": "freeze", "type": "function-form", "title": "Freeze equipment", "x": 28, "y": 32, "w": 28, "h": 22,
             "objectPath": HUB, "functionName": "aps_freezeEquipment", "buttonLabel": "Freeze",
             "fieldsJson": json.dumps([
                 {"name": "equipmentId", "label": "Equipment", "type": "text", "defaultValue": "WU-A01"},
                 {"name": "note", "label": "Note", "type": "text"},
             ])},
            {"id": "freezes", "type": "report", "title": "Freezes", "x": 56, "y": 32, "w": 28, "h": 22,
             "reportPath": "root.platform.reports.aps-freezes"},
        ],
    }, ensure_ascii=False),
}]

bundle = {
    "version": "1.1.0",
    "displayName": "ERP-MES APS",
    "tablePrefix": "aps_",
    "schemaName": "app_erp_mes_aps",
    "requires": [{"appId": "erp-mes-core", "minVersion": "2.1.0"}],
    "migrations": MIGRATIONS,
    "objects": OBJECTS,
    "functions": FUNCTIONS,
    "blueprints": BLUEPRINTS,
    "reports": REPORTS,
    "dashboards": DASHBOARDS,
    "operatorUi": {
        "appId": APP_ID,
        "title": "MES APS",
        "defaultDashboard": "root.platform.dashboards.aps-board",
        "dashboards": [{"path": "root.platform.dashboards.aps-board", "title": "Planner"}],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.aps-job-board", "title": "Job Board"},
            {"path": "root.platform.reports.aps-conflicts", "title": "Conflicts"},
            {"path": "root.platform.reports.aps-freezes", "title": "Freezes"},
        ],
        "defaultReport": "root.platform.reports.aps-job-board",
    },
    "metadata": {
        "product": "erp-mes-aps",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "1.1.0 report tables for board/conflicts/freezes; prior invoke/hub fixes",
    },
}

if __name__ == "__main__":
    with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(bundle, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("Wrote", BUNDLE_OUT)
