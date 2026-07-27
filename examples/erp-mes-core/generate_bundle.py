#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ERP-MES Core (ISA-95) bundle generator.

Canonical ISA-95 (IEC 62264) foundation bundle for ISPF:
  Part 1  - equipment hierarchy (Enterprise..Work Unit, Storage Zone/Unit)
  Part 2  - resource models: Equipment / Material / Personnel / Process Segment
  Part 3  - activity matrix Production(8/8) Quality(core) Inventory(core) Maintenance(lite)
  Part 4  - Work Master -> Work Schedule/Request -> Job Order -> Job Response (+Actuals)
  Part 5  - B2M-style transactions: verb + noun outbox/inbox with idempotent ACK
  KPI     - OEE per ISO 22400 vocabulary

Dialect: works on H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
Rules: UUID PKs + gen_random_uuid() (not H2-only RANDOM_UUID — app search_path
excludes public on PostgreSQL), no `::` casts, CREATE TABLE IF NOT EXISTS,
seeds via INSERT ... SELECT ... WHERE NOT EXISTS (re-entrant on redeploy).
Script DSL: validator white-listed steps only (see FunctionScriptValidator).
"""
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ROOT)
from uml_extent import (  # noqa: E402
    M16_UML_PART2,
    M17_UML_PART4,
    build_uml_functions,
)
from m21_extent import (  # noqa: E402
    M18_PART5_KPI,
    build_m21_functions,
)
from m22_gost_extent import (  # noqa: E402
    M19_GOST_GAPS,
    build_gost_functions,
)
from m20_demo_extent import (  # noqa: E402
    M20_DEMO_RICH,
    build_m20_functions,
)

BUNDLE_OUT = os.path.join(ROOT, "bundle.json")
APP_ID = "erp-mes-core"
SCHEMA = "app_erp_mes_core"
HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1"
WU_A01 = "root.platform.devices.emc-wu-a01"
WU_A02 = "root.platform.devices.emc-wu-a02"

# ---------------------------------------------------------------------------
# DSL helpers
# ---------------------------------------------------------------------------

def F(name, type_="STRING"):
    return {"name": name, "type": type_}


def RL(name, fields):
    return {"name": name, "type": "RECORD_LIST",
            "nestedSchema": {"name": name + "_row", "fields": fields}}


def OUT(*extra):
    return [F("error_code"), F("error_message"), *extra]


def fn(name, inputs, outputs, steps):
    return {
        "objectPath": HUB,
        "functionName": name,
        "version": "1",
        "descriptor": {
            "inputSchema": {"name": "in", "fields": inputs},
            "outputSchema": {"name": "out", "fields": outputs},
        },
        "source": {"type": "script", "body": json.dumps({"steps": steps}, ensure_ascii=False)},
    }


def sel1(var, sql, params=None):
    step = {"type": "selectOne", "var": var, "sql": sql}
    if params:
        step["params"] = params
    return step


def selN(var, sql, params=None):
    step = {"type": "selectMany", "var": var, "sql": sql}
    if params:
        step["params"] = params
    return step


def ex(sql, params=None):
    step = {"type": "exec", "sql": sql}
    if params:
        step["params"] = params
    return step


def ret(fields):
    return {"type": "return", "fields": fields}


def fail_null(var, code, msg):
    return {"type": "failIfNull", "var": var, "error_code": code, "error_message": msg}


def fail_ne(var, equals, code, msg):
    return {"type": "failIfNotEquals", "var": var, "equals": equals,
            "error_code": code, "error_message": msg}


def when(cond, then, els=None):
    step = {"type": "when", "then": then}
    step.update(cond)
    if els:
        step["else"] = els
    return step


def map_rows(var, source, fields):
    return {"type": "map", "var": var, "source": source, "fields": fields}


def write_var(object_path, variable, fields):
    return {"type": "writeVariable", "objectPath": object_path, "variable": variable, "fields": fields}


def invoke(var, function_name, input_):
    return {"type": "invoke_function", "objectPath": HUB, "functionName": function_name,
            "var": var, "input": input_}


def json_parse(var, source, fields):
    return {"type": "jsonParse", "var": var, "source": source, "fields": fields}


def set_var(var, value=None, expression=None):
    step = {"type": "setVar", "var": var}
    if expression is not None:
        step["expression"] = expression
    else:
        step["value"] = value
    return step


def seed(table, columns, values, where):
    """Idempotent seed. Values: str -> quoted literal, None -> NULL,
    str prefixed with '!' -> raw SQL expression (TIMESTAMP '...', CURRENT_TIMESTAMP, true)."""
    if len(columns) != len(values):
        raise ValueError(f"seed({table}): {len(columns)} columns vs {len(values)} values")
    cols = ", ".join(columns)
    parts = []
    for v in values:
        if v is None:
            parts.append("NULL")
        elif isinstance(v, str) and v.startswith("!"):
            parts.append(v[1:])
        else:
            parts.append("'" + str(v).replace("'", "''") + "'")
    placeholders = ", ".join(parts)
    return (f"INSERT INTO {table} ({cols}) SELECT {placeholders} "
            f"WHERE NOT EXISTS (SELECT 1 FROM {table} WHERE {where})")


# ---------------------------------------------------------------------------
# Migrations (M1-M12), SQL joined with "; " - no semicolons inside statements
# ---------------------------------------------------------------------------

M1_EQUIPMENT_PERSONNEL = ";\n".join([
    # ISA-95 Part 2: Equipment model + role-based hierarchy (Part 1)
    """CREATE TABLE IF NOT EXISTS emc_equipment_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       equipment_level VARCHAR(32) NOT NULL,
       parent_class_id VARCHAR(64))""",
    """CREATE TABLE IF NOT EXISTS emc_equipment (
       equipment_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64),
       equipment_level VARCHAR(32) NOT NULL,
       parent_id VARCHAR(64),
       hierarchy_path VARCHAR(512),
       description VARCHAR(256))""",
    # Canonical Property mechanism (extension without migrations)
    """CREATE TABLE IF NOT EXISTS emc_equipment_property (
       equipment_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32))""",
    # ISA-95 Part 2: Personnel model
    """CREATE TABLE IF NOT EXISTS emc_personnel_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256))""",
    """CREATE TABLE IF NOT EXISTS emc_person (
       person_id VARCHAR(64) PRIMARY KEY,
       person_name VARCHAR(256) NOT NULL,
       personnel_class_id VARCHAR(64))""",
    # Qualification: right on equipment instance XOR equipment class
    """CREATE TABLE IF NOT EXISTS emc_person_qualification (
       person_id VARCHAR(64) NOT NULL,
       equipment_id VARCHAR(64),
       equipment_class_id VARCHAR(64),
       qualification VARCHAR(128) DEFAULT 'OPERATE')""",
    # --- seeds: hierarchy ENT-DEMO -> SITE-01 -> AREA-PROD -> LINE-A -> WU-A01/A02 ---
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-ASSEMBLY-MACHINE", "Assembly machine class", "WORK_UNIT", None],
         "class_id = 'EQC-ASSEMBLY-MACHINE'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-PACK-MACHINE", "Packaging machine class", "WORK_UNIT", None],
         "class_id = 'EQC-PACK-MACHINE'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["ENT-DEMO", None, "ENTERPRISE", None, "ENT-DEMO", "Demo enterprise"],
         "equipment_id = 'ENT-DEMO'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["SITE-01", None, "SITE", "ENT-DEMO", "ENT-DEMO/SITE-01", "Demo site"],
         "equipment_id = 'SITE-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["AREA-PROD", None, "AREA", "SITE-01", "ENT-DEMO/SITE-01/AREA-PROD", "Production area"],
         "equipment_id = 'AREA-PROD'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["LINE-A", None, "WORK_CENTER", "AREA-PROD", "ENT-DEMO/SITE-01/AREA-PROD/LINE-A", "Production line A (work center)"],
         "equipment_id = 'LINE-A'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WU-A01", "EQC-ASSEMBLY-MACHINE", "WORK_UNIT", "LINE-A", "ENT-DEMO/SITE-01/AREA-PROD/LINE-A/WU-A01", "Assembly work unit A01"],
         "equipment_id = 'WU-A01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WU-A02", "EQC-PACK-MACHINE", "WORK_UNIT", "LINE-A", "ENT-DEMO/SITE-01/AREA-PROD/LINE-A/WU-A02", "Packaging work unit A02"],
         "equipment_id = 'WU-A02'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-CENTRAL", None, "STORAGE_ZONE", "SITE-01", "ENT-DEMO/SITE-01/WH-CENTRAL", "Central warehouse (storage zone)"],
         "equipment_id = 'WH-CENTRAL'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-LINE-A01", None, "STORAGE_UNIT", "WH-CENTRAL", "ENT-DEMO/SITE-01/WH-CENTRAL/WH-LINE-A01", "Line-side storage A01"],
         "equipment_id = 'WH-LINE-A01'"),
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-OPERATOR", "Line operator"], "class_id = 'PCL-OPERATOR'"),
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-SUPERVISOR", "Shift supervisor"], "class_id = 'PCL-SUPERVISOR'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-001", "Ivan Operator", "PCL-OPERATOR"], "person_id = 'EMP-001'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-002", "Petr Supervisor", "PCL-SUPERVISOR"], "person_id = 'EMP-002'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-003", "Anna Operator", "PCL-OPERATOR"], "person_id = 'EMP-003'"),
    seed("emc_person_qualification", ["person_id", "equipment_id", "equipment_class_id", "qualification"],
         ["EMP-001", "WU-A01", None, "OPERATE"],
         "person_id = 'EMP-001' AND equipment_id = 'WU-A01'"),
    seed("emc_person_qualification", ["person_id", "equipment_id", "equipment_class_id", "qualification"],
         ["EMP-003", None, "EQC-PACK-MACHINE", "OPERATE"],
         "person_id = 'EMP-003' AND equipment_class_id = 'EQC-PACK-MACHINE'"),
])

M2_MATERIAL = ";\n".join([
    # ISA-95 Part 2: Material model
    """CREATE TABLE IF NOT EXISTS emc_material_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       parent_class_id VARCHAR(64))""",
    """CREATE TABLE IF NOT EXISTS emc_material_definition (
       definition_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64),
       kind VARCHAR(16) NOT NULL,
       base_uom VARCHAR(16) NOT NULL,
       description VARCHAR(256))""",
    """CREATE TABLE IF NOT EXISTS emc_material_lot (
       lot_id VARCHAR(64) PRIMARY KEY,
       barcode VARCHAR(128) NOT NULL UNIQUE,
       definition_id VARCHAR(64) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'STOCK',
       disposition VARCHAR(32),
       storage_location VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       base_uom VARCHAR(16) NOT NULL DEFAULT 'pcs',
       weight_kg NUMERIC(14,3),
       length_m NUMERIC(14,3),
       on_equipment_id VARCHAR(64),
       on_job_order_id VARCHAR(64),
       external_system VARCHAR(64),
       external_id VARCHAR(128),
       version_no INTEGER NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_material_sublot (
       sublot_id VARCHAR(64) PRIMARY KEY,
       lot_id VARCHAR(64) NOT NULL,
       barcode VARCHAR(128) NOT NULL UNIQUE,
       status VARCHAR(32) NOT NULL DEFAULT 'STOCK',
       storage_location VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0)""",
    """CREATE TABLE IF NOT EXISTS emc_material_lot_property (
       lot_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32))""",
    # --- seeds ---
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-RAW", "Raw materials", None], "class_id = 'MCL-RAW'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-WIP", "Work in progress", None], "class_id = 'MCL-WIP'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-FG", "Finished goods", None], "class_id = 'MCL-FG'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["RAW-PLASTIC-GRANULE", "MCL-RAW", "RAW", "kg", "Plastic granulate"], "definition_id = 'RAW-PLASTIC-GRANULE'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["RAW-PACKAGING-BOX", "MCL-RAW", "RAW", "pcs", "Packaging box"], "definition_id = 'RAW-PACKAGING-BOX'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-HOUSING", "MCL-WIP", "WIP", "pcs", "Assembled housing"], "definition_id = 'WIP-HOUSING'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["FG-UNIT-PACKED", "MCL-FG", "FG", "pcs", "Packed unit"], "definition_id = 'FG-UNIT-PACKED'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-RAW-0001", "BC-RAW-0001", "RAW-PLASTIC-GRANULE", "STOCK", "WH-LINE-A01", "500", "kg", "500"],
         "lot_id = 'LOT-RAW-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-RAW-0002", "BC-RAW-0002", "RAW-PLASTIC-GRANULE", "STOCK", "WH-CENTRAL", "1000", "kg", "1000"],
         "lot_id = 'LOT-RAW-0002'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-WIP-0001", "BC-WIP-0001", "WIP-HOUSING", "STOCK", "WH-CENTRAL", "200", "pcs", None],
         "lot_id = 'LOT-WIP-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-FG-0001", "BC-FG-0001", "FG-UNIT-PACKED", "STOCK", "WH-CENTRAL", "150", "pcs", None],
         "lot_id = 'LOT-FG-0001'"),
])


M3_SEGMENT_WORKDEF = ";\n".join([
    # ISA-95 Part 2: Process Segment model; Part 4: Work Definition (Work Master)
    """CREATE TABLE IF NOT EXISTS emc_process_segment (
       segment_id VARCHAR(64) PRIMARY KEY,
       parent_id VARCHAR(64),
       operations_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512))""",
    # Segment specifications = canonical "operation BOM/routing"
    """CREATE TABLE IF NOT EXISTS emc_segment_material_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       material_class_id VARCHAR(64),
       definition_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16))""",
    """CREATE TABLE IF NOT EXISTS emc_segment_equipment_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       equipment_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_use VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_segment_personnel_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       personnel_class_id VARCHAR(64),
       person_id VARCHAR(64),
       personnel_use VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_work_master (
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       segment_id VARCHAR(64) NOT NULL,
       duration_min NUMERIC(10,1),
       description VARCHAR(256),
       PRIMARY KEY (work_master_id, version))""",
    # --- seeds ---
    seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
         ["SEG-ASSEMBLE", None, "PRODUCTION", "Assembly", "Assemble housing from granulate"],
         "segment_id = 'SEG-ASSEMBLE'"),
    seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
         ["SEG-PACK", None, "PRODUCTION", "Packing", "Pack housing into boxes"],
         "segment_id = 'SEG-PACK'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-ASSEMBLE:IN-GRANULE", "SEG-ASSEMBLE", None, "RAW-PLASTIC-GRANULE", "CONSUMED", "2.5", "kg"],
         "spec_id = 'SEG-ASSEMBLE:IN-GRANULE'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-ASSEMBLE:OUT-HOUSING", "SEG-ASSEMBLE", None, "WIP-HOUSING", "PRODUCED", "1", "pcs"],
         "spec_id = 'SEG-ASSEMBLE:OUT-HOUSING'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-PACK:IN-HOUSING", "SEG-PACK", None, "WIP-HOUSING", "CONSUMED", "1", "pcs"],
         "spec_id = 'SEG-PACK:IN-HOUSING'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-PACK:IN-BOX", "SEG-PACK", None, "RAW-PACKAGING-BOX", "CONSUMED", "1", "pcs"],
         "spec_id = 'SEG-PACK:IN-BOX'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-PACK:OUT-FG", "SEG-PACK", None, "FG-UNIT-PACKED", "PRODUCED", "1", "pcs"],
         "spec_id = 'SEG-PACK:OUT-FG'"),
    seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
         ["SEG-ASSEMBLE:EQ", "SEG-ASSEMBLE", "EQC-ASSEMBLY-MACHINE", None, "PRIMARY", "1"],
         "spec_id = 'SEG-ASSEMBLE:EQ'"),
    seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
         ["SEG-PACK:EQ", "SEG-PACK", "EQC-PACK-MACHINE", None, "PRIMARY", "1"],
         "spec_id = 'SEG-PACK:EQ'"),
    seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
         ["SEG-ASSEMBLE:PERS", "SEG-ASSEMBLE", "PCL-OPERATOR", None, "OPERATOR", "1"],
         "spec_id = 'SEG-ASSEMBLE:PERS'"),
    seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
         ["SEG-PACK:PERS", "SEG-PACK", "PCL-OPERATOR", None, "OPERATOR", "1"],
         "spec_id = 'SEG-PACK:PERS'"),
    seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
         ["WM-ASSEMBLE", "1", "SEG-ASSEMBLE", "60", "Assemble housing (master)"],
         "work_master_id = 'WM-ASSEMBLE' AND version = '1'"),
    seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
         ["WM-PACK", "1", "SEG-PACK", "30", "Pack housing (master)"],
         "work_master_id = 'WM-PACK' AND version = '1'"),
])

M4_WORK_SCHEDULE = ";\n".join([
    # ISA-95 Part 4: Work Schedule -> Work Request -> Job Order (+Requirements)
    """CREATE TABLE IF NOT EXISTS emc_work_schedule (
       schedule_id VARCHAR(64) PRIMARY KEY,
       external_ref VARCHAR(128),
       schedule_state VARCHAR(32) NOT NULL DEFAULT 'FIRM',
       start_time TIMESTAMP,
       end_time TIMESTAMP,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_work_request (
       request_id VARCHAR(64) PRIMARY KEY,
       schedule_id VARCHAR(64) NOT NULL,
       request_state VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
       priority INTEGER NOT NULL DEFAULT 5,
       product_definition_id VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       start_time TIMESTAMP,
       end_time TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_job_order (
       job_order_id UUID PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL UNIQUE,
       request_id VARCHAR(64) NOT NULL,
       work_master_id VARCHAR(64),
       work_master_version VARCHAR(16),
       segment_id VARCHAR(64),
       equipment_id VARCHAR(64) NOT NULL,
       dispatch_status VARCHAR(32) NOT NULL DEFAULT 'NOT_ALLOWED',
       command VARCHAR(32),
       priority INTEGER NOT NULL DEFAULT 5,
       planned_start TIMESTAMP,
       planned_end TIMESTAMP,
       actual_start TIMESTAMP,
       actual_end TIMESTAMP,
       original_job_no VARCHAR(64),
       replaced_by_job_no VARCHAR(64),
       replan_reason_code VARCHAR(64),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # Resource requirements snapshotted from segment specifications at dispatch
    """CREATE TABLE IF NOT EXISTS emc_job_order_material_req (
       job_no VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64),
       material_class_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16))""",
    """CREATE TABLE IF NOT EXISTS emc_job_order_equipment_req (
       job_no VARCHAR(64) NOT NULL,
       equipment_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_use VARCHAR(32),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_job_order_personnel_req (
       job_no VARCHAR(64) NOT NULL,
       personnel_class_id VARCHAR(64),
       person_id VARCHAR(64),
       personnel_use VARCHAR(32),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_job_order_audit (
       id UUID PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL,
       action VARCHAR(64) NOT NULL,
       detail VARCHAR(1024),
       actor VARCHAR(64),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # --- seeds: one accepted schedule with 3 job orders ---
    seed("emc_work_schedule", ["schedule_id", "external_ref", "schedule_state", "start_time", "end_time"],
         ["SCH-DEMO-001", "ERP-PO-1000456", "RELEASED", "!TIMESTAMP '2026-07-24 00:00:00'", "!TIMESTAMP '2026-07-25 00:00:00'"],
         "schedule_id = 'SCH-DEMO-001'"),
    seed("emc_work_request", ["request_id", "schedule_id", "request_state", "priority", "product_definition_id", "quantity", "uom", "start_time", "end_time"],
         ["WR-DEMO-001", "SCH-DEMO-001", "ACCEPTED", "1", "FG-UNIT-PACKED", "100", "pcs", "!TIMESTAMP '2026-07-24 06:00:00'", "!TIMESTAMP '2026-07-24 18:00:00'"],
         "request_id = 'WR-DEMO-001'"),
    # JO-DEMO-002 RUNNING on WU-A01 (assembly) with open response (seeded in M5)
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end, actual_start)
       SELECT 'a0000002-0000-0000-0000-000000000002', 'JO-DEMO-002', 'WR-DEMO-001', 'WM-ASSEMBLE', '1', 'SEG-ASSEMBLE', 'WU-A01', 'RUNNING', 'START', '1',
              TIMESTAMP '2026-07-24 06:00:00', TIMESTAMP '2026-07-24 08:00:00', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-002')""",
    # JO-DEMO-001 ALLOWED on WU-A02 (pack) - ready to start
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a0000001-0000-0000-0000-000000000001', 'JO-DEMO-001', 'WR-DEMO-001', 'WM-PACK', '1', 'SEG-PACK', 'WU-A02', 'ALLOWED', 'STORE', '1',
              TIMESTAMP '2026-07-24 08:00:00', TIMESTAMP '2026-07-24 10:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-001')""",
    # JO-DEMO-003 ALLOWED on WU-A02 (resource-conflict guard demo)
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a0000003-0000-0000-0000-000000000003', 'JO-DEMO-003', 'WR-DEMO-001', 'WM-PACK', '1', 'SEG-PACK', 'WU-A02', 'ALLOWED', 'STORE', '2',
              TIMESTAMP '2026-07-24 10:00:00', TIMESTAMP '2026-07-24 12:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-003')""",
    # Requirement snapshots for the running job order (from SEG-ASSEMBLE specs)
    """INSERT INTO emc_job_order_material_req (job_no, definition_id, material_class_id, material_use, quantity, uom)
       SELECT 'JO-DEMO-002', definition_id, material_class_id, material_use, quantity, uom FROM emc_segment_material_spec
       WHERE segment_id = 'SEG-ASSEMBLE' AND NOT EXISTS (SELECT 1 FROM emc_job_order_material_req WHERE job_no = 'JO-DEMO-002')""",
    """INSERT INTO emc_job_order_equipment_req (job_no, equipment_class_id, equipment_id, equipment_use, quantity)
       SELECT 'JO-DEMO-002', equipment_class_id, equipment_id, equipment_use, quantity FROM emc_segment_equipment_spec
       WHERE segment_id = 'SEG-ASSEMBLE' AND NOT EXISTS (SELECT 1 FROM emc_job_order_equipment_req WHERE job_no = 'JO-DEMO-002')""",
    """INSERT INTO emc_job_order_personnel_req (job_no, personnel_class_id, person_id, personnel_use, quantity)
       SELECT 'JO-DEMO-002', personnel_class_id, person_id, personnel_use, quantity FROM emc_segment_personnel_spec
       WHERE segment_id = 'SEG-ASSEMBLE' AND NOT EXISTS (SELECT 1 FROM emc_job_order_personnel_req WHERE job_no = 'JO-DEMO-002')""",
])

M5_WORK_PERFORMANCE = ";\n".join([
    # ISA-95 Part 4: Work Performance -> Job Response (+Actuals, Response Data)
    """CREATE TABLE IF NOT EXISTS emc_job_response (
       response_id UUID PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL,
       job_state VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
       actual_start TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       actual_end TIMESTAMP)""",
    # Response data: RUN/PAUSE intervals and collected parameters (Production Data Collection)
    """CREATE TABLE IF NOT EXISTS emc_job_response_data (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       data_kind VARCHAR(32) NOT NULL,
       param_key VARCHAR(64),
       param_value VARCHAR(256),
       uom VARCHAR(16),
       started_at TIMESTAMP,
       ended_at TIMESTAMP,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_material_actual (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       lot_id VARCHAR(64),
       sublot_id VARCHAR(64),
       definition_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_equipment_actual (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       equipment_id VARCHAR(64) NOT NULL,
       equipment_use VARCHAR(32),
       recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_personnel_actual (
       id UUID PRIMARY KEY,
       response_id UUID NOT NULL,
       person_id VARCHAR(64) NOT NULL,
       personnel_use VARCHAR(32),
       recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # Genealogy edges lot -> lot (Production Tracking)
    """CREATE TABLE IF NOT EXISTS emc_lot_genealogy (
       id UUID PRIMARY KEY,
       input_lot_id VARCHAR(64) NOT NULL,
       output_lot_id VARCHAR(64) NOT NULL,
       response_id UUID,
       quantity NUMERIC(14,3),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # --- seed: open response + RUN interval + equipment/personnel actuals for JO-DEMO-002 ---
    """INSERT INTO emc_job_response (response_id, job_no, job_state, actual_start)
       SELECT 'b0000002-0000-0000-0000-000000000002', 'JO-DEMO-002', 'RUNNING', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = 'JO-DEMO-002' AND job_state = 'RUNNING')""",
    """INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at)
       SELECT gen_random_uuid(), 'b0000002-0000-0000-0000-000000000002', 'RUN_INTERVAL', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data WHERE response_id = 'b0000002-0000-0000-0000-000000000002' AND data_kind = 'RUN_INTERVAL' AND ended_at IS NULL)""",
    """INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use)
       SELECT gen_random_uuid(), 'b0000002-0000-0000-0000-000000000002', 'WU-A01', 'PRIMARY'
       WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_actual WHERE response_id = 'b0000002-0000-0000-0000-000000000002')""",
    """INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use)
       SELECT gen_random_uuid(), 'b0000002-0000-0000-0000-000000000002', 'EMP-001', 'OPERATOR'
       WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_actual WHERE response_id = 'b0000002-0000-0000-0000-000000000002')""",
])

M6_INVENTORY = ";\n".join([
    # ISA-95 Part 3: Inventory Operations Management - canonical movement documents
    """CREATE TABLE IF NOT EXISTS emc_inventory_document (
       doc_id VARCHAR(64) PRIMARY KEY,
       kind VARCHAR(32) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
       external_doc_ref VARCHAR(128),
       integration_response_code VARCHAR(64),
       integration_response_message VARCHAR(512),
       operator_person_id VARCHAR(64),
       version_no INTEGER NOT NULL DEFAULT 1,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       submitted_at TIMESTAMP,
       completed_at TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_inventory_document_line (
       line_id UUID PRIMARY KEY,
       doc_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64),
       lot_id VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       source_location VARCHAR(64),
       dest_location VARCHAR(64))""",
])

M7_QUALITY = ";\n".join([
    # ISA-95 Part 3: Quality Operations Management
    """CREATE TABLE IF NOT EXISTS emc_defect_type (
       defect_type_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       category VARCHAR(32) DEFAULT 'QC')""",
    """CREATE TABLE IF NOT EXISTS emc_reason_code (
       reason_code VARCHAR(64) PRIMARY KEY,
       parent_code VARCHAR(64),
       description VARCHAR(256),
       default_defect_type_id VARCHAR(64))""",
    """CREATE TABLE IF NOT EXISTS emc_defect_record (
       defect_id UUID PRIMARY KEY,
       defect_no VARCHAR(64) NOT NULL UNIQUE,
       job_no VARCHAR(64) NOT NULL,
       lot_id VARCHAR(64),
       defect_type_id VARCHAR(64) NOT NULL,
       reason_code VARCHAR(64),
       severity VARCHAR(32) NOT NULL DEFAULT 'MINOR',
       qty_declared NUMERIC(14,3) NOT NULL DEFAULT 0,
       qty_confirmed NUMERIC(14,3),
       status VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
       created_by VARCHAR(64),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_defect_status_history (
       id UUID PRIMARY KEY,
       defect_no VARCHAR(64) NOT NULL,
       from_status VARCHAR(32),
       to_status VARCHAR(32) NOT NULL,
       actor VARCHAR(64),
       note VARCHAR(512),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_qa_test_result (
       id UUID PRIMARY KEY,
       job_no VARCHAR(64),
       lot_id VARCHAR(64),
       test_name VARCHAR(128) NOT NULL,
       result VARCHAR(16) NOT NULL,
       measurements_json VARCHAR(2048),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # --- seeds ---
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-VISUAL", "Visual defect", "QC"], "defect_type_id = 'DFT-VISUAL'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-DIMENSION", "Dimension out of tolerance", "QC"], "defect_type_id = 'DFT-DIMENSION'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-FUNCTIONAL", "Functional failure", "QC"], "defect_type_id = 'DFT-FUNCTIONAL'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-MATERIAL", None, "Material-caused", "DFT-VISUAL"], "reason_code = 'RC-MATERIAL'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-MACHINE", None, "Machine-caused", "DFT-DIMENSION"], "reason_code = 'RC-MACHINE'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-HUMAN", None, "Human error", "DFT-VISUAL"], "reason_code = 'RC-HUMAN'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-METHOD", None, "Method/process-caused", "DFT-FUNCTIONAL"], "reason_code = 'RC-METHOD'"),
])

M8_MAINTENANCE = ";\n".join([
    # ISA-95 Part 3: Maintenance Operations Management (lite)
    """CREATE TABLE IF NOT EXISTS emc_maintenance_request (
       request_id VARCHAR(64) PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       description VARCHAR(512),
       priority INTEGER NOT NULL DEFAULT 5,
       status VARCHAR(32) NOT NULL DEFAULT 'NEW',
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_maintenance_work_order (
       wo_id VARCHAR(64) PRIMARY KEY,
       request_id VARCHAR(64),
       equipment_id VARCHAR(64) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
       planned_start TIMESTAMP,
       planned_end TIMESTAMP,
       actual_start TIMESTAMP,
       actual_end TIMESTAMP,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
])


M9_EVENTS_CALENDAR = ";\n".join([
    # ISA-95 Part 2 (2018): Operations Event Definition/Event; Part 4: Work Calendar
    """CREATE TABLE IF NOT EXISTS emc_operations_event_definition (
       code VARCHAR(64) PRIMARY KEY,
       event_class VARCHAR(32) NOT NULL DEFAULT 'DOWNTIME',
       name VARCHAR(256) NOT NULL,
       requires_length BOOLEAN NOT NULL DEFAULT false,
       requires_time BOOLEAN NOT NULL DEFAULT false,
       requires_comment BOOLEAN NOT NULL DEFAULT false,
       oee_bucket VARCHAR(32) NOT NULL DEFAULT 'NONE',
       six_big_loss VARCHAR(64),
       sort_order INTEGER NOT NULL DEFAULT 100)""",
    """CREATE TABLE IF NOT EXISTS emc_operations_event (
       event_id UUID PRIMARY KEY,
       definition_code VARCHAR(64) NOT NULL,
       job_no VARCHAR(64),
       equipment_id VARCHAR(64),
       lot_id VARCHAR(64),
       length_m NUMERIC(14,3),
       time_min NUMERIC(10,1),
       comment_text VARCHAR(1024),
       status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       registered_by VARCHAR(64),
       started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       ended_at TIMESTAMP)""",
    # Raw L2 signals (buttons/PLC) - resolved into operations events
    """CREATE TABLE IF NOT EXISTS emc_machine_signal (
       signal_id UUID PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       signal_code VARCHAR(64) NOT NULL,
       is_auto BOOLEAN NOT NULL DEFAULT false,
       is_resolved BOOLEAN NOT NULL DEFAULT false,
       received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # Work Calendar (shifts)
    """CREATE TABLE IF NOT EXISTS emc_work_calendar (
       shift_id VARCHAR(64) PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       shift_label VARCHAR(64) NOT NULL,
       planned_minutes NUMERIC(10,1) NOT NULL DEFAULT 480,
       state VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       planned_start TIMESTAMP,
       actual_start TIMESTAMP,
       actual_end TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_shift_assignment (
       id UUID PRIMARY KEY,
       shift_id VARCHAR(64) NOT NULL,
       person_id VARCHAR(64) NOT NULL,
       handover_from_id VARCHAR(64),
       assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # --- seeds ---
    seed("emc_operations_event_definition", ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment", "oee_bucket", "six_big_loss", "sort_order"],
         ["SETUP", "SETUP", "Changeover / setup", "!false", "!true", "!false", "AVAILABILITY", "SETUP_ADJUSTMENT", "10"],
         "code = 'SETUP'"),
    seed("emc_operations_event_definition", ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment", "oee_bucket", "six_big_loss", "sort_order"],
         ["BREAKDOWN", "DOWNTIME", "Equipment breakdown", "!false", "!true", "!true", "AVAILABILITY", "BREAKDOWN", "20"],
         "code = 'BREAKDOWN'"),
    seed("emc_operations_event_definition", ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment", "oee_bucket", "six_big_loss", "sort_order"],
         ["NO_MATERIAL", "DOWNTIME", "No material at line", "!false", "!true", "!false", "AVAILABILITY", "IDLING", "30"],
         "code = 'NO_MATERIAL'"),
    seed("emc_operations_event_definition", ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment", "oee_bucket", "six_big_loss", "sort_order"],
         ["SPEED_LOSS", "OEE", "Reduced speed run", "!false", "!true", "!false", "PERFORMANCE", "REDUCED_SPEED", "40"],
         "code = 'SPEED_LOSS'"),
    seed("emc_operations_event_definition", ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment", "oee_bucket", "six_big_loss", "sort_order"],
         ["QC_HOLD", "QUALITY", "Quality hold", "!false", "!true", "!true", "AVAILABILITY", "BREAKDOWN", "50"],
         "code = 'QC_HOLD'"),
    seed("emc_work_calendar", ["shift_id", "equipment_id", "shift_label", "planned_minutes", "state", "planned_start", "actual_start"],
         ["SHIFT-DEMO-1", "WU-A01", "MORNING", "480", "OPEN", "!TIMESTAMP '2026-07-24 06:00:00'", "!CURRENT_TIMESTAMP"],
         "shift_id = 'SHIFT-DEMO-1'"),
    """INSERT INTO emc_shift_assignment (id, shift_id, person_id)
       SELECT gen_random_uuid(), 'SHIFT-DEMO-1', 'EMP-001'
       WHERE NOT EXISTS (SELECT 1 FROM emc_shift_assignment WHERE shift_id = 'SHIFT-DEMO-1' AND person_id = 'EMP-001')""",
])

M10_WORK_RECORD = ";\n".join([
    # ISA-95 Part 4 cl.15: Work Record (production dossier / job bag)
    """CREATE TABLE IF NOT EXISTS emc_work_record (
       record_id VARCHAR(64) PRIMARY KEY,
       job_no VARCHAR(64) NOT NULL UNIQUE,
       record_no VARCHAR(64) NOT NULL,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_work_record_section (
       record_id VARCHAR(64) NOT NULL,
       section_key VARCHAR(64) NOT NULL,
       title VARCHAR(256),
       content_json VARCHAR(8192),
       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    seed("emc_work_record", ["record_id", "job_no", "record_no"],
         ["WR-JO-DEMO-002", "JO-DEMO-002", "WREC-JO-DEMO-002"], "record_id = 'WR-JO-DEMO-002'"),
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-DEMO-002', 'params', 'Process parameters', '{"temperature":"210","pressure":"40"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-DEMO-002' AND section_key = 'params')""",
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-DEMO-002', 'checklist', 'Start checklist', '{"guardsClosed":"true","materialsStaged":"false"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-DEMO-002' AND section_key = 'checklist')""",
])

M11_INTEGRATION = ";\n".join([
    # ISA-95 Part 5: B2M transactions - verb x noun, idempotent, normalized ACK
    """CREATE TABLE IF NOT EXISTS emc_erp_outbox (
       id UUID PRIMARY KEY,
       verb VARCHAR(32) NOT NULL,
       noun VARCHAR(64) NOT NULL,
       object_id VARCHAR(128),
       payload_json VARCHAR(8192),
       idempotency_key VARCHAR(256) NOT NULL UNIQUE,
       status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
       ack_code VARCHAR(32),
       retry_count INTEGER NOT NULL DEFAULT 0,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_erp_inbox (
       id UUID PRIMARY KEY,
       verb VARCHAR(32) NOT NULL,
       noun VARCHAR(64) NOT NULL,
       payload_json VARCHAR(8192),
       idempotency_key VARCHAR(256) NOT NULL UNIQUE,
       status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
       received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       processed_at TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_integration_log (
       id UUID PRIMARY KEY,
       direction VARCHAR(16) NOT NULL,
       verb VARCHAR(32),
       noun VARCHAR(64),
       success BOOLEAN NOT NULL DEFAULT true,
       code VARCHAR(64),
       message VARCHAR(512),
       retryable BOOLEAN NOT NULL DEFAULT false,
       details_json VARCHAR(4096),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # Read replicas of ERP master data delivered via SYNC transactions
    """CREATE TABLE IF NOT EXISTS emc_master_data_replica (
       entity_type VARCHAR(64) NOT NULL,
       external_id VARCHAR(128) NOT NULL,
       payload_json VARCHAR(4096),
       synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
])

M12_OEE = ";\n".join([
    # ISO 22400 / Part 3 Performance Analysis: OEE per work unit per shift
    """CREATE TABLE IF NOT EXISTS emc_oee_shift (
       id UUID PRIMARY KEY,
       equipment_id VARCHAR(64) NOT NULL,
       shift_label VARCHAR(64) NOT NULL,
       planned_min NUMERIC(10,1) NOT NULL DEFAULT 480,
       availability_loss_min NUMERIC(10,1) NOT NULL DEFAULT 0,
       performance_loss_min NUMERIC(10,1) NOT NULL DEFAULT 0,
       produced_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       good_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       availability_pct NUMERIC(7,3),
       performance_pct NUMERIC(7,3),
       quality_pct NUMERIC(7,3),
       oee_pct NUMERIC(7,3),
       calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
])

# Multi-hop demo genealogy (RAW → WIP → FG) for bidirectional traceability screens.
# Re-entrant: WHERE NOT EXISTS on the same input/output pair.
M13_GENEALOGY_SEED = ";\n".join([
    """INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity)
       SELECT gen_random_uuid(), 'LOT-RAW-0001', 'LOT-WIP-0001', 120
       WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy
                         WHERE input_lot_id = 'LOT-RAW-0001' AND output_lot_id = 'LOT-WIP-0001')""",
    """INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity)
       SELECT gen_random_uuid(), 'LOT-RAW-0002', 'LOT-WIP-0001', 80
       WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy
                         WHERE input_lot_id = 'LOT-RAW-0002' AND output_lot_id = 'LOT-WIP-0001')""",
    """INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity)
       SELECT gen_random_uuid(), 'LOT-WIP-0001', 'LOT-FG-0001', 150
       WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy
                         WHERE input_lot_id = 'LOT-WIP-0001' AND output_lot_id = 'LOT-FG-0001')""",
])

# IEC 62264-2/3/4 extent: Physical Asset, Product Definition, Capability Test,
# Operations Capability/Performance, MOM 4x8 activity registry (Part 3).
def _mom_seed(domain, activity, status, note, link):
    return (
        "INSERT INTO emc_mom_activity (domain, activity, status, note, ui_link) "
        f"SELECT '{domain}', '{activity}', '{status}', '{note}', '{link}' "
        f"WHERE NOT EXISTS (SELECT 1 FROM emc_mom_activity WHERE domain = '{domain}' AND activity = '{activity}')"
    )


_MOM_ACTIVITIES = [
    ("PRODUCTION", "DEFINITION", "COVERED", "Process segment + work master", "emc-dispatch"),
    ("PRODUCTION", "RESOURCE", "COVERED", "Equipment / personnel / material", "emc-inventory"),
    ("PRODUCTION", "DETAILED_SCHEDULING", "COVERED", "Work schedule receive + domain schedule", "emc-dispatch"),
    ("PRODUCTION", "DISPATCHING", "COVERED", "Release / start / pause / resume", "emc-dispatch"),
    ("PRODUCTION", "EXECUTION", "COVERED", "Job order lifecycle", "emc-execution"),
    ("PRODUCTION", "DATA_COLLECTION", "COVERED", "PDC + material actuals", "emc-execution"),
    ("PRODUCTION", "TRACKING", "COVERED", "Lot genealogy tree", "emc-genealogy"),
    ("PRODUCTION", "PERFORMANCE_ANALYSIS", "COVERED", "OEE A×P×Q", "emc-oee"),
    ("QUALITY", "DEFINITION", "COVERED", "Defect types / reason codes", "emc-quality"),
    ("QUALITY", "RESOURCE", "COVERED", "QA personnel via person catalog", "emc-quality"),
    ("QUALITY", "DETAILED_SCHEDULING", "COVERED", "QA sample plan (domain schedule)", "emc-quality"),
    ("QUALITY", "DISPATCHING", "COVERED", "Defect workflow dispatch", "emc-quality"),
    ("QUALITY", "EXECUTION", "COVERED", "Confirm / reject / close defect", "emc-quality"),
    ("QUALITY", "DATA_COLLECTION", "COVERED", "QA test results", "emc-quality"),
    ("QUALITY", "TRACKING", "COVERED", "Defect status history", "emc-quality"),
    ("QUALITY", "PERFORMANCE_ANALYSIS", "COVERED", "Defect rate KPI", "emc-mom-matrix"),
    ("INVENTORY", "DEFINITION", "COVERED", "Inventory document kinds", "emc-inventory"),
    ("INVENTORY", "RESOURCE", "COVERED", "Storage zones + operational locations", "emc-inventory"),
    ("INVENTORY", "DETAILED_SCHEDULING", "COVERED", "Replenishment schedule", "emc-inventory"),
    ("INVENTORY", "DISPATCHING", "COVERED", "Submit inventory document", "emc-inventory"),
    ("INVENTORY", "EXECUTION", "COVERED", "Apply inventory document", "emc-inventory"),
    ("INVENTORY", "DATA_COLLECTION", "COVERED", "Document lines / stock qty", "emc-inventory"),
    ("INVENTORY", "TRACKING", "COVERED", "Stock + material movement", "emc-inventory"),
    ("INVENTORY", "PERFORMANCE_ANALYSIS", "COVERED", "Inventory turns KPI", "emc-mom-matrix"),
    ("MAINTENANCE", "DEFINITION", "COVERED", "Maintenance request model", "emc-oee"),
    ("MAINTENANCE", "RESOURCE", "COVERED", "Equipment as maint target", "emc-oee"),
    ("MAINTENANCE", "DETAILED_SCHEDULING", "COVERED", "PM calendar (domain schedule)", "emc-oee"),
    ("MAINTENANCE", "DISPATCHING", "COVERED", "Accept maintenance request", "emc-oee"),
    ("MAINTENANCE", "EXECUTION", "COVERED", "Complete work order", "emc-oee"),
    ("MAINTENANCE", "DATA_COLLECTION", "COVERED", "Event / downtime capture", "emc-oee"),
    ("MAINTENANCE", "TRACKING", "COVERED", "Maintenance list", "emc-oee"),
    ("MAINTENANCE", "PERFORMANCE_ANALYSIS", "COVERED", "MTTR / MTBF", "emc-mom-matrix"),
]

M14_PART234 = ";\n".join([
    """CREATE TABLE IF NOT EXISTS emc_physical_asset_class (
       class_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       parent_class_id VARCHAR(64))""",
    """CREATE TABLE IF NOT EXISTS emc_physical_asset (
       asset_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       serial_no VARCHAR(128),
       manufacturer VARCHAR(128),
       description VARCHAR(256),
       status VARCHAR(32) NOT NULL DEFAULT 'IN_SERVICE')""",
    """CREATE TABLE IF NOT EXISTS emc_physical_asset_property (
       asset_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (asset_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_product_definition (
       product_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       fg_definition_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE')""",
    """CREATE TABLE IF NOT EXISTS emc_product_segment (
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       sequence_no INTEGER NOT NULL DEFAULT 1,
       PRIMARY KEY (product_id, segment_id))""",
    """CREATE TABLE IF NOT EXISTS emc_capability_test_spec (
       spec_id VARCHAR(64) PRIMARY KEY,
       target_kind VARCHAR(32) NOT NULL,
       target_id VARCHAR(64) NOT NULL,
       test_name VARCHAR(128) NOT NULL,
       criterion VARCHAR(256),
       uom VARCHAR(16))""",
    """CREATE TABLE IF NOT EXISTS emc_capability_test_result (
       result_id UUID PRIMARY KEY,
       spec_id VARCHAR(64) NOT NULL,
       measured_value VARCHAR(128),
       result VARCHAR(32) NOT NULL,
       tested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       tested_by VARCHAR(64))""",
    """CREATE TABLE IF NOT EXISTS emc_operations_capability (
       capability_id VARCHAR(64) PRIMARY KEY,
       operations_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
       equipment_id VARCHAR(64),
       segment_id VARCHAR(64),
       reason VARCHAR(256),
       available_from TIMESTAMP,
       available_to TIMESTAMP,
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE')""",
    """CREATE TABLE IF NOT EXISTS emc_operations_performance (
       performance_id VARCHAR(64) PRIMARY KEY,
       operations_type VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION',
       equipment_id VARCHAR(64),
       shift_id VARCHAR(64),
       good_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       reject_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       run_min NUMERIC(14,3) NOT NULL DEFAULT 0,
       downtime_min NUMERIC(14,3) NOT NULL DEFAULT 0,
       note VARCHAR(256),
       calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_mom_activity (
       domain VARCHAR(32) NOT NULL,
       activity VARCHAR(32) NOT NULL,
       status VARCHAR(16) NOT NULL,
       note VARCHAR(256),
       ui_link VARCHAR(64),
       PRIMARY KEY (domain, activity))""",
    seed("emc_physical_asset_class", ["class_id", "description", "parent_class_id"],
         ["PAC-MACHINE", "Production machines", None], "class_id = 'PAC-MACHINE'"),
    seed("emc_physical_asset", ["asset_id", "class_id", "equipment_id", "serial_no", "manufacturer", "description", "status"],
         ["AST-A01", "PAC-MACHINE", "WU-A01", "SN-A01-001", "DemoOEM", "Assembly cell asset", "IN_SERVICE"],
         "asset_id = 'AST-A01'"),
    seed("emc_physical_asset", ["asset_id", "class_id", "equipment_id", "serial_no", "manufacturer", "description", "status"],
         ["AST-A02", "PAC-MACHINE", "WU-A02", "SN-A02-001", "DemoOEM", "Packing cell asset", "IN_SERVICE"],
         "asset_id = 'AST-A02'"),
    seed("emc_product_definition", ["product_id", "description", "fg_definition_id", "status"],
         ["PD-UNIT-PACKED", "Packed finished unit", "FG-UNIT-PACKED", "ACTIVE"],
         "product_id = 'PD-UNIT-PACKED'"),
    seed("emc_product_segment", ["product_id", "segment_id", "sequence_no"],
         ["PD-UNIT-PACKED", "SEG-ASSEMBLE", "1"],
         "product_id = 'PD-UNIT-PACKED' AND segment_id = 'SEG-ASSEMBLE'"),
    seed("emc_product_segment", ["product_id", "segment_id", "sequence_no"],
         ["PD-UNIT-PACKED", "SEG-PACK", "2"],
         "product_id = 'PD-UNIT-PACKED' AND segment_id = 'SEG-PACK'"),
    seed("emc_capability_test_spec", ["spec_id", "target_kind", "target_id", "test_name", "criterion", "uom"],
         ["CTS-WU-A01-SPEED", "EQUIPMENT", "WU-A01", "Rated speed check", ">= 80", "pcs/h"],
         "spec_id = 'CTS-WU-A01-SPEED'"),
    """INSERT INTO emc_capability_test_result (result_id, spec_id, measured_value, result, tested_by)
       SELECT gen_random_uuid(), 'CTS-WU-A01-SPEED', '95', 'PASS', 'EMP-001'
       WHERE NOT EXISTS (SELECT 1 FROM emc_capability_test_result WHERE spec_id = 'CTS-WU-A01-SPEED')""",
    seed("emc_operations_capability",
         ["capability_id", "operations_type", "equipment_id", "segment_id", "reason", "status"],
         ["CAP-WU-A01-ASSEMBLE", "PRODUCTION", "WU-A01", "SEG-ASSEMBLE", "Qualified + capability test PASS", "AVAILABLE"],
         "capability_id = 'CAP-WU-A01-ASSEMBLE'"),
    seed("emc_operations_capability",
         ["capability_id", "operations_type", "equipment_id", "segment_id", "reason", "status"],
         ["CAP-WU-A02-PACK", "PRODUCTION", "WU-A02", "SEG-PACK", "Qualified packing cell", "AVAILABLE"],
         "capability_id = 'CAP-WU-A02-PACK'"),
] + [_mom_seed(*row) for row in _MOM_ACTIVITIES])


def _mom_update(domain, activity, status, note, link):
    return (
        f"UPDATE emc_mom_activity SET status = '{status}', note = '{note}', ui_link = '{link}' "
        f"WHERE domain = '{domain}' AND activity = '{activity}'"
    )


# Close remaining Part 3 ○/◐ cells + Operational Location (Part 2).
M15_PART3_COMPLETE = ";\n".join([
    """CREATE TABLE IF NOT EXISTS emc_operational_location (
       location_id VARCHAR(64) PRIMARY KEY,
       description VARCHAR(256),
       location_kind VARCHAR(32) NOT NULL DEFAULT 'STORAGE',
       equipment_id VARCHAR(64),
       parent_location_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE')""",
    """CREATE TABLE IF NOT EXISTS emc_domain_schedule (
       schedule_id VARCHAR(64) PRIMARY KEY,
       domain VARCHAR(32) NOT NULL,
       schedule_kind VARCHAR(64) NOT NULL,
       target_id VARCHAR(64),
       planned_start TIMESTAMP,
       planned_end TIMESTAMP,
       quantity NUMERIC(14,3),
       uom VARCHAR(16),
       status VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
       note VARCHAR(256),
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    seed("emc_operational_location",
         ["location_id", "description", "location_kind", "equipment_id", "parent_location_id", "status"],
         ["LOC-WH-RAW", "Raw material warehouse", "STORAGE", "WH-CENTRAL", None, "ACTIVE"],
         "location_id = 'LOC-WH-RAW'"),
    seed("emc_operational_location",
         ["location_id", "description", "location_kind", "equipment_id", "parent_location_id", "status"],
         ["LOC-WH-FG", "Finished goods warehouse", "STORAGE", "WH-CENTRAL", None, "ACTIVE"],
         "location_id = 'LOC-WH-FG'"),
    seed("emc_operational_location",
         ["location_id", "description", "location_kind", "equipment_id", "parent_location_id", "status"],
         ["LOC-LINE-A01", "Line A01 staging", "STAGING", "WU-A01", "LOC-WH-RAW", "ACTIVE"],
         "location_id = 'LOC-LINE-A01'"),
    seed("emc_domain_schedule",
         ["schedule_id", "domain", "schedule_kind", "target_id", "quantity", "uom", "status", "note"],
         ["DS-QA-SAMPLE-001", "QUALITY", "SAMPLE_PLAN", "LOT-FG-0001", "5", "pcs", "PLANNED",
          "Incoming inspection sample plan"],
         "schedule_id = 'DS-QA-SAMPLE-001'"),
    seed("emc_domain_schedule",
         ["schedule_id", "domain", "schedule_kind", "target_id", "quantity", "uom", "status", "note"],
         ["DS-INV-REPL-001", "INVENTORY", "REPLENISHMENT", "RAW-PLASTIC-GRANULE", "100", "kg", "PLANNED",
          "Min/max replenishment for plastic granulate"],
         "schedule_id = 'DS-INV-REPL-001'"),
    seed("emc_domain_schedule",
         ["schedule_id", "domain", "schedule_kind", "target_id", "quantity", "uom", "status", "note"],
         ["DS-PM-A01-001", "MAINTENANCE", "PM_CALENDAR", "WU-A01", "1", "job", "PLANNED",
          "Monthly PM for assembly cell"],
         "schedule_id = 'DS-PM-A01-001'"),
    seed("emc_domain_schedule",
         ["schedule_id", "domain", "schedule_kind", "target_id", "quantity", "uom", "status", "note"],
         ["DS-PROD-FIRM-001", "PRODUCTION", "FIRM_SCHEDULE", "SCH-DEMO-001", "1", "schedule", "RELEASED",
          "Link to work schedule SCH-DEMO-001"],
         "schedule_id = 'DS-PROD-FIRM-001'"),
] + [_mom_update(*row) for row in _MOM_ACTIVITIES]
  + [_mom_seed(*row) for row in _MOM_ACTIVITIES])

MIGRATIONS = [
    {"id": "emc_m1_equipment_personnel", "sql": M1_EQUIPMENT_PERSONNEL},
    {"id": "emc_m2_material", "sql": M2_MATERIAL},
    {"id": "emc_m3_segment_workdef", "sql": M3_SEGMENT_WORKDEF},
    {"id": "emc_m4_work_schedule", "sql": M4_WORK_SCHEDULE},
    {"id": "emc_m5_work_performance", "sql": M5_WORK_PERFORMANCE},
    {"id": "emc_m6_inventory", "sql": M6_INVENTORY},
    {"id": "emc_m7_quality", "sql": M7_QUALITY},
    {"id": "emc_m8_maintenance", "sql": M8_MAINTENANCE},
    {"id": "emc_m9_events_calendar", "sql": M9_EVENTS_CALENDAR},
    {"id": "emc_m10_work_record", "sql": M10_WORK_RECORD},
    {"id": "emc_m11_integration", "sql": M11_INTEGRATION},
    {"id": "emc_m12_oee", "sql": M12_OEE},
    {"id": "emc_m13_genealogy_seed", "sql": M13_GENEALOGY_SEED},
    {"id": "emc_m14_part234", "sql": M14_PART234},
    {"id": "emc_m15_part3_complete", "sql": M15_PART3_COMPLETE},
    {"id": "emc_m16_uml_part2", "sql": M16_UML_PART2},
    {"id": "emc_m17_uml_part4", "sql": M17_UML_PART4},
    {"id": "emc_m18_part5_kpi", "sql": M18_PART5_KPI},
    {"id": "emc_m19_gost_gaps", "sql": M19_GOST_GAPS},
    {"id": "emc_m20_demo_rich", "sql": M20_DEMO_RICH},
]


# ---------------------------------------------------------------------------
# BFF functions (ISA-95 Part 3 activity grid). All on the singleton hub.
# ---------------------------------------------------------------------------

FUNCTIONS = []

# --- Production: Definition Management / Resource Management ----------------

FUNCTIONS.append(fn(
    "emc_segment_list",
    [],
    OUT(RL("rows", [F("segmentId"), F("name"), F("operationsType"), F("parentId"),
                    F("materialSpecs"), F("equipmentSpecs")])),
    [
        selN("segments",
             "SELECT s.segment_id, s.name, s.operations_type, COALESCE(s.parent_id, '') AS parent_id, "
             "(SELECT COUNT(*) FROM emc_segment_material_spec ms WHERE ms.segment_id = s.segment_id) AS material_specs, "
             "(SELECT COUNT(*) FROM emc_segment_equipment_spec es WHERE es.segment_id = s.segment_id) AS equipment_specs "
             "FROM emc_process_segment s ORDER BY s.segment_id"),
        map_rows("rows", "${segments}", {
            "segmentId": "${item.segment_id}", "name": "${item.name}",
            "operationsType": "${item.operations_type}", "parentId": "${item.parent_id}",
            "materialSpecs": "${item.material_specs}", "equipmentSpecs": "${item.equipment_specs}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_workmaster_upsert",
    [F("workMasterId"), F("version"), F("segmentId"), F("durationMin"), F("description")],
    OUT(F("workMasterId"), F("version")),
    [
        sel1("seg", "SELECT segment_id FROM emc_process_segment WHERE segment_id = ?", ["${input.segmentId}"]),
        fail_null("seg", "SEGMENT_NOT_FOUND", "Process segment not found"),
        ex("UPDATE emc_work_master SET segment_id = ?, duration_min = NULLIF(?, ''), description = ? "
           "WHERE work_master_id = ? AND version = ?",
           ["${input.segmentId}", "${input.durationMin}", "${input.description}", "${input.workMasterId}", "${input.version}"]),
        ex("INSERT INTO emc_work_master (work_master_id, version, segment_id, duration_min, description) "
           "SELECT ?, ?, ?, NULLIF(?, ''), ? WHERE NOT EXISTS "
           "(SELECT 1 FROM emc_work_master WHERE work_master_id = ? AND version = ?)",
           ["${input.workMasterId}", "${input.version}", "${input.segmentId}", "${input.durationMin}",
            "${input.description}", "${input.workMasterId}", "${input.version}"]),
        ret({"error_code": "OK", "error_message": "",
             "workMasterId": "${input.workMasterId}", "version": "${input.version}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_workmaster_list",
    [],
    OUT(RL("rows", [F("workMasterId"), F("version"), F("segmentId"), F("segmentName"),
                    F("durationMin"), F("description")])),
    [
        selN("masters",
             "SELECT wm.work_master_id, wm.version, wm.segment_id, s.name AS segment_name, "
             "wm.duration_min, COALESCE(wm.description, '') AS description "
             "FROM emc_work_master wm LEFT JOIN emc_process_segment s ON s.segment_id = wm.segment_id "
             "ORDER BY wm.work_master_id, wm.version"),
        map_rows("rows", "${masters}", {
            "workMasterId": "${item.work_master_id}", "version": "${item.version}",
            "segmentId": "${item.segment_id}", "segmentName": "${item.segment_name}",
            "durationMin": "${item.duration_min}", "description": "${item.description}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_equipment_list",
    [F("equipmentLevel")],
    OUT(RL("rows", [F("equipmentId"), F("classId"), F("equipmentLevel"), F("parentId"),
                    F("hierarchyPath"), F("description")])),
    [
        selN("equipment",
             "SELECT equipment_id, COALESCE(class_id, '') AS class_id, equipment_level, "
             "COALESCE(parent_id, '') AS parent_id, COALESCE(hierarchy_path, '') AS hierarchy_path, "
             "COALESCE(description, '') AS description FROM emc_equipment "
             "WHERE (? = '' OR equipment_level = ?) ORDER BY hierarchy_path",
             ["${input.equipmentLevel}", "${input.equipmentLevel}"]),
        map_rows("rows", "${equipment}", {
            "equipmentId": "${item.equipment_id}", "classId": "${item.class_id}",
            "equipmentLevel": "${item.equipment_level}", "parentId": "${item.parent_id}",
            "hierarchyPath": "${item.hierarchy_path}", "description": "${item.description}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_person_list",
    [],
    OUT(RL("rows", [F("personId"), F("personName"), F("personnelClassId"), F("classDescription")])),
    [
        selN("persons",
             "SELECT p.person_id, p.person_name, COALESCE(p.personnel_class_id, '') AS personnel_class_id, "
             "COALESCE(c.description, '') AS class_description FROM emc_person p "
             "LEFT JOIN emc_personnel_class c ON c.class_id = p.personnel_class_id ORDER BY p.person_id"),
        map_rows("rows", "${persons}", {
            "personId": "${item.person_id}", "personName": "${item.person_name}",
            "personnelClassId": "${item.personnel_class_id}", "classDescription": "${item.class_description}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# --- Production: Detailed Scheduling -----------------------------------------

FUNCTIONS.append(fn(
    "emc_schedule_receive",
    [F("externalRef"), F("requestId"), F("jobNo"), F("workMasterId"), F("workMasterVersion"),
     F("equipmentId"), F("productDefinitionId"), F("quantity"), F("uom"), F("priority"),
     F("plannedStart"), F("plannedEnd")],
    OUT(F("scheduleId"), F("requestId"), F("jobNo"), F("dispatchStatus")),
    [
        fail_null("input.externalRef", "VALIDATION", "externalRef is required"),
        fail_null("input.requestId", "VALIDATION", "requestId is required"),
        fail_null("input.jobNo", "VALIDATION", "jobNo is required"),
        fail_null("input.workMasterId", "VALIDATION", "workMasterId is required"),
        fail_null("input.equipmentId", "VALIDATION", "equipmentId is required"),
        sel1("wm", "SELECT work_master_id, version, segment_id FROM emc_work_master "
                   "WHERE work_master_id = ? AND version = ?",
             ["${input.workMasterId}", "${input.workMasterVersion}"]),
        fail_null("wm", "WORK_MASTER_NOT_FOUND", "Work master not found"),
        sel1("eq", "SELECT equipment_id FROM emc_equipment WHERE equipment_id = ?", ["${input.equipmentId}"]),
        fail_null("eq", "EQUIPMENT_NOT_FOUND", "Equipment not found"),
        ex("INSERT INTO emc_work_schedule (schedule_id, external_ref, schedule_state) "
           "SELECT ?, ?, 'RELEASED' WHERE NOT EXISTS (SELECT 1 FROM emc_work_schedule WHERE schedule_id = ?)",
           ["${input.externalRef}", "${input.externalRef}", "${input.externalRef}"]),
        ex("INSERT INTO emc_work_request (request_id, schedule_id, request_state, priority, product_definition_id, quantity, uom, start_time, end_time) "
           "SELECT ?, ?, 'ACCEPTED', COALESCE(NULLIF(?, ''), '5'), ?, COALESCE(NULLIF(?, ''), '0'), ?, NULLIF(?, ''), NULLIF(?, '') "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_work_request WHERE request_id = ?)",
           ["${input.requestId}", "${input.externalRef}", "${input.priority}", "${input.productDefinitionId}",
            "${input.quantity}", "${input.uom}", "${input.plannedStart}", "${input.plannedEnd}", "${input.requestId}"]),
        ex("INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, "
           "equipment_id, dispatch_status, command, priority, planned_start, planned_end) "
           "SELECT gen_random_uuid(), ?, ?, ?, ?, ?, ?, 'NOT_ALLOWED', 'STORE', COALESCE(NULLIF(?, ''), '5'), NULLIF(?, ''), NULLIF(?, '') "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = ?)",
           ["${input.jobNo}", "${input.requestId}", "${input.workMasterId}", "${input.workMasterVersion}",
            "${wm.segment_id}", "${input.equipmentId}", "${input.priority}", "${input.plannedStart}",
            "${input.plannedEnd}", "${input.jobNo}"]),
        # snapshot resource requirements from segment specifications (Part 4)
        ex("INSERT INTO emc_job_order_material_req (job_no, definition_id, material_class_id, material_use, quantity, uom) "
           "SELECT ?, definition_id, material_class_id, material_use, quantity, uom FROM emc_segment_material_spec "
           "WHERE segment_id = ? AND NOT EXISTS (SELECT 1 FROM emc_job_order_material_req WHERE job_no = ?)",
           ["${input.jobNo}", "${wm.segment_id}", "${input.jobNo}"]),
        ex("INSERT INTO emc_job_order_equipment_req (job_no, equipment_class_id, equipment_id, equipment_use, quantity) "
           "SELECT ?, equipment_class_id, equipment_id, equipment_use, quantity FROM emc_segment_equipment_spec "
           "WHERE segment_id = ? AND NOT EXISTS (SELECT 1 FROM emc_job_order_equipment_req WHERE job_no = ?)",
           ["${input.jobNo}", "${wm.segment_id}", "${input.jobNo}"]),
        ex("INSERT INTO emc_job_order_personnel_req (job_no, personnel_class_id, person_id, personnel_use, quantity) "
           "SELECT ?, personnel_class_id, person_id, personnel_use, quantity FROM emc_segment_personnel_spec "
           "WHERE segment_id = ? AND NOT EXISTS (SELECT 1 FROM emc_job_order_personnel_req WHERE job_no = ?)",
           ["${input.jobNo}", "${wm.segment_id}", "${input.jobNo}"]),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) VALUES (gen_random_uuid(), ?, 'RECEIVED', ?, 'erp')",
           ["${input.jobNo}", "${input.externalRef}"]),
        sel1("job", "SELECT job_no, dispatch_status FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        ret({"error_code": "OK", "error_message": "", "scheduleId": "${input.externalRef}",
             "requestId": "${input.requestId}", "jobNo": "${job.job_no}",
             "dispatchStatus": "${job.dispatch_status}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_schedule_list",
    [],
    OUT(RL("rows", [F("scheduleId"), F("externalRef"), F("scheduleState"), F("requests"), F("jobOrders")])),
    [
        selN("schedules",
             "SELECT s.schedule_id, COALESCE(s.external_ref, '') AS external_ref, s.schedule_state, "
             "(SELECT COUNT(*) FROM emc_work_request r WHERE r.schedule_id = s.schedule_id) AS requests, "
             "(SELECT COUNT(*) FROM emc_job_order j JOIN emc_work_request r2 ON r2.request_id = j.request_id "
             " WHERE r2.schedule_id = s.schedule_id) AS job_orders "
             "FROM emc_work_schedule s ORDER BY s.created_at DESC"),
        map_rows("rows", "${schedules}", {
            "scheduleId": "${item.schedule_id}", "externalRef": "${item.external_ref}",
            "scheduleState": "${item.schedule_state}", "requests": "${item.requests}",
            "jobOrders": "${item.job_orders}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# --- Production: Dispatching ---------------------------------------------------

FUNCTIONS.append(fn(
    "emc_joborder_listBoard",
    [F("equipmentId")],
    OUT(RL("rows", [F("jobNo"), F("dispatchStatus"), F("priority"), F("equipmentId"), F("segmentId"),
                    F("productDefinitionId"), F("quantity"), F("uom"), F("plannedStart")])),
    [
        selN("jobs",
             "SELECT jo.job_no, jo.dispatch_status, jo.priority, jo.equipment_id, COALESCE(jo.segment_id, '') AS segment_id, "
             "COALESCE(wr.product_definition_id, '') AS product_definition_id, wr.quantity, "
             "COALESCE(wr.uom, '') AS uom, jo.planned_start "
             "FROM emc_job_order jo JOIN emc_work_request wr ON wr.request_id = jo.request_id "
             "WHERE (? = '' OR jo.equipment_id = ?) AND jo.dispatch_status NOT IN ('ENDED', 'ABORTED', 'CANCELLED') "
             "ORDER BY jo.priority, jo.planned_start",
             ["${input.equipmentId}", "${input.equipmentId}"]),
        map_rows("rows", "${jobs}", {
            "jobNo": "${item.job_no}", "dispatchStatus": "${item.dispatch_status}", "priority": "${item.priority}",
            "equipmentId": "${item.equipment_id}", "segmentId": "${item.segment_id}",
            "productDefinitionId": "${item.product_definition_id}", "quantity": "${item.quantity}",
            "uom": "${item.uom}", "plannedStart": "${item.planned_start}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_release",
    [F("jobNo")],
    OUT(F("jobNo"), F("dispatchStatus")),
    [
        sel1("job", "SELECT job_no, dispatch_status FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "NOT_ALLOWED", "INVALID_STATE", "Only NOT_ALLOWED job orders can be released"),
        ex("UPDATE emc_job_order SET dispatch_status = 'ALLOWED', command = 'STORE' WHERE job_no = ?", ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, actor) VALUES (gen_random_uuid(), ?, 'RELEASED', 'dispatcher')",
           ["${input.jobNo}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${input.jobNo}", "dispatchStatus": "ALLOWED"}),
    ],
))

# --- Production: Execution Management ------------------------------------------

def machine_write(equipment_id, object_path, status, job_no_ref):
    """writeVariable steps for a seeded work unit (literal paths only)."""
    return [
        when({"var": "job.equipment_id", "equals": equipment_id}, [
            write_var(object_path, "status", {"value": status}),
            write_var(object_path, "activeJobOrderId", {"value": job_no_ref}),
        ]),
    ]


FUNCTIONS.append(fn(
    "emc_joborder_start",
    [F("jobNo"), F("personId")],
    OUT(F("jobNo"), F("status"), F("dispatchStatus"), F("responseId")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id, segment_id FROM emc_job_order WHERE job_no = ?",
             ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "ALLOWED", "INVALID_STATE", "Job order must be ALLOWED to start"),
        sel1("conflict", "SELECT job_no FROM emc_job_order WHERE equipment_id = ? AND dispatch_status = 'RUNNING' "
                         "AND job_no != ? ORDER BY job_no LIMIT 1",
             ["${job.equipment_id}", "${input.jobNo}"]),
        when({"var": "conflict.job_no", "notNull": True}, [
            ret({"error_code": "RESOURCE_CONFLICT",
                 "error_message": "Equipment already has a RUNNING job order: ${conflict.job_no}",
                 "jobNo": "${input.jobNo}", "status": "", "dispatchStatus": "", "responseId": ""}),
        ]),
        # APS-lite: require AVAILABLE operations capability window for equipment
        sel1("cap",
             "SELECT c.capability_id FROM emc_operations_capability c "
             "JOIN emc_ops_capability_equipment ce ON ce.capability_id = c.capability_id "
             "WHERE ce.equipment_id = ? AND c.status = 'AVAILABLE' "
             "AND (c.available_from IS NULL OR c.available_from <= CURRENT_TIMESTAMP) "
             "AND (c.available_to IS NULL OR c.available_to >= CURRENT_TIMESTAMP) "
             "ORDER BY c.capability_id LIMIT 1",
             ["${job.equipment_id}"]),
        when({"var": "cap.capability_id", "notNull": False}, [
            ret({"error_code": "CAPABILITY_WINDOW",
                 "error_message": "No AVAILABLE operations capability window for equipment",
                 "jobNo": "${input.jobNo}", "status": "", "dispatchStatus": "", "responseId": ""}),
        ]),
        ex("INSERT INTO emc_job_response (response_id, job_no, job_state) SELECT gen_random_uuid(), ?, 'RUNNING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING')",
           ["${input.jobNo}", "${input.jobNo}"]),
        ex("UPDATE emc_job_order SET dispatch_status = 'RUNNING', command = 'START', actual_start = CURRENT_TIMESTAMP "
           "WHERE job_no = ?", ["${input.jobNo}"]),
        sel1("resp", "SELECT response_id FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING'",
             ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at) "
           "SELECT gen_random_uuid(), ?, 'RUN_INTERVAL', CURRENT_TIMESTAMP "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data d WHERE d.response_id = ? AND d.ended_at IS NULL)",
           ["${resp.response_id}", "${resp.response_id}"]),
        ex("INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use) "
           "SELECT gen_random_uuid(), ?, ?, 'PRIMARY' WHERE NOT EXISTS "
           "(SELECT 1 FROM emc_equipment_actual WHERE response_id = ?)",
           ["${resp.response_id}", "${job.equipment_id}", "${resp.response_id}"]),
        when({"var": "input.personId", "notNull": True}, [
            ex("INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use) "
               "SELECT gen_random_uuid(), ?, ?, 'OPERATOR' WHERE NOT EXISTS "
               "(SELECT 1 FROM emc_personnel_actual WHERE response_id = ? AND person_id = ?)",
               ["${resp.response_id}", "${input.personId}", "${resp.response_id}", "${input.personId}"]),
        ]),
        # Part 5: PROCESS Operations Event "work commenced" to ERP (idempotent)
        ex("INSERT INTO emc_erp_outbox (id, verb, noun, object_id, payload_json, idempotency_key, status) "
           "SELECT gen_random_uuid(), 'PROCESS', 'OPERATIONS_EVENT', ?, "
           "CONCAT('{\"event\":\"work commenced\",\"jobNo\":\"', ?, '\"}'), CONCAT('WO-COMMENCED:', ?), 'PENDING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_erp_outbox WHERE idempotency_key = CONCAT('WO-COMMENCED:', ?))",
           ["${input.jobNo}", "${input.jobNo}", "${input.jobNo}", "${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "RUNNING", "${job.job_no}"),
        *machine_write("WU-A02", WU_A02, "RUNNING", "${job.job_no}"),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, actor) VALUES (gen_random_uuid(), ?, 'STARTED', 'operator')",
           ["${input.jobNo}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}",
             "status": "RUNNING", "dispatchStatus": "RUNNING", "responseId": "${resp.response_id}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_pause",
    [F("jobNo")],
    OUT(F("jobNo"), F("status"), F("dispatchStatus")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "RUNNING", "INVALID_STATE", "Only RUNNING job orders can be paused"),
        ex("UPDATE emc_job_order SET dispatch_status = 'SUSPENDED', command = 'PAUSE' WHERE job_no = ?", ["${input.jobNo}"]),
        ex("UPDATE emc_job_response_data SET ended_at = CURRENT_TIMESTAMP WHERE ended_at IS NULL "
           "AND response_id IN (SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at) "
           "SELECT gen_random_uuid(), response_id, 'PAUSE_INTERVAL', CURRENT_TIMESTAMP FROM emc_job_response "
           "WHERE job_no = ? AND job_state = 'RUNNING'", ["${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "PAUSED", "${job.job_no}"),
        *machine_write("WU-A02", WU_A02, "PAUSED", "${job.job_no}"),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}",
             "status": "SUSPENDED", "dispatchStatus": "SUSPENDED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_resume",
    [F("jobNo")],
    OUT(F("jobNo"), F("status"), F("dispatchStatus")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "SUSPENDED", "INVALID_STATE", "Only SUSPENDED job orders can be resumed"),
        ex("UPDATE emc_job_order SET dispatch_status = 'RUNNING', command = 'RESUME' WHERE job_no = ?", ["${input.jobNo}"]),
        ex("UPDATE emc_job_response_data SET ended_at = CURRENT_TIMESTAMP WHERE ended_at IS NULL "
           "AND response_id IN (SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at) "
           "SELECT gen_random_uuid(), response_id, 'RUN_INTERVAL', CURRENT_TIMESTAMP FROM emc_job_response "
           "WHERE job_no = ? AND job_state = 'RUNNING'", ["${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "RUNNING", "${job.job_no}"),
        *machine_write("WU-A02", WU_A02, "RUNNING", "${job.job_no}"),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}",
             "status": "RUNNING", "dispatchStatus": "RUNNING"}),
    ],
))
FUNCTIONS.append(fn(
    "emc_joborder_complete",
    [F("jobNo")],
    OUT(F("jobNo"), F("status")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "RUNNING", "INVALID_STATE", "Only RUNNING job orders can be completed"),
        # QC gate: no confirmed-open defects
        sel1("open_defects", "SELECT COUNT(*) AS cnt FROM emc_defect_record WHERE job_no = ? AND status = 'CONFIRMED'",
             ["${input.jobNo}"]),
        when({"var": "open_defects.cnt", "gt": "0"}, [
            ret({"error_code": "QC_GATE_BLOCKED",
                 "error_message": "Confirmed defects must be closed before completion",
                 "jobNo": "${input.jobNo}", "status": ""}),
        ]),
        # at least one produced material actual (Part 4 Material Actual)
        sel1("produced", "SELECT COALESCE(SUM(quantity), 0) AS qty FROM emc_material_actual "
                         "WHERE material_use = 'PRODUCED' AND response_id IN "
                         "(SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        when({"var": "produced.qty", "lte": "0"}, [
            ret({"error_code": "NO_OUTPUT_REGISTERED",
                 "error_message": "No produced material registered for this job order",
                 "jobNo": "${input.jobNo}", "status": ""}),
        ]),
        ex("UPDATE emc_job_response_data SET ended_at = CURRENT_TIMESTAMP WHERE ended_at IS NULL "
           "AND response_id IN (SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        ex("UPDATE emc_job_response SET job_state = 'ENDED', actual_end = CURRENT_TIMESTAMP "
           "WHERE job_no = ? AND job_state = 'RUNNING'", ["${input.jobNo}"]),
        ex("UPDATE emc_job_order SET dispatch_status = 'ENDED', command = 'STOP', actual_end = CURRENT_TIMESTAMP "
           "WHERE job_no = ?", ["${input.jobNo}"]),
        # Part 5: PROCESS Operations Performance to ERP (idempotent)
        ex("INSERT INTO emc_erp_outbox (id, verb, noun, object_id, payload_json, idempotency_key, status) "
           "SELECT gen_random_uuid(), 'PROCESS', 'OPERATIONS_PERFORMANCE', ?, "
           "CONCAT('{\"jobNo\":\"', ?, '\",\"event\":\"work completed\"}'), CONCAT('WO-COMPLETED:', ?), 'PENDING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_erp_outbox WHERE idempotency_key = CONCAT('WO-COMPLETED:', ?))",
           ["${input.jobNo}", "${input.jobNo}", "${input.jobNo}", "${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "IDLE", ""),
        *machine_write("WU-A02", WU_A02, "IDLE", ""),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, actor) VALUES (gen_random_uuid(), ?, 'COMPLETED', 'operator')",
           ["${input.jobNo}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}", "status": "ENDED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_abort",
    [F("jobNo"), F("reason")],
    OUT(F("jobNo"), F("status")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        ex("UPDATE emc_job_order SET dispatch_status = 'ABORTED', command = 'ABORT', actual_end = CURRENT_TIMESTAMP "
           "WHERE job_no = ? AND dispatch_status IN ('RUNNING', 'SUSPENDED')", ["${input.jobNo}"]),
        sel1("after", "SELECT dispatch_status FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_ne("after.dispatch_status", "ABORTED", "INVALID_STATE", "Only RUNNING or SUSPENDED job orders can be aborted"),
        ex("UPDATE emc_job_response_data SET ended_at = CURRENT_TIMESTAMP WHERE ended_at IS NULL "
           "AND response_id IN (SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        ex("UPDATE emc_job_response SET job_state = 'ABORTED', actual_end = CURRENT_TIMESTAMP "
           "WHERE job_no = ? AND job_state = 'RUNNING'", ["${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "IDLE", ""),
        *machine_write("WU-A02", WU_A02, "IDLE", ""),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) VALUES (gen_random_uuid(), ?, 'ABORTED', ?, 'operator')",
           ["${input.jobNo}", "${input.reason}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}", "status": "ABORTED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_replan",
    [F("jobNo"), F("newJobNo"), F("reasonCode"), F("plannedStart"), F("plannedEnd")],
    OUT(F("jobNo"), F("newJobNo"), F("dispatchStatus")),
    [
        sel1("job", "SELECT job_no, dispatch_status FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_null("input.newJobNo", "VALIDATION", "newJobNo is required"),
        ex("INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, "
           "equipment_id, dispatch_status, command, priority, planned_start, planned_end, original_job_no) "
           "SELECT gen_random_uuid(), ?, request_id, work_master_id, work_master_version, segment_id, equipment_id, "
           "'NOT_ALLOWED', 'STORE', priority, NULLIF(?, ''), NULLIF(?, ''), job_no FROM emc_job_order "
           "WHERE job_no = ? AND NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = ?)",
           ["${input.newJobNo}", "${input.plannedStart}", "${input.plannedEnd}", "${input.jobNo}", "${input.newJobNo}"]),
        ex("UPDATE emc_job_order SET dispatch_status = 'CANCELLED', replaced_by_job_no = ?, replan_reason_code = ? "
           "WHERE job_no = ? AND dispatch_status NOT IN ('ENDED', 'ABORTED')",
           ["${input.newJobNo}", "${input.reasonCode}", "${input.jobNo}"]),
        ex("INSERT INTO emc_job_order_material_req (job_no, definition_id, material_class_id, material_use, quantity, uom) "
           "SELECT ?, definition_id, material_class_id, material_use, quantity, uom FROM emc_job_order_material_req "
           "WHERE job_no = ? AND NOT EXISTS (SELECT 1 FROM emc_job_order_material_req WHERE job_no = ?)",
           ["${input.newJobNo}", "${input.jobNo}", "${input.newJobNo}"]),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) VALUES (gen_random_uuid(), ?, 'REPLANNED', ?, 'dispatcher')",
           ["${input.jobNo}", "${input.newJobNo}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${input.jobNo}",
             "newJobNo": "${input.newJobNo}", "dispatchStatus": "NOT_ALLOWED"}),
    ],
))

# BPMN user task callback: operator confirms job order start from work-queue
FUNCTIONS.append(fn(
    "emc_joborder_confirmStart",
    [F("jobNo"), F("personId")],
    OUT(F("jobNo"), F("status")),
    [
        invoke("started", "emc_joborder_start", {"jobNo": "${input.jobNo}", "personId": "${input.personId}"}),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${started.jobNo}", "status": "${started.status}"}),
    ],
))


# --- Production: Data Collection / Tracking ----------------------------------

FUNCTIONS.append(fn(
    "emc_dc_recordQuantity",
    [F("jobNo"), F("paramKey"), F("paramValue"), F("uom")],
    OUT(F("jobNo"), F("paramKey")),
    [
        fail_null("input.paramKey", "VALIDATION", "paramKey is required"),
        sel1("resp", "SELECT response_id FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING'",
             ["${input.jobNo}"]),
        fail_null("resp", "NO_RUNNING_RESPONSE", "No running response for job order"),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, param_key, param_value, uom) "
           "VALUES (gen_random_uuid(), ?, 'PARAMETER', ?, ?, ?)",
           ["${resp.response_id}", "${input.paramKey}", "${input.paramValue}", "${input.uom}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${input.jobNo}", "paramKey": "${input.paramKey}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_track_genealogyByLot",
    [F("lotId")],
    OUT(F("lotId"), RL("rows", [F("direction"), F("lotId"), F("quantity"), F("definitionId"), F("createdAt")])),
    [
        selN("edges",
             "SELECT 'INPUT' AS direction, g.input_lot_id AS lot_id, g.quantity, l.definition_id, g.created_at "
             "FROM emc_lot_genealogy g LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id WHERE g.output_lot_id = ? "
             "UNION ALL "
             "SELECT 'OUTPUT' AS direction, g.output_lot_id AS lot_id, g.quantity, l.definition_id, g.created_at "
             "FROM emc_lot_genealogy g LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id WHERE g.input_lot_id = ? "
             "ORDER BY created_at",
             ["${input.lotId}", "${input.lotId}"]),
        map_rows("rows", "${edges}", {
            "direction": "${item.direction}", "lotId": "${item.lot_id}", "quantity": "${item.quantity}",
            "definitionId": "${item.definition_id}", "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "lotId": "${input.lotId}", "rows": "${rows}"}),
    ],
))

# Recursive bidirectional lot genealogy (mes-demo style: reverse FG→raw + forward raw→FG/shipment).
# direction: BOTH (default) | UPSTREAM | DOWNSTREAM.
# H2: WITH must be top-level; RECURSIVE CTE needs explicit column list: name (c1, c2, ...) AS (...).
_GENEALOGY_TREE_SQL = """
WITH RECURSIVE upstream (lot_id, linked_from_lot_id, quantity, definition_id, created_at, depth, path) AS (
  SELECT g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at, 1,
         CONCAT(g.output_lot_id, '>', g.input_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE g.output_lot_id = ?
  UNION ALL
  SELECT g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at,
         u.depth + 1, CONCAT(u.path, '>', g.input_lot_id)
  FROM upstream u
  JOIN emc_lot_genealogy g ON g.output_lot_id = u.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE u.depth < 15
),
downstream (lot_id, linked_from_lot_id, quantity, definition_id, created_at, depth, path) AS (
  SELECT g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at, 1,
         CONCAT(g.input_lot_id, '>', g.output_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE g.input_lot_id = ?
  UNION ALL
  SELECT g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''), g.created_at,
         d.depth + 1, CONCAT(d.path, '>', g.output_lot_id)
  FROM downstream d
  JOIN emc_lot_genealogy g ON g.input_lot_id = d.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE d.depth < 15
),
tree (direction, lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at) AS (
  SELECT 'UPSTREAM', lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at
  FROM upstream
  UNION ALL
  SELECT 'DOWNSTREAM', lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at
  FROM downstream
)
SELECT direction, lot_id, linked_from_lot_id, quantity, definition_id, depth, path, created_at
FROM tree
WHERE (UPPER(COALESCE(NULLIF(TRIM(?), ''), 'BOTH')) = 'BOTH'
       OR UPPER(TRIM(?)) = tree.direction)
ORDER BY direction, depth, lot_id
"""

FUNCTIONS.append(fn(
    "emc_track_genealogyTreeByLot",
    [F("lotId"), F("direction")],
    OUT(F("lotId"), F("direction"),
        RL("rows", [F("direction"), F("lotId"), F("linkedFromLotId"), F("quantity"),
                    F("definitionId"), F("depth"), F("path"), F("createdAt")])),
    [
        selN("edges", _GENEALOGY_TREE_SQL,
             ["${input.lotId}", "${input.lotId}", "${input.direction}", "${input.direction}"]),
        map_rows("rows", "${edges}", {
            "direction": "${item.direction}", "lotId": "${item.lot_id}",
            "linkedFromLotId": "${item.linked_from_lot_id}", "quantity": "${item.quantity}",
            "definitionId": "${item.definition_id}", "depth": "${item.depth}",
            "path": "${item.path}", "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "lotId": "${input.lotId}",
             "direction": "${input.direction}", "rows": "${rows}"}),
    ],
))

# --- Part 2/4 extent (Physical Asset, Product, Capability, Ops Capability/Performance, MOM matrix)

FUNCTIONS.append(fn(
    "emc_asset_list",
    [],
    OUT(RL("rows", [F("assetId"), F("classId"), F("equipmentId"), F("serialNo"),
                    F("manufacturer"), F("description"), F("status")])),
    [
        selN("rows_raw",
             "SELECT asset_id, COALESCE(class_id, '') AS class_id, COALESCE(equipment_id, '') AS equipment_id, "
             "COALESCE(serial_no, '') AS serial_no, COALESCE(manufacturer, '') AS manufacturer, "
             "COALESCE(description, '') AS description, status FROM emc_physical_asset ORDER BY asset_id"),
        map_rows("rows", "${rows_raw}", {
            "assetId": "${item.asset_id}", "classId": "${item.class_id}",
            "equipmentId": "${item.equipment_id}", "serialNo": "${item.serial_no}",
            "manufacturer": "${item.manufacturer}", "description": "${item.description}",
            "status": "${item.status}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_product_list",
    [],
    OUT(RL("rows", [F("productId"), F("description"), F("fgDefinitionId"), F("status"), F("segments")])),
    [
        selN("rows_raw",
             "SELECT p.product_id, COALESCE(p.description, '') AS description, "
             "COALESCE(p.fg_definition_id, '') AS fg_definition_id, p.status, "
             "(SELECT COUNT(*) FROM emc_product_segment ps WHERE ps.product_id = p.product_id) AS segments "
             "FROM emc_product_definition p ORDER BY p.product_id"),
        map_rows("rows", "${rows_raw}", {
            "productId": "${item.product_id}", "description": "${item.description}",
            "fgDefinitionId": "${item.fg_definition_id}", "status": "${item.status}",
            "segments": "${item.segments}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_capability_listResults",
    [],
    OUT(RL("rows", [F("resultId"), F("specId"), F("testName"), F("measuredValue"),
                    F("result"), F("testedAt"), F("testedBy")])),
    [
        selN("rows_raw",
             "SELECT CAST(r.result_id AS VARCHAR(64)) AS result_id, r.spec_id, s.test_name, "
             "COALESCE(r.measured_value, '') AS measured_value, r.result, r.tested_at, "
             "COALESCE(r.tested_by, '') AS tested_by "
             "FROM emc_capability_test_result r "
             "JOIN emc_capability_test_spec s ON s.spec_id = r.spec_id "
             "ORDER BY r.tested_at DESC"),
        map_rows("rows", "${rows_raw}", {
            "resultId": "${item.result_id}", "specId": "${item.spec_id}",
            "testName": "${item.test_name}", "measuredValue": "${item.measured_value}",
            "result": "${item.result}", "testedAt": "${item.tested_at}",
            "testedBy": "${item.tested_by}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_capability_recordResult",
    [F("specId"), F("measuredValue"), F("result"), F("testedBy")],
    OUT(F("specId"), F("result")),
    [
        sel1("spec", "SELECT spec_id FROM emc_capability_test_spec WHERE spec_id = ?", ["${input.specId}"]),
        fail_null("spec", "SPEC_NOT_FOUND", "Capability test spec not found"),
        ex("INSERT INTO emc_capability_test_result (result_id, spec_id, measured_value, result, tested_by) "
           "VALUES (gen_random_uuid(), ?, ?, ?, ?)",
           ["${input.specId}", "${input.measuredValue}", "${input.result}", "${input.testedBy}"]),
        ret({"error_code": "OK", "error_message": "", "specId": "${input.specId}", "result": "${input.result}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_opscap_list",
    [],
    OUT(RL("rows", [F("capabilityId"), F("operationsType"), F("equipmentId"), F("segmentId"),
                    F("reason"), F("status")])),
    [
        selN("rows_raw",
             "SELECT capability_id, operations_type, COALESCE(equipment_id, '') AS equipment_id, "
             "COALESCE(segment_id, '') AS segment_id, COALESCE(reason, '') AS reason, status "
             "FROM emc_operations_capability ORDER BY capability_id"),
        map_rows("rows", "${rows_raw}", {
            "capabilityId": "${item.capability_id}", "operationsType": "${item.operations_type}",
            "equipmentId": "${item.equipment_id}", "segmentId": "${item.segment_id}",
            "reason": "${item.reason}", "status": "${item.status}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_opsperf_rollup",
    [F("equipmentId"), F("shiftId")],
    OUT(F("performanceId"), F("goodQty"), F("rejectQty"), F("runMin"), F("downtimeMin")),
    [
        sel1("agg",
             "SELECT COALESCE((SELECT SUM(a.quantity) FROM emc_material_actual a "
             "JOIN emc_job_response r ON r.response_id = a.response_id "
             "JOIN emc_job_order o ON o.job_no = r.job_no "
             "WHERE o.equipment_id = ? AND a.material_use = 'PRODUCED'), 0) AS good_qty, "
             "COALESCE((SELECT SUM(d.qty_declared) FROM emc_defect_record d "
             "JOIN emc_job_order o ON o.job_no = d.job_no WHERE o.equipment_id = ?), 0) AS reject_qty, "
             "COALESCE((SELECT SUM(e.time_min) FROM emc_operations_event e "
             "WHERE e.equipment_id = ? AND e.status = 'CLOSED'), 0) AS downtime_min "
             "FROM (SELECT 1) x",
             ["${input.equipmentId}", "${input.equipmentId}", "${input.equipmentId}"]),
        ex("INSERT INTO emc_operations_performance "
           "(performance_id, operations_type, equipment_id, shift_id, good_qty, reject_qty, run_min, downtime_min, note) "
           "SELECT CAST(gen_random_uuid() AS VARCHAR(64)), 'PRODUCTION', ?, ?, ?, ?, 0, ?, 'Rollup from actuals/events'",
           ["${input.equipmentId}", "${input.shiftId}",
            "${agg.good_qty}", "${agg.reject_qty}", "${agg.downtime_min}"]),
        ret({"error_code": "OK", "error_message": "", "performanceId": "${input.shiftId}",
             "goodQty": "${agg.good_qty}", "rejectQty": "${agg.reject_qty}",
             "runMin": "0", "downtimeMin": "${agg.downtime_min}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_mom_listActivities",
    [],
    OUT(RL("rows", [F("domain"), F("activity"), F("status"), F("note"), F("uiLink")])),
    [
        selN("rows_raw",
             "SELECT domain, activity, status, COALESCE(note, '') AS note, COALESCE(ui_link, '') AS ui_link "
             "FROM emc_mom_activity ORDER BY domain, activity"),
        map_rows("rows", "${rows_raw}", {
            "domain": "${item.domain}", "activity": "${item.activity}", "status": "${item.status}",
            "note": "${item.note}", "uiLink": "${item.ui_link}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_location_list",
    [],
    OUT(RL("rows", [F("locationId"), F("description"), F("locationKind"), F("equipmentId"),
                    F("parentLocationId"), F("status")])),
    [
        selN("rows_raw",
             "SELECT location_id, COALESCE(description, '') AS description, location_kind, "
             "COALESCE(equipment_id, '') AS equipment_id, COALESCE(parent_location_id, '') AS parent_location_id, "
             "status FROM emc_operational_location ORDER BY location_id"),
        map_rows("rows", "${rows_raw}", {
            "locationId": "${item.location_id}", "description": "${item.description}",
            "locationKind": "${item.location_kind}", "equipmentId": "${item.equipment_id}",
            "parentLocationId": "${item.parent_location_id}", "status": "${item.status}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_domainschedule_list",
    [F("domain")],
    OUT(RL("rows", [F("scheduleId"), F("domain"), F("scheduleKind"), F("targetId"),
                    F("quantity"), F("uom"), F("status"), F("note")])),
    [
        selN("rows_raw",
             "SELECT schedule_id, domain, schedule_kind, COALESCE(target_id, '') AS target_id, "
             "COALESCE(quantity, 0) AS quantity, COALESCE(uom, '') AS uom, status, COALESCE(note, '') AS note "
             "FROM emc_domain_schedule "
             "WHERE COALESCE(NULLIF(TRIM(?), ''), domain) = domain "
             "ORDER BY domain, schedule_id",
             ["${input.domain}"]),
        map_rows("rows", "${rows_raw}", {
            "scheduleId": "${item.schedule_id}", "domain": "${item.domain}",
            "scheduleKind": "${item.schedule_kind}", "targetId": "${item.target_id}",
            "quantity": "${item.quantity}", "uom": "${item.uom}",
            "status": "${item.status}", "note": "${item.note}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_domainschedule_upsert",
    [F("scheduleId"), F("domain"), F("scheduleKind"), F("targetId"), F("quantity"), F("uom"), F("note")],
    OUT(F("scheduleId"), F("status")),
    [
        ex("UPDATE emc_domain_schedule SET domain = ?, schedule_kind = ?, target_id = ?, "
           "quantity = CAST(? AS NUMERIC), uom = ?, note = ?, status = 'PLANNED' "
           "WHERE schedule_id = ?",
           ["${input.domain}", "${input.scheduleKind}", "${input.targetId}",
            "${input.quantity}", "${input.uom}", "${input.note}", "${input.scheduleId}"]),
        ex("INSERT INTO emc_domain_schedule "
           "(schedule_id, domain, schedule_kind, target_id, quantity, uom, status, note) "
           "SELECT ?, ?, ?, ?, CAST(? AS NUMERIC), ?, 'PLANNED', ? "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_domain_schedule WHERE schedule_id = ?)",
           ["${input.scheduleId}", "${input.domain}", "${input.scheduleKind}",
            "${input.targetId}", "${input.quantity}", "${input.uom}", "${input.note}",
            "${input.scheduleId}"]),
        ret({"error_code": "OK", "error_message": "", "scheduleId": "${input.scheduleId}",
             "status": "PLANNED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_qa_defectRateKpi",
    [],
    OUT(F("defectCount"), F("confirmedQty"), F("jobsWithDefects"), F("defectRatePct")),
    [
        sel1("kpi",
             "SELECT COUNT(*) AS defect_count, "
             "COALESCE(SUM(COALESCE(qty_confirmed, qty_declared)), 0) AS confirmed_qty, "
             "COUNT(DISTINCT job_no) AS jobs_with_defects, "
             "CASE WHEN (SELECT COUNT(*) FROM emc_job_order) = 0 THEN 0 "
             "ELSE ROUND(100.0 * COUNT(DISTINCT job_no) / (SELECT COUNT(*) FROM emc_job_order), 2) END "
             "AS defect_rate_pct "
             "FROM emc_defect_record"),
        ret({"error_code": "OK", "error_message": "",
             "defectCount": "${kpi.defect_count}", "confirmedQty": "${kpi.confirmed_qty}",
             "jobsWithDefects": "${kpi.jobs_with_defects}", "defectRatePct": "${kpi.defect_rate_pct}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_inv_turnsKpi",
    [],
    OUT(F("stockQty"), F("consumedQty"), F("producedQty"), F("turnsApprox")),
    [
        sel1("kpi",
             "SELECT COALESCE((SELECT SUM(quantity) FROM emc_material_lot WHERE status = 'STOCK'), 0) AS stock_qty, "
             "COALESCE((SELECT SUM(quantity) FROM emc_material_actual WHERE material_use = 'CONSUMED'), 0) AS consumed_qty, "
             "COALESCE((SELECT SUM(quantity) FROM emc_material_actual WHERE material_use = 'PRODUCED'), 0) AS produced_qty, "
             "CASE WHEN COALESCE((SELECT SUM(quantity) FROM emc_material_lot WHERE status = 'STOCK'), 0) = 0 THEN 0 "
             "ELSE ROUND(COALESCE((SELECT SUM(quantity) FROM emc_material_actual WHERE material_use = 'CONSUMED'), 0) "
             "/ (SELECT SUM(quantity) FROM emc_material_lot WHERE status = 'STOCK'), 3) END AS turns_approx"),
        ret({"error_code": "OK", "error_message": "",
             "stockQty": "${kpi.stock_qty}", "consumedQty": "${kpi.consumed_qty}",
             "producedQty": "${kpi.produced_qty}", "turnsApprox": "${kpi.turns_approx}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_maint_mttrMtbf",
    [],
    OUT(F("closedEvents"), F("totalDowntimeMin"), F("mttrMin"), F("mtbfMin")),
    [
        sel1("kpi",
             "SELECT COUNT(*) AS closed_events, "
             "COALESCE(SUM(time_min), 0) AS total_downtime_min, "
             "CASE WHEN COUNT(*) = 0 THEN 0 ELSE ROUND(COALESCE(SUM(time_min), 0) / COUNT(*), 2) END AS mttr_min, "
             "CASE WHEN COUNT(*) <= 1 THEN 480 "
             "ELSE ROUND(480.0 * (SELECT COUNT(DISTINCT equipment_id) FROM emc_work_calendar) / COUNT(*), 2) END "
             "AS mtbf_min "
             "FROM emc_operations_event "
             "WHERE status = 'CLOSED' AND COALESCE(time_min, 0) > 0"),
        ret({"error_code": "OK", "error_message": "",
             "closedEvents": "${kpi.closed_events}", "totalDowntimeMin": "${kpi.total_downtime_min}",
             "mttrMin": "${kpi.mttr_min}", "mtbfMin": "${kpi.mtbf_min}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_track_jobHistory",
    [F("jobNo")],
    OUT(F("jobNo"), F("dispatchStatus"), RL("rows", [F("dataKind"), F("paramKey"), F("paramValue"),
                                                     F("startedAt"), F("endedAt")])),
    [
        sel1("job", "SELECT dispatch_status FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        selN("data",
             "SELECT d.data_kind, COALESCE(d.param_key, '') AS param_key, COALESCE(d.param_value, '') AS param_value, "
             "d.started_at, d.ended_at FROM emc_job_response_data d "
             "JOIN emc_job_response r ON r.response_id = d.response_id WHERE r.job_no = ? ORDER BY d.created_at",
             ["${input.jobNo}"]),
        map_rows("rows", "${data}", {
            "dataKind": "${item.data_kind}", "paramKey": "${item.param_key}", "paramValue": "${item.param_value}",
            "startedAt": "${item.started_at}", "endedAt": "${item.ended_at}"}),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${input.jobNo}",
             "dispatchStatus": "${job.dispatch_status}", "rows": "${rows}"}),
    ],
))

# --- Material model + Inventory Operations -----------------------------------

FUNCTIONS.append(fn(
    "emc_matlot_register",
    [F("lotId"), F("barcode"), F("definitionId"), F("storageLocation"), F("quantity"), F("weightKg"), F("lengthM")],
    OUT(F("lotId"), F("barcode"), F("status")),
    [
        fail_null("input.lotId", "VALIDATION", "lotId is required"),
        fail_null("input.barcode", "VALIDATION", "barcode is required"),
        sel1("def", "SELECT definition_id, base_uom FROM emc_material_definition WHERE definition_id = ?",
             ["${input.definitionId}"]),
        fail_null("def", "DEFINITION_NOT_FOUND", "Material definition not found"),
        sel1("loc", "SELECT equipment_id FROM emc_equipment WHERE equipment_id = ? "
                    "AND equipment_level IN ('STORAGE_ZONE', 'STORAGE_UNIT', 'WORK_UNIT')",
             ["${input.storageLocation}"]),
        fail_null("loc", "LOCATION_NOT_FOUND", "Storage location not found in equipment hierarchy"),
        ex("INSERT INTO emc_material_lot (lot_id, barcode, definition_id, status, storage_location, quantity, base_uom, weight_kg, length_m) "
           "SELECT ?, ?, ?, 'STOCK', ?, COALESCE(NULLIF(?, ''), '0'), ?, NULLIF(?, ''), NULLIF(?, '') "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_material_lot WHERE barcode = ?)",
           ["${input.lotId}", "${input.barcode}", "${input.definitionId}", "${input.storageLocation}",
            "${input.quantity}", "${def.base_uom}", "${input.weightKg}", "${input.lengthM}", "${input.barcode}"]),
        ret({"error_code": "OK", "error_message": "", "lotId": "${input.lotId}",
             "barcode": "${input.barcode}", "status": "STOCK"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_matlot_get",
    [F("barcode")],
    OUT(F("lotId"), F("definitionId"), F("status"), F("storageLocation"), F("quantity"), F("baseUom")),
    [
        sel1("lot", "SELECT lot_id, definition_id, status, COALESCE(storage_location, '') AS storage_location, "
                    "quantity, base_uom FROM emc_material_lot WHERE barcode = ?", ["${input.barcode}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Material lot not found by barcode"),
        ret({"error_code": "OK", "error_message": "", "lotId": "${lot.lot_id}",
             "definitionId": "${lot.definition_id}", "status": "${lot.status}",
             "storageLocation": "${lot.storage_location}", "quantity": "${lot.quantity}",
             "baseUom": "${lot.base_uom}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_matlot_list",
    [F("status"), F("storageLocation"), F("definitionId")],
    OUT(RL("rows", [F("lotId"), F("barcode"), F("definitionId"), F("status"), F("storageLocation"),
                    F("quantity"), F("baseUom"), F("onJobNo")])),
    [
        selN("lots",
             "SELECT lot_id, barcode, definition_id, status, COALESCE(storage_location, '') AS storage_location, "
             "quantity, base_uom, COALESCE(on_job_order_id, '') AS on_job_no FROM emc_material_lot "
             "WHERE (? = '' OR status = ?) AND (? = '' OR storage_location = ?) AND (? = '' OR definition_id = ?) "
             "ORDER BY lot_id",
             ["${input.status}", "${input.status}", "${input.storageLocation}", "${input.storageLocation}",
              "${input.definitionId}", "${input.definitionId}"]),
        map_rows("rows", "${lots}", {
            "lotId": "${item.lot_id}", "barcode": "${item.barcode}", "definitionId": "${item.definition_id}",
            "status": "${item.status}", "storageLocation": "${item.storage_location}",
            "quantity": "${item.quantity}", "baseUom": "${item.base_uom}", "onJobNo": "${item.on_job_no}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_matlot_placeOnLine",
    [F("barcode"), F("jobNo")],
    OUT(F("lotId"), F("jobNo"), F("status")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id, segment_id FROM emc_job_order WHERE job_no = ?",
             ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "RUNNING", "INVALID_STATE", "Job order must be RUNNING"),
        sel1("lot", "SELECT lot_id, definition_id, status FROM emc_material_lot WHERE barcode = ?", ["${input.barcode}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Material lot not found by barcode"),
        sel1("spec", "SELECT ms.definition_id FROM emc_segment_material_spec ms "
                     "WHERE ms.segment_id = ? AND ms.definition_id = ? AND ms.material_use = 'CONSUMED'",
             ["${job.segment_id}", "${lot.definition_id}"]),
        fail_null("spec", "MATERIAL_NOT_IN_SEGMENT_SPEC", "Material is not a consumed input of the job segment"),
        sel1("busy", "SELECT lot_id FROM emc_material_lot WHERE on_job_order_id = ? AND definition_id = ? "
                     "AND status = 'ON_LINE' AND lot_id != ? LIMIT 1",
             ["${input.jobNo}", "${lot.definition_id}", "${lot.lot_id}"]),
        when({"var": "busy.lot_id", "notNull": True}, [
            ret({"error_code": "SLOT_OCCUPIED",
                 "error_message": "Material slot already occupied by lot ${busy.lot_id}",
                 "lotId": "", "jobNo": "${input.jobNo}", "status": ""}),
        ]),
        ex("UPDATE emc_material_lot SET status = 'ON_LINE', on_equipment_id = ?, on_job_order_id = ?, "
           "version_no = version_no + 1 WHERE lot_id = ?",
           ["${job.equipment_id}", "${input.jobNo}", "${lot.lot_id}"]),
        ret({"error_code": "OK", "error_message": "", "lotId": "${lot.lot_id}",
             "jobNo": "${input.jobNo}", "status": "ON_LINE"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_matlot_removeFromLine",
    [F("barcode"), F("storageLocation")],
    OUT(F("lotId"), F("status")),
    [
        sel1("lot", "SELECT lot_id, status FROM emc_material_lot WHERE barcode = ?", ["${input.barcode}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Material lot not found by barcode"),
        fail_ne("lot.status", "ON_LINE", "INVALID_STATE", "Lot is not on line"),
        fail_null("input.storageLocation", "VALIDATION", "storageLocation is required"),
        ex("UPDATE emc_material_lot SET status = 'STOCK', storage_location = ?, on_equipment_id = NULL, "
           "on_job_order_id = NULL, version_no = version_no + 1 WHERE lot_id = ?",
           ["${input.storageLocation}", "${lot.lot_id}"]),
        ret({"error_code": "OK", "error_message": "", "lotId": "${lot.lot_id}", "status": "STOCK"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_matlot_consume",
    [F("barcode"), F("quantity")],
    OUT(F("lotId"), F("consumedQty"), F("remainingQty")),
    [
        sel1("lot", "SELECT lot_id, definition_id, status, on_job_order_id, base_uom FROM emc_material_lot WHERE barcode = ?",
             ["${input.barcode}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Material lot not found by barcode"),
        fail_ne("lot.status", "ON_LINE", "LOT_NOT_ON_LINE", "Lot must be ON_LINE to consume"),
        sel1("resp", "SELECT response_id FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING'",
             ["${lot.on_job_order_id}"]),
        fail_null("resp", "NO_RUNNING_RESPONSE", "No running response for the staging job order"),
        # Part 4 Material Actual (consumed)
        ex("INSERT INTO emc_material_actual (id, response_id, lot_id, definition_id, material_use, quantity, uom) "
           "VALUES (gen_random_uuid(), ?, ?, ?, 'CONSUMED', ?, ?)",
           ["${resp.response_id}", "${lot.lot_id}", "${lot.definition_id}", "${input.quantity}", "${lot.base_uom}"]),
        ex("UPDATE emc_material_lot SET quantity = GREATEST(quantity - ?, 0), "
           "weight_kg = CASE WHEN base_uom = 'kg' THEN GREATEST(COALESCE(weight_kg, 0) - ?, 0) ELSE weight_kg END, "
           "version_no = version_no + 1 WHERE lot_id = ?",
           ["${input.quantity}", "${input.quantity}", "${lot.lot_id}"]),
        sel1("after", "SELECT quantity FROM emc_material_lot WHERE lot_id = ?", ["${lot.lot_id}"]),
        ret({"error_code": "OK", "error_message": "", "lotId": "${lot.lot_id}",
             "consumedQty": "${input.quantity}", "remainingQty": "${after.quantity}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_matlot_produce",
    [F("jobNo"), F("lotId"), F("barcode"), F("definitionId"), F("quantity"), F("storageLocation")],
    OUT(F("lotId"), F("jobNo"), F("producedQty")),
    [
        sel1("job", "SELECT job_no, dispatch_status, segment_id, equipment_id FROM emc_job_order WHERE job_no = ?",
             ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "RUNNING", "INVALID_STATE", "Job order must be RUNNING"),
        sel1("spec", "SELECT ms.definition_id FROM emc_segment_material_spec ms "
                     "WHERE ms.segment_id = ? AND ms.definition_id = ? AND ms.material_use = 'PRODUCED'",
             ["${job.segment_id}", "${input.definitionId}"]),
        fail_null("spec", "MATERIAL_NOT_IN_SEGMENT_SPEC", "Material is not a produced output of the job segment"),
        fail_null("input.lotId", "VALIDATION", "lotId is required"),
        fail_null("input.barcode", "VALIDATION", "barcode is required"),
        sel1("def", "SELECT base_uom FROM emc_material_definition WHERE definition_id = ?", ["${input.definitionId}"]),
        ex("INSERT INTO emc_material_lot (lot_id, barcode, definition_id, status, storage_location, quantity, base_uom) "
           "SELECT ?, ?, ?, 'STOCK', ?, COALESCE(NULLIF(?, ''), '0'), ? "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_material_lot WHERE barcode = ?)",
           ["${input.lotId}", "${input.barcode}", "${input.definitionId}", "${input.storageLocation}",
            "${input.quantity}", "${def.base_uom}", "${input.barcode}"]),
        sel1("resp", "SELECT response_id FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING'",
             ["${input.jobNo}"]),
        fail_null("resp", "NO_RUNNING_RESPONSE", "No running response for job order"),
        # Part 4 Material Actual (produced)
        ex("INSERT INTO emc_material_actual (id, response_id, lot_id, definition_id, material_use, quantity, uom) "
           "VALUES (gen_random_uuid(), ?, ?, ?, 'PRODUCED', ?, ?)",
           ["${resp.response_id}", "${input.lotId}", "${input.definitionId}", "${input.quantity}", "${def.base_uom}"]),
        # Genealogy edges: all consumed lots of this response -> new lot
        ex("INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, response_id, quantity) "
           "SELECT gen_random_uuid(), ma.lot_id, ?, ma.response_id, ma.quantity FROM emc_material_actual ma "
           "WHERE ma.response_id = ? AND ma.material_use = 'CONSUMED' AND ma.lot_id IS NOT NULL "
           "AND NOT EXISTS (SELECT 1 FROM emc_lot_genealogy g WHERE g.input_lot_id = ma.lot_id "
           "AND g.output_lot_id = ? AND g.response_id = ma.response_id)",
           ["${input.lotId}", "${resp.response_id}", "${input.lotId}"]),
        ret({"error_code": "OK", "error_message": "", "lotId": "${input.lotId}",
             "jobNo": "${input.jobNo}", "producedQty": "${input.quantity}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_invdoc_create",
    [F("docId"), F("kind"), F("operatorPersonId")],
    OUT(F("docId"), F("kind"), F("status")),
    [
        fail_null("input.docId", "VALIDATION", "docId is required"),
        sel1("kind_ok", "SELECT COUNT(*) AS cnt FROM (VALUES ('DELIVERY_REQUEST'), ('WRITE_OFF'), ('TRANSFER'), "
             "('PRODUCTION_RELEASE')) v(k) WHERE k = ?", ["${input.kind}"]),
        fail_ne("kind_ok.cnt", "1", "INVALID_KIND", "Unknown inventory document kind"),
        ex("INSERT INTO emc_inventory_document (doc_id, kind, status, operator_person_id) "
           "SELECT ?, ?, 'DRAFT', ? WHERE NOT EXISTS (SELECT 1 FROM emc_inventory_document WHERE doc_id = ?)",
           ["${input.docId}", "${input.kind}", "${input.operatorPersonId}", "${input.docId}"]),
        ret({"error_code": "OK", "error_message": "", "docId": "${input.docId}",
             "kind": "${input.kind}", "status": "DRAFT"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_invdoc_addLine",
    [F("docId"), F("definitionId"), F("lotId"), F("quantity"), F("sourceLocation"), F("destLocation")],
    OUT(F("docId")),
    [
        sel1("doc", "SELECT doc_id, status FROM emc_inventory_document WHERE doc_id = ?", ["${input.docId}"]),
        fail_null("doc", "DOC_NOT_FOUND", "Inventory document not found"),
        fail_ne("doc.status", "DRAFT", "INVALID_STATE", "Lines can be added only to DRAFT documents"),
        ex("INSERT INTO emc_inventory_document_line (line_id, doc_id, definition_id, lot_id, quantity, source_location, dest_location) "
           "VALUES (gen_random_uuid(), ?, ?, NULLIF(?, ''), COALESCE(NULLIF(?, ''), '0'), NULLIF(?, ''), NULLIF(?, ''))",
           ["${input.docId}", "${input.definitionId}", "${input.lotId}", "${input.quantity}",
            "${input.sourceLocation}", "${input.destLocation}"]),
        ret({"error_code": "OK", "error_message": "", "docId": "${input.docId}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_invdoc_submit",
    [F("docId")],
    OUT(F("docId"), F("status")),
    [
        sel1("doc", "SELECT doc_id, kind, status FROM emc_inventory_document WHERE doc_id = ?", ["${input.docId}"]),
        fail_null("doc", "DOC_NOT_FOUND", "Inventory document not found"),
        fail_ne("doc.status", "DRAFT", "INVALID_STATE", "Only DRAFT documents can be submitted"),
        ex("UPDATE emc_inventory_document SET status = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP, "
           "version_no = version_no + 1 WHERE doc_id = ?", ["${input.docId}"]),
        # Part 5: PROCESS Material Lot (movement document) to ERP, idempotent
        ex("INSERT INTO emc_erp_outbox (id, verb, noun, object_id, payload_json, idempotency_key, status) "
           "SELECT gen_random_uuid(), 'PROCESS', 'MATERIAL_LOT', ?, "
           "CONCAT('{\"docId\":\"', ?, '\",\"kind\":\"', ?, '\"}'), CONCAT('INVDOC-SUBMIT:', ?), 'PENDING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_erp_outbox WHERE idempotency_key = CONCAT('INVDOC-SUBMIT:', ?))",
           ["${input.docId}", "${input.docId}", "${doc.kind}", "${input.docId}", "${input.docId}"]),
        ret({"error_code": "OK", "error_message": "", "docId": "${input.docId}", "status": "SUBMITTED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_invdoc_apply",
    [F("docId")],
    OUT(F("docId"), F("status")),
    [
        sel1("doc", "SELECT doc_id, kind, status FROM emc_inventory_document WHERE doc_id = ?", ["${input.docId}"]),
        fail_null("doc", "DOC_NOT_FOUND", "Inventory document not found"),
        fail_ne("doc.status", "SUBMITTED", "INVALID_STATE", "Only SUBMITTED documents can be applied"),
        ex("UPDATE emc_material_lot SET status = 'SCRAPPED', version_no = version_no + 1 "
           "WHERE lot_id IN (SELECT lot_id FROM emc_inventory_document_line WHERE doc_id = ? AND lot_id IS NOT NULL) "
           "AND EXISTS (SELECT 1 FROM emc_inventory_document WHERE doc_id = ? AND kind = 'WRITE_OFF')",
           ["${input.docId}", "${input.docId}"]),
        ex("UPDATE emc_material_lot SET status = 'RELEASED', version_no = version_no + 1 "
           "WHERE lot_id IN (SELECT lot_id FROM emc_inventory_document_line WHERE doc_id = ? AND lot_id IS NOT NULL) "
           "AND EXISTS (SELECT 1 FROM emc_inventory_document WHERE doc_id = ? AND kind = 'PRODUCTION_RELEASE')",
           ["${input.docId}", "${input.docId}"]),
        ex("UPDATE emc_material_lot SET storage_location = "
           "(SELECT dest_location FROM emc_inventory_document_line l WHERE l.doc_id = ? AND l.lot_id = emc_material_lot.lot_id), "
           "version_no = version_no + 1 "
           "WHERE lot_id IN (SELECT lot_id FROM emc_inventory_document_line WHERE doc_id = ? AND lot_id IS NOT NULL) "
           "AND EXISTS (SELECT 1 FROM emc_inventory_document WHERE doc_id = ? AND kind = 'TRANSFER')",
           ["${input.docId}", "${input.docId}", "${input.docId}"]),
        ex("UPDATE emc_inventory_document SET status = 'ACCEPTED', completed_at = CURRENT_TIMESTAMP, "
           "version_no = version_no + 1 WHERE doc_id = ?", ["${input.docId}"]),
        ret({"error_code": "OK", "error_message": "", "docId": "${input.docId}", "status": "ACCEPTED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_invdoc_list",
    [F("status")],
    OUT(RL("rows", [F("docId"), F("kind"), F("status"), F("lines"), F("externalDocRef"), F("createdAt")])),
    [
        selN("docs",
             "SELECT d.doc_id, d.kind, d.status, COALESCE(d.external_doc_ref, '') AS external_doc_ref, d.created_at, "
             "(SELECT COUNT(*) FROM emc_inventory_document_line l WHERE l.doc_id = d.doc_id) AS lines "
             "FROM emc_inventory_document d WHERE (? = '' OR d.status = ?) ORDER BY d.created_at DESC",
             ["${input.status}", "${input.status}"]),
        map_rows("rows", "${docs}", {
            "docId": "${item.doc_id}", "kind": "${item.kind}", "status": "${item.status}",
            "lines": "${item.lines}", "externalDocRef": "${item.external_doc_ref}", "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_stock_list",
    [],
    OUT(RL("rows", [F("definitionId"), F("storageLocation"), F("lots"), F("totalQty"), F("baseUom")])),
    [
        selN("stock",
             "SELECT definition_id, COALESCE(storage_location, '') AS storage_location, COUNT(*) AS lots, "
             "SUM(quantity) AS total_qty, base_uom FROM emc_material_lot WHERE status = 'STOCK' "
             "GROUP BY definition_id, storage_location, base_uom ORDER BY definition_id, storage_location"),
        map_rows("rows", "${stock}", {
            "definitionId": "${item.definition_id}", "storageLocation": "${item.storage_location}",
            "lots": "${item.lots}", "totalQty": "${item.total_qty}", "baseUom": "${item.base_uom}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# --- Quality: defect workflow + test results (ISA-95 Part 3) -------------------

FUNCTIONS.append(fn(
    "emc_qa_registerDefect",
    [F("defectNo"), F("jobNo"), F("defectTypeId"), F("reasonCode"), F("lotId"),
     F("qtyDeclared"), F("severity"), F("createdBy")],
    OUT(F("defectNo"), F("status")),
    [
        sel1("job", "SELECT job_no FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        sel1("dt", "SELECT defect_type_id FROM emc_defect_type WHERE defect_type_id = ?", ["${input.defectTypeId}"]),
        fail_null("dt", "DEFECT_TYPE_UNKNOWN", "Defect type not found"),
        sel1("dup", "SELECT defect_no FROM emc_defect_record WHERE defect_no = ?", ["${input.defectNo}"]),
        when({"var": "dup", "notNull": True}, [
            ret({"error_code": "DUPLICATE_DEFECT", "error_message": "Defect number already registered",
                 "defectNo": "${input.defectNo}", "status": ""}),
        ]),
        ex("INSERT INTO emc_defect_record (defect_id, defect_no, job_no, lot_id, defect_type_id, reason_code, severity, qty_declared, created_by) "
           "VALUES (gen_random_uuid(), ?, ?, NULLIF(?, ''), ?, NULLIF(?, ''), COALESCE(NULLIF(?, ''), 'MINOR'), "
           "COALESCE(CAST(NULLIF(CAST(? AS VARCHAR), '') AS NUMERIC), 1), NULLIF(?, ''))",
           ["${input.defectNo}", "${input.jobNo}", "${input.lotId}", "${input.defectTypeId}",
            "${input.reasonCode}", "${input.severity}", "${input.qtyDeclared}", "${input.createdBy}"]),
        ex("INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) "
           "VALUES (gen_random_uuid(), ?, NULL, 'REGISTERED', ?, NULL)",
           ["${input.defectNo}", "${input.createdBy}"]),
        # CRITICAL severity blocks the affected lot (quality hold)
        when({"var": "input.severity", "equals": "CRITICAL"}, [
            ex("UPDATE emc_material_lot SET status = 'BLOCKED_QC' WHERE lot_id = NULLIF(?, '') AND status = 'STOCK'",
               ["${input.lotId}"]),
        ]),
        ret({"error_code": "OK", "error_message": "", "defectNo": "${input.defectNo}", "status": "REGISTERED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_qa_confirmDefect",
    [F("defectNo"), F("by"), F("reasonCode"), F("qtyConfirmed")],
    OUT(F("defectNo"), F("status")),
    [
        sel1("d", "SELECT defect_no, status FROM emc_defect_record WHERE defect_no = ?", ["${input.defectNo}"]),
        fail_null("d", "DEFECT_NOT_FOUND", "Defect not found"),
        fail_ne("d.status", "REGISTERED", "INVALID_STATE", "Only REGISTERED defects can be confirmed"),
        ex("UPDATE emc_defect_record SET status = 'CONFIRMED', "
           "qty_confirmed = COALESCE(CAST(NULLIF(CAST(? AS VARCHAR), '') AS NUMERIC), qty_declared), "
           "reason_code = COALESCE(NULLIF(?, ''), reason_code) "
           "WHERE defect_no = ? AND status = 'REGISTERED'",
           ["${input.qtyConfirmed}", "${input.reasonCode}", "${input.defectNo}"]),
        ex("INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) "
           "VALUES (gen_random_uuid(), ?, 'REGISTERED', 'CONFIRMED', ?, NULL)",
           ["${input.defectNo}", "${input.by}"]),
        ret({"error_code": "OK", "error_message": "", "defectNo": "${input.defectNo}", "status": "CONFIRMED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_qa_rejectDefect",
    [F("defectNo"), F("by"), F("note")],
    OUT(F("defectNo"), F("status")),
    [
        sel1("d", "SELECT defect_no, status FROM emc_defect_record WHERE defect_no = ?", ["${input.defectNo}"]),
        fail_null("d", "DEFECT_NOT_FOUND", "Defect not found"),
        fail_ne("d.status", "REGISTERED", "INVALID_STATE", "Only REGISTERED defects can be rejected"),
        ex("UPDATE emc_defect_record SET status = 'REJECTED' WHERE defect_no = ? AND status = 'REGISTERED'",
           ["${input.defectNo}"]),
        ex("INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) "
           "VALUES (gen_random_uuid(), ?, 'REGISTERED', 'REJECTED', ?, NULLIF(?, ''))",
           ["${input.defectNo}", "${input.by}", "${input.note}"]),
        ret({"error_code": "OK", "error_message": "", "defectNo": "${input.defectNo}", "status": "REJECTED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_qa_closeDefect",
    [F("defectNo"), F("by")],
    OUT(F("defectNo"), F("status")),
    [
        sel1("d", "SELECT defect_no, status, lot_id FROM emc_defect_record WHERE defect_no = ?", ["${input.defectNo}"]),
        fail_null("d", "DEFECT_NOT_FOUND", "Defect not found"),
        fail_ne("d.status", "CONFIRMED", "INVALID_STATE", "Only CONFIRMED defects can be closed"),
        ex("UPDATE emc_defect_record SET status = 'CLOSED' WHERE defect_no = ? AND status = 'CONFIRMED'",
           ["${input.defectNo}"]),
        ex("INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) "
           "VALUES (gen_random_uuid(), ?, 'CONFIRMED', 'CLOSED', ?, NULL)",
           ["${input.defectNo}", "${input.by}"]),
        # release the quality hold on the lot
        ex("UPDATE emc_material_lot SET status = 'STOCK' WHERE lot_id = ? AND status = 'BLOCKED_QC'",
           ["${d.lot_id}"]),
        ret({"error_code": "OK", "error_message": "", "defectNo": "${input.defectNo}", "status": "CLOSED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_qa_listDefects",
    [F("jobNo"), F("status")],
    OUT(RL("rows", [F("defectNo"), F("jobNo"), F("lotId"), F("defectTypeId"), F("reasonCode"),
                    F("severity"), F("qtyDeclared"), F("qtyConfirmed"), F("status"), F("createdBy"), F("createdAt")])),
    [
        selN("defects",
             "SELECT defect_no, job_no, COALESCE(lot_id, '') AS lot_id, defect_type_id, "
             "COALESCE(reason_code, '') AS reason_code, severity, qty_declared, qty_confirmed, status, "
             "COALESCE(created_by, '') AS created_by, created_at FROM emc_defect_record "
             "WHERE (? = '' OR job_no = ?) AND (? = '' OR status = ?) ORDER BY created_at DESC",
             ["${input.jobNo}", "${input.jobNo}", "${input.status}", "${input.status}"]),
        map_rows("rows", "${defects}", {
            "defectNo": "${item.defect_no}", "jobNo": "${item.job_no}", "lotId": "${item.lot_id}",
            "defectTypeId": "${item.defect_type_id}", "reasonCode": "${item.reason_code}",
            "severity": "${item.severity}", "qtyDeclared": "${item.qty_declared}",
            "qtyConfirmed": "${item.qty_confirmed}", "status": "${item.status}",
            "createdBy": "${item.created_by}", "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_qa_recordTestResult",
    [F("jobNo"), F("lotId"), F("testName"), F("result"), F("measurementsJson")],
    OUT(F("testName"), F("result")),
    [
        fail_null("input.testName", "VALIDATION", "testName is required"),
        when({"var": "input.result", "notEquals": "PASS"}, [
            when({"var": "input.result", "notEquals": "FAIL"}, [
                ret({"error_code": "VALIDATION", "error_message": "result must be PASS or FAIL",
                     "testName": "${input.testName}", "result": ""}),
            ]),
        ]),
        ex("INSERT INTO emc_qa_test_result (id, job_no, lot_id, test_name, result, measurements_json) "
           "VALUES (gen_random_uuid(), NULLIF(?, ''), NULLIF(?, ''), ?, ?, NULLIF(?, ''))",
           ["${input.jobNo}", "${input.lotId}", "${input.testName}", "${input.result}", "${input.measurementsJson}"]),
        ret({"error_code": "OK", "error_message": "", "testName": "${input.testName}", "result": "${input.result}"}),
    ],
))

# --- Maintenance: request -> work order (ISA-95 Part 3, lite) ------------------

FUNCTIONS.append(fn(
    "emc_maint_createRequest",
    [F("requestId"), F("equipmentId"), F("description"), F("priority")],
    OUT(F("requestId"), F("status")),
    [
        sel1("eq", "SELECT equipment_id FROM emc_equipment WHERE equipment_id = ?", ["${input.equipmentId}"]),
        fail_null("eq", "EQUIPMENT_NOT_FOUND", "Equipment not found"),
        ex("INSERT INTO emc_maintenance_request (request_id, equipment_id, description, priority, status) "
           "SELECT ?, ?, NULLIF(?, ''), COALESCE(NULLIF(?, ''), '5'), 'NEW' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_maintenance_request WHERE request_id = ?)",
           ["${input.requestId}", "${input.equipmentId}", "${input.description}", "${input.priority}", "${input.requestId}"]),
        ret({"error_code": "OK", "error_message": "", "requestId": "${input.requestId}", "status": "NEW"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_maint_acceptRequest",
    [F("requestId"), F("woId"), F("plannedStart"), F("plannedEnd")],
    OUT(F("requestId"), F("woId"), F("status")),
    [
        sel1("r", "SELECT request_id, equipment_id, status FROM emc_maintenance_request WHERE request_id = ?",
             ["${input.requestId}"]),
        fail_null("r", "REQUEST_NOT_FOUND", "Maintenance request not found"),
        fail_ne("r.status", "NEW", "INVALID_STATE", "Only NEW requests can be accepted"),
        ex("UPDATE emc_maintenance_request SET status = 'ACCEPTED' WHERE request_id = ?", ["${input.requestId}"]),
        ex("INSERT INTO emc_maintenance_work_order (wo_id, request_id, equipment_id, status, planned_start, planned_end) "
           "SELECT ?, ?, ?, 'PLANNED', NULLIF(?, ''), NULLIF(?, '') "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_maintenance_work_order WHERE wo_id = ?)",
           ["${input.woId}", "${input.requestId}", "${r.equipment_id}", "${input.plannedStart}",
            "${input.plannedEnd}", "${input.woId}"]),
        ret({"error_code": "OK", "error_message": "", "requestId": "${input.requestId}",
             "woId": "${input.woId}", "status": "PLANNED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_maint_completeWorkOrder",
    [F("woId")],
    OUT(F("woId"), F("status")),
    [
        sel1("wo", "SELECT wo_id, status FROM emc_maintenance_work_order WHERE wo_id = ?", ["${input.woId}"]),
        fail_null("wo", "WO_NOT_FOUND", "Maintenance work order not found"),
        fail_ne("wo.status", "DONE", "ALREADY_DONE", "Work order is already done"),
        ex("UPDATE emc_maintenance_work_order SET status = 'DONE', "
           "actual_start = COALESCE(actual_start, CURRENT_TIMESTAMP), actual_end = CURRENT_TIMESTAMP "
           "WHERE wo_id = ?", ["${input.woId}"]),
        ex("UPDATE emc_maintenance_request SET status = 'CLOSED' "
           "WHERE request_id = (SELECT request_id FROM emc_maintenance_work_order WHERE wo_id = ?)",
           ["${input.woId}"]),
        ret({"error_code": "OK", "error_message": "", "woId": "${input.woId}", "status": "DONE"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_maint_list",
    [],
    OUT(RL("rows", [F("requestId"), F("equipmentId"), F("description"), F("priority"),
                    F("status"), F("workOrders"), F("createdAt")])),
    [
        selN("reqs",
             "SELECT r.request_id, r.equipment_id, COALESCE(r.description, '') AS description, r.priority, r.status, "
             "(SELECT COUNT(*) FROM emc_maintenance_work_order w WHERE w.request_id = r.request_id) AS work_orders, "
             "r.created_at FROM emc_maintenance_request r ORDER BY r.created_at DESC"),
        map_rows("rows", "${reqs}", {
            "requestId": "${item.request_id}", "equipmentId": "${item.equipment_id}",
            "description": "${item.description}", "priority": "${item.priority}", "status": "${item.status}",
            "workOrders": "${item.work_orders}", "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# --- Operations events (downtime/OEE loss capture, ISA-95 Part 2 2018) ---------

FUNCTIONS.append(fn(
    "emc_eventdef_list",
    [],
    OUT(RL("rows", [F("code"), F("eventClass"), F("name"), F("requiresLength"), F("requiresTime"),
                    F("requiresComment"), F("oeeBucket"), F("sixBigLoss"), F("sortOrder")])),
    [
        selN("defs",
             "SELECT code, event_class, name, requires_length, requires_time, requires_comment, oee_bucket, "
             "COALESCE(six_big_loss, '') AS six_big_loss, sort_order "
             "FROM emc_operations_event_definition ORDER BY sort_order"),
        map_rows("rows", "${defs}", {
            "code": "${item.code}", "eventClass": "${item.event_class}", "name": "${item.name}",
            "requiresLength": "${item.requires_length}", "requiresTime": "${item.requires_time}",
            "requiresComment": "${item.requires_comment}", "oeeBucket": "${item.oee_bucket}",
            "sixBigLoss": "${item.six_big_loss}", "sortOrder": "${item.sort_order}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_event_register",
    [F("definitionCode"), F("jobNo"), F("equipmentId"), F("lotId"),
     F("lengthM"), F("timeMin"), F("comment"), F("by")],
    OUT(F("definitionCode"), F("status")),
    [
        sel1("def", "SELECT code, requires_comment, oee_bucket FROM emc_operations_event_definition WHERE code = ?",
             ["${input.definitionCode}"]),
        fail_null("def", "EVENT_DEF_UNKNOWN", "Operations event definition not found"),
        when({"var": "def.requires_comment", "equals": "true"}, [
            when({"var": "input.comment", "equals": ""}, [
                ret({"error_code": "COMMENT_REQUIRED",
                     "error_message": "This event definition requires a comment",
                     "definitionCode": "${input.definitionCode}", "status": ""}),
            ]),
        ]),
        ex("INSERT INTO emc_operations_event (event_id, definition_code, job_no, equipment_id, lot_id, "
           "length_m, time_min, comment_text, status, registered_by) "
           "VALUES (gen_random_uuid(), ?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), "
           "CAST(NULLIF(CAST(? AS VARCHAR), '') AS NUMERIC), "
           "CAST(NULLIF(CAST(? AS VARCHAR), '') AS NUMERIC), "
           "NULLIF(?, ''), CASE WHEN ? = 'AVAILABILITY' THEN 'OPEN' ELSE 'CLOSED' END, NULLIF(?, ''))",
           ["${input.definitionCode}", "${input.jobNo}", "${input.equipmentId}", "${input.lotId}",
            "${input.lengthM}", "${input.timeMin}", "${input.comment}", "${def.oee_bucket}", "${input.by}"]),
        ret({"error_code": "OK", "error_message": "", "definitionCode": "${input.definitionCode}", "status": "REGISTERED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_event_registerSignal",
    [F("equipmentId"), F("signalCode"), F("isAuto")],
    OUT(F("equipmentId"), F("signalCode")),
    [
        fail_null("input.equipmentId", "VALIDATION", "equipmentId is required"),
        fail_null("input.signalCode", "VALIDATION", "signalCode is required"),
        when({"var": "input.isAuto", "equals": "true"}, [
            ex("INSERT INTO emc_machine_signal (signal_id, equipment_id, signal_code, is_auto) "
               "VALUES (gen_random_uuid(), ?, ?, true)",
               ["${input.equipmentId}", "${input.signalCode}"]),
        ], [
            ex("INSERT INTO emc_machine_signal (signal_id, equipment_id, signal_code, is_auto) "
               "VALUES (gen_random_uuid(), ?, ?, false)",
               ["${input.equipmentId}", "${input.signalCode}"]),
        ]),
        ret({"error_code": "OK", "error_message": "", "equipmentId": "${input.equipmentId}",
             "signalCode": "${input.signalCode}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_event_close",
    [F("eventId"), F("by")],
    OUT(F("eventId"), F("status")),
    [
        sel1("ev", "SELECT event_id, status FROM emc_operations_event WHERE event_id = ?", ["${input.eventId}"]),
        fail_null("ev", "EVENT_NOT_FOUND", "Operations event not found"),
        fail_ne("ev.status", "OPEN", "INVALID_STATE", "Only OPEN events can be closed"),
        ex("UPDATE emc_operations_event SET status = 'CLOSED', ended_at = CURRENT_TIMESTAMP "
           "WHERE event_id = ? AND status = 'OPEN'", ["${input.eventId}"]),
        ret({"error_code": "OK", "error_message": "", "eventId": "${input.eventId}", "status": "CLOSED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_event_list",
    [F("equipmentId"), F("status")],
    OUT(RL("rows", [F("eventId"), F("definitionCode"), F("name"), F("oeeBucket"), F("jobNo"),
                    F("equipmentId"), F("timeMin"), F("status"), F("startedAt"), F("endedAt")])),
    [
        selN("events",
             "SELECT e.event_id, e.definition_code, d.name, d.oee_bucket, COALESCE(e.job_no, '') AS job_no, "
             "COALESCE(e.equipment_id, '') AS equipment_id, e.time_min, e.status, e.started_at, e.ended_at "
             "FROM emc_operations_event e JOIN emc_operations_event_definition d ON d.code = e.definition_code "
             "WHERE (? = '' OR e.equipment_id = ?) AND (? = '' OR e.status = ?) ORDER BY e.started_at DESC",
             ["${input.equipmentId}", "${input.equipmentId}", "${input.status}", "${input.status}"]),
        map_rows("rows", "${events}", {
            "eventId": "${item.event_id}", "definitionCode": "${item.definition_code}", "name": "${item.name}",
            "oeeBucket": "${item.oee_bucket}", "jobNo": "${item.job_no}", "equipmentId": "${item.equipment_id}",
            "timeMin": "${item.time_min}", "status": "${item.status}",
            "startedAt": "${item.started_at}", "endedAt": "${item.ended_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# --- Work calendar (shifts, ISA-95 Part 4) -------------------------------------

FUNCTIONS.append(fn(
    "emc_calendar_openShift",
    [F("shiftId"), F("equipmentId"), F("shiftLabel"), F("plannedMinutes"), F("plannedStart")],
    OUT(F("shiftId"), F("state")),
    [
        sel1("eq", "SELECT equipment_id FROM emc_equipment WHERE equipment_id = ?", ["${input.equipmentId}"]),
        fail_null("eq", "EQUIPMENT_NOT_FOUND", "Equipment not found"),
        ex("INSERT INTO emc_work_calendar (shift_id, equipment_id, shift_label, planned_minutes, state, planned_start, actual_start) "
           "SELECT ?, ?, ?, COALESCE(NULLIF(?, ''), '480'), 'OPEN', NULLIF(?, ''), CURRENT_TIMESTAMP "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_work_calendar WHERE shift_id = ?)",
           ["${input.shiftId}", "${input.equipmentId}", "${input.shiftLabel}", "${input.plannedMinutes}",
            "${input.plannedStart}", "${input.shiftId}"]),
        ret({"error_code": "OK", "error_message": "", "shiftId": "${input.shiftId}", "state": "OPEN"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_calendar_closeShift",
    [F("shiftId")],
    OUT(F("shiftId"), F("state")),
    [
        sel1("s", "SELECT shift_id, state FROM emc_work_calendar WHERE shift_id = ?", ["${input.shiftId}"]),
        fail_null("s", "SHIFT_NOT_FOUND", "Shift not found"),
        fail_ne("s.state", "OPEN", "INVALID_STATE", "Only OPEN shifts can be closed"),
        ex("UPDATE emc_work_calendar SET state = 'CLOSED', actual_end = CURRENT_TIMESTAMP WHERE shift_id = ?",
           ["${input.shiftId}"]),
        ret({"error_code": "OK", "error_message": "", "shiftId": "${input.shiftId}", "state": "CLOSED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_calendar_assignPerson",
    [F("shiftId"), F("personId"), F("handoverFromId")],
    OUT(F("shiftId"), F("personId")),
    [
        sel1("s", "SELECT shift_id FROM emc_work_calendar WHERE shift_id = ?", ["${input.shiftId}"]),
        fail_null("s", "SHIFT_NOT_FOUND", "Shift not found"),
        sel1("p", "SELECT person_id FROM emc_person WHERE person_id = ?", ["${input.personId}"]),
        fail_null("p", "PERSON_NOT_FOUND", "Person not found"),
        ex("INSERT INTO emc_shift_assignment (id, shift_id, person_id, handover_from_id) "
           "SELECT gen_random_uuid(), ?, ?, NULLIF(?, '') "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_shift_assignment WHERE shift_id = ? AND person_id = ?)",
           ["${input.shiftId}", "${input.personId}", "${input.handoverFromId}",
            "${input.shiftId}", "${input.personId}"]),
        ret({"error_code": "OK", "error_message": "", "shiftId": "${input.shiftId}", "personId": "${input.personId}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_calendar_current",
    [F("equipmentId")],
    OUT(F("shiftId"), F("shiftLabel"), F("plannedMinutes"), RL("crew", [F("personId")])),
    [
        sel1("shift",
             "SELECT shift_id, shift_label, planned_minutes FROM emc_work_calendar "
             "WHERE equipment_id = ? AND state = 'OPEN' ORDER BY planned_start DESC LIMIT 1",
             ["${input.equipmentId}"]),
        when({"var": "shift", "notNull": True}, [
            selN("crew_rows", "SELECT person_id FROM emc_shift_assignment WHERE shift_id = ? ORDER BY assigned_at",
                 ["${shift.shift_id}"]),
            map_rows("crew", "${crew_rows}", {"personId": "${item.person_id}"}),
            ret({"error_code": "OK", "error_message": "", "shiftId": "${shift.shift_id}",
                 "shiftLabel": "${shift.shift_label}", "plannedMinutes": "${shift.planned_minutes}",
                 "crew": "${crew}"}),
        ], [
            ret({"error_code": "NO_OPEN_SHIFT", "error_message": "No open shift for equipment",
                 "shiftId": "", "shiftLabel": "", "plannedMinutes": "", "crew": ""}),
        ]),
        # unreachable fallback (validator requires a top-level return)
        ret({"error_code": "NO_OPEN_SHIFT", "error_message": "No open shift for equipment",
             "shiftId": "", "shiftLabel": "", "plannedMinutes": "", "crew": ""}),
    ],
))

# --- Work record / production dossier (ISA-95 Part 4 cl.15) --------------------

FUNCTIONS.append(fn(
    "emc_wrec_get",
    [F("jobNo")],
    OUT(F("recordId"), F("recordNo"),
        RL("sections", [F("sectionKey"), F("title"), F("contentJson"), F("updatedAt")])),
    [
        sel1("rec", "SELECT record_id, record_no FROM emc_work_record WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("rec", "WORK_RECORD_NOT_FOUND", "No work record for job order"),
        selN("sec",
             "SELECT section_key, COALESCE(title, '') AS title, COALESCE(content_json, '') AS content_json, updated_at "
             "FROM emc_work_record_section WHERE record_id = ? ORDER BY section_key",
             ["${rec.record_id}"]),
        map_rows("sections", "${sec}", {
            "sectionKey": "${item.section_key}", "title": "${item.title}",
            "contentJson": "${item.content_json}", "updatedAt": "${item.updated_at}"}),
        ret({"error_code": "OK", "error_message": "", "recordId": "${rec.record_id}",
             "recordNo": "${rec.record_no}", "sections": "${sections}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_wrec_saveSection",
    [F("jobNo"), F("sectionKey"), F("title"), F("contentJson")],
    OUT(F("recordId"), F("sectionKey")),
    [
        fail_null("input.sectionKey", "VALIDATION", "sectionKey is required"),
        # auto-create the work record (job bag) on first section save
        ex("INSERT INTO emc_work_record (record_id, job_no, record_no) "
           "SELECT CONCAT('WR-', ?), ?, CONCAT('WREC-', ?) "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_work_record WHERE job_no = ?)",
           ["${input.jobNo}", "${input.jobNo}", "${input.jobNo}", "${input.jobNo}"]),
        sel1("rec", "SELECT record_id FROM emc_work_record WHERE job_no = ?", ["${input.jobNo}"]),
        ex("UPDATE emc_work_record_section SET title = COALESCE(NULLIF(?, ''), title), "
           "content_json = NULLIF(?, ''), updated_at = CURRENT_TIMESTAMP "
           "WHERE record_id = ? AND section_key = ?",
           ["${input.title}", "${input.contentJson}", "${rec.record_id}", "${input.sectionKey}"]),
        ex("INSERT INTO emc_work_record_section (record_id, section_key, title, content_json) "
           "SELECT ?, ?, NULLIF(?, ''), NULLIF(?, '') "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = ? AND section_key = ?)",
           ["${rec.record_id}", "${input.sectionKey}", "${input.title}", "${input.contentJson}",
            "${rec.record_id}", "${input.sectionKey}"]),
        ret({"error_code": "OK", "error_message": "", "recordId": "${rec.record_id}",
             "sectionKey": "${input.sectionKey}"}),
    ],
))

# --- ERP integration: ISA-95 Part 5 verb x noun transactions -------------------

FUNCTIONS.append(fn(
    "emc_erp_enqueueOutbox",
    [F("verb"), F("noun"), F("objectId"), F("payloadJson"), F("idempotencyKey")],
    OUT(F("idempotencyKey"), F("status")),
    [
        fail_null("input.verb", "VALIDATION", "verb is required"),
        fail_null("input.noun", "VALIDATION", "noun is required"),
        fail_null("input.idempotencyKey", "VALIDATION", "idempotencyKey is required"),
        ex("INSERT INTO emc_erp_outbox (id, verb, noun, object_id, payload_json, idempotency_key, status) "
           "SELECT gen_random_uuid(), ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, 'PENDING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_erp_outbox WHERE idempotency_key = ?)",
           ["${input.verb}", "${input.noun}", "${input.objectId}", "${input.payloadJson}",
            "${input.idempotencyKey}", "${input.idempotencyKey}"]),
        ret({"error_code": "OK", "error_message": "", "idempotencyKey": "${input.idempotencyKey}", "status": "PENDING"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_erp_pollOutbox",
    [F("simulate")],
    OUT(F("transported")),
    [
        # without the built-in simulator there is no ERP connector configured
        when({"var": "input.simulate", "equals": "false"}, [
            ret({"error_code": "CONNECTOR_NOT_CONFIGURED",
                 "error_message": "No ERP connector configured (set simulate=true for the built-in simulator)",
                 "transported": "0"}),
        ]),
        ex("UPDATE emc_erp_outbox SET status = 'IN_FLIGHT' WHERE status = 'PENDING'"),
        sel1("cnt", "SELECT COUNT(*) AS cnt FROM emc_erp_outbox WHERE status = 'IN_FLIGHT'"),
        ex("INSERT INTO emc_integration_log (id, direction, verb, noun, success, code, message) "
           "SELECT gen_random_uuid(), 'OUT', verb, noun, true, 'OK', 'Simulated transport, ACK ACCEPTED' "
           "FROM emc_erp_outbox WHERE status = 'IN_FLIGHT'"),
        ex("UPDATE emc_erp_outbox SET status = 'ACKED', ack_code = 'ACCEPTED' WHERE status = 'IN_FLIGHT'"),
        ret({"error_code": "OK", "error_message": "", "transported": "${cnt.cnt}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_erp_receiveInbox",
    [F("verb"), F("noun"), F("payloadJson"), F("idempotencyKey")],
    OUT(F("idempotencyKey"), F("status")),
    [
        fail_null("input.idempotencyKey", "VALIDATION", "idempotencyKey is required"),
        sel1("dup", "SELECT id FROM emc_erp_inbox WHERE idempotency_key = ?", ["${input.idempotencyKey}"]),
        when({"var": "dup", "notNull": True}, [
            ret({"error_code": "DUPLICATE", "error_message": "Message with this idempotency key already received",
                 "idempotencyKey": "${input.idempotencyKey}", "status": "DUPLICATE"}),
        ]),
        ex("INSERT INTO emc_erp_inbox (id, verb, noun, payload_json, idempotency_key, status) "
           "VALUES (gen_random_uuid(), ?, ?, ?, ?, 'RECEIVED')",
           ["${input.verb}", "${input.noun}", "${input.payloadJson}", "${input.idempotencyKey}"]),
        # dispatch by Part 5 noun
        when({"var": "input.noun", "equals": "OPERATIONS_SCHEDULE"}, [
            json_parse("p", "${input.payloadJson}",
                       ["externalRef", "requestId", "jobNo", "workMasterId", "workMasterVersion",
                        "equipmentId", "productDefinitionId", "quantity", "uom", "priority",
                        "plannedStart", "plannedEnd"]),
            invoke("sched", "emc_schedule_receive", {
                "externalRef": "${p.externalRef}", "requestId": "${p.requestId}", "jobNo": "${p.jobNo}",
                "workMasterId": "${p.workMasterId}", "workMasterVersion": "${p.workMasterVersion}",
                "equipmentId": "${p.equipmentId}", "productDefinitionId": "${p.productDefinitionId}",
                "quantity": "${p.quantity}", "uom": "${p.uom}", "priority": "${p.priority}",
                "plannedStart": "${p.plannedStart}", "plannedEnd": "${p.plannedEnd}"}),
        ]),
        when({"var": "input.noun", "equals": "MASTER_DATA"}, [
            json_parse("m", "${input.payloadJson}", ["entityType", "externalId"]),
            invoke("md", "emc_erp_syncMasterData", {
                "entityType": "${m.entityType}", "externalId": "${m.externalId}",
                "payloadJson": "${input.payloadJson}"}),
        ]),
        when({"var": "input.noun", "equals": "OPERATIONS_CAPABILITY"}, [
            ex("INSERT INTO emc_integration_log (id, direction, verb, noun, success, code, message) "
               "VALUES (gen_random_uuid(), 'IN', ?, ?, true, 'OK', 'Capability query acknowledged')",
               ["${input.verb}", "${input.noun}"]),
        ]),
        when({"var": "input.noun", "equals": "OPERATIONS_DEFINITION"}, [
            ex("INSERT INTO emc_integration_log (id, direction, verb, noun, success, code, message) "
               "VALUES (gen_random_uuid(), 'IN', ?, ?, true, 'OK', 'Operations definition show acknowledged')",
               ["${input.verb}", "${input.noun}"]),
        ]),
        when({"var": "input.noun", "equals": "PRODUCT_DEFINITION"}, [
            ex("INSERT INTO emc_integration_log (id, direction, verb, noun, success, code, message) "
               "VALUES (gen_random_uuid(), 'IN', ?, ?, true, 'OK', 'Product definition show acknowledged')",
               ["${input.verb}", "${input.noun}"]),
        ]),
        ex("UPDATE emc_erp_inbox SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP "
           "WHERE idempotency_key = ?", ["${input.idempotencyKey}"]),
        ret({"error_code": "OK", "error_message": "", "idempotencyKey": "${input.idempotencyKey}", "status": "PROCESSED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_erp_syncMasterData",
    [F("entityType"), F("externalId"), F("payloadJson")],
    OUT(F("entityType"), F("externalId")),
    [
        fail_null("input.entityType", "VALIDATION", "entityType is required"),
        fail_null("input.externalId", "VALIDATION", "externalId is required"),
        ex("UPDATE emc_master_data_replica SET payload_json = ?, synced_at = CURRENT_TIMESTAMP "
           "WHERE entity_type = ? AND external_id = ?",
           ["${input.payloadJson}", "${input.entityType}", "${input.externalId}"]),
        ex("INSERT INTO emc_master_data_replica (entity_type, external_id, payload_json) "
           "SELECT ?, ?, ? WHERE NOT EXISTS "
           "(SELECT 1 FROM emc_master_data_replica WHERE entity_type = ? AND external_id = ?)",
           ["${input.entityType}", "${input.externalId}", "${input.payloadJson}",
            "${input.entityType}", "${input.externalId}"]),
        ret({"error_code": "OK", "error_message": "", "entityType": "${input.entityType}",
             "externalId": "${input.externalId}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_erp_listOutbox",
    [],
    OUT(RL("rows", [F("verb"), F("noun"), F("objectId"), F("status"), F("ackCode"),
                    F("idempotencyKey"), F("createdAt")])),
    [
        selN("msgs",
             "SELECT verb, noun, COALESCE(object_id, '') AS object_id, status, "
             "COALESCE(ack_code, '') AS ack_code, idempotency_key, created_at "
             "FROM emc_erp_outbox ORDER BY created_at DESC"),
        map_rows("rows", "${msgs}", {
            "verb": "${item.verb}", "noun": "${item.noun}", "objectId": "${item.object_id}",
            "status": "${item.status}", "ackCode": "${item.ack_code}",
            "idempotencyKey": "${item.idempotency_key}", "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# --- Performance analysis: OEE per shift (ISO 22400 subset) --------------------

FUNCTIONS.append(fn(
    "emc_oee_calcShift",
    [F("equipmentId"), F("shiftLabel"), F("plannedMinutes")],
    OUT(F("equipmentId"), F("shiftLabel"), F("availabilityPct"), F("performancePct"),
        F("qualityPct"), F("oeePct")),
    [
        fail_null("input.equipmentId", "VALIDATION", "equipmentId is required"),
        fail_null("input.shiftLabel", "VALIDATION", "shiftLabel is required"),
        sel1("av", "SELECT COALESCE(SUM(e.time_min), 0) AS v FROM emc_operations_event e "
                   "JOIN emc_operations_event_definition d ON d.code = e.definition_code "
                   "WHERE d.oee_bucket = 'AVAILABILITY' AND e.equipment_id = ?", ["${input.equipmentId}"]),
        sel1("pf", "SELECT COALESCE(SUM(e.time_min), 0) AS v FROM emc_operations_event e "
                   "JOIN emc_operations_event_definition d ON d.code = e.definition_code "
                   "WHERE d.oee_bucket = 'PERFORMANCE' AND e.equipment_id = ?", ["${input.equipmentId}"]),
        sel1("pr", "SELECT COALESCE(SUM(a.quantity), 0) AS v FROM emc_material_actual a "
                   "JOIN emc_job_response r ON r.response_id = a.response_id "
                   "JOIN emc_job_order o ON o.job_no = r.job_no "
                   "WHERE a.material_use = 'PRODUCED' AND o.equipment_id = ?", ["${input.equipmentId}"]),
        sel1("df", "SELECT COALESCE(SUM(COALESCE(d.qty_confirmed, d.qty_declared)), 0) AS v "
                   "FROM emc_defect_record d JOIN emc_job_order o ON o.job_no = d.job_no "
                   "WHERE d.status IN ('CONFIRMED', 'CLOSED') AND o.equipment_id = ?", ["${input.equipmentId}"]),
        ex("DELETE FROM emc_oee_shift WHERE equipment_id = ? AND shift_label = ?",
           ["${input.equipmentId}", "${input.shiftLabel}"]),
        ex("INSERT INTO emc_oee_shift (id, equipment_id, shift_label, planned_min, availability_loss_min, "
           "performance_loss_min, produced_qty, good_qty, availability_pct, performance_pct, quality_pct, oee_pct) "
           "SELECT gen_random_uuid(), ?, ?, p.planned, p.av, p.pf, p.pr, p.good, p.a_pct, p.p_pct, p.q_pct, "
           "ROUND(p.a_pct * p.p_pct * p.q_pct / 10000, 2) FROM ("
           "SELECT b.planned, b.av, b.pf, b.pr, GREATEST(b.pr - b.df, 0) AS good, "
           "CASE WHEN b.planned > 0 THEN ROUND(100 * (b.planned - b.av) / b.planned, 2) ELSE 0 END AS a_pct, "
           "CASE WHEN b.planned - b.av > 0 THEN ROUND(100 * GREATEST(b.planned - b.av - b.pf, 0) / (b.planned - b.av), 2) ELSE 0 END AS p_pct, "
           "CASE WHEN b.pr > 0 THEN ROUND(100 * GREATEST(b.pr - b.df, 0) / b.pr, 2) ELSE 0 END AS q_pct "
           "FROM (SELECT CAST(COALESCE(NULLIF(?, ''), '480') AS NUMERIC) AS planned, "
           "CAST(? AS NUMERIC) AS av, CAST(? AS NUMERIC) AS pf, CAST(? AS NUMERIC) AS pr, "
           "CAST(? AS NUMERIC) AS df) b) p",
           ["${input.equipmentId}", "${input.shiftLabel}", "${input.plannedMinutes}",
            "${av.v}", "${pf.v}", "${pr.v}", "${df.v}"]),
        sel1("kpi", "SELECT availability_pct, performance_pct, quality_pct, oee_pct FROM emc_oee_shift "
                    "WHERE equipment_id = ? AND shift_label = ?",
             ["${input.equipmentId}", "${input.shiftLabel}"]),
        ret({"error_code": "OK", "error_message": "", "equipmentId": "${input.equipmentId}",
             "shiftLabel": "${input.shiftLabel}", "availabilityPct": "${kpi.availability_pct}",
             "performancePct": "${kpi.performance_pct}", "qualityPct": "${kpi.quality_pct}",
             "oeePct": "${kpi.oee_pct}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_oee_getKpi",
    [F("equipmentId")],
    OUT(F("equipmentId"), F("shiftLabel"), F("availabilityPct"), F("performancePct"),
        F("qualityPct"), F("oeePct")),
    [
        sel1("kpi", "SELECT shift_label, availability_pct, performance_pct, quality_pct, oee_pct "
                    "FROM emc_oee_shift WHERE equipment_id = ? ORDER BY calculated_at DESC LIMIT 1",
             ["${input.equipmentId}"]),
        when({"var": "kpi", "notNull": True}, [
            ret({"error_code": "OK", "error_message": "", "equipmentId": "${input.equipmentId}",
                 "shiftLabel": "${kpi.shift_label}", "availabilityPct": "${kpi.availability_pct}",
                 "performancePct": "${kpi.performance_pct}", "qualityPct": "${kpi.quality_pct}",
                 "oeePct": "${kpi.oee_pct}"}),
        ], [
            ret({"error_code": "OK", "error_message": "No OEE data calculated yet",
                 "equipmentId": "${input.equipmentId}", "shiftLabel": "",
                 "availabilityPct": "0", "performancePct": "0", "qualityPct": "0", "oeePct": "0"}),
        ]),
        # unreachable fallback (validator requires a top-level return)
        ret({"error_code": "OK", "error_message": "No OEE data calculated yet",
             "equipmentId": "${input.equipmentId}", "shiftLabel": "",
             "availabilityPct": "0", "performancePct": "0", "qualityPct": "0", "oeePct": "0"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_oee_listShifts",
    [F("equipmentId")],
    OUT(RL("rows", [F("equipmentId"), F("shiftLabel"), F("plannedMin"), F("availabilityLossMin"),
                    F("performanceLossMin"), F("producedQty"), F("goodQty"), F("availabilityPct"),
                    F("performancePct"), F("qualityPct"), F("oeePct"), F("calculatedAt")])),
    [
        selN("shifts",
             "SELECT equipment_id, shift_label, planned_min, availability_loss_min, performance_loss_min, "
             "produced_qty, good_qty, availability_pct, performance_pct, quality_pct, oee_pct, calculated_at "
             "FROM emc_oee_shift WHERE (? = '' OR equipment_id = ?) ORDER BY calculated_at DESC",
             ["${input.equipmentId}", "${input.equipmentId}"]),
        map_rows("rows", "${shifts}", {
            "equipmentId": "${item.equipment_id}", "shiftLabel": "${item.shift_label}",
            "plannedMin": "${item.planned_min}", "availabilityLossMin": "${item.availability_loss_min}",
            "performanceLossMin": "${item.performance_loss_min}", "producedQty": "${item.produced_qty}",
            "goodQty": "${item.good_qty}", "availabilityPct": "${item.availability_pct}",
            "performancePct": "${item.performance_pct}", "qualityPct": "${item.quality_pct}",
            "oeePct": "${item.oee_pct}", "calculatedAt": "${item.calculated_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

# ----------------------------------------------------------------------------
# Blueprints (variable schemas) + live objects
# ----------------------------------------------------------------------------

LONG_VAR = {"name": "longValue", "fields": [{"name": "value", "type": "LONG"}]}
TEXT_VAR = {"name": "stringValue", "fields": [{"name": "value", "type": "STRING"}]}

def BPV(name, description, group, schema, default):
    return {
        "name": name,
        "description": description,
        "group": group,
        "schema": schema,
        "readable": True,
        "writable": True,
        "defaultValue": {"schema": schema, "rows": [{"value": default}]},
    }

BLUEPRINTS = [
    {
        "name": "erp-mes-core-hub-v1",
        "description": "ERP-MES Core Hub (ISA-95): KPI counters, alert rules, schedules and BFF functions.",
        "type": "SINGLETON",
        "variables": [
            BPV("pendingOutboxCount", "Pending ERP outbox messages", "integration", LONG_VAR, 0),
            BPV("activeDowntimeCount", "Open availability (downtime) events", "oee", LONG_VAR, 0),
            BPV("lowStockCount", "Stock lots below minimum quantity", "inventory", LONG_VAR, 0),
        ],
    },
    {
        "name": "emc-work-unit-v1",
        "description": "Work Unit (ISA-95 equipment): machine state mirrored from job-order lifecycle.",
        "type": "MIXIN",
        "targetObjectType": "DEVICE",
        "variables": [
            BPV("status", "Machine status (IDLE/RUNNING/PAUSED)", "runtime", TEXT_VAR, "IDLE"),
            BPV("speed", "Last reported speed / rate", "runtime", TEXT_VAR, "0"),
            BPV("activeJobOrderId", "Job order currently dispatched to this unit", "runtime", TEXT_VAR, ""),
        ],
    },
]

OBJECTS = [
    {"parentPath": "root.platform.devices", "name": "emc-wu-a01", "type": "DEVICE",
     "displayName": "Work Unit A01 (Assembly)",
     "description": "Assembly work unit A01 (ISA-95 equipment WU-A01).",
     "templateId": "emc-work-unit-v1"},
    {"parentPath": "root.platform.devices", "name": "emc-wu-a02", "type": "DEVICE",
     "displayName": "Work Unit A02 (Packing)",
     "description": "Packing work unit A02 (ISA-95 equipment WU-A02).",
     "templateId": "emc-work-unit-v1"},
]

# ----------------------------------------------------------------------------
# Dashboards, reports
# ----------------------------------------------------------------------------

def _report_widget(key, title, x, y, w, h, report_path, **opts):
    wgt = {"id": key, "type": "report", "title": title, "x": x, "y": y, "w": w, "h": h,
           "reportPath": report_path}
    wgt.update(opts)
    return wgt


def _form_widget(key, title, x, y, w, h, function_name, fields, button_label, object_path=None, **opts):
    wgt = {"id": key, "type": "function-form", "title": title, "x": x, "y": y, "w": w, "h": h,
           "objectPath": object_path or HUB, "functionName": function_name,
           "buttonLabel": button_label, "fieldsJson": json.dumps(fields, ensure_ascii=False)}
    wgt.update(opts)
    return wgt


def _func_widget(key, title, x, y, w, h, function_name, button_label, input_map, object_path=None):
    return {"id": key, "type": "function", "title": title, "x": x, "y": y, "w": w, "h": h,
            "objectPath": object_path or HUB, "functionName": function_name,
            "buttonLabel": button_label, "inputJson": json.dumps(input_map, ensure_ascii=False)}


def _html_widget(key, title, x, y, w, h, html):
    return {"id": key, "type": "html-snippet", "title": title, "x": x, "y": y, "w": w, "h": h,
            "htmlJson": html}


def _job_actions_widget(key, title, x, y, w, h, person_default, object_path=None):
    """Status-aware job action bar (ISA-95 dispatch state machine): one widget with
    Start/Pause/Resume/Complete buttons, each enabled only when the selected job's
    dispatch_status (session param `dispatchStatus`, written by the job table on row
    click) allows the transition. Server-side functions validate the same rules."""
    def btn(label, fn, equals, extra=None, confirm=None):
        b = {"label": label, "functionName": fn,
             "inputJson": json.dumps(dict({"jobNo": "${param:jobNo}"}, **(extra or {}))),
             "enabledWhenJson": json.dumps({"paramKey": "dispatchStatus", "equals": equals})}
        if confirm:
            b["confirmMessage"] = confirm
        return b
    buttons = [
        btn("Запустить", "emc_joborder_start", ["ALLOWED"], {"personId": person_default}),
        btn("Пауза", "emc_joborder_pause", ["RUNNING"]),
        btn("Возобновить", "emc_joborder_resume", ["SUSPENDED"]),
        btn("Завершить", "emc_joborder_complete", ["RUNNING"], confirm="Завершить сменное задание?"),
    ]
    return {"id": key, "type": "function", "title": title, "x": x, "y": y, "w": w, "h": h,
            "objectPath": object_path or HUB, "buttonsJson": json.dumps(buttons, ensure_ascii=False)}


_JOB_STATUS_LEGEND_HTML = (
    "<p>Кнопки активируются статусом выбранного задания (ISA-95):</p>"
    "<ul><li><b>Запустить</b> — ALLOWED</li>"
    "<li><b>Пауза</b> — RUNNING</li>"
    "<li><b>Возобновить</b> — SUSPENDED</li>"
    "<li><b>Завершить</b> — RUNNING, без открытых дефектов</li></ul>"
)


def _value_widget(key, title, x, y, w, h, variable, decimals=0, unit=None, object_path=None):
    wgt = {"id": key, "type": "value", "title": title, "x": x, "y": y, "w": w, "h": h,
           "objectPath": object_path or HUB, "variableName": variable, "valueField": "value",
           "decimals": decimals}
    if unit:
        wgt["unit"] = unit
    return wgt


def _sel(name, label, report, value_field, label_field=None, default=None, required=False, hint=None):
    """Select field fed by a (possibly cross-bundle) catalog report."""
    f = {"name": name, "label": label, "type": "select",
         "optionsFromReport": "root.platform.reports." + report,
         "optionsValueField": value_field}
    if label_field:
        f["optionsLabelField"] = label_field
    if default is not None:
        f["defaultValue"] = default
    if required:
        f["required"] = True
    if hint:
        f["hint"] = hint
    return f


def _static(name, label, options, default=None, required=False):
    f = {"name": name, "label": label, "type": "select", "staticOptions": options}
    if default is not None:
        f["defaultValue"] = default
    if required:
        f["required"] = True
    return f


# Extra grid height for function-form widgets, calibrated against the real
# web-console renderer (Playwright measurement of scrollHeight vs clientHeight
# on the live stand, +2 rows of safety margin for label wrapping at other
# viewport widths). 1 grid row = 8px + 4px margin = 12px.
_FORM_H_BOOST = {
    "Списать материал": 5, "Поставить лот на линию": 7, "Произвести материал": 12,
    "Сбор данных (OPC 10031-4)": 4,
    "Зарегистрировать лот": 12, "На линию": 7, "Создать ERP-документ": 8,
    "Зарегистрировать дефект": 6, "Подтвердить дефект": 10, "Закрыть дефект": 10,
    "Зарегистрировать событие/простой": 6, "Закрыть событие": 10,
    "Рассчитать OEE смены": 12,
}


def _autopack(widgets):
    """Push widgets down where a grown widget would overlap them (never up)."""
    placed = []
    for w in sorted(widgets, key=lambda k: (k["y"], k["x"])):
        ny = w["y"]
        for o in placed:
            if o["x"] < w["x"] + w["w"] and w["x"] < o["x"] + o["w"]:
                ny = max(ny, o["y"] + o["h"])
        w["y"] = ny
        placed.append(w)
    return widgets


def _dashboard(path, title, description, widgets):
    for w in widgets:
        if w.get("type") == "function-form" and w.get("title") in _FORM_H_BOOST:
            w["h"] += _FORM_H_BOOST[w["title"]]
    _autopack(widgets)
    return {"path": path, "title": title,
            "layoutJson": json.dumps({"columns": 84, "rowHeight": 8, "widgets": widgets})}


REPORTS = [
    {"reportId": "emc-job-board", "title": "Job Board (ISA-95 Job Orders)",
     "description": "Active job orders with dispatch status (Part 4 job list).",
     "query": """
SELECT o.job_order_id AS id, o.job_no, COALESCE(s.external_ref, '') AS external_ref,
       COALESCE(o.command, '') AS command, o.dispatch_status, o.equipment_id,
       COALESCE(r.product_definition_id, '') AS product_definition_id, r.quantity, COALESCE(r.uom, '') AS uom,
       o.planned_start, o.planned_end, o.created_at
FROM emc_job_order o
JOIN emc_work_request r ON r.request_id = o.request_id
JOIN emc_work_schedule s ON s.schedule_id = r.schedule_id
WHERE o.dispatch_status NOT IN ('ENDED', 'ABORTED', 'CANCELLED')
ORDER BY o.planned_start ASC, o.job_no
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("id", "ID"), ("job_no", "Job #"), ("external_ref", "ERP Ref"), ("command", "Cmd"),
         ("dispatch_status", "Status"), ("equipment_id", "Equipment"), ("product_definition_id", "Product"),
         ("quantity", "Qty"), ("uom", "UOM"), ("planned_start", "Planned Start"), ("planned_end", "Planned End"),
         ("created_at", "Created")]]},
    {"reportId": "emc-stock-report", "title": "Inventory Stock (Material Lots)",
     "description": "Material lots with quantity, status and location (Part 2 material lot).",
     "query": """
SELECT l.lot_id, l.barcode, l.definition_id AS material_id, COALESCE(d.class_id, '') AS class_id,
       l.quantity, l.base_uom AS uom, l.status, COALESCE(l.storage_location, '') AS storage_location
FROM emc_material_lot l JOIN emc_material_definition d ON d.definition_id = l.definition_id
ORDER BY l.lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("lot_id", "Lot ID"), ("barcode", "Barcode"), ("material_id", "Material"), ("class_id", "Class"),
         ("quantity", "Qty"), ("uom", "UOM"), ("status", "Status"), ("storage_location", "Location")]]},
    {"reportId": "emc-material-movement", "title": "Material Movement (Material Actual)",
     "description": "Consumed / produced / moved / scrapped material actuals per job order (Part 4).",
     "query": """
SELECT a.recorded_at, r.job_no, a.material_use, COALESCE(a.lot_id, '') AS lot_id,
       COALESCE(a.definition_id, '') AS material_id, a.quantity, COALESCE(a.uom, '') AS uom
FROM emc_material_actual a
JOIN emc_job_response r ON r.response_id = a.response_id
ORDER BY a.recorded_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("recorded_at", "Time"), ("job_no", "Job #"), ("material_use", "Use"), ("lot_id", "Lot"),
         ("material_id", "Material"), ("quantity", "Qty"), ("uom", "UOM")]]},
    {"reportId": "emc-defect-report", "title": "Quality Defects",
     "description": "Defect log with QA workflow status (Part 3 quality operations).",
     "query": """
SELECT defect_no, job_no, defect_type_id, qty_declared, severity, status,
       COALESCE(reason_code, '') AS reason_code, COALESCE(created_by, '') AS created_by, created_at
FROM emc_defect_record
ORDER BY created_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("defect_no", "Defect #"), ("job_no", "Job #"), ("defect_type_id", "Type"), ("qty_declared", "Qty"),
         ("severity", "Severity"), ("status", "Status"), ("reason_code", "Reason"), ("created_by", "By"),
         ("created_at", "Created")]]},
    {"reportId": "emc-oee-shift-report", "title": "OEE by Shift (ISO 22400 subset)",
     "description": "Availability / Performance / Quality / OEE per equipment per shift.",
     "query": """
SELECT equipment_id, shift_label, planned_min, availability_loss_min, performance_loss_min,
       produced_qty, good_qty, availability_pct, performance_pct, quality_pct, oee_pct, calculated_at
FROM emc_oee_shift
ORDER BY calculated_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("equipment_id", "Equipment"), ("shift_label", "Shift"), ("planned_min", "Planned min"),
         ("availability_loss_min", "Avail. loss min"), ("performance_loss_min", "Perf. loss min"),
         ("produced_qty", "Produced"), ("good_qty", "Good"),
         ("availability_pct", "A %"), ("performance_pct", "P %"), ("quality_pct", "Q %"), ("oee_pct", "OEE %"),
         ("calculated_at", "Calculated")]]},
    # --- Catalog reports: option sources for form dropdowns (code = value, name = label) ---
    {"reportId": "emc-material-catalog", "title": "Material Catalog",
     "description": "Material definitions as code/name options for form dropdowns.",
     "query": """
SELECT definition_id AS code, COALESCE(description, '') AS name,
       COALESCE(class_id, '') AS class_id, kind, base_uom
FROM emc_material_definition
ORDER BY definition_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("class_id", "Class"), ("kind", "Kind"), ("base_uom", "UOM")]]},
    {"reportId": "emc-equipment-catalog", "title": "Equipment Catalog",
     "description": "Equipment hierarchy as code/name options for form dropdowns.",
     "query": """
SELECT equipment_id AS code, COALESCE(description, '') AS name,
       equipment_level, COALESCE(parent_id, '') AS parent_id
FROM emc_equipment
ORDER BY hierarchy_path
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("equipment_level", "Level"), ("parent_id", "Parent")]]},
    {"reportId": "emc-person-catalog", "title": "Personnel Catalog",
     "description": "Persons as code/name options for form dropdowns.",
     "query": """
SELECT person_id AS code, person_name AS name, COALESCE(personnel_class_id, '') AS personnel_class_id
FROM emc_person
ORDER BY person_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("personnel_class_id", "Class")]]},
    {"reportId": "emc-defect-type-catalog", "title": "Defect Type Catalog",
     "description": "Defect types as code/name options for form dropdowns.",
     "query": """
SELECT defect_type_id AS code, COALESCE(description, '') AS name, COALESCE(category, '') AS category
FROM emc_defect_type
ORDER BY defect_type_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("category", "Category")]]},
    {"reportId": "emc-reason-code-catalog", "title": "Reason Code Catalog",
     "description": "Reason codes as code/name options for form dropdowns.",
     "query": """
SELECT reason_code AS code, COALESCE(description, '') AS name
FROM emc_reason_code
ORDER BY reason_code
""",
     "columns": [{"field": f, "label": l} for f, l in [("code", "Code"), ("name", "Name")]]},
    {"reportId": "emc-eventdef-catalog", "title": "Event Definition Catalog",
     "description": "Operations event definitions as code/name options for form dropdowns.",
     "query": """
SELECT code, name, event_class, oee_bucket
FROM emc_operations_event_definition
ORDER BY sort_order
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("event_class", "Class"), ("oee_bucket", "OEE Bucket")]]},
    {"reportId": "emc-shift-catalog", "title": "Shift Catalog",
     "description": "Calendar shifts as code/name options for form dropdowns.",
     "query": """
SELECT shift_id AS code, shift_label AS name, equipment_id, state
FROM emc_work_calendar
ORDER BY shift_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Shift"), ("equipment_id", "Equipment"), ("state", "State")]]},
    {"reportId": "emc-event-journal", "title": "Operations Event Journal",
     "description": "Registered operations events with definition names and OEE buckets.",
     "query": """
SELECT e.event_id AS id, e.definition_code, d.name, d.oee_bucket,
       COALESCE(e.job_no, '') AS job_no, COALESCE(e.equipment_id, '') AS equipment_id,
       e.time_min, e.status, e.started_at, e.ended_at
FROM emc_operations_event e
JOIN emc_operations_event_definition d ON d.code = e.definition_code
ORDER BY e.started_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("id", "ID"), ("definition_code", "Code"), ("name", "Name"), ("oee_bucket", "OEE Bucket"),
         ("job_no", "Job #"), ("equipment_id", "Equipment"), ("time_min", "Time min"),
         ("status", "Status"), ("started_at", "Started"), ("ended_at", "Ended")]]},
    {"reportId": "emc-genealogy-edges", "title": "Lot Genealogy Edges",
     "description": "Lot→lot edges for the selected lot (direct incident edges).",
     "parameters": ["lotId"],
     "query": """
SELECT g.input_lot_id, COALESCE(li.definition_id, '') AS input_material,
       g.output_lot_id, COALESCE(lo.definition_id, '') AS output_material,
       g.quantity, g.created_at
FROM emc_lot_genealogy g
LEFT JOIN emc_material_lot li ON li.lot_id = g.input_lot_id
LEFT JOIN emc_material_lot lo ON lo.lot_id = g.output_lot_id
WHERE ? <> '' AND (g.input_lot_id = ? OR g.output_lot_id = ?)
ORDER BY g.created_at, g.input_lot_id, g.output_lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("input_lot_id", "Input Lot"), ("input_material", "Input Material"),
         ("output_lot_id", "Output Lot"), ("output_material", "Output Material"),
         ("quantity", "Qty"), ("created_at", "Created")]]},
    {"reportId": "emc-genealogy-upstream-fg", "title": "Reverse Trace (selected lot → Raw)",
     "description": "Multi-level UPSTREAM tree for the selected lotId only.",
     "parameters": ["lotId"],
     "query": """
WITH RECURSIVE upstream (root_lot, lot_id, linked_from_lot_id, quantity, definition_id, depth, path) AS (
  SELECT g.output_lot_id, g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''), 1,
         CONCAT(g.output_lot_id, '>', g.input_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE ? <> '' AND g.output_lot_id = ?
  UNION ALL
  SELECT u.root_lot, g.input_lot_id, g.output_lot_id, g.quantity, COALESCE(l.definition_id, ''),
         u.depth + 1, CONCAT(u.path, '>', g.input_lot_id)
  FROM upstream u
  JOIN emc_lot_genealogy g ON g.output_lot_id = u.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.input_lot_id
  WHERE u.depth < 15
)
SELECT root_lot, depth, lot_id, definition_id AS material_id, linked_from_lot_id, quantity, path
FROM upstream
ORDER BY depth, lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("root_lot", "Root Lot"), ("depth", "Level"), ("lot_id", "Lot"), ("material_id", "Material"),
         ("linked_from_lot_id", "From Lot"), ("quantity", "Qty"), ("path", "Path")]]},
    {"reportId": "emc-genealogy-downstream-raw", "title": "Forward Trace (selected lot → FG)",
     "description": "Multi-level DOWNSTREAM tree for the selected lotId only.",
     "parameters": ["lotId"],
     "query": """
WITH RECURSIVE downstream (root_lot, lot_id, linked_from_lot_id, quantity, definition_id, depth, path) AS (
  SELECT g.input_lot_id, g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''), 1,
         CONCAT(g.input_lot_id, '>', g.output_lot_id)
  FROM emc_lot_genealogy g
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE ? <> '' AND g.input_lot_id = ?
  UNION ALL
  SELECT d.root_lot, g.output_lot_id, g.input_lot_id, g.quantity, COALESCE(l.definition_id, ''),
         d.depth + 1, CONCAT(d.path, '>', g.output_lot_id)
  FROM downstream d
  JOIN emc_lot_genealogy g ON g.input_lot_id = d.lot_id
  LEFT JOIN emc_material_lot l ON l.lot_id = g.output_lot_id
  WHERE d.depth < 15
)
SELECT root_lot, depth, lot_id, definition_id AS material_id, linked_from_lot_id, quantity, path
FROM downstream
ORDER BY depth, lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("root_lot", "Root Lot"), ("depth", "Level"), ("lot_id", "Lot"), ("material_id", "Material"),
         ("linked_from_lot_id", "From Lot"), ("quantity", "Qty"), ("path", "Path")]]},
    {"reportId": "emc-genealogy-lot-catalog", "title": "Genealogy Lot Catalog",
     "description": "Selectable demo lots for genealogy (FG / WIP / RAW).",
     "query": """
SELECT lot_id, barcode, definition_id AS material_id, status, storage_location, quantity, base_uom
FROM emc_material_lot
WHERE lot_id LIKE 'LOT-FG-%' OR lot_id LIKE 'LOT-WIP-%' OR lot_id LIKE 'LOT-RAW-%'
ORDER BY CASE WHEN lot_id LIKE 'LOT-FG-%' THEN 1 WHEN lot_id LIKE 'LOT-WIP-%' THEN 2 ELSE 3 END, lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("lot_id", "Lot"), ("barcode", "Barcode"), ("material_id", "Material"),
         ("status", "Status"), ("storage_location", "Location"),
         ("quantity", "Qty"), ("base_uom", "UOM")]]},
    {"reportId": "emc-job-lot-link-report", "title": "Job ↔ Lot Links",
     "description": "Produced FG lots per job (select job → sets lotId for genealogy).",
     "query": """
SELECT j.job_no, j.lot_id, j.link_role,
       COALESCE(l.definition_id, '') AS material_id, COALESCE(l.status, '') AS lot_status,
       COALESCE(o.dispatch_status, '') AS dispatch_status
FROM emc_job_lot_link j
LEFT JOIN emc_material_lot l ON l.lot_id = j.lot_id
LEFT JOIN emc_job_order o ON o.job_no = j.job_no
WHERE j.link_role = 'PRODUCED' AND j.lot_id LIKE 'LOT-FG-%'
ORDER BY j.job_no, j.lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("job_no", "Job"), ("lot_id", "Lot"), ("link_role", "Role"),
         ("material_id", "Material"), ("lot_status", "Lot Status"),
         ("dispatch_status", "Job Status")]]},
    {"reportId": "emc-genealogy-mass-balance", "title": "Genealogy Mass Balance (Actuals)",
     "description": "Consumed vs produced quantities from material actuals (mass-balance view).",
     "query": """
SELECT COALESCE(a.definition_id, '') AS material_id, a.material_use,
       SUM(a.quantity) AS qty, COALESCE(MAX(a.uom), '') AS uom,
       COUNT(*) AS rows_n
FROM emc_material_actual a
GROUP BY a.definition_id, a.material_use
ORDER BY a.definition_id, a.material_use
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("material_id", "Material"), ("material_use", "Use"),
         ("qty", "Qty"), ("uom", "UOM"), ("rows_n", "Rows")]]},
    {"reportId": "emc-mom-activity-matrix", "title": "MOM Activity Matrix (IEC 62264-3)",
     "description": "Part 3 4×8 activity coverage: Production/Quality/Inventory/Maintenance × 8 activities.",
     "query": """
SELECT domain, activity, status, COALESCE(note, '') AS note, COALESCE(ui_link, '') AS ui_link
FROM emc_mom_activity
ORDER BY CASE domain
  WHEN 'PRODUCTION' THEN 1 WHEN 'QUALITY' THEN 2 WHEN 'INVENTORY' THEN 3 ELSE 4 END,
  CASE activity
  WHEN 'DEFINITION' THEN 1 WHEN 'RESOURCE' THEN 2 WHEN 'DETAILED_SCHEDULING' THEN 3
  WHEN 'DISPATCHING' THEN 4 WHEN 'EXECUTION' THEN 5 WHEN 'DATA_COLLECTION' THEN 6
  WHEN 'TRACKING' THEN 7 ELSE 8 END
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("domain", "Domain"), ("activity", "Activity"), ("status", "Status"),
         ("note", "Note"), ("ui_link", "UI")]]},
    {"reportId": "emc-physical-asset-report", "title": "Physical Assets (Part 2)",
     "description": "Physical asset register linked to equipment hierarchy.",
     "query": """
SELECT a.asset_id, COALESCE(a.class_id, '') AS class_id, COALESCE(a.equipment_id, '') AS equipment_id,
       COALESCE(a.serial_no, '') AS serial_no, COALESCE(a.manufacturer, '') AS manufacturer,
       COALESCE(a.description, '') AS description, a.status
FROM emc_physical_asset a ORDER BY a.asset_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("asset_id", "Asset"), ("class_id", "Class"), ("equipment_id", "Equipment"),
         ("serial_no", "Serial"), ("manufacturer", "OEM"), ("description", "Description"),
         ("status", "Status")]]},
    {"reportId": "emc-product-definition-report", "title": "Product Definitions (Part 2)",
     "description": "Product definitions with FG material and process segments.",
     "query": """
SELECT p.product_id, COALESCE(p.description, '') AS description,
       COALESCE(p.fg_definition_id, '') AS fg_definition_id, p.status,
       (SELECT COUNT(*) FROM emc_product_segment ps WHERE ps.product_id = p.product_id) AS segments
FROM emc_product_definition p ORDER BY p.product_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("product_id", "Product"), ("description", "Description"),
         ("fg_definition_id", "FG Material"), ("status", "Status"), ("segments", "Segments")]]},
    {"reportId": "emc-ops-capability-report", "title": "Operations Capability (Part 4)",
     "description": "What each work unit is capable of running.",
     "query": """
SELECT capability_id, operations_type, COALESCE(equipment_id, '') AS equipment_id,
       COALESCE(segment_id, '') AS segment_id, COALESCE(reason, '') AS reason, status
FROM emc_operations_capability ORDER BY capability_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("capability_id", "Capability"), ("operations_type", "Type"),
         ("equipment_id", "Equipment"), ("segment_id", "Segment"),
         ("reason", "Reason"), ("status", "Status")]]},
    {"reportId": "emc-capability-test-report", "title": "Capability Test Results (Part 2)",
     "description": "Capability test specs and latest results.",
     "query": """
SELECT s.spec_id, s.target_kind, s.target_id, s.test_name, COALESCE(s.criterion, '') AS criterion,
       COALESCE(r.measured_value, '') AS measured_value, COALESCE(r.result, '') AS result,
       r.tested_at
FROM emc_capability_test_spec s
LEFT JOIN emc_capability_test_result r ON r.spec_id = s.spec_id
ORDER BY s.spec_id, r.tested_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("spec_id", "Spec"), ("target_kind", "Target Kind"), ("target_id", "Target"),
         ("test_name", "Test"), ("criterion", "Criterion"), ("measured_value", "Measured"),
         ("result", "Result"), ("tested_at", "Tested At")]]},
    {"reportId": "emc-operational-location-report", "title": "Operational Locations (Part 2)",
     "description": "Operational locations linked to equipment / storage hierarchy.",
     "query": """
SELECT location_id, COALESCE(description, '') AS description, location_kind,
       COALESCE(equipment_id, '') AS equipment_id, COALESCE(parent_location_id, '') AS parent_location_id,
       status
FROM emc_operational_location ORDER BY location_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("location_id", "Location"), ("description", "Description"), ("location_kind", "Kind"),
         ("equipment_id", "Equipment"), ("parent_location_id", "Parent"), ("status", "Status")]]},
    {"reportId": "emc-domain-schedule-report", "title": "Domain Schedules (Part 3)",
     "description": "Detailed scheduling across Production / Quality / Inventory / Maintenance.",
     "query": """
SELECT schedule_id, domain, schedule_kind, COALESCE(target_id, '') AS target_id,
       quantity, COALESCE(uom, '') AS uom, status, COALESCE(note, '') AS note
FROM emc_domain_schedule
ORDER BY CASE domain
  WHEN 'PRODUCTION' THEN 1 WHEN 'QUALITY' THEN 2 WHEN 'INVENTORY' THEN 3 ELSE 4 END,
  schedule_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("schedule_id", "Schedule"), ("domain", "Domain"), ("schedule_kind", "Kind"),
         ("target_id", "Target"), ("quantity", "Qty"), ("uom", "UOM"),
         ("status", "Status"), ("note", "Note")]]},
    {"reportId": "emc-qa-defect-rate-report", "title": "Quality Defect Rate KPI",
     "description": "Defect counts and share of jobs with defects (Part 3 performance analysis).",
     "query": """
SELECT COUNT(*) AS defect_count,
       COALESCE(SUM(COALESCE(qty_confirmed, qty_declared)), 0) AS confirmed_qty,
       COUNT(DISTINCT job_no) AS jobs_with_defects,
       (SELECT COUNT(*) FROM emc_job_order) AS job_count,
       CASE WHEN (SELECT COUNT(*) FROM emc_job_order) = 0 THEN 0
            ELSE ROUND(100.0 * COUNT(DISTINCT job_no) / (SELECT COUNT(*) FROM emc_job_order), 2)
       END AS defect_rate_pct
FROM emc_defect_record
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("defect_count", "Defects"), ("confirmed_qty", "Confirmed Qty"),
         ("jobs_with_defects", "Jobs w/ Defects"), ("job_count", "Jobs"),
         ("defect_rate_pct", "Defect Rate %")]]},
    {"reportId": "emc-inv-turns-report", "title": "Inventory Turns KPI",
     "description": "Approx inventory turns from stock vs consumed actuals.",
     "query": """
SELECT COALESCE((SELECT SUM(quantity) FROM emc_material_lot WHERE status = 'STOCK'), 0) AS stock_qty,
       COALESCE((SELECT SUM(quantity) FROM emc_material_actual WHERE material_use = 'CONSUMED'), 0) AS consumed_qty,
       COALESCE((SELECT SUM(quantity) FROM emc_material_actual WHERE material_use = 'PRODUCED'), 0) AS produced_qty,
       CASE WHEN COALESCE((SELECT SUM(quantity) FROM emc_material_lot WHERE status = 'STOCK'), 0) = 0 THEN 0
            ELSE ROUND(COALESCE((SELECT SUM(quantity) FROM emc_material_actual WHERE material_use = 'CONSUMED'), 0)
                 / (SELECT SUM(quantity) FROM emc_material_lot WHERE status = 'STOCK'), 3)
       END AS turns_approx
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("stock_qty", "Stock Qty"), ("consumed_qty", "Consumed"),
         ("produced_qty", "Produced"), ("turns_approx", "Turns ≈")]]},
    {"reportId": "emc-maint-mttr-report", "title": "Maintenance MTTR / MTBF",
     "description": "MTTR from closed downtime events; MTBF heuristic from shift calendar.",
     "query": """
SELECT COUNT(*) AS closed_events,
       COALESCE(SUM(time_min), 0) AS total_downtime_min,
       CASE WHEN COUNT(*) = 0 THEN 0 ELSE ROUND(COALESCE(SUM(time_min), 0) / COUNT(*), 2) END AS mttr_min,
       CASE WHEN COUNT(*) <= 1 THEN 480
            ELSE ROUND(480.0 * (SELECT COUNT(DISTINCT equipment_id) FROM emc_work_calendar) / COUNT(*), 2)
       END AS mtbf_min
FROM emc_operations_event
WHERE status = 'CLOSED' AND COALESCE(time_min, 0) > 0
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("closed_events", "Closed Events"), ("total_downtime_min", "Downtime Min"),
         ("mttr_min", "MTTR Min"), ("mtbf_min", "MTBF Min ≈")]]},
    {"reportId": "emc-rrn-networks", "title": "Resource Relationship Networks (Part 4)",
     "description": "GOST / IEC 62264-4 Resource Relationship Network headers.",
     "query": """
SELECT network_id, name, COALESCE(description, '') AS description,
       COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, status
FROM emc_resource_relationship_network ORDER BY network_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("network_id", "Network"), ("name", "Name"), ("description", "Description"),
         ("hierarchy_scope_id", "Scope"), ("status", "Status")]]},
    {"reportId": "emc-rrn-edges", "title": "RRN Edges (Part 4)",
     "description": "Resource relationships (equipment↔tool/container/software).",
     "query": """
SELECT rel_id, network_id, from_resource_type, from_resource_id,
       to_resource_type, to_resource_id, relationship_type, dependency
FROM emc_resource_relationship ORDER BY network_id, rel_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("rel_id", "Rel"), ("network_id", "Network"),
         ("from_resource_type", "From Type"), ("from_resource_id", "From"),
         ("to_resource_type", "To Type"), ("to_resource_id", "To"),
         ("relationship_type", "Type"), ("dependency", "Dependency")]]},
    {"reportId": "emc-container-report", "title": "Containers (Part 2 §5.6)",
     "description": "Container resources (GOST R IEC 62264-2).",
     "query": """
SELECT container_id, class_id, name, COALESCE(description, '') AS description,
       COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id,
       COALESCE(CAST(capacity AS VARCHAR), '') AS capacity,
       COALESCE(capacity_uom, '') AS capacity_uom, status
FROM emc_container ORDER BY container_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("container_id", "Container"), ("class_id", "Class"), ("name", "Name"),
         ("description", "Description"), ("hierarchy_scope_id", "Scope"),
         ("capacity", "Capacity"), ("capacity_uom", "UOM"), ("status", "Status")]]},
    {"reportId": "emc-tool-report", "title": "Tools (Part 2 §5.6)",
     "description": "Tool resources linked to equipment.",
     "query": """
SELECT tool_id, class_id, name, COALESCE(description, '') AS description,
       COALESCE(equipment_id, '') AS equipment_id,
       COALESCE(CAST(calibration_due AS VARCHAR), '') AS calibration_due, status
FROM emc_tool ORDER BY tool_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("tool_id", "Tool"), ("class_id", "Class"), ("name", "Name"),
         ("description", "Description"), ("equipment_id", "Equipment"),
         ("calibration_due", "Calibration Due"), ("status", "Status")]]},
    {"reportId": "emc-software-report", "title": "Software (Part 2 §5.6)",
     "description": "Software resources (MES agents / control software).",
     "query": """
SELECT software_id, class_id, name, COALESCE(vendor, '') AS vendor,
       COALESCE(version_label, '') AS version_label, status
FROM emc_software ORDER BY software_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("software_id", "Software"), ("class_id", "Class"), ("name", "Name"),
         ("vendor", "Vendor"), ("version_label", "Version"), ("status", "Status")]]},
    {"reportId": "emc-opsdef-report", "title": "Operations Definitions (Part 2)",
     "description": "First-class Operations Definition headers.",
     "query": """
SELECT definition_id, version, name, COALESCE(description, '') AS description,
       published_flag, status,
       (SELECT COUNT(*) FROM emc_operations_definition_segment s
        WHERE s.definition_id = d.definition_id AND s.version = d.version) AS segments
FROM emc_operations_definition d ORDER BY definition_id, version
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("definition_id", "Definition"), ("version", "Ver"), ("name", "Name"),
         ("description", "Description"), ("published_flag", "Published"),
         ("status", "Status"), ("segments", "Segments")]]},
    {"reportId": "emc-opssched-report", "title": "Operations Schedules (Part 2)",
     "description": "Operations Schedule + request count.",
     "query": """
SELECT s.schedule_id, s.name, s.state, COALESCE(s.description, '') AS description,
       (SELECT COUNT(*) FROM emc_operations_request r WHERE r.schedule_id = s.schedule_id) AS requests
FROM emc_operations_schedule s ORDER BY s.schedule_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("schedule_id", "Schedule"), ("name", "Name"), ("state", "State"),
         ("description", "Description"), ("requests", "Requests")]]},
    {"reportId": "emc-workcap-report", "title": "Work Capability (Part 4)",
     "description": "Work Capability headers (distinct from Operations Capability).",
     "query": """
SELECT capability_id, name, COALESCE(description, '') AS description,
       COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, status
FROM emc_work_capability ORDER BY capability_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("capability_id", "Capability"), ("name", "Name"), ("description", "Description"),
         ("hierarchy_scope_id", "Scope"), ("status", "Status")]]},
    {"reportId": "emc-wmc-report", "title": "Work Master Capability (Part 4)",
     "description": "Work Master ↔ Work Capability links.",
     "query": """
SELECT work_master_id, version, capability_id
FROM emc_work_master_capability ORDER BY work_master_id, version, capability_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("work_master_id", "Work Master"), ("version", "Ver"),
         ("capability_id", "Capability")]]},
    {"reportId": "emc-work-alert-report", "title": "Work Alerts (Part 4)",
     "description": "Work Alert register.",
     "query": """
SELECT alert_id, alert_type, severity, COALESCE(work_master_id, '') AS work_master_id,
       message, status, CAST(raised_at AS VARCHAR) AS raised_at,
       COALESCE(ack_by, '') AS ack_by
FROM emc_work_alert ORDER BY raised_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("alert_id", "Alert"), ("alert_type", "Type"), ("severity", "Severity"),
         ("work_master_id", "Work Master"), ("message", "Message"),
         ("status", "Status"), ("raised_at", "Raised"), ("ack_by", "Ack By")]]},
    {"reportId": "emc-kpi-value-report", "title": "Work KPI Values (ISO 22400)",
     "description": "Calculated / seeded KPI values (Part 4 Work KPI).",
     "query": """
SELECT v.kpi_code, d.name, COALESCE(v.scope_id, '') AS scope_id,
       COALESCE(v.period_label, '') AS period_label, v.value_num,
       CAST(v.calculated_at AS VARCHAR) AS calculated_at
FROM emc_kpi_value v
LEFT JOIN emc_kpi_definition d ON d.kpi_code = v.kpi_code
ORDER BY v.calculated_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("kpi_code", "KPI"), ("name", "Name"), ("scope_id", "Scope"),
         ("period_label", "Period"), ("value_num", "Value"),
         ("calculated_at", "Calculated")]]},
    {"reportId": "emc-maint-request-report", "title": "Maintenance Requests",
     "description": "Maintenance requests and linked work orders.",
     "query": """
SELECT r.request_id, r.equipment_id, COALESCE(r.description, '') AS description,
       r.priority, r.status,
       COALESCE((SELECT w.wo_id FROM emc_maintenance_work_order w
                 WHERE w.request_id = r.request_id LIMIT 1), '') AS wo_id,
       CAST(r.created_at AS VARCHAR) AS created_at
FROM emc_maintenance_request r ORDER BY r.created_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("request_id", "Request"), ("equipment_id", "Equipment"),
         ("description", "Description"), ("priority", "Priority"),
         ("status", "Status"), ("wo_id", "WO"), ("created_at", "Created")]]},
    {"reportId": "emc-invdoc-report", "title": "Inventory Documents",
     "description": "Inventory movement documents (Part 3 Inventory).",
     "query": """
SELECT d.doc_id, d.kind, d.status,
       (SELECT COUNT(*) FROM emc_inventory_document_line l WHERE l.doc_id = d.doc_id) AS lines,
       COALESCE(d.operator_person_id, '') AS operator_person_id,
       CAST(d.created_at AS VARCHAR) AS created_at
FROM emc_inventory_document d ORDER BY d.created_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("doc_id", "Doc"), ("kind", "Kind"), ("status", "Status"),
         ("lines", "Lines"), ("operator_person_id", "Operator"),
         ("created_at", "Created")]]},
    {"reportId": "emc-qa-test-report", "title": "QA Test Results",
     "description": "Quality test results (Part 3 Quality).",
     "query": """
SELECT CAST(id AS VARCHAR) AS id, COALESCE(job_no, '') AS job_no,
       COALESCE(lot_id, '') AS lot_id, test_name, result,
       COALESCE(measurements_json, '') AS measurements_json,
       CAST(created_at AS VARCHAR) AS created_at
FROM emc_qa_test_result ORDER BY created_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("id", "ID"), ("job_no", "Job"), ("lot_id", "Lot"),
         ("test_name", "Test"), ("result", "Result"),
         ("measurements_json", "Measurements"), ("created_at", "Created")]]},
]

DASHBOARDS = [
    _dashboard("root.platform.dashboards.emc-dispatch", "Диспетчер производства (ISA-95)",
               "Доска сменных заданий: запуск, пауза, возобновление, завершение.",
               [
                   _value_widget("kpiDowntime", "Открыто простоев", 0, 0, 28, 12, "activeDowntimeCount"),
                   _value_widget("kpiOutbox", "Сообщений ERP в outbox", 28, 0, 28, 12, "pendingOutboxCount"),
                   _value_widget("kpiLowStock", "Лотов ниже минимума", 56, 0, 28, 12, "lowStockCount"),
                   _report_widget("jobs", "Сменные задания", 0, 12, 56, 49, "root.platform.reports.emc-job-board",
                                  selectable=True, rowSelectionKey="job_no",
                                  rowParamsFromRowJson=json.dumps({"jobNo": "job_no", "dispatchStatus": "dispatch_status"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["dispatch_status", "equipment_id"]),
                                  statusDotColumnsJson=json.dumps(["dispatch_status"])),
                   _job_actions_widget("jobActions", "Действия с заданием", 56, 12, 28, 14, "EMP-001"),
                   _html_widget("jobActionsHint", "Логика статусов (ISA-95)", 56, 26, 28, 30,
                                _JOB_STATUS_LEGEND_HTML),
               ]),
    _dashboard("root.platform.dashboards.emc-execution", "Исполнение и материалы",
               "Учёт материалов на линии: расход, постановка, производство, сбор данных.",
               [
                   _form_widget("consume", "Списать материал", 0, 0, 28, 16, "emc_matlot_consume",
                                [_sel("barcode", "Штрихкод лота", "emc-stock-report", "barcode", "material_id", required=True),
                                 {"name": "quantity", "label": "Количество", "type": "number", "defaultValue": "1"}],
                                "Списать"),
                   _form_widget("place", "Поставить лот на линию", 0, 16, 28, 16, "emc_matlot_placeOnLine",
                                [_sel("barcode", "Штрихкод лота", "emc-stock-report", "barcode", "material_id", required=True),
                                 _sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True)],
                                "Поставить"),
                   _form_widget("produce", "Произвести материал", 28, 0, 28, 32, "emc_matlot_produce",
                                [_sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True),
                                 {"name": "lotId", "label": "Новый лот (ID)", "type": "text", "required": True},
                                 {"name": "barcode", "label": "Штрихкод", "type": "text", "required": True},
                                 _sel("definitionId", "Материал", "emc-material-catalog", "code", "name", required=True),
                                 {"name": "quantity", "label": "Количество", "type": "number", "defaultValue": "1"},
                                 _sel("storageLocation", "Склад", "emc-equipment-catalog", "code", "name")],
                                "Произвести"),
                   _form_widget("dc", "Сбор данных (OPC 10031-4)", 56, 0, 28, 26, "emc_dc_recordQuantity",
                                [_sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True),
                                 _static("paramKey", "Параметр", ["GOOD_QTY", "REJECT_QTY", "RATE", "SPEED"], default="GOOD_QTY"),
                                 {"name": "paramValue", "label": "Значение", "type": "text", "defaultValue": "0"},
                                 _static("uom", "Единица", ["pcs", "kg", "m", "m2"], default="pcs")],
                                "Записать"),
                   _report_widget("moves", "Движение материалов", 0, 36, 84, 26, "root.platform.reports.emc-material-movement",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["material_use", "job_no"])),
               ]),
    _dashboard("root.platform.dashboards.emc-inventory", "Склад и документы ERP",
               "Остатки, постановка на линию и инвентарные документы ERP.",
               [
                   _report_widget("stock", "Остатки (лоты)", 0, 0, 56, 60, "root.platform.reports.emc-stock-report",
                                  selectable=True, rowSelectionKey="lot_id",
                                  rowParamsFromRowJson=json.dumps({"barcode": "barcode", "lotId": "lot_id"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["status", "class_id", "storage_location"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _form_widget("register", "Зарегистрировать лот", 56, 0, 28, 26, "emc_matlot_register",
                                [{"name": "lotId", "label": "Лот (ID)", "type": "text", "required": True},
                                 {"name": "barcode", "label": "Штрихкод", "type": "text", "required": True},
                                 _sel("definitionId", "Материал", "emc-material-catalog", "code", "name", required=True),
                                 _sel("storageLocation", "Склад", "emc-equipment-catalog", "code", "name"),
                                 {"name": "quantity", "label": "Количество", "type": "number", "defaultValue": "1"}],
                                "Зарегистрировать"),
                   _form_widget("place", "На линию", 56, 26, 28, 16, "emc_matlot_placeOnLine",
                                [_sel("barcode", "Штрихкод лота", "emc-stock-report", "barcode", "material_id", required=True),
                                 _sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True)],
                                "Поставить"),
                   _form_widget("invdoc", "Создать ERP-документ", 56, 42, 28, 18, "emc_invdoc_create",
                                [{"name": "docId", "label": "Документ №", "type": "text", "required": True},
                                 _static("kind", "Вид документа",
                                         ["DELIVERY_REQUEST", "RESOURCE_REQUEST", "STOCK_TAKING",
                                          "SCRAP_REQUEST", "RELEASE", "TRANSFER"], default="DELIVERY_REQUEST"),
                                 _sel("operatorPersonId", "Сотрудник", "emc-person-catalog", "code", "name", default="EMP-001")],
                                "Создать"),
                   _report_widget("invdocs", "Документы склада", 0, 60, 56, 22,
                                  "root.platform.reports.emc-invdoc-report",
                                  selectable=True, rowSelectionKey="doc_id",
                                  rowParamsFromRowJson=json.dumps({"docId": "doc_id"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["status", "kind"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _form_widget("submitDoc", "Submit документ", 56, 60, 28, 11,
                                "emc_invdoc_submit",
                                [{"name": "docId", "label": "Документ №", "type": "text",
                                  "defaultValue": "INV-DEMO-001", "required": True}],
                                "Submit"),
                   _form_widget("acceptDoc", "Accept документ", 56, 71, 28, 11,
                                "emc_invdoc_apply",
                                [{"name": "docId", "label": "Документ №", "type": "text",
                                  "defaultValue": "INV-DEMO-001", "required": True}],
                                "Accept"),
               ]),
    _dashboard("root.platform.dashboards.emc-quality", "Качество",
               "Регистрация дефектов и QA-поток (REGISTERED → CONFIRMED/REJECTED → CLOSED).",
               [
                   _report_widget("defects", "Дефекты", 0, 0, 56, 67, "root.platform.reports.emc-defect-report",
                                  selectable=True, rowSelectionKey="defect_no",
                                  rowParamsFromRowJson=json.dumps({"defectNo": "defect_no"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["status", "severity", "defect_type_id"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _form_widget("register", "Зарегистрировать дефект", 56, 0, 28, 41, "emc_qa_registerDefect",
                                [{"name": "defectNo", "label": "Дефект №", "type": "text", "required": True},
                                 _sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True),
                                 _sel("defectTypeId", "Тип дефекта", "emc-defect-type-catalog", "code", "name", required=True),
                                 _sel("reasonCode", "Код причины", "emc-reason-code-catalog", "code", "name"),
                                 _static("severity", "Критичность", ["MINOR", "MAJOR", "CRITICAL"], default="MINOR"),
                                 {"name": "qtyDeclared", "label": "Количество", "type": "number", "defaultValue": "1"},
                                 _sel("createdBy", "Сотрудник", "emc-person-catalog", "code", "name", default="EMP-001")],
                                "Зарегистрировать"),
                   _form_widget("confirm", "Подтвердить дефект", 56, 41, 28, 16, "emc_qa_confirmDefect",
                                [_sel("defectNo", "Дефект №", "emc-defect-report", "defect_no", "status", required=True),
                                 {"name": "by", "label": "Кем", "type": "text", "defaultValue": "qa"},
                                 _sel("reasonCode", "Код причины", "emc-reason-code-catalog", "code", "name")],
                                "Подтвердить"),
                   _form_widget("close", "Закрыть дефект", 56, 57, 28, 11, "emc_qa_closeDefect",
                                [_sel("defectNo", "Дефект №", "emc-defect-report", "defect_no", "status", required=True),
                                 {"name": "by", "label": "Кем", "type": "text", "defaultValue": "qa"}],
                                "Закрыть"),
                   _report_widget("qaTests", "QA Test Results", 0, 67, 56, 18,
                                  "root.platform.reports.emc-qa-test-report",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["result", "test_name"]),
                                  statusDotColumnsJson=json.dumps(["result"])),
                   _form_widget("recordQa", "Записать QA-тест", 56, 68, 28, 18,
                                "emc_qa_recordTestResult",
                                [{"name": "jobNo", "label": "Job", "type": "text", "defaultValue": ""},
                                 {"name": "lotId", "label": "Lot", "type": "text",
                                  "defaultValue": "LOT-FG-0001"},
                                 {"name": "testName", "label": "Test", "type": "text",
                                  "defaultValue": "Dimensional check", "required": True},
                                 _static("result", "Result", ["PASS", "FAIL"], default="PASS"),
                                 {"name": "measurementsJson", "label": "JSON", "type": "text",
                                  "defaultValue": "{}"}],
                                "Записать"),
               ]),
    _dashboard("root.platform.dashboards.emc-oee", "OEE и простои",
               "Журнал событий, регистрация простоев и расчёт OEE по сменам.",
               [
                   _form_widget("registerEvent", "Зарегистрировать событие/простой", 0, 0, 28, 36, "emc_event_register",
                                [_sel("definitionCode", "Код события", "emc-eventdef-catalog", "code", "name", required=True),
                                 _sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status"),
                                 _sel("equipmentId", "Оборудование", "emc-equipment-catalog", "code", "name"),
                                 {"name": "timeMin", "label": "Длительность, мин", "type": "number", "defaultValue": ""},
                                 {"name": "lengthM", "label": "Метраж, м", "type": "number", "defaultValue": ""},
                                 {"name": "comment", "label": "Комментарий", "type": "textarea", "defaultValue": ""},
                                 {"name": "by", "label": "Кем", "type": "text", "defaultValue": "operator"}],
                                "Зарегистрировать"),
                   _form_widget("closeEvent", "Закрыть событие", 0, 36, 28, 11, "emc_event_close",
                                [_sel("eventId", "Событие", "emc-event-journal", "id", "name", required=True),
                                 {"name": "by", "label": "Кем", "type": "text", "defaultValue": "operator"}],
                                "Закрыть"),
                   _form_widget("calc", "Рассчитать OEE смены", 28, 0, 28, 16, "emc_oee_calcShift",
                                [_sel("equipmentId", "Оборудование", "emc-equipment-catalog", "code", "name", required=True),
                                 _sel("shiftLabel", "Смена", "emc-shift-catalog", "code", required=True),
                                 {"name": "plannedMinutes", "label": "Плановые минуты", "type": "number", "defaultValue": "480"}],
                                "Рассчитать"),
                   _value_widget("kpiDowntime", "Открыто простоев", 56, 0, 28, 12, "activeDowntimeCount"),
                   _report_widget("journal", "Журнал событий", 28, 16, 56, 30, "root.platform.reports.emc-event-journal",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["status", "oee_bucket", "equipment_id"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("shifts", "KPI смен (OEE)", 0, 47, 84, 20, "root.platform.reports.emc-oee-shift-report"),
               ]),
    _dashboard("root.platform.dashboards.emc-genealogy", "Генеалогия партии",
               "Прослеживаемость партий: выберите лот или заказ — дерево только для него.",
               [
                   _html_widget("help", "Как пользоваться", 0, 0, 84, 10,
                                "<p>1) Выберите <b>лот</b> в каталоге (радио) — параметр <code>lotId</code>. "
                                "2) Или выберите <b>заказ</b> в Job↔Lot (FG produced). "
                                "3) Таблицы ниже показывают <b>только выбранный</b> лот "
                                "(↑ обратная / ↓ прямая цепочка и рёбра).</p>"),
                   _report_widget("lots", "Каталог лотов (выбрать)", 0, 10, 42, 26,
                                  "root.platform.reports.emc-genealogy-lot-catalog",
                                  selectable=True, rowSelectionKey="lot_id",
                                  rowParamsFromRowJson=json.dumps({"lotId": "lot_id"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["material_id", "status"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("jobLots", "Job ↔ Lot (выбрать заказ)", 42, 10, 42, 26,
                                  "root.platform.reports.emc-job-lot-link-report",
                                  selectable=True, rowSelectionKey="lot_id",
                                  rowParamsFromRowJson=json.dumps({
                                      "lotId": "lot_id", "jobNo": "job_no"}),
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["job_no", "link_role"]),
                                  statusDotColumnsJson=json.dumps(["dispatch_status"])),
                   _form_widget("trace", "Запрос генеалогии", 0, 36, 28, 22, "emc_track_genealogyTreeByLot",
                                [{"name": "lotId", "label": "Партия (лот)", "type": "text",
                                  "defaultValue": "${param:lotId}", "required": True},
                                 _static("direction", "Направление",
                                         ["BOTH", "UPSTREAM", "DOWNSTREAM"], default="UPSTREAM")],
                                "Построить дерево"),
                   _func_widget("demoFg1", "Демо FG-0001 ↑", 28, 36, 18, 11,
                                "emc_track_genealogyTreeByLot", "ГП→сырьё",
                                {"lotId": "LOT-FG-0001", "direction": "UPSTREAM"}),
                   _func_widget("demoFg2", "Демо FG-0002 ↑", 46, 36, 18, 11,
                                "emc_track_genealogyTreeByLot", "ГП→сырьё",
                                {"lotId": "LOT-FG-0002", "direction": "UPSTREAM"}),
                   _func_widget("demoFg3", "Демо FG-0003 ↑", 64, 36, 20, 11,
                                "emc_track_genealogyTreeByLot", "ГП→сырьё",
                                {"lotId": "LOT-FG-0003", "direction": "UPSTREAM"}),
                   _func_widget("demoRaw1", "Демо RAW-0001 ↓", 28, 47, 18, 11,
                                "emc_track_genealogyTreeByLot", "Сырьё→ГП",
                                {"lotId": "LOT-RAW-0001", "direction": "DOWNSTREAM"}),
                   _func_widget("demoRaw3", "Демо RAW-0003 ↓", 46, 47, 18, 11,
                                "emc_track_genealogyTreeByLot", "Сырьё→ГП",
                                {"lotId": "LOT-RAW-0003", "direction": "DOWNSTREAM"}),
                   _func_widget("demoRaw5", "Демо RAW-0005 ↓", 64, 47, 20, 11,
                                "emc_track_genealogyTreeByLot", "Сырьё→ГП",
                                {"lotId": "LOT-RAW-0005", "direction": "DOWNSTREAM"}),
                   _report_widget("upstream", "Обратная цепочка (выбранный лот)", 0, 58, 42, 28,
                                  "root.platform.reports.emc-genealogy-upstream-fg",
                                  contextParamsJson=json.dumps({"lotId": "lotId"}),
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["depth", "lot_id"])),
                   _report_widget("downstream", "Прямая цепочка (выбранный лот)", 42, 58, 42, 28,
                                  "root.platform.reports.emc-genealogy-downstream-raw",
                                  contextParamsJson=json.dumps({"lotId": "lotId"}),
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["depth", "lot_id"])),
                   _report_widget("edges", "Рёбра выбранного лота", 0, 86, 42, 22,
                                  "root.platform.reports.emc-genealogy-edges",
                                  contextParamsJson=json.dumps({"lotId": "lotId"}),
                                  filterable=True),
                   _report_widget("balance", "Масс-баланс (actuals)", 42, 86, 42, 22,
                                  "root.platform.reports.emc-genealogy-mass-balance"),
                   _report_widget("defects", "Дефекты (зона риска / QC)", 0, 108, 84, 20,
                                  "root.platform.reports.emc-defect-report",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["status", "severity", "job_no"])),
               ]),
    _dashboard("root.platform.dashboards.emc-mom-matrix", "MOM (IEC 62264-3)",
               "Матрица деятельности Level 3: Production / Quality / Inventory / Maintenance × 8 activities.",
               [
                   _html_widget("help", "ГОСТ Р МЭК 62264 / IEC 62264-3", 0, 0, 84, 12,
                                "<p><b>Part 3</b> — модель деятельности MOM (4×8). "
                                "Статусы ячеек: <code>COVERED</code>. "
                                "Detailed Scheduling — <code>emc_domain_schedule</code>; "
                                "Performance — defect rate / turns / MTTR.</p>"
                                "<p><b>Part 2</b> — Physical Asset, Product Definition, Capability Test, "
                                "Operational Location. "
                                "<b>Part 4</b> — Operations Capability / Performance.</p>"),
                   _report_widget("matrix", "Матрица 4×8", 0, 12, 84, 30,
                                  "root.platform.reports.emc-mom-activity-matrix",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["domain", "activity", "status"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("sched", "Domain Schedules", 0, 42, 42, 22,
                                  "root.platform.reports.emc-domain-schedule-report",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["domain", "status"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("locs", "Operational Locations", 42, 42, 42, 22,
                                  "root.platform.reports.emc-operational-location-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("qaKpi", "QA Defect Rate", 0, 64, 28, 16,
                                  "root.platform.reports.emc-qa-defect-rate-report"),
                   _report_widget("invKpi", "Inventory Turns", 28, 64, 28, 16,
                                  "root.platform.reports.emc-inv-turns-report"),
                   _report_widget("mntKpi", "MTTR / MTBF", 56, 64, 28, 16,
                                  "root.platform.reports.emc-maint-mttr-report"),
                   _report_widget("assets", "Physical Assets", 0, 80, 42, 18,
                                  "root.platform.reports.emc-physical-asset-report"),
                   _report_widget("products", "Product Definitions", 42, 80, 42, 18,
                                  "root.platform.reports.emc-product-definition-report"),
                   _report_widget("capab", "Operations Capability", 0, 98, 42, 18,
                                  "root.platform.reports.emc-ops-capability-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("tests", "Capability Tests", 42, 98, 42, 18,
                                  "root.platform.reports.emc-capability-test-report"),
                   _form_widget("upsertSched", "Domain schedule upsert", 0, 116, 42, 24,
                                "emc_domainschedule_upsert",
                                [{"name": "scheduleId", "label": "Schedule ID", "type": "text",
                                  "defaultValue": "DS-QA-SAMPLE-002", "required": True},
                                 _static("domain", "Domain",
                                         ["PRODUCTION", "QUALITY", "INVENTORY", "MAINTENANCE"],
                                         default="QUALITY"),
                                 _static("scheduleKind", "Kind",
                                         ["SAMPLE_PLAN", "REPLENISHMENT", "PM_CALENDAR", "FIRM_SCHEDULE"],
                                         default="SAMPLE_PLAN"),
                                 {"name": "targetId", "label": "Target", "type": "text",
                                  "defaultValue": "LOT-FG-0001"},
                                 {"name": "quantity", "label": "Qty", "type": "number", "defaultValue": "5"},
                                 {"name": "uom", "label": "UOM", "type": "text", "defaultValue": "pcs"},
                                 {"name": "note", "label": "Note", "type": "text",
                                  "defaultValue": "Operator-added sample plan"}],
                                "Сохранить"),
                   _form_widget("recordTest", "Capability test result", 42, 116, 42, 24,
                                "emc_capability_recordResult",
                                [{"name": "specId", "label": "Spec ID", "type": "text",
                                  "defaultValue": "CTS-WU-A01-SPEED", "required": True},
                                 {"name": "measuredValue", "label": "Измерение", "type": "text",
                                  "defaultValue": "95"},
                                 _static("result", "Результат", ["PASS", "FAIL"], default="PASS"),
                                 _sel("testedBy", "Кто", "emc-person-catalog", "code", "name",
                                      default="EMP-001")],
                                "Записать"),
               ]),
    _dashboard("root.platform.dashboards.emc-gost-conformance",
               "ГОСТ Р МЭК 62264 / IEC 62264 (M5)",
               "Объектное покрытие Parts 2+4: RRN, Container/Tool/Software, Ops Definition, Work Capability, Alerts.",
               [
                   _html_widget("help", "Приёмка demostand", 0, 0, 84, 10,
                                "<p><b>Ручная приёмка только через UI</b>. "
                                "Проверьте таблицы ниже (seed-данные) и формы действий. "
                                "Part 4 в РФ = <b>ПНСТ 172—2016</b>.</p>"),
                   _report_widget("rrn", "RRN Networks", 0, 10, 42, 18,
                                  "root.platform.reports.emc-rrn-networks",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("edges", "RRN Edges", 42, 10, 42, 18,
                                  "root.platform.reports.emc-rrn-edges",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["from_resource_type", "to_resource_type"])),
                   _report_widget("ctr", "Containers", 0, 28, 28, 18,
                                  "root.platform.reports.emc-container-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("tool", "Tools", 28, 28, 28, 18,
                                  "root.platform.reports.emc-tool-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("sw", "Software", 56, 28, 28, 18,
                                  "root.platform.reports.emc-software-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("opsdef", "Operations Definitions", 0, 46, 42, 18,
                                  "root.platform.reports.emc-opsdef-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("opssched", "Operations Schedules", 42, 46, 42, 18,
                                  "root.platform.reports.emc-opssched-report",
                                  statusDotColumnsJson=json.dumps(["state"])),
                   _report_widget("wcap", "Work Capability", 0, 64, 28, 16,
                                  "root.platform.reports.emc-workcap-report",
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _report_widget("wmc", "Work Master Capability", 28, 64, 28, 16,
                                  "root.platform.reports.emc-wmc-report"),
                   _report_widget("kpi", "Work KPI", 56, 64, 28, 16,
                                  "root.platform.reports.emc-kpi-value-report"),
                   _report_widget("alerts", "Work Alerts", 0, 80, 56, 20,
                                  "root.platform.reports.emc-work-alert-report",
                                  selectable=True, rowSelectionKey="alert_id",
                                  rowParamsFromRowJson=json.dumps({"alertId": "alert_id"}),
                                  autoSelectFirstRow=True,
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _form_widget("ackAlert", "Acknowledge Work Alert", 56, 80, 28, 20,
                                "emc_work_alert_ack",
                                [{"name": "alertId", "label": "Alert ID", "type": "text",
                                  "defaultValue": "WA-DEMO-001", "required": True},
                                 {"name": "ackBy", "label": "Кем", "type": "text",
                                  "defaultValue": "EMP-001", "required": True}],
                                "Подтвердить"),
                   _form_widget("upsertOd", "Ops Definition upsert", 0, 100, 84, 22,
                                "emc_opsdef_upsert",
                                [{"name": "definitionId", "label": "Definition ID", "type": "text",
                                  "defaultValue": "OD-ASSEMBLY-01", "required": True},
                                 {"name": "version", "label": "Version", "type": "text",
                                  "defaultValue": "1"},
                                 {"name": "name", "label": "Name", "type": "text",
                                  "defaultValue": "Assembly operations definition", "required": True},
                                 {"name": "description", "label": "Description", "type": "text",
                                  "defaultValue": "Updated from demostand UI"},
                                 {"name": "hierarchyScopeId", "label": "Scope", "type": "text",
                                  "defaultValue": "SCOPE-SITE-01"},
                                 _static("publishedFlag", "Published", ["true", "false"], default="true")],
                                "Сохранить"),
               ]),
    _dashboard("root.platform.dashboards.emc-maint",
               "ТОиР / Maintenance (Part 3)",
               "Заявки ТОиР и наряды: создать → принять → завершить.",
               [
                   _report_widget("reqs", "Заявки ТОиР", 0, 0, 56, 40,
                                  "root.platform.reports.emc-maint-request-report",
                                  selectable=True, rowSelectionKey="request_id",
                                  rowParamsFromRowJson=json.dumps({
                                      "requestId": "request_id", "woId": "wo_id"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["status", "equipment_id"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _form_widget("createReq", "Создать заявку", 56, 0, 28, 28,
                                "emc_maint_createRequest",
                                [{"name": "requestId", "label": "Request ID", "type": "text",
                                  "defaultValue": "MR-UI-001", "required": True},
                                 _sel("equipmentId", "Оборудование", "emc-equipment-catalog",
                                      "code", "name", default="WU-A01", required=True),
                                 {"name": "description", "label": "Описание", "type": "text",
                                  "defaultValue": "UI demostand request"},
                                 {"name": "priority", "label": "Приоритет", "type": "number",
                                  "defaultValue": "3"}],
                                "Создать"),
                   _form_widget("acceptReq", "Принять → WO", 56, 28, 28, 22,
                                "emc_maint_acceptRequest",
                                [{"name": "requestId", "label": "Request ID", "type": "text",
                                  "defaultValue": "MR-UI-001", "required": True},
                                 {"name": "woId", "label": "WO ID", "type": "text",
                                  "defaultValue": "MWO-UI-001", "required": True},
                                 {"name": "plannedStart", "label": "План старт", "type": "text",
                                  "defaultValue": ""},
                                 {"name": "plannedEnd", "label": "План конец", "type": "text",
                                  "defaultValue": ""}],
                                "Принять"),
                   _form_widget("completeWo", "Завершить WO", 0, 40, 42, 16,
                                "emc_maint_completeWorkOrder",
                                [{"name": "woId", "label": "WO ID", "type": "text",
                                  "defaultValue": "MWO-DEMO-001", "required": True}],
                                "Завершить"),
                   _report_widget("mttr", "MTTR / MTBF", 42, 40, 42, 16,
                                  "root.platform.reports.emc-maint-mttr-report"),
               ]),
]


# ----------------------------------------------------------------------------
# Bindings, alert rules, schedules, workflow, events
# ----------------------------------------------------------------------------

BINDINGS = [
    {"objectPath": HUB, "variable": "pendingOutboxCount",
     "query": "SELECT COUNT(*) AS v FROM emc_erp_outbox WHERE status = 'PENDING'",
     "refresh": "on_schedule", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "activeDowntimeCount",
     "query": ("SELECT COUNT(*) AS v FROM emc_operations_event e "
               "JOIN emc_operations_event_definition d ON d.code = e.definition_code "
               "WHERE e.status = 'OPEN' AND d.oee_bucket = 'AVAILABILITY'"),
     "refresh": "on_schedule", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "lowStockCount",
     "query": "SELECT COUNT(*) AS v FROM emc_material_lot WHERE status = 'STOCK' AND quantity < 10",
     "refresh": "on_schedule", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
]

ALERT_RULES = [
    {"name": "emc-low-stock", "objectPath": HUB, "watchVariable": "lowStockCount",
     "conditionExpr": "self.lowStockCount[\"value\"] > 0", "eventName": "lowStockAlert",
     "enabled": True, "edgeTrigger": True, "delaySeconds": 0, "sustainWhileTrue": False},
    {"name": "emc-critical-downtime", "objectPath": HUB, "watchVariable": "activeDowntimeCount",
     "conditionExpr": "self.activeDowntimeCount[\"value\"] >= 2", "eventName": "criticalDowntime",
     "enabled": True, "edgeTrigger": True, "delaySeconds": 0, "sustainWhileTrue": False},
]

SCHEDULES = [
    {"scheduleId": "emc-erp-outbox-poll", "enabled": False, "intervalMs": 60000,
     "actionType": "invoke_function",
     "action": {"objectPath": HUB, "functionName": "emc_erp_pollOutbox"}},
    {"scheduleId": "emc-oee-shift-rollup", "enabled": False, "intervalMs": 300000,
     "actionType": "invoke_function",
     "action": {"objectPath": HUB, "functionName": "emc_oee_calcShift"}},
]

_WORKFLOW_BPMN = """<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:isfp="http://ispf.io/bpmn"
                  id="emc-job-dispatch-defs" targetNamespace="http://ispf.io/erp-mes-core">
  <bpmn:process id="emc-job-dispatch" isExecutable="true">
    <bpmn:startEvent id="start" name="Job dispatched"/>
    <bpmn:serviceTask id="log" name="Log dispatch" isfp:action="log"
                      isfp:message="Job order dispatched for execution"/>
    <bpmn:userTask id="confirm" name="Confirm job start" isfp:title="Confirm job start"
                   isfp:instructions="Verify line clearance and materials, then confirm start."
                   isfp:assigneeRole="operator"
                   isfp:targetObject="root.platform.singleton-blueprints.erp-mes-core-hub-v1"
                   isfp:function="emc_joborder_confirmStart"/>
    <bpmn:endEvent id="end" name="Done"/>
    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="log"/>
    <bpmn:sequenceFlow id="f2" sourceRef="log" targetRef="confirm"/>
    <bpmn:sequenceFlow id="f3" sourceRef="confirm" targetRef="end"/>
  </bpmn:process>
</bpmn:definitions>"""

WORKFLOWS = [
    {"path": "root.platform.workflows.emc-job-dispatch", "title": "Job Dispatch Confirmation",
     "status": "ACTIVE", "operatorAppId": APP_ID, "bpmnXml": _WORKFLOW_BPMN},
]

EVENTS = [
    {"id": "lowStockAlert", "roles": ["operator", "admin"]},
    {"id": "criticalDowntime", "roles": ["operator", "admin"]},
]

# ----------------------------------------------------------------------------
# Assembly
# ----------------------------------------------------------------------------

FUNCTIONS.extend(build_uml_functions(
    fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null))
FUNCTIONS.extend(build_m21_functions(
    fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null, when, invoke))
FUNCTIONS.extend(build_gost_functions(
    fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null))
FUNCTIONS.extend(build_m20_functions(
    fn, F, OUT, RL, selN, map_rows, ret))

bundle = {
    "version": "2.2.2",
    "displayName": "ERP-MES Core (ISA-95)",
    "tablePrefix": "emc_",
    "schemaName": "app_erp_mes_core",
    "migrations": MIGRATIONS,
    "objects": OBJECTS,
    "functions": FUNCTIONS,
    "blueprints": BLUEPRINTS,
    "dashboards": DASHBOARDS,
    "workflows": WORKFLOWS,
    "bindings": BINDINGS,
    "reports": REPORTS,
    "alertRules": ALERT_RULES,
    "schedules": SCHEDULES,
    "events": EVENTS,
    "operatorUi": {
        "appId": APP_ID,
        "title": "ERP-MES Core (ISA-95)",
        "defaultDashboard": "root.platform.dashboards.emc-dispatch",
        "dashboards": [
            {"path": "root.platform.dashboards.emc-dispatch", "title": "Диспетчер"},
            {"path": "root.platform.dashboards.emc-execution", "title": "Исполнение"},
            {"path": "root.platform.dashboards.emc-inventory", "title": "Склад"},
            {"path": "root.platform.dashboards.emc-quality", "title": "Качество"},
            {"path": "root.platform.dashboards.emc-oee", "title": "OEE и простои"},
            {"path": "root.platform.dashboards.emc-genealogy", "title": "Генеалогия партии"},
            {"path": "root.platform.dashboards.emc-mom-matrix", "title": "MOM 62264-3"},
            {"path": "root.platform.dashboards.emc-gost-conformance", "title": "ГОСТ 62264"},
            {"path": "root.platform.dashboards.emc-maint", "title": "ТОиР"},
        ],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.emc-job-board", "title": "Job Board"},
            {"path": "root.platform.reports.emc-stock-report", "title": "Stock"},
            {"path": "root.platform.reports.emc-material-movement", "title": "Material Movement"},
            {"path": "root.platform.reports.emc-defect-report", "title": "Defects"},
            {"path": "root.platform.reports.emc-oee-shift-report", "title": "OEE by Shift"},
            {"path": "root.platform.reports.emc-genealogy-edges", "title": "Genealogy Edges"},
            {"path": "root.platform.reports.emc-genealogy-upstream-fg", "title": "Reverse Trace FG"},
            {"path": "root.platform.reports.emc-genealogy-downstream-raw", "title": "Forward Trace Raw"},
            {"path": "root.platform.reports.emc-mom-activity-matrix", "title": "MOM 4x8 Matrix"},
            {"path": "root.platform.reports.emc-physical-asset-report", "title": "Physical Assets"},
            {"path": "root.platform.reports.emc-ops-capability-report", "title": "Ops Capability"},
            {"path": "root.platform.reports.emc-domain-schedule-report", "title": "Domain Schedules"},
            {"path": "root.platform.reports.emc-operational-location-report", "title": "Locations"},
            {"path": "root.platform.reports.emc-rrn-edges", "title": "RRN Edges"},
            {"path": "root.platform.reports.emc-container-report", "title": "Containers"},
            {"path": "root.platform.reports.emc-tool-report", "title": "Tools"},
            {"path": "root.platform.reports.emc-software-report", "title": "Software"},
            {"path": "root.platform.reports.emc-opsdef-report", "title": "Ops Definitions"},
            {"path": "root.platform.reports.emc-maint-request-report", "title": "Maint Requests"},
            {"path": "root.platform.reports.emc-invdoc-report", "title": "Inventory Docs"},
        ],
        "defaultReport": "root.platform.reports.emc-job-board",
    },
    "metadata": {
        "product": "erp-mes-core",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "2.2.1 rich demostand seeds (3+ examples/process) + job↔lot genealogy links; 2.2.0 GOST M5; 2.1.0 Part 5 UML + KPI + APS-lite",
    },
}

with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(bundle, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("Wrote", BUNDLE_OUT, "migrations=", len(MIGRATIONS), "functions=", len(FUNCTIONS))
