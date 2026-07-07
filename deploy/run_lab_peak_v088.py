#!/usr/bin/env python3
"""Deploy 0.9.88 (Scylla partition batching) + peak 16×32k on lab."""
from __future__ import annotations

import paramiko
import sys
from pathlib import Path
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"


def run(c, cmd, timeout=7200):
    print(">", cmd[:130], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out:
        print(out[-6000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2000:], flush=True)
    print("exit", code, flush=True)
    return code, out, err


def upload_text(sftp, local: Path, remote: str) -> None:
    with sftp.file(remote, "w") as f:
        f.write(local.read_bytes().replace(b"\r\n", b"\n"))


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    sftp = c.open_sftp()

    print("Upload JAR 0.9.88...", flush=True)
    sftp.put(str(DEPLOY / "staging" / "ispf-server.jar"), f"{ROOT}/ispf-server.jar")

    for name, remote in [
        ("lab-stress.env", f"{ROOT}/lab-stress.env"),
        ("lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
        ("setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
        ("mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
        ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
        ("lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
    ]:
        upload_text(sftp, DEPLOY / name, remote)
        print("  uploaded", name, flush=True)
    sftp.close()

    steps = [
        f"chmod +x {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh {ROOT}/lab-emqtt-cleanup.sh",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
        f"cd {ROOT} && for i in $(seq 1 90); do curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 "
        f"&& break; sleep 3; done && curl -sf http://127.0.0.1:8000/api/v1/info",
        f"cd {ROOT} && DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 "
        f"PROBE_TOTAL_RATE=96000 AUTO_CALIBRATE=true CALIBRATE_WARMUP=20 CALIBRATE_PHASE=45 "
        f"CALIBRATE_COOLDOWN=30 CALIBRATE_MARGIN=0.98 EMQTT_SHARD_MAX=8 bash lab-mqtt-event-journal-multi-test.sh "
        f"2>&1 | tee {ROOT}/loadtest/peak-v088.log",
        f"grep -E 'Events/s total|Efficiency|version|Journal events' {ROOT}/loadtest/peak-v088.log | tail -10",
        f"curl -sf http://127.0.0.1:8000/api/v1/platform/metrics | python3 -c "
        f"\"import json,sys; a=json.load(sys.stdin).get('sections',{{}}).get('automation',{{}}); "
        f"print('queue',a.get('eventJournalQueueSize'),'sync_fallback',a.get('eventJournalSyncFallbackTotal'),'flushed',a.get('eventJournalFlushedTotal'))\"",
        "docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' "
        f"$(docker compose --env-file {ROOT}/lab-stress.env -f {ROOT}/lab-test-host-compose.yml ps -q)",
    ]

    log_parts = []
    for step in steps:
        code, out, err = run(c, step, timeout=7200)
        log_parts.append(f"=== {step[:100]} ===\n{out}\n{err}\nexit={code}\n")
        if code != 0 and "peak-v088" not in step and "grep" not in step:
            break
    c.close()

    text = "".join(log_parts)
    Path(REPO / "tmp_peak_v088_result.txt").write_text(text, encoding="utf-8")
    print("\n=== SUMMARY ===", flush=True)
    for line in text.splitlines():
        if any(k in line for k in ("Events/s", "Efficiency", "version", "queue", "sync_fallback", "ispf-server", "scylla")):
            print(line)
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    raise SystemExit(main())
