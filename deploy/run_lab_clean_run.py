#!/usr/bin/env python3
"""Wipe lab volumes, restart stack, run sustained 16×5200 journal benchmark."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"

UPLOADS = [
    ("lab-stress.env", f"{ROOT}/lab-stress.env"),
    ("lab-test-host-clean-run.sh", f"{ROOT}/lab-test-host-clean-run.sh"),
    ("lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
    ("lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    ("setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    ("mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
]


def run(c, cmd, timeout=7200):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-12000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2000:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    for name, remote in UPLOADS:
        with sftp.file(remote, "w") as f:
            f.write((DEPLOY / name).read_bytes().replace(b"\r\n", b"\n"))
        print("  uploaded", name, flush=True)
    sftp.close()

    code, out, _ = run(
        c,
        f"chmod +x {ROOT}/lab-test-host-clean-run.sh {ROOT}/lab-mqtt-event-journal-multi-test.sh "
        f"{ROOT}/lab-emqtt-cleanup.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh && "
        f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
        f"DEVICES=16 RATE_PER_DEVICE=${{STRESS_SUSTAINED_RATE_PER_DEVICE:-5200}} "
        f"WARMUP=25 PHASE=90 bash lab-test-host-clean-run.sh",
        timeout=7200,
    )
    c.close()
    Path(REPO / "tmp_clean_run_result.txt").write_text(out, encoding="utf-8")
    return 0 if code == 0 else code


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
