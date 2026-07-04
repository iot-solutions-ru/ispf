#!/usr/bin/env python3
"""Run sustained event-journal test at realistic rate (~100% MQTT efficiency)."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
# Override via env on lab: STRESS_SUSTAINED_RATE_PER_DEVICE from lab-stress.env
SUSTAINED_RATE = 5200


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
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    for name, remote in [
        ("lab-stress.env", f"{ROOT}/lab-stress.env"),
        ("lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
        ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    ]:
        with sftp.file(remote, "w") as f:
            f.write((DEPLOY / name).read_bytes().replace(b"\r\n", b"\n"))
        print("  uploaded", name, flush=True)
    sftp.close()

    steps = [
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
        f"DEVICES=16 RATE_PER_DEVICE=${{STRESS_SUSTAINED_RATE_PER_DEVICE:-{SUSTAINED_RATE}}} "
        f"WARMUP=25 PHASE=90 SKIP_DEVICE_SETUP=true AUTO_CALIBRATE=false EMQTT_SHARD_MAX=4 "
        f"bash lab-mqtt-event-journal-multi-test.sh 2>&1 | tee {ROOT}/loadtest/sustained.log",
    ]
    for step in steps:
        run(c, step, timeout=7200)
    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
