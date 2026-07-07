#!/usr/bin/env python3
"""Deploy stress profile to lab, recreate Scylla+ISPF, run 16x5000 benchmark."""
from __future__ import annotations

import paramiko
from pathlib import Path

HOST = "84.42.21.226"
PORT = 5031
USER = "iot-solutions"
from lab_ssh import lab_password
PASSWORD = lab_password()
ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
STAGING = DEPLOY / "staging"

UPLOADS = [
    "lab-test-host-compose.yml",
    "lab-stress.env",
    "lab-stress-run.sh",
    "lab-mqtt-event-journal-multi-test.sh",
    "lab-test-host-clean-run.sh",
    "setup-mqtt-event-journal-devices.py",
    "mqtt_loadtest_lib.py",
    "loadtest_cleanup_lib.py",
    "mqtt-emqtt-bench.sh",
    "nginx-lab.conf",
]


def upload_text(sftp, local: Path, remote: str) -> None:
    data = local.read_bytes().replace(b"\r\n", b"\n")
    with sftp.file(remote, "w") as f:
        f.write(data)


def main() -> int:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=60)
    sftp = client.open_sftp()

    jar = STAGING / "ispf-server.jar"
    packs = STAGING / "driver-packs.tar.gz"
    if jar.exists():
        print("upload ispf-server.jar")
        sftp.put(str(jar), f"{ROOT}/ispf-server.jar")
    if packs.exists():
        print("upload driver-packs.tar.gz")
        sftp.put(str(packs), f"{ROOT}/staging/driver-packs.tar.gz")

    for name in UPLOADS:
        local = DEPLOY / name
        if not local.exists():
            continue
        dest = f"{ROOT}/{name}" if not name.endswith(".py") and name != "mqtt-emqtt-bench.sh" else (
            f"{ROOT}/loadtest/{name}" if name not in ("lab-stress-run.sh", "lab-mqtt-event-journal-multi-test.sh", "lab-test-host-clean-run.sh", "lab-stress.env", "lab-test-host-compose.yml", "nginx-lab.conf") else f"{ROOT}/{name}"
        )
        if name in ("setup-mqtt-event-journal-devices.py", "mqtt_loadtest_lib.py", "loadtest_cleanup_lib.py", "mqtt-emqtt-bench.sh"):
            dest = f"{ROOT}/loadtest/{name}"
        elif name in ("lab-test-host-compose.yml", "lab-stress.env", "lab-stress-run.sh", "lab-mqtt-event-journal-multi-test.sh", "nginx-lab.conf"):
            dest = f"{ROOT}/{name}"
        print("upload", name, "->", dest)
        upload_text(sftp, local, dest)
    sftp.close()

    cmd = f"""
set -euo pipefail
cd {ROOT}
mkdir -p staging loadtest data/drivers
if [ -f staging/driver-packs.tar.gz ]; then
  rm -f data/drivers/.extracted
  tar -xzf staging/driver-packs.tar.gz -C data/drivers
fi
chmod +x lab-stress-run.sh lab-mqtt-event-journal-multi-test.sh loadtest/mqtt-emqtt-bench.sh

echo '=== Recreate Scylla + ISPF with stress profile ==='
docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate scylla
echo 'waiting for Scylla (up to 3 min)...'
SCYLLA_CID=$(docker compose --env-file lab-stress.env -f lab-test-host-compose.yml ps -q scylla)
for i in $(seq 1 60); do
  docker exec "$SCYLLA_CID" cqlsh -e "SELECT now() FROM system.local" >/dev/null 2>&1 && break
  sleep 3
done
docker exec "$SCYLLA_CID" cqlsh -e "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = {{'class': 'SimpleStrategy', 'replication_factor': 1}};" 2>/dev/null || true

docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server nginx

echo 'waiting for login...'
for i in $(seq 1 90); do
  curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login -H 'Content-Type: application/json' -d '{{"username":"admin","password":"admin"}}' >/dev/null 2>&1 && break
  sleep 3
done
curl -sf http://127.0.0.1:8000/api/v1/info | head -c 200
echo

bash {ROOT}/lab-stress-run.sh
"""
    print("recreate stack + stress run (~3-5 min)...")
    _, stdout, stderr = client.exec_command(cmd, timeout=7200)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    client.close()

    result = out + ("\n--- stderr ---\n" + err if err.strip() else "")
    (REPO / "tmp_stress_result.txt").write_text(result, encoding="utf-8")
    import sys
    sys.stdout.buffer.write(result.encode("utf-8", errors="replace"))
    sys.stdout.buffer.write(f"\nexit {code}\n".encode())
    return code


if __name__ == "__main__":
    raise SystemExit(main())
