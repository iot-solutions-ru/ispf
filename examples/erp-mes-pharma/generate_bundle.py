#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ERP-MES Pharma (ISA-95/ISA-88) overlay bundle generator.

Pharmaceutical industry (solid dosage form) overlay on top of the industry-neutral
erp-mes-core bundle, built from GMP / ISA-88 batch / eBR / 21 CFR Part 11 canon:
  pha_m1 - own overlay tables (event-code extension, e-signature records, serialization)
  pha_m2 - pharma event code catalog (20 PHA-* codes) seeded into the core
           emc_operations_event_definition + pha_eventdef_ext
  pha_m3 - solid-dosage equipment hierarchy (dispense booth .. cartoner) + personnel
  pha_m4 - pharma materials (API, excipients, packaging, WIP, FG) with quarantine lots
  pha_m5 - wet granulation routing (8 process segments + work masters), quality
           dictionaries, demo order (JO-PH-001 RUNNING) + eBR work record sections

Runs with the SAME schemaName as the core (app_erp_mes_core): overlay migrations,
functions and bindings see the core emc_* tables through search_path. Own tables
use the pha_ prefix (tablePrefix validator scope). Migration ids use the pha_m
prefix (global root.platform.migrations namespace, must not clash with emc_*/emp_*).

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
APP_ID = "erp-mes-pharma"
SCHEMA = "app_erp_mes_core"
HUB = "root.platform.singleton-blueprints.erp-mes-pharma-hub-v1"
CORE_HUB = "root.platform.singleton-blueprints.erp-mes-core-hub-v1"
PHARMA_EQUIPMENT = ("DSP-01", "GRN-01", "FBD-01", "BLN-01", "TPR-01", "COT-01", "BLS-01", "CRT-01")
PHARMA_EQUIPMENT_SQL = "(" + ", ".join("'" + e + "'" for e in PHARMA_EQUIPMENT) + ")"

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
# Pharma event code catalog (GMP/ISA-88 canon, ~20 PHA-* codes)
# (code, event_class, name, requires_length, requires_time, requires_comment,
#  oee_bucket, six_big_loss, sort_order, section, operator_hint, gmp_critical)
# ---------------------------------------------------------------------------

EVENT_CODES = [
    ("PHA-LC", "SETUP", "Line clearance: проверка очистки линии", False, True, True,
     "AVAILABILITY", "SETUP_ADJUSTMENT", 10, "All",
     "Указать предыдущий продукт/серию, кто проверил очистку", True),
    ("PHA-CIP", "SETUP", "CIP: мойка на месте", False, True, False,
     "AVAILABILITY", "PLANNED_STOP", 20, "Granulation/Coating",
     "Указать цикл мойки, моющий раствор", False),
    ("PHA-CLEAN", "SETUP", "Ручная уборка помещения/оборудования", False, True, True,
     "AVAILABILITY", "PLANNED_STOP", 30, "All",
     "Указать помещение/оборудование, вид уборки (текущая/генеральная)", True),
    ("PHA-CALIB", "DOWNTIME", "Поверка/калибровка приборов", False, True, True,
     "AVAILABILITY", "PLANNED_STOP", 40, "All",
     "Указать прибор, № свидетельства о поверке", True),
    ("PHA-ENV", "DOWNTIME", "Отклонение микроклимата (температура/влажность/перепад)", False, True, True,
     "AVAILABILITY", "UNPLANNED_STOP", 50, "All",
     "Указать фактические значения, помещение", True),
    ("PHA-QA-HOLD", "QUALITY", "Ожидание решения ОКК (карантин)", False, True, True,
     "AVAILABILITY", "UNPLANNED_STOP", 60, "All",
     "Указать № протокола/отбора проб", False),
    ("PHA-DEVIATION", "QUALITY", "Девиация: регистрация и расследование", False, False, True,
     "NONE", None, 70, "All",
     "Указать № девиации, краткое описание", True),
    ("PHA-IPC-WAIT", "QUALITY", "Ожидание результата IPC", False, True, False,
     "AVAILABILITY", "UNPLANNED_STOP", 80, "All",
     "Указать контролируемый параметр", False),
    ("PHA-IPC-SAMPLE", "PRODUCTION", "Отбор проб IPC (в процессе)", False, False, True,
     "NONE", None, 90, "All",
     "Указать точку отбора, параметр", False),
    ("PHA-PROD", "PRODUCTION", "Производство: чистый процесс (таблетирование/фасовка)", True, True, False,
     "PERFORMANCE", "REDUCED_SPEED", 100, "All",
     "Только чистое время процесса", False),
    ("PHA-REPROCESS", "PRODUCTION", "Переработка/возврат на доработку", False, True, True,
     "PERFORMANCE", "REDUCED_SPEED", 110, "All",
     "Указать основание для переработки, № разрешения ОКК", True),
    ("PHA-UTIL", "DOWNTIME", "Авария инженерных систем (HVAC/пар/вода)", False, True, True,
     "AVAILABILITY", "BREAKDOWN", 120, "All",
     "Указать систему, характер аварии", False),
    ("PHA-WEIGH-ERR", "QUALITY", "Ошибка взвешивания / расхождение тары", False, False, True,
     "PERFORMANCE", "REDUCED_SPEED", 130, "All",
     "Указать весы, фактическое/целевое значение", False),
    ("PHA-NO-OPER", "DOWNTIME", "Недоукомплектованная смена", False, False, True,
     "AVAILABILITY", "UNPLANNED_STOP", 140, "All",
     "Указать отсутствующую позицию, причину", False),
    ("PHA-MICRO", "DOWNTIME", "Микроостановы (забивка сита, заедание пуансонов и т.п.)", False, False, True,
     "PERFORMANCE", "REDUCED_SPEED", 150, "All",
     "Указать узел, характер остановки", False),
    ("PHA-CODE-ERR", "DOWNTIME", "Сбой сериализации/верификации кодов", False, True, True,
     "PERFORMANCE", "REDUCED_SPEED", 160, "Packaging",
     "Указать код/короб, сообщение системы сериализации", False),
    ("PHA-STARTUP", "SETUP", "Запуск линии после простоя", False, True, True,
     "AVAILABILITY", "PLANNED_STOP", 170, "All",
     "Указать причину предыдущей остановки", False),
    ("PHA-NO-MAT", "DOWNTIME", "Ожидание сырья или упаковочных материалов", False, True, True,
     "AVAILABILITY", "UNPLANNED_STOP", 180, "All",
     "Указать наименование материала, причину отсутствия", False),
    ("PHA-DOC-WAIT", "DOWNTIME", "Ожидание документации/этикетирования", False, True, True,
     "AVAILABILITY", "UNPLANNED_STOP", 190, "Packaging",
     "Указать вид документации", False),
    ("PHA-TRAIN", "DOWNTIME", "Плановый простой: обучение GMP", False, True, True,
     "NONE", None, 200, "All",
     "Указать тему обучения", False),
]


# ---------------------------------------------------------------------------
# Migrations (pha_m1-pha_m5), SQL joined with "; " - no semicolons inside statements
# ---------------------------------------------------------------------------

M1_OVERLAY_TABLES = ";\n".join([
    # Overlay-only tables
    """CREATE TABLE IF NOT EXISTS pha_eventdef_ext (
       code VARCHAR(64) PRIMARY KEY,
       section VARCHAR(64),
       operator_hint VARCHAR(512),
       gmp_critical BOOLEAN NOT NULL DEFAULT false)""",
    # 21 CFR Part 11 electronic signatures (meaning = AUTHORED/REVIEWED/APPROVED)
    """CREATE TABLE IF NOT EXISTS pha_esign_record (
       id UUID PRIMARY KEY,
       entity_type VARCHAR(32) NOT NULL,
       entity_id VARCHAR(128) NOT NULL,
       signer VARCHAR(64) NOT NULL,
       meaning VARCHAR(16) NOT NULL,
       comment_text VARCHAR(512),
       signed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    # Serialization with aggregation (parent_code = case/pallet code)
    """CREATE TABLE IF NOT EXISTS pha_serial_record (
       serial_code VARCHAR(128) PRIMARY KEY,
       job_no VARCHAR(64),
       lot_id VARCHAR(64),
       gtin VARCHAR(32),
       parent_code VARCHAR(128),
       status VARCHAR(32) NOT NULL DEFAULT 'COMMISSIONED',
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
])

# Pharma event code catalog -> core event definitions + overlay extension rows
_M2_STATEMENTS = []
for (code, event_class, name, req_len, req_time, req_comment,
     oee_bucket, six_big_loss, sort_order, section, hint, gmp_critical) in EVENT_CODES:
    _M2_STATEMENTS.append(
        seed("emc_operations_event_definition",
             ["code", "event_class", "name", "requires_length", "requires_time", "requires_comment",
              "oee_bucket", "six_big_loss", "sort_order"],
             [code, event_class, name, bool_sql(req_len), bool_sql(req_time), bool_sql(req_comment),
              oee_bucket, six_big_loss, str(sort_order)],
             f"code = '{code}'"))
    _M2_STATEMENTS.append(
        seed("pha_eventdef_ext",
             ["code", "section", "operator_hint", "gmp_critical"],
             [code, section, hint, bool_sql(gmp_critical)],
             f"code = '{code}'"))
M2_EVENT_CATALOG = ";\n".join(_M2_STATEMENTS)

M3_EQUIPMENT_PERSONNEL = ";\n".join([
    # ISA-95 Part 1/2: solid dosage equipment classes (work-unit level)
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-DISPENSE-BOOTH", "Weighing/dispensing booth", "WORK_UNIT", None],
         "class_id = 'EQC-DISPENSE-BOOTH'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-GRANULATOR", "High-shear wet granulator", "WORK_UNIT", None],
         "class_id = 'EQC-GRANULATOR'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-FLUID-BED-DRYER", "Fluid bed dryer", "WORK_UNIT", None],
         "class_id = 'EQC-FLUID-BED-DRYER'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-BLENDER", "Bin blender", "WORK_UNIT", None],
         "class_id = 'EQC-BLENDER'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-TABLET-PRESS", "Rotary tablet press", "WORK_UNIT", None],
         "class_id = 'EQC-TABLET-PRESS'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-COATER", "Perforated drum coater", "WORK_UNIT", None],
         "class_id = 'EQC-COATER'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-BLISTER-LINE", "Blister packaging line", "WORK_UNIT", None],
         "class_id = 'EQC-BLISTER-LINE'"),
    seed("emc_equipment_class", ["class_id", "description", "equipment_level", "parent_class_id"],
         ["EQC-CARTONER", "Cartoner with serialization", "WORK_UNIT", None],
         "class_id = 'EQC-CARTONER'"),
    # Role-based hierarchy: ENT-PHARMA -> SITE-PH-01 -> areas -> work units
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["ENT-PHARMA", None, "ENTERPRISE", None, "ENT-PHARMA", "Pharma demo enterprise"],
         "equipment_id = 'ENT-PHARMA'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["SITE-PH-01", None, "SITE", "ENT-PHARMA", "ENT-PHARMA/SITE-PH-01", "Pharma demo site"],
         "equipment_id = 'SITE-PH-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["AREA-SOLID", None, "AREA", "SITE-PH-01", "ENT-PHARMA/SITE-PH-01/AREA-SOLID", "Solid dosage area"],
         "equipment_id = 'AREA-SOLID'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["AREA-PACK", None, "AREA", "SITE-PH-01", "ENT-PHARMA/SITE-PH-01/AREA-PACK", "Packaging area"],
         "equipment_id = 'AREA-PACK'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["DSP-01", "EQC-DISPENSE-BOOTH", "WORK_UNIT", "AREA-SOLID", "ENT-PHARMA/SITE-PH-01/AREA-SOLID/DSP-01", "Dispensing booth DSP-01"],
         "equipment_id = 'DSP-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["GRN-01", "EQC-GRANULATOR", "WORK_UNIT", "AREA-SOLID", "ENT-PHARMA/SITE-PH-01/AREA-SOLID/GRN-01", "Granulator GRN-01"],
         "equipment_id = 'GRN-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["FBD-01", "EQC-FLUID-BED-DRYER", "WORK_UNIT", "AREA-SOLID", "ENT-PHARMA/SITE-PH-01/AREA-SOLID/FBD-01", "Fluid bed dryer FBD-01"],
         "equipment_id = 'FBD-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["BLN-01", "EQC-BLENDER", "WORK_UNIT", "AREA-SOLID", "ENT-PHARMA/SITE-PH-01/AREA-SOLID/BLN-01", "Bin blender BLN-01"],
         "equipment_id = 'BLN-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["TPR-01", "EQC-TABLET-PRESS", "WORK_UNIT", "AREA-SOLID", "ENT-PHARMA/SITE-PH-01/AREA-SOLID/TPR-01", "Tablet press TPR-01"],
         "equipment_id = 'TPR-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["COT-01", "EQC-COATER", "WORK_UNIT", "AREA-SOLID", "ENT-PHARMA/SITE-PH-01/AREA-SOLID/COT-01", "Coater COT-01"],
         "equipment_id = 'COT-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["BLS-01", "EQC-BLISTER-LINE", "WORK_UNIT", "AREA-PACK", "ENT-PHARMA/SITE-PH-01/AREA-PACK/BLS-01", "Blister line BLS-01"],
         "equipment_id = 'BLS-01'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["CRT-01", "EQC-CARTONER", "WORK_UNIT", "AREA-PACK", "ENT-PHARMA/SITE-PH-01/AREA-PACK/CRT-01", "Cartoner CRT-01 (serialization)"],
         "equipment_id = 'CRT-01'"),
    # Status-model warehouses as equipment (pharma canon: quarantine/released/rejected)
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-QUAR", None, "STORAGE_ZONE", "SITE-PH-01", "ENT-PHARMA/SITE-PH-01/WH-QUAR", "Карантин (quarantine warehouse)"],
         "equipment_id = 'WH-QUAR'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-REL", None, "STORAGE_ZONE", "SITE-PH-01", "ENT-PHARMA/SITE-PH-01/WH-REL", "Отпущено (released warehouse)"],
         "equipment_id = 'WH-REL'"),
    seed("emc_equipment", ["equipment_id", "class_id", "equipment_level", "parent_id", "hierarchy_path", "description"],
         ["WH-REJ", None, "STORAGE_ZONE", "SITE-PH-01", "ENT-PHARMA/SITE-PH-01/WH-REJ", "Забраковано (rejected warehouse)"],
         "equipment_id = 'WH-REJ'"),
    # Equipment properties (extension without migrations)
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["TPR-01", "punches", "33", None],
         "equipment_id = 'TPR-01' AND prop_key = 'punches'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["TPR-01", "max_tab_per_h", "200000", "tab/h"],
         "equipment_id = 'TPR-01' AND prop_key = 'max_tab_per_h'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["COT-01", "drum_kg", "120", "kg"],
         "equipment_id = 'COT-01' AND prop_key = 'drum_kg'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["AREA-SOLID", "cleanroom_class", "ISO 8", None],
         "equipment_id = 'AREA-SOLID' AND prop_key = 'cleanroom_class'"),
    seed("emc_equipment_property", ["equipment_id", "prop_key", "prop_value", "uom"],
         ["AREA-PACK", "cleanroom_class", "ISO 8", None],
         "equipment_id = 'AREA-PACK' AND prop_key = 'cleanroom_class'"),
    # ISA-95 Part 2: Personnel model
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-PHARMACIST", "Pharmacist / technologist"], "class_id = 'PCL-PHARMACIST'"),
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-OPERATOR-PH", "Production operator (pharma)"], "class_id = 'PCL-OPERATOR-PH'"),
    seed("emc_personnel_class", ["class_id", "description"],
         ["PCL-QA", "QA specialist"], "class_id = 'PCL-QA'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-H01", "Olga Technologist", "PCL-PHARMACIST"], "person_id = 'EMP-H01'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-H02", "Sergey Operator", "PCL-OPERATOR-PH"], "person_id = 'EMP-H02'"),
    seed("emc_person", ["person_id", "person_name", "personnel_class_id"],
         ["EMP-H03", "Maria QA", "PCL-QA"], "person_id = 'EMP-H03'"),
    # Qualification: by equipment class (press operator) XOR by site instance (QA everywhere)
    seed("emc_person_qualification", ["person_id", "equipment_id", "equipment_class_id", "qualification"],
         ["EMP-H02", None, "EQC-TABLET-PRESS", "OPERATE"],
         "person_id = 'EMP-H02' AND equipment_class_id = 'EQC-TABLET-PRESS'"),
    seed("emc_person_qualification", ["person_id", "equipment_id", "equipment_class_id", "qualification"],
         ["EMP-H03", "SITE-PH-01", None, "OPERATE"],
         "person_id = 'EMP-H03' AND equipment_id = 'SITE-PH-01'"),
])


M4_MATERIALS = ";\n".join([
    # ISA-95 Part 2: pharma material classes (parents MCL-RAW/MCL-WIP/MCL-FG come from the core)
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-API", "Active pharmaceutical ingredients", "MCL-RAW"], "class_id = 'MCL-API'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-EXCIPIENT", "Excipients", "MCL-RAW"], "class_id = 'MCL-EXCIPIENT'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-PACK", "Packaging materials", "MCL-RAW"], "class_id = 'MCL-PACK'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-WIP-PH", "WIP pharma (granulate/blend/tablets)", "MCL-WIP"], "class_id = 'MCL-WIP-PH'"),
    seed("emc_material_class", ["class_id", "description", "parent_class_id"],
         ["MCL-FG-PH", "Finished goods pharma", "MCL-FG"], "class_id = 'MCL-FG-PH'"),
    # Material definitions
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["API-PARACETAMOL", "MCL-API", "RAW", "kg", "Paracetamol API"], "definition_id = 'API-PARACETAMOL'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["EXC-LACTOSE-MONO", "MCL-EXCIPIENT", "RAW", "kg", "Lactose monohydrate"], "definition_id = 'EXC-LACTOSE-MONO'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["EXC-MCC", "MCL-EXCIPIENT", "RAW", "kg", "Microcrystalline cellulose"], "definition_id = 'EXC-MCC'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["EXC-PVP-K30", "MCL-EXCIPIENT", "RAW", "kg", "Povidone K30 (binder)"], "definition_id = 'EXC-PVP-K30'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["EXC-MG-STEARATE", "MCL-EXCIPIENT", "RAW", "kg", "Magnesium stearate (lubricant)"], "definition_id = 'EXC-MG-STEARATE'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["EXC-OPADRY", "MCL-EXCIPIENT", "RAW", "kg", "Opadry film coating"], "definition_id = 'EXC-OPADRY'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["SOLV-WFI", "MCL-EXCIPIENT", "RAW", "kg", "Вода очищенная"], "definition_id = 'SOLV-WFI'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["PACK-PVC-FOIL", "MCL-PACK", "RAW", "m", "PVC forming foil"], "definition_id = 'PACK-PVC-FOIL'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["PACK-ALU-FOIL", "MCL-PACK", "RAW", "m", "Aluminium lidding foil"], "definition_id = 'PACK-ALU-FOIL'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["PACK-CARTON", "MCL-PACK", "RAW", "pcs", "Folding carton"], "definition_id = 'PACK-CARTON'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["PACK-LEAFLET", "MCL-PACK", "RAW", "pcs", "Patient information leaflet"], "definition_id = 'PACK-LEAFLET'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-PREMIX", "MCL-WIP-PH", "WIP", "kg", "Premix after dispensing"], "definition_id = 'WIP-PREMIX'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-GRANULATE", "MCL-WIP-PH", "WIP", "kg", "Wet granulate"], "definition_id = 'WIP-GRANULATE'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-FINAL-BLEND", "MCL-WIP-PH", "WIP", "kg", "Final blend for compression"], "definition_id = 'WIP-FINAL-BLEND'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-TABLET-CORE", "MCL-WIP-PH", "WIP", "kg", "Tablet cores"], "definition_id = 'WIP-TABLET-CORE'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["WIP-TABLET-COATED", "MCL-WIP-PH", "WIP", "kg", "Coated tablets"], "definition_id = 'WIP-TABLET-COATED'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["FG-TAB-BLISTER", "MCL-FG-PH", "FG", "pcs", "Парацетамол 500 мг №10 блистер"], "definition_id = 'FG-TAB-BLISTER'"),
    seed("emc_material_definition", ["definition_id", "class_id", "kind", "base_uom", "description"],
         ["FG-TAB-CARTON", "MCL-FG-PH", "FG", "pcs", "Парацетамол 500 мг №10 в коробе (сериализован)"], "definition_id = 'FG-TAB-CARTON'"),
    # Lots with GMP disposition (QUARANTINE / RELEASED)
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "disposition", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-API-0001", "BC-API-0001", "API-PARACETAMOL", "STOCK", "QUARANTINE", "WH-QUAR", "25", "kg", "25"],
         "lot_id = 'LOT-API-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "disposition", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-API-0002", "BC-API-0002", "API-PARACETAMOL", "STOCK", "RELEASED", "WH-REL", "30", "kg", "30"],
         "lot_id = 'LOT-API-0002'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "disposition", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-EXC-0001", "BC-EXC-0001", "EXC-LACTOSE-MONO", "STOCK", "RELEASED", "WH-REL", "50", "kg", "50"],
         "lot_id = 'LOT-EXC-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "disposition", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-EXC-0002", "BC-EXC-0002", "EXC-MG-STEARATE", "STOCK", "RELEASED", "WH-REL", "10", "kg", "10"],
         "lot_id = 'LOT-EXC-0002'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "disposition", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-WIP-PH-0001", "BC-WIP-PH-0001", "WIP-FINAL-BLEND", "STOCK", "RELEASED", "WH-REL", "80", "kg", "80"],
         "lot_id = 'LOT-WIP-PH-0001'"),
    seed("emc_material_lot", ["lot_id", "barcode", "definition_id", "status", "disposition", "storage_location", "quantity", "base_uom", "weight_kg"],
         ["LOT-FG-PH-0001", "BC-FG-PH-0001", "FG-TAB-BLISTER", "STOCK", "QUARANTINE", "WH-QUAR", "5000", "pcs", "420"],
         "lot_id = 'LOT-FG-PH-0001'"),
    # Lot properties (pharma canon: GTIN, batch number, expiry/retest dates)
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-API-0001", "gtin", "04607012340001", None],
         "lot_id = 'LOT-API-0001' AND prop_key = 'gtin'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-API-0001", "batch_no", "B2026-041", None],
         "lot_id = 'LOT-API-0001' AND prop_key = 'batch_no'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-API-0001", "expiry_date", "2028-06-30", None],
         "lot_id = 'LOT-API-0001' AND prop_key = 'expiry_date'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-API-0001", "retest_date", "2027-06-30", None],
         "lot_id = 'LOT-API-0001' AND prop_key = 'retest_date'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FG-PH-0001", "gtin", "04607012340018", None],
         "lot_id = 'LOT-FG-PH-0001' AND prop_key = 'gtin'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FG-PH-0001", "batch_no", "B2026-118", None],
         "lot_id = 'LOT-FG-PH-0001' AND prop_key = 'batch_no'"),
    seed("emc_material_lot_property", ["lot_id", "prop_key", "prop_value", "uom"],
         ["LOT-FG-PH-0001", "expiry_date", "2029-01-31", None],
         "lot_id = 'LOT-FG-PH-0001' AND prop_key = 'expiry_date'"),
])


# Wet granulation routing: (segment_id, name, description, equipment_class, work_master, duration_min)
SEGMENTS = [
    ("SEG-DISPENSE", "Взвешивание и дозирование", "Weighing and dispensing with second-person verification",
     "EQC-DISPENSE-BOOTH", "WM-DISPENSE", "120"),
    ("SEG-GRANULATE", "Влажная грануляция", "High-shear wet granulation",
     "EQC-GRANULATOR", "WM-GRANULATE", "90"),
    ("SEG-DRY", "Сушка в кипящем слое", "Fluid bed drying with LOD control",
     "EQC-FLUID-BED-DRYER", "WM-DRY", "60"),
    ("SEG-BLEND-FINAL", "Финальное смешение/опудривание", "Final blending and lubrication",
     "EQC-BLENDER", "WM-BLEND-FINAL", "30"),
    ("SEG-COMPRESS", "Таблетирование", "Tablet compression on rotary press",
     "EQC-TABLET-PRESS", "WM-COMPRESS", "240"),
    ("SEG-COAT", "Оболочка", "Film coating in perforated drum",
     "EQC-COATER", "WM-COAT", "180"),
    ("SEG-BLISTER", "Блистерование", "Blister packaging (PVC/Alu)",
     "EQC-BLISTER-LINE", "WM-BLISTER", "300"),
    ("SEG-CARTON", "Картонаж и сериализация", "Cartoning with serialization and aggregation",
     "EQC-CARTONER", "WM-CARTON", "200"),
]

# Segment material specs: (spec_id, segment_id, material_class_id, definition_id, use, qty, uom)
SEGMENT_MATERIAL_SPECS = [
    ("SEG-DISPENSE:IN-API", "SEG-DISPENSE", None, "API-PARACETAMOL", "CONSUMED", "20", "kg"),
    ("SEG-DISPENSE:IN-LACTOSE", "SEG-DISPENSE", None, "EXC-LACTOSE-MONO", "CONSUMED", "55", "kg"),
    ("SEG-DISPENSE:IN-MCC", "SEG-DISPENSE", None, "EXC-MCC", "CONSUMED", "23", "kg"),
    ("SEG-DISPENSE:OUT-PREMIX", "SEG-DISPENSE", None, "WIP-PREMIX", "PRODUCED", "98", "kg"),
    ("SEG-GRANULATE:IN-PREMIX", "SEG-GRANULATE", None, "WIP-PREMIX", "CONSUMED", "98", "kg"),
    ("SEG-GRANULATE:IN-PVP", "SEG-GRANULATE", None, "EXC-PVP-K30", "CONSUMED", "2", "kg"),
    ("SEG-GRANULATE:IN-WFI", "SEG-GRANULATE", None, "SOLV-WFI", "CONSUMED", "15", "kg"),
    ("SEG-GRANULATE:OUT-GRAN", "SEG-GRANULATE", None, "WIP-GRANULATE", "PRODUCED", "115", "kg"),
    ("SEG-DRY:IN-GRAN", "SEG-DRY", None, "WIP-GRANULATE", "CONSUMED", "115", "kg"),
    ("SEG-DRY:OUT-GRAN", "SEG-DRY", None, "WIP-GRANULATE", "PRODUCED", "100", "kg"),
    ("SEG-BLEND-FINAL:IN-GRAN", "SEG-BLEND-FINAL", None, "WIP-GRANULATE", "CONSUMED", "100", "kg"),
    ("SEG-BLEND-FINAL:IN-MGST", "SEG-BLEND-FINAL", None, "EXC-MG-STEARATE", "CONSUMED", "1", "kg"),
    ("SEG-BLEND-FINAL:OUT-BLEND", "SEG-BLEND-FINAL", None, "WIP-FINAL-BLEND", "PRODUCED", "101", "kg"),
    ("SEG-COMPRESS:IN-BLEND", "SEG-COMPRESS", None, "WIP-FINAL-BLEND", "CONSUMED", "101", "kg"),
    ("SEG-COMPRESS:OUT-CORE", "SEG-COMPRESS", None, "WIP-TABLET-CORE", "PRODUCED", "100", "kg"),
    ("SEG-COAT:IN-CORE", "SEG-COAT", None, "WIP-TABLET-CORE", "CONSUMED", "100", "kg"),
    ("SEG-COAT:IN-OPADRY", "SEG-COAT", None, "EXC-OPADRY", "CONSUMED", "3", "kg"),
    ("SEG-COAT:OUT-COATED", "SEG-COAT", None, "WIP-TABLET-COATED", "PRODUCED", "103", "kg"),
    ("SEG-BLISTER:IN-COATED", "SEG-BLISTER", None, "WIP-TABLET-COATED", "CONSUMED", "103", "kg"),
    ("SEG-BLISTER:IN-PVC", "SEG-BLISTER", None, "PACK-PVC-FOIL", "CONSUMED", "500", "m"),
    ("SEG-BLISTER:IN-ALU", "SEG-BLISTER", None, "PACK-ALU-FOIL", "CONSUMED", "500", "m"),
    ("SEG-BLISTER:OUT-FG", "SEG-BLISTER", None, "FG-TAB-BLISTER", "PRODUCED", "50000", "pcs"),
    ("SEG-CARTON:IN-BLISTER", "SEG-CARTON", None, "FG-TAB-BLISTER", "CONSUMED", "50000", "pcs"),
    ("SEG-CARTON:IN-CARTON", "SEG-CARTON", None, "PACK-CARTON", "CONSUMED", "5000", "pcs"),
    ("SEG-CARTON:IN-LEAFLET", "SEG-CARTON", None, "PACK-LEAFLET", "CONSUMED", "5000", "pcs"),
    ("SEG-CARTON:OUT-FG", "SEG-CARTON", None, "FG-TAB-CARTON", "PRODUCED", "5000", "pcs"),
]

_M5_STATEMENTS = []
# ISA-95 Part 2: process segments + specifications + work masters
for (segment_id, name, description, equipment_class, work_master, duration) in SEGMENTS:
    _M5_STATEMENTS.append(
        seed("emc_process_segment", ["segment_id", "parent_id", "operations_type", "name", "description"],
             [segment_id, None, "PRODUCTION", name, description],
             f"segment_id = '{segment_id}'"))
    _M5_STATEMENTS.append(
        seed("emc_segment_equipment_spec", ["spec_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
             [f"{segment_id}:EQ", segment_id, equipment_class, None, "PRIMARY", "1"],
             f"spec_id = '{segment_id}:EQ'"))
    _M5_STATEMENTS.append(
        seed("emc_segment_personnel_spec", ["spec_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
             [f"{segment_id}:PERS", segment_id, "PCL-OPERATOR-PH", None, "OPERATOR", "1"],
             f"spec_id = '{segment_id}:PERS'"))
    _M5_STATEMENTS.append(
        seed("emc_work_master", ["work_master_id", "version", "segment_id", "duration_min", "description"],
             [work_master, "1", segment_id, duration, f"{name} (master)"],
             f"work_master_id = '{work_master}' AND version = '1'"))
for spec in SEGMENT_MATERIAL_SPECS:
    _M5_STATEMENTS.append(
        seed("emc_segment_material_spec", ["spec_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
             list(spec), f"spec_id = '{spec[0]}'"))
_M5_STATEMENTS.extend([
    # ISA-95 Part 3: quality dictionaries (solid dosage canon)
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-WEIGHT-VAR", "Отклонение массы таблетки", "QC"], "defect_type_id = 'DFT-WEIGHT-VAR'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-HARDNESS", "Твёрдость вне спецификации", "QC"], "defect_type_id = 'DFT-HARDNESS'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-FRIABILITY", "Истираемость", "QC"], "defect_type_id = 'DFT-FRIABILITY'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-DISSOLUTION", "Растворение/распадаемость", "QC"], "defect_type_id = 'DFT-DISSOLUTION'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-CAPPING", "Расслоение (capping)", "QC"], "defect_type_id = 'DFT-CAPPING'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-STICKING", "Прилипание к пуансонам", "QC"], "defect_type_id = 'DFT-STICKING'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-CONTAM", "Загрязнение/посторонние включения", "QC"], "defect_type_id = 'DFT-CONTAM'"),
    seed("emc_defect_type", ["defect_type_id", "description", "category"],
         ["DFT-CODE-LEG", "Нечитаемый код DataMatrix", "QC"], "defect_type_id = 'DFT-CODE-LEG'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-MOISTURE", None, "LOD вне нормы", "DFT-CAPPING"],
         "reason_code = 'RC-MOISTURE'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-GRAN-SIZE", None, "Гранулометрия", "DFT-WEIGHT-VAR"],
         "reason_code = 'RC-GRAN-SIZE'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-PUNCH-WEAR", None, "Износ пуансонов", "DFT-STICKING"],
         "reason_code = 'RC-PUNCH-WEAR'"),
    seed("emc_reason_code", ["reason_code", "parent_code", "description", "default_defect_type_id"],
         ["RC-RAW-SPEC", None, "Сырьё вне спецификации", "DFT-CONTAM"],
         "reason_code = 'RC-RAW-SPEC'"),
    # Demo order (ISA-95 Part 4): schedule -> request -> 3 job orders
    seed("emc_work_schedule", ["schedule_id", "external_ref", "schedule_state", "start_time", "end_time"],
         ["SCH-PH-001", "PO-2026-118", "RELEASED", "!TIMESTAMP '2026-07-24 00:00:00'", "!TIMESTAMP '2026-07-25 00:00:00'"],
         "schedule_id = 'SCH-PH-001'"),
    seed("emc_work_request", ["request_id", "schedule_id", "request_state", "priority", "product_definition_id", "quantity", "uom", "start_time", "end_time"],
         ["REQ-PH-001", "SCH-PH-001", "ACCEPTED", "1", "FG-TAB-CARTON", "50000", "pcs", "!TIMESTAMP '2026-07-24 06:00:00'", "!TIMESTAMP '2026-07-24 18:00:00'"],
         "request_id = 'REQ-PH-001'"),
    # JO-PH-001 RUNNING on TPR-01 (compression) with open response below
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end, actual_start)
       SELECT 'a2000001-0000-0000-0000-000000000001', 'JO-PH-001', 'REQ-PH-001', 'WM-COMPRESS', '1', 'SEG-COMPRESS', 'TPR-01', 'RUNNING', 'START', '1',
              TIMESTAMP '2026-07-24 06:00:00', TIMESTAMP '2026-07-24 10:00:00', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-PH-001')""",
    # JO-PH-002 ALLOWED on COT-01 (coating) - ready to start
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a2000002-0000-0000-0000-000000000002', 'JO-PH-002', 'REQ-PH-001', 'WM-COAT', '1', 'SEG-COAT', 'COT-01', 'ALLOWED', 'STORE', '2',
              TIMESTAMP '2026-07-24 10:00:00', TIMESTAMP '2026-07-24 13:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-PH-002')""",
    # JO-PH-003 NOT_ALLOWED on BLS-01 (blister) - not yet released by the dispatcher
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version, segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a2000003-0000-0000-0000-000000000003', 'JO-PH-003', 'REQ-PH-001', 'WM-BLISTER', '1', 'SEG-BLISTER', 'BLS-01', 'NOT_ALLOWED', 'STORE', '3',
              TIMESTAMP '2026-07-24 13:00:00', TIMESTAMP '2026-07-24 18:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-PH-003')""",
    # Requirement snapshots for the running job order (from SEG-COMPRESS specs)
    """INSERT INTO emc_job_order_material_req (job_no, definition_id, material_class_id, material_use, quantity, uom)
       SELECT 'JO-PH-001', definition_id, material_class_id, material_use, quantity, uom FROM emc_segment_material_spec
       WHERE segment_id = 'SEG-COMPRESS' AND NOT EXISTS (SELECT 1 FROM emc_job_order_material_req WHERE job_no = 'JO-PH-001')""",
    """INSERT INTO emc_job_order_equipment_req (job_no, equipment_class_id, equipment_id, equipment_use, quantity)
       SELECT 'JO-PH-001', equipment_class_id, equipment_id, equipment_use, quantity FROM emc_segment_equipment_spec
       WHERE segment_id = 'SEG-COMPRESS' AND NOT EXISTS (SELECT 1 FROM emc_job_order_equipment_req WHERE job_no = 'JO-PH-001')""",
    """INSERT INTO emc_job_order_personnel_req (job_no, personnel_class_id, person_id, personnel_use, quantity)
       SELECT 'JO-PH-001', personnel_class_id, person_id, personnel_use, quantity FROM emc_segment_personnel_spec
       WHERE segment_id = 'SEG-COMPRESS' AND NOT EXISTS (SELECT 1 FROM emc_job_order_personnel_req WHERE job_no = 'JO-PH-001')""",
    # Open job response + RUN interval + actuals for JO-PH-001 (Part 4 Work Performance)
    """INSERT INTO emc_job_response (response_id, job_no, job_state, actual_start)
       SELECT 'b2000001-0000-0000-0000-000000000001', 'JO-PH-001', 'RUNNING', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = 'JO-PH-001' AND job_state = 'RUNNING')""",
    """INSERT INTO emc_job_response_data (id, response_id, data_kind, started_at)
       SELECT RANDOM_UUID(), 'b2000001-0000-0000-0000-000000000001', 'RUN_INTERVAL', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response_data WHERE response_id = 'b2000001-0000-0000-0000-000000000001' AND data_kind = 'RUN_INTERVAL' AND ended_at IS NULL)""",
    """INSERT INTO emc_equipment_actual (id, response_id, equipment_id, equipment_use)
       SELECT RANDOM_UUID(), 'b2000001-0000-0000-0000-000000000001', 'TPR-01', 'PRIMARY'
       WHERE NOT EXISTS (SELECT 1 FROM emc_equipment_actual WHERE response_id = 'b2000001-0000-0000-0000-000000000001')""",
    """INSERT INTO emc_personnel_actual (id, response_id, person_id, personnel_use)
       SELECT RANDOM_UUID(), 'b2000001-0000-0000-0000-000000000001', 'EMP-H02', 'OPERATOR'
       WHERE NOT EXISTS (SELECT 1 FROM emc_personnel_actual WHERE response_id = 'b2000001-0000-0000-0000-000000000001' AND person_id = 'EMP-H02')""",
    # Shift on TPR-01 with the press operator assigned
    seed("emc_work_calendar", ["shift_id", "equipment_id", "shift_label", "planned_minutes", "state", "planned_start", "actual_start"],
         ["SHIFT-PH-1", "TPR-01", "MORNING", "480", "OPEN", "!TIMESTAMP '2026-07-24 06:00:00'", "!CURRENT_TIMESTAMP"],
         "shift_id = 'SHIFT-PH-1'"),
    """INSERT INTO emc_shift_assignment (id, shift_id, person_id)
       SELECT RANDOM_UUID(), 'SHIFT-PH-1', 'EMP-H02'
       WHERE NOT EXISTS (SELECT 1 FROM emc_shift_assignment WHERE shift_id = 'SHIFT-PH-1' AND person_id = 'EMP-H02')""",
    # eBR: work record (electronic batch record) for JO-PH-001 with GMP sections
    seed("emc_work_record", ["record_id", "job_no", "record_no"],
         ["WR-JO-PH-001", "JO-PH-001", "WREC-JO-PH-001"], "record_id = 'WR-JO-PH-001'"),
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-PH-001', 'dispensingProtocol', 'Dispensing protocol (second-person)', '{"premixLot":"LOT-WIP-PH-0001","weighedBy":"EMP-H02","verifiedBy":"EMP-H01","principle":"second-person"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-PH-001' AND section_key = 'dispensingProtocol')""",
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-PH-001', 'ipcResults', 'IPC results', '{"avgWeightMg":"602","targetWeightMg":"600","hardnessN":"85","friabilityPct":"0.4","disintegrationMin":"4"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-PH-001' AND section_key = 'ipcResults')""",
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-PH-001', 'lineClearance', 'Line clearance', '{"previousProduct":"API-PARACETAMOL B2026-038","cleanedBy":"EMP-H02","verifiedBy":"EMP-H03","status":"CLEARED"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-PH-001' AND section_key = 'lineClearance')""",
    """INSERT INTO emc_work_record_section (record_id, section_key, title, content_json)
       SELECT 'WR-JO-PH-001', 'deviations', 'Deviations', '{"count":"1","items":[{"id":"DEV-2026-07","desc":"Кратковременное превышение влажности в коридоре, влияния на серию нет","status":"CLOSED"}]}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_work_record_section WHERE record_id = 'WR-JO-PH-001' AND section_key = 'deviations')""",
    # 21 CFR Part 11: QA release signature for the RELEASED api lot (consistent pair:
    # LOT-API-0002 is RELEASED, the signature documents exactly that release)
    """INSERT INTO pha_esign_record (id, entity_type, entity_id, signer, meaning, comment_text)
       SELECT 'c2000001-0000-0000-0000-000000000001', 'LOT', 'LOT-API-0002', 'EMP-H03', 'APPROVED', 'Выпуск сырья по протоколу QC-2026-041'
       WHERE NOT EXISTS (SELECT 1 FROM pha_esign_record WHERE entity_type = 'LOT' AND entity_id = 'LOT-API-0002' AND signer = 'EMP-H03' AND meaning = 'APPROVED')""",
    # Serialization demo: 1 aggregated case with 2 serialized packs for LOT-FG-PH-0001
    seed("pha_serial_record", ["serial_code", "job_no", "lot_id", "gtin", "parent_code", "status"],
         ["DM-BOX-0000000001", "JO-PH-003", "LOT-FG-PH-0001", "04607012340018", None, "COMMISSIONED"],
         "serial_code = 'DM-BOX-0000000001'"),
    seed("pha_serial_record", ["serial_code", "job_no", "lot_id", "gtin", "parent_code", "status"],
         ["DM-PACK-0000000001", "JO-PH-003", "LOT-FG-PH-0001", "04607012340018", "DM-BOX-0000000001", "COMMISSIONED"],
         "serial_code = 'DM-PACK-0000000001'"),
    seed("pha_serial_record", ["serial_code", "job_no", "lot_id", "gtin", "parent_code", "status"],
         ["DM-PACK-0000000002", "JO-PH-003", "LOT-FG-PH-0001", "04607012340018", "DM-BOX-0000000001", "COMMISSIONED"],
         "serial_code = 'DM-PACK-0000000002'"),
])
M5_SEGMENTS_QUALITY_DEMO = ";\n".join(_M5_STATEMENTS)

MIGRATIONS = [
    {"id": "pha_m1_overlay_tables", "sql": M1_OVERLAY_TABLES},
    {"id": "pha_m2_event_catalog", "sql": M2_EVENT_CATALOG},
    {"id": "pha_m3_equipment_personnel", "sql": M3_EQUIPMENT_PERSONNEL},
    {"id": "pha_m4_materials", "sql": M4_MATERIALS},
    {"id": "pha_m5_segments_quality_demo", "sql": M5_SEGMENTS_QUALITY_DEMO},
]


# ---------------------------------------------------------------------------
# BFF functions (overlay hub). Prefix pha_ - never reuse emc_* function names
# (findLatest lookup is global and would shadow the core implementations).
# ---------------------------------------------------------------------------

FUNCTIONS = []

FUNCTIONS.append(fn(
    "pha_eventdef_list",
    [F("section")],
    OUT(RL("rows", [F("code"), F("name"), F("eventClass"), F("section"), F("operatorHint"),
                    F("gmpCritical"), F("requiresLength"), F("requiresTime"), F("requiresComment"),
                    F("oeeBucket"), F("sixBigLoss"), F("sortOrder")])),
    [
        selN("defs",
             "SELECT d.code, d.name, d.event_class, COALESCE(x.section, '') AS section, "
             "COALESCE(x.operator_hint, '') AS operator_hint, "
             "CASE WHEN x.gmp_critical THEN 'true' ELSE 'false' END AS gmp_critical, "
             "d.requires_length, d.requires_time, d.requires_comment, d.oee_bucket, "
             "COALESCE(d.six_big_loss, '') AS six_big_loss, d.sort_order "
             "FROM emc_operations_event_definition d "
             "LEFT JOIN pha_eventdef_ext x ON x.code = d.code "
             "WHERE (? = '' OR x.section = ?) "
             "ORDER BY d.sort_order, d.code",
             ["${input.section}", "${input.section}"]),
        map_rows("rows", "${defs}", {
            "code": "${item.code}", "name": "${item.name}", "eventClass": "${item.event_class}",
            "section": "${item.section}", "operatorHint": "${item.operator_hint}",
            "gmpCritical": "${item.gmp_critical}", "requiresLength": "${item.requires_length}",
            "requiresTime": "${item.requires_time}", "requiresComment": "${item.requires_comment}",
            "oeeBucket": "${item.oee_bucket}", "sixBigLoss": "${item.six_big_loss}",
            "sortOrder": "${item.sort_order}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "pha_lot_release",
    [F("lotId"), F("by"), F("comment")],
    OUT(F("lotId"), F("disposition")),
    [
        sel1("lot", "SELECT lot_id, status, COALESCE(disposition, '') AS disposition "
                    "FROM emc_material_lot WHERE lot_id = ?",
             ["${input.lotId}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Lot not found"),
        fail_ne("lot.disposition", "QUARANTINE", "INVALID_STATE",
                "Release allowed only from QUARANTINE"),
        ex("UPDATE emc_material_lot SET disposition = 'RELEASED', status = 'STOCK', "
           "version_no = version_no + 1 WHERE lot_id = ?",
           ["${input.lotId}"]),
        ex("INSERT INTO pha_esign_record (id, entity_type, entity_id, signer, meaning, comment_text) "
           "VALUES (RANDOM_UUID(), 'LOT', ?, ?, 'APPROVED', NULLIF(?, ''))",
           ["${input.lotId}", "${input.by}", "${input.comment}"]),
        ret({"error_code": "OK", "error_message": "",
             "lotId": "${input.lotId}", "disposition": "RELEASED"}),
    ],
))

FUNCTIONS.append(fn(
    "pha_lot_reject",
    [F("lotId"), F("by"), F("reason")],
    OUT(F("lotId"), F("disposition")),
    [
        sel1("lot", "SELECT lot_id, COALESCE(disposition, '') AS disposition "
                    "FROM emc_material_lot WHERE lot_id = ?",
             ["${input.lotId}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Lot not found"),
        sel1("guard", "SELECT COUNT(*) AS cnt FROM (VALUES ('QUARANTINE'), ('RELEASED')) v(d) WHERE d = ?",
             ["${lot.disposition}"]),
        fail_ne("guard.cnt", "1", "INVALID_STATE",
                "Reject allowed from QUARANTINE or RELEASED"),
        ex("UPDATE emc_material_lot SET disposition = 'REJECTED', status = 'BLOCKED_QC', "
           "version_no = version_no + 1 WHERE lot_id = ?",
           ["${input.lotId}"]),
        ex("INSERT INTO pha_esign_record (id, entity_type, entity_id, signer, meaning, comment_text) "
           "VALUES (RANDOM_UUID(), 'LOT', ?, ?, 'REVIEWED', NULLIF(?, ''))",
           ["${input.lotId}", "${input.by}", "${input.reason}"]),
        ret({"error_code": "OK", "error_message": "",
             "lotId": "${input.lotId}", "disposition": "REJECTED"}),
    ],
))

FUNCTIONS.append(fn(
    "pha_esign_list",
    [F("entityId")],
    OUT(RL("rows", [F("entityType"), F("entityId"), F("signer"), F("meaning"),
                    F("comment"), F("signedAt")])),
    [
        selN("sigs",
             "SELECT entity_type, entity_id, signer, meaning, "
             "COALESCE(comment_text, '') AS comment_text, signed_at "
             "FROM pha_esign_record WHERE (? = '' OR entity_id = ?) ORDER BY signed_at DESC",
             ["${input.entityId}", "${input.entityId}"]),
        map_rows("rows", "${sigs}", {
            "entityType": "${item.entity_type}", "entityId": "${item.entity_id}",
            "signer": "${item.signer}", "meaning": "${item.meaning}",
            "comment": "${item.comment_text}", "signedAt": "${item.signed_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "pha_serial_register",
    [F("serialCode"), F("jobNo"), F("lotId"), F("gtin"), F("parentCode")],
    OUT(F("serialCode"), F("status")),
    [
        fail_null("input.serialCode", "VALIDATION", "serialCode required"),
        sel1("dup", "SELECT serial_code FROM pha_serial_record WHERE serial_code = ?",
             ["${input.serialCode}"]),
        when({"var": "dup", "notNull": True}, [
            ret({"error_code": "DUPLICATE_SERIAL", "error_message": "Serial code already registered",
                 "serialCode": "${input.serialCode}", "status": ""}),
        ]),
        ex("INSERT INTO pha_serial_record (serial_code, job_no, lot_id, gtin, parent_code, status) "
           "VALUES (?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), 'COMMISSIONED')",
           ["${input.serialCode}", "${input.jobNo}", "${input.lotId}",
            "${input.gtin}", "${input.parentCode}"]),
        ret({"error_code": "OK", "error_message": "",
             "serialCode": "${input.serialCode}", "status": "COMMISSIONED"}),
    ],
))

FUNCTIONS.append(fn(
    "pha_serial_list",
    [F("jobNo")],
    OUT(RL("rows", [F("serialCode"), F("gtin"), F("lotId"), F("jobNo"),
                    F("parentCode"), F("status"), F("createdAt")])),
    [
        selN("serials",
             "SELECT serial_code, COALESCE(gtin, '') AS gtin, COALESCE(lot_id, '') AS lot_id, "
             "COALESCE(job_no, '') AS job_no, COALESCE(parent_code, '') AS parent_code, "
             "status, created_at "
             "FROM pha_serial_record WHERE (? = '' OR job_no = ?) ORDER BY created_at, serial_code",
             ["${input.jobNo}", "${input.jobNo}"]),
        map_rows("rows", "${serials}", {
            "serialCode": "${item.serial_code}", "gtin": "${item.gtin}",
            "lotId": "${item.lot_id}", "jobNo": "${item.job_no}",
            "parentCode": "${item.parent_code}", "status": "${item.status}",
            "createdAt": "${item.created_at}"}),
        ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
    ],
))

FUNCTIONS.append(fn(
    "pha_dispense_weigh",
    [F("barcode"), F("targetQty"), F("actualQty"), F("tolerancePct"),
     F("weighedBy"), F("verifiedBy")],
    OUT(F("lotId"), F("actualQty")),
    [
        sel1("lot", "SELECT lot_id FROM emc_material_lot WHERE barcode = ?",
             ["${input.barcode}"]),
        fail_null("lot", "LOT_NOT_FOUND", "Lot not found"),
        fail_null("input.targetQty", "VALIDATION", "targetQty required"),
        fail_null("input.actualQty", "VALIDATION", "actualQty required"),
        sel1("same", "SELECT CASE WHEN ? = ? THEN '1' ELSE '0' END AS v",
             ["${input.weighedBy}", "${input.verifiedBy}"]),
        when({"var": "same.v", "equals": "1"}, [
            ret({"error_code": "SECOND_PERSON_REQUIRED",
                 "error_message": "Second person verification required",
                 "lotId": "", "actualQty": ""}),
        ]),
        sel1("tol",
             "SELECT CASE WHEN ABS(CAST(NULLIF(?, '') AS NUMERIC) - CAST(NULLIF(?, '') AS NUMERIC)) "
             "<= CAST(NULLIF(?, '') AS NUMERIC) * CAST(NULLIF(?, '') AS NUMERIC) / 100 "
             "THEN '1' ELSE '0' END AS v",
             ["${input.actualQty}", "${input.targetQty}", "${input.targetQty}", "${input.tolerancePct}"]),
        when({"var": "tol.v", "equals": "0"}, [
            ret({"error_code": "TOLERANCE_EXCEEDED",
                 "error_message": "Weight deviation exceeds tolerance",
                 "lotId": "", "actualQty": ""}),
        ]),
        ex("INSERT INTO pha_esign_record (id, entity_type, entity_id, signer, meaning, comment_text) "
           "VALUES (RANDOM_UUID(), 'DISPENSE', ?, ?, 'AUTHORED', CONCAT('target=', ?, ' actual=', ?))",
           ["${lot.lot_id}", "${input.weighedBy}", "${input.targetQty}", "${input.actualQty}"]),
        ex("INSERT INTO pha_esign_record (id, entity_type, entity_id, signer, meaning, comment_text) "
           "VALUES (RANDOM_UUID(), 'DISPENSE', ?, ?, 'REVIEWED', CONCAT('target=', ?, ' actual=', ?))",
           ["${lot.lot_id}", "${input.verifiedBy}", "${input.targetQty}", "${input.actualQty}"]),
        ret({"error_code": "OK", "error_message": "",
             "lotId": "${lot.lot_id}", "actualQty": "${input.actualQty}"}),
    ],
))

# ----------------------------------------------------------------------------
# Blueprints (variable schemas) + live objects
# ----------------------------------------------------------------------------

LONG_VAR = {"name": "longValue", "fields": [{"name": "value", "type": "LONG"}]}


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
        "name": "erp-mes-pharma-hub-v1",
        "description": "ERP-MES Pharma Hub (ISA-95/ISA-88 overlay): GMP quality counters, alert rules and BFF functions.",
        "type": "SINGLETON",
        "variables": [
            BPV("quarantineLotCount", "Material lots in QUARANTINE disposition", "quality", LONG_VAR, 0),
            BPV("openDeviationCount", "Defect records in REGISTERED/CONFIRMED state", "quality", LONG_VAR, 0),
            BPV("activePharmaJobCount", "RUNNING job orders on pharma equipment", "production", LONG_VAR, 0),
        ],
    },
]

OBJECTS = []

# ----------------------------------------------------------------------------
# Bindings, alert rules, events
# ----------------------------------------------------------------------------

BINDINGS = [
    {"objectPath": HUB, "variable": "quarantineLotCount",
     "query": "SELECT COUNT(*) AS v FROM emc_material_lot WHERE disposition = 'QUARANTINE'",
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "openDeviationCount",
     "query": "SELECT COUNT(*) AS v FROM emc_defect_record WHERE status IN ('REGISTERED', 'CONFIRMED')",
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
    {"objectPath": HUB, "variable": "activePharmaJobCount",
     "query": ("SELECT COUNT(*) AS v FROM emc_job_order "
               "WHERE dispatch_status = 'RUNNING' AND equipment_id IN " + PHARMA_EQUIPMENT_SQL),
     "refresh": "interval", "refreshIntervalMs": 30000, "valueField": "v", "enabled": True},
]

ALERT_RULES = [
    {"name": "pha-quarantine-aging", "objectPath": HUB, "watchVariable": "quarantineLotCount",
     "conditionExpr": "self.quarantineLotCount[\"value\"] >= 2", "eventName": "quarantineAgingAlert",
     "enabled": True, "edgeTrigger": True, "delaySeconds": 0, "sustainWhileTrue": False},
    {"name": "pha-deviation-open", "objectPath": HUB, "watchVariable": "openDeviationCount",
     "conditionExpr": "self.openDeviationCount[\"value\"] >= 1", "eventName": "deviationOpenAlert",
     "enabled": True, "edgeTrigger": True, "delaySeconds": 0, "sustainWhileTrue": False},
]

EVENTS = [
    {"id": "quarantineAgingAlert", "roles": ["operator", "admin"]},
    {"id": "deviationOpenAlert", "roles": ["operator", "admin"]},
]

# ----------------------------------------------------------------------------
# Dashboards, reports
# ----------------------------------------------------------------------------

def _report_widget(key, title, x, y, w, h, report_path, page_size=25):
    return {"key": key, "type": "report", "title": title, "x": x, "y": y, "w": w, "h": h,
            "settingsJson": json.dumps({"reportPath": report_path, "pageSize": page_size})}


def _form_widget(key, title, x, y, w, h, function_name, fields, button_label, object_path=HUB):
    return {"key": key, "type": "function-form", "title": title, "x": x, "y": y, "w": w, "h": h,
            "settingsJson": json.dumps({"objectPath": object_path, "functionName": function_name,
                                        "buttonLabel": button_label, "fieldsJson": json.dumps(fields)})}


def _html_widget(key, title, x, y, w, h, html):
    return {"key": key, "type": "html-snippet", "title": title, "x": x, "y": y, "w": w, "h": h,
            "settingsJson": json.dumps({"htmlJson": json.dumps({"html": html})})}


def _dashboard(path, title, description, widgets):
    return {"path": path, "title": title,
            "layoutJson": json.dumps({"columns": 84, "rowHeight": 8, "widgets": widgets})}


REPORTS = [
    {"reportId": "pha-job-board", "title": "Pharma Job Board (ISA-95/ISA-88)",
     "description": "Active job orders on solid dosage and packaging lines with dispatch status.",
     "query": """
SELECT o.job_order_id AS id, o.job_no, COALESCE(s.external_ref, '') AS external_ref,
       COALESCE(o.command, '') AS command, o.dispatch_status, o.equipment_id,
       COALESCE(r.product_definition_id, '') AS product_definition_id, r.quantity, COALESCE(r.uom, '') AS uom,
       o.planned_start, o.planned_end, o.created_at
FROM emc_job_order o
JOIN emc_work_request r ON r.request_id = o.request_id
JOIN emc_work_schedule s ON s.schedule_id = r.schedule_id
WHERE o.dispatch_status NOT IN ('ENDED', 'ABORTED', 'CANCELLED')
  AND o.equipment_id IN """ + PHARMA_EQUIPMENT_SQL + """
ORDER BY o.planned_start ASC, o.job_no
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("id", "ID"), ("job_no", "Job #"), ("external_ref", "ERP Ref"), ("command", "Cmd"),
         ("dispatch_status", "Status"), ("equipment_id", "Line"), ("product_definition_id", "Product"),
         ("quantity", "Qty"), ("uom", "UOM"), ("planned_start", "Planned Start"),
         ("planned_end", "Planned End"), ("created_at", "Created")]]},
    {"reportId": "pha-quarantine-lots", "title": "Quarantine & Batch Release",
     "description": "Lots in QUARANTINE disposition with GTIN, batch number and expiry date.",
     "query": """
SELECT l.lot_id, l.barcode, l.definition_id AS material_id, l.status,
       COALESCE(l.disposition, '') AS disposition, l.quantity, l.base_uom AS uom,
       COALESCE(l.storage_location, '') AS storage_location,
       COALESCE(pb.prop_value, '') AS batch_no, COALESCE(pg.prop_value, '') AS gtin,
       COALESCE(pe.prop_value, '') AS expiry_date
FROM emc_material_lot l
LEFT JOIN emc_material_lot_property pb ON pb.lot_id = l.lot_id AND pb.prop_key = 'batch_no'
LEFT JOIN emc_material_lot_property pg ON pg.lot_id = l.lot_id AND pg.prop_key = 'gtin'
LEFT JOIN emc_material_lot_property pe ON pe.lot_id = l.lot_id AND pe.prop_key = 'expiry_date'
WHERE l.disposition = 'QUARANTINE'
ORDER BY l.lot_id
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("lot_id", "Lot ID"), ("barcode", "Barcode"), ("material_id", "Material"),
         ("batch_no", "Batch #"), ("gtin", "GTIN"), ("expiry_date", "Expiry"),
         ("quantity", "Qty"), ("uom", "UOM"), ("storage_location", "Warehouse"),
         ("status", "Status"), ("disposition", "Disposition")]]},
    {"reportId": "pha-serial-report", "title": "Serial Code Registry",
     "description": "DataMatrix serial codes with GTIN and aggregation (parent code).",
     "query": """
SELECT serial_code, COALESCE(gtin, '') AS gtin, COALESCE(lot_id, '') AS lot_id,
       COALESCE(job_no, '') AS job_no, COALESCE(parent_code, '') AS parent_code,
       status, created_at
FROM pha_serial_record
ORDER BY created_at DESC, serial_code
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("serial_code", "Serial Code"), ("gtin", "GTIN"), ("lot_id", "Lot ID"),
         ("job_no", "Job #"), ("parent_code", "Aggregate"), ("status", "Status"),
         ("created_at", "Created")]]},
    {"reportId": "pha-esign-audit", "title": "E-Signature Audit (21 CFR Part 11)",
     "description": "Electronic signature journal: who signed what, meaning and time.",
     "query": """
SELECT entity_type, entity_id, signer, meaning,
       COALESCE(comment_text, '') AS comment_text, signed_at
FROM pha_esign_record
ORDER BY signed_at DESC
""",
     "columns": [{"field": f, "label": l} for f, l in [
         ("entity_type", "Entity"), ("entity_id", "ID"), ("signer", "Signer"),
         ("meaning", "Meaning"), ("comment_text", "Comment"), ("signed_at", "Signed At")]]},
]

DASHBOARDS = [
    _dashboard("root.platform.dashboards.pha-production", "Производство (фарма, ISA-88)",
               "Solid dosage production: job board, job-order lifecycle and verified dispensing.",
               [
                   _report_widget("jobs", "Pharma Job Orders", 0, 0, 54, 50,
                                  "root.platform.reports.pha-job-board"),
                   _form_widget("start", "Start Job", 54, 0, 30, 10, "emc_joborder_start",
                                [{"name": "jobNo", "label": "Job #", "type": "text"},
                                 {"name": "personId", "label": "Operator", "type": "text", "default": "EMP-H02"}],
                                "Start", CORE_HUB),
                   _form_widget("pause", "Pause Job", 54, 10, 30, 8, "emc_joborder_pause",
                                [{"name": "jobNo", "label": "Job #", "type": "text"}], "Pause", CORE_HUB),
                   _form_widget("resume", "Resume Job", 54, 18, 30, 8, "emc_joborder_resume",
                                [{"name": "jobNo", "label": "Job #", "type": "text"}], "Resume", CORE_HUB),
                   _form_widget("complete", "Complete Job", 54, 26, 30, 8, "emc_joborder_complete",
                                [{"name": "jobNo", "label": "Job #", "type": "text"}], "Complete", CORE_HUB),
                   _form_widget("dispense", "Dispensing (double verification)", 54, 34, 30, 16,
                                "pha_dispense_weigh",
                                [{"name": "barcode", "label": "Lot barcode", "type": "text", "default": "BC-EXC-0001"},
                                 {"name": "targetQty", "label": "Target qty", "type": "number", "default": "100"},
                                 {"name": "actualQty", "label": "Actual qty", "type": "number", "default": ""},
                                 {"name": "tolerancePct", "label": "Tolerance %", "type": "number", "default": "2"},
                                 {"name": "weighedBy", "label": "Weighed by", "type": "text", "default": "EMP-H02"},
                                 {"name": "verifiedBy", "label": "Verified by", "type": "text", "default": "EMP-H01"}],
                                "Verify & Sign"),
               ]),
    _dashboard("root.platform.dashboards.pha-quality", "Качество и выпуск серии (GMP)",
               "Quarantine, batch release/reject with e-signature, deviation registration.",
               [
                   _report_widget("quarantine", "Quarantine & Batch Release", 0, 0, 54, 50,
                                  "root.platform.reports.pha-quarantine-lots"),
                   _form_widget("release", "Release Lot (QA, e-sign)", 54, 0, 30, 10, "pha_lot_release",
                                [{"name": "lotId", "label": "Lot ID", "type": "text"},
                                 {"name": "by", "label": "QA signer", "type": "text", "default": "EMP-H03"},
                                 {"name": "comment", "label": "Comment", "type": "text", "default": ""}],
                                "Release"),
                   _form_widget("reject", "Reject Lot", 54, 10, 30, 10, "pha_lot_reject",
                                [{"name": "lotId", "label": "Lot ID", "type": "text"},
                                 {"name": "by", "label": "Signer", "type": "text", "default": "EMP-H03"},
                                 {"name": "reason", "label": "Reason", "type": "text", "default": ""}],
                                "Reject"),
                   _form_widget("defect", "Register Deviation", 54, 20, 30, 15, "emc_qa_registerDefect",
                                [{"name": "defectNo", "label": "Deviation #", "type": "text"},
                                 {"name": "jobNo", "label": "Job #", "type": "text", "default": "JO-PH-001"},
                                 {"name": "defectTypeId", "label": "Defect type", "type": "text", "default": "DFT-WEIGHT-VAR"},
                                 {"name": "reasonCode", "label": "Reason code", "type": "text", "default": ""},
                                 {"name": "lotId", "label": "Lot ID", "type": "text", "default": ""},
                                 {"name": "qtyDeclared", "label": "Qty", "type": "number", "default": "1"},
                                 {"name": "severity", "label": "Severity", "type": "text", "default": "MINOR"},
                                 {"name": "createdBy", "label": "Created by", "type": "text", "default": "EMP-H03"}],
                                "Register", CORE_HUB),
               ]),
    _dashboard("root.platform.dashboards.pha-serialization", "Сериализация и аудит",
               "DataMatrix serial registry with aggregation and the e-signature audit trail.",
               [
                   _report_widget("serials", "Serial Code Registry", 0, 0, 54, 25,
                                  "root.platform.reports.pha-serial-report"),
                   _report_widget("audit", "E-Signature Audit", 0, 25, 54, 25,
                                  "root.platform.reports.pha-esign-audit"),
                   _form_widget("register", "Register Serial Code", 54, 0, 30, 18, "pha_serial_register",
                                [{"name": "serialCode", "label": "Serial code", "type": "text"},
                                 {"name": "gtin", "label": "GTIN", "type": "text", "default": "04607012340018"},
                                 {"name": "lotId", "label": "Lot ID", "type": "text", "default": "LOT-FG-PH-0001"},
                                 {"name": "jobNo", "label": "Job #", "type": "text", "default": "JO-PH-003"},
                                 {"name": "parentCode", "label": "Aggregate (box/pallet)", "type": "text", "default": ""}],
                                "Commission"),
                   _html_widget("note", "Агрегация", 54, 18, 30, 14,
                                "<h3>Aggregation</h3><p>Hierarchy: unit &rarr; box (<i>DM-BOX-*</i>) &rarr; pallet. "
                                "The <b>Aggregate</b> field links a unit code to the upper-level package. "
                                "Decommissioning is a status change (<i>DECOMMISSIONED</i>).</p>"),
               ]),
]

# ----------------------------------------------------------------------------
# Assembly
# ----------------------------------------------------------------------------

bundle = {
    "version": "1.0.0",
    "displayName": "ERP-MES Pharma (ISA-95/ISA-88)",
    "tablePrefix": "pha_",
    "schemaName": SCHEMA,
    "requires": [{"appId": "erp-mes-core", "minVersion": "1.0.0"}],
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
        "title": "MES Фарма (ISA-95/ISA-88)",
        "defaultDashboard": "root.platform.dashboards.pha-production",
        "dashboards": [
            {"path": "root.platform.dashboards.pha-production", "title": "Производство"},
            {"path": "root.platform.dashboards.pha-quality", "title": "Качество и выпуск серии"},
            {"path": "root.platform.dashboards.pha-serialization", "title": "Сериализация и аудит"},
        ],
        "eventJournalObjectPath": HUB,
        "reports": [
            {"path": "root.platform.reports.pha-job-board", "title": "Pharma Job Board"},
            {"path": "root.platform.reports.pha-quarantine-lots", "title": "Quarantine & Release"},
            {"path": "root.platform.reports.pha-serial-report", "title": "Serial Registry"},
            {"path": "root.platform.reports.pha-esign-audit", "title": "E-Signature Audit"},
        ],
        "defaultReport": "root.platform.reports.pha-job-board",
    },
    "metadata": {
        "product": "erp-mes-pharma",
        "publisher": "IoT Solutions",
        "delivery": "marketplace",
        "changelog": "1.0.0 pharma (solid dosage) overlay on erp-mes-core: GMP/eBR/e-signatures/serialization",
    },
}


def main():
    with io.open(BUNDLE_OUT, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(bundle, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("Wrote", BUNDLE_OUT, "migrations=", len(MIGRATIONS), "functions=", len(FUNCTIONS))


if __name__ == "__main__":
    main()
