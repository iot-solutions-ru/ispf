#!/usr/bin/env python3
"""Confirm ~83k journal on existing lab stack: 16×32k, 8 emqtt shards, no wipe."""
from __future__ import annotations

import paramiko
import sys

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=7200):
    print(">", cmd[:150], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-10000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    log = f"{ROOT}/loadtest/peak-confirm.log"
    steps = [
        f"curl -sf http://127.0.0.1:8000/api/v1/info | head -c 200",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
            f"SKIP_DEVICE_SETUP=true AUTO_CALIBRATE=false EMQTT_SHARD_MAX=8 "
            f"bash lab-mqtt-event-journal-multi-test.sh 2>&1 | tee {log}"
        ),
        f"grep -E 'emqtt-bench|Events/s|Efficiency|Journal events|drivers running' {log} | tail -15",
        (
            "docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' "
            f"$(docker compose --env-file {ROOT}/lab-stress.env "
            f"-f {ROOT}/lab-test-host-compose.yml ps -q ispf-server scylla mqtt)"
        ),
    ]
    for step in steps:
        code, _, _ = run(c, step, timeout=7200)
        if code != 0 and "grep" not in step and "docker stats" not in step:
            c.close()
            return code
    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
