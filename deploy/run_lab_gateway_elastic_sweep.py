#!/usr/bin/env python3
"""Lab: gateway orchestrator 16@10k — elastic max32 vs max64 + historian writer A/B."""
from __future__ import annotations

import re
import subprocess
import sys
import time
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
STAGING = DEPLOY / "staging"
VERSION = "0.9.97"

PROFILES: dict[str, dict[str, str]] = {
    "pipeline-max32": {
        "ISPF_MQTT_GATEWAY_INGRESS_DISPATCH_THREADS_MAX": "32",
        "ISPF_RUNTIME_TELEMETRY_INGRESS_WORKERS_MAX": "32",
        "ISPF_RUNTIME_TELEMETRY_COALESCE_SCHEDULER_THREADS_MAX": "32",
        "ISPF_VARIABLE_HISTORY_WRITER_THREADS_MAX": "32",
    },
    "pipeline-max64": {
        "ISPF_MQTT_GATEWAY_INGRESS_DISPATCH_THREADS_MAX": "64",
        "ISPF_RUNTIME_TELEMETRY_INGRESS_WORKERS_MAX": "64",
        "ISPF_RUNTIME_TELEMETRY_COALESCE_SCHEDULER_THREADS_MAX": "64",
        "ISPF_VARIABLE_HISTORY_WRITER_THREADS_MAX": "64",
    },
    "hist-max24-pipe64": {
        "ISPF_MQTT_GATEWAY_INGRESS_DISPATCH_THREADS_MAX": "64",
        "ISPF_RUNTIME_TELEMETRY_INGRESS_WORKERS_MAX": "64",
        "ISPF_RUNTIME_TELEMETRY_COALESCE_SCHEDULER_THREADS_MAX": "64",
        "ISPF_VARIABLE_HISTORY_WRITER_THREADS_MAX": "24",
    },
}


UPLOADS = [
    (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
    (DEPLOY / "lab-gate-mqtt-gateway-orchestrator.sh", f"{ROOT}/lab-gate-mqtt-gateway-orchestrator.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    (DEPLOY / "setup-mqtt-gateway-orchestrator-devices.py", f"{ROOT}/loadtest/setup-mqtt-gateway-orchestrator-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "loadtest-cleanup.py", f"{ROOT}/loadtest/loadtest-cleanup.py"),
]


def run_local(cmd: list[str]) -> None:
    print(">", " ".join(cmd[:10]), flush=True)
    subprocess.run(cmd, cwd=REPO, check=True)


def run(c, cmd: str, timeout: int = 3600) -> tuple[int, str]:
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-8000:], flush=True)
    if err.strip():
        print("STDERR:", err[-800:], flush=True)
    return code, out


def upload_text(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def merge_env(base: Path, overrides: dict[str, str]) -> str:
    lines: list[str] = []
    seen: set[str] = set()
    if base.is_file():
        for raw in base.read_text(encoding="utf-8").splitlines():
            if not raw.strip() or raw.strip().startswith("#"):
                lines.append(raw)
                continue
            key = raw.split("=", 1)[0].strip()
            if key in overrides:
                lines.append(f"{key}={overrides[key]}")
                seen.add(key)
            else:
                lines.append(raw)
    for key, value in overrides.items():
        if key not in seen:
            lines.append(f"{key}={value}")
    return "\n".join(lines) + "\n"


def parse_gate_row(text: str) -> str:
    for line in reversed(text.splitlines()):
        if line.startswith("GATE_ROW|"):
            return line.strip()
    return ""


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    print(f"=== Build ISPF {VERSION} ===", flush=True)
    run_local([
        str(REPO / "gradlew.bat" if sys.platform == "win32" else "gradlew"),
        ":packages:ispf-server:bootJar",
        "-x", "test",
        f"-Pversion={VERSION}",
    ])
    jar = REPO / "packages" / "ispf-server" / "build" / "libs" / f"ispf-server-{VERSION}.jar"
    if not jar.is_file():
        jar = next(p for p in (REPO / "packages" / "ispf-server" / "build" / "libs").glob("ispf-server-*.jar") if "plain" not in p.name)
    STAGING.mkdir(parents=True, exist_ok=True)
    staging_jar = STAGING / "ispf-server.jar"
    staging_jar.write_bytes(jar.read_bytes())

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    sftp.put(str(staging_jar), f"{ROOT}/ispf-server.jar")
    for local, remote in UPLOADS:
        upload_text(sftp, local, remote)
        print("  uploaded", local.name, flush=True)
    sftp.close()

    run(c, f"chmod +x {ROOT}/lab-gate-mqtt-gateway-orchestrator.sh {ROOT}/lab-emqtt-cleanup.sh "
        f"{ROOT}/loadtest/mqtt-emqtt-bench.sh", timeout=30)

    results: list[str] = []
    base_env = DEPLOY / "lab-stress.env"

    for profile, overrides in PROFILES.items():
        print(f"\n========== PROFILE {profile} ==========", flush=True)
        merged = merge_env(base_env, overrides)
        remote_env = f"{ROOT}/lab-stress.env"
        with c.open_sftp().file(remote_env, "w") as f:
            f.write(merged.replace("\r\n", "\n"))

        code, _ = run(
            c,
            f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
            timeout=300,
        )
        if code != 0:
            results.append(f"{profile}\tDEPLOY_FAILED")
            continue

        run(
            c,
            f"cd {ROOT} && for i in $(seq 1 60); do "
            f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
            f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 "
            f"&& echo login_ok && break; sleep 3; done",
            timeout=200,
        )
        time.sleep(5)

        log = f"{ROOT}/loadtest/gate-sweep-{profile}.log"
        gate_cmd = (
            f"env LOG={log} DEVICES=16 PUBLISH_RATE=10000 WARMUP=20 PHASE=20 INTERVAL_MS=1 "
            f"EMQTT_CPU_LIMIT=2.0 SHARD_MAX=4 bash {ROOT}/lab-gate-mqtt-gateway-orchestrator.sh"
        )
        code, out = run(c, gate_cmd, timeout=3600)
        _, version = run(
            c,
            "curl -sf http://127.0.0.1:8000/api/v1/info | python3 -c \"import json,sys; print(json.load(sys.stdin).get('version'))\"",
            timeout=30,
        )
        _, row = run(c, f"grep GATE_ROW {log} | tail -1", timeout=30)
        row = row.strip() or parse_gate_row(out)
        results.append(f"{profile}\tversion={version.strip()}\t{row}")
        print(f"RESULT {profile}: {row}", flush=True)

    summary_path = REPO / "tmp_lab_gateway_elastic_sweep.tsv"
    header = "profile\tversion\tGATE_ROW"
    summary_path.write_text(header + "\n" + "\n".join(results) + "\n", encoding="utf-8")

    print("\n=== GATEWAY ELASTIC SWEEP SUMMARY ===", flush=True)
    print(header, flush=True)
    for line in results:
        print(line, flush=True)
        if "GATE_ROW|" in line:
            parts = line.split("\t")[-1].split("|")
            if len(parts) >= 9:
                try:
                    ratio = float(parts[8])
                    print(f"  -> dispatch_ratio={ratio:.2f} historian/s={parts[6]} broker/s={parts[7]}", flush=True)
                except ValueError:
                    pass
    print(f"\nSaved: {summary_path}", flush=True)
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
