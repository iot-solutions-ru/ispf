# -*- coding: utf-8 -*-
"""Pharma ISA-88 recipe / phase / batch run (overlay on erp-mes-core)."""
from __future__ import annotations


def _seed(table, cols, vals, where):
    col_list = ", ".join(cols)
    placeholders = ", ".join("'" + str(v).replace("'", "''") + "'" for v in vals)
    return (
        f"INSERT INTO {table} ({col_list}) SELECT {placeholders} "
        f"WHERE NOT EXISTS (SELECT 1 FROM {table} WHERE {where})"
    )


M9_ISA88 = ";\n".join([
    """CREATE TABLE IF NOT EXISTS pha_recipe (
       recipe_id VARCHAR(64) PRIMARY KEY,
       name VARCHAR(256) NOT NULL,
       product_definition_id VARCHAR(64),
       version VARCHAR(32) NOT NULL DEFAULT '1',
       status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE')""",
    """CREATE TABLE IF NOT EXISTS pha_recipe_phase (
       recipe_id VARCHAR(64) NOT NULL,
       phase_seq INT NOT NULL,
       phase_code VARCHAR(64) NOT NULL,
       phase_name VARCHAR(256) NOT NULL,
       segment_id VARCHAR(64),
       PRIMARY KEY (recipe_id, phase_seq))""",
    """CREATE TABLE IF NOT EXISTS pha_batch_run (
       batch_run_id VARCHAR(64) PRIMARY KEY,
       batch_id VARCHAR(64) NOT NULL,
       recipe_id VARCHAR(64) NOT NULL,
       job_no VARCHAR(64),
       phase_code VARCHAR(64) NOT NULL,
       phase_seq INT NOT NULL DEFAULT 1,
       status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
       started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)""",
    _seed("pha_recipe",
          ["recipe_id", "name", "product_definition_id", "version", "status"],
          ["RCP-TAB-CARTON", "Paracetamol tablet cartoning", "PD-TAB-CARTON", "1", "ACTIVE"],
          "recipe_id = 'RCP-TAB-CARTON'"),
    _seed("pha_recipe_phase",
          ["recipe_id", "phase_seq", "phase_code", "phase_name", "segment_id"],
          ["RCP-TAB-CARTON", "1", "DISPENSE", "Dispense API/excipients", "SEG-DISPENSE"],
          "recipe_id = 'RCP-TAB-CARTON' AND phase_seq = 1"),
    _seed("pha_recipe_phase",
          ["recipe_id", "phase_seq", "phase_code", "phase_name", "segment_id"],
          ["RCP-TAB-CARTON", "2", "GRANULATE", "Wet granulation", "SEG-GRANULATE"],
          "recipe_id = 'RCP-TAB-CARTON' AND phase_seq = 2"),
    _seed("pha_recipe_phase",
          ["recipe_id", "phase_seq", "phase_code", "phase_name", "segment_id"],
          ["RCP-TAB-CARTON", "3", "COMPRESS", "Tablet compression", "SEG-COMPRESS"],
          "recipe_id = 'RCP-TAB-CARTON' AND phase_seq = 3"),
    _seed("pha_recipe_phase",
          ["recipe_id", "phase_seq", "phase_code", "phase_name", "segment_id"],
          ["RCP-TAB-CARTON", "4", "PACK", "Blister + carton", "SEG-PACK"],
          "recipe_id = 'RCP-TAB-CARTON' AND phase_seq = 4"),
    _seed("pha_batch_run",
          ["batch_run_id", "batch_id", "recipe_id", "job_no", "phase_code", "phase_seq", "status"],
          ["BR-PH-001", "BATCH-PH-DEMO-001", "RCP-TAB-CARTON", "JO-PH-001", "COMPRESS", "3", "RUNNING"],
          "batch_run_id = 'BR-PH-001'"),
    # APS-lite / capability gate for pharma press
    """INSERT INTO emc_operations_capability (capability_id, operations_type, equipment_id, segment_id, reason, status)
       SELECT 'CAP-TPR-01', 'PRODUCTION', 'TPR-01', 'SEG-COMPRESS', 'Tablet press capability', 'AVAILABLE'
       WHERE NOT EXISTS (SELECT 1 FROM emc_operations_capability WHERE capability_id = 'CAP-TPR-01')""",
    """INSERT INTO emc_ops_capability_equipment (capability_id, equipment_id, equipment_class_id, quantity)
       SELECT 'CAP-TPR-01', 'TPR-01', 'EQC-TABLET-PRESS', '1'
       WHERE NOT EXISTS (SELECT 1 FROM emc_ops_capability_equipment
                         WHERE capability_id = 'CAP-TPR-01' AND equipment_id = 'TPR-01')""",
])


def build_isa88_functions(fn, F, OUT, RL, selN, sel1, map_rows, ret, ex, fail_null, when):
    out = []
    out.append(fn(
        "pha_recipe_list",
        [],
        OUT(RL("rows", [F("recipeId"), F("name"), F("productDefinitionId"), F("version"), F("status")])),
        [
            selN("rows_raw",
                 "SELECT recipe_id, name, COALESCE(product_definition_id,'') AS product_definition_id, "
                 "version, status FROM pha_recipe ORDER BY recipe_id"),
            map_rows("rows", "${rows_raw}", {
                "recipeId": "${item.recipe_id}", "name": "${item.name}",
                "productDefinitionId": "${item.product_definition_id}",
                "version": "${item.version}", "status": "${item.status}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))
    out.append(fn(
        "pha_recipe_listPhases",
        [F("recipeId")],
        OUT(RL("rows", [F("phaseSeq"), F("phaseCode"), F("phaseName"), F("segmentId")])),
        [
            selN("rows_raw",
                 "SELECT phase_seq, phase_code, phase_name, COALESCE(segment_id,'') AS segment_id "
                 "FROM pha_recipe_phase WHERE recipe_id = ? ORDER BY phase_seq",
                 ["${input.recipeId}"]),
            map_rows("rows", "${rows_raw}", {
                "phaseSeq": "${item.phase_seq}", "phaseCode": "${item.phase_code}",
                "phaseName": "${item.phase_name}", "segmentId": "${item.segment_id}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))
    out.append(fn(
        "pha_batch_start",
        [F("batchRunId"), F("batchId"), F("recipeId"), F("jobNo")],
        OUT(F("batchRunId"), F("phaseCode"), F("status")),
        [
            fail_null("input.batchRunId", "VALIDATION", "batchRunId required"),
            fail_null("input.recipeId", "VALIDATION", "recipeId required"),
            sel1("ph", "SELECT phase_code, phase_seq FROM pha_recipe_phase WHERE recipe_id = ? "
                       "ORDER BY phase_seq LIMIT 1", ["${input.recipeId}"]),
            fail_null("ph", "RECIPE_EMPTY", "Recipe has no phases"),
            ex("INSERT INTO pha_batch_run (batch_run_id, batch_id, recipe_id, job_no, phase_code, phase_seq, status) "
               "SELECT ?, ?, ?, NULLIF(?, ''), ?, ?, 'RUNNING' "
               "WHERE NOT EXISTS (SELECT 1 FROM pha_batch_run WHERE batch_run_id = ?)",
               ["${input.batchRunId}", "${input.batchId}", "${input.recipeId}", "${input.jobNo}",
                "${ph.phase_code}", "${ph.phase_seq}", "${input.batchRunId}"]),
            ret({"error_code": "OK", "error_message": "", "batchRunId": "${input.batchRunId}",
                 "phaseCode": "${ph.phase_code}", "status": "RUNNING"}),
        ],
    ))
    out.append(fn(
        "pha_batch_runPhase",
        [F("batchRunId"), F("phaseCode")],
        OUT(F("batchRunId"), F("phaseCode"), F("phaseSeq"), F("status")),
        [
            sel1("br", "SELECT batch_run_id, recipe_id, status FROM pha_batch_run WHERE batch_run_id = ?",
                 ["${input.batchRunId}"]),
            fail_null("br", "NOT_FOUND", "Batch run not found"),
            sel1("ph", "SELECT phase_seq, phase_code FROM pha_recipe_phase "
                       "WHERE recipe_id = ? AND phase_code = ?",
                 ["${br.recipe_id}", "${input.phaseCode}"]),
            fail_null("ph", "PHASE_NOT_FOUND", "Phase not in recipe"),
            ex("UPDATE pha_batch_run SET phase_code = ?, phase_seq = ?, updated_at = CURRENT_TIMESTAMP, "
               "status = CASE WHEN ? = (SELECT MAX(phase_seq) FROM pha_recipe_phase WHERE recipe_id = ?) "
               "THEN 'COMPLETED' ELSE 'RUNNING' END "
               "WHERE batch_run_id = ?",
               ["${input.phaseCode}", "${ph.phase_seq}", "${ph.phase_seq}", "${br.recipe_id}",
                "${input.batchRunId}"]),
            sel1("st", "SELECT status FROM pha_batch_run WHERE batch_run_id = ?", ["${input.batchRunId}"]),
            ret({"error_code": "OK", "error_message": "", "batchRunId": "${input.batchRunId}",
                 "phaseCode": "${input.phaseCode}", "phaseSeq": "${ph.phase_seq}", "status": "${st.status}"}),
        ],
    ))
    out.append(fn(
        "pha_batch_getStatus",
        [F("batchRunId")],
        OUT(F("batchRunId"), F("batchId"), F("recipeId"), F("jobNo"), F("phaseCode"), F("phaseSeq"), F("status")),
        [
            sel1("br",
                 "SELECT batch_run_id, batch_id, recipe_id, COALESCE(job_no,'') AS job_no, "
                 "phase_code, phase_seq, status FROM pha_batch_run WHERE batch_run_id = ?",
                 ["${input.batchRunId}"]),
            fail_null("br", "NOT_FOUND", "Batch run not found"),
            ret({"error_code": "OK", "error_message": "",
                 "batchRunId": "${br.batch_run_id}", "batchId": "${br.batch_id}",
                 "recipeId": "${br.recipe_id}", "jobNo": "${br.job_no}",
                 "phaseCode": "${br.phase_code}", "phaseSeq": "${br.phase_seq}",
                 "status": "${br.status}"}),
        ],
    ))
    out.append(fn(
        "pha_batch_list",
        [],
        OUT(RL("rows", [F("batchRunId"), F("batchId"), F("recipeId"), F("jobNo"), F("phaseCode"), F("status")])),
        [
            selN("rows_raw",
                 "SELECT batch_run_id, batch_id, recipe_id, COALESCE(job_no,'') AS job_no, phase_code, status "
                 "FROM pha_batch_run ORDER BY started_at DESC"),
            map_rows("rows", "${rows_raw}", {
                "batchRunId": "${item.batch_run_id}", "batchId": "${item.batch_id}",
                "recipeId": "${item.recipe_id}", "jobNo": "${item.job_no}",
                "phaseCode": "${item.phase_code}", "status": "${item.status}"}),
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ],
    ))
    return out
