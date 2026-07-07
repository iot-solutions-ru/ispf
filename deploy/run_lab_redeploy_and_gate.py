#!/usr/bin/env python3
"""Build, redeploy lab (server jar + driver packs), run 1×100k and 10×shared 10k tests."""
from __future__ import annotations

import subprocess
import sys
import tarfile
import time
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
STAGING = DEPLOY / "staging"
ROOT = "/home/iot-solutions/ispf"
VERSION = "0.9.93"
SHARED_TOPIC = "ispf/loadtest/shared/temperature"
TOPIC_1DEV = "ispf/loadtest/00001/temperature"


def run_local(cmd: list[str], timeout: int = 3600) -> None:
    print(">", " ".join(cmd), flush=True)
    proc = subprocess.run(cmd, cwd=REPO, timeout=timeout)
    if proc.returncode != 0:
        raise SystemExit(proc.returncode)


def pack_driver_packs() -> Path:
    src = REPO / "build" / "driver-packs"
    if not src.is_dir():
        raise SystemExit(f"Missing {src}; run syncAllDriverPacks")
    STAGING.mkdir(parents=True, exist_ok=True)
    tar_path = STAGING / "driver-packs.tar.gz"
    if tar_path.exists():
        tar_path.unlink()
    with tarfile.open(tar_path, "w:gz") as tar:
        for item in sorted(src.iterdir()):
            tar.add(item, arcname=item.name)
    print(f"packed {tar_path} ({tar_path.stat().st_size} bytes)", flush=True)
    return tar_path


def ssh_run(c: paramiko.SSHClient, cmd: str, timeout: int = 7200, quiet: bool = False) -> tuple[int, str, str]:
    if not quiet:
        print(">", cmd[:180], flush=True)
    _, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace")
    err = stderr.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    if not quiet:
        if out:
            print(out[-12000:], end="" if out.endswith("\n") else "\n", flush=True)
        if err.strip():
            print("STDERR:", err[-1500:], flush=True)
    return code, out, err


def upload_text(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def wait_sweep(c: paramiko.SSHClient, log: str, max_polls: int = 240) -> None:
    last_len = 0
    for attempt in range(max_polls):
        _, out, _ = ssh_run(c, f"wc -c < {log} 2>/dev/null || echo 0", quiet=True)
        size = int(out.strip() or "0")
        if size > last_len:
            _, chunk, _ = ssh_run(c, f"tail -c +{last_len + 1} {log} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            last_len = size
        _, proc, _ = ssh_run(
            c,
            "pgrep -f '[/]lab-shared-topic-queue-sweep.sh' >/dev/null 2>&1; echo exit=$?",
            quiet=True,
        )
        if "exit=1" in proc:
            _, chunk, _ = ssh_run(c, f"tail -c +{last_len + 1} {log} 2>/dev/null", quiet=True)
            if chunk:
                print(chunk, end="", flush=True)
            return
        if attempt % 12 == 0:
            print(f"... sweep poll {attempt * 5}s", flush=True)
        time.sleep(5)


def deploy_lab(c: paramiko.SSHClient, sftp: paramiko.SFTPClient) -> None:
    jar = STAGING / "ispf-server.jar"
    packs = STAGING / "driver-packs.tar.gz"
    if not jar.is_file() or not packs.is_file():
        raise SystemExit("Missing staging artifacts")

    print("Upload server jar + driver packs...", flush=True)
    sftp.put(str(jar), f"{ROOT}/ispf-server.jar")
    sftp.put(str(packs), f"{ROOT}/staging/driver-packs.tar.gz")

    uploads = [
        (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
        (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
        (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
        (DEPLOY / "lab-ispf-ramp-bench.sh", f"{ROOT}/lab-ispf-ramp-bench.sh"),
        (DEPLOY / "lab-gate-1dev-100k.sh", f"{ROOT}/lab-gate-1dev-100k.sh"),
        (DEPLOY / "lab-shared-topic-queue-sweep.sh", f"{ROOT}/lab-shared-topic-queue-sweep.sh"),
        (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
        (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
        (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
        (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
        (DEPLOY / "loadtest-cleanup.py", f"{ROOT}/loadtest/loadtest-cleanup.py"),
    ]
    for local, remote in uploads:
        upload_text(sftp, local, remote)
        print("  uploaded", local.name, flush=True)

    ssh_run(c, f"bash {ROOT}/lab-emqtt-cleanup.sh 2>/dev/null || true", timeout=60)
    code, _, _ = ssh_run(
        c,
        f"cd {ROOT} && rm -f data/drivers/.extracted && "
        f"mkdir -p data/drivers staging loadtest && "
        f"tar -xzf staging/driver-packs.tar.gz -C data/drivers && "
        f"chmod +x lab-emqtt-cleanup.sh lab-gate-1dev-100k.sh lab-shared-topic-queue-sweep.sh "
        f"loadtest/mqtt-emqtt-bench.sh && "
        f"docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
        timeout=300,
    )
    if code != 0:
        raise SystemExit("ispf-server recreate failed")

    ssh_run(
        c,
        f"cd {ROOT} && for i in $(seq 1 90); do "
        f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 "
        f"&& echo login_ok attempt=$i && curl -sf http://127.0.0.1:8000/api/v1/info && break; sleep 3; done",
        timeout=300,
    )

    ssh_run(
        c,
        "docker exec ispf-lab-ispf-server-1 sh -c '"
        "for t in /proc/1/task/*; do cat \"$t/comm\" 2>/dev/null; done | grep mqtt-ingress | sort | uniq -c | sort -rn | head -5'",
        timeout=60,
    )


def test_1dev_100k(c: paramiko.SSHClient) -> str:
    log = f"{ROOT}/loadtest/gate-1dev-100k.log"
    ssh_run(
        c,
        f"cd {ROOT}/loadtest && python3 -u loadtest-cleanup.py "
        f"--base-url http://127.0.0.1:8000 --purge-mqtt --keep-background",
        timeout=600,
    )
    ssh_run(
        c,
        f"cd {ROOT} && python3 -u loadtest/setup-mqtt-event-journal-devices.py "
        f"--devices 1 --base-url http://127.0.0.1:8000 --broker-url tcp://mqtt:1883 --bench-no-l0-coalesce",
        timeout=300,
    )
    code, _, _ = ssh_run(
        c,
        f"cd {ROOT} && LOG={log} TARGET_RATE=100000 EMQTT_CLIENTS=220 EMQTT_SHARDS=4 "
        f"EMQTT_CPU_LIMIT=3.0 WARMUP=20 PHASE=60 bash lab-gate-1dev-100k.sh",
        timeout=900,
    )
    _, summary, _ = ssh_run(
        c,
        f"grep -E 'GATE_ROW|RESULT |eventsFired|broker_rx|capture' {log} | tail -10",
        timeout=60,
    )
    (REPO / "tmp_lab_gate_1dev_100k.txt").write_text(summary, encoding="utf-8")
    if code != 0:
        print(f"test 1 exit {code}", flush=True)
    return summary


def test_10dev_shared_10k(c: paramiko.SSHClient) -> str:
    log = f"{ROOT}/loadtest/gate-10dev-shared-10k.log"
    ssh_run(c, f": > {log}", quiet=True)
    ssh_run(
        c,
        f"cd {ROOT}/loadtest && python3 -u loadtest-cleanup.py "
        f"--base-url http://127.0.0.1:8000 --purge-mqtt --keep-background",
        timeout=600,
    )
    launch = (
        f"nohup env LOG={log} DEVICES=10 STOP_ON_QUEUE=true SKIP_DEVICE_SETUP=false "
        f"QUEUE_STOP_THRESHOLD=500 QUEUE_STOP_CONSECUTIVE=2 "
        f"SWEEP_PUBLISH_RATES='10000' SHARED_TOPIC={SHARED_TOPIC} "
        f"SHARED_TOPIC_SHARDS=4 EMQTT_CPU_LIMIT=2.5 WARMUP=20 PHASE=60 "
        f"bash {ROOT}/lab-shared-topic-queue-sweep.sh </dev/null "
        f">>{ROOT}/loadtest/gate-10dev.nohup.log 2>&1 & echo started"
    )
    ssh_run(c, launch, timeout=30)
    wait_sweep(c, log, max_polls=180)
    _, summary, _ = ssh_run(
        c,
        f"grep -E 'STEP |RESULT |SWEEP_ROW|SWEEP SUMMARY|fanout|broker_rx|peak journal' {log} | tail -25",
        timeout=60,
    )
    (REPO / "tmp_lab_gate_10dev_10k.txt").write_text(summary, encoding="utf-8")
    return summary


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    print("=== BUILD ===", flush=True)
    run_local(
        [
            str(REPO / "gradlew.bat" if sys.platform == "win32" else "gradlew"),
            ":packages:ispf-server:bootJar",
            "syncAllDriverPacks",
            "-x",
            "test",
            f"-Pversion={VERSION}",
        ],
        timeout=3600,
    )
    jar_src = REPO / "packages" / "ispf-server" / "build" / "libs" / f"ispf-server-{VERSION}.jar"
    if not jar_src.is_file():
        raise SystemExit(f"Missing {jar_src}")
    STAGING.mkdir(parents=True, exist_ok=True)
    import shutil

    shutil.copy2(jar_src, STAGING / "ispf-server.jar")
    pack_driver_packs()

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()

    print("\n=== DEPLOY LAB ===", flush=True)
    deploy_lab(c, sftp)
    sftp.close()

    print("\n=== TEST 1: 1 device × 100k/s ===", flush=True)
    s1 = test_1dev_100k(c)

    print("\n=== cooldown 30s ===", flush=True)
    time.sleep(30)

    print("\n=== TEST 2: 10 devices shared topic × 10k/s publish ===", flush=True)
    s2 = test_10dev_shared_10k(c)

    ssh_run(
        c,
        "docker exec ispf-lab-ispf-server-1 sh -c '"
        "grep Threads /proc/1/status; "
        "for t in /proc/1/task/*; do cat \"$t/comm\" 2>/dev/null; done | grep mqtt-ingress | sort | uniq -c | sort -rn | head -5'",
        timeout=60,
    )
    c.close()

    print("\n========== GATE SUMMARY ==========", flush=True)
    print("--- 1 dev 100k ---", flush=True)
    print(s1, flush=True)
    print("--- 10 dev shared 10k ---", flush=True)
    print(s2, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
