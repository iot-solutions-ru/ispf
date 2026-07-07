#!/usr/bin/env python3
"""Staggered cluster bootstrap + driver locks + replica-4 on 192.168.100.10."""
from __future__ import annotations

import json
import paramiko
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
ROOT = "/home/iot-solutions/ispf"
PEER = "192.168.100.10"


def run(c, cmd, timeout=900):
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
        print("Missing deploy/staging/ispf-server.jar", flush=True)
        return 1

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()
    run(c, f"mkdir -p {ROOT}/cluster-staging {ROOT}/loadtest")
    sftp.put(str(jar), f"{ROOT}/cluster-staging/ispf-server.jar")
    for name in (
        "lab-cluster-compose.yml",
        "nginx-cluster-lab.conf",
        "lab-cluster-bootstrap.sh",
        "lab-cluster-peer-start.sh",
        "lab-cluster-test.sh",
        "cluster-smoke-test.sh",
    ):
        upload_text(sftp, DEPLOY / name, f"{ROOT}/{name}")
    sftp.close()

    _, lan_ip, _ = run(
        c,
        "ip -4 route get 192.168.100.10 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i==\"src\") print $(i+1)}'",
        timeout=30,
    )
    lab_lan = lan_ip.strip() or "0.0.0.0"
    print(f"LAB LAN bind: {lab_lan}", flush=True)

    steps = [
        f"chmod +x {ROOT}/lab-cluster-bootstrap.sh {ROOT}/lab-cluster-peer-start.sh {ROOT}/lab-cluster-test.sh {ROOT}/cluster-smoke-test.sh",
        f"cd {ROOT} && ISPF_CLUSTER_LAN_BIND={lab_lan} bash lab-cluster-bootstrap.sh 2>&1 | tee loadtest/cluster-bootstrap.log",
        f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login -H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}'",
        f"docker compose -f {ROOT}/lab-cluster-compose.yml exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT holder_id, COUNT(*) AS locks FROM platform_driver_locks GROUP BY holder_id ORDER BY 1;\"",
    ]
    for step in steps:
        code, _, _ = run(c, step, timeout=900)
        if code != 0:
            c.close()
            return 1

    # Peer replica-4
    peer_steps = [
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'mkdir -p ~/ispf-cluster/drivers'",
        f"scp -o StrictHostKeyChecking=no {ROOT}/cluster-staging/ispf-server.jar {USER}@{PEER}:~/ispf-cluster/ispf-server.jar",
        f"rsync -a --ignore-missing-args {ROOT}/data/drivers/ {USER}@{PEER}:~/ispf-cluster/drivers/ 2>/dev/null || "
        f"scp -o StrictHostKeyChecking=no -r {ROOT}/data/drivers/* {USER}@{PEER}:~/ispf-cluster/drivers/ 2>/dev/null || true",
        f"scp -o StrictHostKeyChecking=no {ROOT}/lab-cluster-peer-start.sh {USER}@{PEER}:~/ispf-cluster/peer-start.sh",
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'chmod +x ~/ispf-cluster/peer-start.sh && "
        f"bash ~/ispf-cluster/peer-start.sh {lab_lan} ~/ispf-cluster/ispf-server.jar ~/ispf-cluster/drivers "
        f"2>&1 | tee ~/ispf-cluster/peer-start.log'",
        f"cd {ROOT} && ISPF_CLUSTER_LAN_BIND={lab_lan} docker compose -f lab-cluster-compose.yml up -d --no-deps --force-recreate nginx",
    ]
    for step in peer_steps:
        code, out, _ = run(c, step, timeout=900)
        if code != 0 and "rsync" not in step and "scp -r" not in step:
            print("WARN: peer step failed, continuing", flush=True)

    # Verify replica-4 in LB pool
    seen = set()
    for _ in range(20):
        _, out, _ = run(c, "curl -sf http://127.0.0.1:8000/api/v1/info", timeout=30)
        try:
            seen.add(json.loads(out).get("replicaId"))
        except json.JSONDecodeError:
            pass
    print(f"\n=== FINAL round-robin replicas: {sorted(seen)} ===", flush=True)

    _, out, _ = run(
        c,
        f"docker compose -f {ROOT}/lab-cluster-compose.yml exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT holder_id, COUNT(*) AS locks FROM platform_driver_locks GROUP BY holder_id ORDER BY 1;\"",
        timeout=60,
    )
    print("=== FINAL driver locks ===\n", out, flush=True)

    run(c, f"bash {ROOT}/lab-cluster-test.sh 2>&1 | tee {ROOT}/loadtest/cluster-full.log", timeout=900)
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
