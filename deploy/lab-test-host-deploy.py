#!/usr/bin/env python3
"""Deploy ISPF lab stack to a test host over SFTP + SSH."""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

import paramiko

REPO_ROOT = Path(__file__).resolve().parents[1]
DEPLOY = REPO_ROOT / "deploy"
STAGING = DEPLOY / "staging"


def upload_file(sftp: paramiko.SFTPClient, local: Path, remote: str, text_mode: bool = False) -> None:
    if text_mode and local.suffix in {".sh", ".conf", ".yml"}:
        data = local.read_bytes().replace(b"\r\n", b"\n")
        with sftp.file(remote, "w") as remote_file:
            remote_file.write(data)
    else:
        sftp.put(str(local), remote)


def upload_tree(sftp: paramiko.SFTPClient, local: Path, remote: str) -> None:
    remote = remote.replace("\\", "/")
    if local.is_file():
        sftp.put(str(local), remote)
        return
    try:
        sftp.mkdir(remote)
    except OSError:
        pass
    for item in local.iterdir():
        upload_tree(sftp, item, f"{remote}/{item.name}")


def run(client: paramiko.SSHClient, command: str, timeout: int = 3600) -> tuple[int, str, str]:
    stdin, stdout, stderr = client.exec_command(command, timeout=timeout)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.environ.get("ISPF_LAB_HOST", "84.42.21.226"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("ISPF_LAB_SSH_PORT", "5031")))
    parser.add_argument("--user", default=os.environ.get("ISPF_LAB_USER", "iot-solutions"))
    parser.add_argument("--password", default=os.environ.get("ISPF_LAB_PASSWORD"))
    parser.add_argument("--http-port", type=int, default=8000)
    parser.add_argument("--skip-upload", action="store_true", help="Only re-run remote setup")
    args = parser.parse_args()

    if not args.password:
        print("Set --password or ISPF_LAB_PASSWORD", file=sys.stderr)
        return 2

    required = [
        STAGING / "ispf-server.jar",
        STAGING / "driver-packs.tar.gz",
        STAGING / "web-console" / "index.html",
        DEPLOY / "lab-test-host-compose.yml",
        DEPLOY / "lab-test-host-setup.sh",
        DEPLOY / "nginx-lab.conf",
    ]
    for path in required:
        if not path.exists():
            print(f"Missing artifact: {path}", file=sys.stderr)
            return 2

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting to {args.user}@{args.host}:{args.port} ...")
    client.connect(args.host, port=args.port, username=args.user, password=args.password, timeout=60)

    code, home, _ = run(client, "echo $HOME")
    home = home.strip()
    remote_root = f"{home}/ispf"
    print(f"Remote lab root: {remote_root}")

    if not args.skip_upload:
        run(client, f"mkdir -p {remote_root}/staging {remote_root}/loadtest {remote_root}/mqtt {remote_root}/data/drivers {remote_root}/bin")
        sftp = client.open_sftp()
        uploads = [
            (STAGING / "ispf-server.jar", f"{remote_root}/staging/ispf-server.jar"),
            (STAGING / "driver-packs.tar.gz", f"{remote_root}/staging/driver-packs.tar.gz"),
            (DEPLOY / "lab-test-host-compose.yml", f"{remote_root}/lab-test-host-compose.yml"),
            (DEPLOY / "lab-test-host-setup.sh", f"{remote_root}/lab-test-host-setup.sh"),
            (DEPLOY / "nginx-lab.conf", f"{remote_root}/nginx-lab.conf"),
            (DEPLOY / "health-check.sh", f"{remote_root}/staging/health-check.sh"),
            (DEPLOY / "vps-cassandra-verify.sh", f"{remote_root}/staging/vps-cassandra-verify.sh"),
            (DEPLOY / "mqtt_loadtest_lib.py", f"{remote_root}/staging/mqtt_loadtest_lib.py"),
        ]
        for local, remote in uploads:
            if not local.exists():
                continue
            print(f"Uploading {local.name} ...")
            upload_file(sftp, local, remote, text_mode=True)
        print("Uploading web-console ...")
        upload_tree(sftp, STAGING / "web-console", f"{remote_root}/staging/web-console")
        sftp.close()
    else:
        sftp = client.open_sftp()
        for local, remote in [
            (DEPLOY / "lab-test-host-compose.yml", f"{remote_root}/lab-test-host-compose.yml"),
            (DEPLOY / "lab-test-host-setup.sh", f"{remote_root}/lab-test-host-setup.sh"),
            (DEPLOY / "nginx-lab.conf", f"{remote_root}/nginx-lab.conf"),
        ]:
            upload_file(sftp, local, remote, text_mode=True)
        sftp.close()

    setup_cmd = (
        f"sed -i 's/\\r$//' {remote_root}/lab-test-host-setup.sh && "
        f"chmod +x {remote_root}/lab-test-host-setup.sh && "
        f"ISPF_LAB_ROOT={remote_root} ISPF_LAB_HTTP_PORT={args.http_port} "
        f"bash {remote_root}/lab-test-host-setup.sh"
    )
    print("Running setup (pull images + start stack, may take several minutes) ...")
    code, out, err = run(client, setup_cmd, timeout=3600)
    print(out)
    if err:
        print(err, file=sys.stderr)
    if code != 0:
        print(f"Setup failed with exit code {code}", file=sys.stderr)
        client.close()
        return code

    print("\n=== Post-deploy verify ===")
    verify_cmd = (
        f"curl -sf http://127.0.0.1:{args.http_port}/api/v1/info; echo; "
        f"docker compose -f {remote_root}/lab-test-host-compose.yml ps"
    )
    code, out, err = run(client, verify_cmd, timeout=120)
    print(out)
    if err:
        print(err)

    client.close()
    print(f"\nDone. UI: http://{args.host}:{args.http_port}/  (admin/admin)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
