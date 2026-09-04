#!/usr/bin/env bash
# Seed demostand (or any ISPF) with unmocked 500-el HMI stress mimic + operator app.
# Usage:
#   ISPF_BASE_URL=https://ispf.iot-solutions.ru ISPF_PASSWORD=admin \
#     bash tools/hmi/seed-stress-mimic-500.sh
set -euo pipefail

BASE_URL="${ISPF_BASE_URL:-https://ispf.iot-solutions.ru}"
USER="${ISPF_USERNAME:-admin}"
PASS="${ISPF_PASSWORD:-admin}"
MIMIC_PATH="${HMI_STRESS_MIMIC_PATH:-root.platform.mimics.hmi-stress-500}"
DASH_PATH="${HMI_STRESS_DASH_PATH:-root.platform.dashboards.hmi-stress-500}"
APP_ID="${HMI_STRESS_APP_ID:-hmi-stress-500}"
ELEMENT_COUNT="${HMI_STRESS_ELEMENTS:-500}"

python3 - "$BASE_URL" "$USER" "$PASS" "$MIMIC_PATH" "$DASH_PATH" "$APP_ID" "$ELEMENT_COUNT" <<'PY'
import json, sys, urllib.error, urllib.request

base, user, password, mimic_path, dash_path, app_id, element_count_s = sys.argv[1:8]
element_count = int(element_count_s)


def request(method: str, url: str, token: str | None = None, body: dict | None = None):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as err:
        detail = err.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} -> {err.code}: {detail[:500]}") from err


_, login = request("POST", f"{base.rstrip('/')}/api/v1/auth/login", body={"username": user, "password": password})
token = login["token"]

# Ensure mimic object
try:
    request("GET", f"{base.rstrip('/')}/api/v1/objects/by-path?path={mimic_path}", token=token)
    print(f"mimic exists: {mimic_path}")
except RuntimeError as err:
    if "404" not in str(err):
        raise
    parent, _, name = mimic_path.rpartition(".")
    request(
        "POST",
        f"{base.rstrip('/')}/api/v1/objects",
        token=token,
        body={
            "parentPath": parent,
            "name": name,
            "type": "MIMIC",
            "displayName": "HMI stress 500",
            "templateId": "mimic-v1",
        },
    )
    print(f"mimic created: {mimic_path}")

layer = "layer-default"
cols = 20
elements = []
for i in range(element_count):
    pump = "ui-pump-1" if i % 2 == 0 else "ui-pump-2"
    elements.append(
        {
            "id": f"stress-el-{i}",
            "layerId": layer,
            "symbolId": "custom.svg",
            "x": 40 + (i % cols) * 64,
            "y": 40 + (i // cols) * 48,
            "width": 56,
            "height": 40,
            "rotation": 0,
            "props": {
                "width": 56,
                "height": 40,
                "viewBox": "0 0 56 40",
                "svg": '<rect width="56" height="40" fill="#3fb950" rx="4"/>',
            },
            "bindings": {
                "value": {
                    "objectPath": f"root.platform.devices.{pump}",
                    "variableName": "sineWave",
                    "valueField": "value",
                    "transform": "number",
                }
            },
            "formatRules": [
                {
                    "id": f"fr-{i}",
                    "bindingKey": "value",
                    "operator": ">",
                    "value": -999,
                    "style": {"fill": "#58a6ff"},
                }
            ],
        }
    )

diagram = {
    "version": 2,
    "width": 1400,
    "height": 1300,
    "background": "#0d1117",
    "grid": {"visible": False, "snap": False, "size": 20},
    "layers": [{"id": layer, "name": "Main", "visible": True}],
    "elements": elements,
    "connections": [],
    "customSymbols": [],
}
request(
    "PUT",
    f"{base.rstrip('/')}/api/v1/mimics/by-path/diagram?path={mimic_path}",
    token=token,
    body={"diagramJson": json.dumps(diagram, separators=(",", ":"))},
)
print(f"diagram saved: {element_count} elements bound to ui-pump-1/2 sineWave")

# Ensure dashboard
try:
    request("GET", f"{base.rstrip('/')}/api/v1/objects/by-path?path={dash_path}", token=token)
    print(f"dashboard exists: {dash_path}")
except RuntimeError as err:
    if "404" not in str(err):
        raise
    parent, _, name = dash_path.rpartition(".")
    request(
        "POST",
        f"{base.rstrip('/')}/api/v1/objects",
        token=token,
        body={
            "parentPath": parent,
            "name": name,
            "type": "DASHBOARD",
            "displayName": "HMI stress 500",
            "templateId": "dashboard-v1",
        },
    )
    print(f"dashboard created: {dash_path}")

layout = {
    "columns": 84,
    "rowHeight": 8,
    "widgets": [
        {
            "id": "stress-mimic",
            "type": "scada-mimic",
            "title": "HMI stress 500",
            "x": 0,
            "y": 0,
            "w": 84,
            "h": 70,
            "mimicPath": mimic_path,
        }
    ],
}
request(
    "PUT",
    f"{base.rstrip('/')}/api/v1/dashboards/by-path/layout?path={dash_path}",
    token=token,
    body={"layoutJson": json.dumps(layout, separators=(",", ":"))},
)
request(
    "PUT",
    f"{base.rstrip('/')}/api/v1/dashboards/by-path/title?path={dash_path}",
    token=token,
    body={"title": "HMI stress 500"},
)
request(
    "PUT",
    f"{base.rstrip('/')}/api/v1/operator-apps/{app_id}/ui",
    token=token,
    body={
        "appId": app_id,
        "title": "HMI stress 500",
        "defaultDashboard": dash_path,
        "dashboards": [{"path": dash_path, "title": "Stress 500"}],
    },
)
print(f"operator app ready: {app_id} -> {dash_path}")
print("Re-run live FPS:")
print(
    f"  E2E_BASE_URL={base} E2E_LIVE_FPS=1 E2E_OPERATOR_APP={app_id} "
    "MIMIC_MIN_FPS_LIVE=20 npm run test:quality -- --grep 'live operator mimic'"
)
PY
