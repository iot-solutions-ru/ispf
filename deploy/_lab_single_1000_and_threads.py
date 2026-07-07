#!/usr/bin/env python3
"""Cooldown, thread histogram, single 1000/s shared-topic step (--skip-setup), thread histogram again."""
from __future__ import annotations

import sys
import time

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"


def run(c: paramiko.SSHClient, cmd: str, timeout: int = 120, quiet: bool = False) -> str:
    if not quiet:
        print(">", cmd[:180], flush=True)
    _, stdout, _ = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    if not quiet and out:
        print(out, end="" if out.endswith("\n") else "\n", flush=True)
    return out


def thread_hist(c: paramiko.SSHClient, label: str) -> None:
    print(f"\n=== THREAD HIST {label} ===", flush=True)
    run(
        c,
        r"""docker exec ispf-lab-ispf-server-1 sh -c '
grep Threads /proc/1/status
echo "--- ingress thread names ---"
for t in /proc/1/task/*; do cat "$t/comm" 2>/dev/null; done | grep -E "mqtt-ingress|ingress" | sort | uniq -c | sort -rn
echo "--- top 15 ---"
for t in /proc/1/task/*; do cat "$t/comm" 2>/dev/null; done | sort | uniq -c | sort -rn | head -15
'""",
    )


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)

    print("Cooldown 60s...", flush=True)
    time.sleep(60)
    thread_hist(c, "after cooldown (idle)")

    log = f"{ROOT}/loadtest/journal-shared-topic-sweep-16dev-1000only.log"
    run(c, f": > {log}", quiet=True)
    launch = (
        f"nohup env LOG={log} DEVICES=16 STOP_ON_QUEUE=true SKIP_DEVICE_SETUP=true "
        f"QUEUE_STOP_THRESHOLD=500 QUEUE_STOP_CONSECUTIVE=2 "
        f"SWEEP_PUBLISH_RATES='1000' SHARED_TOPIC=ispf/loadtest/shared/temperature "
        f"SHARED_TOPIC_SHARDS=4 EMQTT_CPU_LIMIT=2.0 WARMUP=20 PHASE=20 "
        f"bash {ROOT}/lab-shared-topic-queue-sweep.sh </dev/null "
        f">>{ROOT}/loadtest/single-1000.nohup.log 2>&1 & echo sweep_started"
    )
    run(c, launch, timeout=30)

    for attempt in range(120):
        proc = run(
            c,
            "pgrep -f '[/]lab-shared-topic-queue-sweep.sh' >/dev/null 2>&1; "
            "echo exit=$?",
            quiet=True,
        ).strip()
        if "exit=1" in proc:
            break
        if attempt % 6 == 0:
            print(f"... waiting sweep ({attempt * 5}s)", flush=True)
        time.sleep(5)

    print("\n=== SWEEP OUTPUT (1000/s only) ===", flush=True)
    run(c, f"grep -E 'STEP |RESULT |SWEEP_ROW|SWEEP SUMMARY|fanout|broker_rx' {log}")

    thread_hist(c, "after 1000/s step")

    run(
        c,
        "TOKEN=$(curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        "-H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin\"}' "
        "| python3 -c 'import json,sys; print(json.load(sys.stdin)[\"token\"])') && "
        "curl -sf 'http://127.0.0.1:8000/api/v1/drivers/runtime/status?"
        "devicePath=root.platform.devices.loadtest-mqtt-dev-00001' "
        "-H \"Authorization: Bearer $TOKEN\"",
    )
    run(
        c,
        "docker exec ispf-lab-ispf-server-1 printenv | grep -E 'ISPF_DRIVER_MQTT' | sort",
    )
    run(
        c,
        "docker exec ispf-lab-ispf-server-1 sh -c "
        "'jar tf /app/ispf-server.jar 2>/dev/null | grep DriverIngressFifo | head -3'",
    )
    run(
        c,
        "TOKEN=$(curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        "-H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin\"}' "
        "| python3 -c 'import json,sys; print(json.load(sys.stdin)[\"token\"])') && "
        "curl -sf 'http://127.0.0.1:8000/api/v1/objects/root.platform.devices."
        "loadtest-mqtt-dev-00001/variables/driverConfigJson' "
        "-H \"Authorization: Bearer $TOKEN\"",
    )
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
