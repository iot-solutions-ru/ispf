#!/usr/bin/env python3
import sys
from pathlib import Path
import paramiko

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = "/home/iot-solutions/ispf/loadtest"
c = connect_ssh(timeout=60)

sftp = c.open_sftp()
for name in ("_lab_stop_ghost_drivers_remote.py", "_lab_probe_fanout.py"):
    data = Path("deploy", name).read_bytes().replace(b"\r\n", b"\n")
    with sftp.file(f"{ROOT}/{name.lstrip('_')}", "w") as f:
        f.write(data)
sftp.close()
for cmd in (
    f"python3 {ROOT}/lab_stop_ghost_drivers_remote.py 16",
    f"python3 {ROOT}/lab_probe_fanout.py",
):
    print(">", cmd, flush=True)
    _, o, e = c.exec_command(cmd, timeout=180)
    print(o.read().decode("utf-8", "replace"))
c.close()
