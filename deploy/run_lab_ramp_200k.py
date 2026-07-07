#!/usr/bin/env python3
"""1-device MQTT ramp on lab — steps up to ~200k msg/s formula (RAMP_TO_200K)."""
from __future__ import annotations

import sys
import time
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
LOG = f"{ROOT}/loadtest/ispf-ramp-200k.log"

FILES = [
    (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-ispf-ramp-bench.sh", f"{ROOT}/lab-ispf-ramp-bench.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
]


def run(c, cmd, timeout=120, quiet=False):
    if not quiet:
        print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if not quiet and out:
        print(out[-4000:], flush=True)
    if not quiet and err.strip():
        print("STDERR:", err[-800:], flush=True)
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
        f"rm -f {LOG} && "
        f"nohup env LOG={LOG} RAMP_TO_200K=true EMQTT_CPU_LIMIT=2.5 "
        f"bash {ROOT}/lab-ispf-ramp-bench.sh </dev/null >>{ROOT}/loadtest/ispf-ramp-200k.nohup.log 2>&1 & "
        f"echo ramp_pid=$!"
    )
    _, out, _ = run(c, f"chmod +x {ROOT}/lab-ispf-ramp-bench.sh {ROOT}/lab-emqtt-cleanup.sh && {launch}", timeout=30)
    if "ramp_pid=" not in out:
        print("Failed to start ramp", flush=True)
        c.close()
        return 1

    print("1-device ramp → 200k started, streaming log...", flush=True)
    last_len = 0
    for attempt in range(160):
        _, tail, _ = run(c, f"wc -c < {LOG} 2>/dev/null || echo 0", quiet=True)
        size = int(tail.strip() or "0")
        if size > last_len:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {LOG} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            last_len = size
        _, proc, _ = run(
            c,
            f"grep -qF '=== DONE' {LOG} 2>/dev/null && echo done || "
            f"(pgrep -f '[/]lab-ispf-ramp-bench.sh' >/dev/null 2>&1 && echo running || echo done)",
            quiet=True,
        )
        if "done" in proc and size > 0:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {LOG} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            break
        if attempt % 4 == 0:
            emqtt = run(c, "docker ps -q --filter label=ispf.emqtt-bench=1 | wc -l", quiet=True)[1].strip()
            print(f"... {attempt * 15}s elapsed, emqtt_shards={emqtt}", flush=True)
        time.sleep(15)

    _, summary, _ = run(c, f"grep -A15 'RAMP SUMMARY' {LOG} | tail -18", timeout=60)
    c.close()

    (REPO / "tmp_lab_ramp_200k_result.txt").write_text(summary, encoding="utf-8")
    print("\n=== RAMP 1-DEVICE → 200k ===", flush=True)
    print(summary, flush=True)
    print(f"Log: {LOG}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
