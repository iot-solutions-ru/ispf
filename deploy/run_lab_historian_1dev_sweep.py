#!/usr/bin/env python3
"""1-device historian rate sweep with increasing MQTT driver callbackThreads."""
from __future__ import annotations

import paramiko
import re
import sys
import time

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"

# (rate_per_device msg/s, callback_threads)
SWEEP = [
    (4_000, 64),
    (8_000, 128),
    (16_000, 256),
    (32_000, 512),
    (64_000, 1024),
]

WARMUP = 15
PHASE = 45


def run(c, cmd, timeout=7200):
    print(">", cmd[:200], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-12000:], flush=True)
    if err.strip() and code != 0:
        print("STDERR:", err[-600:], flush=True)
    print("exit", code, flush=True)
    return code, out


def parse_metrics(log: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for pat, key in [
        (r"Mosquitto PUBLISH in:\s*([0-9.]+)", "mqtt_in"),
        (r"Historian flushed \(metrics\):\s*([0-9.]+)", "flushed"),
        (r"Historian capture \(flushed/delivered\):\s*([0-9.]+)%", "capture"),
        (r"Samples \(Scylla COUNT\):\s*([0-9.]+)", "scylla_rate"),
    ]:
        m = re.search(pat, log)
        if m:
            out[key] = m.group(1)
    return out


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    run(c, f"bash {ROOT}/lab-emqtt-cleanup.sh")
    results: list[dict] = []

    for rate, threads in SWEEP:
        log = f"{ROOT}/loadtest/historian-1dev-sweep-{rate}-{threads}.log"
        print(f"\n========== rate={rate} callbackThreads={threads} ==========", flush=True)
        cmd = (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"bash {ROOT}/lab-emqtt-cleanup.sh && "
            f"DEVICES=1 RATE_PER_DEVICE={rate} WARMUP={WARMUP} PHASE={PHASE} "
            f"SKIP_DEVICE_SETUP=false NUMERIC_PAYLOAD=true "
            f"CALLBACK_THREADS={threads} CALLBACK_QUEUE_CAPACITY=500000 "
            f"bash lab-mqtt-historian-multi-test.sh 2>&1 | tee {log}"
        )
        code, log_text = run(c, cmd, timeout=7200)
        m = parse_metrics(log_text)
        row = {"rate": rate, "threads": threads, "log": log, **m}
        results.append(row)
        print(
            f"  -> mqtt={m.get('mqtt_in', '?')}/s flushed={m.get('flushed', '?')}/s "
            f"capture={m.get('capture', '?')}%",
            flush=True,
        )
        if code != 0:
            print("  step failed, stopping sweep", flush=True)
            break
        time.sleep(5)

    run(
        c,
        f"docker stats --no-stream --format '{{{{.Name}}}} {{{{.CPUPerc}}}}' "
        f"$(docker compose -f {ROOT}/lab-test-host-compose.yml ps -q ispf-server)",
    )
    c.close()

    print("\n=== SWEEP SUMMARY (1 device, ISPF 0.9.89) ===")
    print(f"{'target':>8} {'threads':>8} {'mqtt_in':>10} {'flushed':>10} {'capture%':>9}")
    best = None
    for r in results:
        print(
            f"{r['rate']:>8} {r['threads']:>8} "
            f"{r.get('mqtt_in', 'n/a'):>10} {r.get('flushed', 'n/a'):>10} "
            f"{r.get('capture', 'n/a'):>9}"
        )
        try:
            cap = float(r.get("capture", 0))
            flushed = float(r.get("flushed", 0))
            if cap >= 85 and (best is None or flushed > float(best.get("flushed", 0))):
                best = r
        except ValueError:
            pass

    if best:
        print(
            f"\nBest capture≥85%: target={best['rate']} threads={best['threads']} "
            f"flushed={best.get('flushed')}/s capture={best.get('capture')}%"
        )
    else:
        max_flushed = max(results, key=lambda r: float(r.get("flushed") or 0), default=None)
        if max_flushed:
            print(
                f"\nMax flushed: target={max_flushed['rate']} threads={max_flushed['threads']} "
                f"flushed={max_flushed.get('flushed')}/s capture={max_flushed.get('capture')}%"
            )
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
