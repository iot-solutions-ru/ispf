#!/usr/bin/env python3
"""Rebind cluster LAN ports and restart replica-4."""
import json
import paramiko

HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
ROOT = "/home/iot-solutions/ispf"
PEER = "192.168.100.10"


def run(c, cmd, timeout=600):
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-5000:], flush=True)
    if err.strip():
        print("STDERR:", err[-1500:], flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    _, lan_ip, _ = run(
        c,
        "ip -4 route get 192.168.100.10 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i==\"src\") print $(i+1)}'",
    )
    lab_lan = lan_ip.strip()
    print(f"LAN bind: {lab_lan}", flush=True)

    steps = [
        f"cd {ROOT} && ISPF_CLUSTER_LAN_BIND={lab_lan} docker compose -f lab-cluster-compose.yml up -d postgres redis nats",
        f"sleep 5 && ss -tlnp | grep -E ':5433|:6380|:4223' || netstat -tlnp 2>/dev/null | grep -E ':5433|:6380|:4223'",
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'nc -zv {lab_lan} 5433 2>&1 || true'",
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'bash ~/ispf-cluster/peer-start.sh {lab_lan} ~/ispf-cluster/ispf-server.jar ~/ispf-cluster/drivers'",
        f"cd {ROOT} && docker compose -f lab-cluster-compose.yml up -d --no-deps --force-recreate nginx",
        "sleep 10",
    ]
    for step in steps:
        code, _, _ = run(c, step, timeout=900)
        if code != 0 and "nc -zv" not in step:
            c.close()
            return 1

    seen = set()
    for _ in range(30):
        _, out, _ = run(c, "curl -sf http://127.0.0.1:8098/api/v1/info", timeout=20)
        try:
            seen.add(json.loads(out).get("replicaId"))
        except json.JSONDecodeError:
            pass
    print(f"\n=== LB replicas: {sorted(seen)} ===", flush=True)

    _, out, _ = run(
        c,
        f"docker compose -f {ROOT}/lab-cluster-compose.yml exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT holder_id, COUNT(*) AS locks FROM platform_driver_locks GROUP BY holder_id ORDER BY 1;\"",
    )
    print(out, flush=True)

    run(c, f"bash {ROOT}/lab-cluster-test.sh", timeout=900)
    c.close()
    return 0 if "replica-4" in seen else 1


if __name__ == "__main__":
    raise SystemExit(main())
