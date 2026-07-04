#!/usr/bin/env python3
"""Upload max-tuned stress profile and run 16×50k hard-break on lab host."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"

FILES = [
    (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-stress-hard-break.sh", f"{ROOT}/lab-stress-hard-break.sh"),
    (DEPLOY / "lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    (DEPLOY / "lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
    (DEPLOY / "setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    (DEPLOY / "mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    (DEPLOY / "loadtest_cleanup_lib.py", f"{ROOT}/loadtest/loadtest_cleanup_lib.py"),
    (DEPLOY / "mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
]


def run(c, cmd, timeout=7200):
    print(">", cmd[:120])
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode(errors="replace")
    err = e.read().decode(errors="replace")
    code = o.channel.recv_exit_status()
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)
    sftp = c.open_sftp()
    for local, remote in FILES:
        with sftp.file(remote, "w") as f:
            f.write(local.read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", local.name)
    sftp.close()

    steps = [
        f"chmod +x {ROOT}/lab-stress-hard-break.sh {ROOT}/lab-emqtt-cleanup.sh "
        f"{ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh",
        "nproc && free -h | head -2",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate scylla",
        f"cd {ROOT} && for i in $(seq 1 120); do "
        f"docker compose --env-file lab-stress.env -f lab-test-host-compose.yml exec -T scylla "
        f"cqlsh -e 'SELECT now() FROM system.local' >/dev/null 2>&1 && echo scylla_ok && break; sleep 3; done",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server nginx",
        f"cd {ROOT} && for i in $(seq 1 120); do "
        f"curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login -H 'Content-Type: application/json' "
        f"-d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 && echo login_ok && break; sleep 3; done",
        f"cd {ROOT} && bash lab-stress-hard-break.sh",
    ]

    chunks: list[str] = []
    for step in steps:
        code, out, err = run(c, step, timeout=7200)
        chunks.append(f"=== {step[:100]} ===\n{out}\n{err}\nexit={code}\n")
        if code != 0 and "lab-stress-hard-break" not in step:
            print("FAILED step", code)
            break
    c.close()

    text = "".join(chunks)
    Path(REPO / "tmp_hard_break_result.txt").write_text(text, encoding="utf-8")
    sys.stdout.buffer.write(text[-12000:].encode("utf-8", errors="replace"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
