#!/usr/bin/env python3
"""Lab broker + ISPF driver environment check."""
import sys
from pathlib import Path

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE



def run(c, cmd, timeout=120):
    print(">", cmd[:140], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    if out:
        print(out[-10000:], flush=True)
    if err.strip():
        print("STDERR:", err[-2000:], flush=True)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)
    run(c, "docker ps --format '{{.Names}} {{.Image}}' | grep -iE 'mqtt|mosq|emqx|ispf'")
    run(c, "cd ~/ispf && docker compose ps 2>/dev/null || docker-compose ps 2>/dev/null || true")
    run(c, "curl -s http://127.0.0.1:8000/api/v1/platform/metrics | python3 -c \"import json,sys; d=json.load(sys.stdin); print([s for s in d.get('sections',[]) if s.get('id')=='drivers'])\"")
    run(c, "docker ps -q | head -1 | xargs -I{} docker logs {} --tail 5 2>&1 | head -20 || true")
    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
