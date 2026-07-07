#!/usr/bin/env python3
"""Lab: 1× MQTT gateway orchestrator → N child sensor instances (dispatchTelemetry)."""
from __future__ import annotations

import sys
import time
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"

UPLOADS = [
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-gate-mqtt-gateway-orchestrator.sh", f"{ROOT}/lab-gate-mqtt-gateway-orchestrator.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    (DEPLOY / "setup-mqtt-gateway-orchestrator-devices.py", f"{ROOT}/loadtest/setup-mqtt-gateway-orchestrator-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "loadtest-cleanup.py", f"{ROOT}/loadtest/loadtest-cleanup.py"),
]


def run(c, cmd, timeout=7200, quiet=False):
    if not quiet:
        print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if not quiet and out:
        print(out[-12000:], flush=True)
    if not quiet and err.strip():
        print("STDERR:", err[-1200:], flush=True)
    return code, out, err


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    devices = int(sys.argv[1]) if len(sys.argv) > 1 else 16
    publish_rate = int(sys.argv[2]) if len(sys.argv) > 2 else devices

    shard_max = 4
    parallel_workers = 1
    warmup = 20
    phase = 20
    gate_timeout = 3600
    cleanup_timeout = 600
    emqtt_cpu = 2.0
    if devices >= 1000:
        shard_max = min(128, max(32, devices // 100))
        parallel_workers = 48
        warmup = 30
        phase = 60
        gate_timeout = 7200
        cleanup_timeout = 1800
        emqtt_cpu = 1.0
    elif devices >= 100:
        shard_max = min(32, max(8, devices // 20))
        parallel_workers = 16
        warmup = 25
        phase = 30
        gate_timeout = 5400
        cleanup_timeout = 900

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    for local, remote in UPLOADS:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", local.name, flush=True)
    sftp.close()

    run(c, f"chmod +x {ROOT}/lab-gate-mqtt-gateway-orchestrator.sh {ROOT}/lab-emqtt-cleanup.sh "
        f"{ROOT}/loadtest/mqtt-emqtt-bench.sh", timeout=30)

    log = f"{ROOT}/loadtest/gate-mqtt-gateway-{devices}dev-{publish_rate}.log"
    run(c, f": > {log}", timeout=15, quiet=True)

    print("Purging prior per-device MQTT drivers...", flush=True)
    run(
        c,
        f"cd {ROOT}/loadtest && python3 -u loadtest-cleanup.py "
        f"--base-url http://127.0.0.1:8000 --purge-mqtt --keep-background",
        timeout=cleanup_timeout,
    )

    lazy_flag = "LAZY_INSTANCES=true " if devices >= 500 else ""
    cmd = (
        f"env LOG={log} DEVICES={devices} PUBLISH_RATE={publish_rate} "
        f"WARMUP={warmup} PHASE={phase} INTERVAL_MS=1 EMQTT_CPU_LIMIT={emqtt_cpu} "
        f"SHARD_MAX={shard_max} PARALLEL_WORKERS={parallel_workers} "
        f"{lazy_flag}"
        f"bash {ROOT}/lab-gate-mqtt-gateway-orchestrator.sh"
    )
    print(
        f"Running gateway orchestrator gate ({devices} children, {publish_rate}/s total, "
        f"~{publish_rate / devices:.2g}/device, shards={shard_max})...",
        flush=True,
    )
    code, out, _ = run(c, cmd, timeout=gate_timeout)
    if code != 0:
        print(f"gate exit code={code}", flush=True)

    _, row, _ = run(c, f"grep GATE_ROW {log} | tail -1", timeout=30, quiet=True)
    _, version, _ = run(
        c,
        "curl -sf http://127.0.0.1:8000/api/v1/info | python3 -c \"import json,sys; print(json.load(sys.stdin).get('version'))\"",
        timeout=30,
        quiet=True,
    )
    c.close()

    result_path = REPO / f"tmp_lab_gateway_orchestrator_{devices}dev_result.txt"
    result_path.write_text(f"version={version.strip()}\n{row.strip()}\n\n{out[-4000:]}", encoding="utf-8")
    print("\n=== GATEWAY ORCHESTRATOR ===", flush=True)
    print(f"version={version.strip()}", flush=True)
    print(row.strip(), flush=True)
    print(f"Log: {log}", flush=True)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
