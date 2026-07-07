#!/usr/bin/env python3
"""Deploy 4-node ISPF cluster on lab (port 8000): 3 Docker + replica-4 on LAN peer."""
from __future__ import annotations

import json
import subprocess
import tarfile
import tempfile
import paramiko
from pathlib import Path
from lab_ssh import HOST, PORT as SSH_PORT, USER, lab_password

REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
ROOT = "/home/iot-solutions/ispf"
PEER = "192.168.100.10"
HTTP_PORT = 8000


def run(c, cmd, timeout=900):
    print(">", cmd[:160], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-10000:].encode("ascii", "replace").decode("ascii"), flush=True)
    if err.strip():
        print("STDERR:", err[-2500:].encode("ascii", "replace").decode("ascii"), flush=True)
    print("exit", code, flush=True)
    return code, out, err


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def ensure_build() -> Path:
    jar = DEPLOY / "staging" / "ispf-server.jar"
    built = REPO / "packages" / "ispf-server" / "build" / "libs"
    latest = sorted(built.glob("ispf-server-*.jar"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not jar.is_file() and latest:
        DEPLOY.mkdir(parents=True, exist_ok=True)
        import shutil
        shutil.copy2(latest[0], jar)
    if not jar.is_file():
        print("Building ispf-server...", flush=True)
        subprocess.run(
            [str(REPO / "gradlew.bat" if Path("gradlew.bat").exists() else REPO / "gradlew"),
             ":packages:ispf-server:bootJar", "-x", "test"],
            cwd=REPO,
            check=True,
        )
        latest = sorted(built.glob("ispf-server-*.jar"), key=lambda p: p.stat().st_mtime, reverse=True)[0]
        DEPLOY.mkdir(parents=True, exist_ok=True)
        import shutil
        shutil.copy2(latest, jar)
    return jar


def ensure_web_console() -> Path:
    dist = REPO / "apps" / "web-console" / "dist"
    if not (dist / "index.html").is_file():
        print("Building web-console...", flush=True)
        subprocess.run(["npm", "ci"], cwd=REPO / "apps" / "web-console", check=True, shell=True)
        subprocess.run(["npm", "run", "build"], cwd=REPO / "apps" / "web-console", check=True, shell=True)
    return dist


def upload_web_console(sftp, dist: Path, remote_root: str) -> None:
    with tempfile.NamedTemporaryFile(suffix=".tar.gz", delete=False) as tmp:
        tar_path = Path(tmp.name)
    with tarfile.open(tar_path, "w:gz") as tar:
        tar.add(dist, arcname=".")
    remote_tar = f"{remote_root}/web-console.tgz"
    sftp.put(str(tar_path), remote_tar)
    tar_path.unlink(missing_ok=True)


def main() -> int:
    jar = ensure_build()
    dist = ensure_web_console()

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, SSH_PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()

    run(c, f"mkdir -p {ROOT}/cluster-staging {ROOT}/web-console {ROOT}/loadtest {ROOT}/data/drivers")
    sftp.put(str(jar), f"{ROOT}/cluster-staging/ispf-server.jar")
    upload_web_console(sftp, dist, ROOT)
    run(c, f"rm -rf {ROOT}/web-console/* && tar -xzf {ROOT}/web-console.tgz -C {ROOT}/web-console && rm -f {ROOT}/web-console.tgz")

    for name in (
        "lab-cluster-compose.yml",
        "nginx-cluster-lab.conf",
        "lab-cluster-bootstrap.sh",
        "lab-cluster-peer-start.sh",
        "lab-cluster-test.sh",
        "cluster-smoke-test.sh",
    ):
        upload_text(sftp, DEPLOY / name, f"{ROOT}/{name}")
    sftp.close()

    _, lan_ip, _ = run(
        c,
        "ip -4 route get 192.168.100.10 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i==\"src\") print $(i+1)}'",
        timeout=30,
    )
    lab_lan = lan_ip.strip() or "0.0.0.0"
    print(f"LAB LAN: {lab_lan}, HTTP_PORT: {HTTP_PORT}", flush=True)

    env = f"ISPF_CLUSTER_LAN_BIND={lab_lan} ISPF_LAB_CLUSTER_HTTP_PORT={HTTP_PORT}"

    steps = [
        f"chmod +x {ROOT}/lab-cluster-bootstrap.sh {ROOT}/lab-cluster-peer-start.sh {ROOT}/lab-cluster-test.sh {ROOT}/cluster-smoke-test.sh",
        f"cd {ROOT} && {env} bash lab-cluster-bootstrap.sh 2>&1 | tee loadtest/cluster-bootstrap.log; exit ${{PIPESTATUS[0]}}",
    ]
    for step in steps:
        code, _, _ = run(c, step, timeout=900)
        if code != 0:
            c.close()
            return 1

    peer_steps = [
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'mkdir -p ~/ispf-cluster/drivers'",
        f"scp -o StrictHostKeyChecking=no {ROOT}/cluster-staging/ispf-server.jar {USER}@{PEER}:~/ispf-cluster/ispf-server.jar",
        f"rsync -a {ROOT}/data/drivers/ {USER}@{PEER}:~/ispf-cluster/drivers/ 2>/dev/null || "
        f"(mkdir -p /tmp/drivers && cp -a {ROOT}/data/drivers/. /tmp/drivers/ 2>/dev/null; "
        f"scp -o StrictHostKeyChecking=no -r /tmp/drivers/. {USER}@{PEER}:~/ispf-cluster/drivers/ 2>/dev/null || true)",
        f"scp -o StrictHostKeyChecking=no {ROOT}/lab-cluster-peer-start.sh {USER}@{PEER}:~/ispf-cluster/peer-start.sh",
        f"ssh -o StrictHostKeyChecking=no {USER}@{PEER} 'chmod +x ~/ispf-cluster/peer-start.sh && "
        f"bash ~/ispf-cluster/peer-start.sh {lab_lan} ~/ispf-cluster/ispf-server.jar ~/ispf-cluster/drivers "
        f"2>&1 | tee ~/ispf-cluster/peer-start.log'",
        f"cd {ROOT} && {env} docker compose -f lab-cluster-compose.yml up -d --no-deps --force-recreate nginx",
        "sleep 15",
    ]
    for step in peer_steps:
        code, _, _ = run(c, step, timeout=900)
        if code != 0 and "rsync" not in step and "scp -r" not in step:
            print("WARN: step failed", flush=True)

    seen = set()
    for _ in range(24):
        _, out, _ = run(c, f"curl -sf http://127.0.0.1:{HTTP_PORT}/api/v1/info", timeout=30)
        try:
            seen.add(json.loads(out).get("replicaId"))
        except json.JSONDecodeError:
            pass

    print(f"\n=== Replicas in LB pool: {sorted(seen)} ===", flush=True)
    run(c, f"curl -sf http://127.0.0.1:{HTTP_PORT}/api/v1/info | python3 -m json.tool | head -20")
    run(
        c,
        f"docker compose -f {ROOT}/lab-cluster-compose.yml exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT replica_id, status FROM (SELECT replica_id, "
        f"CASE WHEN last_heartbeat_at > NOW() - INTERVAL '30 seconds' THEN 'UP' ELSE 'STALE' END AS status "
        f"FROM platform_cluster_replicas) t ORDER BY 1;\" 2>/dev/null || "
        f"docker compose -f {ROOT}/lab-cluster-compose.yml exec -T postgres psql -U ispf -d ispf -c "
        f"\"SELECT holder_id, COUNT(*) FROM platform_driver_locks GROUP BY holder_id ORDER BY 1;\"",
    )
    run(c, f"ISPF_LAB_CLUSTER_HTTP_PORT={HTTP_PORT} bash {ROOT}/lab-cluster-test.sh 2>&1 | tee {ROOT}/loadtest/cluster-8000.log", timeout=900)

    c.close()
    if len(seen) >= 4:
        print(f"\nOK: cluster on :{HTTP_PORT} with {len(seen)} nodes", flush=True)
        return 0
    print(f"\nWARN: expected 4 replicas, got {sorted(seen)}", flush=True)
    return 1 if len(seen) < 3 else 0


if __name__ == "__main__":
    raise SystemExit(main())
