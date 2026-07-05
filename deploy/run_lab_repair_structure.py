#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
DEPLOY = Path(__file__).resolve().parent
UPLOADS = [
    (DEPLOY / "repair-loadtest-device-structure.py", f"{ROOT}/loadtest/repair-loadtest-device-structure.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
]

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, PORT, USER, PW, timeout=60)
sftp = c.open_sftp()
for local, remote in UPLOADS:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))
sftp.close()
_, o, e = c.exec_command(
    f"cd {ROOT}/loadtest && python3 -u repair-loadtest-device-structure.py --base-url http://127.0.0.1:8000",
    timeout=3600,
)
print(o.read().decode("utf-8", "replace"))
err = e.read().decode("utf-8", "replace")
if err.strip():
    print("ERR:", err)
c.close()
