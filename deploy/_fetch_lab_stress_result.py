#!/usr/bin/env python3
import paramiko
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = "/home/iot-solutions/ispf"
HOST, PORT, USER, PW = "84.42.21.226", 5031, "iot-solutions", "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"


def run(c, cmd, timeout=120):
    print(">", cmd[:120], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    text = out if out.strip() else err
    if text.strip():
        print(text[-10000:], flush=True)
    print("exit", code, flush=True)
    return code, out


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, PW, timeout=60)

    run(c, 'docker ps --format "table {{.Names}}\t{{.Status}}"')
    run(c, f"tail -100 {ROOT}/loadtest/stress-run.log 2>/dev/null || echo NO_LOG")
    run(
        c,
        f"grep -E 'Events/s|Efficiency|aggregate|Target|Journal|queue|sync_fallback' "
        f"{ROOT}/loadtest/stress-run.log 2>/dev/null | tail -30",
    )
    run(c, "curl -sf http://127.0.0.1:8000/api/v1/info | python3 -m json.tool | head -15 || echo ISPF_DOWN")
    run(
        c,
        f"curl -sf http://127.0.0.1:8000/api/v1/platform/metrics | python3 -c "
        f"\"import json,sys; a=json.load(sys.stdin).get('sections',{{}}).get('automation',{{}}); "
        f"print('queue',a.get('eventJournalQueueSize'),'flushed',a.get('eventJournalFlushedTotal'),"
        f"'sync_fallback',a.get('eventJournalSyncFallbackTotal'))\" 2>/dev/null || true",
    )
    run(
        c,
        f"docker stats --no-stream --format '{{{{.Name}}}} {{{{.CPUPerc}}}} {{{{.MemUsage}}}}' "
        f"$(docker compose --env-file {ROOT}/lab-stress.env -f {ROOT}/lab-test-host-compose.yml ps -q 2>/dev/null)",
    )
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
