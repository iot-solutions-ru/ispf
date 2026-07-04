#!/usr/bin/env python3
"""Deploy cluster stack (3 Docker replicas + nginx) on lab and run smoke tests."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=3600):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-8000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2000:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main() -> int:
    jar = DEPLOY / "staging" / "ispf-server.jar"
    if not jar.is_file():
        print("Missing deploy/staging/ispf-server.jar — run gradlew bootJar first", flush=True)
        return 1

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()

    print("Upload cluster artifacts...", flush=True)
    run(c, f"mkdir -p {ROOT}/cluster-staging {ROOT}/loadtest")
    sftp.put(str(jar), f"{ROOT}/cluster-staging/ispf-server.jar")

    for name in ("lab-cluster-compose.yml", "nginx-cluster.conf", "lab-cluster-test.sh"):
        upload_text(sftp, DEPLOY / name, f"{ROOT}/{name}")
        print(f"  uploaded {name}", flush=True)
    sftp.close()

    steps = [
        f"chmod +x {ROOT}/lab-cluster-test.sh",
        f"cd {ROOT} && docker compose -f lab-cluster-compose.yml down -v 2>/dev/null || true",
        f"cd {ROOT} && docker compose -f lab-cluster-compose.yml pull postgres redis nats nginx 2>&1 | tail -5",
        f"cd {ROOT} && docker compose -f lab-cluster-compose.yml up -d",
        f"bash {ROOT}/lab-cluster-test.sh 2>&1 | tee {ROOT}/loadtest/cluster-lab-smoke.log",
        f"grep -E 'unique replicas|PASSED|replicaId|clusterEnabled|heldDriverLocks' {ROOT}/loadtest/cluster-lab-smoke.log | tail -20",
    ]

    log_parts: list[str] = []
    failed = False
    for step in steps:
        code, out, err = run(c, step, timeout=3600)
        log_parts.append(f"=== {step[:120]} ===\n{out}\n{err}\nexit={code}\n")
        if code != 0 and "grep" not in step:
            failed = True
            break

    c.close()
    result = Path(REPO / "tmp_lab_cluster_result.txt")
    result.write_text("".join(log_parts), encoding="utf-8")
    print(f"\nFull log: {result}", flush=True)
    print("\n=== SUMMARY ===", flush=True)
    for line in "".join(log_parts).splitlines():
        if any(k in line for k in ("replicaId", "unique replicas", "PASSED", "clusterEnabled", "heldDriverLocks", "ERROR", "WARN")):
            print(line)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
