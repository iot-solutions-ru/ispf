#!/usr/bin/env bash
# Stop all running device drivers on prod VPS (demo simulators, tank-farm, SNMP, MQTT ingress).
set -eu

BASE="${BASE_URL:-http://127.0.0.1:8080}"

TOKEN=$(curl -sf -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

export BASE TOKEN

python3 <<'PY'
import json
import os
import sys
import urllib.parse
import urllib.request

base = os.environ["BASE"]
token = os.environ["TOKEN"]
headers = {"Authorization": f"Bearer {token}"}


def request(method, url, data=None):
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read()
            return resp.status, body
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def get_json(url):
    status, body = request("GET", url)
    if status != 200:
        return None
    return json.loads(body)


def stop_driver(path):
    enc = urllib.parse.quote(path, safe="")
    status, body = request("POST", f"{base}/api/v1/drivers/runtime/stop?devicePath={enc}")
    if status == 200:
        print(f"stopped: {path}")
        return True
    snippet = body[:160].decode("utf-8", errors="replace") if body else ""
    print(f"stop failed ({status}): {path} {snippet}", file=sys.stderr)
    return False


def walk(parent):
    children = get_json(f"{base}/api/v1/objects?parent={urllib.parse.quote(parent, safe='')}&lite=true")
    if not children:
        return
    for node in children:
        path = node.get("path")
        if not path:
            continue
        if node.get("type") == "DEVICE":
            status = get_json(
                f"{base}/api/v1/drivers/runtime/status?devicePath={urllib.parse.quote(path, safe='')}"
            )
            if status and status.get("status") == "RUNNING":
                stop_driver(path)
        walk(path)


print("Stopping all RUNNING device drivers under root.platform.devices...")
walk("root.platform.devices")
PY

docker update --restart=no ispf-mqtt-loadtest 2>/dev/null || true
if docker stop ispf-mqtt-loadtest 2>/dev/null; then
  echo "mqtt-loadtest container stopped"
else
  echo "mqtt-loadtest not running"
fi

sleep 5
curl -sf "$BASE/api/v1/info" | python3 -c 'import json,sys; d=json.load(sys.stdin); print("version", d["version"])'
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' ispf-vps-replica-1 2>/dev/null || true
