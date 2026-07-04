#!/usr/bin/env python3
"""Restart replica-4 on LAN peer with fixed Postgres driver config."""
from __future__ import annotations

import json
import paramiko
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ROOT = "/home/iot-solutions/ispf"
PEER = "192.168.100.10"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=600):
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-6000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    return code, out, err


def main() -> int:
    peer_sh = (REPO / "deploy" / "lab-cluster-peer-start.sh").read_bytes().replace(b"\r\n", b"\n")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    with sftp.file(f"{ROOT}/lab-cluster-peer-start.sh", "w") as f:
        f.write(peer_sh)
    sftp.close()

    _, lan_ip, _ = run(
        c,
        "ip -4 route get 192.168.100.10 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i==\"src\") print $(i+1)}'",
    )
    lab_lan = lan_ip.strip()
    print(f"LAB LAN: {lab_lan}", flush=True)

    steps = [
        f"scp -o StrictHostKeyChecking=no {ROOT}/lab-cluster-peer-start.sh {USER}@{PEER}:~/ispf-cluster/peer-start.sh",
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'chmod +x ~/ispf-cluster/peer-start.sh && "
        f"bash ~/ispf-cluster/peer-start.sh {lab_lan} ~/ispf-cluster/ispf-server.jar ~/ispf-cluster/drivers'",
        f"cd {ROOT} && docker compose -f lab-cluster-compose.yml up -d --no-deps --force-recreate nginx",
        "sleep 15",
    ]
    for step in steps:
        code, _, _ = run(c, step, timeout=900)
        if code != 0:
            c.close()
            return 1

    seen = set()
    for _ in range(24):
        _, out, _ = run(c, "curl -sf http://127.0.0.1:8098/api/v1/info", timeout=30)
        try:
            seen.add(json.loads(out).get("replicaId"))
        except json.JSONDecodeError:
            pass
    print(f"\n=== replicas in LB: {sorted(seen)} ===", flush=True)

    _, out, _ = run(
        c,
        f"docker compose -f {ROOT}/lab-cluster-compose.yml exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT holder_id, COUNT(*) AS locks FROM platform_driver_locks GROUP BY holder_id ORDER BY 1;\"",
    )
    print("=== driver locks ===\n", out, flush=True)

    run(c, f"bash {ROOT}/lab-cluster-test.sh 2>&1 | tee {ROOT}/loadtest/cluster-peer-rerun.log", timeout=900)
    c.close()
    return 0 if "replica-4" in seen else 1


if __name__ == "__main__":
    raise SystemExit(main())
