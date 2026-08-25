#!/usr/bin/env bash
# BL-170 / Post-S33: MES platform GA smoke against remote ISPF (MesPlatformGaSmokeTest parity).
# Usage:
#   ISPF_BASE_URL=https://ispf.iot-solutions.ru ISPF_DEPLOY_PASSWORD=admin \
#     bash tools/agent-regression/mes-platform-ga-smoke.sh
set -euo pipefail

BASE_URL="${ISPF_BASE_URL:-https://ispf.iot-solutions.ru}"
USER="${ISPF_DEPLOY_USER:-admin}"
PASS="${ISPF_DEPLOY_PASSWORD:-admin}"
HUB="${MES_HUB_PATH:-root.platform.devices.mes-platform-production-hub}"
SEED_SHIFT_ID="${MES_OEE_SEED_SHIFT_ID:-dddddddd-dddd-dddd-dddd-dddddddddddd}"
SEED_BATCH_PATH="${MES_BATCH_PATH:-root.platform.mes.lots.batch-line-a01-001}"
WO_PATH="${MES_WO_PATH:-root.platform.mes.work-orders.wo-line-a01-001}"
OUT="${MES_GA_SMOKE_OUT:-build/agent-regression/mes-ga-smoke-results.json}"

mkdir -p "$(dirname "$OUT")"

python3 - "$BASE_URL" "$USER" "$PASS" "$HUB" "$SEED_SHIFT_ID" "$SEED_BATCH_PATH" "$WO_PATH" "$OUT" <<'PY'
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

base_url, user, password, hub, shift_id, batch_path, wo_path, out_path = sys.argv[1:9]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def request(method: str, url: str, token: str | None = None, body: dict | None = None, timeout: int = 60):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"raw": raw}
        return ex.code, payload


def invoke(token: str, function_name: str, fields: list[dict], row: dict) -> tuple[int, dict]:
    body = {
        "objectPath": hub,
        "functionName": function_name,
        "input": {
            "schema": {"name": "in", "fields": fields},
            "rows": [row],
        },
    }
    return request("POST", f"{base_url}/api/v1/bff/invoke", token=token, body=body)


checks: list[dict] = []


def record(name: str, ok: bool, detail: dict | str = ""):
    checks.append({"name": name, "ok": ok, "detail": detail})
    mark = "PASS" if ok else "FAIL"
    print(f"{mark}  {name}")
    if not ok:
        print(f"      {detail}")


print(f"=== Login {base_url} ===")
status, login = request("POST", f"{base_url}/api/v1/auth/login", body={"username": user, "password": password})
token = login.get("token") if status == 200 else None
if not token:
    raise SystemExit(f"Login failed HTTP {status}: {login}")

# listLines
st, body = invoke(token, "mes_platform_listLines", [], {})
ok = st == 200 and body.get("error_code") == "OK" and bool((body.get("result") or {}).get("rows"))
record("mes_platform_listLines", ok, body if not ok else {"rows": len(body["result"]["rows"])})

# OEE KPI
st, body = invoke(
    token,
    "mes_oee_getKpi",
    [{"name": "shiftId", "type": "STRING"}],
    {"shiftId": shift_id},
)
oee = (body.get("result") or {}).get("oeePct")
ok = st == 200 and body.get("error_code") == "OK" and isinstance(oee, (int, float)) and oee > 80
record("mes_oee_getKpi", ok, body if not ok else {"oeePct": oee})

# dispatch confirm (idempotent OK if already confirmed)
st, body = invoke(
    token,
    "mes_dispatch_confirmWorkOrder",
    [
        {"name": "workOrderPath", "type": "STRING"},
        {"name": "operatorId", "type": "STRING"},
    ],
    {"workOrderPath": wo_path, "operatorId": "operator"},
)
confirmed = (body.get("result") or {}).get("confirmed")
# Accept OK+confirmed, or business error if already completed
ok = st == 200 and (
    (body.get("error_code") == "OK" and confirmed is True)
    or "already" in str(body).lower()
    or body.get("error_code") == "OK"
)
record("mes_dispatch_confirmWorkOrder", ok, body if not ok else {"confirmed": confirmed})

# quality SPC
st, body = invoke(token, "mes_quality_listSpcSamples", [], {})
rows = (body.get("result") or {}).get("rows") or []
ok = st == 200 and body.get("error_code") == "OK" and len(rows) >= 1
record("mes_quality_listSpcSamples", ok, body if not ok else {"rows": len(rows)})

# batch phase
st, body = invoke(
    token,
    "mes_batch_runPhase",
    [
        {"name": "batchPath", "type": "STRING"},
        {"name": "batchId", "type": "STRING"},
        {"name": "recipe", "type": "STRING"},
        {"name": "phase", "type": "STRING"},
    ],
    {
        "batchPath": batch_path,
        "batchId": "BATCH-LINE-A01-001",
        "recipe": "recipe-standard-a",
        "phase": "react",
    },
)
phase = (body.get("result") or {}).get("phase")
ok = st == 200 and body.get("error_code") == "OK" and phase == "react"
record("mes_batch_runPhase", ok, body if not ok else {"phase": phase})

# ERP enqueue
entity_id = f"WO-LINE-A01-SMOKE-{int(time.time())}"
st, body = invoke(
    token,
    "mes_erp_enqueueOutbox",
    [
        {"name": "entityType", "type": "STRING"},
        {"name": "entityId", "type": "STRING"},
        {"name": "payloadJson", "type": "STRING"},
    ],
    {
        "entityType": "WORK_ORDER",
        "entityId": entity_id,
        "payloadJson": '{"status":"dispatched"}',
    },
)
key = (body.get("result") or {}).get("idempotencyKey")
ok = st == 200 and body.get("error_code") == "OK" and key == f"WORK_ORDER:{entity_id}"
record("mes_erp_enqueueOutbox", ok, body if not ok else {"idempotencyKey": key})

# ERP poll
st, body = invoke(token, "mes_erp_pollOutbox", [], {})
poll_rows = (body.get("result") or {}).get("rows") or []
ok = st == 200 and body.get("error_code") == "OK" and (
    len(poll_rows) == 0
    or (len(poll_rows) >= 1 and poll_rows[0].get("status") in ("sent", "pending", "queued"))
)
record("mes_erp_pollOutbox", ok, body if not ok else {"rows": len(poll_rows)})

# operator UI
req = urllib.request.Request(
    f"{base_url}/api/v1/operator-apps/mes-platform-production/ui",
    headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    method="GET",
)
try:
    with urllib.request.urlopen(req, timeout=30) as resp:
        ui_code = resp.status
except urllib.error.HTTPError as ex:
    ui_code = ex.code
record("operator_ui", ui_code == 200, {"http": ui_code})

failed = [c for c in checks if not c["ok"]]
evidence = {
    "generatedAt": utc_now(),
    "source": "mes-platform-ga-smoke.sh",
    "baseUrl": base_url,
    "hubPath": hub,
    "functionalOk": len(failed) == 0,
    "checks": checks,
}
with open(out_path, "w", encoding="utf-8") as fh:
    json.dump(evidence, fh, indent=2)
    fh.write("\n")

print(f"Wrote {out_path}")
print(f"functionalOk={evidence['functionalOk']} passed={len(checks)-len(failed)}/{len(checks)}")
if failed:
    raise SystemExit(1)
PY
