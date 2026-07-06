#!/usr/bin/env bash
# Tune demostand drivers for lower CPU: TELEMETRY_ONLY + per-device coalesce.
set -eu

BASE="${BASE_URL:-http://127.0.0.1:8080}"

python3 <<'PY'
import json
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080"

def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

login = api("POST", "/api/v1/auth/login", {"username": "admin", "password": "admin"})
TOKEN = login["token"]

SNMP_CONFIG = {
    "host": "127.0.0.1",
    "port": "161",
    "community": "public",
    "version": "2c",
    "timeoutMs": "3000",
    "retries": "1",
}
SNMP_MAPPINGS = {
    "sysName": "1.3.6.1.2.1.1.5.0:STRING",
    "sysDescr": "1.3.6.1.2.1.1.1.0:STRING",
    "sysUpTime": "1.3.6.1.2.1.1.3.0",
    "sysLocation": "1.3.6.1.2.1.1.6.0:STRING",
    "sysContact": "1.3.6.1.2.1.1.4.0:STRING",
    "hrMemorySize": "1.3.6.1.2.1.25.2.2.0:INTEGER",
    "hrSystemProcesses": "1.3.6.1.2.1.25.1.6.0:INTEGER",
    "hrSystemNumUsers": "1.3.6.1.2.1.25.1.5.0:INTEGER",
    "ifNumber": "1.3.6.1.2.1.2.1.0:INTEGER",
    "ifInOctets": "1.3.6.1.2.1.2.2.1.10.2:INTEGER",
    "ifOutOctets": "1.3.6.1.2.1.2.2.1.16.2:INTEGER",
    "ifDescr": "1.3.6.1.2.1.2.2.1.2.2:STRING",
    "ifSpeed": "1.3.6.1.2.1.2.2.1.5.2:INTEGER",
    "ifOperStatus": "1.3.6.1.2.1.2.2.1.8.2:INTEGER",
    "ifInErrors": "1.3.6.1.2.1.2.2.1.14.2:INTEGER",
    "ifOutErrors": "1.3.6.1.2.1.2.2.1.20.2:INTEGER",
    "ifInUcastPkts": "1.3.6.1.2.1.2.2.1.11.2:INTEGER",
    "ifOutUcastPkts": "1.3.6.1.2.1.2.2.1.17.2:INTEGER",
    "hrProcessorLoad": "1.3.6.1.2.1.25.3.3.1.2.196608:INTEGER:optional",
}

def configure(path, body):
    enc = urllib.parse.quote(path, safe="")
    api("POST", f"/api/v1/drivers/runtime/stop?devicePath={enc}", token=TOKEN)
    result = api("PUT", f"/api/v1/drivers/runtime/configure?devicePath={enc}", body, token=TOKEN)
    print(json.dumps({"path": path, "status": result.get("status"), "mode": result.get("telemetryPublishMode"),
                      "coalesceMs": result.get("telemetryCoalesceMs"), "pollMs": result.get("pollIntervalMs")}))

configure("root.platform.devices.snmp-localhost", {
    "driverId": "snmp",
    "pollIntervalMs": 5000,
    "configuration": SNMP_CONFIG,
    "pointMappings": SNMP_MAPPINGS,
    "telemetryPublishMode": "TELEMETRY_ONLY",
    "telemetryCoalesceMs": 5000,
    "autoStart": True,
})

configure("root.platform.devices.demo-sensor-01", {
    "driverId": "virtual",
    "pollIntervalMs": 2000,
    "configuration": {
        "baseTemperature": "22.0",
        "amplitude": "15.0",
        "periodSec": "60",
    },
    "pointMappings": {"temperature": "sim"},
    "telemetryPublishMode": "FULL",
    "telemetryCoalesceMs": 2000,
    "autoStart": True,
})
PY

echo "Done."
