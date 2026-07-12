# Local deploy overrides (gitignored)

This directory is **not** in git. Copy this file to `README.md` on first use.

## Lab historian stress

1. Copy templates from [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) to `~/ispf` on lab hosts.
2. Copy env files here or on the server with real values:
   - `lab-loadgen.env` — LAN IPs, `user@host` SSH, `MQTT_PUBLISH_HOST`
   - `lab-db.env` — DB passwords
   - `lab-stress.env` / `lab-stress-ch.env` — optional JVM tuning
3. Workstation SSH: `deploy/lab_ssh.py` from `deploy/lab_ssh.example.py` (if present) or your own helper — **never commit**.

## Other lab / VPS ops

Gitignored patterns: see root [`.gitignore`](../../.gitignore) (`deploy/lab-*`, `deploy/vps-*`, `deploy/run_lab_*`, …).

Universal rollout scripts remain in [`deploy/`](../README.md).
