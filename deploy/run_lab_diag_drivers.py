#!/usr/bin/env python3
"""Upload and run driver diagnostics on lab."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    local = DEPLOY / "_lab_diag_drivers_remote.py"
    remote = f"{ROOT}/loadtest/_diag_drivers.py"
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    sftp.close()

    cmds = [
        f"python3 {remote}",
        "docker exec mqtt mosquitto_sub -h localhost -t '$SYS/broker/clients/connected' -C 1 -W 3 2>/dev/null || true",
        "docker logs mqtt --tail 30 2>&1",
    ]
    for cmd in cmds:
        print(">", cmd[:120], flush=True)
        _, o, e = c.exec_command(cmd, timeout=120)
        out = o.read().decode("utf-8", "replace")
        err = e.read().decode("utf-8", "replace")
        if out:
            print(out[-8000:], flush=True)
        if err.strip():
            print("STDERR:", err[-2000:], flush=True)
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
