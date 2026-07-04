#!/usr/bin/env python3
"""Peak test 16×32k with enlarged queues + metrics sampling."""
from __future__ import annotations

import json
import paramiko
import sys
import time
from pathlib import Path

ROOT = "/home/iot-solutions/ispf"
REPO = Path(__file__).resolve().parents[1]
DEPLOY = REPO / "deploy"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"

UPLOAD = [
    ("lab-stress.env", f"{ROOT}/lab-stress.env"),
    ("lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
    ("setup-mqtt-event-journal-devices.py", f"{ROOT}/loadtest/setup-mqtt-event-journal-devices.py"),
    ("mqtt_loadtest_lib.py", f"{ROOT}/loadtest/mqtt_loadtest_lib.py"),
    ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
    ("lab-emqtt-cleanup.sh", f"{ROOT}/lab-emqtt-cleanup.sh"),
]
JAR_LOCAL = REPO / "deploy" / "staging" / "ispf-server.jar"
DRIVER_PACKS = REPO / "deploy" / "staging" / "driver-packs.tar.gz"

METRICS_SAMPLER = r"""#!/usr/bin/env bash
OUT="$1"
HTTP="${2:-8000}"
while true; do
  ts=$(date -Is)
  m=$(curl -sf --max-time 3 "http://127.0.0.1:${HTTP}/api/v1/platform/metrics" 2>/dev/null || echo "")
  if [ -n "$m" ]; then
    q=$(echo "$m" | python3 -c "import json,sys; d=json.load(sys.stdin); a=d.get('sections',{}).get('automation',{}); print(a.get('eventJournalQueueSize',0), a.get('eventJournalSyncFallbackTotal',0), a.get('eventsFiredTotal',0))" 2>/dev/null || echo "? ? ?")
    echo "$ts journal_queue sync_fallback events_fired $q" >> "$OUT"
  else
    echo "$ts METRICS_DOWN" >> "$OUT"
  fi
  sleep 2
done
"""


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
    for name, remote in UPLOAD:
        with sftp.file(remote, "w") as f:
            f.write((DEPLOY / name).read_bytes().replace(b"\r\n", b"\n"))
        print("uploaded", name)
    with sftp.file(f"{ROOT}/loadtest/metrics-sampler.sh", "w") as f:
        f.write(METRICS_SAMPLER.replace("\r\n", "\n").encode())
    sftp.close()

    if JAR_LOCAL.is_file():
        sftp = c.open_sftp()
        print("uploading ispf-server.jar (0.9.88 + Scylla partition batching)...")
        sftp.put(str(JAR_LOCAL), f"{ROOT}/ispf-server.jar")
        if DRIVER_PACKS.is_file():
            sftp.put(str(DRIVER_PACKS), f"{ROOT}/staging/driver-packs.tar.gz")
        sftp.close()

    steps = [
        f"chmod +x {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh "
        f"{ROOT}/lab-emqtt-cleanup.sh {ROOT}/loadtest/metrics-sampler.sh",
        f"bash {ROOT}/lab-emqtt-cleanup.sh",
        f"cd {ROOT} && rm -f data/drivers/.extracted && mkdir -p data/drivers staging && "
        f"tar -xzf staging/driver-packs.tar.gz -C data/drivers 2>/dev/null || true",
        f"cd {ROOT} && docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server",
        f"cd {ROOT} && for i in $(seq 1 90); do curl -sf -X POST http://127.0.0.1:8000/api/v1/auth/login "
        f"-H 'Content-Type: application/json' -d '{{\"username\":\"admin\",\"password\":\"admin\"}}' >/dev/null 2>&1 "
        f"&& echo login_ok && break; sleep 3; done",
        f"curl -sf http://127.0.0.1:8000/api/v1/info | python3 -c \"import sys,json; print('version', json.load(sys.stdin).get('version'))\"",
        f"bash {ROOT}/loadtest/metrics-sampler.sh {ROOT}/loadtest/queue-peak-metrics.log 8000 & echo $! > {ROOT}/loadtest/metrics-sampler.pid",
        f"cd {ROOT} && DEVICES=16 RATE_PER_DEVICE=32000 WARMUP=20 PHASE=60 CALLBACK_THREADS=64 "
        f"CALLBACK_QUEUE_CAPACITY=500000 EMQTT_SHARD_MAX=8 bash lab-mqtt-event-journal-multi-test.sh",
        f"kill $(cat {ROOT}/loadtest/metrics-sampler.pid) 2>/dev/null || true",
        f"tail -30 {ROOT}/loadtest/queue-peak-metrics.log",
        f"curl -sf http://127.0.0.1:8000/api/v1/platform/metrics | python3 -m json.tool | head -40",
    ]
    chunks = []
    for step in steps:
        code, out, err = run(c, step, timeout=7200)
        chunks.append(f"=== {step[:90]} ===\n{out}\n{err}\nexit={code}\n")
    c.close()
    text = "".join(chunks)
    Path(REPO / "tmp_queue_peak_result.txt").write_text(text, encoding="utf-8")
    sys.stdout.buffer.write(text[-8000:].encode("utf-8", errors="replace"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
