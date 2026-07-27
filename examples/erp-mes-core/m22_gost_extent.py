# -*- coding: utf-8 -*-
"""ERP-MES Core 2.2 / M5: GOST object gaps — RRN, Container/Tool/Software,
Operations Definition/Schedule, Work Capability / Work Master Capability,
Work Alert; Maint/QA/Inv demo seeds."""
from __future__ import annotations


def _seed(table, cols, vals, where):
    col_list = ", ".join(cols)
    placeholders = ", ".join(
        "NULL" if v is None else ("'" + str(v).replace("'", "''") + "'") for v in vals
    )
    return (
        f"INSERT INTO {table} ({col_list}) SELECT {placeholders} "
        f"WHERE NOT EXISTS (SELECT 1 FROM {table} WHERE {where})"
    )


M19_GOST_GAPS = ";\n".join([
    # --- Part 2 §5.6 Containers / Tools / Software ---
    """CREATE TABLE IF NOT EXISTS emc_container_class (
       class_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       capacity_uom VARCHAR(32))""",
    """CREATE TABLE IF NOT EXISTS emc_container (
       container_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64) NOT NULL,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       capacity NUMERIC(14,3),
       capacity_uom VARCHAR(32),
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE')""",
    """CREATE TABLE IF NOT EXISTS emc_container_property (
       container_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (container_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_tool_class (
       class_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512))""",
    """CREATE TABLE IF NOT EXISTS emc_tool (
       tool_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64) NOT NULL,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       equipment_id VARCHAR(64),
       calibration_due DATE,
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE')""",
    """CREATE TABLE IF NOT EXISTS emc_tool_property (
       tool_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (tool_id, prop_key))""",
    """CREATE TABLE IF NOT EXISTS emc_software_class (
       class_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512))""",
    """CREATE TABLE IF NOT EXISTS emc_software (
       software_id VARCHAR(64) PRIMARY KEY,
       class_id VARCHAR(64) NOT NULL,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       vendor VARCHAR(128),
       version_label VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE')""",
    """CREATE TABLE IF NOT EXISTS emc_software_property (
       software_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (software_id, prop_key))""",
    # --- Part 2 Operations Definition + Schedule ---
    """CREATE TABLE IF NOT EXISTS emc_operations_definition (
       definition_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       published_flag VARCHAR(8) NOT NULL DEFAULT 'false',
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
       PRIMARY KEY (definition_id, version))""",
    """CREATE TABLE IF NOT EXISTS emc_operations_definition_segment (
       definition_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       segment_id VARCHAR(64) NOT NULL,
       sequence_no INTEGER NOT NULL DEFAULT 1,
       PRIMARY KEY (definition_id, version, segment_id))""",
    """CREATE TABLE IF NOT EXISTS emc_operations_schedule (
       schedule_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       hierarchy_scope_id VARCHAR(64),
       start_time TIMESTAMP,
       end_time TIMESTAMP,
       state VARCHAR(32) NOT NULL DEFAULT 'RELEASED',
       description VARCHAR(512))""",
    """CREATE TABLE IF NOT EXISTS emc_operations_request (
       request_id VARCHAR(64) PRIMARY KEY,
       schedule_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64),
       definition_version VARCHAR(16),
       priority INTEGER NOT NULL DEFAULT 5,
       requested_start TIMESTAMP,
       requested_end TIMESTAMP,
       state VARCHAR(32) NOT NULL DEFAULT 'RELEASED',
       description VARCHAR(512))""",
    # --- Part 4 Resource Relationship Network ---
    """CREATE TABLE IF NOT EXISTS emc_resource_relationship_network (
       network_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE')""",
    """CREATE TABLE IF NOT EXISTS emc_resource_relationship (
       rel_id VARCHAR(64) PRIMARY KEY,
       network_id VARCHAR(64) NOT NULL,
       from_resource_type VARCHAR(32) NOT NULL,
       from_resource_id VARCHAR(64) NOT NULL,
       to_resource_type VARCHAR(32) NOT NULL,
       to_resource_id VARCHAR(64) NOT NULL,
       relationship_type VARCHAR(64) NOT NULL,
       dependency VARCHAR(32) NOT NULL DEFAULT 'USES')""",
    """CREATE TABLE IF NOT EXISTS emc_resource_relationship_property (
       rel_id VARCHAR(64) NOT NULL,
       prop_key VARCHAR(64) NOT NULL,
       prop_value VARCHAR(256),
       uom VARCHAR(32),
       PRIMARY KEY (rel_id, prop_key))""",
    # --- Part 4 Work Capability / Work Master Capability ---
    """CREATE TABLE IF NOT EXISTS emc_work_capability (
       capability_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       description VARCHAR(512),
       hierarchy_scope_id VARCHAR(64),
       status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE')""",
    """CREATE TABLE IF NOT EXISTS emc_work_capability_equipment (
       capability_id VARCHAR(64) NOT NULL,
       equipment_id VARCHAR(64) NOT NULL DEFAULT '',
       equipment_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, equipment_id, equipment_class_id))""",
    """CREATE TABLE IF NOT EXISTS emc_work_capability_material (
       capability_id VARCHAR(64) NOT NULL,
       definition_id VARCHAR(64) NOT NULL DEFAULT '',
       material_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 0,
       uom VARCHAR(16),
       PRIMARY KEY (capability_id, definition_id, material_class_id))""",
    """CREATE TABLE IF NOT EXISTS emc_work_capability_personnel (
       capability_id VARCHAR(64) NOT NULL,
       person_id VARCHAR(64) NOT NULL DEFAULT '',
       personnel_class_id VARCHAR(64) NOT NULL DEFAULT '',
       quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
       PRIMARY KEY (capability_id, person_id, personnel_class_id))""",
    """CREATE TABLE IF NOT EXISTS emc_work_capability_segment (
       capability_id VARCHAR(64) NOT NULL,
       segment_id VARCHAR(64) NOT NULL,
       PRIMARY KEY (capability_id, segment_id))""",
    """CREATE TABLE IF NOT EXISTS emc_work_master_capability (
       work_master_id VARCHAR(64) NOT NULL,
       version VARCHAR(16) NOT NULL DEFAULT '1',
       capability_id VARCHAR(64) NOT NULL,
       effective_from TIMESTAMP,
       effective_to TIMESTAMP,
       PRIMARY KEY (work_master_id, version, capability_id))""",
    # --- Part 4 Work Alert ---
    """CREATE TABLE IF NOT EXISTS emc_work_alert (
       alert_id VARCHAR(64) PRIMARY KEY,
       alert_type VARCHAR(64) NOT NULL,
       severity VARCHAR(32) NOT NULL DEFAULT 'WARNING',
       work_master_id VARCHAR(64),
       job_order_id VARCHAR(64),
       message VARCHAR(512) NOT NULL,
       status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
       raised_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       ack_by VARCHAR(64),
       ack_at TIMESTAMP)""",
    # --- Seeds: §5.6 ---
    _seed("emc_container_class", ["class_id", "name", "description", "capacity_uom"],
          ["CC-BIN", "Storage bin", "Bulk / WIP bin", "kg"],
          "class_id = 'CC-BIN'"),
    _seed("emc_container",
          ["container_id", "class_id", "name", "description", "hierarchy_scope_id",
           "capacity", "capacity_uom", "status"],
          ["CTR-BIN-A", "CC-BIN", "Line A bin", "WIP bin at WU-A01", "SCOPE-SITE-01",
           "500", "kg", "AVAILABLE"],
          "container_id = 'CTR-BIN-A'"),
    _seed("emc_container_property", ["container_id", "prop_key", "prop_value", "uom"],
          ["CTR-BIN-A", "tare_weight", "12.5", "kg"],
          "container_id = 'CTR-BIN-A' AND prop_key = 'tare_weight'"),
    _seed("emc_tool_class", ["class_id", "name", "description"],
          ["TC-FIXTURE", "Assembly fixture", "Mechanical fixture / jig"],
          "class_id = 'TC-FIXTURE'"),
    _seed("emc_tool",
          ["tool_id", "class_id", "name", "description", "hierarchy_scope_id",
           "equipment_id", "status"],
          ["TOOL-FIX-A01", "TC-FIXTURE", "Fixture A01", "Assembly fixture for WU-A01",
           "SCOPE-SITE-01", "WU-A01", "AVAILABLE"],
          "tool_id = 'TOOL-FIX-A01'"),
    _seed("emc_tool_property", ["tool_id", "prop_key", "prop_value", "uom"],
          ["TOOL-FIX-A01", "max_cycles", "10000", "1"],
          "tool_id = 'TOOL-FIX-A01' AND prop_key = 'max_cycles'"),
    _seed("emc_software_class", ["class_id", "name", "description"],
          ["SC-MES-AGENT", "MES agent runtime", "Edge / hub agent software"],
          "class_id = 'SC-MES-AGENT'"),
    _seed("emc_software",
          ["software_id", "class_id", "name", "description", "hierarchy_scope_id",
           "vendor", "version_label", "status"],
          ["SW-MES-CORE", "SC-MES-AGENT", "ERP-MES Core agent", "Demostand MES hub software",
           "SCOPE-SITE-01", "IoT Solutions", "2.2.0", "ACTIVE"],
          "software_id = 'SW-MES-CORE'"),
    _seed("emc_software_property", ["software_id", "prop_key", "prop_value", "uom"],
          ["SW-MES-CORE", "license", "demo", None],
          "software_id = 'SW-MES-CORE' AND prop_key = 'license'"),
    # --- Seeds: Ops Definition / Schedule ---
    _seed("emc_operations_definition",
          ["definition_id", "version", "name", "description", "hierarchy_scope_id",
           "published_flag", "status"],
          ["OD-ASSEMBLY-01", "1", "Assembly operations definition",
           "GOST Part 2 Operations Definition for assemble+pack", "SCOPE-SITE-01",
           "true", "ACTIVE"],
          "definition_id = 'OD-ASSEMBLY-01' AND version = '1'"),
    _seed("emc_operations_definition_segment",
          ["definition_id", "version", "segment_id", "sequence_no"],
          ["OD-ASSEMBLY-01", "1", "SEG-ASSEMBLE", "1"],
          "definition_id = 'OD-ASSEMBLY-01' AND version = '1' AND segment_id = 'SEG-ASSEMBLE'"),
    _seed("emc_operations_definition_segment",
          ["definition_id", "version", "segment_id", "sequence_no"],
          ["OD-ASSEMBLY-01", "1", "SEG-PACK", "2"],
          "definition_id = 'OD-ASSEMBLY-01' AND version = '1' AND segment_id = 'SEG-PACK'"),
    _seed("emc_operations_schedule",
          ["schedule_id", "name", "hierarchy_scope_id", "state", "description"],
          ["OS-DEMO-001", "Demo operations schedule", "SCOPE-SITE-01", "RELEASED",
           "Firm schedule linked to OD-ASSEMBLY-01"],
          "schedule_id = 'OS-DEMO-001'"),
    _seed("emc_operations_request",
          ["request_id", "schedule_id", "definition_id", "definition_version",
           "priority", "state", "description"],
          ["OR-DEMO-001", "OS-DEMO-001", "OD-ASSEMBLY-01", "1", "3", "RELEASED",
           "Request for assembly definition"],
          "request_id = 'OR-DEMO-001'"),
    # --- Seeds: RRN ---
    _seed("emc_resource_relationship_network",
          ["network_id", "name", "description", "hierarchy_scope_id", "status"],
          ["RRN-SITE-01", "Site-01 resource network",
           "Equipment uses tools/containers/software", "SCOPE-SITE-01", "ACTIVE"],
          "network_id = 'RRN-SITE-01'"),
    _seed("emc_resource_relationship",
          ["rel_id", "network_id", "from_resource_type", "from_resource_id",
           "to_resource_type", "to_resource_id", "relationship_type", "dependency"],
          ["RR-WU-A01-TOOL", "RRN-SITE-01", "EQUIPMENT", "WU-A01",
           "TOOL", "TOOL-FIX-A01", "USES", "REQUIRED"],
          "rel_id = 'RR-WU-A01-TOOL'"),
    _seed("emc_resource_relationship",
          ["rel_id", "network_id", "from_resource_type", "from_resource_id",
           "to_resource_type", "to_resource_id", "relationship_type", "dependency"],
          ["RR-WU-A01-CTR", "RRN-SITE-01", "EQUIPMENT", "WU-A01",
           "CONTAINER", "CTR-BIN-A", "USES", "OPTIONAL"],
          "rel_id = 'RR-WU-A01-CTR'"),
    _seed("emc_resource_relationship",
          ["rel_id", "network_id", "from_resource_type", "from_resource_id",
           "to_resource_type", "to_resource_id", "relationship_type", "dependency"],
          ["RR-SITE-SW", "RRN-SITE-01", "EQUIPMENT", "SITE-01",
           "SOFTWARE", "SW-MES-CORE", "RUNS", "REQUIRED"],
          "rel_id = 'RR-SITE-SW'"),
    _seed("emc_resource_relationship_property",
          ["rel_id", "prop_key", "prop_value", "uom"],
          ["RR-WU-A01-TOOL", "setup_min", "5", "min"],
          "rel_id = 'RR-WU-A01-TOOL' AND prop_key = 'setup_min'"),
    # --- Seeds: Work Capability ---
    _seed("emc_work_capability",
          ["capability_id", "name", "description", "hierarchy_scope_id", "status"],
          ["WC-ASSEMBLE-A01", "Assemble capability WU-A01",
           "Work capability for assembly at WU-A01", "SCOPE-SITE-01", "AVAILABLE"],
          "capability_id = 'WC-ASSEMBLE-A01'"),
    _seed("emc_work_capability_equipment",
          ["capability_id", "equipment_id", "equipment_class_id", "quantity"],
          ["WC-ASSEMBLE-A01", "WU-A01", "EQC-ASSEMBLY-MACHINE", "1"],
          "capability_id = 'WC-ASSEMBLE-A01' AND equipment_id = 'WU-A01'"),
    _seed("emc_work_capability_material",
          ["capability_id", "definition_id", "material_class_id", "quantity", "uom"],
          ["WC-ASSEMBLE-A01", "RAW-PLASTIC-GRANULE", "MCL-RAW", "2.5", "kg"],
          "capability_id = 'WC-ASSEMBLE-A01' AND definition_id = 'RAW-PLASTIC-GRANULE'"),
    _seed("emc_work_capability_personnel",
          ["capability_id", "person_id", "personnel_class_id", "quantity"],
          ["WC-ASSEMBLE-A01", "EMP-001", "PCL-OPERATOR", "1"],
          "capability_id = 'WC-ASSEMBLE-A01' AND person_id = 'EMP-001'"),
    _seed("emc_work_capability_segment",
          ["capability_id", "segment_id"],
          ["WC-ASSEMBLE-A01", "SEG-ASSEMBLE"],
          "capability_id = 'WC-ASSEMBLE-A01' AND segment_id = 'SEG-ASSEMBLE'"),
    _seed("emc_work_master_capability",
          ["work_master_id", "version", "capability_id"],
          ["WM-ASSEMBLE", "1", "WC-ASSEMBLE-A01"],
          "work_master_id = 'WM-ASSEMBLE' AND version = '1' AND capability_id = 'WC-ASSEMBLE-A01'"),
    # --- Seeds: Work Alert + KPI value ---
    _seed("emc_work_alert",
          ["alert_id", "alert_type", "severity", "work_master_id", "job_order_id",
           "message", "status"],
          ["WA-DEMO-001", "RESOURCE_SHORTAGE", "WARNING", "WM-ASSEMBLE", None,
           "Fixture TOOL-FIX-A01 calibration due soon", "OPEN"],
          "alert_id = 'WA-DEMO-001'"),
    _seed("emc_kpi_value",
          ["id", "kpi_code", "scope_id", "period_label", "value_num"],
          ["KPI-DEMO-OEE", "OEE", "SCOPE-SITE-01", "DEMO-SHIFT", "82.5"],
          "id = 'KPI-DEMO-OEE'"),
    # --- Maint / QA / Inv demo seeds ---
    _seed("emc_maintenance_request",
          ["request_id", "equipment_id", "description", "priority", "status"],
          ["MR-DEMO-001", "WU-A01", "Bearing noise on assembly cell", "2", "NEW"],
          "request_id = 'MR-DEMO-001'"),
    _seed("emc_maintenance_work_order",
          ["wo_id", "request_id", "equipment_id", "status"],
          ["MWO-DEMO-001", "MR-DEMO-001", "WU-A01", "PLANNED"],
          "wo_id = 'MWO-DEMO-001'"),
    """UPDATE emc_maintenance_request SET status = 'ACCEPTED'
       WHERE request_id = 'MR-DEMO-001' AND status = 'NEW'""",
    """INSERT INTO emc_qa_test_result (id, job_no, lot_id, test_name, result, measurements_json)
       SELECT gen_random_uuid(), NULL, 'LOT-FG-0001', 'Visual inspection', 'PASS', '{"score":"OK"}'
       WHERE NOT EXISTS (
         SELECT 1 FROM emc_qa_test_result
         WHERE test_name = 'Visual inspection' AND lot_id = 'LOT-FG-0001')""",
    _seed("emc_inventory_document",
          ["doc_id", "kind", "status", "operator_person_id"],
          ["INV-DEMO-001", "TRANSFER", "DRAFT", "EMP-001"],
          "doc_id = 'INV-DEMO-001'"),
    """INSERT INTO emc_inventory_document_line
       (line_id, doc_id, definition_id, lot_id, quantity, source_location, dest_location)
       SELECT gen_random_uuid(), 'INV-DEMO-001', 'RAW-PLASTIC-GRANULE', NULL, 10,
              'WH-CENTRAL', 'WH-LINE-A01'
       WHERE NOT EXISTS (
         SELECT 1 FROM emc_inventory_document_line WHERE doc_id = 'INV-DEMO-001')""",
])


def build_gost_functions(fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null):
    out = []

    def _list_simple(name, sql, fields_map, out_fields):
        out.append(fn(
            name, [],
            OUT(RL("rows", out_fields)),
            [
                selN("rows_raw", sql),
                map_rows("rows", "${rows_raw}", fields_map),
                ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
            ],
        ))

    _list_simple(
        "emc_container_list",
        "SELECT container_id, class_id, name, COALESCE(description, '') AS description, "
        "COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, "
        "COALESCE(CAST(capacity AS VARCHAR), '') AS capacity, "
        "COALESCE(capacity_uom, '') AS capacity_uom, status "
        "FROM emc_container ORDER BY container_id",
        {"containerId": "${item.container_id}", "classId": "${item.class_id}",
         "name": "${item.name}", "description": "${item.description}",
         "hierarchyScopeId": "${item.hierarchy_scope_id}", "capacity": "${item.capacity}",
         "capacityUom": "${item.capacity_uom}", "status": "${item.status}"},
        [F("containerId"), F("classId"), F("name"), F("description"),
         F("hierarchyScopeId"), F("capacity"), F("capacityUom"), F("status")],
    )

    _list_simple(
        "emc_tool_list",
        "SELECT tool_id, class_id, name, COALESCE(description, '') AS description, "
        "COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, "
        "COALESCE(equipment_id, '') AS equipment_id, "
        "COALESCE(CAST(calibration_due AS VARCHAR), '') AS calibration_due, status "
        "FROM emc_tool ORDER BY tool_id",
        {"toolId": "${item.tool_id}", "classId": "${item.class_id}", "name": "${item.name}",
         "description": "${item.description}", "hierarchyScopeId": "${item.hierarchy_scope_id}",
         "equipmentId": "${item.equipment_id}", "calibrationDue": "${item.calibration_due}",
         "status": "${item.status}"},
        [F("toolId"), F("classId"), F("name"), F("description"),
         F("hierarchyScopeId"), F("equipmentId"), F("calibrationDue"), F("status")],
    )

    _list_simple(
        "emc_software_list",
        "SELECT software_id, class_id, name, COALESCE(description, '') AS description, "
        "COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, "
        "COALESCE(vendor, '') AS vendor, COALESCE(version_label, '') AS version_label, status "
        "FROM emc_software ORDER BY software_id",
        {"softwareId": "${item.software_id}", "classId": "${item.class_id}", "name": "${item.name}",
         "description": "${item.description}", "hierarchyScopeId": "${item.hierarchy_scope_id}",
         "vendor": "${item.vendor}", "versionLabel": "${item.version_label}", "status": "${item.status}"},
        [F("softwareId"), F("classId"), F("name"), F("description"),
         F("hierarchyScopeId"), F("vendor"), F("versionLabel"), F("status")],
    )

    _list_simple(
        "emc_opsdef_list",
        "SELECT definition_id, version, name, COALESCE(description, '') AS description, "
        "COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, published_flag, status "
        "FROM emc_operations_definition ORDER BY definition_id, version",
        {"definitionId": "${item.definition_id}", "version": "${item.version}",
         "name": "${item.name}", "description": "${item.description}",
         "hierarchyScopeId": "${item.hierarchy_scope_id}",
         "publishedFlag": "${item.published_flag}", "status": "${item.status}"},
        [F("definitionId"), F("version"), F("name"), F("description"),
         F("hierarchyScopeId"), F("publishedFlag"), F("status")],
    )

    out.append(fn(
        "emc_opsdef_segments_list",
        [F("definitionId")],
        OUT(RL("rows", [F("definitionId"), F("version"), F("segmentId"), F("sequenceNo")])),
        [
            selN("rows_raw",
                 "SELECT definition_id, version, segment_id, sequence_no "
                 "FROM emc_operations_definition_segment "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), definition_id) = definition_id "
                 "ORDER BY definition_id, sequence_no",
                 ["${input.definitionId}"]),
            map_rows("rows", "${rows_raw}", {
                "definitionId": "${item.definition_id}", "version": "${item.version}",
                "segmentId": "${item.segment_id}", "sequenceNo": "${item.sequence_no}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    _list_simple(
        "emc_opssched_list",
        "SELECT schedule_id, name, COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, "
        "COALESCE(CAST(start_time AS VARCHAR), '') AS start_time, "
        "COALESCE(CAST(end_time AS VARCHAR), '') AS end_time, state, "
        "COALESCE(description, '') AS description "
        "FROM emc_operations_schedule ORDER BY schedule_id",
        {"scheduleId": "${item.schedule_id}", "name": "${item.name}",
         "hierarchyScopeId": "${item.hierarchy_scope_id}", "startTime": "${item.start_time}",
         "endTime": "${item.end_time}", "state": "${item.state}",
         "description": "${item.description}"},
        [F("scheduleId"), F("name"), F("hierarchyScopeId"), F("startTime"),
         F("endTime"), F("state"), F("description")],
    )

    _list_simple(
        "emc_opsreq_list",
        "SELECT request_id, schedule_id, COALESCE(definition_id, '') AS definition_id, "
        "COALESCE(definition_version, '') AS definition_version, priority, state, "
        "COALESCE(description, '') AS description "
        "FROM emc_operations_request ORDER BY request_id",
        {"requestId": "${item.request_id}", "scheduleId": "${item.schedule_id}",
         "definitionId": "${item.definition_id}", "definitionVersion": "${item.definition_version}",
         "priority": "${item.priority}", "state": "${item.state}",
         "description": "${item.description}"},
        [F("requestId"), F("scheduleId"), F("definitionId"), F("definitionVersion"),
         F("priority"), F("state"), F("description")],
    )

    _list_simple(
        "emc_rrn_list",
        "SELECT network_id, name, COALESCE(description, '') AS description, "
        "COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, status "
        "FROM emc_resource_relationship_network ORDER BY network_id",
        {"networkId": "${item.network_id}", "name": "${item.name}",
         "description": "${item.description}", "hierarchyScopeId": "${item.hierarchy_scope_id}",
         "status": "${item.status}"},
        [F("networkId"), F("name"), F("description"), F("hierarchyScopeId"), F("status")],
    )

    out.append(fn(
        "emc_rrn_edges_list",
        [F("networkId")],
        OUT(RL("rows", [F("relId"), F("networkId"), F("fromType"), F("fromId"),
                        F("toType"), F("toId"), F("relationshipType"), F("dependency")])),
        [
            selN("rows_raw",
                 "SELECT rel_id, network_id, from_resource_type, from_resource_id, "
                 "to_resource_type, to_resource_id, relationship_type, dependency "
                 "FROM emc_resource_relationship "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), network_id) = network_id "
                 "ORDER BY network_id, rel_id",
                 ["${input.networkId}"]),
            map_rows("rows", "${rows_raw}", {
                "relId": "${item.rel_id}", "networkId": "${item.network_id}",
                "fromType": "${item.from_resource_type}", "fromId": "${item.from_resource_id}",
                "toType": "${item.to_resource_type}", "toId": "${item.to_resource_id}",
                "relationshipType": "${item.relationship_type}",
                "dependency": "${item.dependency}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    _list_simple(
        "emc_workcap_list",
        "SELECT capability_id, name, COALESCE(description, '') AS description, "
        "COALESCE(hierarchy_scope_id, '') AS hierarchy_scope_id, status "
        "FROM emc_work_capability ORDER BY capability_id",
        {"capabilityId": "${item.capability_id}", "name": "${item.name}",
         "description": "${item.description}", "hierarchyScopeId": "${item.hierarchy_scope_id}",
         "status": "${item.status}"},
        [F("capabilityId"), F("name"), F("description"), F("hierarchyScopeId"), F("status")],
    )

    out.append(fn(
        "emc_workcap_children_list",
        [F("capabilityId")],
        OUT(RL("rows", [F("kind"), F("capabilityId"), F("refId"), F("classId"),
                        F("quantity"), F("uom")])),
        [
            selN("rows_raw",
                 "SELECT 'EQUIPMENT' AS kind, capability_id, equipment_id AS ref_id, "
                 "equipment_class_id AS class_id, quantity, '' AS uom "
                 "FROM emc_work_capability_equipment "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id "
                 "UNION ALL "
                 "SELECT 'MATERIAL', capability_id, definition_id, material_class_id, quantity, "
                 "COALESCE(uom, '') FROM emc_work_capability_material "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id "
                 "UNION ALL "
                 "SELECT 'PERSONNEL', capability_id, person_id, personnel_class_id, quantity, '' "
                 "FROM emc_work_capability_personnel "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id "
                 "UNION ALL "
                 "SELECT 'SEGMENT', capability_id, segment_id, '', CAST(1 AS NUMERIC), '' "
                 "FROM emc_work_capability_segment "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), capability_id) = capability_id",
                 ["${input.capabilityId}", "${input.capabilityId}",
                  "${input.capabilityId}", "${input.capabilityId}"]),
            map_rows("rows", "${rows_raw}", {
                "kind": "${item.kind}", "capabilityId": "${item.capability_id}",
                "refId": "${item.ref_id}", "classId": "${item.class_id}",
                "quantity": "${item.quantity}", "uom": "${item.uom}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    _list_simple(
        "emc_wmc_list",
        "SELECT work_master_id, version, capability_id, "
        "COALESCE(CAST(effective_from AS VARCHAR), '') AS effective_from, "
        "COALESCE(CAST(effective_to AS VARCHAR), '') AS effective_to "
        "FROM emc_work_master_capability ORDER BY work_master_id, version, capability_id",
        {"workMasterId": "${item.work_master_id}", "version": "${item.version}",
         "capabilityId": "${item.capability_id}", "effectiveFrom": "${item.effective_from}",
         "effectiveTo": "${item.effective_to}"},
        [F("workMasterId"), F("version"), F("capabilityId"),
         F("effectiveFrom"), F("effectiveTo")],
    )

    _list_simple(
        "emc_work_alert_list",
        "SELECT alert_id, alert_type, severity, COALESCE(work_master_id, '') AS work_master_id, "
        "COALESCE(job_order_id, '') AS job_order_id, message, status, "
        "CAST(raised_at AS VARCHAR) AS raised_at, "
        "COALESCE(ack_by, '') AS ack_by, COALESCE(CAST(ack_at AS VARCHAR), '') AS ack_at "
        "FROM emc_work_alert ORDER BY raised_at DESC",
        {"alertId": "${item.alert_id}", "alertType": "${item.alert_type}",
         "severity": "${item.severity}", "workMasterId": "${item.work_master_id}",
         "jobOrderId": "${item.job_order_id}", "message": "${item.message}",
         "status": "${item.status}", "raisedAt": "${item.raised_at}",
         "ackBy": "${item.ack_by}", "ackAt": "${item.ack_at}"},
        [F("alertId"), F("alertType"), F("severity"), F("workMasterId"),
         F("jobOrderId"), F("message"), F("status"), F("raisedAt"), F("ackBy"), F("ackAt")],
    )

    out.append(fn(
        "emc_work_alert_ack",
        [F("alertId"), F("ackBy")],
        OUT(F("alertId"), F("status")),
        [
            sel1("a", "SELECT alert_id, status FROM emc_work_alert WHERE alert_id = ?",
                 ["${input.alertId}"]),
            fail_null("a", "ALERT_NOT_FOUND", "Work alert not found"),
            ex("UPDATE emc_work_alert SET status = 'ACKNOWLEDGED', ack_by = ?, ack_at = CURRENT_TIMESTAMP "
               "WHERE alert_id = ?",
               ["${input.ackBy}", "${input.alertId}"]),
            ret({"error_code": "OK", "error_message": "", "alertId": "${input.alertId}",
                 "status": "ACKNOWLEDGED"}),
        ],
    ))

    out.append(fn(
        "emc_opsdef_upsert",
        [F("definitionId"), F("version"), F("name"), F("description"),
         F("hierarchyScopeId"), F("publishedFlag")],
        OUT(F("definitionId"), F("version")),
        [
            fail_null("input.definitionId", "VALIDATION", "definitionId is required"),
            fail_null("input.name", "VALIDATION", "name is required"),
            ex("UPDATE emc_operations_definition SET name = ?, description = NULLIF(?, ''), "
               "hierarchy_scope_id = NULLIF(?, ''), published_flag = COALESCE(NULLIF(?, ''), 'false') "
               "WHERE definition_id = ? AND version = ?",
               ["${input.name}", "${input.description}", "${input.hierarchyScopeId}",
                "${input.publishedFlag}", "${input.definitionId}", "${input.version}"]),
            ex("INSERT INTO emc_operations_definition "
               "(definition_id, version, name, description, hierarchy_scope_id, published_flag) "
               "SELECT ?, COALESCE(NULLIF(?, ''), '1'), ?, NULLIF(?, ''), NULLIF(?, ''), "
               "COALESCE(NULLIF(?, ''), 'false') "
               "WHERE NOT EXISTS (SELECT 1 FROM emc_operations_definition "
               "WHERE definition_id = ? AND version = COALESCE(NULLIF(?, ''), '1'))",
               ["${input.definitionId}", "${input.version}", "${input.name}",
                "${input.description}", "${input.hierarchyScopeId}", "${input.publishedFlag}",
                "${input.definitionId}", "${input.version}"]),
            ret({"error_code": "OK", "error_message": "",
                 "definitionId": "${input.definitionId}", "version": "${input.version}"}),
        ],
    ))

    return out
