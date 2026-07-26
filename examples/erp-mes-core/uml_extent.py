# -*- coding: utf-8 -*-
"""IEC 62264 Parts 2/4 UML attribute extent for erp-mes-core 2.0.

Imported by generate_bundle.py. Migrations are re-entrant (IF NOT EXISTS / WHERE NOT EXISTS).
"""


def _seed(table, columns, values, where):
    cols = ", ".join(columns)
    parts = []
    for v in values:
        if v is None:
            parts.append("NULL")
        elif isinstance(v, str) and v.startswith("!"):
            parts.append(v[1:])
        else:
            parts.append("'" + str(v).replace("'", "''") + "'")
    return (f"INSERT INTO {table} ({cols}) SELECT {', '.join(parts)} "
            f"WHERE NOT EXISTS (SELECT 1 FROM {table} WHERE {where})")


# ---------------------------------------------------------------------------
# M16 — Part 2 attribute completeness
# ---------------------------------------------------------------------------

_M16_BASE = [
    """CREATE TABLE IF NOT EXISTS emc_hierarchy_scope (
       scope_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       parent_scope_id VARCHAR(64),
       description VARCHAR(512))""",
    """CREATE TABLE IF NOT EXISTS emc_equipment_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_physical_asset_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_personnel_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_person_property (
       person_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (person_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_material_class_property (
       class_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (class_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_material_definition_property (
       definition_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(512),
       uom VARCHAR(32),
       PRIMARY KEY (definition_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_qualification_test_spec (
       spec_id VARCHAR(64) PRIMARY KEY,
       person_id VARCHAR(64),
       personnel_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_class_id VARCHAR(64),
       test_name VARCHAR(128) NOT NULL,
       criterion VARCHAR(256),
       qualification VARCHAR(128) NOT NULL DEFAULT 'OPERATE')""",
    """CREATE TABLE IF NOT EXISTS emc_qualification_test_result (
       result_id UUID PRIMARY KEY,
       spec_id VARCHAR(64) NOT NULL,
       measured_value VARCHAR(128),
       result VARCHAR(32) NOT NULL,
       tested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       tested_by VARCHAR(64))""",
    """CREATE TABLE IF NOT EXISTS emc_material_assembled_from (
       assembly_id VARCHAR(64) PRIMARY KEY,
       parent_definition_id VARCHAR(64) NOT NULL,
       child_definition_id VARCHAR(64),
       child_class_id VARCHAR(64),
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       uom VARCHAR(16),
       assembly_type VARCHAR(32) NOT NULL DEFAULT 'BOM',
       sequence_no INTEGER NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_segment_parameter_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       segment_id VARCHAR(64) NOT NULL,
       param_key VARCHAR(64) NOT NULL,
       param_name VARCHAR(128),
       default_value VARCHAR(128),
       uom VARCHAR(32),
       required_flag VARCHAR(8) NOT NULL DEFAULT 'false')""",
    """CREATE TABLE IF NOT EXISTS emc_product_segment_material_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       material_class_id VARCHAR(64),
       definition_id VARCHAR(64),
       material_use VARCHAR(32) NOT NULL,
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16))""",
    """CREATE TABLE IF NOT EXISTS emc_product_segment_equipment_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       equipment_class_id VARCHAR(64),
       equipment_id VARCHAR(64),
       equipment_use VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_product_segment_personnel_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       personnel_class_id VARCHAR(64),
       person_id VARCHAR(64),
       personnel_use VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1)""",
    """CREATE TABLE IF NOT EXISTS emc_product_segment_parameter_spec (
       spec_id VARCHAR(96) PRIMARY KEY,
       product_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       param_key VARCHAR(64) NOT NULL,
       param_name VARCHAR(128),
       default_value VARCHAR(128),
       uom VARCHAR(32),
       required_flag VARCHAR(8) NOT NULL DEFAULT 'false')""",
    """CREATE TABLE IF NOT EXISTS emc_ops_capability_equipment (
       capability_id VARCHAR(64) NOT NULL,
       equipment_id VARCHAR(64) NOT NULL DEFAULT '',
       equipment_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, equipment_id, equipment_class_id))""",
    """CREATE TABLE IF NOT EXISTS emc_ops_capability_material (
       capability_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64) NOT NULL DEFAULT '',
       material_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       PRIMARY KEY (capability_id, definition_id, material_class_id))""",
    """CREATE TABLE IF NOT EXISTS emc_ops_capability_personnel (
       capability_id VARCHAR(64) NOT NULL,
       person_id VARCHAR(64) NOT NULL DEFAULT '',
       personnel_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, person_id, personnel_class_id))""",
    """CREATE TABLE IF NOT EXISTS emc_ops_capability_segment (
       capability_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       PRIMARY KEY (capability_id, segment_id))""",
    """CREATE TABLE IF NOT EXISTS emc_mom_activity_bff (
       domain VARCHAR(32) NOT NULL,
       activity VARCHAR(32) NOT NULL,
       function_name VARCHAR(128) NOT NULL,
       PRIMARY KEY (domain, activity, function_name))""",
    """ALTER TABLE emc_equipment ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64)""",
    """ALTER TABLE emc_person ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64)""",
    """ALTER TABLE emc_material_definition ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64)""",
    """ALTER TABLE emc_operational_location ADD COLUMN IF NOT EXISTS hierarchy_scope_id VARCHAR(64)""",
    _seed("emc_hierarchy_scope", ["scope_id", "name", "parent_scope_id", "description"],
          ["SCOPE-DEMO", "Demo enterprise scope", None, "Default hierarchy scope for demostand"],
          "scope_id = 'SCOPE-DEMO'"),
    _seed("emc_hierarchy_scope", ["scope_id", "name", "parent_scope_id", "description"],
          ["SCOPE-SITE-01", "Site-01 scope", "SCOPE-DEMO", "Site level scope"],
          "scope_id = 'SCOPE-SITE-01'"),
    """UPDATE emc_equipment SET hierarchy_scope_id = 'SCOPE-DEMO' WHERE equipment_id = 'ENT-DEMO' AND hierarchy_scope_id IS NULL""",
    """UPDATE emc_equipment SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE equipment_id = 'SITE-01' AND hierarchy_scope_id IS NULL""",
    """UPDATE emc_equipment SET hierarchy_scope_id = 'SCOPE-SITE-01'
       WHERE hierarchy_scope_id IS NULL AND equipment_id IN ('AREA-PROD','LINE-A','WU-A01','WU-A02','WH-CENTRAL','WH-LINE-A01')""",
    """UPDATE emc_person SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE hierarchy_scope_id IS NULL""",
    """UPDATE emc_material_definition SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE hierarchy_scope_id IS NULL""",
    """UPDATE emc_operational_location SET hierarchy_scope_id = 'SCOPE-SITE-01' WHERE hierarchy_scope_id IS NULL""",
    _seed("emc_equipment_class_property", ["class_id", "prop_key", "prop_value", "uom"],
          ["EQC-ASSEMBLY-MACHINE", "rated_speed", "100", "pcs/h"],
          "class_id = 'EQC-ASSEMBLY-MACHINE' AND prop_key = 'rated_speed'"),
    _seed("emc_personnel_class_property", ["class_id", "prop_key", "prop_value", "uom"],
          ["PCL-OPERATOR", "shift_pattern", "2x8", None],
          "class_id = 'PCL-OPERATOR' AND prop_key = 'shift_pattern'"),
    _seed("emc_person_property", ["person_id", "prop_key", "prop_value", "uom"],
          ["EMP-001", "badge_id", "B-001", None],
          "person_id = 'EMP-001' AND prop_key = 'badge_id'"),
    _seed("emc_material_class_property", ["class_id", "prop_key", "prop_value", "uom"],
          ["MCL-RAW", "hazard_class", "NONE", None],
          "class_id = 'MCL-RAW' AND prop_key = 'hazard_class'"),
    _seed("emc_material_definition_property", ["definition_id", "prop_key", "prop_value", "uom"],
          ["RAW-PLASTIC-GRANULE", "density", "0.92", "g/cm3"],
          "definition_id = 'RAW-PLASTIC-GRANULE' AND prop_key = 'density'"),
    _seed("emc_physical_asset_class_property", ["class_id", "prop_key", "prop_value", "uom"],
          ["PAC-MACHINE", "maintenance_interval_days", "30", "d"],
          "class_id = 'PAC-MACHINE' AND prop_key = 'maintenance_interval_days'"),
    _seed("emc_qualification_test_spec",
          ["spec_id", "person_id", "personnel_class_id", "equipment_id", "equipment_class_id",
           "test_name", "criterion", "qualification"],
          ["QTS-EMP001-A01", "EMP-001", None, "WU-A01", None, "Operate assembly cell", "PASS checklist", "OPERATE"],
          "spec_id = 'QTS-EMP001-A01'"),
    """INSERT INTO emc_qualification_test_result (result_id, spec_id, measured_value, result, tested_by)
       SELECT gen_random_uuid(), 'QTS-EMP001-A01', 'OK', 'PASS', 'EMP-002'
       WHERE NOT EXISTS (SELECT 1 FROM emc_qualification_test_result WHERE spec_id = 'QTS-EMP001-A01')""",
    _seed("emc_material_assembled_from",
          ["assembly_id", "parent_definition_id", "child_definition_id", "child_class_id",
           "quantity", "uom", "assembly_type", "sequence_no"],
          ["AF-FG-GRANULE", "FG-UNIT-PACKED", "RAW-PLASTIC-GRANULE", None, "2.5", "kg", "BOM", "1"],
          "assembly_id = 'AF-FG-GRANULE'"),
    _seed("emc_material_assembled_from",
          ["assembly_id", "parent_definition_id", "child_definition_id", "child_class_id",
           "quantity", "uom", "assembly_type", "sequence_no"],
          ["AF-FG-BOX", "FG-UNIT-PACKED", "RAW-PACKAGING-BOX", None, "1", "pcs", "BOM", "2"],
          "assembly_id = 'AF-FG-BOX'"),
    _seed("emc_segment_parameter_spec",
          ["spec_id", "segment_id", "param_key", "param_name", "default_value", "uom", "required_flag"],
          ["SEG-ASSEMBLE:TEMP", "SEG-ASSEMBLE", "TEMPERATURE", "Process temperature", "210", "C", "true"],
          "spec_id = 'SEG-ASSEMBLE:TEMP'"),
    _seed("emc_product_segment_material_spec",
          ["spec_id", "product_id", "segment_id", "material_class_id", "definition_id", "material_use", "quantity", "uom"],
          ["PD-UNIT:SEG-PACK:OUT", "PD-UNIT-PACKED", "SEG-PACK", None, "FG-UNIT-PACKED", "PRODUCED", "1", "pcs"],
          "spec_id = 'PD-UNIT:SEG-PACK:OUT'"),
    _seed("emc_product_segment_equipment_spec",
          ["spec_id", "product_id", "segment_id", "equipment_class_id", "equipment_id", "equipment_use", "quantity"],
          ["PD-UNIT:SEG-PACK:EQ", "PD-UNIT-PACKED", "SEG-PACK", "EQC-PACK-MACHINE", "", "PRIMARY", "1"],
          "spec_id = 'PD-UNIT:SEG-PACK:EQ'"),
    _seed("emc_product_segment_personnel_spec",
          ["spec_id", "product_id", "segment_id", "personnel_class_id", "person_id", "personnel_use", "quantity"],
          ["PD-UNIT:SEG-PACK:PERS", "PD-UNIT-PACKED", "SEG-PACK", "PCL-OPERATOR", "", "OPERATOR", "1"],
          "spec_id = 'PD-UNIT:SEG-PACK:PERS'"),
    _seed("emc_product_segment_parameter_spec",
          ["spec_id", "product_id", "segment_id", "param_key", "param_name", "default_value", "uom", "required_flag"],
          ["PD-UNIT:SEG-PACK:RATE", "PD-UNIT-PACKED", "SEG-PACK", "PACK_RATE", "Pack rate", "60", "pcs/h", "false"],
          "spec_id = 'PD-UNIT:SEG-PACK:RATE'"),
    _seed("emc_ops_capability_equipment",
          ["capability_id", "equipment_id", "equipment_class_id", "quantity"],
          ["CAP-WU-A01-ASSEMBLE", "WU-A01", "EQC-ASSEMBLY-MACHINE", "1"],
          "capability_id = 'CAP-WU-A01-ASSEMBLE' AND equipment_id = 'WU-A01'"),
    _seed("emc_ops_capability_material",
          ["capability_id", "definition_id", "material_class_id", "quantity", "uom"],
          ["CAP-WU-A01-ASSEMBLE", "RAW-PLASTIC-GRANULE", "MCL-RAW", "100", "kg"],
          "capability_id = 'CAP-WU-A01-ASSEMBLE' AND definition_id = 'RAW-PLASTIC-GRANULE'"),
    _seed("emc_ops_capability_personnel",
          ["capability_id", "person_id", "personnel_class_id", "quantity"],
          ["CAP-WU-A01-ASSEMBLE", "", "PCL-OPERATOR", "1"],
          "capability_id = 'CAP-WU-A01-ASSEMBLE' AND personnel_class_id = 'PCL-OPERATOR'"),
    _seed("emc_ops_capability_segment", ["capability_id", "segment_id"],
          ["CAP-WU-A01-ASSEMBLE", "SEG-ASSEMBLE"],
          "capability_id = 'CAP-WU-A01-ASSEMBLE' AND segment_id = 'SEG-ASSEMBLE'"),
]

_MOM_BFF = [
    ("PRODUCTION", "DEFINITION", "emc_segment_list"),
    ("PRODUCTION", "RESOURCE", "emc_equipment_list"),
    ("PRODUCTION", "DETAILED_SCHEDULING", "emc_schedule_receive"),
    ("PRODUCTION", "DISPATCHING", "emc_joborder_release"),
    ("PRODUCTION", "EXECUTION", "emc_joborder_start"),
    ("PRODUCTION", "DATA_COLLECTION", "emc_dc_recordQuantity"),
    ("PRODUCTION", "TRACKING", "emc_track_genealogyTreeByLot"),
    ("PRODUCTION", "PERFORMANCE_ANALYSIS", "emc_oee_calcShift"),
    ("QUALITY", "DEFINITION", "emc_qa_listDefects"),
    ("QUALITY", "RESOURCE", "emc_person_list"),
    ("QUALITY", "DETAILED_SCHEDULING", "emc_domainschedule_list"),
    ("QUALITY", "DISPATCHING", "emc_qa_registerDefect"),
    ("QUALITY", "EXECUTION", "emc_qa_confirmDefect"),
    ("QUALITY", "DATA_COLLECTION", "emc_qa_recordTestResult"),
    ("QUALITY", "TRACKING", "emc_qa_listDefects"),
    ("QUALITY", "PERFORMANCE_ANALYSIS", "emc_qa_defectRateKpi"),
    ("INVENTORY", "DEFINITION", "emc_stock_list"),
    ("INVENTORY", "RESOURCE", "emc_location_list"),
    ("INVENTORY", "DETAILED_SCHEDULING", "emc_domainschedule_list"),
    ("INVENTORY", "DISPATCHING", "emc_invdoc_create"),
    ("INVENTORY", "EXECUTION", "emc_invdoc_apply"),
    ("INVENTORY", "DATA_COLLECTION", "emc_stock_list"),
    ("INVENTORY", "TRACKING", "emc_stock_list"),
    ("INVENTORY", "PERFORMANCE_ANALYSIS", "emc_inv_turnsKpi"),
    ("MAINTENANCE", "DEFINITION", "emc_maint_list"),
    ("MAINTENANCE", "RESOURCE", "emc_equipment_list"),
    ("MAINTENANCE", "DETAILED_SCHEDULING", "emc_domainschedule_list"),
    ("MAINTENANCE", "DISPATCHING", "emc_maint_acceptRequest"),
    ("MAINTENANCE", "EXECUTION", "emc_maint_completeWorkOrder"),
    ("MAINTENANCE", "DATA_COLLECTION", "emc_event_register"),
    ("MAINTENANCE", "TRACKING", "emc_maint_list"),
    ("MAINTENANCE", "PERFORMANCE_ANALYSIS", "emc_maint_mttrMtbf"),
]

M16_UML_PART2 = ";\n".join(_M16_BASE + [
    f"INSERT INTO emc_mom_activity_bff (domain, activity, function_name) "
    f"SELECT '{d}', '{a}', '{fn}' WHERE NOT EXISTS "
    f"(SELECT 1 FROM emc_mom_activity_bff WHERE domain = '{d}' AND activity = '{a}' AND function_name = '{fn}')"
    for d, a, fn in _MOM_BFF
])

M17_UML_PART4 = ";\n".join([
    """CREATE TABLE IF NOT EXISTS emc_work_master_node (
       node_id VARCHAR(64) PRIMARY KEY,
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       segment_id VARCHAR(64) NOT NULL,
       sequence_no INTEGER NOT NULL DEFAULT 1,
       node_kind VARCHAR(32) NOT NULL DEFAULT 'SEGMENT')""",
    """CREATE TABLE IF NOT EXISTS emc_work_master_edge (
       edge_id VARCHAR(64) PRIMARY KEY,
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       from_node_id VARCHAR(64) NOT NULL,
       to_node_id VARCHAR(64) NOT NULL,
       edge_kind VARCHAR(32) NOT NULL DEFAULT 'SEQUENCE')""",
    """CREATE TABLE IF NOT EXISTS emc_job_order_parameter_req (
       job_no VARCHAR(64) NOT NULL,
       param_key VARCHAR(64) NOT NULL,
       param_value VARCHAR(128),
       uom VARCHAR(32),
       PRIMARY KEY (job_no, param_key))""",
    """CREATE TABLE IF NOT EXISTS emc_work_directive (
       directive_id VARCHAR(64) PRIMARY KEY,
       work_master_id VARCHAR(64),
       version VARCHAR(16),
       job_no VARCHAR(64),
       title VARCHAR(256) NOT NULL,
       body_text VARCHAR(2048),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_work_performance (
       performance_id VARCHAR(64) PRIMARY KEY,
       job_no VARCHAR(64),
       work_master_id VARCHAR(64),
       start_time TIMESTAMP,
       end_time TIMESTAMP,
       good_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       reject_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
       status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       note VARCHAR(256),
       calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    """CREATE TABLE IF NOT EXISTS emc_genealogy_node (
       node_id VARCHAR(64) PRIMARY KEY,
       node_kind VARCHAR(32) NOT NULL DEFAULT 'LOT',
       lot_id VARCHAR(64),
       definition_id VARCHAR(64),
       label VARCHAR(256))""",
    """ALTER TABLE emc_lot_genealogy ADD COLUMN IF NOT EXISTS relation_kind VARCHAR(32)""",
    """ALTER TABLE emc_lot_genealogy ADD COLUMN IF NOT EXISTS assembly_type VARCHAR(32)""",
    """UPDATE emc_lot_genealogy SET relation_kind = 'CONSUME_PRODUCE' WHERE relation_kind IS NULL""",
    """UPDATE emc_lot_genealogy SET assembly_type = 'PROCESS' WHERE assembly_type IS NULL""",
    _seed("emc_work_master",
          ["work_master_id", "version", "segment_id", "duration_min", "description"],
          ["WM-ROUTE-PACK", "1", "SEG-ASSEMBLE", "90", "Assemble then pack (multi-segment)"],
          "work_master_id = 'WM-ROUTE-PACK' AND version = '1'"),
    _seed("emc_work_master_node",
          ["node_id", "work_master_id", "version", "segment_id", "sequence_no", "node_kind"],
          ["WMN-ASSEMBLE-1", "WM-ASSEMBLE", "1", "SEG-ASSEMBLE", "1", "SEGMENT"],
          "node_id = 'WMN-ASSEMBLE-1'"),
    _seed("emc_work_master_node",
          ["node_id", "work_master_id", "version", "segment_id", "sequence_no", "node_kind"],
          ["WMN-ROUTE-A", "WM-ROUTE-PACK", "1", "SEG-ASSEMBLE", "1", "SEGMENT"],
          "node_id = 'WMN-ROUTE-A'"),
    _seed("emc_work_master_node",
          ["node_id", "work_master_id", "version", "segment_id", "sequence_no", "node_kind"],
          ["WMN-ROUTE-P", "WM-ROUTE-PACK", "1", "SEG-PACK", "2", "SEGMENT"],
          "node_id = 'WMN-ROUTE-P'"),
    _seed("emc_work_master_edge",
          ["edge_id", "work_master_id", "version", "from_node_id", "to_node_id", "edge_kind"],
          ["WME-ROUTE-AP", "WM-ROUTE-PACK", "1", "WMN-ROUTE-A", "WMN-ROUTE-P", "SEQUENCE"],
          "edge_id = 'WME-ROUTE-AP'"),
    _seed("emc_job_order_parameter_req",
          ["job_no", "param_key", "param_value", "uom"],
          ["JO-DEMO-002", "TEMPERATURE", "210", "C"],
          "job_no = 'JO-DEMO-002' AND param_key = 'TEMPERATURE'"),
    _seed("emc_work_directive",
          ["directive_id", "work_master_id", "version", "job_no", "title", "body_text", "status"],
          ["WD-ASSEMBLE-1", "WM-ASSEMBLE", "1", "JO-DEMO-002",
           "Assembly line clearance", "Verify guards closed; materials staged; start checklist OK.", "ACTIVE"],
          "directive_id = 'WD-ASSEMBLE-1'"),
    _seed("emc_work_performance",
          ["performance_id", "job_no", "work_master_id", "good_qty", "reject_qty", "status", "note"],
          ["WP-JO-DEMO-002", "JO-DEMO-002", "WM-ASSEMBLE", "0", "0", "OPEN", "Seed performance header"],
          "performance_id = 'WP-JO-DEMO-002'"),
    _seed("emc_genealogy_node",
          ["node_id", "node_kind", "lot_id", "definition_id", "label"],
          ["GN-LOT-FG-0001", "LOT", "LOT-FG-0001", "FG-UNIT-PACKED", "Finished unit lot"],
          "node_id = 'GN-LOT-FG-0001'"),
    _seed("emc_material_sublot",
          ["sublot_id", "lot_id", "barcode", "status", "storage_location", "quantity"],
          ["SL-RAW-0001-A", "LOT-RAW-0001", "BC-RAW-0001-A", "STOCK", "WH-LINE-A01", "100"],
          "sublot_id = 'SL-RAW-0001-A'"),
])


def build_uml_functions(fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null):
    """BFF for UML extent objects (additive; never shadow existing emc_*)."""
    out = []

    # --- hierarchy scope ---
    out.append(fn(
        "emc_hierarchy_scope_list", [],
        OUT(RL("rows", [F("scopeId"), F("name"), F("parentScopeId"), F("description")])),
        [
            selN("rows_raw",
                 "SELECT scope_id, name, COALESCE(parent_scope_id, '') AS parent_scope_id, "
                 "COALESCE(description, '') AS description FROM emc_hierarchy_scope ORDER BY scope_id"),
            map_rows("rows", "${rows_raw}", {
                "scopeId": "${item.scope_id}", "name": "${item.name}",
                "parentScopeId": "${item.parent_scope_id}", "description": "${item.description}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_qualification_listSpecs", [],
        OUT(RL("rows", [F("specId"), F("personId"), F("equipmentId"), F("testName"),
                        F("qualification"), F("criterion")])),
        [
            selN("rows_raw",
                 "SELECT spec_id, COALESCE(person_id, '') AS person_id, "
                 "COALESCE(equipment_id, '') AS equipment_id, test_name, qualification, "
                 "COALESCE(criterion, '') AS criterion FROM emc_qualification_test_spec ORDER BY spec_id"),
            map_rows("rows", "${rows_raw}", {
                "specId": "${item.spec_id}", "personId": "${item.person_id}",
                "equipmentId": "${item.equipment_id}", "testName": "${item.test_name}",
                "qualification": "${item.qualification}", "criterion": "${item.criterion}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_qualification_recordResult",
        [F("specId"), F("measuredValue"), F("result"), F("testedBy")],
        OUT(F("specId"), F("result")),
        [
            sel1("spec", "SELECT spec_id FROM emc_qualification_test_spec WHERE spec_id = ?",
                 ["${input.specId}"]),
            fail_null("spec", "SPEC_NOT_FOUND", "Qualification test spec not found"),
            ex("INSERT INTO emc_qualification_test_result "
               "(result_id, spec_id, measured_value, result, tested_by) "
               "VALUES (gen_random_uuid(), ?, ?, ?, ?)",
               ["${input.specId}", "${input.measuredValue}", "${input.result}", "${input.testedBy}"]),
            ret({"error_code": "OK", "error_message": "", "specId": "${input.specId}",
                 "result": "${input.result}"}),
        ],
    ))

    out.append(fn(
        "emc_assembledfrom_list",
        [F("parentDefinitionId")],
        OUT(RL("rows", [F("assemblyId"), F("parentDefinitionId"), F("childDefinitionId"),
                        F("quantity"), F("uom"), F("assemblyType"), F("sequenceNo")])),
        [
            selN("rows_raw",
                 "SELECT assembly_id, parent_definition_id, COALESCE(child_definition_id, '') AS child_definition_id, "
                 "quantity, COALESCE(uom, '') AS uom, assembly_type, sequence_no "
                 "FROM emc_material_assembled_from "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), parent_definition_id) = parent_definition_id "
                 "ORDER BY parent_definition_id, sequence_no",
                 ["${input.parentDefinitionId}"]),
            map_rows("rows", "${rows_raw}", {
                "assemblyId": "${item.assembly_id}", "parentDefinitionId": "${item.parent_definition_id}",
                "childDefinitionId": "${item.child_definition_id}", "quantity": "${item.quantity}",
                "uom": "${item.uom}", "assemblyType": "${item.assembly_type}",
                "sequenceNo": "${item.sequence_no}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_assembledfrom_upsert",
        [F("assemblyId"), F("parentDefinitionId"), F("childDefinitionId"), F("quantity"),
         F("uom"), F("assemblyType"), F("sequenceNo")],
        OUT(F("assemblyId")),
        [
            ex("UPDATE emc_material_assembled_from SET parent_definition_id = ?, child_definition_id = ?, "
               "quantity = CAST(? AS NUMERIC), uom = ?, assembly_type = ?, sequence_no = CAST(? AS INTEGER) "
               "WHERE assembly_id = ?",
               ["${input.parentDefinitionId}", "${input.childDefinitionId}", "${input.quantity}",
                "${input.uom}", "${input.assemblyType}", "${input.sequenceNo}", "${input.assemblyId}"]),
            ex("INSERT INTO emc_material_assembled_from "
               "(assembly_id, parent_definition_id, child_definition_id, quantity, uom, assembly_type, sequence_no) "
               "SELECT ?, ?, ?, CAST(? AS NUMERIC), ?, ?, CAST(? AS INTEGER) "
               "WHERE NOT EXISTS (SELECT 1 FROM emc_material_assembled_from WHERE assembly_id = ?)",
               ["${input.assemblyId}", "${input.parentDefinitionId}", "${input.childDefinitionId}",
                "${input.quantity}", "${input.uom}", "${input.assemblyType}", "${input.sequenceNo}",
                "${input.assemblyId}"]),
            ret({"error_code": "OK", "error_message": "", "assemblyId": "${input.assemblyId}"}),
        ],
    ))

    out.append(fn(
        "emc_segment_param_list",
        [F("segmentId")],
        OUT(RL("rows", [F("specId"), F("segmentId"), F("paramKey"), F("paramName"),
                        F("defaultValue"), F("uom"), F("requiredFlag")])),
        [
            selN("rows_raw",
                 "SELECT spec_id, segment_id, param_key, COALESCE(param_name, '') AS param_name, "
                 "COALESCE(default_value, '') AS default_value, COALESCE(uom, '') AS uom, required_flag "
                 "FROM emc_segment_parameter_spec "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), segment_id) = segment_id ORDER BY segment_id, param_key",
                 ["${input.segmentId}"]),
            map_rows("rows", "${rows_raw}", {
                "specId": "${item.spec_id}", "segmentId": "${item.segment_id}",
                "paramKey": "${item.param_key}", "paramName": "${item.param_name}",
                "defaultValue": "${item.default_value}", "uom": "${item.uom}",
                "requiredFlag": "${item.required_flag}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_product_segment_specs_list",
        [F("productId")],
        OUT(RL("rows", [F("kind"), F("specId"), F("productId"), F("segmentId"), F("refId"),
                        F("useOrKey"), F("quantity"), F("uom")])),
        [
            selN("rows_raw",
                 "SELECT 'MATERIAL' AS kind, spec_id, product_id, segment_id, "
                 "COALESCE(definition_id, material_class_id, '') AS ref_id, material_use AS use_or_key, "
                 "quantity, COALESCE(uom, '') AS uom FROM emc_product_segment_material_spec "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), product_id) = product_id "
                 "UNION ALL "
                 "SELECT 'EQUIPMENT', spec_id, product_id, segment_id, "
                 "COALESCE(NULLIF(equipment_id, ''), equipment_class_id, ''), equipment_use, quantity, '' "
                 "FROM emc_product_segment_equipment_spec "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), product_id) = product_id "
                 "UNION ALL "
                 "SELECT 'PERSONNEL', spec_id, product_id, segment_id, "
                 "COALESCE(NULLIF(person_id, ''), personnel_class_id, ''), personnel_use, quantity, '' "
                 "FROM emc_product_segment_personnel_spec "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), product_id) = product_id "
                 "UNION ALL "
                 "SELECT 'PARAMETER', spec_id, product_id, segment_id, param_key, "
                 "COALESCE(param_name, ''), CAST(0 AS NUMERIC), COALESCE(uom, '') "
                 "FROM emc_product_segment_parameter_spec "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), product_id) = product_id",
                 ["${input.productId}", "${input.productId}", "${input.productId}", "${input.productId}"]),
            map_rows("rows", "${rows_raw}", {
                "kind": "${item.kind}", "specId": "${item.spec_id}", "productId": "${item.product_id}",
                "segmentId": "${item.segment_id}", "refId": "${item.ref_id}",
                "useOrKey": "${item.use_or_key}", "quantity": "${item.quantity}", "uom": "${item.uom}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_opscap_children_list",
        [F("capabilityId")],
        OUT(RL("rows", [F("kind"), F("capabilityId"), F("refId"), F("quantity"), F("uom")])),
        [
            selN("rows_raw",
                 "SELECT 'EQUIPMENT' AS kind, capability_id, "
                 "COALESCE(NULLIF(equipment_id, ''), equipment_class_id, '') AS ref_id, "
                 "quantity, '' AS uom FROM emc_ops_capability_equipment "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id "
                 "UNION ALL "
                 "SELECT 'MATERIAL', capability_id, "
                 "COALESCE(NULLIF(definition_id, ''), material_class_id, ''), quantity, COALESCE(uom, '') "
                 "FROM emc_ops_capability_material "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id "
                 "UNION ALL "
                 "SELECT 'PERSONNEL', capability_id, "
                 "COALESCE(NULLIF(person_id, ''), personnel_class_id, ''), quantity, '' "
                 "FROM emc_ops_capability_personnel "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id "
                 "UNION ALL "
                 "SELECT 'SEGMENT', capability_id, segment_id, CAST(1 AS NUMERIC), '' "
                 "FROM emc_ops_capability_segment "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id",
                 ["${input.capabilityId}", "${input.capabilityId}",
                  "${input.capabilityId}", "${input.capabilityId}"]),
            map_rows("rows", "${rows_raw}", {
                "kind": "${item.kind}", "capabilityId": "${item.capability_id}",
                "refId": "${item.ref_id}", "quantity": "${item.quantity}", "uom": "${item.uom}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_workmaster_nodes_list",
        [F("workMasterId")],
        OUT(RL("rows", [F("nodeId"), F("workMasterId"), F("version"), F("segmentId"),
                        F("sequenceNo"), F("nodeKind")])),
        [
            selN("rows_raw",
                 "SELECT node_id, work_master_id, version, segment_id, sequence_no, node_kind "
                 "FROM emc_work_master_node "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), work_master_id) = work_master_id "
                 "ORDER BY work_master_id, sequence_no",
                 ["${input.workMasterId}"]),
            map_rows("rows", "${rows_raw}", {
                "nodeId": "${item.node_id}", "workMasterId": "${item.work_master_id}",
                "version": "${item.version}", "segmentId": "${item.segment_id}",
                "sequenceNo": "${item.sequence_no}", "nodeKind": "${item.node_kind}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_workdirective_list", [],
        OUT(RL("rows", [F("directiveId"), F("workMasterId"), F("jobNo"), F("title"), F("status")])),
        [
            selN("rows_raw",
                 "SELECT directive_id, COALESCE(work_master_id, '') AS work_master_id, "
                 "COALESCE(job_no, '') AS job_no, title, status FROM emc_work_directive ORDER BY directive_id"),
            map_rows("rows", "${rows_raw}", {
                "directiveId": "${item.directive_id}", "workMasterId": "${item.work_master_id}",
                "jobNo": "${item.job_no}", "title": "${item.title}", "status": "${item.status}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_workdirective_upsert",
        [F("directiveId"), F("workMasterId"), F("version"), F("jobNo"), F("title"), F("bodyText")],
        OUT(F("directiveId")),
        [
            ex("UPDATE emc_work_directive SET work_master_id = ?, version = ?, job_no = ?, title = ?, "
               "body_text = ?, status = 'ACTIVE' WHERE directive_id = ?",
               ["${input.workMasterId}", "${input.version}", "${input.jobNo}",
                "${input.title}", "${input.bodyText}", "${input.directiveId}"]),
            ex("INSERT INTO emc_work_directive "
               "(directive_id, work_master_id, version, job_no, title, body_text, status) "
               "SELECT ?, ?, ?, ?, ?, ?, 'ACTIVE' "
               "WHERE NOT EXISTS (SELECT 1 FROM emc_work_directive WHERE directive_id = ?)",
               ["${input.directiveId}", "${input.workMasterId}", "${input.version}",
                "${input.jobNo}", "${input.title}", "${input.bodyText}", "${input.directiveId}"]),
            ret({"error_code": "OK", "error_message": "", "directiveId": "${input.directiveId}"}),
        ],
    ))

    out.append(fn(
        "emc_workperf_list", [],
        OUT(RL("rows", [F("performanceId"), F("jobNo"), F("workMasterId"), F("goodQty"),
                        F("rejectQty"), F("status")])),
        [
            selN("rows_raw",
                 "SELECT performance_id, COALESCE(job_no, '') AS job_no, "
                 "COALESCE(work_master_id, '') AS work_master_id, good_qty, reject_qty, status "
                 "FROM emc_work_performance ORDER BY calculated_at DESC"),
            map_rows("rows", "${rows_raw}", {
                "performanceId": "${item.performance_id}", "jobNo": "${item.job_no}",
                "workMasterId": "${item.work_master_id}", "goodQty": "${item.good_qty}",
                "rejectQty": "${item.reject_qty}", "status": "${item.status}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_workperf_rollup",
        [F("jobNo")],
        OUT(F("performanceId"), F("goodQty"), F("rejectQty")),
        [
            sel1("agg",
                 "SELECT COALESCE((SELECT SUM(a.quantity) FROM emc_material_actual a "
                 "JOIN emc_job_response r ON r.response_id = a.response_id "
                 "WHERE r.job_no = ? AND a.material_use = 'PRODUCED'), 0) AS good_qty, "
                 "COALESCE((SELECT SUM(d.qty_declared) FROM emc_defect_record d WHERE d.job_no = ?), 0) "
                 "AS reject_qty FROM (SELECT 1) x",
                 ["${input.jobNo}", "${input.jobNo}"]),
            ex("UPDATE emc_work_performance SET good_qty = ?, reject_qty = ?, "
               "calculated_at = CURRENT_TIMESTAMP, status = 'OPEN' WHERE job_no = ?",
               ["${agg.good_qty}", "${agg.reject_qty}", "${input.jobNo}"]),
            ex("INSERT INTO emc_work_performance "
               "(performance_id, job_no, good_qty, reject_qty, status, note) "
               "SELECT CONCAT('WP-', ?), ?, ?, ?, 'OPEN', 'Rollup' "
               "WHERE NOT EXISTS (SELECT 1 FROM emc_work_performance WHERE job_no = ?)",
               ["${input.jobNo}", "${input.jobNo}", "${agg.good_qty}", "${agg.reject_qty}",
                "${input.jobNo}"]),
            ret({"error_code": "OK", "error_message": "", "performanceId": "${input.jobNo}",
                 "goodQty": "${agg.good_qty}", "rejectQty": "${agg.reject_qty}"}),
        ],
    ))

    out.append(fn(
        "emc_sublot_list",
        [F("lotId")],
        OUT(RL("rows", [F("sublotId"), F("lotId"), F("barcode"), F("status"),
                        F("storageLocation"), F("quantity")])),
        [
            selN("rows_raw",
                 "SELECT sublot_id, lot_id, barcode, status, "
                 "COALESCE(storage_location, '') AS storage_location, quantity "
                 "FROM emc_material_sublot "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), lot_id) = lot_id ORDER BY sublot_id",
                 ["${input.lotId}"]),
            map_rows("rows", "${rows_raw}", {
                "sublotId": "${item.sublot_id}", "lotId": "${item.lot_id}",
                "barcode": "${item.barcode}", "status": "${item.status}",
                "storageLocation": "${item.storage_location}", "quantity": "${item.quantity}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_sublot_split",
        [F("lotId"), F("sublotId"), F("barcode"), F("quantity"), F("storageLocation")],
        OUT(F("sublotId"), F("lotId")),
        [
            sel1("lot", "SELECT lot_id FROM emc_material_lot WHERE lot_id = ?", ["${input.lotId}"]),
            fail_null("lot", "LOT_NOT_FOUND", "Parent lot not found"),
            ex("INSERT INTO emc_material_sublot "
               "(sublot_id, lot_id, barcode, status, storage_location, quantity) "
               "VALUES (?, ?, ?, 'STOCK', ?, CAST(? AS NUMERIC))",
               ["${input.sublotId}", "${input.lotId}", "${input.barcode}",
                "${input.storageLocation}", "${input.quantity}"]),
            ret({"error_code": "OK", "error_message": "", "sublotId": "${input.sublotId}",
                 "lotId": "${input.lotId}"}),
        ],
    ))

    out.append(fn(
        "emc_joborder_param_list",
        [F("jobNo")],
        OUT(RL("rows", [F("jobNo"), F("paramKey"), F("paramValue"), F("uom")])),
        [
            selN("rows_raw",
                 "SELECT job_no, param_key, COALESCE(param_value, '') AS param_value, "
                 "COALESCE(uom, '') AS uom FROM emc_job_order_parameter_req "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), job_no) = job_no ORDER BY job_no, param_key",
                 ["${input.jobNo}"]),
            map_rows("rows", "${rows_raw}", {
                "jobNo": "${item.job_no}", "paramKey": "${item.param_key}",
                "paramValue": "${item.param_value}", "uom": "${item.uom}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_mom_listActivityBff", [],
        OUT(RL("rows", [F("domain"), F("activity"), F("functionName")])),
        [
            selN("rows_raw",
                 "SELECT domain, activity, function_name FROM emc_mom_activity_bff "
                 "ORDER BY domain, activity, function_name"),
            map_rows("rows", "${rows_raw}", {
                "domain": "${item.domain}", "activity": "${item.activity}",
                "functionName": "${item.function_name}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_classprop_list",
        [F("ownerKind"), F("ownerId")],
        OUT(RL("rows", [F("ownerKind"), F("ownerId"), F("propKey"), F("propValue"), F("uom")])),
        [
            selN("rows_raw",
                 "SELECT 'EQUIPMENT_CLASS' AS owner_kind, class_id AS owner_id, prop_key, "
                 "COALESCE(prop_value, '') AS prop_value, COALESCE(uom, '') AS uom "
                 "FROM emc_equipment_class_property "
                 "WHERE (UPPER(TRIM(?)) = '' OR UPPER(TRIM(?)) = 'EQUIPMENT_CLASS') "
                 "AND COALESCE(NULLIF(TRIM(?), ''), class_id) = class_id "
                 "UNION ALL "
                 "SELECT 'PERSONNEL_CLASS', class_id, prop_key, COALESCE(prop_value, ''), COALESCE(uom, '') "
                 "FROM emc_personnel_class_property "
                 "WHERE (UPPER(TRIM(?)) = '' OR UPPER(TRIM(?)) = 'PERSONNEL_CLASS') "
                 "AND COALESCE(NULLIF(TRIM(?), ''), class_id) = class_id "
                 "UNION ALL "
                 "SELECT 'MATERIAL_CLASS', class_id, prop_key, COALESCE(prop_value, ''), COALESCE(uom, '') "
                 "FROM emc_material_class_property "
                 "WHERE (UPPER(TRIM(?)) = '' OR UPPER(TRIM(?)) = 'MATERIAL_CLASS') "
                 "AND COALESCE(NULLIF(TRIM(?), ''), class_id) = class_id "
                 "UNION ALL "
                 "SELECT 'MATERIAL_DEFINITION', definition_id, prop_key, COALESCE(prop_value, ''), COALESCE(uom, '') "
                 "FROM emc_material_definition_property "
                 "WHERE (UPPER(TRIM(?)) = '' OR UPPER(TRIM(?)) = 'MATERIAL_DEFINITION') "
                 "AND COALESCE(NULLIF(TRIM(?), ''), definition_id) = definition_id "
                 "UNION ALL "
                 "SELECT 'PERSON', person_id, prop_key, COALESCE(prop_value, ''), COALESCE(uom, '') "
                 "FROM emc_person_property "
                 "WHERE (UPPER(TRIM(?)) = '' OR UPPER(TRIM(?)) = 'PERSON') "
                 "AND COALESCE(NULLIF(TRIM(?), ''), person_id) = person_id",
                 ["${input.ownerKind}", "${input.ownerKind}", "${input.ownerId}",
                  "${input.ownerKind}", "${input.ownerKind}", "${input.ownerId}",
                  "${input.ownerKind}", "${input.ownerKind}", "${input.ownerId}",
                  "${input.ownerKind}", "${input.ownerKind}", "${input.ownerId}",
                  "${input.ownerKind}", "${input.ownerKind}", "${input.ownerId}"]),
            map_rows("rows", "${rows_raw}", {
                "ownerKind": "${item.owner_kind}", "ownerId": "${item.owner_id}",
                "propKey": "${item.prop_key}", "propValue": "${item.prop_value}",
                "uom": "${item.uom}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    return out


UML_TABLES = [
    "emc_hierarchy_scope",
    "emc_equipment_class_property",
    "emc_physical_asset_class_property",
    "emc_personnel_class_property",
    "emc_person_property",
    "emc_material_class_property",
    "emc_material_definition_property",
    "emc_qualification_test_spec",
    "emc_qualification_test_result",
    "emc_material_assembled_from",
    "emc_segment_parameter_spec",
    "emc_product_segment_material_spec",
    "emc_product_segment_equipment_spec",
    "emc_product_segment_personnel_spec",
    "emc_product_segment_parameter_spec",
    "emc_ops_capability_equipment",
    "emc_ops_capability_material",
    "emc_ops_capability_personnel",
    "emc_ops_capability_segment",
    "emc_mom_activity_bff",
    "emc_work_master_node",
    "emc_work_master_edge",
    "emc_job_order_parameter_req",
    "emc_work_directive",
    "emc_work_performance",
    "emc_genealogy_node",
]

UML_FUNCTION_NAMES = [
    "emc_hierarchy_scope_list",
    "emc_qualification_listSpecs",
    "emc_qualification_recordResult",
    "emc_assembledfrom_list",
    "emc_assembledfrom_upsert",
    "emc_segment_param_list",
    "emc_product_segment_specs_list",
    "emc_opscap_children_list",
    "emc_workmaster_nodes_list",
    "emc_workdirective_list",
    "emc_workdirective_upsert",
    "emc_workperf_list",
    "emc_workperf_rollup",
    "emc_sublot_list",
    "emc_sublot_split",
    "emc_joborder_param_list",
    "emc_mom_listActivityBff",
    "emc_classprop_list",
]
