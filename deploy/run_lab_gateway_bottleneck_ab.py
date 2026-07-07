#!/usr/bin/env python3
"""Lab: isolate gateway orchestrator bottleneck (L3 / bus / historian fast path)."""
from __future__ import annotations

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
VERSION = "0.9.98"

PROFILES: dict[str, dict[str, str]] = {
    "baseline-l3-on-bus-sync": {},
    "l3-off": {
        "ISPF_RUNTIME_TELEMETRY_INGRESS_QUEUE_ENABLED": "false",
    },
    "bus-async-on": {
        "ISPF_OBJECT_CHANGE_ASYNC_ENABLED": "true",
    },
    "fast-path-fix": {
        "ISPF_OBJECT_CHANGE_ASYNC_ENABLED": "false",
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
    print(">", " ".join(cmd[:12]), flush=True)
    subprocess.run(cmd, cwd=REPO, check=True)


def run(c, cmd: str, timeout: int = 3600) -> tuple[int, str]:
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-6000:], flush=True)
    if err.strip():
        print("STDERR:", err[-600:], flush=True)
    return code, out


def merge_env(base: Path, overrides: dict[str, str]) -> str:
    lines: list[str] = []
    seen: set[str] = set()
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


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    print(f"=== Build ISPF {VERSION} (historian fast-path fix) ===", flush=True)
    gradlew = "gradlew.bat" if sys.platform == "win32" else "gradlew"
    run_local([str(REPO / gradlew), ":packages:ispf-server:bootJar", "-x", "test", f"-Pversion={VERSION}"])
    jar = REPO / "packages" / "ispf-server" / "build" / "libs" / f"ispf-server-{VERSION}.jar"
    STAGING.mkdir(parents=True, exist_ok=True)
    staging_jar = STAGING / "ispf-server.jar"
    staging_jar.write_bytes(jar.read_bytes())

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    sftp.put(str(staging_jar), f"{ROOT}/ispf-server.jar")
    for local, remote in UPLOADS:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    sftp.close()
    run(c, f"chmod +x {ROOT}/lab-gate-mqtt-gateway-orchestrator.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh", timeout=30)

    base_env = DEPLOY / "lab-stress.env"
    results: list[str] = []

    for profile, overrides in PROFILES.items():
        print(f"\n========== {profile} ==========", flush=True)
        merged = merge_env(base_env, overrides)
        with c.open_sftp().file(f"{ROOT}/lab-stress.env", "w") as f:
            f.write(merged.replace("\r\n", "\n"))

        code, _ = run(
            c,
            f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
            timeout=300,
        )
        if code != 0:
            results.append(f"{profile}\tDEPLOY_FAIL")
            continue

        run(
            c,
            f"cd {ROOT} && for i in $(seq 1 60); do "
            f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
            f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 "
            f"&& break; sleep 3; done",
            timeout=200,
        )
        time.sleep(5)

        log = f"{ROOT}/loadtest/gate-bottleneck-{profile}.log"
        gate_cmd = (
            f"env LOG={log} DEVICES=16 PUBLISH_RATE=10000 WARMUP=15 PHASE=15 INTERVAL_MS=1 "
            f"EMQTT_CPU_LIMIT=2.0 SHARD_MAX=4 bash {ROOT}/lab-gate-mqtt-gateway-orchestrator.sh"
        )
        run(c, gate_cmd, timeout=3600)
        _, row = run(c, f"grep GATE_ROW {log} | tail -1", timeout=30)
        results.append(f"{profile}\t{row.strip()}")

    summary = REPO / "tmp_lab_gateway_bottleneck_ab.tsv"
    summary.write_text("profile\tGATE_ROW\n" + "\n".join(results) + "\n", encoding="utf-8")
    print("\n=== BOTTLENECK A/B ===", flush=True)
    for line in results:
        print(line, flush=True)
        if "GATE_ROW|" in line:
            parts = line.split("\t")[1].split("|")
            if len(parts) >= 9:
                print(f"  ratio={parts[8]} hist/s={parts[6]} broker/s={parts[7]}", flush=True)
    print(f"\nSaved: {summary}", flush=True)
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
