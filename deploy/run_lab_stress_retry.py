#!/usr/bin/env python3
"""Wait for lab ISPF login, then run event-journal stress benchmark."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=7200):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-12000:], flush=True)
    if err.strip():
        print("STDERR:", err[-3000:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    wait_login = (
        f"cd {ROOT} && for i in $(seq 1 120); do "
        f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' | grep -q token "
        f"&& echo login_ok attempt=$i && break; sleep 5; done"
    )
    code, out, _ = run(c, wait_login, timeout=900)
    if "login_ok" not in out:
        run(c, f"docker logs --tail 60 ispf-lab-ispf-server-1 2>&1")
        c.close()
        return 1

    run(c, f"curl -sf http://127.0.0.1:8000/api/v1/info | python3 -m json.tool | head -12")

    code, out, err = run(c, f"cd {ROOT} && bash lab-stress-run.sh", timeout=7200)
    c.close()

    summary_path = Path(__file__).resolve().parents[1] / "tmp_lab_stress_summary.txt"
    summary_path.write_text(out + "\n" + err, encoding="utf-8")

    for line in (out + err).splitlines():
        if any(k in line for k in ("Events/s", "Efficiency", "Target:", "queue", "sync_fallback", "STRESS RUN")):
            print("SUMMARY:", line, flush=True)

    return 0 if code == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
