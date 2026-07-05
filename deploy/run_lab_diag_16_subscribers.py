#!/usr/bin/env python3
"""Run 16-driver + broker subscriber diagnostics on lab."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    local = REPO / "deploy" / "_lab_diag_16_subscribers_remote.py"
    remote = f"{ROOT}/loadtest/_diag_16_subscribers.py"
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    sftp.close()

    cmd = f"python3 -u {remote}"
    print(">", cmd, flush=True)
    _, o, e = c.exec_command(cmd, timeout=300)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    if out:
        print(out)
    if err.strip():
        print("STDERR:", err)
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
