#!/usr/bin/env python3
"""Upload stress artifacts and run lab stress benchmark."""
import paramiko
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"

FILES = [
    (DEPLOY / "lab-test-host-compose.yml", f"{ROOT}/lab-test-host-compose.yml"),
    (DEPLOY / "lab-stress.env", f"{ROOT}/lab-stress.env"),
    (DEPLOY / "lab-stress-run.sh", f"{ROOT}/lab-stress-run.sh"),
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

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, PORT, USER, lab_password(), timeout=60)
sftp = c.open_sftp()
for local, remote in FILES:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))
    print("uploaded", local.name)
sftp.close()

steps = [
    f"chmod +x {ROOT}/lab-stress-run.sh {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh",
    f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate scylla",
    f"cd {ROOT} && for i in $(seq 1 80); do docker compose --env-file lab-stress.env -f lab-test-host-compose.yml exec -T scylla cqlsh -e 'SELECT now() FROM system.local' >/dev/null 2>&1 && echo scylla_ok && break; sleep 3; done",
    f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server nginx",
    f"cd {ROOT} && for i in $(seq 1 90); do curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login -H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 && echo login_ok && break; sleep 3; done",
    f"cd {ROOT} && bash lab-stress-run.sh",
]
all_out = []
for step in steps:
    code, out, err = run(c, step, timeout=7200)
    all_out.append(f"=== {step[:80]} ===\n{out}\n{err}\nexit={code}\n")
    if code != 0 and "lab-stress-run" not in step:
        print("FAILED step", code)
        break
c.close()
Path(REPO / "tmp_stress_full.txt").write_text("".join(all_out), encoding="utf-8")
print("".join(all_out)[-4000:])
