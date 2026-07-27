# -*- coding: utf-8 -*-
"""ERP-MES Core 2.1 / M4 extent: Part 5 profiles, ISO 22400 KPI registry, APS-lite BFF."""
from __future__ import annotations


def _seed(table, cols, vals, where):
    col_list = ", ".join(cols)
    placeholders = ", ".join("'" + str(v).replace("'", "''") + "'" for v in vals)
    return (
        f"INSERT INTO {table} ({col_list}) SELECT {placeholders} "
        f"WHERE NOT EXISTS (SELECT 1 FROM {table} WHERE {where})"
    )


M18_PART5_KPI = ";\n".join([
    """CREATE TABLE IF NOT EXISTS emc_erp_transaction_profile (
       verb VARCHAR(32) NOT NULL,
       noun VARCHAR(64) NOT NULL,
       direction VARCHAR(8) NOT NULL,
       description VARCHAR(256),
       PRIMARY KEY (verb, noun, direction))""",
    """CREATE TABLE IF NOT EXISTS emc_kpi_definition (
       kpi_code VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       iso22400_id VARCHAR(64),
       unit VARCHAR(32),
       description VARCHAR(512))""",
    """CREATE TABLE IF NOT EXISTS emc_kpi_value (
       id VARCHAR(64) PRIMARY KEY,
       kpi_code VARCHAR(64) NOT NULL,
       scope_id VARCHAR(64),
       period_label VARCHAR(128),
       value_num DOUBLE PRECISION,
       calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""",
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["PROCESS", "OPERATIONS_EVENT", "OUT", "Work commenced / downtime events"],
          "verb='PROCESS' AND noun='OPERATIONS_EVENT' AND direction='OUT'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["PROCESS", "OPERATIONS_PERFORMANCE", "OUT", "Job completed performance"],
          "verb='PROCESS' AND noun='OPERATIONS_PERFORMANCE' AND direction='OUT'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["PROCESS", "MATERIAL_LOT", "OUT", "Inventory / lot movement"],
          "verb='PROCESS' AND noun='MATERIAL_LOT' AND direction='OUT'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["PROCESS", "OPERATIONS_SCHEDULE", "IN", "Inbound firm schedule"],
          "verb='PROCESS' AND noun='OPERATIONS_SCHEDULE' AND direction='IN'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["SYNC", "MASTER_DATA", "IN", "Inbound master data replica"],
          "verb='SYNC' AND noun='MASTER_DATA' AND direction='IN'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["GET", "OPERATIONS_CAPABILITY", "IN", "Capability query from L4"],
          "verb='GET' AND noun='OPERATIONS_CAPABILITY' AND direction='IN'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["SHOW", "OPERATIONS_DEFINITION", "IN", "Show operations definition"],
          "verb='SHOW' AND noun='OPERATIONS_DEFINITION' AND direction='IN'"),
    _seed("emc_erp_transaction_profile",
          ["verb", "noun", "direction", "description"],
          ["SHOW", "PRODUCT_DEFINITION", "IN", "Show product definition"],
          "verb='SHOW' AND noun='PRODUCT_DEFINITION' AND direction='IN'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["OEE", "Overall Equipment Effectiveness", "PE001", "%", "A×P×Q"],
          "kpi_code='OEE'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["AVAILABILITY", "Availability", "PE002", "%", "Planned vs availability loss"],
          "kpi_code='AVAILABILITY'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["PERFORMANCE", "Performance Efficiency", "PE003", "%", "Speed / performance losses"],
          "kpi_code='PERFORMANCE'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["QUALITY", "Quality Ratio", "PE004", "%", "Good vs defective"],
          "kpi_code='QUALITY'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["SETUP_RATIO", "Setup Ratio", "PE016", "%", "Setup time / planned"],
          "kpi_code='SETUP_RATIO'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["SCRAP_RATIO", "Scrap Ratio", "PE010", "%", "Scrap / produced"],
          "kpi_code='SCRAP_RATIO'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["DEFECT_RATE", "Defect Rate", "PE011", "%", "Defect qty / jobs"],
          "kpi_code='DEFECT_RATE'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["INV_TURNS", "Inventory Turns", "PE022", "1", "Movement / average stock"],
          "kpi_code='INV_TURNS'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["MTTR", "Mean Time To Repair", "PE006", "min", "From closed downtime events"],
          "kpi_code='MTTR'"),
    _seed("emc_kpi_definition",
          ["kpi_code", "name", "iso22400_id", "unit", "description"],
          ["MTBF", "Mean Time Between Failures", "PE007", "min", "From downtime events"],
          "kpi_code='MTBF'"),
    # Seed capability child for packing cell (APS-lite gates)
    _seed("emc_operations_capability",
          ["capability_id", "operations_type", "equipment_id", "segment_id", "reason", "status"],
          ["CAP-WU-A02-PACK", "PRODUCTION", "WU-A02", "SEG-PACK", "Pack cell capability", "AVAILABLE"],
          "capability_id = 'CAP-WU-A02-PACK'"),
    _seed("emc_ops_capability_equipment",
          ["capability_id", "equipment_id", "equipment_class_id", "quantity"],
          ["CAP-WU-A02-PACK", "WU-A02", "EQC-PACK-MACHINE", "1"],
          "capability_id = 'CAP-WU-A02-PACK' AND equipment_id = 'WU-A02'"),
])


def build_m21_functions(fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null, when, invoke):
    out = []

    out.append(fn(
        "emc_erp_listProfiles",
        [],
        OUT(RL("rows", [F("verb"), F("noun"), F("direction"), F("description")])),
        [
            selN("rows_raw",
                 "SELECT verb, noun, direction, COALESCE(description, '') AS description "
                 "FROM emc_erp_transaction_profile ORDER BY direction, verb, noun"),
            map_rows("rows", "${rows_raw}", {
                "verb": "${item.verb}", "noun": "${item.noun}",
                "direction": "${item.direction}", "description": "${item.description}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_erp_listIntegrationLog",
        [],
        OUT(RL("rows", [F("direction"), F("verb"), F("noun"), F("success"), F("code"), F("message")])),
        [
            selN("rows_raw",
                 "SELECT direction, COALESCE(verb, '') AS verb, COALESCE(noun, '') AS noun, "
                 "CASE WHEN success THEN 'true' ELSE 'false' END AS success, "
                 "COALESCE(code, '') AS code, COALESCE(message, '') AS message "
                 "FROM emc_integration_log ORDER BY id DESC"),
            map_rows("rows", "${rows_raw}", {
                "direction": "${item.direction}", "verb": "${item.verb}", "noun": "${item.noun}",
                "success": "${item.success}", "code": "${item.code}", "message": "${item.message}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_kpi_listDefinitions",
        [],
        OUT(RL("rows", [F("kpiCode"), F("name"), F("iso22400Id"), F("unit"), F("description")])),
        [
            selN("rows_raw",
                 "SELECT kpi_code, name, COALESCE(iso22400_id, '') AS iso22400_id, "
                 "COALESCE(unit, '') AS unit, COALESCE(description, '') AS description "
                 "FROM emc_kpi_definition ORDER BY kpi_code"),
            map_rows("rows", "${rows_raw}", {
                "kpiCode": "${item.kpi_code}", "name": "${item.name}",
                "iso22400Id": "${item.iso22400_id}", "unit": "${item.unit}",
                "description": "${item.description}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_kpi_calc",
        [F("scopeId"), F("periodLabel")],
        OUT(F("calculated")),
        [
            # OEE from latest shift rows average
            sel1("oee",
                 "SELECT COALESCE(AVG(oee_pct), 0) AS v, COALESCE(AVG(availability_pct), 0) AS a, "
                 "COALESCE(AVG(performance_pct), 0) AS p, COALESCE(AVG(quality_pct), 0) AS q "
                 "FROM emc_oee_shift"),
            ex("DELETE FROM emc_kpi_value WHERE period_label = ? AND kpi_code IN "
               "('OEE','AVAILABILITY','PERFORMANCE','QUALITY','SETUP_RATIO','SCRAP_RATIO',"
               "'DEFECT_RATE','INV_TURNS','MTTR','MTBF')",
               ["${input.periodLabel}"]),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'OEE', NULLIF(?, ''), ?, ?)",
               ["${input.scopeId}", "${input.periodLabel}", "${oee.v}"]),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'AVAILABILITY', NULLIF(?, ''), ?, ?)",
               ["${input.scopeId}", "${input.periodLabel}", "${oee.a}"]),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'PERFORMANCE', NULLIF(?, ''), ?, ?)",
               ["${input.scopeId}", "${input.periodLabel}", "${oee.p}"]),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'QUALITY', NULLIF(?, ''), ?, ?)",
               ["${input.scopeId}", "${input.periodLabel}", "${oee.q}"]),
            sel1("setup",
                 "SELECT CASE WHEN COALESCE(SUM(planned_min), 0) = 0 THEN 0 ELSE "
                 "100.0 * COALESCE(SUM(CASE WHEN d.oee_bucket = 'SETUP' THEN e.time_min ELSE 0 END), 0) "
                 "/ SUM(planned_min) END AS v "
                 "FROM emc_oee_shift s "
                 "LEFT JOIN emc_operations_event e ON e.equipment_id = s.equipment_id "
                 "LEFT JOIN emc_operations_event_definition d ON d.code = e.definition_code"),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'SETUP_RATIO', NULLIF(?, ''), ?, COALESCE(?, 0))",
               ["${input.scopeId}", "${input.periodLabel}", "${setup.v}"]),
            sel1("scrap",
                 "SELECT CASE WHEN COALESCE(SUM(a.quantity), 0) = 0 THEN 0 ELSE "
                 "100.0 * COALESCE((SELECT SUM(COALESCE(d.qty_confirmed, d.qty_declared)) FROM emc_defect_record d "
                 "WHERE d.status IN ('CONFIRMED','CLOSED')), 0) / SUM(a.quantity) END AS v "
                 "FROM emc_material_actual a WHERE a.material_use = 'PRODUCED'"),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'SCRAP_RATIO', NULLIF(?, ''), ?, COALESCE(?, 0))",
               ["${input.scopeId}", "${input.periodLabel}", "${scrap.v}"]),
            sel1("def",
                 "SELECT CASE WHEN (SELECT COUNT(*) FROM emc_job_order) = 0 THEN 0 ELSE "
                 "100.0 * (SELECT COUNT(*) FROM emc_defect_record) / (SELECT COUNT(*) FROM emc_job_order) END AS v"),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'DEFECT_RATE', NULLIF(?, ''), ?, COALESCE(?, 0))",
               ["${input.scopeId}", "${input.periodLabel}", "${def.v}"]),
            sel1("turns",
                 "SELECT CASE WHEN COALESCE((SELECT SUM(quantity) FROM emc_material_lot), 0) = 0 THEN 0 ELSE "
                 "COALESCE((SELECT COUNT(*) FROM emc_inventory_document), 0) * 1.0 / "
                 "(SELECT SUM(quantity) FROM emc_material_lot) END AS v"),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'INV_TURNS', NULLIF(?, ''), ?, COALESCE(?, 0))",
               ["${input.scopeId}", "${input.periodLabel}", "${turns.v}"]),
            sel1("mt",
                 "SELECT COALESCE(AVG(time_min), 0) AS mttr, "
                 "CASE WHEN COUNT(*) <= 1 THEN 480 ELSE 480.0 / COUNT(*) END AS mtbf "
                 "FROM emc_operations_event WHERE ended_at IS NOT NULL"),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'MTTR', NULLIF(?, ''), ?, COALESCE(?, 0))",
               ["${input.scopeId}", "${input.periodLabel}", "${mt.mttr}"]),
            ex("INSERT INTO emc_kpi_value (id, kpi_code, scope_id, period_label, value_num) VALUES "
               "(gen_random_uuid(), 'MTBF', NULLIF(?, ''), ?, COALESCE(?, 0))",
               ["${input.scopeId}", "${input.periodLabel}", "${mt.mtbf}"]),
            ret({"error_code": "OK", "error_message": "", "calculated": "10"}),
        ],
    ))

    out.append(fn(
        "emc_kpi_listValues",
        [F("periodLabel")],
        OUT(RL("rows", [F("kpiCode"), F("scopeId"), F("periodLabel"), F("valueNum"), F("calculatedAt")])),
        [
            selN("rows_raw",
                 "SELECT kpi_code, COALESCE(scope_id, '') AS scope_id, COALESCE(period_label, '') AS period_label, "
                 "value_num, calculated_at FROM emc_kpi_value "
                 "WHERE (? = '' OR period_label = ?) ORDER BY kpi_code",
                 ["${input.periodLabel}", "${input.periodLabel}"]),
            map_rows("rows", "${rows_raw}", {
                "kpiCode": "${item.kpi_code}", "scopeId": "${item.scope_id}",
                "periodLabel": "${item.period_label}", "valueNum": "${item.value_num}",
                "calculatedAt": "${item.calculated_at}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    out.append(fn(
        "emc_joborder_updatePlan",
        [F("jobNo"), F("plannedStart"), F("plannedEnd")],
        OUT(F("jobNo"), F("plannedStart"), F("plannedEnd")),
        [
            sel1("job", "SELECT job_no, dispatch_status FROM emc_job_order WHERE job_no = ?", ["${input.jobNo}"]),
            fail_null("job", "JOB_NOT_FOUND", "Job order not found"),
            ex("UPDATE emc_job_order SET planned_start = NULLIF(?, ''), planned_end = NULLIF(?, '') WHERE job_no = ?",
               ["${input.plannedStart}", "${input.plannedEnd}", "${input.jobNo}"]),
            ex("INSERT INTO emc_job_order_audit (id, job_no, action, detail, actor) "
               "VALUES (gen_random_uuid(), ?, 'PLAN_UPDATED', CONCAT(?, ' .. ', ?), 'aps')",
               ["${input.jobNo}", "${input.plannedStart}", "${input.plannedEnd}"]),
            ret({"error_code": "OK", "error_message": "", "jobNo": "${input.jobNo}",
                 "plannedStart": "${input.plannedStart}", "plannedEnd": "${input.plannedEnd}"}),
        ],
    ))

    out.append(fn(
        "emc_aps_listConflicts",
        [],
        OUT(RL("rows", [F("jobNo"), F("equipmentId"), F("dispatchStatus"), F("conflictKind"), F("detail")])),
        [
            selN("rows_raw",
                 "SELECT o.job_no, COALESCE(o.equipment_id, '') AS equipment_id, o.dispatch_status, "
                 "'RESOURCE_BUSY' AS conflict_kind, "
                 "CONCAT('Another RUNNING job on ', o.equipment_id) AS detail "
                 "FROM emc_job_order o "
                 "WHERE o.dispatch_status IN ('ALLOWED','NOT_ALLOWED') "
                 "AND EXISTS (SELECT 1 FROM emc_job_order r WHERE r.equipment_id = o.equipment_id "
                 "AND r.dispatch_status = 'RUNNING' AND r.job_no <> o.job_no) "
                 "UNION ALL "
                 "SELECT o.job_no, COALESCE(o.equipment_id, '') AS equipment_id, o.dispatch_status, "
                 "'CAPABILITY_WINDOW' AS conflict_kind, "
                 "'No AVAILABLE operations capability window for equipment' AS detail "
                 "FROM emc_job_order o "
                 "WHERE o.dispatch_status IN ('ALLOWED','NOT_ALLOWED') "
                 "AND o.equipment_id IS NOT NULL "
                 "AND NOT EXISTS ("
                 "  SELECT 1 FROM emc_operations_capability c "
                 "  JOIN emc_ops_capability_equipment ce ON ce.capability_id = c.capability_id "
                 "  WHERE ce.equipment_id = o.equipment_id AND c.status = 'AVAILABLE' "
                 "  AND (c.available_from IS NULL OR c.available_from <= CURRENT_TIMESTAMP) "
                 "  AND (c.available_to IS NULL OR c.available_to >= CURRENT_TIMESTAMP)"
                 ") "
                 "ORDER BY job_no"),
            map_rows("rows", "${rows_raw}", {
                "jobNo": "${item.job_no}", "equipmentId": "${item.equipment_id}",
                "dispatchStatus": "${item.dispatch_status}", "conflictKind": "${item.conflict_kind}",
                "detail": "${item.detail}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))

    return out


M21_FUNCTION_NAMES = [
    "emc_erp_listProfiles",
    "emc_erp_listIntegrationLog",
    "emc_kpi_listDefinitions",
    "emc_kpi_calc",
    "emc_kpi_listValues",
    "emc_aps_listConflicts",
    "emc_joborder_updatePlan",
]
