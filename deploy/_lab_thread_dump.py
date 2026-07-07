#!/usr/bin/env python3
import paramiko
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

c = connect_ssh(timeout=30)



def run(cmd, timeout=180):
    _, o, e = c.exec_command(cmd, timeout=timeout)
    return o.read().decode("utf-8", "replace"), e.read().decode("utf-8", "replace")


remote_script = r"""
docker exec ispf-lab-ispf-server-1 sh -c '
PID=1
echo "=== /proc/1/status ==="
grep -E "Threads|VmRSS|VmSize" /proc/$PID/status || true
echo "=== comm histogram (top 15) ==="
for t in /proc/$PID/task/*; do cat "$t/comm" 2>/dev/null; done | sort | uniq -c | sort -rn | head -15
'
"""

out, err = run(remote_script)
print(out)

out3, _ = run(
    "docker exec ispf-lab-ispf-server-1 sh -c 'printenv | grep -E \"ISPF_DRIVER_MQTT|CALLBACK|ELASTIC\" | sort'"
)
print("=== mqtt env ===")
print(out3 or "(empty)")

# driver count via API
for path in [
    "/api/v1/drivers/runtime/status?devicePath=root.platform.devices.loadtest-mqtt-dev-00001",
    "/api/v1/automation/metrics",
]:
    api_cmd = (
        "TOKEN=$(curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        "-H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin\"}' "
        "| python3 -c 'import json,sys; print(json.load(sys.stdin)[\"token\"])') && "
        f"curl -sf 'http://127.0.0.1:8000{path}' -H \"Authorization: Bearer $TOKEN\""
    )
    out2, _ = run(api_cmd)
    print(f"=== {path} ===")
    print(out2[:2000])
c.close()
