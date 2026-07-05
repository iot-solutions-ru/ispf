#!/usr/bin/env python3
"""Single-device MQTT event-journal stress test on lab."""
from __future__ import annotations

import re
import sys
from pathlib import Path

import paramiko

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
LOG = f"{ROOT}/loadtest/stress-1dev-mqtt.log"

FILES = [
    (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
]


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


def main() -> int:
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

    wait_login = (
        f"cd {ROOT} && for i in $(seq 1 90); do "
        f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' | grep -q token "
        f"&& echo login_ok attempt=$i && break; sleep 3; done"
    )

    steps = [
        f"chmod +x {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/lab-emqtt-cleanup.sh "
        f"{ROOT}/loadtest/mqtt-emqtt-bench.sh",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d scylla mqtt ispf-server nginx",
        wait_login,
        "curl -sf http://127.0.0.1:8000/api/v1/info | python3 -m json.tool | head -15",
        (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"DEVICES=1 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=90 "
            f"SKIP_DEVICE_SETUP=false BENCH_NO_L0_COALESCE=true "
            f"bash lab-mqtt-event-journal-multi-test.sh 2>&1 | tee {LOG}"
        ),
        f"grep -E 'Events/s|Efficiency|Target:|Journal|version|mosquitto|queue|sync_fallback' {LOG} | tail -25",
        f"docker stats --no-stream --format '{{{{.Name}}}} {{{{.CPUPerc}}}} {{{{.MemUsage}}}}' "
        f"$(docker compose --env-file {ROOT}/lab-stress.env -f {ROOT}/lab-test-host-compose.yml ps -q)",
    ]

    log_all = []
    exit_code = 0
    for step in steps:
        code, out, err = run(c, step)
        log_all.append(out + err)
        if code != 0 and "tee" not in step and "grep" not in step and "docker stats" not in step:
            exit_code = code
            break
    c.close()

    text = "".join(log_all)
    (REPO / "tmp_stress_1dev_result.txt").write_text(text, encoding="utf-8")

    print("\n=== STRESS 1 DEVICE SUMMARY ===", flush=True)
    for pat, label in [
        (r'"version"\s*:\s*"([^"]+)"', "version"),
        (r"Events/s total[^\d]*([0-9.]+)", "events/s total"),
        (r"Efficiency[^\d]*([0-9.]+)%", "efficiency %"),
        (r"Mosquitto PUBLISH in:\s*([0-9.]+)", "mosquitto in/s"),
        (r"Journal events[^\d]*([0-9.]+)", "journal delta/s"),
    ]:
        m = re.search(pat, text)
        if m:
            print(f"  {label}: {m.group(1)}", flush=True)

    for line in text.splitlines():
        if any(k in line for k in ("Events/s", "Efficiency", "Target:", "login_ok", "Devices:")):
            print(line, flush=True)

    print(f"  Log: {LOG}", flush=True)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
