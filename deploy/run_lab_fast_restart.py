#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
local = Path(__file__).resolve().parent / "_lab_fast_restart_remote.py"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, PORT, USER, PW, timeout=60)
sftp = c.open_sftp()
with sftp.file(f"{ROOT}/loadtest/_fast_restart.py", "w") as f:
    f.write(local.read_bytes().replace(b"\r\n", b"\n"))
sftp.close()
_, o, e = c.exec_command(f"python3 -u {ROOT}/loadtest/_fast_restart.py", timeout=600)
print(o.read().decode("utf-8", "replace"))
if e.read().decode().strip():
    print("ERR:", e.read())
c.close()
