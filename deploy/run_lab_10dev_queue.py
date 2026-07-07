#!/usr/bin/env python3
"""10-device event journal sweep on lab until eventJournalQueueSize > 0."""
from __future__ import annotations

import sys
import time
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
LOG = f"{ROOT}/loadtest/journal-10dev-queue-sweep-peak.log"

UPLOADS = [
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-10dev-queue-sweep.sh", f"{ROOT}/lab-10dev-queue-sweep.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
]


def run(c, cmd, timeout=7200, quiet=False):
    if not quiet:
        print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if not quiet and out:
        print(out[-6000:], flush=True)
    if not quiet and err.strip():
        print("STDERR:", err[-1200:], flush=True)
    return code, out, err


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    for local, remote in UPLOADS:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", local.name, flush=True)
    sftp.close()

    run(c, f"bash {ROOT}/lab-emqtt-cleanup.sh 2>/dev/null || true", timeout=60, quiet=True)
    run(c, f"chmod +x {ROOT}/lab-10dev-queue-sweep.sh {ROOT}/lab-emqtt-cleanup.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh", timeout=30)
    run(c, f": > {LOG}", timeout=15, quiet=True)

    launch = (
        f"nohup env LOG={LOG} DEVICES=10 STOP_ON_QUEUE=true SKIP_DEVICE_SETUP=true "
        f"QUEUE_STOP_THRESHOLD=100 QUEUE_STOP_CONSECUTIVE=2 "
        f"SWEEP_RATES='35000 40000 45000 50000 55000 60000 65000 70000' "
        f"EMQTT_SHARD_MAX=8 EMQTT_CPU_LIMIT=3.5 WARMUP=20 PHASE=90 "
        f"bash {ROOT}/lab-10dev-queue-sweep.sh </dev/null "
        f">>{ROOT}/loadtest/journal-10dev-queue-sweep.nohup.log 2>&1 & echo sweep_pid=$!"
    )
    _, out, _ = run(c, launch, timeout=30)
    if "sweep_pid=" not in out:
        print("Failed to start sweep", flush=True)
        c.close()
        return 1

    print("10-device queue sweep started, streaming log...", flush=True)
    last_len = 0
    for attempt in range(720):
        _, tail, _ = run(c, f"wc -c < {LOG} 2>/dev/null || echo 0", quiet=True)
        size = int(tail.strip() or "0")
        if size > last_len:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {LOG} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            last_len = size
        _, proc, _ = run(
            c,
            f"pgrep -f '[/]lab-10dev-queue-sweep.sh' >/dev/null 2>&1 && echo running || echo done",
            quiet=True,
        )
        if "done" in proc:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {LOG} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            break
        if attempt % 6 == 0:
            rows = run(c, f"grep -c SWEEP_ROW {LOG} 2>/dev/null || echo 0", quiet=True)[1].strip()
            emqtt = run(c, "docker ps -q --filter label=ispf.emqtt-bench=1 | wc -l", quiet=True)[1].strip()
            print(f"... {attempt * 5}s elapsed, steps_done={rows}, emqtt_shards={emqtt}", flush=True)
        time.sleep(5)

    _, summary, _ = run(c, f"grep -A30 'SWEEP SUMMARY' {LOG} | tail -35", timeout=60)
    c.close()

    (REPO / "tmp_lab_10dev_queue_result.txt").write_text(summary, encoding="utf-8")
    print("\n=== 10-DEVICE QUEUE SWEEP ===", flush=True)
    print(summary, flush=True)
    print(f"Log: {LOG}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
