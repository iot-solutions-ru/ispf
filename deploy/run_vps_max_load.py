#!/usr/bin/env python3
"""Deploy VPS event-journal multi-device bench scripts and run max-load test."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
REMOTE = "root@ispf.iot-solutions.ru"
LOADTEST = "/opt/ispf/loadtest"
LOG = f"{LOADTEST}/vps-max-load-peak.log"


def scp(local: Path, remote: str) -> None:
    data = local.read_bytes().replace(b"\r\n", b"\n")
    tmp = REPO / "tmp_scp_upload"
    tmp.write_bytes(data)
    subprocess.run(["scp", "-o", "BatchMode=yes", str(tmp), f"{REMOTE}:{remote}"], check=True)
    tmp.unlink(missing_ok=True)


def ssh(cmd: str, timeout: int = 7200) -> tuple[int, str]:
    print(">", cmd[:160], flush=True)
    r = subprocess.run(
        ["ssh", "-o", "BatchMode=yes", REMOTE, cmd],
        capture_output=True,
        text=True,
        timeout=timeout,
        encoding="utf-8",
        errors="replace",
    )
    out = (r.stdout or "") + (r.stderr or "")
    if r.stdout:
        print(r.stdout[-14000:], flush=True)
    if r.stderr.strip():
        print("STDERR:", r.stderr[-2000:], flush=True)
    print("exit", r.returncode, flush=True)
    return r.returncode, out


def parse_rate(text: str, label: str) -> float | None:
    m = re.search(rf"{re.escape(label)}:\s*([0-9.]+)", text)
    return float(m.group(1)) if m else None


def main() -> int:
    files = [
        ("vps-mqtt-event-journal-multi-test.sh", f"{LOADTEST}/vps-mqtt-event-journal-multi-test.sh"),
        ("lab-emqtt-cleanup.sh", f"{LOADTEST}/vps-emqtt-cleanup.sh"),
        ("mqtt-emqtt-bench.sh", f"{LOADTEST}/mqtt-emqtt-bench.sh"),
        ("setup-mqtt-event-journal-devices.py", f"{LOADTEST}/setup-mqtt-event-journal-devices.py"),
        ("mqtt_loadtest_lib.py", f"{LOADTEST}/mqtt_loadtest_lib.py"),
    ]
    for name, remote in files:
        scp(DEPLOY / name, remote)
        print("  uploaded", name, flush=True)

    steps = [
        f"chmod +x {LOADTEST}/vps-mqtt-event-journal-multi-test.sh {LOADTEST}/vps-emqtt-cleanup.sh {LOADTEST}/mqtt-emqtt-bench.sh",
        # Mosquitto $SYS counters
        "grep -q sys_interval /opt/ispf/mqtt/mosquitto.conf 2>/dev/null || "
        "echo 'sys_interval 1' >> /opt/ispf/mqtt/mosquitto.conf",
        "docker rm -f ispf-mqtt-loadtest 2>/dev/null; "
        "docker run -d --name ispf-mqtt-loadtest --restart unless-stopped "
        "-p 127.0.0.1:1883:1883 -v /opt/ispf/mqtt/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro "
        "eclipse-mosquitto:2",
        "sleep 2",
        # Stop orphaned emqtt
        f"bash {LOADTEST}/vps-emqtt-cleanup.sh",
        # Peak test (same params as lab max-load)
        (
            f"DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
            f"SKIP_DEVICE_SETUP=false EMQTT_SHARD_MAX=8 EMQTT_CPU_LIMIT=1.5 "
            f"bash {LOADTEST}/vps-mqtt-event-journal-multi-test.sh 2>&1 | tee {LOG}"
        ),
    ]
    full_out = ""
    for step in steps:
        code, out = ssh(step)
        full_out += out
        if code != 0 and "tee" not in step:
            return code

    _, log_text = ssh(f"cat {LOG}", timeout=120)

    print("\n" + "=" * 60)
    print("VPS MAX LOAD SUMMARY (ispf.iot-solutions.ru)")
    print("=" * 60)
    fired = parse_rate(log_text, "ISPF eventsFired (ingress)")
    meta = parse_rate(log_text, "Journal (Scylla meta)")
    per_dev = parse_rate(log_text, "Journal per device (avg)")
    meta_fired = None
    m = re.search(r"Scylla meta vs eventsFired:\s*([0-9.]+)%", log_text)
    if m:
        meta_fired = float(m.group(1))
    for label, val in [
        ("Journal (eventsFired)", fired),
        ("Scylla meta", meta),
        ("Per device", per_dev),
        ("meta vs eventsFired", meta_fired),
    ]:
        if val is not None:
            suffix = "%" if label == "meta vs eventsFired" else " events/s"
            print(f"  {label}: {val:,.1f}{suffix}")
    print(f"\n  Full log: {REMOTE}:{LOG}")
    print("=" * 60)
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
