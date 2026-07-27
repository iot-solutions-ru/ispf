# -*- coding: utf-8 -*-
"""ERP-MES Core 2.2.1 — rich demostand seeds (3+ examples per process)."""
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


def _lot(lot_id, barcode, definition_id, status, location, qty, uom):
    return _seed(
        "emc_material_lot",
        ["lot_id", "barcode", "definition_id", "status", "storage_location", "quantity", "base_uom"],
        [lot_id, barcode, definition_id, status, location, qty, uom],
        f"lot_id = '{lot_id}'",
    )


def _edge(inp, out, qty):
    return (
        f"INSERT INTO emc_lot_genealogy (id, input_lot_id, output_lot_id, quantity) "
        f"SELECT gen_random_uuid(), '{inp}', '{out}', {qty} "
        f"WHERE NOT EXISTS (SELECT 1 FROM emc_lot_genealogy "
        f"WHERE input_lot_id = '{inp}' AND output_lot_id = '{out}')"
    )


M20_DEMO_RICH = ";\n".join([
    """CREATE TABLE IF NOT EXISTS emc_job_lot_link (
       job_no VARCHAR(64) NOT NULL,
       lot_id VARCHAR(64) NOT NULL,
       link_role VARCHAR(32) NOT NULL DEFAULT 'PRODUCED',
       PRIMARY KEY (job_no, lot_id, link_role))""",
    # --- Extra lots (3 genealogy chains) ---
    _lot("LOT-RAW-0003", "BC-RAW-0003", "RAW-PLASTIC-GRANULE", "STOCK", "WH-CENTRAL", "800", "kg"),
    _lot("LOT-RAW-0004", "BC-RAW-0004", "RAW-PACKAGING-BOX", "STOCK", "WH-CENTRAL", "400", "pcs"),
    _lot("LOT-RAW-0005", "BC-RAW-0005", "RAW-PLASTIC-GRANULE", "STOCK", "WH-LINE-A01", "300", "kg"),
    _lot("LOT-WIP-0002", "BC-WIP-0002", "WIP-HOUSING", "STOCK", "WH-CENTRAL", "90", "pcs"),
    _lot("LOT-WIP-0003", "BC-WIP-0003", "WIP-HOUSING", "STOCK", "WH-LINE-A01", "60", "pcs"),
    _lot("LOT-FG-0002", "BC-FG-0002", "FG-UNIT-PACKED", "STOCK", "WH-CENTRAL", "85", "pcs"),
    _lot("LOT-FG-0003", "BC-FG-0003", "FG-UNIT-PACKED", "STOCK", "WH-CENTRAL", "40", "pcs"),
    _lot("LOT-FG-0004", "BC-FG-0004", "FG-UNIT-PACKED", "QUARANTINE", "WH-CENTRAL", "25", "pcs"),
    # Chain 2: RAW-0003 → WIP-0002 → FG-0002
    _edge("LOT-RAW-0003", "LOT-WIP-0002", 90),
    _edge("LOT-WIP-0002", "LOT-FG-0002", 85),
    # Chain 3: RAW-0005 + RAW-0004 → WIP-0003 → FG-0003
    _edge("LOT-RAW-0005", "LOT-WIP-0003", 70),
    _edge("LOT-RAW-0004", "LOT-WIP-0003", 60),
    _edge("LOT-WIP-0003", "LOT-FG-0003", 40),
    # Chain 4 (quarantine FG): WIP-0001 also contributes to FG-0004
    _edge("LOT-WIP-0001", "LOT-FG-0004", 25),
    # --- Extra jobs (3+ statuses) ---
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version,
        segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end, actual_start)
       SELECT 'a0000004-0000-0000-0000-000000000004', 'JO-DEMO-004', 'WR-DEMO-001', 'WM-ASSEMBLE', '1',
              'SEG-ASSEMBLE', 'WU-A01', 'SUSPENDED', 'START', '2',
              TIMESTAMP '2026-07-24 12:00:00', TIMESTAMP '2026-07-24 14:00:00', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-004')""",
    """INSERT INTO emc_job_order (job_order_id, job_no, request_id, work_master_id, work_master_version,
        segment_id, equipment_id, dispatch_status, command, priority, planned_start, planned_end)
       SELECT 'a0000005-0000-0000-0000-000000000005', 'JO-DEMO-005', 'WR-DEMO-001', 'WM-PACK', '1',
              'SEG-PACK', 'WU-A02', 'ALLOWED', 'STORE', '3',
              TIMESTAMP '2026-07-24 14:00:00', TIMESTAMP '2026-07-24 16:00:00'
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_order WHERE job_no = 'JO-DEMO-005')""",
    """INSERT INTO emc_job_response (response_id, job_no, job_state, actual_start)
       SELECT 'b0000004-0000-0000-0000-000000000004', 'JO-DEMO-004', 'SUSPENDED', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = 'JO-DEMO-004')""",
    """INSERT INTO emc_job_response (response_id, job_no, job_state, actual_start)
       SELECT 'b0000002-0000-0000-0000-000000000002', 'JO-DEMO-002', 'RUNNING', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_job_response WHERE job_no = 'JO-DEMO-002')""",
    # Job ↔ lot links (so genealogy can follow selected job)
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-002", "LOT-FG-0001", "PRODUCED"],
          "job_no = 'JO-DEMO-002' AND lot_id = 'LOT-FG-0001'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-002", "LOT-WIP-0001", "PRODUCED"],
          "job_no = 'JO-DEMO-002' AND lot_id = 'LOT-WIP-0001'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-002", "LOT-RAW-0001", "CONSUMED"],
          "job_no = 'JO-DEMO-002' AND lot_id = 'LOT-RAW-0001'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-004", "LOT-FG-0002", "PRODUCED"],
          "job_no = 'JO-DEMO-004' AND lot_id = 'LOT-FG-0002'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-004", "LOT-WIP-0002", "PRODUCED"],
          "job_no = 'JO-DEMO-004' AND lot_id = 'LOT-WIP-0002'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-004", "LOT-RAW-0003", "CONSUMED"],
          "job_no = 'JO-DEMO-004' AND lot_id = 'LOT-RAW-0003'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-001", "LOT-FG-0003", "PRODUCED"],
          "job_no = 'JO-DEMO-001' AND lot_id = 'LOT-FG-0003'"),
    _seed("emc_job_lot_link", ["job_no", "lot_id", "link_role"],
          ["JO-DEMO-005", "LOT-FG-0004", "PRODUCED"],
          "job_no = 'JO-DEMO-005' AND lot_id = 'LOT-FG-0004'"),
    # --- Defects (3+) ---
    """INSERT INTO emc_defect_record
       (defect_id, defect_no, job_no, lot_id, defect_type_id, reason_code, severity, qty_declared, status, created_by)
       SELECT gen_random_uuid(), 'DEF-DEMO-001', 'JO-DEMO-002', 'LOT-FG-0001', 'DFT-VISUAL', 'RC-MATERIAL',
              'MINOR', 2, 'REGISTERED', 'EMP-001'
       WHERE NOT EXISTS (SELECT 1 FROM emc_defect_record WHERE defect_no = 'DEF-DEMO-001')""",
    """INSERT INTO emc_defect_record
       (defect_id, defect_no, job_no, lot_id, defect_type_id, reason_code, severity, qty_declared, status, created_by)
       SELECT gen_random_uuid(), 'DEF-DEMO-002', 'JO-DEMO-004', 'LOT-FG-0002', 'DFT-DIMENSION', 'RC-MACHINE',
              'MAJOR', 1, 'CONFIRMED', 'EMP-002'
       WHERE NOT EXISTS (SELECT 1 FROM emc_defect_record WHERE defect_no = 'DEF-DEMO-002')""",
    """INSERT INTO emc_defect_record
       (defect_id, defect_no, job_no, lot_id, defect_type_id, reason_code, severity, qty_declared, status, created_by)
       SELECT gen_random_uuid(), 'DEF-DEMO-003', 'JO-DEMO-001', 'LOT-FG-0003', 'DFT-FUNCTIONAL', 'RC-METHOD',
              'CRITICAL', 3, 'REGISTERED', 'EMP-001'
       WHERE NOT EXISTS (SELECT 1 FROM emc_defect_record WHERE defect_no = 'DEF-DEMO-003')""",
    # --- QA tests (3+) ---
    """INSERT INTO emc_qa_test_result (id, job_no, lot_id, test_name, result, measurements_json)
       SELECT gen_random_uuid(), 'JO-DEMO-002', 'LOT-FG-0001', 'Visual AQL', 'PASS', '{"sample":12}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_qa_test_result WHERE test_name = 'Visual AQL' AND lot_id = 'LOT-FG-0001')""",
    """INSERT INTO emc_qa_test_result (id, job_no, lot_id, test_name, result, measurements_json)
       SELECT gen_random_uuid(), 'JO-DEMO-004', 'LOT-FG-0002', 'Dimensional check', 'FAIL', '{"tol":"±0.2"}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_qa_test_result WHERE test_name = 'Dimensional check' AND lot_id = 'LOT-FG-0002')""",
    """INSERT INTO emc_qa_test_result (id, job_no, lot_id, test_name, result, measurements_json)
       SELECT gen_random_uuid(), 'JO-DEMO-001', 'LOT-FG-0003', 'Functional bench', 'PASS', '{"ok":true}'
       WHERE NOT EXISTS (SELECT 1 FROM emc_qa_test_result WHERE test_name = 'Functional bench' AND lot_id = 'LOT-FG-0003')""",
    # --- Inventory docs (3+) ---
    _seed("emc_inventory_document", ["doc_id", "kind", "status", "operator_person_id"],
          ["INV-DEMO-002", "DELIVERY_REQUEST", "SUBMITTED", "EMP-002"],
          "doc_id = 'INV-DEMO-002'"),
    _seed("emc_inventory_document", ["doc_id", "kind", "status", "operator_person_id"],
          ["INV-DEMO-003", "STOCK_TAKING", "DRAFT", "EMP-001"],
          "doc_id = 'INV-DEMO-003'"),
    """INSERT INTO emc_inventory_document_line
       (line_id, doc_id, definition_id, lot_id, quantity, source_location, dest_location)
       SELECT gen_random_uuid(), 'INV-DEMO-002', 'RAW-PACKAGING-BOX', 'LOT-RAW-0004', 50, 'WH-CENTRAL', 'WH-LINE-A01'
       WHERE NOT EXISTS (SELECT 1 FROM emc_inventory_document_line WHERE doc_id = 'INV-DEMO-002')""",
    """INSERT INTO emc_inventory_document_line
       (line_id, doc_id, definition_id, lot_id, quantity, source_location, dest_location)
       SELECT gen_random_uuid(), 'INV-DEMO-003', 'FG-UNIT-PACKED', 'LOT-FG-0002', 85, 'WH-CENTRAL', 'WH-CENTRAL'
       WHERE NOT EXISTS (SELECT 1 FROM emc_inventory_document_line WHERE doc_id = 'INV-DEMO-003')""",
    # --- Maintenance (3+) ---
    _seed("emc_maintenance_request",
          ["request_id", "equipment_id", "description", "priority", "status"],
          ["MR-DEMO-002", "WU-A02", "Pack cell sensor drift", "3", "NEW"],
          "request_id = 'MR-DEMO-002'"),
    _seed("emc_maintenance_request",
          ["request_id", "equipment_id", "description", "priority", "status"],
          ["MR-DEMO-003", "LINE-A", "Conveyor lubrication overdue", "4", "ACCEPTED"],
          "request_id = 'MR-DEMO-003'"),
    _seed("emc_maintenance_work_order",
          ["wo_id", "request_id", "equipment_id", "status"],
          ["MWO-DEMO-003", "MR-DEMO-003", "LINE-A", "PLANNED"],
          "wo_id = 'MWO-DEMO-003'"),
    # --- Work alerts (3+) ---
    _seed("emc_work_alert",
          ["alert_id", "alert_type", "severity", "work_master_id", "job_order_id", "message", "status"],
          ["WA-DEMO-002", "QUALITY_HOLD", "CRITICAL", "WM-PACK", "JO-DEMO-001",
           "LOT-FG-0003 pending functional retest", "OPEN"],
          "alert_id = 'WA-DEMO-002'"),
    _seed("emc_work_alert",
          ["alert_id", "alert_type", "severity", "work_master_id", "job_order_id", "message", "status"],
          ["WA-DEMO-003", "SCHEDULE_SLIP", "WARNING", "WM-ASSEMBLE", "JO-DEMO-004",
           "JO-DEMO-004 suspended > 30 min", "OPEN"],
          "alert_id = 'WA-DEMO-003'"),
    # --- Ops events (3+) ---
    """INSERT INTO emc_operations_event
       (event_id, definition_code, job_no, equipment_id, time_min, comment_text, status, started_at)
       SELECT gen_random_uuid(),
              (SELECT code FROM emc_operations_event_definition ORDER BY sort_order LIMIT 1),
              'JO-DEMO-002', 'WU-A01', 12, 'Seed downtime #1', 'OPEN', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event WHERE comment_text = 'Seed downtime #1')""",
    """INSERT INTO emc_operations_event
       (event_id, definition_code, job_no, equipment_id, time_min, comment_text, status, started_at, ended_at)
       SELECT gen_random_uuid(),
              (SELECT code FROM emc_operations_event_definition ORDER BY sort_order LIMIT 1 OFFSET 1),
              'JO-DEMO-004', 'WU-A01', 8, 'Seed downtime #2', 'CLOSED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event WHERE comment_text = 'Seed downtime #2')""",
    """INSERT INTO emc_operations_event
       (event_id, definition_code, job_no, equipment_id, time_min, comment_text, status, started_at)
       SELECT gen_random_uuid(),
              (SELECT code FROM emc_operations_event_definition ORDER BY sort_order LIMIT 1 OFFSET 2),
              'JO-DEMO-001', 'WU-A02', 5, 'Seed downtime #3', 'OPEN', CURRENT_TIMESTAMP
       WHERE NOT EXISTS (SELECT 1 FROM emc_operations_event WHERE comment_text = 'Seed downtime #3')""",
    # --- Containers / tools / software (3+) ---
    _seed("emc_container",
          ["container_id", "class_id", "name", "description", "hierarchy_scope_id",
           "capacity", "capacity_uom", "status"],
          ["CTR-BIN-B", "CC-BIN", "Line B bin", "WIP bin pack cell", "SCOPE-SITE-01",
           "300", "kg", "AVAILABLE"],
          "container_id = 'CTR-BIN-B'"),
    _seed("emc_container",
          ["container_id", "class_id", "name", "description", "hierarchy_scope_id",
           "capacity", "capacity_uom", "status"],
          ["CTR-BIN-C", "CC-BIN", "Quarantine bin", "Hold / quarantine", "SCOPE-SITE-01",
           "100", "pcs", "IN_USE"],
          "container_id = 'CTR-BIN-C'"),
    _seed("emc_tool",
          ["tool_id", "class_id", "name", "description", "hierarchy_scope_id",
           "equipment_id", "status"],
          ["TOOL-FIX-A02", "TC-FIXTURE", "Fixture A02", "Pack fixture", "SCOPE-SITE-01",
           "WU-A02", "AVAILABLE"],
          "tool_id = 'TOOL-FIX-A02'"),
    _seed("emc_tool",
          ["tool_id", "class_id", "name", "description", "hierarchy_scope_id",
           "equipment_id", "status"],
          ["TOOL-GAUGE-01", "TC-FIXTURE", "Go/NoGo gauge", "QC gauge", "SCOPE-SITE-01",
           "WU-A01", "CALIBRATION_DUE"],
          "tool_id = 'TOOL-GAUGE-01'"),
    _seed("emc_software",
          ["software_id", "class_id", "name", "description", "hierarchy_scope_id",
           "vendor", "version_label", "status"],
          ["SW-PLC-A01", "SC-MES-AGENT", "Cell PLC runtime", "WU-A01 PLC image",
           "SCOPE-SITE-01", "VendorPLC", "3.2.1", "ACTIVE"],
          "software_id = 'SW-PLC-A01'"),
    _seed("emc_software",
          ["software_id", "class_id", "name", "description", "hierarchy_scope_id",
           "vendor", "version_label", "status"],
          ["SW-HMI-LINE", "SC-MES-AGENT", "Line HMI", "Operator HMI pack",
           "SCOPE-SITE-01", "IoT Solutions", "1.4.0", "ACTIVE"],
          "software_id = 'SW-HMI-LINE'"),
    # Extra RRN edges
    _seed("emc_resource_relationship",
          ["rel_id", "network_id", "from_resource_type", "from_resource_id",
           "to_resource_type", "to_resource_id", "relationship_type", "dependency"],
          ["RR-WU-A02-TOOL", "RRN-SITE-01", "EQUIPMENT", "WU-A02",
           "TOOL", "TOOL-FIX-A02", "USES", "REQUIRED"],
          "rel_id = 'RR-WU-A02-TOOL'"),
    _seed("emc_resource_relationship",
          ["rel_id", "network_id", "from_resource_type", "from_resource_id",
           "to_resource_type", "to_resource_id", "relationship_type", "dependency"],
          ["RR-WU-A01-PLC", "RRN-SITE-01", "EQUIPMENT", "WU-A01",
           "SOFTWARE", "SW-PLC-A01", "RUNS", "REQUIRED"],
          "rel_id = 'RR-WU-A01-PLC'"),
    # Ops definitions (3+)
    _seed("emc_operations_definition",
          ["definition_id", "version", "name", "description", "hierarchy_scope_id",
           "published_flag", "status"],
          ["OD-PACK-01", "1", "Pack operations definition", "Pack cell OD",
           "SCOPE-SITE-01", "true", "ACTIVE"],
          "definition_id = 'OD-PACK-01' AND version = '1'"),
    _seed("emc_operations_definition_segment",
          ["definition_id", "version", "segment_id", "sequence_no"],
          ["OD-PACK-01", "1", "SEG-PACK", "1"],
          "definition_id = 'OD-PACK-01' AND version = '1' AND segment_id = 'SEG-PACK'"),
    _seed("emc_operations_definition",
          ["definition_id", "version", "name", "description", "hierarchy_scope_id",
           "published_flag", "status"],
          ["OD-QA-SAMPLE", "1", "QA sample definition", "Quality sample OD",
           "SCOPE-SITE-01", "false", "DRAFT"],
          "definition_id = 'OD-QA-SAMPLE' AND version = '1'"),
    _seed("emc_operations_schedule",
          ["schedule_id", "name", "hierarchy_scope_id", "state", "description"],
          ["OS-DEMO-002", "Pack firm schedule", "SCOPE-SITE-01", "RELEASED", "Pack OD schedule"],
          "schedule_id = 'OS-DEMO-002'"),
    _seed("emc_operations_schedule",
          ["schedule_id", "name", "hierarchy_scope_id", "state", "description"],
          ["OS-DEMO-003", "QA sample schedule", "SCOPE-SITE-01", "DRAFT", "QA OD schedule"],
          "schedule_id = 'OS-DEMO-003'"),
    _seed("emc_operations_request",
          ["request_id", "schedule_id", "definition_id", "definition_version",
           "priority", "state", "description"],
          ["OR-DEMO-002", "OS-DEMO-002", "OD-PACK-01", "1", "2", "RELEASED", "Pack request"],
          "request_id = 'OR-DEMO-002'"),
    _seed("emc_operations_request",
          ["request_id", "schedule_id", "definition_id", "definition_version",
           "priority", "state", "description"],
          ["OR-DEMO-003", "OS-DEMO-003", "OD-QA-SAMPLE", "1", "5", "DRAFT", "QA request"],
          "request_id = 'OR-DEMO-003'"),
    # Work capabilities (3+)
    _seed("emc_work_capability",
          ["capability_id", "name", "description", "hierarchy_scope_id", "status"],
          ["WC-PACK-A02", "Pack capability WU-A02", "Pack cell WC", "SCOPE-SITE-01", "AVAILABLE"],
          "capability_id = 'WC-PACK-A02'"),
    _seed("emc_work_capability",
          ["capability_id", "name", "description", "hierarchy_scope_id", "status"],
          ["WC-QA-SAMPLE", "QA sample capability", "QA WC", "SCOPE-SITE-01", "AVAILABLE"],
          "capability_id = 'WC-QA-SAMPLE'"),
    _seed("emc_work_master_capability",
          ["work_master_id", "version", "capability_id"],
          ["WM-PACK", "1", "WC-PACK-A02"],
          "work_master_id = 'WM-PACK' AND version = '1' AND capability_id = 'WC-PACK-A02'"),
])


def build_m20_functions(fn, F, OUT, RL, selN, map_rows, ret):
    out = []
    out.append(fn(
        "emc_job_lots_list",
        [F("jobNo")],
        OUT(RL("rows", [F("jobNo"), F("lotId"), F("linkRole"), F("definitionId"), F("status")])),
        [
            selN("rows_raw",
                 "SELECT j.job_no, j.lot_id, j.link_role, "
                 "COALESCE(l.definition_id, '') AS definition_id, COALESCE(l.status, '') AS status "
                 "FROM emc_job_lot_link j "
                 "LEFT JOIN emc_material_lot l ON l.lot_id = j.lot_id "
                 "WHERE COALESCE(NULLIF(TRIM(?), ''), j.job_no) = j.job_no "
                 "ORDER BY j.job_no, j.link_role, j.lot_id",
                 ["${input.jobNo}"]),
            map_rows("rows", "${rows_raw}", {
                "jobNo": "${item.job_no}", "lotId": "${item.lot_id}",
                "linkRole": "${item.link_role}", "definitionId": "${item.definition_id}",
                "status": "${item.status}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))
    return out
