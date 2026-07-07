#!/usr/bin/env python3
"""Build skip-RAM + 256 threads, deploy to lab, run historian peak 16×32k."""
from __future__ import annotations

import paramiko
import re
import subprocess
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
STAGING = DEPLOY / "staging"
VERSION = "0.9.89"


def run_local(cmd: list[str]) -> None:
    print(">", " ".join(cmd[:8]), flush=True)
    subprocess.run(cmd, cwd=REPO, check=True)


def run(c, cmd, timeout=7200):
    print(">", cmd[:170], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-18000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    print("exit", code, flush=True)
    return code, out


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main() -> int:
    print(f"=== Build ISPF {VERSION} ===", flush=True)
    run_local([
        str(REPO / "gradlew.bat" if sys.platform == "win32" else "gradlew"),
        ":packages:ispf-server:bootJar",
        "-x", "test",
        f"-Pversion={VERSION}",
    ])
    jar = REPO / "packages" / "ispf-server" / "build" / "libs" / f"ispf-server-{VERSION}.jar"
    if not jar.exists():
        jars = list((REPO / "packages" / "ispf-server" / "build" / "libs").glob("ispf-server-*.jar"))
        jar = next(p for p in jars if "plain" not in p.name)
    STAGING.mkdir(parents=True, exist_ok=True)
    staging_jar = STAGING / "ispf-server.jar"
    staging_jar.write_bytes(jar.read_bytes())
    print(f"  staged {jar.name} -> {staging_jar}", flush=True)

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    print("Upload JAR + scripts...", flush=True)
    sftp.put(str(staging_jar), f"{ROOT}/ispf-server.jar")
    for name, remote in [
        ("lab-stress.env", f"{ROOT}/lab-stress.env"),
        ("lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
        ("lab-mqtt-historian-multi-test.sh", f"{ROOT}/lab-mqtt-historian-multi-test.sh"),
        ("setup-mqtt-historian-devices.py", f"{ROOT}/loadtest/setup-mqtt-historian-devices.py"),
        ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
        ("mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
        ("lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    ]:
        upload_text(sftp, DEPLOY / name, remote)
        print("  uploaded", name, flush=True)
    sftp.close()

    log = f"{ROOT}/loadtest/historian-ab-256-skipram.log"
    steps = [
        f"chmod +x {ROOT}/lab-mqtt-historian-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml restart mqtt",
        "sleep 3",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
        "sleep 20",
        f"curl -sf http://127.0.0.1:8000/api/v1/info | python3 -c \"import json,sys; d=json.load(sys.stdin); print(d.get('version'), d.get('environment'))\"",
        f"grep ISPF_DRIVER_MQTT_CALLBACK_THREADS {ROOT}/lab-stress.env",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        (
            f"cd {ROOT} && set -a && . lab-stress.env && set +a && "
            f"echo ISPF_DRIVER_MQTT_CALLBACK_THREADS=$ISPF_DRIVER_MQTT_CALLBACK_THREADS && "
            f"DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
            f"SKIP_DEVICE_SETUP=false NUMERIC_PAYLOAD=true "
            f"bash lab-mqtt-historian-multi-test.sh 2>&1 | tee {log}"
        ),
        f"docker stats --no-stream --format '{{{{.Name}}}} {{{{.CPUPerc}}}}' "
        f"$(docker compose -f {ROOT}/lab-test-host-compose.yml ps -q scylla) "
        f"$(docker compose -f {ROOT}/lab-test-host-compose.yml ps -q ispf-server)",
    ]
    for step in steps:
        code, out = run(c, step)
        if code != 0 and "tee" not in step and "env check" not in step and "curl -sf http" not in step:
            c.close()
            return code

    _, log_text = run(c, f"cat {log}", timeout=60)
    print("\n=== HISTORIAN A/B (256 threads + skip-RAM) ===")
    print(f"  Baseline (0.9.88, 64 threads, no skip-RAM): flushed ~143k/s, capture ~24%")
    metrics = {}
    for pat, label in [
        (r"ISPF ([0-9.]+)", "version"),
        (r"Historian minIntervalMs \(platform\): ([0-9]+)", "minIntervalMs"),
        (r"Mosquitto PUBLISH in:\s*([0-9.]+)", "mosquitto_in/s"),
        (r"Historian flushed \(metrics\):\s*([0-9.]+)", "flushed/s"),
        (r"Samples per device \(avg\):\s*([0-9.]+)", "per_device/s"),
        (r"Historian capture \(flushed/delivered\):\s*([0-9.]+)%", "capture%"),
    ]:
        m = re.search(pat, log_text)
        if m:
            metrics[label] = m.group(1)
            print(f"  {label}: {m.group(1)}")
    if "flushed/s" in metrics:
        try:
            delta = float(metrics["flushed/s"]) - 142.8
            print(f"  vs baseline flushed: {delta:+.1f}k/s")
        except ValueError:
            pass
    print(f"  Log: {log}")
    c.close()
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
