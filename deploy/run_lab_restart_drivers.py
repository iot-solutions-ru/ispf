#!/usr/bin/env python3
"""Upload and restart loadtest drivers on lab."""
from __future__ import annotations

import socket
import sys
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"

UPLOADS = [
    (DEPLOY / "restart-loadtest-mqtt-drivers.py", f"{ROOT}/loadtest/restart-loadtest-mqtt-drivers.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "_lab_diag_drivers_remote.py", f"{ROOT}/loadtest/_diag_drivers.py"),
]


def run(c, cmd, timeout=600):
    print(">", cmd[:140], flush=True)
    _, stdout, stderr = c.exec_command(cmd, timeout=timeout, get_pty=True)
    stdout.channel.settimeout(5.0)
    while not stdout.channel.exit_status_ready():
        try:
            chunk = stdout.channel.recv(4096)
            if chunk:
                print(chunk.decode("utf-8", "replace"), end="", flush=True)
        except socket.timeout:
            continue
    while stdout.channel.recv_ready():
        chunk = stdout.channel.recv(4096)
        if chunk:
            print(chunk.decode("utf-8", "replace"), end="", flush=True)
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    return code


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    devices = int(sys.argv[1]) if len(sys.argv) > 1 else 100

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    for local, remote in UPLOADS:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    sftp.close()

    code = run(
        c,
        f"cd {ROOT}/loadtest && python3 -u restart-loadtest-mqtt-drivers.py "
        f"--base-url http://127.0.0.1:8000 --devices {devices} --batch-size 10 --pause-s 1.5 --max-passes 6",
        timeout=600,
    )
    run(c, f"python3 {ROOT}/loadtest/_diag_drivers.py", timeout=180)
    run(c, "docker exec ispf-lab-mqtt-1 mosquitto_sub -h localhost -t '$SYS/broker/clients/connected' -C 1 -W 3 2>/dev/null || true")
    c.close()
    return code


if __name__ == "__main__":
    raise SystemExit(main())
