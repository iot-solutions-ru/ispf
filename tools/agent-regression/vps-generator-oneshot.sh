#!/usr/bin/env bash
# BL-180: live solution generator on remote ISPF + soft-budget evidence JSON (curl).
# Usage:
#   ISPF_VPS_URL=https://ispf.iot-solutions.ru ISPF_VPS_PASSWORD=admin \
#     bash tools/agent-regression/vps-generator-oneshot.sh hvac
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DOMAIN="${1:-${AGENT_LIVE_GENERATOR_DOMAIN:-hvac}}"
BASE_URL="${ISPF_VPS_URL:-${ISPF_BASE_URL:-https://ispf.iot-solutions.ru}}"
USER="${ISPF_VPS_USER:-${ISPF_DEPLOY_USER:-admin}}"
PASS="${ISPF_VPS_PASSWORD:-${ISPF_DEPLOY_PASSWORD:-admin}}"
OUT="${AGENT_LIVE_GENERATOR_RESULTS:-build/agent-regression/live-generator-results.json}"
SOFT_BUDGET_MS="${AGENT_LIVE_GENERATOR_SOFT_BUDGET_MS:-900000}"

case "$DOMAIN" in
  hvac|mes|scada) ;;
  *)
    echo "FAIL: domain must be hvac|mes|scada (got: $DOMAIN)" >&2
    exit 1
    ;;
esac

mkdir -p "$(dirname "$OUT")"

python3 - "$DOMAIN" "$BASE_URL" "$USER" "$PASS" "$OUT" "$SOFT_BUDGET_MS" <<'PY'
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

domain, base_url, user, password, out_path, soft_budget_ms = sys.argv[1:7]
soft_budget_ms = int(soft_budget_ms)

prompts = {
    "hvac": (
        "Describe a building HVAC plant with one AHU and zone comfort monitoring. "
        "Need an overview dashboard and a high-status alert on the hub."
    ),
    "mes": (
        "Describe a factory MES cell with one packaging line and OEE monitoring. "
        "Need an overview dashboard and a high-status alert on the hub."
    ),
    "scada": (
        "Describe a SCADA plant with one pump station and tank level monitoring. "
        "Need an overview dashboard and a high-status alert on the hub."
    ),
}
prompt = prompts[domain]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def request(method: str, url: str, token: str | None = None, body: dict | None = None, timeout: int = 960):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def request_status(method: str, url: str, token: str, timeout: int = 30) -> int:
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status
    except urllib.error.HTTPError as ex:
        return ex.code

run_started = utc_now()
print(f"=== Login {base_url} ===")
login = request("POST", f"{base_url}/api/v1/auth/login", body={"username": user, "password": password}, timeout=30)
token = login.get("token")
if not token:
    raise SystemExit(f"Login failed: {login}")

print("=== AI provider ===")
provider = request("GET", f"{base_url}/api/v1/ai/provider", token=token, timeout=30)
print(json.dumps({"enabled": provider.get("enabled"), "available": provider.get("available"), "model": provider.get("model")}))
if not provider.get("available"):
    raise SystemExit(f"AI provider not available: {provider}")

print(f"=== Generate apply domain={domain} ===")
started = time.monotonic()
gen = request(
    "POST",
    f"{base_url}/api/v1/ai/solutions/generate",
    token=token,
    body={"prompt": prompt, "apply": True},
)
elapsed_ms = int((time.monotonic() - started) * 1000)
print(f"elapsedMs={elapsed_ms}")
print(json.dumps({k: gen.get(k) for k in ("status", "mode", "appId", "hubPath", "bundleTrust") if k in gen})[:500])

functional_ok = bool(
    gen.get("mode") == "live"
    and gen.get("appId")
    and gen.get("hubPath")
    and gen.get("dashboardPath")
    and gen.get("alertPath")
)
soft_budget_met = elapsed_ms <= soft_budget_ms
ui_code = ""
if functional_ok:
    ui_code = str(request_status("GET", f"{base_url}/api/v1/operator-apps/{gen['appId']}/ui", token))
    if ui_code != "200":
        functional_ok = False

evidence = {
    "generatedAt": utc_now(),
    "runStartedAt": run_started,
    "source": "vps-generator-oneshot.sh",
    "baseUrl": base_url,
    "softBudgetMs": soft_budget_ms,
    "softBudgetMet": bool(soft_budget_met and functional_ok),
    "functionalOk": bool(functional_ok),
    "domains": [
        {
            "domain": domain,
            "status": "OK" if functional_ok else "ERROR",
            "elapsedMs": elapsed_ms,
            "softBudgetMs": soft_budget_ms,
            "softBudgetMet": bool(soft_budget_met),
            "appId": gen.get("appId") or "",
            "hubPath": gen.get("hubPath") or "",
            "dashboardPath": gen.get("dashboardPath") or "",
            "alertPath": gen.get("alertPath") or "",
            "mode": gen.get("mode") or "",
            "composition": gen.get("composition") or "",
            "bundleTrust": gen.get("bundleTrust") or "",
            "operatorUiHttp": ui_code,
        }
    ],
}

with open(out_path, "w", encoding="utf-8") as fh:
    json.dump(evidence, fh, indent=2)
    fh.write("\n")

print(f"Wrote {out_path}")
print(f"functionalOk={functional_ok} softBudgetMet={evidence['softBudgetMet']} elapsedMs={elapsed_ms} ui={ui_code}")

if not functional_ok:
    raise SystemExit(1)
if not soft_budget_met:
    print("WARN: soft budget exceeded (soft signal)")
    raise SystemExit(2)
PY
