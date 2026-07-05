#!/usr/bin/env python3
"""1-device MQTT ramp on lab — continue from step N to ~1M msg/s formula."""
from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

import paramiko

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
LOG = f"{ROOT}/loadtest/ispf-ramp-1m.log"
PREV_LOG = f"{ROOT}/loadtest/ispf-ramp-200k.log"

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
    parser = argparse.ArgumentParser()
    parser.add_argument("--from-step", type=int, default=int(os.environ.get("RAMP_FROM_STEP", "12")))
    args = parser.parse_args()
    from_step = args.from_step

    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    for local, remote in FILES:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", local.name, flush=True)
    sftp.close()

    stop = (
        f"pkill -f '[/]lab-ispf-ramp-bench.sh' 2>/dev/null || true; "
        f"bash {ROOT}/lab-emqtt-cleanup.sh 2>/dev/null || true"
    )
    run(c, stop, timeout=30, quiet=True)

    seed = (
        f"cp {PREV_LOG} {LOG} 2>/dev/null || true; "
        f"echo '=== RESTART from step {from_step} $(date -Is) ===' >> {LOG}"
    )
    run(c, seed, timeout=30, quiet=True)
    _, start_tail, _ = run(c, f"wc -c < {LOG} 2>/dev/null || echo 0", quiet=True)
    start_len = int(start_tail.strip() or "0")

    run(c, f"chmod +x {ROOT}/lab-ispf-ramp-bench.sh {ROOT}/lab-emqtt-cleanup.sh", timeout=15, quiet=True)
    launch = (
        f"nohup env LOG={LOG} RAMP_TO_1M=true RAMP_FROM_STEP={from_step} RAMP_FAST=true COOLDOWN=2 "
        f"RAMP_STOP_ON_QUEUE=true EMQTT_CPU_LIMIT=3.0 bash {ROOT}/lab-ispf-ramp-bench.sh </dev/null "
        f">>{ROOT}/loadtest/ispf-ramp-1m.nohup.log 2>&1 & echo ramp_pid=$!"
    )
    _, out, _ = run(c, launch, timeout=15)
    if "ramp_pid=" not in out:
        print("Failed to start ramp", flush=True)
        c.close()
        return 1

    print(f"1-device ramp → 1M started (from step {from_step}), streaming log...", flush=True)
    last_len = start_len
    for attempt in range(360):
        _, tail, _ = run(c, f"wc -c < {LOG} 2>/dev/null || echo 0", quiet=True)
        size = int(tail.strip() or "0")
        if size > last_len:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {LOG} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            last_len = size
        _, proc, _ = run(
            c,
            f"pgrep -f '[/]lab-ispf-ramp-bench.sh' >/dev/null 2>&1 && echo running || echo done",
            quiet=True,
        )
        if "done" in proc:
            _, new_part, _ = run(c, f"tail -c +{start_len + 1} {LOG} 2>/dev/null", quiet=True)
            if (
                "RAMP_ABORT|queue" in new_part
                or "RAMP STOPPED" in new_part
                or "RAMP_ROW|18|" in new_part
                or (f"=== DONE" in new_part and f"RAMP_ROW|{from_step}|" in new_part)
            ):
                break
        if attempt % 4 == 0:
            rows = run(c, f"grep -c RAMP_ROW {LOG} 2>/dev/null || echo 0", quiet=True)[1].strip()
            emqtt = run(c, "docker ps -q --filter label=ispf.emqtt-bench=1 | wc -l", quiet=True)[1].strip()
            print(f"... {attempt * 10}s elapsed, steps_done={rows}, emqtt_shards={emqtt}", flush=True)
        time.sleep(10)

    _, summary, _ = run(c, f"grep -A20 'RAMP SUMMARY' {LOG} | tail -24", timeout=60)
    c.close()

    (REPO / "tmp_lab_ramp_1m_result.txt").write_text(summary, encoding="utf-8")
    print("\n=== RAMP 1-DEVICE → 1M ===", flush=True)
    print(summary, flush=True)
    print(f"Log: {LOG}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
