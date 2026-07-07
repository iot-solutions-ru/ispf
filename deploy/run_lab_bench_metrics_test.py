#!/usr/bin/env python3
"""Upload bench fixes + recreate mqtt for $SYS; short confirm run."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"


def run(c, cmd, timeout=7200):
    print(">", cmd[:150], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-12000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    uploads = [
        ("lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
        ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
        ("mosquitto/mosquitto.conf", f"{ROOT}/mqtt/mosquitto.conf"),
    ]
    for name, remote in uploads:
        upload_text(sftp, DEPLOY / name.replace("mosquitto/", "mosquitto/") if name.startswith("mosquitto") else DEPLOY / name.split("/")[-1] if "/" in name else DEPLOY / name, remote)
    # fix paths
    upload_text(sftp, DEPLOY / "lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh")
    upload_text(sftp, DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh")
    upload_text(sftp, DEPLOY / "mosquitto" / "mosquitto.conf", f"{ROOT}/mqtt/mosquitto.conf")
    sftp.close()

    log = f"{ROOT}/loadtest/bench-metrics-test.log"
    steps = [
        f"chmod +x {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh",
        (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=15 PHASE=45 "
            f"SKIP_DEVICE_SETUP=false AUTO_CALIBRATE=false EMQTT_SHARD_MAX=8 "
            f"bash lab-mqtt-event-journal-multi-test.sh 2>&1 | tee {log}"
        ),
        f"grep -E 'Throughput|Efficiency|Mosquitto|eventsFired|Journal|configured|formula|capture' {log}",
    ]
    for step in steps:
        run(c, step, timeout=7200)
    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
