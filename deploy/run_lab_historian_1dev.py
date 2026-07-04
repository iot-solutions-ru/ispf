#!/usr/bin/env python3
"""Single-device historian peak on lab (1×32k)."""
from __future__ import annotations

import paramiko
import re
import sys

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
LOG = f"{ROOT}/loadtest/historian-1dev.log"


def run(c, cmd, timeout=7200):
    print(">", cmd[:170], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-16000:], flush=True)
    if err.strip():
        print("STDERR:", err[-800:], flush=True)
    print("exit", code, flush=True)
    return code, out


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    cmd = (
        f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
        f"bash {ROOT}/lab-emqtt-cleanup.sh && "
        f"DEVICES=1 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
        f"SKIP_DEVICE_SETUP=false NUMERIC_PAYLOAD=true "
        f"bash lab-mqtt-historian-multi-test.sh 2>&1 | tee {LOG}"
    )
    code, log_text = run(c, cmd)
    run(c, f"docker stats --no-stream --format '{{{{.Name}}}} {{{{.CPUPerc}}}}' "
        f"$(docker compose -f {ROOT}/lab-test-host-compose.yml ps -q ispf-server)")
    print("\n=== HISTORIAN 1 DEVICE (32k target) ===")
    for pat, label in [
        (r"ISPF ([0-9.]+)", "version"),
        (r"Mosquitto PUBLISH in:\s*([0-9.]+)", "mosquitto in/s"),
        (r"Historian flushed \(metrics\):\s*([0-9.]+)", "flushed/s"),
        (r"Historian capture \(flushed/delivered\):\s*([0-9.]+)%", "capture %"),
        (r"Samples \(Scylla COUNT\):\s*([0-9.]+)", "scylla count/s"),
    ]:
        m = re.search(pat, log_text)
        if m:
            print(f"  {label}: {m.group(1)}")
    print(f"  per-device ref (16 dev peak): ~8.9k flushed/s/device, capture ~24%")
    print(f"  Log: {LOG}")
    c.close()
    return code


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
