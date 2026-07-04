#!/usr/bin/env python3
"""Fix lab 502: create Scylla keyspace and restart ISPF."""
from __future__ import annotations

import paramiko
import sys
import time

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
CQL = (
    "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = "
    "{'class': 'SimpleStrategy', 'replication_factor': 1};"
)


def run(c, cmd, timeout=120):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    text = (out + err).strip()
    if text:
        print(text[-5000:], flush=True)
    print("exit", code, flush=True)
    return code, out


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    compose = f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml"

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    run(c, f'{compose} exec -T scylla cqlsh -e "{CQL}"')
    run(c, f'{compose} exec -T scylla cqlsh -e "DESCRIBE KEYSPACE ispf;"')
    run(c, f"{compose} restart ispf-server")
    run(c, f"{compose} ps")

    ok = False
    for i in range(1, 91):
        _, out = run(
            c,
            "curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
            "-H 'Content-Type: application/json' "
            "-d '{\"username\":\"admin\",\"password\":\"admin\"}'",
            timeout=30,
        )
        if "token" in out:
            print(f"LOGIN OK (attempt {i})", flush=True)
            ok = True
            break
        time.sleep(5)

    run(c, "curl -sf http://127.0.0.1:8000/api/v1/info | python3 -m json.tool | head -12")
    run(
        c,
        'docker inspect ispf-lab-ispf-server-1 --format '
        '"restarts={{.RestartCount}} status={{.State.Status}}"',
    )
    c.close()
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
