#!/usr/bin/env python3
"""Upload calibrated bench script and run sustained test (~100% efficiency) on lab."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"


def run(c, cmd, timeout=7200):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-8000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    with sftp.file(f"{ROOT}/lab-mqtt-event-journal-multi-test.sh", "w") as f:
        f.write((DEPLOY / "lab-mqtt-event-journal-multi-test.sh").read_bytes().replace(b"\r\n", b"\n"))
    with sftp.file(f"{ROOT}/loadtest/mqtt-emqtt-bench.sh", "w") as f:
        f.write((DEPLOY / "mqtt-emqtt-bench.sh").read_bytes().replace(b"\r\n", b"\n"))
    sftp.close()
    print("Uploaded lab-mqtt-event-journal-multi-test.sh", flush=True)

    steps = [
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        f"pkill -f 'lab-mqtt-event-journal-multi-test' 2>/dev/null || true",
        f"cd {ROOT} && DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
        f"PROBE_TOTAL_RATE=96000 AUTO_CALIBRATE=true CALIBRATE_WARMUP=20 CALIBRATE_PHASE=45 "
        f"CALIBRATE_COOLDOWN=30 CALIBRATE_MARGIN=0.98 EMQTT_SHARD_MAX=8 bash lab-mqtt-event-journal-multi-test.sh "
        f"2>&1 | tee {ROOT}/loadtest/calibrated.log",
    ]
    for step in steps:
        run(c, step, timeout=7200)
    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
