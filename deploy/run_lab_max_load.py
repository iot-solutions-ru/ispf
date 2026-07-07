#!/usr/bin/env python3
"""Peak journal test with new metrics + max sustainable load estimate."""
from __future__ import annotations

import paramiko
import re
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"


def run(c, cmd, timeout=7200):
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-14000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2000:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def parse_rate(text: str, label: str) -> float | None:
    m = re.search(rf"{re.escape(label)}:\s*([0-9.]+)", text)
    return float(m.group(1)) if m else None


def parse_pct(text: str, label: str) -> float | None:
    m = re.search(rf"{re.escape(label)}:\s*([0-9.]+|n/a)%?", text)
    if not m or m.group(1) == "n/a":
        return None
    return float(m.group(1))


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    for local, remote in [
        (DEPLOY / "lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
        (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
        (DEPLOY / "mosquitto" / "mosquitto.conf", f"{ROOT}/mqtt/mosquitto.conf"),
        (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    ]:
        upload_text(sftp, local, remote)
        print("  uploaded", local.name, flush=True)
    sftp.close()

    peak_log = f"{ROOT}/loadtest/max-load-peak.log"
    steps = [
        f"chmod +x {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh {ROOT}/lab-emqtt-cleanup.sh",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        # Peak probe: max emqtt pressure (8 shards, 512k target)
        (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
            f"SKIP_DEVICE_SETUP=false AUTO_CALIBRATE=false EMQTT_SHARD_MAX=8 "
            f"bash lab-mqtt-event-journal-multi-test.sh 2>&1 | tee {peak_log}"
        ),
    ]
    peak_out = ""
    for step in steps:
        code, out, _ = run(c, step, timeout=7200)
        peak_out += out
        if code != 0 and "tee" not in step:
            c.close()
            return code

    # Fetch full log for parsing
    _, log_text, _ = run(c, f"cat {peak_log}", timeout=120)

    fired = parse_rate(log_text, "ISPF eventsFired \\(ingress\\)")
    if fired is None:
        fired = parse_rate(log_text, "ISPF eventsFired (ingress)")
    meta = parse_rate(log_text, "Journal \\(Scylla meta\\)")
    if meta is None:
        meta = parse_rate(log_text, "Journal (Scylla meta)")
    flushed = parse_rate(log_text, "Journal flushed \\(metrics\\)")
    if flushed is None:
        flushed = parse_rate(log_text, "Journal flushed (metrics)")
    mqtt_tx = parse_rate(log_text, "Mosquitto delivered \\(broker\\)")
    if mqtt_tx is None:
        mqtt_tx = parse_rate(log_text, "Mosquitto delivered (broker)")
    mqtt_rx = parse_rate(log_text, "Mosquitto PUBLISH in \\(broker\\)")
    if mqtt_rx is None:
        mqtt_rx = parse_rate(log_text, "Mosquitto PUBLISH in (broker)")
    per_dev = parse_rate(log_text, "Journal per device \\(avg\\)")
    if per_dev is None:
        per_dev = parse_rate(log_text, "Journal per device (avg)")
    capture = parse_pct(log_text, "ISPF capture \\(fired/delivered\\)")
    meta_fired = parse_pct(log_text, "Scylla meta vs eventsFired")

    _, stats, _ = run(
        c,
        "docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' "
        f"$(docker compose --env-file {ROOT}/lab-stress.env -f {ROOT}/lab-test-host-compose.yml ps -q ispf-server scylla mqtt)",
        timeout=60,
    )

    print("\n" + "=" * 60)
    print("MAX LOAD SUMMARY (lab 84.42.21.226, 16 devices, 8 emqtt shards)")
    print("=" * 60)
    if fired:
        print(f"  Max sustained journal (eventsFired):  {fired:,.0f} events/s")
        print(f"  Max per device:                       {per_dev or fired/16:,.0f} events/s")
    if meta:
        print(f"  Scylla meta rate:                     {meta:,.0f} events/s")
    if mqtt_tx:
        print(f"  Mosquitto delivered to subscribers:   {mqtt_tx:,.0f} msg/s")
    if mqtt_rx:
        print(f"  Mosquitto PUBLISH in (all clients):   {mqtt_rx:,.0f} msg/s")
    if capture is not None:
        print(f"  ISPF capture (fired/delivered):       {capture:.1f}%")
    if meta_fired is not None:
        print(f"  Scylla meta vs eventsFired:           {meta_fired:.1f}%")
    print("\n  Container CPU after test:")
    for line in stats.strip().splitlines():
        print(f"    {line}")
    print("\n  Interpretation:")
    if fired and capture and capture >= 95:
        print(f"    Messages not lost on ISPF path → max ~{fired:,.0f} events/s journal")
        print(f"    (= {fired/16:,.0f}/device × 16 devices on this host config)")
    elif fired and mqtt_tx and mqtt_tx > 0:
        cap = 100.0 * (fired or 0) / mqtt_tx
        print(f"    ISPF capture vs broker delivered: ~{cap:.0f}%")
        print(f"    Journal ceiling ~{fired:,.0f} events/s (broker may deliver more at higher emqtt pressure)")
    print(f"\n  Full log: {peak_log}")
    print("=" * 60)

    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
