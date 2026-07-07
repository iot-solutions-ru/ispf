#!/usr/bin/env python3
"""Validate historian path end-to-end on lab, then run sustained load test."""
from __future__ import annotations

import paramiko
import re
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"


def run(c, cmd, timeout=7200):
    print(">", cmd[:170], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-16000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    print("exit", code, flush=True)
    return code, out


def upload(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    uploads = [
        ("lab-stress.env", f"{ROOT}/lab-stress.env"),
        ("lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
        ("lab-mqtt-historian-multi-test.sh", f"{ROOT}/lab-mqtt-historian-multi-test.sh"),
        ("setup-mqtt-historian-devices.py", f"{ROOT}/loadtest/setup-mqtt-historian-devices.py"),
        ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
        ("mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
        ("lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    ]
    for name, remote in uploads:
        upload(sftp, DEPLOY / name, remote)
        print("  uploaded", name, flush=True)
    sftp.close()

    log = f"{ROOT}/loadtest/historian-peak.log"
    steps = [
        f"chmod +x {ROOT}/lab-mqtt-historian-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml restart mqtt",
        "sleep 3",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
        "sleep 15",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"DEVICES=16 RATE_PER_DEVICE=6800 WARMUP=20 PHASE=60 "
            f"SKIP_DEVICE_SETUP=false EMQTT_SHARD_MAX=4 NUMERIC_PAYLOAD=true "
            f"bash lab-mqtt-historian-multi-test.sh 2>&1 | tee {log}"
        ),
    ]
    for step in steps:
        code, out = run(c, step)
        if code != 0 and "tee" not in step:
            c.close()
            return code

    _, log_text = run(c, f"cat {log}", timeout=60)
    print("\n=== HISTORIAN LAB TEST SUMMARY ===")
    for pat, label in [
        (r"Mosquitto PUBLISH in:\s*([0-9.]+)", "Mosquitto in msg/s"),
        (r"Historian flushed \(metrics\):\s*([0-9.]+)", "Flushed samples/s"),
        (r"Samples \(Scylla COUNT\):\s*([0-9.]+)", "Scylla samples/s"),
        (r"Samples per device \(avg\):\s*([0-9.]+)", "Per device samples/s"),
        (r"Historian capture \(flushed/delivered\):\s*([0-9.n/a]+)%", "Capture %"),
        (r"Scylla COUNT vs flushed:\s*([0-9.n/a]+)%", "Scylla vs flushed %"),
    ]:
        m = re.search(pat, log_text)
        if m:
            print(f"  {label}: {m.group(1)}")
    print(f"  Log: {log}")
    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
