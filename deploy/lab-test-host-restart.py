#!/usr/bin/env python3
"""Apply compose on remote lab host and restart nginx on port 8000."""
import paramiko
from pathlib import Path

HOST = "84.42.21.226"
PORT = 5031
USER = "iot-solutions"
PASSWORD = "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
ROOT = "/home/iot-solutions/ispf"
HTTP_PORT = 8000
DEPLOY = Path(__file__).resolve().parent


def run(client, cmd, timeout=300):
    print("===", cmd[:120], "===")
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    if out:
        print(out)
    if err:
        print("ERR:", err[-2500:])
    return stdout.channel.recv_exit_status()


def upload_text(sftp, local: Path, remote: str):
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

    sftp = client.open_sftp()
    for name in ("lab-test-host-compose.yml", "lab-test-host-smoke.sh", "lab-test-host-setup.sh"):
        upload_text(sftp, DEPLOY / name, f"{ROOT}/{name}")
    sftp.close()

    compose = f"{ROOT}/lab-test-host-compose.yml"
    run(client, f"ISPF_LAB_HTTP_PORT={HTTP_PORT} docker compose -f {compose} up -d ispf-server nginx")

    wait = (
        f"for i in $(seq 1 72); do "
        f"curl -sf http://127.0.0.1:{HTTP_PORT}/api/v1/info >/tmp/ispf-info.json && break; "
        f"sleep 5; done; cat /tmp/ispf-info.json 2>/dev/null || echo NOT_READY"
    )
    run(client, wait, timeout=400)

    code = run(
        client,
        f"chmod +x {ROOT}/lab-test-host-smoke.sh && ISPF_LAB_ROOT={ROOT} ISPF_LAB_HTTP_PORT={HTTP_PORT} bash {ROOT}/lab-test-host-smoke.sh",
        timeout=180,
    )
    run(client, f"ss -tlnp | grep {HTTP_PORT} || true")
    client.close()
    print(f"\nExternal URL: http://{HOST}:{HTTP_PORT}/  (admin/admin)")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
