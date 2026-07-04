#!/usr/bin/env python3
"""Upload lab scripts and run clean 8x2000 test on 84.42.21.226."""
from __future__ import annotations

import paramiko
from pathlib import Path

HOST = "84.42.21.226"
PORT = 5031
USER = "iot-solutions"
PASSWORD = "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
ROOT = "/home/iot-solutions/ispf"
DEPLOY = Path(__file__).resolve().parent

UPLOADS = [
    "lab-test-host-compose.yml",
    "lab-test-host-clean-run.sh",
    "lab-mqtt-event-journal-multi-test.sh",
    "nginx-lab.conf",
    "setup-mqtt-event-journal-devices.py",
    "mqtt_loadtest_lib.py",
    "loadtest_cleanup_lib.py",
    "mqtt-emqtt-bench.sh",
]


def upload_text(sftp, local: Path, remote: str) -> None:
    data = local.read_bytes().replace(b"\r\n", b"\n")
    with sftp.file(remote, "w") as f:
        f.write(data)


def main() -> int:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

    sftp = client.open_sftp()
    for name in UPLOADS:
        local = DEPLOY / name
        if not local.exists():
            print("skip missing", name)
            continue
        dest = f"{ROOT}/{name}" if name.endswith((".yml", ".conf", ".sh")) else f"{ROOT}/loadtest/{name}"
        if name.endswith(".sh"):
            dest = f"{ROOT}/{name}" if name.startswith("lab-") else f"{ROOT}/loadtest/{name}"
        if name in ("setup-mqtt-event-journal-devices.py", "mqtt_loadtest_lib.py", "loadtest_cleanup_lib.py", "mqtt-emqtt-bench.sh"):
            dest = f"{ROOT}/loadtest/{name}"
        print("upload", name, "->", dest)
        upload_text(sftp, local, dest)
    sftp.close()

    cmd = (
        f"chmod +x {ROOT}/lab-test-host-clean-run.sh {ROOT}/lab-mqtt-event-journal-multi-test.sh "
        f"{ROOT}/loadtest/mqtt-emqtt-bench.sh && "
        f"ISPF_LAB_ROOT={ROOT} ISPF_LAB_HTTP_PORT=8000 bash {ROOT}/lab-test-host-clean-run.sh"
    )
    print("running clean test (may take ~5-10 min)...")
    _, stdout, stderr = client.exec_command(cmd, timeout=3600)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    import sys
    sys.stdout.buffer.write(out.encode("utf-8", errors="replace"))
    sys.stdout.buffer.write(b"\n")
    if err:
        sys.stdout.buffer.write(b"ERR: ")
        sys.stdout.buffer.write(err[-4000:].encode("utf-8", errors="replace"))
        sys.stdout.buffer.write(b"\n")
    client.close()
    print("exit", code)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
