#!/usr/bin/env python3
"""Upload ispf-server.jar + lab env/compose and recreate ispf-server on lab."""
from __future__ import annotations

import sys
from pathlib import Path

import paramiko

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=600):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-4000:], flush=True)
    if err.strip():
        print("STDERR:", err[-800:], flush=True)
    print("exit", code, flush=True)
    return code


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    jar = DEPLOY / "staging" / "ispf-server.jar"
    if not jar.is_file():
        print("Missing", jar, file=sys.stderr)
        return 1

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    print("Upload JAR...", flush=True)
    sftp.put(str(jar), f"{ROOT}/ispf-server.jar")
    for name in ("lab-stress.env", "lab-test-host-compose.yml", "lab-ispf-ramp-bench.sh", "lab-emqtt-cleanup.sh"):
        upload_text(sftp, DEPLOY / name, f"{ROOT}/{name}")
        print("  uploaded", name, flush=True)
    sftp.close()

    run(c, f"bash {ROOT}/lab-emqtt-cleanup.sh 2>/dev/null || true", timeout=60)
    code = run(
        c,
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
        timeout=300,
    )
    if code != 0:
        c.close()
        return code
    run(
        c,
        f"cd {ROOT} && for i in $(seq 1 90); do "
        f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 "
        f"&& echo login_ok attempt=$i && curl -sf http://127.0.0.1:8000/api/v1/info && break; sleep 3; done",
        timeout=300,
    )
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
