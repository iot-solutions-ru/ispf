#!/usr/bin/env python3
"""Stop all Docker containers and cleanup unnecessary processes on lab."""
from __future__ import annotations

import paramiko

HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
PEER = "192.168.100.10"


def run(c, cmd, timeout=300):
    print(">", cmd[:220], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out.strip():
        print(out[-10000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2500:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    print("=== BEFORE: containers ===", flush=True)
    run(c, 'docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"')

    print("\n=== BEFORE: listening ports (user) ===", flush=True)
    run(c, "ss -tlnp 2>/dev/null | grep -v '127.0.0.1:22' | head -40 || true")

    print("\n=== BEFORE: heavy/user processes ===", flush=True)
    run(
        c,
        "ps aux --sort=-%mem | head -25",
    )

    steps = [
        # All compose projects under home
        "find /home/iot-solutions -maxdepth 3 -name '*compose*.yml' -o -name 'docker-compose*.yml' 2>/dev/null | sort -u",
        "for f in $(find /home/iot-solutions -maxdepth 3 \\( -name '*compose*.yml' -o -name 'docker-compose*.yml' \\) 2>/dev/null); do "
        "echo \"compose down: $f\"; "
        "(cd \"$(dirname \"$f\")\" && docker compose -f \"$(basename \"$f\")\" down -v 2>/dev/null) || true; "
        "done",
        # Stop ALL running containers on lab host
        "docker ps -q | xargs -r docker stop -t 15 2>/dev/null || true",
        "docker ps -aq | xargs -r docker rm -f 2>/dev/null || true",
        # Bench/orphan containers by label/name patterns
        "docker ps -aq --filter label=ispf.emqtt-bench=1 | xargs -r docker rm -f 2>/dev/null || true",
        # Kill leftover bench/java/emqtt processes owned by user (not system java)
        "pkill -u iot-solutions -f 'emqtt-bench' 2>/dev/null || true",
        "pkill -u iot-solutions -f 'mqtt-emqtt-bench' 2>/dev/null || true",
        "pkill -u iot-solutions -f 'ispf-server.jar' 2>/dev/null || true",
        "pkill -u iot-solutions -f 'loadtest/' 2>/dev/null || true",
        # Peer host cleanup
        f"ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 {USER}@{PEER} "
        "'docker ps -q | xargs -r docker stop -t 10 2>/dev/null; "
        "docker ps -aq | xargs -r docker rm -f 2>/dev/null; "
        "pkill -u iot-solutions -f ispf-server.jar 2>/dev/null || true'",
        # Docker prune (dangling only — not volumes/images unless user wants full cleanup)
        "docker network prune -f 2>/dev/null || true",
    ]

    for step in steps:
        run(c, step, timeout=600)

    print("\n=== AFTER: containers ===", flush=True)
    run(c, 'docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"')

    print("\n=== AFTER: listening ports ===", flush=True)
    run(c, "ss -tlnp 2>/dev/null | grep -E ':8000|:8098|:8080|:8084|:1883|:9042|:5432|:5433' || echo '(lab service ports free)'")

    print("\n=== AFTER: user processes (top mem) ===", flush=True)
    run(c, "ps aux --sort=-%mem | grep -E 'iot-solutions|docker|java|vllm|emqtt|ispf' | grep -v grep || echo '(no lab bench processes)'")

    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
