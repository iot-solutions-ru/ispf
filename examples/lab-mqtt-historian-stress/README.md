# Lab: MQTT historian stress — deploy templates

Anonymized compose, env, and benchmark scripts for [docs/en/lab-mqtt-historian-stress.md](../../docs/en/lab-mqtt-historian-stress.md) (RU: [docs/ru/lab-mqtt-historian-stress.md](../../docs/ru/lab-mqtt-historian-stress.md)).

Addresses use [RFC 5737](https://datatracker.ietf.org/doc/html/rfc5737) documentation ranges (`198.51.100.x`). Passwords are placeholders (`CHANGE_ME_*`). Replace on your site before running.

## Layout

| Path | Purpose |
|------|---------|
| `compose/` | Docker Compose for ISPF app, loadgen (Mosquitto), DB (PG + Scylla + CH) |
| `env/` | Stress profiles (`lab-stress.env`, `lab-stress-ch.env`) and topology (`lab-loadgen.env`, `lab-db.env`) |
| `scripts/` | Bootstrap, emqtt remote, I-01 historian benchmarks (Scylla / ClickHouse), **I-03 event journal** (smoke / peak / 400k fan-out) |
| `local/README.example.md` | How to keep real hosts and secrets **out of git** |

## On lab hosts

Copy to the same directory on both application and loadgen hosts (e.g. `~/ispf`):

```bash
REPO=examples/lab-mqtt-historian-stress
DEST=~/ispf
mkdir -p "$DEST"
cp "$REPO"/compose/*.yml "$DEST"/
cp "$REPO"/env/*.env "$DEST"/
cp "$REPO"/scripts/*.sh "$DEST"/
chmod +x "$DEST"/*.sh
```

Edit `lab-loadgen.env` and `lab-db.env` with your LAN IPs, SSH `user@host`, and passwords. Do **not** commit those edits.

## Quick run (after bootstrap)

On **loadgen / DB host**:

```bash
bash lab-db-bootstrap.sh
bash lab-loadgen-bootstrap.sh
```

On **application host**:

```bash
docker compose --env-file lab-stress.env -f lab-test-host-compose.yml up -d --force-recreate ispf-server nginx
DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-test.sh
```

ClickHouse store: swap `lab-stress.env` → `lab-stress-ch.env`, run `lab-single-mqtt-historian-ch-test.sh`.

**I-03 event journal** (smoke / peak / ~400k fan-out): copy `setup-mqtt-event-journal-devices.py` from gitignored `deploy/` to `~/ispf/loadtest/`, then:

```bash
bash lab-single-mqtt-event-journal-test.sh          # smoke: 4×500 msg/s
bash lab-run-event-journal-peak.sh                    # peak per-topic: 16×32k
bash lab-run-event-journal-400k.sh                    # ~400k events/s (MQTT fan-out)
```

See [lab-mqtt-event-journal-ingress](../../docs/en/lab-mqtt-event-journal-ingress.md) (RU: [docs/ru/...](../../docs/ru/lab-mqtt-event-journal-ingress.md)).

Gateway scale (50k / 100k msg/s, scenario I-02 extension): orchestration scripts live in **gitignored** `deploy/lab-*` and `deploy/mqtt_loadtest_lib.py` on the operator workstation — copy to `~/ispf` on lab hosts. Committed topology template: `env/lab-loadgen.env` (`198.51.100.10` loadgen, `198.51.100.11` app). See [lab-mqtt-gateway-ingress](../../docs/en/lab-mqtt-gateway-ingress.md).

## Operator-only files (gitignored)

Real lab copies live under `deploy/lab-*`, `deploy/lab_ssh.py`, `deploy/local/` — see [deploy/README.md](../../deploy/README.md) and root `.gitignore`. Workstation SSH helpers are never committed.
