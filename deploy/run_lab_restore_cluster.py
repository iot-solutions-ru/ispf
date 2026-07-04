#!/usr/bin/env python3
"""Restore full 4-node cluster after admin fix."""
import json
import paramiko
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
ROOT = "/home/iot-solutions/ispf"
PEER = "192.168.100.10"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=300):
    print(">", cmd[:180], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    text = (out + err)[-6000:].encode("ascii", "replace").decode("ascii")
    if text.strip():
        print(text, flush=True)
    return code, out, err


def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    _, lan, _ = run(c, "ip -4 route get 192.168.100.10 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i==\"src\") print $(i+1)}'")
    lab_lan = lan.strip()
    env = f"ISPF_CLUSTER_LAN_BIND={lab_lan} ISPF_LAB_CLUSTER_PORT=8000"

    steps = [
        f"cd {ROOT} && {env} docker compose -f lab-cluster-compose.yml up -d postgres redis nats",
        f"cd {ROOT} && {env} docker compose -f lab-cluster-compose.yml up -d ispf-server-1 ispf-server-2 ispf-server-3",
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'bash ~/ispf-cluster/peer-start.sh {lab_lan} ~/ispf-cluster/ispf-server.jar ~/ispf-cluster/drivers'",
        f"cd {ROOT} && {env} docker compose -f lab-cluster-compose.yml up -d --no-deps --force-recreate nginx",
        "sleep 20",
    ]
    for s in steps:
        run(c, s, timeout=600)

    _, out, _ = run(c, "curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login -H 'Content-Type: application/json' -d '{\"username\":\"admin\",\"password\":\"admin\"}'")
    print("LOGIN:", "OK" if "token" in out else "FAIL", flush=True)

    seen = set()
    for _ in range(16):
        _, out, _ = run(c, "curl -sf http://127.0.0.1:8000/api/v1/info")
        try:
            seen.add(json.loads(out).get("replicaId"))
        except Exception:
            pass
    print("Replicas:", sorted(seen), flush=True)
    c.close()


if __name__ == "__main__":
    main()
