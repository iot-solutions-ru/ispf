#!/usr/bin/env python3
"""Lab: N devices on one MQTT topic, low publish rate, fan-out until journal queue."""
from __future__ import annotations

import sys
import time
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
LOG = f"{ROOT}/loadtest/journal-shared-topic-sweep.log"

UPLOADS = [
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-shared-topic-queue-sweep.sh", f"{ROOT}/lab-shared-topic-queue-sweep.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "loadtest-cleanup.py", f"{ROOT}/loadtest/loadtest-cleanup.py"),
    (DEPLOY / "repair-loadtest-device-structure.py", f"{ROOT}/loadtest/repair-loadtest-device-structure.py"),
]


def run(c, cmd, timeout=7200, quiet=False):
    if not quiet:
        print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if not quiet and out:
        print(out[-8000:], flush=True)
    if not quiet and err.strip():
        print("STDERR:", err[-1200:], flush=True)
    return code, out, err


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    devices = int(sys.argv[1]) if len(sys.argv) > 1 else 100
    skip_setup = "--skip-setup" in sys.argv

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
    run(c, "pkill -f '[/]lab-shared-topic-queue-sweep.sh' 2>/dev/null || true", timeout=30, quiet=True)
    run(
        c,
        f"chmod +x {ROOT}/lab-shared-topic-queue-sweep.sh {ROOT}/lab-emqtt-cleanup.sh "
        f"{ROOT}/loadtest/mqtt-emqtt-bench.sh",
        timeout=30,
    )
    log = f"{ROOT}/loadtest/journal-shared-topic-sweep-{devices}dev.log"
    run(c, f": > {log}", timeout=15, quiet=True)

    if not skip_setup:
        print(f"Purging old mqtt loadtest devices before seeding {devices}...", flush=True)
        run(
            c,
            f"cd {ROOT}/loadtest && python3 -u loadtest-cleanup.py "
            f"--base-url http://127.0.0.1:8000 --purge-mqtt --keep-background",
            timeout=600,
        )

    rates = "1000 5000 10000"
    launch = (
        f"nohup env LOG={log} DEVICES={devices} STOP_ON_QUEUE=true SKIP_DEVICE_SETUP={'true' if skip_setup else 'false'} "
        f"QUEUE_STOP_THRESHOLD=500 QUEUE_STOP_CONSECUTIVE=2 "
        f"SWEEP_PUBLISH_RATES='{rates}' "
        f"SHARED_TOPIC=ispf/loadtest/shared/temperature "
        f"SHARED_TOPIC_SHARDS=4 EMQTT_CPU_LIMIT=2.0 WARMUP=20 PHASE=20 "
        f"bash {ROOT}/lab-shared-topic-queue-sweep.sh </dev/null "
        f">>{ROOT}/loadtest/journal-shared-topic-sweep.nohup.log 2>&1 & echo sweep_pid=$!"
    )
    _, out, _ = run(c, launch, timeout=30)
    if "sweep_pid=" not in out:
        print("Failed to start sweep", flush=True)
        c.close()
        return 1

    print(f"Shared-topic sweep started ({devices} devices), log={log}...", flush=True)
    last_len = 0
    for attempt in range(900):
        _, tail, _ = run(c, f"wc -c < {log} 2>/dev/null || echo 0", quiet=True)
        size = int(tail.strip() or "0")
        if size > last_len:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {log} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            last_len = size
        _, proc, _ = run(
            c,
            f"pgrep -f '[/]lab-shared-topic-queue-sweep.sh' >/dev/null 2>&1 && echo running || echo done",
            quiet=True,
        )
        if "done" in proc:
            _, chunk, _ = run(c, f"tail -c +{last_len + 1} {log} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            break
        if attempt % 12 == 0:
            rows = run(c, f"grep -c SWEEP_ROW {log} 2>/dev/null || echo 0", quiet=True)[1].strip()
            print(f"... {attempt * 5}s elapsed, steps_done={rows}", flush=True)
        time.sleep(5)

    _, summary, _ = run(c, f"grep -A35 'SWEEP SUMMARY' {log} | tail -40", timeout=60)
    c.close()

    (REPO / f"tmp_lab_shared_topic_{devices}dev_result.txt").write_text(summary, encoding="utf-8")
    print("\n=== SHARED-TOPIC SWEEP ===", flush=True)
    print(summary, flush=True)
    print(f"Log: {log}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
