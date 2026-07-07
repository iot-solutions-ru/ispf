#!/usr/bin/env python3
"""Peak-finding sweep with max Scylla/ISPF tuning on lab."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"

FILES = [
    "lab-stress.env",
    "lab-stress-sweep.sh",
    "lab-mqtt-event-journal-multi-test.sh",
    "mqtt-emqtt-bench.sh",
    "lab-emqtt-cleanup.sh",
]


def run(c, cmd, timeout=14400):
    print(">", cmd[:110])
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
    remote = f"{ROOT}/{name}" if name.endswith((".sh", ".env")) else f"{ROOT}/loadtest/{name}"
    if name == "mqtt-emqtt-bench.sh":
        remote = f"{ROOT}/loadtest/{name}"
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    print("uploaded", name)
sftp.close()

env = (
    "SWEEP_RATES='8000 12000 16000 20000 24000 28000 32000 40000 50000' "
    "SWEEP_PHASE=60 SWEEP_WARMUP=20 SWEEP_CALLBACK_THREADS=64 EMQTT_SHARD_MAX=8"
)
code, out, err = run(
    c,
    f"chmod +x {ROOT}/lab-stress-sweep.sh {ROOT}/lab-mqtt-event-journal-multi-test.sh "
    f"{ROOT}/loadtest/mqtt-emqtt-bench.sh {ROOT}/lab-emqtt-cleanup.sh && "
    f"bash {ROOT}/lab-emqtt-cleanup.sh && "
    f"cd {ROOT} && {env} bash lab-stress-sweep.sh",
    timeout=14400,
)
c.close()
text = out + ("\n--- stderr ---\n" + err if err.strip() else "")
Path(REPO / "tmp_sweep_tuned_result.txt").write_text(text, encoding="utf-8")
sys.stdout.buffer.write(text[-10000:].encode("utf-8", errors="replace"))
sys.stdout.buffer.write(f"\nexit {code}\n".encode())
raise SystemExit(code)
