#!/usr/bin/env python3
"""Seed 100 devices on one shared MQTT topic and verify persistence on lab."""
from __future__ import annotations

import sys
import time
from pathlib import Path

import paramiko

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
SHARED_TOPIC = "ispf/loadtest/shared/temperature"

UPLOADS = [
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "_lab_verify_shared_topic_remote.py", f"{ROOT}/loadtest/_verify_shared.py"),
    (DEPLOY / "_lab_pg_loadtest.py", f"{ROOT}/loadtest/_pg_loadtest.py"),
]


def run(c, cmd, timeout=3600):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-6000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    return code, out, err


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    devices = int(sys.argv[1]) if len(sys.argv) > 1 else 100

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    for local, remote in UPLOADS:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", local.name, flush=True)
    sftp.close()

    seed_cmd = (
        f"cd {ROOT}/loadtest && python3 setup-mqtt-event-journal-devices.py "
        f"--devices {devices} --shared-topic '{SHARED_TOPIC}' "
        f"--base-url http://127.0.0.1:8000 --broker-url tcp://mqtt:1883 --bench-no-l0-coalesce"
    )
    code, _, _ = run(c, seed_cmd, timeout=3600)
    if code != 0:
        c.close()
        return code

    time.sleep(3)
    run(c, f"python3 {ROOT}/loadtest/_pg_loadtest.py", timeout=120)
    run(c, f"python3 {ROOT}/loadtest/_verify_shared.py", timeout=180)
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
