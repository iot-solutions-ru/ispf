#!/usr/bin/env python3
"""Upload and run progressive 1-device MQTT ramp test on lab."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
LOG = f"{ROOT}/loadtest/ispf-ramp.log"

FILES = [
    (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-ispf-ramp-bench.sh", f"{ROOT}/lab-ispf-ramp-bench.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
]


def run(c, cmd, timeout=7200):
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-18000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2000:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    for local, remote in FILES:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", local.name, flush=True)
    sftp.close()

    launch = (
        f"nohup bash {ROOT}/lab-ispf-ramp-bench.sh > {ROOT}/loadtest/ispf-ramp.nohup.log 2>&1 & echo ramp_pid=$!"
    )
    _, out, _ = run(c, f"chmod +x {ROOT}/lab-ispf-ramp-bench.sh {ROOT}/lab-emqtt-cleanup.sh && {launch}")
    if "ramp_pid=" not in out:
        print("Failed to start ramp", flush=True)
        c.close()
        return 1

    print("Ramp started in background, streaming log tail...", flush=True)
    last_len = 0
    for attempt in range(120):
        _, tail, _ = run(c, f"wc -c < {LOG} 2>/dev/null || echo 0", timeout=30)
        size = int(tail.strip() or "0")
        if size > last_len:
            _, chunk, _ = run(
                c,
                f"tail -c +{last_len + 1} {LOG} 2>/dev/null",
                timeout=30,
            )
            if chunk:
                print(chunk, end="", flush=True)
            last_len = size
        proc = run(c, f"pgrep -f '{ROOT}/lab-ispf-ramp-bench.sh' >/dev/null && echo running || echo done", timeout=30)[1]
        if "done" in proc:
            break
        if attempt % 4 == 0:
            print(f"... still running ({attempt * 15}s)", flush=True)
        import time
        time.sleep(15)

    _, out, err = run(c, f"grep -A10 'RAMP SUMMARY' {LOG} | tail -12", timeout=60)
    c.close()

    text = out + err
    (REPO / "tmp_lab_ramp_result.txt").write_text(text, encoding="utf-8")

    print("\n=== LAB RAMP SUMMARY ===", flush=True)
    print(text, flush=True)
    print(f"  Log: {LOG}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
