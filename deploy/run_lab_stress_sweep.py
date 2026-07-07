#!/usr/bin/env python3
"""Upload stress sweep script and run rate ramp on lab host."""
from __future__ import annotations

import paramiko
from pathlib import Path

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"

FILES = [
    "lab-test-host-compose.yml",
    "lab-stress.env",
    "lab-stress-sweep.sh",
    "lab-mqtt-event-journal-multi-test.sh",
    "setup-mqtt-event-journal-devices.py",
    "mqtt_loadtest_lib.py",
    "loadtest_cleanup_lib.py",
    "mqtt-emqtt-bench.sh",
]


def run(c, cmd, timeout=14400):
    print(">", cmd[:100])
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode(errors="replace")
    err = e.read().decode(errors="replace")
    return o.channel.recv_exit_status(), out, err


c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, PORT, USER, lab_password(), timeout=60)
sftp = c.open_sftp()
for name in FILES:
    local = DEPLOY / name
    remote = f"{ROOT}/{name}" if name.endswith((".sh", ".yml", ".env")) or name.startswith("lab-") else f"{ROOT}/loadtest/{name}"
    if name in ("setup-mqtt-event-journal-devices.py", "mqtt_loadtest_lib.py", "loadtest_cleanup_lib.py", "mqtt-emqtt-bench.sh"):
        remote = f"{ROOT}/loadtest/{name}"
    elif name == "lab-stress-sweep.sh":
        remote = f"{ROOT}/{name}"
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    print("uploaded", name)
sftp.close()

code, out, err = run(
    c,
    f"chmod +x {ROOT}/lab-stress-sweep.sh {ROOT}/lab-mqtt-event-journal-multi-test.sh "
    f"{ROOT}/loadtest/mqtt-emqtt-bench.sh && "
    f"cd {ROOT} && bash lab-stress-sweep.sh",
    timeout=14400,
)
c.close()

result = out + ("\n--- stderr ---\n" + err if err.strip() else "")
Path(REPO / "tmp_sweep_result.txt").write_text(result, encoding="utf-8")
import sys
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE
sys.stdout.buffer.write(result[-8000:].encode("utf-8", errors="replace"))
sys.stdout.buffer.write(f"\nexit {code}\n".encode())
raise SystemExit(code)
