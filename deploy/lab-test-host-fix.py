#!/usr/bin/env python3
import paramiko
from pathlib import Path

HOST = "84.42.21.226"
PORT = 5031
USER = "iot-solutions"
from lab_ssh import lab_password
PASSWORD = lab_password()
REMOTE_ROOT = "/home/iot-solutions/ispf"
SETUP = Path(__file__).resolve().parent / "lab-test-host-setup.sh"


def run(client, cmd, timeout=600):
    print("===", cmd[:120], "===")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    if out:
        print(out)
    if err:
        print("ERR:", err[-3000:])
    return code


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

    sftp = client.open_sftp()
    data = SETUP.read_bytes().replace(b"\r\n", b"\n")
    with sftp.file(f"{REMOTE_ROOT}/lab-test-host-setup.sh", "w") as f:
        f.write(data)
    sftp.close()

    compose = f"{REMOTE_ROOT}/lab-test-host-compose.yml"
    cql = (
        "CREATE KEYSPACE IF NOT EXISTS ispf WITH replication = "
        "{'class': 'SimpleStrategy', 'replication_factor': 1};"
    )
    run(
        client,
        f'SCYLLA=$(docker compose -f {compose} ps -q scylla) && '
        f'docker exec "$SCYLLA" cqlsh -e "{cql}"',
    )
    run(client, f"cp -f {REMOTE_ROOT}/staging/ispf-server.jar {REMOTE_ROOT}/ispf-server.jar")
    run(
        client,
        f"mkdir -p {REMOTE_ROOT}/web-console && "
        f"cp -a {REMOTE_ROOT}/staging/web-console/. {REMOTE_ROOT}/web-console/",
    )
    run(
        client,
        f"mkdir -p {REMOTE_ROOT}/data/drivers && "
        f"tar -xzf {REMOTE_ROOT}/staging/driver-packs.tar.gz -C {REMOTE_ROOT}/data/drivers",
        timeout=300,
    )
    run(client, f"docker compose -f {compose} up -d ispf-server nginx")

    wait_cmd = (
        "for i in $(seq 1 72); do "
        "curl -sf http://127.0.0.1:18080/api/v1/info >/tmp/ispf-info.json && break; "
        "sleep 5; "
        "done; "
        "cat /tmp/ispf-info.json 2>/dev/null || echo API_NOT_READY"
    )
    run(client, wait_cmd, timeout=400)
    run(client, f"docker compose -f {compose} ps")
    run(
        client,
        "TOKEN=$(curl -sf -X POST http://127.0.0.1:18080/api/v1/auth/login "
        "-H 'Content-Type: application/json' "
        "-d '{\"username\":\"admin\",\"password\":\"admin\"}' "
        "| python3 -c \"import sys,json; print(json.load(sys.stdin).get('token',''))\") && "
        "curl -sf -H \"Authorization: Bearer $TOKEN\" "
        "\"http://127.0.0.1:18080/api/v1/objects/by-path?path=root.platform.devices.demo-sensor-01\" "
        "| head -c 400; echo",
    )
    client.close()


if __name__ == "__main__":
    main()
