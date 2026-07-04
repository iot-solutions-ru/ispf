#!/usr/bin/env python3
"""Diagnose and fix admin login on lab cluster."""
from __future__ import annotations

import json
import paramiko

HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
ROOT = "/home/iot-solutions/ispf"
LAB_PORT = 8000


def run(c, cmd, timeout=300):
    print(">", cmd[:200], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    text = (out + err)[-8000:].encode("ascii", "replace").decode("ascii")
    if text.strip():
        print(text, flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    compose = f"docker compose -f {ROOT}/lab-cluster-compose.yml"

    run(c, f"{compose} ps")
    run(
        c,
        f"{compose} exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT COUNT(*) AS users FROM platform_users; "
        f"SELECT COUNT(*) AS devices FROM object_nodes WHERE path LIKE 'root.platform.devices.%';\"",
    )
    run(c, f"docker logs ispf-cluster-lab-ispf-server-1-1 2>&1 | grep -iE 'fixture|bootstrap|PlatformUser|admin' | tail -30")
    run(
        c,
        f"curl -sf -X POST http://127.0.0.1:{LAB_PORT}/api/v1/auth/login "
        f"-H 'Content-Type: application/json' "
        f"-d '{{\"username\":\"admin\",\"password\":\"admin\"}}' || echo LOGIN_FAILED",
    )

    print("\n=== Fix: restart replica-1 only (fixtures leader) ===", flush=True)
    run(c, f"{compose} stop ispf-server-2 ispf-server-3")
    run(c, f"{compose} restart ispf-server-1")
    run(c, "sleep 45")
    run(
        c,
        f"{compose} exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT COUNT(*) AS users FROM platform_users;\"",
    )
    run(c, f"docker logs ispf-cluster-lab-ispf-server-1-1 2>&1 | grep -iE 'fixture|bootstrap|PlatformUser|admin|seed' | tail -25")

    for i in range(30):
        _, out, _ = run(c, f"curl -sf -X POST http://127.0.0.1:{LAB_PORT}/api/v1/auth/login -H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}'", timeout=30)
        if "token" in out:
            print("LOGIN OK", flush=True)
            break
        run(c, "sleep 5", timeout=10)
    else:
        print("Login still failing after restart", flush=True)

    run(c, f"{compose} up -d ispf-server-2 ispf-server-3")
    run(c, "sleep 20")
    _, out, _ = run(c, f"curl -sf -X POST http://127.0.0.1:{LAB_PORT}/api/v1/auth/login -H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}'")
    try:
        token = json.loads(out).get("token")
        print(f"Final login: {'OK' if token else 'FAIL'}", flush=True)
    except json.JSONDecodeError:
        print("Final login: FAIL", flush=True)

    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
