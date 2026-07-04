#!/usr/bin/env python3
"""Upload 0.9.87 jar + driver packs, restart ISPF, run 8x2000 bench (L0 coalesce off)."""
from __future__ import annotations

import paramiko
from pathlib import Path

HOST = "84.42.21.226"
PORT = 5031
USER = "iot-solutions"
PASSWORD = "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
STAGING = REPO / "deploy" / "staging"
DEPLOY = REPO / "deploy"


def upload_text(sftp, local: Path, remote: str) -> None:
    data = local.read_bytes().replace(b"\r\n", b"\n")
    with sftp.file(remote, "w") as f:
        f.write(data)


def main() -> int:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=60)
    sftp = client.open_sftp()

    print("upload ispf-server.jar + driver-packs ...")
    sftp.put(str(STAGING / "ispf-server.jar"), f"{ROOT}/ispf-server.jar")
    sftp.put(str(STAGING / "driver-packs.tar.gz"), f"{ROOT}/staging/driver-packs.tar.gz")

    scripts = [
        "lab-mqtt-event-journal-multi-test.sh",
        "setup-mqtt-event-journal-devices.py",
        "mqtt_loadtest_lib.py",
        "loadtest_cleanup_lib.py",
        "mqtt-emqtt-bench.sh",
    ]
    for name in scripts:
        local = REPO / "deploy" / name
        dest = f"{ROOT}/{name}" if name.startswith("lab-") else f"{ROOT}/loadtest/{name}"
        print("upload", name)
        if name.endswith((".sh", ".py")):
            upload_text(sftp, local, dest)
        else:
            sftp.put(str(local), dest)
    sftp.close()

    cmd = f"""
set -euo pipefail
cd {ROOT}
rm -f data/drivers/.extracted
mkdir -p data/drivers staging loadtest
tar -xzf staging/driver-packs.tar.gz -C data/drivers
chmod +x lab-mqtt-event-journal-multi-test.sh loadtest/mqtt-emqtt-bench.sh
docker compose -f lab-test-host-compose.yml restart ispf-server
echo 'waiting for API...'
for i in $(seq 1 60); do
  curl -sf http://127.0.0.1:8000/api/v1/info >/dev/null && break
  sleep 3
done
curl -sf http://127.0.0.1:8000/api/v1/info | python3 -c "import sys,json; print(json.load(sys.stdin).get('version'))"
echo '=== BENCHMARK START $(date -Is) — watch btop ==='
ISPF_LAB_ROOT={ROOT} DEVICES=8 RATE_PER_DEVICE=2000 WARMUP=15 PHASE=60 BENCH_NO_L0_COALESCE=true CALLBACK_THREADS=16 \\
  bash {ROOT}/lab-mqtt-event-journal-multi-test.sh
"""
    print("restart + benchmark (~2 min)...")
    _, stdout, stderr = client.exec_command(cmd, timeout=600)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    client.close()
    Path(REPO / "tmp_bench_nocoalesce.txt").write_text(out + err, encoding="utf-8")
    print(out)
    if err.strip():
        print("stderr:", err[-2000:])
    print("exit", code)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
