#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ERP-MES Printing (ISA-95) overlay bundle generator.

Printing industry (flexo) overlay on top of the industry-neutral erp-mes-core bundle:
  emp_m1 - own overlay tables (extended event-code attributes, Zero Pull setup tags)
  emp_m2 - Flexibase customer event code catalog (155 codes, OGP/BM/POUCH/MES) seeded
           into the core emc_operations_event_definition + emp_eventdef_ext
  emp_m3 - printing equipment hierarchy (PR120/PR130/LM210/SL300/RW100) + personnel
  emp_m4 - printing materials (films, inks, glue, solvent, roll lots with barcodes)
  emp_m5 - process segments PRINT/LAMINATE/SLIT/REWIND, work masters, quality
           dictionaries, demo print order (JO-PRINT-001 RUNNING) + job bag sections

Runs with the SAME schemaName as the core (app_erp_mes_core): overlay migrations,
functions and bindings see the core emc_* tables through search_path. Own tables
use the emp_ prefix (tablePrefix validator scope). Migration ids use the emp_m
prefix (global root.platform.migrations namespace, must not clash with emc_*).

Dialect: works on H2 (PostgreSQL mode, tests) and PostgreSQL (runtime).
Rules: UUID PKs + RANDOM_UUID(), no `::` casts, CREATE TABLE IF NOT EXISTS,
seeds via INSERT ... SELECT ... WHERE NOT EXISTS (re-entrant on redeploy).
Script DSL: validator white-listed steps only (see FunctionScriptValidator).
"""
import io
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from event_catalog_data import EVENT_CODES, SETUP_TAGS

ROOT = os.path.dirname(os.path.abspath(__file__))
BUNDLE_OUT = os.path.join(ROOT, "bundle.json")
APP_ID = "erp-mes-printing"
SCHEMA = "app_erp_mes_core"
HUB = "root.platform.singleton-blueprints.erp-mes-printing-hub-v1"
CORE_HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1"
PRINT_EQUIPMENT = ("PR120", "PR130", "LM210", "SL300", "RW100")
PRINT_EQUIPMENT_SQL = "(" + ", ".join("'" + e + "'" for e in PRINT_EQUIPMENT) + ")"

# ---------------------------------------------------------------------------
# DSL helpers (verbatim copy from erp-mes-core/generate_bundle.py)
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


def bool_sql(flag):
    return "!true" if flag else "!false"


# ---------------------------------------------------------------------------
# Migrations (emp_m1-emp_m5), SQL joined with "; " - no semicolons inside statements
# ---------------------------------------------------------------------------

M1_OVERLAY_TABLES = ";\n".join([
    # Overlay-only tables: extended event-code attributes absent from the core model
    """CREATE TABLE IF NOT EXISTS emp_eventdef_ext (
       code VARCHAR(64) PRIMARY KEY,
       catalog VARCHAR(16) NOT NULL,
       section VARCHAR(64),
       operator_hint VARCHAR(512),
       time_type VARCHAR(8))""",
    # Zero Pull setup tags: reasons for repeated setup attempts (used with code OGP-120)
    """CREATE TABLE IF NOT EXISTS emp_setup_tag (
       tag VARCHAR(16) PRIMARY KEY,
       name VARCHAR(128),
       description VARCHAR(512),
       sort_order INTEGER NOT NULL DEFAULT 100)""",
])

# Flexibase customer catalog -> core event definitions + overlay extension rows
# (no standalone comment lines: the server statement splitter would execute them as empty SQL)
_M2_STATEMENTS = []
for (code, event_class, name, req_len, req_time, req_comment,
     oee_bucket, six_big_loss, sort_order, catalog, section, hint, time_type) in EVENT_CODES:
    _M2_STATEMENTS.append(
        seed("emc_operations_event_definition",
             ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment",
              "oee_bucket", "six_big_loss", "sort_order"],
             [code, event_class, name, bool_sql(req_len), bool_sql(req_time), bool_sql(req_comment),
              oee_bucket, six_big_loss, str(sort_order)],
             f"code = '{code}'"))
    _M2_STATEMENTS.append(
        seed("emp_eventdef_ext",
             ["code", "catalog", "section", "operator_hint", "time_type"],
             [code, catalog, section, hint, time_type],
             f"code = '{code}'"))
# Zero Pull setup tags (reason dictionary for repeated setup at OGP-120)
for (tag, name, description, sort_order) in SETUP_TAGS:
    _M2_STATEMENTS.append(
        seed("emp_setup_tag",
             ["tag", "name", "description", "sort_order"],
             [tag, name, description, str(sort_order)],
             f"tag = '{tag}'"))
M2_EVENT_CATALOG = ";\n".join(_M2_STATEMENTS)

M3_EQUIPMENT_PERSONNEL = ";\n".join([
    # ISA-95 Part 1/2: printing equipment classes (work-unit level)
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-FLEXO-PRESS", "Flexographic printing press", "WORK_UNIT", None],
         "class_id = 'EQC-FLEXO-PRESS'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-LAMINATOR", "Solventless laminator", "WORK_UNIT", None],
         "class_id = 'EQC-LAMINATOR'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-SLITTER", "Slitter rewinder", "WORK_UNIT", None],
         "class_id = 'EQC-SLITTER'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-REWINDER", "Inspection rewinder", "WORK_UNIT", None],
         "class_id = 'EQC-REWINDER'"),
    # Role-based hierarchy: ENT-PRINT -> SITE-PRINT-01 -> areas -> work units
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["ENT-PRINT", None, "ENTERPRISE", None, "ENT-PRINT", "Printing demo enterprise"],
         "equipment_id = 'ENT-PRINT'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["SITE-PRINT-01", None, "SITE", "ENT-PRINT", "ENT-PRINT/SITE-PRINT-01", "Printing demo site"],
         "equipment_id = 'SITE-PRINT-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["AREA-PRINT", None, "AREA", "SITE-PRINT-01", "ENT-PRINT/SITE-PRINT-01/AREA-PRINT", "Printing area"],
         "equipment_id = 'AREA-PRINT'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["AREA-LAM", None, "AREA", "SITE-PRINT-01", "ENT-PRINT/SITE-PRINT-01/AREA-LAM", "Lamination area"],
         "equipment_id = 'AREA-LAM'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["AREA-SLIT", None, "AREA", "SITE-PRINT-01", "ENT-PRINT/SITE-PRINT-01/AREA-SLIT", "Slitting area"],
         "equipment_id = 'AREA-SLIT'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["PR120", "EQC-FLEXO-PRESS", "WORK_UNIT", "AREA-PRINT", "ENT-PRINT/SITE-PRINT-01/AREA-PRINT/PR120", "Flexo press PR120 (8 colors)"],
         "equipment_id = 'PR120'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["PR130", "EQC-FLEXO-PRESS", "WORK_UNIT", "AREA-PRINT", "ENT-PRINT/SITE-PRINT-01/AREA-PRINT/PR130", "Flexo press PR130"],
         "equipment_id = 'PR130'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["LM210", "EQC-LAMINATOR", "WORK_UNIT", "AREA-LAM", "ENT-PRINT/SITE-PRINT-01/AREA-LAM/LM210", "Solventless laminator LM210"],
         "equipment_id = 'LM210'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["SL300", "EQC-SLITTER", "WORK_UNIT", "AREA-SLIT", "ENT-PRINT/SITE-PRINT-01/AREA-SLIT/SL300", "Slitter SL300"],
         "equipment_id = 'SL300'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["RW100", "EQC-REWINDER", "WORK_UNIT", "AREA-SLIT", "ENT-PRINT/SITE-PRINT-01/AREA-SLIT/RW100", "Inspection rewinder RW100"],
         "equipment_id = 'RW100'"),
    # Warehouses as equipment (canonical core pattern)
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-PRINT", None, "STORAGE_ZONE", "SITE-PRINT-01", "ENT-PRINT/SITE-PRINT-01/WH-PRINT", "Printing materials warehouse"],
         "equipment_id = 'WH-PRINT'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-PR120-LS", None, "STORAGE_UNIT", "WH-PRINT", "ENT-PRINT/SITE-PRINT-01/WH-PRINT/WH-PR120-LS", "Line-side storage PR120"],
         "equipment_id = 'WH-PR120-LS'"),
    # Equipment properties (extension without migrations)
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["PR120", "colors", "8", None],
         "equipment_id = 'PR120' AND prop_key = 'colors'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["PR120", "max_width_mm", "1200", "mm"],
         "equipment_id = 'PR120' AND prop_key = 'max_width_mm'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["PR120", "max_speed_mpm", "300", "mpm"],
         "equipment_id = 'PR120' AND prop_key = 'max_speed_mpm'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["LM210", "max_width_mm", "1100", "mm"],
         "equipment_id = 'LM210' AND prop_key = 'max_width_mm'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["SL300", "max_speed_mpm", "400", "mpm"],
         "equipment_id = 'SL300' AND prop_key = 'max_speed_mpm'"),
    # ISA-95 Part 2: Personnel model
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-PRINTER", "Flexo printer"], "class_id = 'PCL-PRINTER'"),
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-ASSISTANT", "Press assistant"], "class_id = 'PCL-ASSISTANT'"),
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-SLITTER-OPERATOR", "Slitter operator"], "class_id = 'PCL-SLITTER-OPERATOR'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-P01", "Ivan Printer", "PCL-PRINTER"], "person_id = 'EMP-P01'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-P02", "Oleg Assistant", "PCL-ASSISTANT"], "person_id = 'EMP-P02'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-P03", "Anna Slitter", "PCL-SLITTER-OPERATOR"], "person_id = 'EMP-P03'"),
    # Qualification: by equipment class (printer) XOR by equipment instance (slitter operator)
    seed("emc_person_qualification", ["person_id", "equipment_id", "equipment_class_id", "qualification"],
         ["EMP-P01", None, "EQC-FLEXO-PRESS", "OPERATE"],
         "person_id = 'EMP-P01' AND equipment_class_id = 'EQC-FLEXO-PRESS'"),
    seed("emc_person_qualification", ["person_id", "equipment_id", "equipment_class_id", "qualification"],
         ["EMP-P03", "SL300", None, "OPERATE"],
         "person_id = 'EMP-P03' AND equipment_id = 'SL300'"),
])


M4_MATERIALS = ";\n".join([
    # ISA-95 Part 2: printing material classes (parents MCL-RAW/MCL-WIP/MCL-FG come from the core)
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-FILM", "Films (PET/BOPP)", "MCL-RAW"], "class_id = 'MCL-FILM'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-INK", "Printing inks", "MCL-RAW"], "class_id = 'MCL-INK'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-GLUE", "Lamination adhesives", "MCL-RAW"], "class_id = 'MCL-GLUE'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-SOLVENT", "Solvents", "MCL-RAW"], "class_id = 'MCL-SOLVENT'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-WIP-ROLL", "WIP rolls (printed/laminated)", "MCL-WIP"], "class_id = 'MCL-WIP-ROLL'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-FG-ROLL", "Finished goods rolls", "MCL-FG"], "class_id = 'MCL-FG-ROLL'"),
    # Material definitions
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["FILM-PET12", "MCL-FILM", "RAW", "kg", "PET film 12 µm"], "definition_id = 'FILM-PET12'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["FILM-BOPP20", "MCL-FILM", "RAW", "kg", "BOPP film 20 µm"], "definition_id = 'FILM-BOPP20'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["INK-C", "MCL-INK", "RAW", "kg", "Cyan ink"], "definition_id = 'INK-C'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["INK-M", "MCL-INK", "RAW", "kg", "Magenta ink"], "definition_id = 'INK-M'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["INK-Y", "MCL-INK", "RAW", "kg", "Yellow ink"], "definition_id = 'INK-Y'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["INK-K", "MCL-INK", "RAW", "kg", "Black ink"], "definition_id = 'INK-K'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["INK-W", "MCL-INK", "RAW", "kg", "White ink"], "definition_id = 'INK-W'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["GLUE-PUR", "MCL-GLUE", "RAW", "kg", "PUR laminating adhesive"], "definition_id = 'GLUE-PUR'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["SOLV-ETAC", "MCL-SOLVENT", "RAW", "kg", "Ethyl acetate solvent"], "definition_id = 'SOLV-ETAC'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-PRINTED-ROLL", "MCL-WIP-ROLL", "WIP", "m", "Printed roll (after flexo print)"], "definition_id = 'WIP-PRINTED-ROLL'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-LAM-ROLL", "MCL-WIP-ROLL", "WIP", "m", "Laminated roll"], "definition_id = 'WIP-LAM-ROLL'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["FG-SLEEVE-ROLL", "MCL-FG-ROLL", "FG", "m", "Shrink-sleeve rolls"], "definition_id = 'FG-SLEEVE-ROLL'"),
    # Lots: film rolls with length, ink cans in kg (length NULL), WIP printed roll
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg", "length_m"],
         ["LOT-FILM-0001", "BC-FILM-0001", "FILM-PET12", "STOCK", "WH-PRINT", "6000", "kg", "432", "6000"],
         "lot_id = 'LOT-FILM-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg", "length_m"],
         ["LOT-FILM-0002", "BC-FILM-0002", "FILM-PET12", "STOCK", "WH-PRINT", "4500", "kg", "324", "4500"],
         "lot_id = 'LOT-FILM-0002'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg", "length_m"],
         ["LOT-FILM-0003", "BC-FILM-0003", "FILM-BOPP20", "STOCK", "WH-PRINT", "5000", "kg", "506", "5000"],
         "lot_id = 'LOT-FILM-0003'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-INK-C", "BC-INK-C", "INK-C", "STOCK", "WH-PRINT", "20", "kg", "20"],
         "lot_id = 'LOT-INK-C'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-INK-M", "BC-INK-M", "INK-M", "STOCK", "WH-PRINT", "20", "kg", "20"],
         "lot_id = 'LOT-INK-M'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-INK-Y", "BC-INK-Y", "INK-Y", "STOCK", "WH-PRINT", "20", "kg", "20"],
         "lot_id = 'LOT-INK-Y'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-INK-K", "BC-INK-K", "INK-K", "STOCK", "WH-PRINT", "20", "kg", "20"],
         "lot_id = 'LOT-INK-K'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-INK-W", "BC-INK-W", "INK-W", "STOCK", "WH-PRINT", "20", "kg", "20"],
         "lot_id = 'LOT-INK-W'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-GLUE-0001", "BC-GLUE-0001", "GLUE-PUR", "STOCK", "WH-PRINT", "25", "kg", "25"],
         "lot_id = 'LOT-GLUE-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom", "weight_kg", "length_m"],
         ["LOT-PRT-0001", "BC-PRT-0001", "WIP-PRINTED-ROLL", "STOCK", "WH-PRINT", "3000", "m", "210", "3000"],
         "lot_id = 'LOT-PRT-0001'"),
    # Lot properties: roll width for films/WIP, film thickness
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FILM-0001", "width_mm", "1050", "mm"],
         "lot_id = 'LOT-FILM-0001' AND prop_key = 'width_mm'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FILM-0001", "thickness_mkm", "12", "µm"],
         "lot_id = 'LOT-FILM-0001' AND prop_key = 'thickness_mkm'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FILM-0002", "width_mm", "1050", "mm"],
         "lot_id = 'LOT-FILM-0002' AND prop_key = 'width_mm'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FILM-0002", "thickness_mkm", "12", "µm"],
         "lot_id = 'LOT-FILM-0002' AND prop_key = 'thickness_mkm'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FILM-0003", "width_mm", "1100", "mm"],
         "lot_id = 'LOT-FILM-0003' AND prop_key = 'width_mm'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FILM-0003", "thickness_mkm", "20", "µm"],
         "lot_id = 'LOT-FILM-0003' AND prop_key = 'thickness_mkm'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-PRT-0001", "width_mm", "1050", "mm"],
         "lot_id = 'LOT-PRT-0001' AND prop_key = 'width_mm'"),
])

M5_SEGMENTS_QUALITY_DEMO = ";\n".join([
    # ISA-95 Part 2: Process Segments (flexo print -> laminate -> slit / rewind)
    seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
         ["SEG-PRINT", None, "PRODUCTION", "Flexo print", "Flexographic printing on film"],
         "segment_id = 'SEG-PRINT'"),
    seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
         ["SEG-LAMINATE", None, "PRODUCTION", "Solventless lamination", "Laminate printed roll with BOPP"],
         "segment_id = 'SEG-LAMINATE'"),
    seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
         ["SEG-SLIT", None, "PRODUCTION", "Slitting", "Slit laminated roll into sleeve rolls"],
         "segment_id = 'SEG-SLIT'"),
    seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
         ["SEG-REWIND", None, "PRODUCTION", "Rewind/inspection", "Rewind and inspect laminated roll"],
         "segment_id = 'SEG-REWIND'"),
    # Segment specifications = canonical operation BOM/routing
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-PRINT:IN-FILM", "SEG-PRINT", "MCL-FILM", None, "CONSUMED", "500", "m"],
         "spec_id = 'SEG-PRINT:IN-FILM'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-PRINT:IN-INK-K", "SEG-PRINT", None, "INK-K", "CONSUMED", "2", "kg"],
         "spec_id = 'SEG-PRINT:IN-INK-K'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-PRINT:OUT-ROLL", "SEG-PRINT", None, "WIP-PRINTED-ROLL", "PRODUCED", "500", "m"],
         "spec_id = 'SEG-PRINT:OUT-ROLL'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-LAMINATE:IN-PRINTED", "SEG-LAMINATE", None, "WIP-PRINTED-ROLL", "CONSUMED", "500", "m"],
         "spec_id = 'SEG-LAMINATE:IN-PRINTED'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-LAMINATE:IN-BOPP", "SEG-LAMINATE", None, "FILM-BOPP20", "CONSUMED", "500", "m"],
         "spec_id = 'SEG-LAMINATE:IN-BOPP'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-LAMINATE:IN-GLUE", "SEG-LAMINATE", None, "GLUE-PUR", "CONSUMED", "1.5", "kg"],
         "spec_id = 'SEG-LAMINATE:IN-GLUE'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-LAMINATE:OUT-ROLL", "SEG-LAMINATE", None, "WIP-LAM-ROLL", "PRODUCED", "500", "m"],
         "spec_id = 'SEG-LAMINATE:OUT-ROLL'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-SLIT:IN-LAM", "SEG-SLIT", None, "WIP-LAM-ROLL", "CONSUMED", "500", "m"],
         "spec_id = 'SEG-SLIT:IN-LAM'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-SLIT:OUT-FG", "SEG-SLIT", None, "FG-SLEEVE-ROLL", "PRODUCED", "500", "m"],
         "spec_id = 'SEG-SLIT:OUT-FG'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-REWIND:IN-LAM", "SEG-REWIND", None, "WIP-LAM-ROLL", "CONSUMED", "500", "m"],
         "spec_id = 'SEG-REWIND:IN-LAM'"),
    seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
         ["SEG-REWIND:OUT-LAM", "SEG-REWIND", None, "WIP-LAM-ROLL", "PRODUCED", "500", "m"],
         "spec_id = 'SEG-REWIND:OUT-LAM'"),
    seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
         ["SEG-PRINT:EQ", "SEG-PRINT", "EQC-FLEXO-PRESS", None, "PRIMARY", "1"],
         "spec_id = 'SEG-PRINT:EQ'"),
    seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
         ["SEG-LAMINATE:EQ", "SEG-LAMINATE", "EQC-LAMINATOR", None, "PRIMARY", "1"],
         "spec_id = 'SEG-LAMINATE:EQ'"),
    seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
         ["SEG-SLIT:EQ", "SEG-SLIT", "EQC-SLITTER", None, "PRIMARY", "1"],
         "spec_id = 'SEG-SLIT:EQ'"),
    seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
         ["SEG-REWIND:EQ", "SEG-REWIND", "EQC-REWINDER", None, "PRIMARY", "1"],
         "spec_id = 'SEG-REWIND:EQ'"),
    seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
         ["SEG-PRINT:PERS", "SEG-PRINT", "PCL-PRINTER", None, "OPERATOR", "1"],
         "spec_id = 'SEG-PRINT:PERS'"),
    seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
         ["SEG-LAMINATE:PERS", "SEG-LAMINATE", "PCL-PRINTER", None, "OPERATOR", "1"],
         "spec_id = 'SEG-LAMINATE:PERS'"),
    seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
         ["SEG-SLIT:PERS", "SEG-SLIT", "PCL-SLITTER-OPERATOR", None, "OPERATOR", "1"],
         "spec_id = 'SEG-SLIT:PERS'"),
    seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
         ["SEG-REWIND:PERS", "SEG-REWIND", "PCL-ASSISTANT", None, "OPERATOR", "1"],
         "spec_id = 'SEG-REWIND:PERS'"),
    # Work Masters (duration includes setup/make-ready)
    seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
         ["WM-FLEXO-PRINT", "1", "SEG-PRINT", "240", "Flexo print incl. make-ready (master)"],
         "work_master_id = 'WM-FLEXO-PRINT' AND version = '1'"),
    seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
         ["WM-LAMINATE", "1", "SEG-LAMINATE", "180", "Solventless lamination (master)"],
         "work_master_id = 'WM-LAMINATE' AND version = '1'"),
    seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
         ["WM-SLIT", "1", "SEG-SLIT", "90", "Slitting (master)"],
         "work_master_id = 'WM-SLIT' AND version = '1'"),
    seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
         ["WM-REWIND", "1", "SEG-REWIND", "60", "Rewind/inspection (master)"],
         "work_master_id = 'WM-REWIND' AND version = '1'"),
    # ISA-95 Part 3: quality dictionaries (from the customer event catalog codes 137-163)
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-PRT-MISREGISTER", "Print misregistration", "QC"], "defect_type_id = 'DFT-PRT-MISREGISTER'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-PRT-CUTOFF", "Cut-off (print step) deviation", "QC"], "defect_type_id = 'DFT-PRT-CUTOFF'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-PRT-DOCTOR-STREAK", "Doctor blade streaks", "QC"], "defect_type_id = 'DFT-PRT-DOCTOR-STREAK'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-PRT-INK-SPIT", "Ink spits", "QC"], "defect_type_id = 'DFT-PRT-INK-SPIT'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-PRT-COLOR-DEV", "Color deviation (deltaE)", "QC"], "defect_type_id = 'DFT-PRT-COLOR-DEV'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-LAM-WRINKLE", "Lamination wrinkles", "QC"], "defect_type_id = 'DFT-LAM-WRINKLE'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-LAM-BLISTER", "Lamination blistering", "QC"], "defect_type_id = 'DFT-LAM-BLISTER'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-LAM-WAVE", "Waves on laminated material", "QC"], "defect_type_id = 'DFT-LAM-WAVE'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-SLT-TELESCOPE", "Roll telescoping", "QC"], "defect_type_id = 'DFT-SLT-TELESCOPE'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-SLT-TRIM", "Unremoved trim", "QC"], "defect_type_id = 'DFT-SLT-TRIM'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-SLT-SHIFT", "Slitting shift", "QC"], "defect_type_id = 'DFT-SLT-SHIFT'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-CYLINDER", None, "Printing cylinder damage/wear", "DFT-PRT-MISREGISTER"],
         "reason_code = 'RC-CYLINDER'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-INK-VISCOSITY", None, "Ink viscosity out of spec", "DFT-PRT-COLOR-DEV"],
         "reason_code = 'RC-INK-VISCOSITY'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-DOCTOR-BLADE", None, "Doctor blade assembly/wear", "DFT-PRT-DOCTOR-STREAK"],
         "reason_code = 'RC-DOCTOR-BLADE'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-TENSION", None, "Web tension out of spec", "DFT-LAM-WRINKLE"],
         "reason_code = 'RC-TENSION'"),
    # Demo print order (ISA-95 Part 4): schedule -> request -> 3 job orders
    seed("emc_work_schedule", ["schedule_id", "external_ref", "schedule_state", "start_time", "end_time"],
         ["SCH-PRINT-001", "ORD-2026-042", "RELEASED", "!TIMESTAMP '2026-07-24 00:00:00'", "!TIMESTAMP '2026-07-25 00:00:00'"],
         "schedule_id = 'SCH-PRINT-001'"),
    seed("emc_work_request", ["request_id", "schedule_id", "request_state", "priority", "product_definition_id", "quantity", "uom", "start_time", "end_time"],
         ["REQ-PRINT-001", "SCH-PRINT-001", "ACCEPTED", "1", "FG-SLEEVE-ROLL", "20000", "m", "!TIMESTAMP '2026-07-24 06:00:00'", "!TIMESTAMP '2026-07-24 18:00:00'"],
         "request_id = 'REQ-PRINT-001'"),
    # JO-PRINT-001 RUNNING on PR120 (flexo print) with open response below
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end, actual_start)
       SELECT 'a1000001-0000-0000-0000-000000000001', 'JO-PRINT-001', 'REQ-PRINT-001', 'WM-FLEXO-PRINT', '1', 'SEG-PRINT', 'PR120', 'RUNNING', 'START', '1',
              TIMESTAMP '2026-07-24 06:00:00', TIMESTAMP '2026-07-24 10:00:00', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-PRINT-001')""",
    # JO-PRINT-002 ALLOWED on LM210 (laminate) - ready to start
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a1000002-0000-0000-0000-000000000002', 'JO-PRINT-002', 'REQ-PRINT-001', 'WM-LAMINATE', '1', 'SEG-LAMINATE', 'LM210', 'ALLOWED', 'STORE', '2',
              TIMESTAMP '2026-07-24 10:00:00', TIMESTAMP '2026-07-24 13:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-PRINT-002')""",
    # JO-PRINT-003 NOT_ALLOWED on SL300 (slit) - not yet released by the dispatcher
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a1000003-0000-0000-0000-000000000003', 'JO-PRINT-003', 'REQ-PRINT-001', 'WM-SLIT', '1', 'SEG-SLIT', 'SL300', 'NOT_ALLOWED', 'STORE', '3',
              TIMESTAMP '2026-07-24 13:00:00', TIMESTAMP '2026-07-24 14:30:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-PRINT-003')""",
    # Requirement snapshots for the running job order (from SEG-PRINT specs)
    """INSERT INTO emc_job_order_material_req (job_no, definition_id, material_class_id, material_use, quantity, uom)
       SELECT 'JO-PRINT-001', definition_id, material_class_id, material_use, quantity, uom FROM emc_segment_material_spec
       WHERE segment_id = 'SEG-PRINT' AND NOT EXISTS (SELECT 1 FROM emc_job_order_material_req WHERE job_no = 'JO-PRINT-001')""",
    """INSERT INTO emc_job_order_equipment_req (job_no, equipment_class_id, equipment_id, equipment_use, quantity)
       SELECT 'JO-PRINT-001', equipment_class_id, equipment_id, equipment_use, quantity FROM emc_segment_equipment_spec
       WHERE segment_id = 'SEG-PRINT' AND NOT EXISTS (SELECT 1 FROM emc_job_order_equipment_req WHERE job_no = 'JO-PRINT-001')""",
    """INSERT INTO emc_job_order_personnel_req (job_no, personnel_class_id, person_id, personnel_use, quantity)
       SELECT 'JO-PRINT-001', personnel_class_id, person_id, personnel_use, quantity FROM emc_segment_personnel_spec
       WHERE segment_id = 'SEG-PRINT' AND NOT EXISTS (SELECT 1 FROM emc_job_order_personnel_req WHERE job_no = 'JO-PRINT-001')""",
    # Open job response + RUN interval + actuals for JO-PRINT-001 (Part 4 Work Performance)
    """INSERT INTO emc_job_response (response_id, job_no, job_state, actual_start)
       SELECT 'b1000001-0000-0000-0000-000000000001', 'JO-PRINT-001', 'RUNNING', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = 'JO-PRINT-001' AND job_state = 'RUNNING')""",
    """INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at)
       SELECT RANDOM_UUID(), 'b1000001-0000-0000-0000-000000000001', 'RUN_INTERVAL', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data WHERE response_id = 'b1000001-0000-0000-0000-000000000001' AND data_kind = 'RUN_INTERVAL' AND ended_at IS NULL)""",
    """INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use)
       SELECT RANDOM_UUID(), 'b1000001-0000-0000-0000-000000000001', 'PR120', 'PRIMARY'
       WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_actual WHERE response_id = 'b1000001-0000-0000-0000-000000000001')""",
    """INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use)
       SELECT RANDOM_UUID(), 'b1000001-0000-0000-0000-000000000001', 'EMP-P01', 'OPERATOR'
       WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_actual WHERE response_id = 'b1000001-0000-0000-0000-000000000001' AND person_id = 'EMP-P01')""",
    # Shift on PR120 with the printer assigned
    seed("emc_work_calendar", ["shift_id", "equipment_id", "shift_label", "planned_minutes", "state", "planned_start", "actual_start"],
         ["SHIFT-PRINT-1", "PR120", "MORNING", "480", "OPEN", "!TIMESTAMP '2026-07-24 06:00:00'", "!CURRENT_TIMESTAMP"],
         "shift_id = 'SHIFT-PRINT-1'"),
    """INSERT INTO emc_shift_assignment (id, shift_id, person_id)
       SELECT RANDOM_UUID(), 'SHIFT-PRINT-1', 'EMP-P01'
       WHERE NOT EXISTS (SELECT 1 FROM emc_shift_assignment WHERE shift_id = 'SHIFT-PRINT-1' AND person_id = 'EMP-P01')""",
    # Work record (production dossier / job bag) for JO-PRINT-001 with printing sections
    seed("emc_work_record", ["record_id", "job_no", "record_no"],
         ["WR-JO-PRINT-001", "JO-PRINT-001", "WREC-JO-PRINT-001"], "record_id = 'WR-JO-PRINT-001'"),
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-PRINT-001', 'colorControl', 'Color control', '{"deltaE2000":"1.2","viscositySec":"22","labTarget":"L52 a18 b25","labActual":"L51.8 a17.9 b25.4"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-PRINT-001' AND section_key = 'colorControl')""",
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-PRINT-001', 'imposition', 'Imposition', '{"cutOffMm":"520","registration":"OK","plateSet":"PL-2026-118"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-PRINT-001' AND section_key = 'imposition')""",
])

MIGRATIONS = [
    {"id": "emp_m1_overlay_tables", "sql": M1_OVERLAY_TABLES},
    {"id": "emp_m2_event_catalog", "sql": M2_EVENT_CATALOG},
    {"id": "emp_m3_equipment_personnel", "sql": M3_EQUIPMENT_PERSONNEL},
    {"id": "emp_m4_materials", "sql": M4_MATERIALS},
    {"id": "emp_m5_segments_quality_demo", "sql": M5_SEGMENTS_QUALITY_DEMO},
]


# ---------------------------------------------------------------------------
# BFF functions (overlay hub). Prefix emp_ - never reuse emc_* function names
# (findLatest lookup is global and would shadow the core implementations).
# ---------------------------------------------------------------------------

FUNCTIONS = []

FUNCTIONS.append(fn(
    "emp_eventdef_list",
    [F("section"), F("catalog")],
    OUT(RL("rows", [F("code"), F("name"), F("eventClass"), F("catalog"), F("section"),
                    F("operatorHint"), F("requiresLength"), F("requiresTime"), F("requiresComment"),
                    F("oeeBucket"), F("sixBigLoss"), F("sortOrder")])),
    [
        selN("defs",
             "SELECT d.code, d.name, d.event_class, COALESCE(x.catalog, '') AS catalog, "
             "COALESCE(x.section, '') AS section, COALESCE(x.operator_hint, '') AS operator_hint, "
             "d.requires_length, d.requires_time, d.requires_comment, d.oee_bucket, "
             "COALESCE(d.six_big_loss, '') AS six_big_loss, d.sort_order "
             "FROM emc_operations_event_definition d "
             "LEFT JOIN emp_eventdef_ext x ON x.code = d.code "
             "WHERE (? = '' OR x.section = ?) AND (? = '' OR x.catalog = ?) "
             "ORDER BY d.sort_order, d.code",
             ["${input.section}", "${input.section}", "${input.catalog}", "${input.catalog}"]),
        map_rows("rows", "${defs}", {
            "code": "${item.code}", "name": "${item.name}", "eventClass": "${item.event_class}",
            "catalog": "${item.catalog}", "section": "${item.section}",
            "operatorHint": "${item.operator_hint}", "requiresLength": "${item.requires_length}",
            "requiresTime": "${item.requires_time}", "requiresComment": "${item.requires_comment}",
            "oeeBucket": "${item.oee_bucket}", "sixBigLoss": "${item.six_big_loss}",
            "sortOrder": "${item.sort_order}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emp_roll_list",
    [F("definitionId")],
    OUT(RL("rows", [F("lotId"), F("barcode"), F("definitionId"), F("status"), F("location"),
                    F("quantity"), F("uom"), F("lengthM"), F("weightKg"),
                    F("widthMm"), F("thicknessMkm")])),
    [
        selN("lots",
             "SELECT l.lot_id, l.barcode, l.definition_id, l.status, "
             "COALESCE(l.storage_location, '') AS storage_location, l.quantity, l.base_uom, "
             "l.length_m, l.weight_kg, "
             "COALESCE(w.prop_value, '') AS width_mm, COALESCE(t.prop_value, '') AS thickness_mkm "
             "FROM emc_material_lot l "
             "LEFT JOIN emc_material_lot_property w ON w.lot_id = l.lot_id AND w.prop_key = 'width_mm' "
             "LEFT JOIN emc_material_lot_property t ON t.lot_id = l.lot_id AND t.prop_key = 'thickness_mkm' "
             "WHERE (? = '' OR l.definition_id = ?) ORDER BY l.lot_id",
             ["${input.definitionId}", "${input.definitionId}"]),
        map_rows("rows", "${lots}", {
            "lotId": "${item.lot_id}", "barcode": "${item.barcode}",
            "definitionId": "${item.definition_id}", "status": "${item.status}",
            "location": "${item.storage_location}", "quantity": "${item.quantity}",
            "uom": "${item.base_uom}", "lengthM": "${item.length_m}", "weightKg": "${item.weight_kg}",
            "widthMm": "${item.width_mm}", "thicknessMkm": "${item.thickness_mkm}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "emp_setuptag_list",
    [],
    OUT(RL("rows", [F("tag"), F("name"), F("description"), F("sortOrder")])),
    [
        selN("tags",
             "SELECT tag, COALESCE(name, '') AS name, COALESCE(description, '') AS description, "
             "sort_order FROM emp_setup_tag ORDER BY sort_order"),
        map_rows("rows", "${tags}", {
            "tag": "${item.tag}", "name": "${item.name}",
            "description": "${item.description}", "sortOrder": "${item.sort_order}"}),
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
        "name": "erp-mes-printing-hub-v1",
        "description": "ERP-MES Printing Hub (ISA-95 overlay): printing KPI counters, alert rules and BFF functions.",
        "type": "SINGLETON",
        "variables": [
            BPV("openPrintDowntimeCount", "Open availability (downtime) events on printing equipment", "oee", LONG_VAR, 0),
            BPV("activePrintJobCount", "RUNNING job orders on printing equipment", "production", LONG_VAR, 0),
            BPV("lowRollStockCount", "Stock rolls below minimum length (500 m)", "inventory", LONG_VAR, 0),
        ],
    },
]

# Work-unit devices reuse the core MIXIN blueprint emc-work-unit-v1 (global blueprint
# registry; deploy order is enforced by requires: erp-mes-core).
OBJECTS = [
    {"parentPath": "root.platform.devices", "name": "emp-pr120", "type": "DEVICE",
     "displayName": "Flexo Press PR120",
     "description": "Flexo press PR120 (ISA-95 equipment PR120).",
     "templateId": "emc-work-unit-v1"},
    {"parentPath": "root.platform.devices", "name": "emp-lm210", "type": "DEVICE",
     "displayName": "Laminator LM210",
     "description": "Solventless laminator LM210 (ISA-95 equipment LM210).",
     "templateId": "emc-work-unit-v1"},
    {"parentPath": "root.platform.devices", "name": "emp-sl300", "type": "DEVICE",
     "displayName": "Slitter SL300",
     "description": "Slitter SL300 (ISA-95 equipment SL300).",
     "templateId": "emc-work-unit-v1"},
]

# ----------------------------------------------------------------------------
# Bindings, alert rules, events
# ----------------------------------------------------------------------------

BINDINGS = [
    {"objectPath": HUB, "variable": "openPrintDowntimeCount",
     "query": ("SELECT COUNT(*) AS v FROM emc_operations_event e "
               "JOIN emc_operations_event_definition d ON d.code = e.definition_code "
               "WHERE e.status = 'OPEN' AND d.oee_bucket = 'AVAILABILITY' "
               "AND e.equipment_id IN " + PRINT_EQUIPMENT_SQL),
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "activePrintJobCount",
     "query": ("SELECT COUNT(*) AS v FROM emc_job_order "
               "WHERE dispatch_status = 'RUNNING' AND equipment_id IN " + PRINT_EQUIPMENT_SQL),
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "lowRollStockCount",
     "query": ("SELECT COUNT(*) AS v FROM emc_material_lot "
               "WHERE status = 'STOCK' AND length_m IS NOT NULL AND length_m < 500"),
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
]

ALERT_RULES = [
    {"name": "emp-critical-downtime", "objectPath": HUB, "watchVariable": "openPrintDowntimeCount",
     "conditionExpr": "self.openPrintDowntimeCount[\"value\"] >= 2", "eventName": "printDowntimeAlert",
     "enabled": True, "edgeTrigger": True, "delaySeconds": 0, "sustainWhileTrue": False},
    {"name": "emp-low-roll-stock", "objectPath": HUB, "watchVariable": "lowRollStockCount",
     "conditionExpr": "self.lowRollStockCount[\"value\"] > 0", "eventName": "lowRollStockAlert",
     "enabled": True, "edgeTrigger": True, "delaySeconds": 0, "sustainWhileTrue": False},
]

EVENTS = [
    {"id": "printDowntimeAlert", "roles": ["operator", "admin"]},
    {"id": "lowRollStockAlert", "roles": ["operator", "admin"]},
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
# on the live stand, +2 rows of safety margin). 1 grid row = 12px.
_FORM_H_BOOST = {
    "Зарегистрировать событие": 6, "Закрыть событие": 10,
    "Поставить рулон на линию": 7, "Списать материал": 5,
    "Зарегистрировать рулон": 14,
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
    {"reportId": "emp-print-job-board", "title": "Print Job Board (ISA-95 Job Orders)",
     "description": "Active job orders on printing equipment with dispatch status (Part 4 job list).",
     "query": """
SELECT o.job_order_id AS id, o.job_no, COALESCE(s.external_ref, '') AS external_ref,
       COALESCE(o.command, '') AS command, o.dispatch_status, o.equipment_id,
       COALESCE(r.product_definition_id, '') AS product_definition_id, r.quantity, COALESCE(r.uom, '') AS uom,
       o.planned_start, o.planned_end, o.created_at
FROM emc_job_order o
JOIN emc_work_request r ON r.request_id = o.request_id
JOIN emc_work_schedule s ON s.schedule_id = r.schedule_id
WHERE o.dispatch_status NOT IN ('ENDED', 'ABORTED', 'CANCELLED')
  AND o.equipment_id IN ('PR120', 'PR130', 'LM210', 'SL300', 'RW100')
ORDER BY o.planned_start ASC, o.job_no
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("id", "ID"), ("job_no", "Job #"), ("external_ref", "ERP Ref"), ("command", "Cmd"),
         ("dispatch_status", "Status"), ("equipment_id", "Equipment"), ("product_definition_id", "Product"),
         ("quantity", "Qty"), ("uom", "UOM"), ("planned_start", "Planned Start"), ("planned_end", "Planned End"),
         ("created_at", "Created")]]},
    {"reportId": "emp-downtime-by-code", "title": "Downtime by Event Code (Flexibase catalog)",
     "description": "Operations events aggregated by definition code with Flexibase section.",
     "query": """
SELECT d.code, d.name, COALESCE(x.section, '') AS section, COUNT(*) AS events,
       COALESCE(SUM(e.time_min), 0) AS total_time_min
FROM emc_operations_event e
JOIN emc_operations_event_definition d ON d.code = e.definition_code
LEFT JOIN emp_eventdef_ext x ON x.code = d.code
WHERE e.status IN ('OPEN', 'CLOSED')
GROUP BY d.code, d.name, x.section
ORDER BY total_time_min DESC, d.code
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("section", "Section"), ("events", "Events"),
         ("total_time_min", "Time min")]]},
    {"reportId": "emp-roll-stock", "title": "Roll & Material Stock",
     "description": "Material lots (rolls and ink cans) with roll dimensions from lot properties.",
     "query": """
SELECT l.lot_id, l.barcode, l.definition_id AS material_id, COALESCE(d.class_id, '') AS class_id,
       l.status, COALESCE(l.storage_location, '') AS storage_location,
       l.quantity, l.base_uom AS uom, l.length_m, l.weight_kg,
       COALESCE(w.prop_value, '') AS width_mm, COALESCE(t.prop_value, '') AS thickness_mkm
FROM emc_material_lot l
JOIN emc_material_definition d ON d.definition_id = l.definition_id
LEFT JOIN emc_material_lot_property w ON w.lot_id = l.lot_id AND w.prop_key = 'width_mm'
LEFT JOIN emc_material_lot_property t ON t.lot_id = l.lot_id AND t.prop_key = 'thickness_mkm'
ORDER BY l.lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("lot_id", "Lot ID"), ("barcode", "Barcode"), ("material_id", "Material"), ("class_id", "Class"),
         ("status", "Status"), ("storage_location", "Location"), ("quantity", "Qty"), ("uom", "UOM"),
         ("length_m", "Length m"), ("weight_kg", "Weight kg"), ("width_mm", "Width mm"),
         ("thickness_mkm", "Thickness µm")]]},
    # --- Catalog reports: option sources for form dropdowns ---
    {"reportId": "emp-eventdef-catalog", "title": "Print Event Code Catalog",
     "description": "Flexibase event codes as code/name options for form dropdowns.",
     "query": """
SELECT d.code, d.name, x.catalog, x.section
FROM emc_operations_event_definition d
JOIN emp_eventdef_ext x ON x.code = d.code
ORDER BY d.sort_order
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Code"), ("name", "Name"), ("catalog", "Catalog"), ("section", "Section")]]},
    {"reportId": "emp-setuptag-catalog", "title": "Zero Pull Setup Tags",
     "description": "Zero Pull setup tag dictionary (repeated setup attempts at OGP-120).",
     "query": """
SELECT tag AS code, COALESCE(name, '') AS name, COALESCE(description, '') AS description
FROM emp_setup_tag
ORDER BY sort_order
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("code", "Tag"), ("name", "Name"), ("description", "Description")]]},
]

DASHBOARDS = [
    _dashboard("root.platform.dashboards.emp-print-dispatch", "Диспетчер печати",
               "Диспетчеризация печати: доска заданий, запуск/пауза/завершение, KPI простоев.",
               [
                   _value_widget("kpiDowntime", "Открыто простоев", 0, 0, 28, 12, "openPrintDowntimeCount"),
                   _value_widget("kpiJobs", "Заданий в работе", 28, 0, 28, 12, "activePrintJobCount"),
                   _value_widget("kpiRolls", "Рулонов ниже минимума", 56, 0, 28, 12, "lowRollStockCount"),
                   _report_widget("jobs", "Задания печати", 0, 12, 56, 49, "root.platform.reports.emp-print-job-board",
                                  selectable=True, rowSelectionKey="job_no",
                                  rowParamsFromRowJson=json.dumps({"jobNo": "job_no", "dispatchStatus": "dispatch_status"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["dispatch_status", "equipment_id"]),
                                  statusDotColumnsJson=json.dumps(["dispatch_status"])),
                   _job_actions_widget("jobActions", "Действия с заданием", 56, 12, 28, 14, "EMP-P01", CORE_HUB),
                   _html_widget("jobActionsHint", "Логика статусов (ISA-95)", 56, 26, 28, 30,
                                _JOB_STATUS_LEGEND_HTML),
               ]),
    _dashboard("root.platform.dashboards.emp-print-events", "События и простои",
               "Регистрация событий по каталогу Flexibase, аналитика простоев, теги Zero Pull.",
               [
                   _form_widget("registerEvent", "Зарегистрировать событие", 0, 0, 28, 36, "emc_event_register",
                                [_sel("definitionCode", "Код события", "emp-eventdef-catalog", "code", "name", required=True),
                                 _sel("jobNo", "Задание", "emp-print-job-board", "job_no", "dispatch_status"),
                                 _static("equipmentId", "Оборудование",
                                         ["PR120", "PR130", "LM210", "SL300", "RW100"], default="PR120"),
                                 {"name": "timeMin", "label": "Длительность, мин", "type": "number", "defaultValue": ""},
                                 {"name": "lengthM", "label": "Метраж, м", "type": "number", "defaultValue": ""},
                                 {"name": "comment", "label": "Комментарий", "type": "textarea", "defaultValue": ""}],
                                "Зарегистрировать", CORE_HUB),
                   _form_widget("closeEvent", "Закрыть событие", 0, 36, 28, 11, "emc_event_close",
                                [_sel("eventId", "Событие", "emc-event-journal", "id", "name", required=True),
                                 {"name": "by", "label": "Кем", "type": "text", "defaultValue": "operator"}],
                                "Закрыть", CORE_HUB),
                   _report_widget("tags", "Теги Zero Pull", 0, 47, 28, 17, "root.platform.reports.emp-setuptag-catalog"),
                   _report_widget("downtime", "Простои по кодам", 28, 0, 56, 30, "root.platform.reports.emp-downtime-by-code",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["section"])),
                   _report_widget("catalog", "Каталог кодов событий", 28, 30, 56, 34, "root.platform.reports.emp-eventdef-catalog",
                                  filterable=True,
                                  columnFiltersJson=json.dumps(["catalog", "section"])),
               ]),
    _dashboard("root.platform.dashboards.emp-print-rolls", "Рулоны и материалы",
               "Склад рулонов и чернил, постановка на линию и расход по заданиям.",
               [
                   _report_widget("rolls", "Рулоны и материалы", 0, 0, 56, 56, "root.platform.reports.emp-roll-stock",
                                  selectable=True, rowSelectionKey="barcode",
                                  rowParamsFromRowJson=json.dumps({"barcode": "barcode"}),
                                  autoSelectFirstRow=True, filterable=True,
                                  columnFiltersJson=json.dumps(["status", "material_id"]),
                                  statusDotColumnsJson=json.dumps(["status"])),
                   _form_widget("place", "Поставить рулон на линию", 56, 0, 28, 16, "emc_matlot_placeOnLine",
                                [_sel("barcode", "Штрихкод рулона", "emp-roll-stock", "barcode", "material_id", required=True),
                                 _sel("jobNo", "Задание", "emp-print-job-board", "job_no", "dispatch_status", required=True)],
                                "Поставить", CORE_HUB),
                   _form_widget("consume", "Списать материал", 56, 16, 28, 16, "emc_matlot_consume",
                                [_sel("barcode", "Штрихкод рулона", "emp-roll-stock", "barcode", "material_id", required=True),
                                 {"name": "quantity", "label": "Количество", "type": "number", "defaultValue": "1"}],
                                "Списать", CORE_HUB),
                   _form_widget("register", "Зарегистрировать рулон", 56, 32, 28, 24, "emc_matlot_register",
                                [{"name": "lotId", "label": "Рулон (ID)", "type": "text", "required": True},
                                 {"name": "barcode", "label": "Штрихкод", "type": "text", "required": True},
                                 _sel("definitionId", "Материал", "emc-material-catalog", "code", "name", required=True),
                                 _static("storageLocation", "Склад", ["WH-PRINT", "WH-PR120-LS"], default="WH-PRINT"),
                                 {"name": "quantity", "label": "Количество", "type": "number", "defaultValue": "1"}],
                                "Зарегистрировать", CORE_HUB),
               ]),
]


# ----------------------------------------------------------------------------
# Assembly
# ----------------------------------------------------------------------------

bundle = {
    "version": "1.2.0",
    "displayName": "ERP-MES Printing (ISA-95)",
    "tablePrefix": "emp_",
    "schemaName": SCHEMA,
    "requires": [{"appId": "erp-mes-core", "minVersion": "1.1.0"}],
    "migrations": MIGRATIONS,
    "objects": OBJECTS,
    "functions": FUNCTIONS,
    "blueprints": BLUEPRINTS,
    "dashboards": DASHBOARDS,
    "bindings": BINDINGS,
    "reports": REPORTS,
    "alertRules": ALERT_RULES,
    "events": EVENTS,
    "operatorUi": {
        "appId": APP_ID,
        "title": "MES Полиграфия (ISA-95)",
        "defaultDashboard": "root.platform.dashboards.emp-print-dispatch",
        "dashboards": [
            {"path": "root.platform.dashboards.emp-print-dispatch", "title": "Диспетчер печати"},
            {"path": "root.platform.dashboards.emp-print-events", "title": "События и простои"},
            {"path": "root.platform.dashboards.emp-print-rolls", "title": "Рулоны и материалы"},
        ],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.emp-print-job-board", "title": "Print Job Board"},
            {"path": "root.platform.reports.emp-downtime-by-code", "title": "Downtime by Code"},
            {"path": "root.platform.reports.emp-roll-stock", "title": "Roll Stock"},
        ],
        "defaultReport": "root.platform.reports.emp-print-job-board",
    },
    "metadata": {
        "product": "erp-mes-printing",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "1.2.0 status-aware job action bar (Start/Pause/Resume/Complete enabled by dispatch_status, requires platform with function.buttonsJson); 1.1.1 form heights calibrated; 1.1.0 operator UI rework (requires erp-mes-core 1.1.0)",
    },
}

with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
    json.dump(bundle, fh, ensure_ascii=False, indent=2)
    fh.write("\n")
print("Wrote", BUNDLE_OUT, "migrations=", len(MIGRATIONS), "functions=", len(FUNCTIONS),
      "event_codes=", len(EVENT_CODES), "setup_tags=", len(SETUP_TAGS))
