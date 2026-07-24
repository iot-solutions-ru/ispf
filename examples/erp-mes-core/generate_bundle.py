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
Rules: UUID PKs + RANDOM_UUID(), no `::` casts, CREATE TABLE IF NOT EXISTS,
seeds via INSERT ... SELECT ... WHERE NOT EXISTS (re-entrant on redeploy).
Script DSL: validator white-listed steps only (see FunctionScriptValidator).
"""
import io
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
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
       SELECT RANDOM_UUID(), 'b0000002-0000-0000-0000-000000000002', 'RUN_INTERVAL', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data WHERE response_id = 'b0000002-0000-0000-0000-000000000002' AND data_kind = 'RUN_INTERVAL' AND ended_at IS NULL)""",
    """INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use)
       SELECT RANDOM_UUID(), 'b0000002-0000-0000-0000-000000000002', 'WU-A01', 'PRIMARY'
       WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_actual WHERE response_id = 'b0000002-0000-0000-0000-000000000002')""",
    """INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use)
       SELECT RANDOM_UUID(), 'b0000002-0000-0000-0000-000000000002', 'EMP-001', 'OPERATOR'
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
       SELECT RANDOM_UUID(), 'SHIFT-DEMO-1', 'EMP-001'
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
           "SELECT RANDOM_UUID(), ?, ?, ?, ?, ?, ?, 'NOT_ALLOWED', 'STORE', COALESCE(NULLIF(?, ''), '5'), NULLIF(?, ''), NULLIF(?, '') "
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
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) VALUES (RANDOM_UUID(), ?, 'RECEIVED', ?, 'erp')",
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
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, actor) VALUES (RANDOM_UUID(), ?, 'RELEASED', 'dispatcher')",
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
    OUT(F("jobNo"), F("status"), F("responseId")),
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
                 "jobNo": "${input.jobNo}", "status": "", "responseId": ""}),
        ]),
        ex("INSERT INTO emc_job_response (response_id, job_no, job_state) SELECT RANDOM_UUID(), ?, 'RUNNING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING')",
           ["${input.jobNo}", "${input.jobNo}"]),
        ex("UPDATE emc_job_order SET dispatch_status = 'RUNNING', command = 'START', actual_start = CURRENT_TIMESTAMP "
           "WHERE job_no = ?", ["${input.jobNo}"]),
        sel1("resp", "SELECT response_id FROM emc_job_response WHERE job_no = ? AND job_state = 'RUNNING'",
             ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at) "
           "SELECT RANDOM_UUID(), ?, 'RUN_INTERVAL', CURRENT_TIMESTAMP "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data d WHERE d.response_id = ? AND d.ended_at IS NULL)",
           ["${resp.response_id}", "${resp.response_id}"]),
        ex("INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use) "
           "SELECT RANDOM_UUID(), ?, ?, 'PRIMARY' WHERE NOT EXISTS "
           "(SELECT 1 FROM emc_equipment_actual WHERE response_id = ?)",
           ["${resp.response_id}", "${job.equipment_id}", "${resp.response_id}"]),
        when({"var": "input.personId", "notNull": True}, [
            ex("INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use) "
               "SELECT RANDOM_UUID(), ?, ?, 'OPERATOR' WHERE NOT EXISTS "
               "(SELECT 1 FROM emc_personnel_actual WHERE response_id = ? AND person_id = ?)",
               ["${resp.response_id}", "${input.personId}", "${resp.response_id}", "${input.personId}"]),
        ]),
        # Part 5: PROCESS Operations Event "work commenced" to ERP (idempotent)
        ex("INSERT INTO emc_erp_outbox (id, verb, noun, object_id, payload_json, idempotency_key, status) "
           "SELECT RANDOM_UUID(), 'PROCESS', 'OPERATIONS_EVENT', ?, "
           "CONCAT('{\"event\":\"work commenced\",\"jobNo\":\"', ?, '\"}'), CONCAT('WO-COMMENCED:', ?), 'PENDING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_erp_outbox WHERE idempotency_key = CONCAT('WO-COMMENCED:', ?))",
           ["${input.jobNo}", "${input.jobNo}", "${input.jobNo}", "${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "RUNNING", "${job.job_no}"),
        *machine_write("WU-A02", WU_A02, "RUNNING", "${job.job_no}"),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, actor) VALUES (RANDOM_UUID(), ?, 'STARTED', 'operator')",
           ["${input.jobNo}"]),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}",
             "status": "RUNNING", "responseId": "${resp.response_id}"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_pause",
    [F("jobNo")],
    OUT(F("jobNo"), F("status")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "RUNNING", "INVALID_STATE", "Only RUNNING job orders can be paused"),
        ex("UPDATE emc_job_order SET dispatch_status = 'SUSPENDED', command = 'PAUSE' WHERE job_no = ?", ["${input.jobNo}"]),
        ex("UPDATE emc_job_response_data SET ended_at = CURRENT_TIMESTAMP WHERE ended_at IS NULL "
           "AND response_id IN (SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at) "
           "SELECT RANDOM_UUID(), response_id, 'PAUSE_INTERVAL', CURRENT_TIMESTAMP FROM emc_job_response "
           "WHERE job_no = ? AND job_state = 'RUNNING'", ["${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "PAUSED", "${job.job_no}"),
        *machine_write("WU-A02", WU_A02, "PAUSED", "${job.job_no}"),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}", "status": "SUSPENDED"}),
    ],
))

FUNCTIONS.append(fn(
    "emc_joborder_resume",
    [F("jobNo")],
    OUT(F("jobNo"), F("status")),
    [
        sel1("job", "SELECT job_no, dispatch_status, equipment_id FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
        fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
        fail_ne("job.dispatch_status", "SUSPENDED", "INVALID_STATE", "Only SUSPENDED job orders can be resumed"),
        ex("UPDATE emc_job_order SET dispatch_status = 'RUNNING', command = 'RESUME' WHERE job_no = ?", ["${input.jobNo}"]),
        ex("UPDATE emc_job_response_data SET ended_at = CURRENT_TIMESTAMP WHERE ended_at IS NULL "
           "AND response_id IN (SELECT response_id FROM emc_job_response WHERE job_no = ?)", ["${input.jobNo}"]),
        ex("INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at) "
           "SELECT RANDOM_UUID(), response_id, 'RUN_INTERVAL', CURRENT_TIMESTAMP FROM emc_job_response "
           "WHERE job_no = ? AND job_state = 'RUNNING'", ["${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "RUNNING", "${job.job_no}"),
        *machine_write("WU-A02", WU_A02, "RUNNING", "${job.job_no}"),
        ret({"error_code": "OK", "error_message": "", "jobNo": "${job.job_no}", "status": "RUNNING"}),
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
           "SELECT RANDOM_UUID(), 'PROCESS', 'OPERATIONS_PERFORMANCE', ?, "
           "CONCAT('{\"jobNo\":\"', ?, '\",\"event\":\"work completed\"}'), CONCAT('WO-COMPLETED:', ?), 'PENDING' "
           "WHERE NOT EXISTS (SELECT 1 FROM emc_erp_outbox WHERE idempotency_key = CONCAT('WO-COMPLETED:', ?))",
           ["${input.jobNo}", "${input.jobNo}", "${input.jobNo}", "${input.jobNo}"]),
        *machine_write("WU-A01", WU_A01, "IDLE", ""),
        *machine_write("WU-A02", WU_A02, "IDLE", ""),
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, actor) VALUES (RANDOM_UUID(), ?, 'COMPLETED', 'operator')",
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
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) VALUES (RANDOM_UUID(), ?, 'ABORTED', ?, 'operator')",
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
           "SELECT RANDOM_UUID(), ?, request_id, work_master_id, work_master_version, segment_id, equipment_id, "
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
        ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) VALUES (RANDOM_UUID(), ?, 'REPLANNED', ?, 'dispatcher')",
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
           "VALUES (RANDOM_UUID(), ?, 'PARAMETER', ?, ?, ?)",
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
           "VALUES (RANDOM_UUID(), ?, ?, ?, 'CONSUMED', ?, ?)",
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
           "VALUES (RANDOM_UUID(), ?, ?, ?, 'PRODUCED', ?, ?)",
           ["${resp.response_id}", "${input.lotId}", "${input.definitionId}", "${input.quantity}", "${def.base_uom}"]),
        # Genealogy edges: all consumed lots of this response -> new lot
        ex("INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, response_id, quantity) "
           "SELECT RANDOM_UUID(), ma.lot_id, ?, ma.response_id, ma.quantity FROM emc_material_actual ma "
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
           "VALUES (RANDOM_UUID(), ?, ?, NULLIF(?, ''), COALESCE(NULLIF(?, ''), '0'), NULLIF(?, ''), NULLIF(?, ''))",
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
           "SELECT RANDOM_UUID(), 'PROCESS', 'MATERIAL_LOT', ?, "
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
           "VALUES (RANDOM_UUID(), ?, ?, NULLIF(?, ''), ?, NULLIF(?, ''), COALESCE(NULLIF(?, ''), 'MINOR'), COALESCE(NULLIF(?, ''), '1'), NULLIF(?, ''))",
           ["${input.defectNo}", "${input.jobNo}", "${input.lotId}", "${input.defectTypeId}",
            "${input.reasonCode}", "${input.severity}", "${input.qtyDeclared}", "${input.createdBy}"]),
        ex("INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) "
           "VALUES (RANDOM_UUID(), ?, NULL, 'REGISTERED', ?, NULL)",
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
           "qty_confirmed = COALESCE(CAST(NULLIF(?, '') AS NUMERIC), qty_declared), "
           "reason_code = COALESCE(NULLIF(?, ''), reason_code) "
           "WHERE defect_no = ? AND status = 'REGISTERED'",
           ["${input.qtyConfirmed}", "${input.reasonCode}", "${input.defectNo}"]),
        ex("INSERT INTO emc_defect_status_history (id, defect_no, from_status, to_status, actor, note) "
           "VALUES (RANDOM_UUID(), ?, 'REGISTERED', 'CONFIRMED', ?, NULL)",
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
           "VALUES (RANDOM_UUID(), ?, 'REGISTERED', 'REJECTED', ?, NULLIF(?, ''))",
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
           "VALUES (RANDOM_UUID(), ?, 'CONFIRMED', 'CLOSED', ?, NULL)",
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
           "VALUES (RANDOM_UUID(), NULLIF(?, ''), NULLIF(?, ''), ?, ?, NULLIF(?, ''))",
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
           "VALUES (RANDOM_UUID(), ?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), "
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
               "VALUES (RANDOM_UUID(), ?, ?, true)",
               ["${input.equipmentId}", "${input.signalCode}"]),
        ], [
            ex("INSERT INTO emc_machine_signal (signal_id, equipment_id, signal_code, is_auto) "
               "VALUES (RANDOM_UUID(), ?, ?, false)",
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
           "SELECT RANDOM_UUID(), ?, ?, NULLIF(?, '') "
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
           "SELECT RANDOM_UUID(), ?, ?, NULLIF(?, ''), NULLIF(?, ''), ?, 'PENDING' "
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
           "SELECT RANDOM_UUID(), 'OUT', verb, noun, true, 'OK', 'Simulated transport, ACK ACCEPTED' "
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
           "VALUES (RANDOM_UUID(), ?, ?, ?, ?, 'RECEIVED')",
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
           "SELECT RANDOM_UUID(), ?, ?, p.planned, p.av, p.pf, p.pr, p.good, p.a_pct, p.p_pct, p.q_pct, "
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


def _dashboard(path, title, description, widgets):
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
                                  rowParamsFromRowJson=json.dumps({"jobNo": "job_no"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["dispatch_status", "equipment_id"]),
                                  statusDotColumnsJson=json.dumps(["dispatch_status"])),
                   _form_widget("start", "Запустить задание", 56, 12, 28, 16, "emc_joborder_start",
                                [_sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True),
                                 _sel("personId", "Оператор", "emc-person-catalog", "code", "name", default="EMP-001")],
                                "Запустить"),
                   _form_widget("pause", "Пауза", 56, 28, 28, 11, "emc_joborder_pause",
                                [_sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True)],
                                "Пауза"),
                   _form_widget("resume", "Возобновить", 56, 39, 28, 11, "emc_joborder_resume",
                                [_sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True)],
                                "Возобновить"),
                   _form_widget("complete", "Завершить", 56, 50, 28, 11, "emc_joborder_complete",
                                [_sel("jobNo", "Сменное задание", "emc-job-board", "job_no", "dispatch_status", required=True)],
                                "Завершить"),
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
                                 {"name": "comment", "label": "Комментарий", "type": "textarea", "defaultValue": ""}],
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
]


# ----------------------------------------------------------------------------
# Bindings, alert rules, schedules, workflow, events
# ----------------------------------------------------------------------------

BINDINGS = [
    {"objectPath": HUB, "variable": "pendingOutboxCount",
     "query": "SELECT COUNT(*) AS v FROM emc_erp_outbox WHERE status = 'PENDING'",
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "activeDowntimeCount",
     "query": ("SELECT COUNT(*) AS v FROM emc_operations_event e "
               "JOIN emc_operations_event_definition d ON d.code = e.definition_code "
               "WHERE e.status = 'OPEN' AND d.oee_bucket = 'AVAILABILITY'"),
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "lowStockCount",
     "query": "SELECT COUNT(*) AS v FROM emc_material_lot WHERE status = 'STOCK' AND quantity < 10",
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
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

bundle = {
    "version": "1.1.0",
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
        ],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.emc-job-board", "title": "Job Board"},
            {"path": "root.platform.reports.emc-stock-report", "title": "Stock"},
            {"path": "root.platform.reports.emc-material-movement", "title": "Material Movement"},
            {"path": "root.platform.reports.emc-defect-report", "title": "Defects"},
            {"path": "root.platform.reports.emc-oee-shift-report", "title": "OEE by Shift"},
        ],
        "defaultReport": "root.platform.reports.emc-job-board",
    },
    "metadata": {
        "product": "erp-mes-core",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "1.1.0 operator UI rework: flat widget format, dropdown selects from catalog reports, row-click autofill, KPI cards, event journal",
    },
}

with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(bundle, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("Wrote", BUNDLE_OUT, "migrations=", len(MIGRATIONS), "functions=", len(FUNCTIONS))
