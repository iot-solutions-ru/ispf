#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
local = Path(__file__).resolve().parent / "_lab_fanout_check_remote.py"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, PORT, USER, PW, timeout=60)
sftp = c.open_sftp()
with sftp.file(f"{ROOT}/loadtest/_fanout_check.py", "w") as f:
    f.write(local.read_bytes().replace(b"\r\n", b"\n"))
sftp.close()
_, o, e = c.exec_command(f"python3 -u {ROOT}/loadtest/_fanout_check.py", timeout=120)
print(o.read().decode("utf-8", "replace"))
err = e.read().decode("utf-8", "replace")
if err.strip():
    print("ERR:", err)
c.close()
