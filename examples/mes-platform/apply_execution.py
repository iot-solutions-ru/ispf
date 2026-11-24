"""Inject the plan-execution contour into a mes-platform bundle.

Idempotent. Example:
  python apply_execution.py bundle.json ^
    --hub root.platform.singleton-blueprints.mes-platform-hub-v1 ^
    --app mes-platform
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

MIGRATION_ID = "mes_platform_execution_v1"
SEED_ID = "mes_platform_execution_v1_demo"

MIGRATION_SQL = """
CREATE TABLE IF NOT EXISTS mes_exec_asset (
  asset_id VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  site_code VARCHAR(32) NOT NULL,
  area_code VARCHAR(32) NOT NULL,
  asset_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  freshness VARCHAR(32) NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_exec_sku (
  sku_code VARCHAR(64) PRIMARY KEY,
  display_name VARCHAR(128) NOT NULL,
  uom VARCHAR(16) NOT NULL,
  quality_status VARCHAR(32) NOT NULL DEFAULT 'released',
  freeze_minutes INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS mes_exec_resource (
  resource_id VARCHAR(64) PRIMARY KEY,
  resource_type VARCHAR(32) NOT NULL,
  site_code VARCHAR(32) NOT NULL,
  qty NUMERIC(14,3) NOT NULL,
  reserved_qty NUMERIC(14,3) NOT NULL DEFAULT 0,
  uom VARCHAR(16) NOT NULL,
  data_status VARCHAR(32) NOT NULL,
  quality_status VARCHAR(32) NOT NULL,
  owner_role VARCHAR(64) NOT NULL,
  freshness VARCHAR(32) NOT NULL,
  comment_text VARCHAR(256) NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS mes_exec_reserve (
  reserve_id VARCHAR(64) PRIMARY KEY,
  resource_id VARCHAR(64) NOT NULL,
  pub_id VARCHAR(64) NOT NULL,
  qty NUMERIC(14,3) NOT NULL
);
CREATE TABLE IF NOT EXISTS mes_exec_candidate (
  candidate_id VARCHAR(64) PRIMARY KEY,
  site_code VARCHAR(32) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  planned_qty NUMERIC(14,3) NOT NULL,
  uom VARCHAR(16) NOT NULL,
  quality_status VARCHAR(32) NOT NULL,
  validation_status VARCHAR(32) NOT NULL,
  validation_message VARCHAR(256) NOT NULL DEFAULT '',
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_exec_snapshot (
  snapshot_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64) NOT NULL,
  payload_json VARCHAR(4000) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_exec_pub (
  pub_id VARCHAR(64) PRIMARY KEY,
  pub_version INTEGER NOT NULL,
  candidate_id VARCHAR(64) NOT NULL,
  previous_pub_id VARCHAR(64),
  site_code VARCHAR(32) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  planned_qty NUMERIC(14,3) NOT NULL,
  uom VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  shop_status VARCHAR(32) NOT NULL,
  snapshot_id VARCHAR(64) NOT NULL,
  reason_text VARCHAR(256) NOT NULL DEFAULT '',
  published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_exec_fact (
  fact_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  actual_qty NUMERIC(14,3) NOT NULL,
  delay_minutes INTEGER NOT NULL DEFAULT 0,
  reason_code VARCHAR(64) NOT NULL DEFAULT '',
  recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_exec_deviation (
  deviation_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  delay_minutes INTEGER NOT NULL,
  status VARCHAR(32) NOT NULL
);
CREATE TABLE IF NOT EXISTS mes_exec_tail (
  tail_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  tail_qty NUMERIC(14,3) NOT NULL,
  uom VARCHAR(16) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  period_code VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  status VARCHAR(32) NOT NULL
);
CREATE TABLE IF NOT EXISTS mes_exec_period_close (
  close_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64) NOT NULL,
  period_code VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  closed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (pub_id, period_code)
);
CREATE TABLE IF NOT EXISTS mes_exec_alert (
  alert_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64),
  site_code VARCHAR(32) NOT NULL,
  severity VARCHAR(32) NOT NULL,
  alert_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  owner_role VARCHAR(64) NOT NULL DEFAULT '',
  message_text VARCHAR(256) NOT NULL
);
CREATE TABLE IF NOT EXISTS mes_exec_inbox (
  message_id VARCHAR(64) PRIMARY KEY,
  message_type VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL UNIQUE,
  payload_json VARCHAR(4000) NOT NULL,
  status VARCHAR(32) NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS mes_exec_delivery (
  delivery_id VARCHAR(64) PRIMARY KEY,
  pub_id VARCHAR(64) NOT NULL,
  recipient_code VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  error_text VARCHAR(256) NOT NULL DEFAULT '',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (pub_id, recipient_code)
);
""".strip()

SEED_SQL = """
INSERT INTO mes_exec_asset (asset_id, display_name, site_code, area_code, asset_type, status, source_name, freshness)
SELECT 'TANK-01', 'White mass tank 01', 'PLANT-A', 'PROCESS', 'TANK', 'running', 'CUTE_PROCESS', 'fresh'
WHERE NOT EXISTS (SELECT 1 FROM mes_exec_asset WHERE asset_id = 'TANK-01');
INSERT INTO mes_exec_asset (asset_id, display_name, site_code, area_code, asset_type, status, source_name, freshness)
SELECT 'LINE-A01', 'Filling line A01', 'PLANT-A', 'FILLING', 'LINE', 'running', 'CUTE_FILLING', 'stale'
WHERE NOT EXISTS (SELECT 1 FROM mes_exec_asset WHERE asset_id = 'LINE-A01');
INSERT INTO mes_exec_sku (sku_code, display_name, uom, quality_status, freeze_minutes)
SELECT 'MAT-WIDGET-A01', 'Assembly widget A01', 'EA', 'released', 30
WHERE NOT EXISTS (SELECT 1 FROM mes_exec_sku WHERE sku_code = 'MAT-WIDGET-A01');
INSERT INTO mes_exec_sku (sku_code, display_name, uom, quality_status, freeze_minutes)
SELECT 'MAT-BLOCKED', 'Blocked trial SKU', 'EA', 'blocked', 0
WHERE NOT EXISTS (SELECT 1 FROM mes_exec_sku WHERE sku_code = 'MAT-BLOCKED');
INSERT INTO mes_exec_resource (resource_id, resource_type, site_code, qty, reserved_qty, uom, data_status, quality_status, owner_role, freshness)
SELECT 'RES-TANK-01', 'SEMI', 'PLANT-A', 1000, 0, 'KG', 'confirmed', 'released', 'production', 'fresh'
WHERE NOT EXISTS (SELECT 1 FROM mes_exec_resource WHERE resource_id = 'RES-TANK-01');
INSERT INTO mes_exec_resource (resource_id, resource_type, site_code, qty, reserved_qty, uom, data_status, quality_status, owner_role, freshness)
SELECT 'RES-CELL-01', 'COOLING', 'PLANT-A', 10, 0, 'PAL', 'confirmed', 'blocked', 'cooling', 'fresh'
WHERE NOT EXISTS (SELECT 1 FROM mes_exec_resource WHERE resource_id = 'RES-CELL-01');
""".strip()


def script(steps: list) -> str:
    return json.dumps({"steps": steps}, separators=(",", ":"), ensure_ascii=False)


def ret(fields: dict) -> dict:
    return {"type": "return", "fields": fields}


def ok(*extra: tuple[str, str]) -> list[dict]:
    fields = [{"name": "error_code", "type": "STRING"}, {"name": "error_message", "type": "STRING"}]
    fields.extend({"name": name, "type": typ} for name, typ in extra)
    return fields


def rows_field(name: str, columns: list[tuple[str, str]]) -> dict:
    return {
        "name": "rows",
        "type": "RECORD_LIST",
        "nestedSchema": {
            "name": name,
            "fields": [{"name": col, "type": typ} for col, typ in columns],
        },
    }


def fn(hub: str, name: str, inputs: list[tuple[str, str]], outputs: list, steps: list) -> dict:
    return {
        "objectPath": hub,
        "functionName": name,
        "version": "1",
        "descriptor": {
            "inputSchema": {"name": "in", "fields": [{"name": n, "type": t} for n, t in inputs]},
            "outputSchema": {"name": "out", "fields": outputs},
        },
        "source": {"type": "script", "body": script(steps)},
    }


def one(var: str, sql: str, params: list[str] | None = None) -> dict:
    step = {"type": "selectOne", "var": var, "sql": sql}
    if params is not None:
        step["params"] = params
    return step


def many(var: str, sql: str, params: list[str] | None = None) -> dict:
    step = {"type": "selectMany", "var": var, "sql": sql}
    if params is not None:
        step["params"] = params
    return step


def sql_exec(sql: str, params: list[str] | None = None) -> dict:
    step = {"type": "exec", "sql": sql}
    if params is not None:
        step["params"] = params
    return step


def build_functions(hub: str) -> list[dict]:
    asset_cols = [
        ("assetId", "STRING"), ("displayName", "STRING"), ("freshness", "STRING"),
        ("status", "STRING"), ("sourceName", "STRING"),
    ]
    res_cols = [
        ("resourceId", "STRING"), ("resourceType", "STRING"), ("qty", "STRING"),
        ("reservedQty", "STRING"), ("freeQty", "STRING"), ("dataStatus", "STRING"),
        ("qualityStatus", "STRING"), ("freshness", "STRING"),
    ]
    pub_cols = [
        ("pubId", "STRING"), ("skuCode", "STRING"), ("plannedQty", "STRING"),
        ("status", "STRING"), ("shopStatus", "STRING"), ("snapshotId", "STRING"),
    ]
    cand_cols = [
        ("candidateId", "STRING"), ("skuCode", "STRING"), ("plannedQty", "STRING"),
        ("qualityStatus", "STRING"), ("validationStatus", "STRING"),
    ]
    dev_cols = [("deviationId", "STRING"), ("severity", "STRING"), ("reasonCode", "STRING"), ("delayMinutes", "STRING"), ("status", "STRING")]
    tail_cols = [("tailId", "STRING"), ("tailQty", "STRING"), ("reasonCode", "STRING"), ("periodCode", "STRING"), ("status", "STRING")]
    alert_cols = [("alertId", "STRING"), ("severity", "STRING"), ("alertType", "STRING"), ("status", "STRING"), ("ownerRole", "STRING"), ("messageText", "STRING")]
    del_cols = [("recipientCode", "STRING"), ("status", "STRING"), ("errorText", "STRING")]

    return [
        fn(hub, "mes_plan_getDataReadiness", [("siteCode", "STRING")],
           ok(("status", "STRING"), ("staleCount", "STRING"), ("assetCount", "STRING")), [
            one("row", "SELECT CAST(COUNT(*) AS VARCHAR(16)) AS asset_count, CAST(COALESCE(SUM(CASE WHEN freshness <> 'fresh' THEN 1 ELSE 0 END), 0) AS VARCHAR(16)) AS stale_count FROM mes_exec_asset WHERE site_code = ?", ["${input.siteCode}"]),
            {"type": "setVar", "var": "status", "value": "ready"},
            {"type": "if", "var": "row.asset_count", "equals": "0", "then": [
                {"type": "setVar", "var": "status", "value": "empty"},
            ]},
            {"type": "if", "var": "row.stale_count", "notEquals": "0", "then": [
                {"type": "if", "var": "row.asset_count", "notEquals": "0", "then": [
                    {"type": "setVar", "var": "status", "value": "partial"},
                ]},
            ]},
            ret({"error_code": "OK", "error_message": "", "status": "${status}", "staleCount": "${row.stale_count}", "assetCount": "${row.asset_count}"}),
        ]),
        fn(hub, "mes_plan_listAssets", [("siteCode", "STRING")], ok() + [rows_field("asset_row", asset_cols)], [
            many("assets", "SELECT asset_id, display_name, freshness, status, source_name FROM mes_exec_asset WHERE site_code = ? ORDER BY asset_id", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${assets}", "fields": {
                "assetId": "${item.asset_id}", "displayName": "${item.display_name}", "freshness": "${item.freshness}",
                "status": "${item.status}", "sourceName": "${item.source_name}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_listResources", [("siteCode", "STRING")], ok() + [rows_field("resource_row", res_cols)], [
            many("resources", "SELECT resource_id, resource_type, CAST(qty AS VARCHAR(32)) AS qty, CAST(reserved_qty AS VARCHAR(32)) AS reserved_qty, CAST(qty - reserved_qty AS VARCHAR(32)) AS free_qty, data_status, quality_status, freshness FROM mes_exec_resource WHERE site_code = ? ORDER BY resource_id", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${resources}", "fields": {
                "resourceId": "${item.resource_id}", "resourceType": "${item.resource_type}", "qty": "${item.qty}",
                "reservedQty": "${item.reserved_qty}", "freeQty": "${item.free_qty}", "dataStatus": "${item.data_status}",
                "qualityStatus": "${item.quality_status}", "freshness": "${item.freshness}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_confirmResource", [("resourceId", "STRING"), ("actor", "STRING"), ("reason", "STRING")],
           ok(("resourceId", "STRING"), ("dataStatus", "STRING")), [
            sql_exec("UPDATE mes_exec_resource SET data_status = 'confirmed', freshness = 'fresh', owner_role = ?, comment_text = ? WHERE resource_id = ?", ["${input.actor}", "${input.reason}", "${input.resourceId}"]),
            one("res", "SELECT resource_id, data_status FROM mes_exec_resource WHERE resource_id = ?", ["${input.resourceId}"]),
            {"type": "failIfNull", "var": "res", "message": "Resource not found"},
            ret({"error_code": "OK", "error_message": "", "resourceId": "${res.resource_id}", "dataStatus": "${res.data_status}"}),
        ]),
        fn(hub, "mes_plan_reserveSoft",
           [("reserveId", "STRING"), ("resourceId", "STRING"), ("pubId", "STRING"), ("qty", "STRING")],
           ok(("reserveId", "STRING"), ("status", "STRING")), [
            one("existing", "SELECT reserve_id FROM mes_exec_reserve WHERE reserve_id = ?", ["${input.reserveId}"]),
            {"type": "if", "var": "existing.reserve_id", "notNull": True, "then": [
                ret({"error_code": "OK", "error_message": "", "reserveId": "${input.reserveId}", "status": "already_reserved"}),
            ]},
            one("gate", "SELECT quality_status, data_status, CASE WHEN (qty - reserved_qty) >= ? THEN 'yes' ELSE 'no' END AS enough FROM mes_exec_resource WHERE resource_id = ?", ["${input.qty}", "${input.resourceId}"]),
            {"type": "failIfNull", "var": "gate", "message": "Resource not found"},
            {"type": "if", "var": "gate.quality_status", "equals": "blocked", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Quality block forbids reservation", "reserveId": "", "status": "blocked"}),
            ]},
            {"type": "if", "var": "gate.data_status", "notEquals": "confirmed", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Resource is not confirmed", "reserveId": "", "status": "unconfirmed"}),
            ]},
            {"type": "if", "var": "gate.enough", "equals": "no", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Not enough free quantity", "reserveId": "", "status": "short"}),
            ]},
            sql_exec("UPDATE mes_exec_resource SET reserved_qty = reserved_qty + ? WHERE resource_id = ?", ["${input.qty}", "${input.resourceId}"]),
            sql_exec("INSERT INTO mes_exec_reserve (reserve_id, resource_id, pub_id, qty) VALUES (?, ?, ?, ?)", ["${input.reserveId}", "${input.resourceId}", "${input.pubId}", "${input.qty}"]),
            ret({"error_code": "OK", "error_message": "", "reserveId": "${input.reserveId}", "status": "reserved"}),
        ]),
        fn(hub, "mes_plan_releaseReserve", [("reserveId", "STRING")], ok(("status", "STRING")), [
            one("hold", "SELECT reserve_id, resource_id, CAST(qty AS VARCHAR(32)) AS qty FROM mes_exec_reserve WHERE reserve_id = ?", ["${input.reserveId}"]),
            {"type": "if", "var": "hold", "notNull": False, "then": [
                ret({"error_code": "OK", "error_message": "", "status": "already_released"}),
            ]},
            sql_exec("UPDATE mes_exec_resource SET reserved_qty = reserved_qty - ? WHERE resource_id = ?", ["${hold.qty}", "${hold.resource_id}"]),
            sql_exec("DELETE FROM mes_exec_reserve WHERE reserve_id = ?", ["${input.reserveId}"]),
            ret({"error_code": "OK", "error_message": "", "status": "released"}),
        ]),
        fn(hub, "mes_plan_ingestCandidate",
           [("candidateId", "STRING"), ("siteCode", "STRING"), ("skuCode", "STRING"), ("plannedQty", "STRING"), ("uom", "STRING"), ("qualityStatus", "STRING"), ("idempotencyKey", "STRING")],
           ok(("candidateId", "STRING"), ("validationStatus", "STRING"), ("idempotencyKey", "STRING")), [
            sql_exec("INSERT INTO mes_exec_inbox (message_id, message_type, idempotency_key, payload_json, status) SELECT ?, 'IF-04', ?, ?, 'received' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_inbox WHERE idempotency_key = ?)",
                ["${input.candidateId}", "${input.idempotencyKey}", "${input.candidateId}", "${input.idempotencyKey}"]),
            sql_exec("INSERT INTO mes_exec_candidate (candidate_id, site_code, sku_code, planned_qty, uom, quality_status, validation_status, validation_message, idempotency_key) SELECT ?, ?, ?, ?, ?, ?, 'received', '', ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_candidate WHERE idempotency_key = ?)",
                ["${input.candidateId}", "${input.siteCode}", "${input.skuCode}", "${input.plannedQty}", "${input.uom}", "${input.qualityStatus}", "${input.idempotencyKey}", "${input.idempotencyKey}"]),
            one("row", "SELECT candidate_id, validation_status, idempotency_key FROM mes_exec_candidate WHERE idempotency_key = ?", ["${input.idempotencyKey}"]),
            {"type": "failIfNull", "var": "row", "message": "Candidate was not stored"},
            ret({"error_code": "OK", "error_message": "", "candidateId": "${row.candidate_id}", "validationStatus": "${row.validation_status}", "idempotencyKey": "${row.idempotency_key}"}),
        ]),
        fn(hub, "mes_plan_listCandidates", [("siteCode", "STRING")], ok() + [rows_field("candidate_row", cand_cols)], [
            many("cands", "SELECT candidate_id, sku_code, CAST(planned_qty AS VARCHAR(32)) AS planned_qty, quality_status, validation_status FROM mes_exec_candidate WHERE site_code = ? ORDER BY created_at", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${cands}", "fields": {
                "candidateId": "${item.candidate_id}", "skuCode": "${item.sku_code}", "plannedQty": "${item.planned_qty}",
                "qualityStatus": "${item.quality_status}", "validationStatus": "${item.validation_status}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_validate", [("candidateId", "STRING")], ok(("candidateId", "STRING"), ("validationStatus", "STRING")), [
            one("row", "SELECT c.candidate_id, CASE WHEN c.quality_status = 'blocked' OR COALESCE(s.quality_status, 'released') = 'blocked' THEN 'blocked' ELSE 'ok' END AS gate FROM mes_exec_candidate c LEFT JOIN mes_exec_sku s ON s.sku_code = c.sku_code WHERE c.candidate_id = ?", ["${input.candidateId}"]),
            {"type": "failIfNull", "var": "row", "message": "Candidate not found"},
            {"type": "if", "var": "row.gate", "equals": "blocked", "then": [
                sql_exec("UPDATE mes_exec_candidate SET validation_status = 'rejected', validation_message = 'Quality block forbids publication' WHERE candidate_id = ?", ["${input.candidateId}"]),
                ret({"error_code": "BLOCKED", "error_message": "Quality block forbids publication", "candidateId": "${input.candidateId}", "validationStatus": "rejected"}),
            ]},
            sql_exec("UPDATE mes_exec_candidate SET validation_status = 'ready', validation_message = '' WHERE candidate_id = ?", ["${input.candidateId}"]),
            ret({"error_code": "OK", "error_message": "", "candidateId": "${input.candidateId}", "validationStatus": "ready"}),
        ]),
        fn(hub, "mes_plan_publish", [("candidateId", "STRING"), ("actor", "STRING")],
           ok(("pubId", "STRING"), ("snapshotId", "STRING"), ("status", "STRING")), [
            one("existing", "SELECT pub_id, snapshot_id, status FROM mes_exec_pub WHERE candidate_id = ? AND status <> 'cancelled' AND status <> 'replaced'", ["${input.candidateId}"]),
            {"type": "if", "var": "existing.pub_id", "notNull": True, "then": [
                ret({"error_code": "OK", "error_message": "", "pubId": "${existing.pub_id}", "snapshotId": "${existing.snapshot_id}", "status": "${existing.status}"}),
            ]},
            one("cand", "SELECT c.candidate_id, c.site_code, c.sku_code, CAST(c.planned_qty AS VARCHAR(32)) AS planned_qty, c.uom, c.quality_status, c.validation_status, COALESCE(s.quality_status, 'released') AS sku_quality FROM mes_exec_candidate c LEFT JOIN mes_exec_sku s ON s.sku_code = c.sku_code WHERE c.candidate_id = ?", ["${input.candidateId}"]),
            {"type": "failIfNull", "var": "cand", "message": "Candidate not found"},
            {"type": "if", "var": "cand.validation_status", "notEquals": "ready", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Candidate is not ready to publish", "pubId": "", "snapshotId": "", "status": "rejected"}),
            ]},
            {"type": "if", "var": "cand.quality_status", "equals": "blocked", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Quality block forbids publication", "pubId": "", "snapshotId": "", "status": "rejected"}),
            ]},
            {"type": "if", "var": "cand.sku_quality", "equals": "blocked", "then": [
                ret({"error_code": "BLOCKED", "error_message": "SKU quality block forbids publication", "pubId": "", "snapshotId": "", "status": "rejected"}),
            ]},
            one("ids", "SELECT CONCAT('PUB-', ?) AS pub_id, CONCAT('SNAP-', ?) AS snapshot_id", ["${input.candidateId}", "${input.candidateId}"]),
            one("snap", "SELECT CONCAT('{\"candidateId\":\"', ?, '\",\"siteCode\":\"', ?, '\",\"skuCode\":\"', ?, '\",\"plannedQty\":\"', ?, '\",\"uom\":\"', ?, '\",\"qualityStatus\":\"', ?, '\"}') AS payload, CONCAT(?, '|', ?, '|', ?, '|', ?) AS payload_hash",
                ["${cand.candidate_id}", "${cand.site_code}", "${cand.sku_code}", "${cand.planned_qty}", "${cand.uom}", "${cand.quality_status}",
                 "${cand.candidate_id}", "${cand.sku_code}", "${cand.planned_qty}", "${cand.uom}"]),
            sql_exec("INSERT INTO mes_exec_snapshot (snapshot_id, pub_id, payload_json, payload_hash) SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_snapshot WHERE snapshot_id = ?)",
                ["${ids.snapshot_id}", "${ids.pub_id}", "${snap.payload}", "${snap.payload_hash}", "${ids.snapshot_id}"]),
            sql_exec("INSERT INTO mes_exec_pub (pub_id, pub_version, candidate_id, previous_pub_id, site_code, sku_code, planned_qty, uom, status, shop_status, snapshot_id, reason_text) SELECT ?, 1, ?, NULL, ?, ?, ?, ?, 'published', 'awaiting_shop', ?, ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_pub WHERE pub_id = ?)",
                ["${ids.pub_id}", "${cand.candidate_id}", "${cand.site_code}", "${cand.sku_code}", "${cand.planned_qty}", "${cand.uom}", "${ids.snapshot_id}", "${input.actor}", "${ids.pub_id}"]),
            sql_exec("INSERT INTO mes_exec_delivery (delivery_id, pub_id, recipient_code, status) SELECT CONCAT(?, ':SPP'), ?, 'SPP', 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_delivery WHERE pub_id = ? AND recipient_code = 'SPP')",
                ["${ids.pub_id}", "${ids.pub_id}", "${ids.pub_id}"]),
            sql_exec("INSERT INTO mes_exec_delivery (delivery_id, pub_id, recipient_code, status) SELECT CONCAT(?, ':SHOP'), ?, 'SHOP', 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_delivery WHERE pub_id = ? AND recipient_code = 'SHOP')",
                ["${ids.pub_id}", "${ids.pub_id}", "${ids.pub_id}"]),
            sql_exec("INSERT INTO mes_erp_outbox (id, entity_type, entity_id, payload_json, idempotency_key, status) SELECT RANDOM_UUID(), 'IF-05', ?, ?, CONCAT('IF-05:', ?), 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_erp_outbox WHERE idempotency_key = CONCAT('IF-05:', ?))",
                ["${ids.pub_id}", "${snap.payload}", "${ids.pub_id}", "${ids.pub_id}"]),
            ret({"error_code": "OK", "error_message": "", "pubId": "${ids.pub_id}", "snapshotId": "${ids.snapshot_id}", "status": "published"}),
        ]),
        fn(hub, "mes_plan_republish", [("pubId", "STRING"), ("reason", "STRING"), ("actor", "STRING")],
           ok(("pubId", "STRING"), ("previousPubId", "STRING"), ("status", "STRING"), ("snapshotId", "STRING")), [
            one("again", "SELECT pub_id, status, snapshot_id FROM mes_exec_pub WHERE previous_pub_id = ?", ["${input.pubId}"]),
            {"type": "if", "var": "again.pub_id", "notNull": True, "then": [
                ret({"error_code": "OK", "error_message": "", "pubId": "${again.pub_id}", "previousPubId": "${input.pubId}", "status": "${again.status}", "snapshotId": "${again.snapshot_id}"}),
            ]},
            one("old", "SELECT pub_id, candidate_id, site_code, sku_code, CAST(planned_qty AS VARCHAR(32)) AS planned_qty, uom, status FROM mes_exec_pub WHERE pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "old", "message": "Published plan not found"},
            {"type": "if", "var": "old.status", "equals": "cancelled", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Cancelled plan cannot be republished", "pubId": "", "previousPubId": "${input.pubId}", "status": "cancelled", "snapshotId": ""}),
            ]},
            one("sku", "SELECT COALESCE(quality_status, 'released') AS sku_quality FROM mes_exec_sku WHERE sku_code = ?", ["${old.sku_code}"]),
            {"type": "if", "var": "sku.sku_quality", "equals": "blocked", "then": [
                ret({"error_code": "BLOCKED", "error_message": "SKU quality block forbids republication", "pubId": "", "previousPubId": "${input.pubId}", "status": "rejected", "snapshotId": ""}),
            ]},
            one("ids", "SELECT CONCAT(?, '-R2') AS new_pub, CONCAT(CONCAT('SNAP-', ?), '-R2') AS new_snap", ["${input.pubId}", "${input.pubId}"]),
            one("snap", "SELECT CONCAT('{\"previousPubId\":\"', ?, '\",\"candidateId\":\"', ?, '\",\"siteCode\":\"', ?, '\",\"skuCode\":\"', ?, '\",\"plannedQty\":\"', ?, '\",\"uom\":\"', ?, '\",\"reason\":\"', ?, '\"}') AS payload, CONCAT(?, '|', ?, '|R2') AS payload_hash",
                ["${old.pub_id}", "${old.candidate_id}", "${old.site_code}", "${old.sku_code}", "${old.planned_qty}", "${old.uom}", "${input.reason}",
                 "${old.pub_id}", "${old.planned_qty}"]),
            sql_exec("INSERT INTO mes_exec_snapshot (snapshot_id, pub_id, payload_json, payload_hash) SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_snapshot WHERE snapshot_id = ?)",
                ["${ids.new_snap}", "${ids.new_pub}", "${snap.payload}", "${snap.payload_hash}", "${ids.new_snap}"]),
            sql_exec("INSERT INTO mes_exec_pub (pub_id, pub_version, candidate_id, previous_pub_id, site_code, sku_code, planned_qty, uom, status, shop_status, snapshot_id, reason_text) VALUES (?, 2, ?, ?, ?, ?, ?, ?, 'published', 'awaiting_shop', ?, ?)",
                ["${ids.new_pub}", "${old.candidate_id}", "${old.pub_id}", "${old.site_code}", "${old.sku_code}", "${old.planned_qty}", "${old.uom}", "${ids.new_snap}", "${input.reason}"]),
            sql_exec("UPDATE mes_exec_pub SET status = 'replaced' WHERE pub_id = ?", ["${input.pubId}"]),
            sql_exec("INSERT INTO mes_exec_delivery (delivery_id, pub_id, recipient_code, status) SELECT CONCAT(?, ':SPP'), ?, 'SPP', 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_delivery WHERE pub_id = ? AND recipient_code = 'SPP')",
                ["${ids.new_pub}", "${ids.new_pub}", "${ids.new_pub}"]),
            sql_exec("INSERT INTO mes_exec_delivery (delivery_id, pub_id, recipient_code, status) SELECT CONCAT(?, ':SHOP'), ?, 'SHOP', 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_delivery WHERE pub_id = ? AND recipient_code = 'SHOP')",
                ["${ids.new_pub}", "${ids.new_pub}", "${ids.new_pub}"]),
            sql_exec("INSERT INTO mes_erp_outbox (id, entity_type, entity_id, payload_json, idempotency_key, status) SELECT RANDOM_UUID(), 'IF-05', ?, ?, CONCAT('IF-05:', ?), 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_erp_outbox WHERE idempotency_key = CONCAT('IF-05:', ?))",
                ["${ids.new_pub}", "${snap.payload}", "${ids.new_pub}", "${ids.new_pub}"]),
            ret({"error_code": "OK", "error_message": "", "pubId": "${ids.new_pub}", "previousPubId": "${input.pubId}", "status": "published", "snapshotId": "${ids.new_snap}"}),
        ]),
        fn(hub, "mes_plan_cancel", [("pubId", "STRING"), ("reason", "STRING")], ok(("pubId", "STRING"), ("status", "STRING")), [
            sql_exec("UPDATE mes_exec_pub SET status = 'cancelled', reason_text = ? WHERE pub_id = ?", ["${input.reason}", "${input.pubId}"]),
            many("holds", "SELECT resource_id, CAST(qty AS VARCHAR(32)) AS qty FROM mes_exec_reserve WHERE pub_id = ?", ["${input.pubId}"]),
            {"type": "for_each_row", "source": "${holds}", "rowVar": "hold", "steps": [
                sql_exec("UPDATE mes_exec_resource SET reserved_qty = reserved_qty - ? WHERE resource_id = ?", ["${hold.qty}", "${hold.resource_id}"]),
            ]},
            sql_exec("DELETE FROM mes_exec_reserve WHERE pub_id = ?", ["${input.pubId}"]),
            ret({"error_code": "OK", "error_message": "", "pubId": "${input.pubId}", "status": "cancelled"}),
        ]),
        fn(hub, "mes_plan_acceptPub", [("pubId", "STRING"), ("actor", "STRING")],
           ok(("pubId", "STRING"), ("shopStatus", "STRING")), [
            one("row", "SELECT pub_id, status, shop_status FROM mes_exec_pub WHERE pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "row", "message": "Published plan not found"},
            {"type": "if", "var": "row.status", "notEquals": "published", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Only a published plan can be accepted", "pubId": "${row.pub_id}", "shopStatus": "${row.shop_status}"}),
            ]},
            sql_exec("UPDATE mes_exec_pub SET shop_status = 'accepted' WHERE pub_id = ? AND status = 'published'", ["${input.pubId}"]),
            one("after", "SELECT pub_id, shop_status FROM mes_exec_pub WHERE pub_id = ?", ["${input.pubId}"]),
            ret({"error_code": "OK", "error_message": "", "pubId": "${after.pub_id}", "shopStatus": "${after.shop_status}"}),
        ]),
        fn(hub, "mes_plan_rejectPub", [("pubId", "STRING"), ("reason", "STRING")],
           ok(("pubId", "STRING"), ("shopStatus", "STRING")), [
            {"type": "if", "var": "input.reason", "equals": "", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Rejection requires a reason", "pubId": "${input.pubId}", "shopStatus": ""}),
            ]},
            one("row", "SELECT pub_id, status, shop_status FROM mes_exec_pub WHERE pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "row", "message": "Published plan not found"},
            {"type": "if", "var": "row.status", "notEquals": "published", "then": [
                ret({"error_code": "BLOCKED", "error_message": "Only a published plan can be rejected", "pubId": "${row.pub_id}", "shopStatus": "${row.shop_status}"}),
            ]},
            sql_exec("UPDATE mes_exec_pub SET shop_status = 'rejected', reason_text = ? WHERE pub_id = ? AND status = 'published'", ["${input.reason}", "${input.pubId}"]),
            one("after", "SELECT pub_id, shop_status FROM mes_exec_pub WHERE pub_id = ?", ["${input.pubId}"]),
            ret({"error_code": "OK", "error_message": "", "pubId": "${after.pub_id}", "shopStatus": "${after.shop_status}"}),
        ]),
        fn(hub, "mes_plan_listPubs", [("siteCode", "STRING")], ok() + [rows_field("pub_row", pub_cols)], [
            many("pubs", "SELECT pub_id, sku_code, CAST(planned_qty AS VARCHAR(32)) AS planned_qty, status, shop_status, snapshot_id FROM mes_exec_pub WHERE site_code = ? AND status <> 'cancelled' ORDER BY published_at", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${pubs}", "fields": {
                "pubId": "${item.pub_id}", "skuCode": "${item.sku_code}", "plannedQty": "${item.planned_qty}",
                "status": "${item.status}", "shopStatus": "${item.shop_status}", "snapshotId": "${item.snapshot_id}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_getPub", [("pubId", "STRING")],
           ok(("pubId", "STRING"), ("status", "STRING"), ("shopStatus", "STRING"), ("snapshotId", "STRING"), ("plannedQty", "STRING"), ("payloadJson", "STRING")), [
            one("row", "SELECT p.pub_id, p.status, p.shop_status, p.snapshot_id, CAST(p.planned_qty AS VARCHAR(32)) AS planned_qty, COALESCE(s.payload_json, '') AS payload_json FROM mes_exec_pub p LEFT JOIN mes_exec_snapshot s ON s.snapshot_id = p.snapshot_id WHERE p.pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "row", "message": "Published plan not found"},
            ret({"error_code": "OK", "error_message": "", "pubId": "${row.pub_id}", "status": "${row.status}", "shopStatus": "${row.shop_status}", "snapshotId": "${row.snapshot_id}", "plannedQty": "${row.planned_qty}", "payloadJson": "${row.payload_json}"}),
        ]),
        fn(hub, "mes_plan_recordFact",
           [("factId", "STRING"), ("pubId", "STRING"), ("actualQty", "STRING"), ("delayMinutes", "STRING"), ("reasonCode", "STRING")],
           ok(("factId", "STRING"), ("deviationId", "STRING")), [
            one("pub", "SELECT pub_id, sku_code FROM mes_exec_pub WHERE pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "pub", "message": "Published plan not found"},
            sql_exec("INSERT INTO mes_exec_fact (fact_id, pub_id, sku_code, actual_qty, delay_minutes, reason_code) SELECT ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_fact WHERE fact_id = ?)",
                ["${input.factId}", "${input.pubId}", "${pub.sku_code}", "${input.actualQty}", "${input.delayMinutes}", "${input.reasonCode}", "${input.factId}"]),
            one("late", "SELECT CASE WHEN ? > 30 THEN 'yes' ELSE 'no' END AS flag", ["${input.delayMinutes}"]),
            {"type": "setVar", "var": "deviationId", "value": ""},
            {"type": "if", "var": "late.flag", "equals": "yes", "then": [
                sql_exec("INSERT INTO mes_exec_deviation (deviation_id, pub_id, severity, reason_code, delay_minutes, status) SELECT CONCAT(?, '-D'), ?, 'Warning', ?, ?, 'open' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_deviation WHERE deviation_id = CONCAT(?, '-D'))",
                    ["${input.factId}", "${input.pubId}", "${input.reasonCode}", "${input.delayMinutes}", "${input.factId}"]),
                one("dev", "SELECT CONCAT(?, '-D') AS deviation_id", ["${input.factId}"]),
                {"type": "setVar", "var": "deviationId", "value": "${dev.deviation_id}"},
            ]},
            ret({"error_code": "OK", "error_message": "", "factId": "${input.factId}", "deviationId": "${deviationId}"}),
        ]),
        fn(hub, "mes_plan_planFact", [("pubId", "STRING")],
           ok(("plannedQty", "STRING"), ("actualQty", "STRING"), ("deltaQty", "STRING"), ("basis", "STRING")), [
            one("row", "SELECT CAST(p.planned_qty AS VARCHAR(32)) AS planned_qty, CAST(COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) AS VARCHAR(32)) AS actual_qty, CAST(p.planned_qty - COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) AS VARCHAR(32)) AS delta_qty, CASE WHEN p.planned_qty = 0 THEN 'no_plan' ELSE 'comparable' END AS basis FROM mes_exec_pub p WHERE p.pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "row", "message": "Published plan not found"},
            ret({"error_code": "OK", "error_message": "", "plannedQty": "${row.planned_qty}", "actualQty": "${row.actual_qty}", "deltaQty": "${row.delta_qty}", "basis": "${row.basis}"}),
        ]),
        fn(hub, "mes_plan_listDeviations", [("pubId", "STRING")], ok() + [rows_field("deviation_row", dev_cols)], [
            many("devs", "SELECT deviation_id, severity, reason_code, CAST(delay_minutes AS VARCHAR(16)) AS delay_minutes, status FROM mes_exec_deviation WHERE pub_id = ? ORDER BY deviation_id", ["${input.pubId}"]),
            {"type": "map", "var": "rows", "source": "${devs}", "fields": {
                "deviationId": "${item.deviation_id}", "severity": "${item.severity}", "reasonCode": "${item.reason_code}",
                "delayMinutes": "${item.delay_minutes}", "status": "${item.status}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_forecastLite", [("pubId", "STRING")],
           ok(("remainingQty", "STRING"), ("risk", "STRING")), [
            one("row", "SELECT CAST(p.planned_qty - COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) AS VARCHAR(32)) AS remaining_qty, CASE WHEN COALESCE((SELECT MAX(f.delay_minutes) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) > 30 THEN 'at_risk' ELSE 'on_plan' END AS risk FROM mes_exec_pub p WHERE p.pub_id = ?", ["${input.pubId}"]),
            {"type": "failIfNull", "var": "row", "message": "Published plan not found"},
            ret({"error_code": "OK", "error_message": "", "remainingQty": "${row.remaining_qty}", "risk": "${row.risk}"}),
        ]),
        fn(hub, "mes_plan_closePeriod",
           [("pubId", "STRING"), ("periodCode", "STRING"), ("toleranceQty", "STRING"), ("reasonCode", "STRING")],
           ok(("tailId", "STRING"), ("tailQty", "STRING"), ("status", "STRING")), [
            one("closed", "SELECT close_id, status FROM mes_exec_period_close WHERE pub_id = ? AND period_code = ?", ["${input.pubId}", "${input.periodCode}"]),
            {"type": "if", "var": "closed.close_id", "notNull": True, "then": [
                one("prior", "SELECT tail_id, CAST(tail_qty AS VARCHAR(32)) AS tail_qty FROM mes_exec_tail WHERE idempotency_key = CONCAT(?, CONCAT(':', ?))", ["${input.pubId}", "${input.periodCode}"]),
                {"type": "setVar", "var": "tailId", "value": ""},
                {"type": "setVar", "var": "tailQty", "value": "0"},
                {"type": "if", "var": "prior.tail_id", "notNull": True, "then": [
                    {"type": "setVar", "var": "tailId", "value": "${prior.tail_id}"},
                    {"type": "setVar", "var": "tailQty", "value": "${prior.tail_qty}"},
                ]},
                ret({"error_code": "OK", "error_message": "", "tailId": "${tailId}", "tailQty": "${tailQty}", "status": "${closed.status}"}),
            ]},
            one("gap", "SELECT p.sku_code, p.uom, CAST(p.planned_qty - COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) AS VARCHAR(32)) AS tail_qty, CASE WHEN (p.planned_qty - COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0)) > ? THEN 'yes' ELSE 'no' END AS over FROM mes_exec_pub p WHERE p.pub_id = ?", ["${input.toleranceQty}", "${input.pubId}"]),
            {"type": "failIfNull", "var": "gap", "message": "Published plan not found"},
            one("ids", "SELECT CONCAT(?, CONCAT(':', ?)) AS tail_key, CONCAT('CLOSE-', CONCAT(?, CONCAT('-', ?))) AS close_id", ["${input.pubId}", "${input.periodCode}", "${input.pubId}", "${input.periodCode}"]),
            {"type": "setVar", "var": "tailId", "value": ""},
            {"type": "if", "var": "gap.over", "equals": "yes", "then": [
                sql_exec("INSERT INTO mes_exec_tail (tail_id, pub_id, sku_code, tail_qty, uom, reason_code, period_code, idempotency_key, status) SELECT ?, ?, ?, ?, ?, ?, ?, ?, 'open' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_tail WHERE idempotency_key = ?)",
                    ["${ids.tail_key}", "${input.pubId}", "${gap.sku_code}", "${gap.tail_qty}", "${gap.uom}", "${input.reasonCode}", "${input.periodCode}", "${ids.tail_key}", "${ids.tail_key}"]),
                {"type": "setVar", "var": "tailId", "value": "${ids.tail_key}"},
            ]},
            sql_exec("INSERT INTO mes_exec_period_close (close_id, pub_id, period_code, status) SELECT ?, ?, ?, 'closed' WHERE NOT EXISTS (SELECT 1 FROM mes_exec_period_close WHERE pub_id = ? AND period_code = ?)",
                ["${ids.close_id}", "${input.pubId}", "${input.periodCode}", "${input.pubId}", "${input.periodCode}"]),
            ret({"error_code": "OK", "error_message": "", "tailId": "${tailId}", "tailQty": "${gap.tail_qty}", "status": "closed"}),
        ]),
        fn(hub, "mes_plan_listTails", [("pubId", "STRING")], ok() + [rows_field("tail_row", tail_cols)], [
            many("tails", "SELECT tail_id, CAST(tail_qty AS VARCHAR(32)) AS tail_qty, reason_code, period_code, status FROM mes_exec_tail WHERE pub_id = ? ORDER BY tail_id", ["${input.pubId}"]),
            {"type": "map", "var": "rows", "source": "${tails}", "fields": {
                "tailId": "${item.tail_id}", "tailQty": "${item.tail_qty}", "reasonCode": "${item.reason_code}",
                "periodCode": "${item.period_code}", "status": "${item.status}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_openAlert",
           [("alertId", "STRING"), ("pubId", "STRING"), ("siteCode", "STRING"), ("severity", "STRING"), ("alertType", "STRING"), ("message", "STRING")],
           ok(("alertId", "STRING"), ("status", "STRING")), [
            sql_exec("INSERT INTO mes_exec_alert (alert_id, pub_id, site_code, severity, alert_type, status, owner_role, message_text) SELECT ?, ?, ?, ?, ?, 'open', '', ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_alert WHERE alert_id = ?)",
                ["${input.alertId}", "${input.pubId}", "${input.siteCode}", "${input.severity}", "${input.alertType}", "${input.message}", "${input.alertId}"]),
            ret({"error_code": "OK", "error_message": "", "alertId": "${input.alertId}", "status": "open"}),
        ]),
        fn(hub, "mes_plan_assignAlert", [("alertId", "STRING"), ("ownerRole", "STRING")], ok(("status", "STRING")), [
            sql_exec("UPDATE mes_exec_alert SET owner_role = ?, status = 'in_progress' WHERE alert_id = ?", ["${input.ownerRole}", "${input.alertId}"]),
            ret({"error_code": "OK", "error_message": "", "status": "in_progress"}),
        ]),
        fn(hub, "mes_plan_confirmRisk", [("alertId", "STRING")], ok(("status", "STRING")), [
            sql_exec("UPDATE mes_exec_alert SET status = 'risk_confirmed' WHERE alert_id = ?", ["${input.alertId}"]),
            ret({"error_code": "OK", "error_message": "", "status": "risk_confirmed"}),
        ]),
        fn(hub, "mes_plan_closeAlert", [("alertId", "STRING")], ok(("status", "STRING")), [
            sql_exec("UPDATE mes_exec_alert SET status = 'closed' WHERE alert_id = ?", ["${input.alertId}"]),
            ret({"error_code": "OK", "error_message": "", "status": "closed"}),
        ]),
        fn(hub, "mes_plan_listAlerts", [("siteCode", "STRING")], ok() + [rows_field("alert_row", alert_cols)], [
            many("alerts", "SELECT alert_id, severity, alert_type, status, owner_role, message_text FROM mes_exec_alert WHERE site_code = ? ORDER BY alert_id", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${alerts}", "fields": {
                "alertId": "${item.alert_id}", "severity": "${item.severity}", "alertType": "${item.alert_type}",
                "status": "${item.status}", "ownerRole": "${item.owner_role}", "messageText": "${item.message_text}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_requestReplan",
           [("alertId", "STRING"), ("pubId", "STRING"), ("siteCode", "STRING"), ("reason", "STRING")],
           ok(("alertId", "STRING"), ("idempotencyKey", "STRING")), [
            sql_exec("INSERT INTO mes_exec_alert (alert_id, pub_id, site_code, severity, alert_type, status, owner_role, message_text) SELECT ?, ?, ?, 'Critical', 'replan', 'open', 'planner', ? WHERE NOT EXISTS (SELECT 1 FROM mes_exec_alert WHERE alert_id = ?)",
                ["${input.alertId}", "${input.pubId}", "${input.siteCode}", "${input.reason}", "${input.alertId}"]),
            sql_exec("INSERT INTO mes_erp_outbox (id, entity_type, entity_id, payload_json, idempotency_key, status) SELECT RANDOM_UUID(), 'IF-07', ?, ?, CONCAT('IF-07:', ?), 'pending' WHERE NOT EXISTS (SELECT 1 FROM mes_erp_outbox WHERE idempotency_key = CONCAT('IF-07:', ?))",
                ["${input.pubId}", "${input.reason}", "${input.alertId}", "${input.alertId}"]),
            one("key", "SELECT CONCAT('IF-07:', ?) AS idempotency_key", ["${input.alertId}"]),
            ret({"error_code": "OK", "error_message": "", "alertId": "${input.alertId}", "idempotencyKey": "${key.idempotency_key}"}),
        ]),
        fn(hub, "mes_plan_ackInbox", [("idempotencyKey", "STRING")], ok(("status", "STRING")), [
            sql_exec("UPDATE mes_exec_inbox SET status = 'acked' WHERE idempotency_key = ?", ["${input.idempotencyKey}"]),
            ret({"error_code": "OK", "error_message": "", "status": "acked"}),
        ]),
        fn(hub, "mes_plan_listDeliveries", [("pubId", "STRING")], ok() + [rows_field("delivery_row", del_cols)], [
            many("dels", "SELECT recipient_code, status, error_text FROM mes_exec_delivery WHERE pub_id = ? ORDER BY recipient_code", ["${input.pubId}"]),
            {"type": "map", "var": "rows", "source": "${dels}", "fields": {
                "recipientCode": "${item.recipient_code}", "status": "${item.status}", "errorText": "${item.error_text}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_listDeliveriesBySite", [("siteCode", "STRING")], ok() + [rows_field("delivery_site_row", [
            ("pubId", "STRING"), ("recipientCode", "STRING"), ("status", "STRING"), ("errorText", "STRING"),
        ])], [
            many("dels", "SELECT d.pub_id, d.recipient_code, d.status, d.error_text FROM mes_exec_delivery d JOIN mes_exec_pub p ON p.pub_id = d.pub_id WHERE p.site_code = ? ORDER BY d.pub_id, d.recipient_code", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${dels}", "fields": {
                "pubId": "${item.pub_id}", "recipientCode": "${item.recipient_code}", "status": "${item.status}", "errorText": "${item.error_text}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_listTailsBySite", [("siteCode", "STRING")], ok() + [rows_field("tail_site_row", [
            ("pubId", "STRING"), ("tailId", "STRING"), ("tailQty", "STRING"), ("reasonCode", "STRING"), ("periodCode", "STRING"), ("status", "STRING"),
        ])], [
            many("tails", "SELECT t.pub_id, t.tail_id, CAST(t.tail_qty AS VARCHAR(32)) AS tail_qty, t.reason_code, t.period_code, t.status FROM mes_exec_tail t JOIN mes_exec_pub p ON p.pub_id = t.pub_id WHERE p.site_code = ? ORDER BY t.tail_id", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${tails}", "fields": {
                "pubId": "${item.pub_id}", "tailId": "${item.tail_id}", "tailQty": "${item.tail_qty}",
                "reasonCode": "${item.reason_code}", "periodCode": "${item.period_code}", "status": "${item.status}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_listDeviationsBySite", [("siteCode", "STRING")], ok() + [rows_field("deviation_site_row", [
            ("pubId", "STRING"), ("deviationId", "STRING"), ("severity", "STRING"), ("reasonCode", "STRING"), ("delayMinutes", "STRING"), ("status", "STRING"),
        ])], [
            many("devs", "SELECT d.pub_id, d.deviation_id, d.severity, d.reason_code, CAST(d.delay_minutes AS VARCHAR(16)) AS delay_minutes, d.status FROM mes_exec_deviation d JOIN mes_exec_pub p ON p.pub_id = d.pub_id WHERE p.site_code = ? ORDER BY d.deviation_id", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${devs}", "fields": {
                "pubId": "${item.pub_id}", "deviationId": "${item.deviation_id}", "severity": "${item.severity}",
                "reasonCode": "${item.reason_code}", "delayMinutes": "${item.delay_minutes}", "status": "${item.status}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_planFactBySite", [("siteCode", "STRING")], ok() + [rows_field("plan_fact_row", [
            ("pubId", "STRING"), ("skuCode", "STRING"), ("plannedQty", "STRING"), ("actualQty", "STRING"), ("deltaQty", "STRING"), ("risk", "STRING"),
        ])], [
            many("facts", """SELECT p.pub_id, p.sku_code,
                CAST(p.planned_qty AS VARCHAR(32)) AS planned_qty,
                CAST(COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) AS VARCHAR(32)) AS actual_qty,
                CAST(p.planned_qty - COALESCE((SELECT SUM(f.actual_qty) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) AS VARCHAR(32)) AS delta_qty,
                CASE WHEN COALESCE((SELECT MAX(f.delay_minutes) FROM mes_exec_fact f WHERE f.pub_id = p.pub_id), 0) > 30 THEN 'at_risk' ELSE 'on_plan' END AS risk
                FROM mes_exec_pub p WHERE p.site_code = ? AND p.status <> 'cancelled' ORDER BY p.published_at""", ["${input.siteCode}"]),
            {"type": "map", "var": "rows", "source": "${facts}", "fields": {
                "pubId": "${item.pub_id}", "skuCode": "${item.sku_code}", "plannedQty": "${item.planned_qty}",
                "actualQty": "${item.actual_qty}", "deltaQty": "${item.delta_qty}", "risk": "${item.risk}",
            }},
            ret({"error_code": "OK", "error_message": "", "rows": "${rows}"}),
        ]),
        fn(hub, "mes_plan_setDelivery",
           [("pubId", "STRING"), ("recipientCode", "STRING"), ("status", "STRING"), ("errorText", "STRING")],
           ok(("status", "STRING")), [
            one("row", "SELECT delivery_id FROM mes_exec_delivery WHERE pub_id = ? AND recipient_code = ?",
                ["${input.pubId}", "${input.recipientCode}"]),
            {"type": "if", "var": "row.delivery_id", "notNull": False, "then": [
                ret({"error_code": "BLOCKED", "error_message": "Delivery row not found", "status": ""}),
            ]},
            sql_exec("UPDATE mes_exec_delivery SET status = ?, error_text = ?, updated_at = CURRENT_TIMESTAMP WHERE pub_id = ? AND recipient_code = ?",
                ["${input.status}", "${input.errorText}", "${input.pubId}", "${input.recipientCode}"]),
            one("after", "SELECT status FROM mes_exec_delivery WHERE pub_id = ? AND recipient_code = ?",
                ["${input.pubId}", "${input.recipientCode}"]),
            ret({"error_code": "OK", "error_message": "", "status": "${after.status}"}),
        ]),
    ]


def widget(widget_id: str, title: str, hub: str, function_name: str, x: int, y: int, w: int, h: int, button: str, input_map: dict | None = None) -> dict:
    item = {
        "id": widget_id,
        "type": "function",
        "title": title,
        "x": x,
        "y": y,
        "w": w,
        "h": h,
        "objectPath": hub,
        "functionName": function_name,
        "buttonLabel": button,
    }
    if input_map is not None:
        item["inputMapJson"] = json.dumps(input_map, separators=(",", ":"))
    return item


def dashboard(path: str, title: str, widgets: list[dict]) -> dict:
    return {
        "path": path,
        "title": title,
        "refreshIntervalMs": 5000,
        "layoutJson": json.dumps({"columns": 84, "rowHeight": 8, "widgets": widgets}, separators=(",", ":")),
    }


def build_dashboards(hub: str, dash_prefix: str = "mes-platform-") -> list[dict]:
    site = {"siteCode": "PLANT-A"}
    root = "root.platform.dashboards." + dash_prefix
    return [
        dashboard(root + "data", "MES Data readiness", [
            widget("data-ready", "Data readiness", hub, "mes_plan_getDataReadiness", 0, 0, 42, 21, "Check PLANT-A", site),
            widget("data-assets", "Assets", hub, "mes_plan_listAssets", 42, 0, 42, 21, "List assets", site),
        ]),
        dashboard(root + "resources", "MES Resources", [
            widget("res-list", "Resources", hub, "mes_plan_listResources", 0, 0, 84, 28, "List PLANT-A resources", site),
        ]),
        dashboard(root + "exec-alerts", "MES Execution alerts", [
            widget("alert-list", "Alerts", hub, "mes_plan_listAlerts", 0, 0, 84, 28, "List PLANT-A alerts", site),
        ]),
        dashboard(root + "publish", "MES Publish", [
            widget("cand-list", "Candidates", hub, "mes_plan_listCandidates", 0, 0, 42, 24, "List candidates", site),
            widget("pub-list", "Published plans", hub, "mes_plan_listPubs", 42, 0, 42, 24, "List published plans", site),
        ]),
        dashboard(root + "official", "MES Official plan", [
            widget("off-ready", "Data readiness", hub, "mes_plan_getDataReadiness", 0, 0, 28, 18, "Check PLANT-A", site),
            widget("off-pubs", "Official versions", hub, "mes_plan_listPubs", 28, 0, 56, 18, "List official plans", site),
            widget("off-dels", "Recipient delivery", hub, "mes_plan_listDeliveriesBySite", 0, 18, 84, 18, "List deliveries", site),
        ]),
        dashboard(root + "monitor", "MES Monitor", [
            widget("mon-fact", "Plan / fact", hub, "mes_plan_planFactBySite", 0, 0, 84, 18, "Plan against fact", site),
            widget("mon-tails", "Tails", hub, "mes_plan_listTailsBySite", 0, 18, 42, 18, "List tails", site),
            widget("mon-devs", "Deviations", hub, "mes_plan_listDeviationsBySite", 42, 18, 42, 18, "List deviations", site),
        ]),
        dashboard(root + "integration", "MES Integration status", [
            widget("pub-delivery", "Deliveries by site", hub, "mes_plan_listDeliveriesBySite", 0, 0, 84, 24, "List deliveries", site),
        ]),
    ]


def workflow(app_id: str, hub: str, wf_prefix: str = "mes-plan-") -> dict:
    process_id = wf_prefix.rstrip("-") + "-accept"
    path = "root.platform.workflows." + process_id
    bpmn = (
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:ispf=\"http://ispf.io/bpmn\" targetNamespace=\"http://ispf.io/bpmn\">"
        f"<process id=\"{process_id}\" name=\"MES plan shop acceptance\" isExecutable=\"true\">"
        "<startEvent id=\"start\" name=\"Published\"/>"
        "<userTask id=\"accept\" name=\"Shop accepts official plan\" ispf:title=\"Accept official plan\" "
        "ispf:instructions=\"Accept the published plan or reject it with a reason\" "
        "ispf:assigneeRole=\"operator\" ispf:targetObject=\"" + hub + "\" ispf:function=\"mes_plan_acceptPub\"/>"
        "<endEvent id=\"end\" name=\"End\"/>"
        "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"accept\"/>"
        "<sequenceFlow id=\"f2\" sourceRef=\"accept\" targetRef=\"end\"/>"
        "</process></definitions>"
    )
    return {
        "path": path,
        "bpmnXml": bpmn,
        "status": "ACTIVE",
        "operatorAppId": app_id,
    }


def close_workflow(app_id: str, hub: str, wf_prefix: str = "mes-plan-") -> dict:
    process_id = wf_prefix.rstrip("-") + "-close"
    path = "root.platform.workflows." + process_id
    bpmn = (
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:ispf=\"http://ispf.io/bpmn\" targetNamespace=\"http://ispf.io/bpmn\">"
        f"<process id=\"{process_id}\" name=\"MES period close\" isExecutable=\"true\">"
        "<startEvent id=\"start\" name=\"Close period\"/>"
        "<userTask id=\"close\" name=\"Close shift\" ispf:title=\"Close period\" "
        "ispf:instructions=\"Confirm the period close and the resulting tail\" "
        "ispf:assigneeRole=\"operator\" ispf:targetObject=\"" + hub + "\" ispf:function=\"mes_plan_closePeriod\"/>"
        "<endEvent id=\"end\" name=\"End\"/>"
        "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"close\"/>"
        "<sequenceFlow id=\"f2\" sourceRef=\"close\" targetRef=\"end\"/>"
        "</process></definitions>"
    )
    return {
        "path": path,
        "bpmnXml": bpmn,
        "status": "ACTIVE",
        "operatorAppId": app_id,
    }


def reject_workflow(app_id: str, hub: str, wf_prefix: str = "mes-plan-") -> dict:
    process_id = wf_prefix.rstrip("-") + "-reject"
    path = "root.platform.workflows." + process_id
    bpmn = (
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:ispf=\"http://ispf.io/bpmn\" targetNamespace=\"http://ispf.io/bpmn\">"
        f"<process id=\"{process_id}\" name=\"MES plan shop rejection\" isExecutable=\"true\">"
        "<startEvent id=\"start\" name=\"Published\"/>"
        "<userTask id=\"reject\" name=\"Shop rejects official plan\" ispf:title=\"Reject official plan\" "
        "ispf:instructions=\"Reject the published plan with a reason\" "
        "ispf:assigneeRole=\"operator\" ispf:targetObject=\"" + hub + "\" ispf:function=\"mes_plan_rejectPub\"/>"
        "<endEvent id=\"end\" name=\"End\"/>"
        "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"reject\"/>"
        "<sequenceFlow id=\"f2\" sourceRef=\"reject\" targetRef=\"end\"/>"
        "</process></definitions>"
    )
    return {
        "path": path,
        "bpmnXml": bpmn,
        "status": "ACTIVE",
        "operatorAppId": app_id,
    }


def apply(
    bundle: dict,
    hub: str,
    app_id: str,
    dash_prefix: str = "mes-platform-",
    wf_prefix: str = "mes-plan-",
    migration_id: str = MIGRATION_ID,
    seed_id: str = SEED_ID,
    drop_workflow_paths: list[str] | None = None,
) -> bool:
    changed = False
    root = "root.platform.dashboards." + dash_prefix
    migrations = bundle.setdefault("migrations", [])
    if not any(item.get("id") == migration_id for item in migrations):
        migrations.append({"id": migration_id, "sql": MIGRATION_SQL})
        changed = True
    seeds = bundle.setdefault("seeds", [])
    if not any(item.get("id") == seed_id for item in seeds):
        seeds.append({"id": seed_id, "profile": "default", "sql": SEED_SQL})
        changed = True
    functions = bundle.setdefault("functions", [])
    by_name = {
        item.get("functionName"): idx
        for idx, item in enumerate(functions)
        if isinstance(item, dict) and item.get("functionName")
    }
    for item in build_functions(hub):
        name = item["functionName"]
        if name in by_name:
            idx = by_name[name]
            if functions[idx] != item:
                functions[idx] = item
                changed = True
        else:
            functions.append(item)
            by_name[name] = len(functions) - 1
            changed = True
    dashboards = bundle.setdefault("dashboards", [])
    have_paths = {item.get("path") for item in dashboards}
    for item in build_dashboards(hub, dash_prefix):
        if item["path"] not in have_paths:
            dashboards.append(item)
            changed = True
        else:
            # Refresh Official / Monitor / Integration layouts when already present.
            for idx, existing in enumerate(dashboards):
                if existing.get("path") == item["path"] and existing.get("path", "").endswith(
                    ("-official", "-monitor", "-integration")
                ):
                    dashboards[idx] = item
                    changed = True
                    break
    workflows = bundle.setdefault("workflows", [])
    drop = set(drop_workflow_paths or [])
    if drop:
        kept = [item for item in workflows if item.get("path") not in drop]
        if len(kept) != len(workflows):
            bundle["workflows"] = kept
            workflows = kept
            changed = True
    have_wf = {item.get("path") for item in workflows}
    for item in (
        workflow(app_id, hub, wf_prefix),
        reject_workflow(app_id, hub, wf_prefix),
        close_workflow(app_id, hub, wf_prefix),
    ):
        if item["path"] not in have_wf:
            workflows.append(item)
            changed = True
        else:
            for idx, existing in enumerate(workflows):
                if existing.get("path") == item["path"]:
                    if existing.get("bpmnXml") != item["bpmnXml"]:
                        workflows[idx] = item
                        changed = True
                    break
    operator = bundle.setdefault("operatorUi", {})
    tabs = operator.setdefault("dashboards", [])
    have_tabs = {item.get("path") for item in tabs}
    for path, title in (
        (root + "data", "Data"),
        (root + "resources", "Resources"),
        (root + "exec-alerts", "Exec alerts"),
        (root + "publish", "Publish"),
        (root + "official", "Official plan"),
        (root + "monitor", "Monitor"),
        (root + "integration", "Integration"),
    ):
        if path not in have_tabs:
            tabs.append({"path": path, "title": title})
            changed = True
    if operator.pop("uiPack", None) is not None:
        changed = True
    meta = bundle.setdefault("metadata", {})
    note = "plan execution: candidate, official plan, resources, fact, tails, alerts"
    changelog = meta.get("changelog") or ""
    if note not in changelog:
        meta["changelog"] = (changelog + "; " + note).strip("; ")
        changed = True
    am_note = "AM harden: frozen snapshot, SKU gate on publish, republish deliveries, accept/reject guards"
    if am_note not in (meta.get("changelog") or ""):
        meta["changelog"] = ((meta.get("changelog") or "") + "; " + am_note).strip("; ")
        changed = True
    return changed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("bundle")
    parser.add_argument("--hub", required=True)
    parser.add_argument("--app", required=True)
    parser.add_argument("--dash-prefix", default="mes-platform-")
    parser.add_argument("--wf-prefix", default="mes-plan-")
    parser.add_argument("--migration-id", default=MIGRATION_ID)
    parser.add_argument("--seed-id", default=SEED_ID)
    parser.add_argument(
        "--drop-workflow",
        action="append",
        default=[],
        help="Workflow object path to remove (repeatable); use for co-install collisions",
    )
    args = parser.parse_args()
    path = Path(args.bundle)
    bundle = json.loads(path.read_text(encoding="utf-8"))
    changed = apply(
        bundle,
        args.hub,
        args.app,
        args.dash_prefix,
        args.wf_prefix,
        args.migration_id,
        args.seed_id,
        args.drop_workflow,
    )
    if changed:
        path.write_text(json.dumps(bundle, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("updated" if changed else "unchanged", path)


if __name__ == "__main__":
    main()
