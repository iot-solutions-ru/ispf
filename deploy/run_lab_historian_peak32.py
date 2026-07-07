#!/usr/bin/env python3
"""Lab historian peak: 16×32k msg/s → Scylla variable_samples."""
from __future__ import annotations

import paramiko
import re
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
DEPLOY = Path(__file__).resolve().parents[1] / "deploy"


def run(c, cmd, timeout=7200):
    print(">", cmd[:170], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-12000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1000:], flush=True)
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
    upload(sftp, DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh")
    sftp.close()

    log = f"{ROOT}/loadtest/historian-peak-32k.log"
    cmd = (
        f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
        f"DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
        f"SKIP_DEVICE_SETUP=false NUMERIC_PAYLOAD=true "
        f"bash lab-mqtt-historian-multi-test.sh 2>&1 | tee {log}"
    )
    code, log_text = run(c, cmd)
    print("\n=== HISTORIAN PEAK 32k SUMMARY ===")
    for pat, label in [
        (r"Mosquitto PUBLISH in:\s*([0-9.]+)", "Mosquitto in/s"),
        (r"Historian flushed \(metrics\):\s*([0-9.]+)", "Flushed samples/s"),
        (r"Samples \(Scylla COUNT\):\s*([0-9.]+)", "Scylla COUNT/s (phase)"),
        (r"Historian capture \(flushed/delivered\):\s*([0-9.]+)%", "Capture %"),
    ]:
        m = re.search(pat, log_text)
        if m:
            print(f"  {label}: {m.group(1)}")
    print(f"  Log: {log}")
    c.close()
    return code


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
