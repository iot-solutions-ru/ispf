> **Language:** Canonical English. Russian edition: [ru/lab-mqtt-historian-stress.md](../ru/lab-mqtt-historian-stress.md).

# Lab: MQTT historian stress (Scylla vs ClickHouse)

Load stand for **TELEMETRY_ONLY** historian path: mqtt driver → fast historian path → `VariableHistoryAsyncWriter` → `variable_samples` in **Scylla** or **ClickHouse**.

**Purpose:** compare historian write throughput on lab hardware with ingress coalesce and debounce **disabled** — not a prod SLA number.

**Where to run:** dedicated lab host (SSH from workstation; HTTP API via nginx edge).  
**Templates in git:** [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) — anonymized compose, env, and scripts. Copy to `~/ispf` on lab hosts and set your addresses.

Real hosts, SSH, and passwords belong in **local** `deploy/lab-*.env` and `deploy/lab_ssh.py` only (`.gitignore`, do not commit).

## Split topology

Load generators and databases are **off** the ISPF JVM host so emqtt-bench does not compete with historian writers:

| Node | Role | Compose / scripts |
|------|------|-------------------|
| **Application host** (`ISPF_LAB_CLUSTER_HOST`) | ISPF + nginx (public edge) | `lab-test-host-compose.yml` + `lab-stress.env` or `lab-stress-ch.env` |
| **Loadgen / DB host** (`ISPF_LAB_LOADGEN_HOST` / `ISPF_LAB_DB_HOST`) | PostgreSQL, Scylla, ClickHouse, Mosquitto, emqtt | `lab-db-compose.yml`, `lab-loadgen-compose.yml` |

```text
emqtt-bench (--network host, broker on loadgen host)
    → Mosquitto (loadgen container)
    → 16× mqtt driver (TELEMETRY_ONLY, ingressCoalesceEnabled=false)
    → TelemetryHistorianFastPath → VariableHistoryAsyncWriter
    → Scylla variable_samples   OR   ClickHouse variable_samples
```

Drivers on the application host subscribe via **`ISPF_MQTT_BROKER_URL`** (typically `tcp://<loadgen-host>:1883`). Publishers on the loadgen host must set **`MQTT_PUBLISH_HOST`** in `lab-loadgen.env` — the LAN address Mosquitto actually listens on, not `127.0.0.1` when the broker is bound to an external interface only.

## Example runbook (Scylla then ClickHouse)

### 0. Bootstrap DB + MQTT on loadgen host

```bash
# On application host (ISPF)
ssh "${ISPF_LAB_DB_SSH}" 'cd ~/ispf && bash lab-db-bootstrap.sh'
bash ~/ispf/lab-loadgen-bootstrap.sh
```

`ISPF_LAB_DB_SSH`, `ISPF_LAB_LOADGEN_SSH` — see `examples/lab-mqtt-historian-stress/env/lab-loadgen.env` (replace with your site values).

### 1. Scylla historian (250k and 500k msg/s)

```bash
cd ~/ispf

docker compose --env-file lab-stress.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx

curl -sf http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000}/api/v1/info

bash lab-emqtt-cleanup-remote.sh

# 16 devices × 15625 msg/s = 250k/s; phase 90s after 20s warmup
DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-test.sh

# 500k/s: RATE_PER_DEVICE=31250
DEVICES=16 RATE_PER_DEVICE=31250 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-test.sh
```

### 2. ClickHouse historian (same rates)

`ISPF_VARIABLE_HISTORY_STORE` must come from env — `lab-test-host-compose.yml` uses `${ISPF_VARIABLE_HISTORY_STORE:-scylla}`.

```bash
docker compose --env-file lab-stress-ch.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx

curl -sf http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000}/api/v1/info
docker compose -f lab-test-host-compose.yml exec ispf-server \
  printenv ISPF_VARIABLE_HISTORY_STORE   # expect clickhouse

bash lab-emqtt-cleanup-remote.sh

DEVICES=16 RATE_PER_DEVICE=15625 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-ch-test.sh

DEVICES=16 RATE_PER_DEVICE=31250 PHASE=90 WARMUP=20 INTERVAL_MS=1 EMQTT_SHARD_MAX=8 \
  bash lab-single-mqtt-historian-ch-test.sh

# Restore Scylla profile when done
docker compose --env-file lab-stress.env -f lab-test-host-compose.yml \
  up -d --force-recreate ispf-server nginx
```

From workstation: copy `examples/lab-mqtt-historian-stress/` to lab; SSH helper — `deploy/lab_ssh.py` (gitignored, see `deploy/local/README.example.md`).

## What the benchmark script does

`lab-single-mqtt-historian-test.sh` / `lab-single-mqtt-historian-ch-test.sh` (scenario **I-01 historian**):

| Step | Action |
|------|--------|
| 1 | `setup-mqtt-historian-devices.py` — 16× `loadtest-mqtt-dev-*`, `TELEMETRY_ONLY`, historian on `temperature`, `ingressCoalesceEnabled=false`, broker from `ISPF_MQTT_BROKER_URL` |
| 2 | Stabilize 15s; cleanup stale emqtt on loadgen host |
| 3 | Start `lab-emqtt-remote.sh` with `NUMERIC_PAYLOAD=true` (timestamp payloads are suppressed by `minIntervalMs`) |
| 4 | Warmup `WARMUP` s, then measure `PHASE` s |
| 5 | Compare deltas: Mosquitto `$SYS/broker/messages/received`, `variableHistoryFlushedTotal`, store row count |
| 6 | Wait for `variableHistoryQueueSize` → 0 |

### Pass criteria (script)

- Historian flushed delta ≥ 30 samples in measure window
- Store delta ≥ 40 rows (CH `count()`; Scylla `COUNT(*)` — see pitfalls)

**Primary metric for end-to-end delivery:** `Mosquitto received ≈ historian flushed ≈ store delta` (after queue drain).

## Baseline (lab, ISPF 0.9.137, 2026-07)

Stand: split topology, 16 mqtt drivers, `NUMERIC_PAYLOAD`, `minIntervalMs=0`, ingress coalesce off, queue 8M, JVM 28G, Scylla 20 SMP / 40G on DB host, ClickHouse 24.8 on same host.

| Store | Target | Mosquitto RX | Historian flushed | Store Δ/s | Queue at end |
|-------|--------|--------------|-------------------|-----------|--------------|
| **Scylla** | 250k | **257k** msg/s | **258k** samples/s | COUNT timeout* | 0 |
| **Scylla** | 500k | **520k** msg/s | **559k** samples/s | COUNT timeout* | 0 (peak ~7.9M, drained) |
| **ClickHouse** | 250k | **288k** msg/s | **288k** samples/s | **289k** rows/s | 0 |
| **ClickHouse** | 500k | **579k** msg/s | **579k** samples/s | **579k** rows/s | 0 |

\* `SELECT COUNT(*) FROM variable_samples` on Scylla **times out** after tens of millions of rows — script may print `FAIL` while historian metrics show healthy throughput. Use `variableHistoryFlushedTotal` and Mosquitto `$SYS` for Scylla verification.

**Scylla-only tuning:** `ISPF_VARIABLE_HISTORY_BENCHMARK_SPREAD_SAMPLED_AT=true` — unique `sampled_at` per message (ms clustering). **ClickHouse lab profile:** spread **off** (CH accepts high insert rate without per-series timestamp spread).

## Lab vs production

Absolute msg/s **are not comparable** across hosts. Use this table to understand **what is intentionally different** on the lab stress profile vs a typical **production stand**:

| Aspect | Lab historian stress | Prod (typical) |
|--------|----------------------|----------------|
| **Goal** | Max historian ingress + store write | Stable ops, retention, UI, mixed workloads |
| **Topology** | ISPF on dedicated app host; DB + MQTT on loadgen host | Often single VPS: ISPF + PG + CH journal + compact Scylla |
| **Historian store** | Scylla **or** CH (A/B on same hardware) | `jdbc`/Timescale default; CH journal; historian often PG ([deployment](deployment.md)) |
| **`minIntervalMs`** | **0** (every message can sample) | **5000–10000**+ (DB debounce, [demostands](demostands.md)) |
| **Ingress coalesce** | L0/L3/L4 **off**; `TELEMETRY_ONLY` | Per-device / global coalesce 1–5s for real tags |
| **MQTT callback threads** | 256, queue 500k | Tens, not hundreds |
| **Historian queue** | 8M, elastic writers 8–48 | Thousands–low tens of thousands |
| **JVM heap** | 28G, G1 tuned for flood | 4G idle / moderate prod |
| **Scylla** | Many SMP, tens of GB RAM, dedicated DB host | 1 SMP, hundreds of MB — sized for footprint, not flood |
| **ClickHouse** | Dedicated container, lab credentials | Prod playbook, retention, analytics ([clickhouse-prod-playbook](clickhouse-prod-playbook.md)) |
| **Payload** | Numeric constant (`NUMERIC_PAYLOAD`) | Real telemetry; timestamp in payload interacts with debounce |
| **Devices** | 16 synthetic loadtest topics | Real fleet + demo fixtures (prod idle disables heavy demo) |
| **Event journal** | On (Scylla test) / **off** (CH test) | CH on prod for `event_history` |
| **Fixtures** | `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false` | Prod policy: no demo fixtures on deploy |

**Prod implication:** sustained **~250–500k samples/s** into historian is a **lab ceiling** on dedicated hardware with stress env — not what prod is configured or sized for. Prod fair benches (4 dev, coalesce sweep, `min-interval-ms=100`) live in [load-testing](load-testing.md).

## Pitfalls

| Issue | Symptom | Fix |
|-------|---------|-----|
| emqtt publishes to `127.0.0.1` while Mosquitto listens on LAN | Mosquitto RX ~0, `econnrefused` | Set `MQTT_PUBLISH_HOST` in `env/lab-loadgen.env` (loadgen host address) |
| CH env ignored | `printenv` shows `scylla` | `ISPF_VARIABLE_HISTORY_STORE: "${ISPF_VARIABLE_HISTORY_STORE:-scylla}"` in compose |
| nginx 502 after recreate | API down 3–5 s | Always `up -d --force-recreate ispf-server nginx` together |
| Scylla `COUNT(*)` FAIL | delta 0, flushed OK | Expected at high row count; trust flushed + Mosquitto |
| CH script `pipefail` error | exit 2 | LF line endings: `sed -i 's/\r$//' lab-single-mqtt-historian-ch-test.sh` |
| Timestamp MQTT payload | ~1 sample/device | Use `NUMERIC_PAYLOAD=true` in benchmark scripts |
| `historySampleMode` CHANGES_ONLY (0.9.139+) | 0 flushed with constant payload without ALL_VALUES | `enable_variable_history(..., historySampleMode=ALL_VALUES)` in loadtest seed |
| `Done (formula estimate)` | Inflated msg/s | Not measured; use `$SYS` and ISPF metrics |

## Key files

Templates in repo: [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/).

| File | Purpose |
|------|---------|
| `compose/lab-test-host-compose.yml` | ISPF + nginx on application host; parameterized historian store |
| `env/lab-stress.env` | Scylla historian stress (spread, 8M queue, journal on) |
| `env/lab-stress-ch.env` | ClickHouse historian stress (journal off) |
| `compose/lab-db-compose.yml` / `scripts/lab-db-bootstrap.sh` | PG + Scylla + CH on DB host |
| `env/lab-loadgen.env` / `compose/lab-loadgen-compose.yml` | Mosquitto + `MQTT_PUBLISH_HOST`, SSH aliases |
| `scripts/lab-single-mqtt-historian-test.sh` | Scylla I-01 benchmark |
| `scripts/lab-single-mqtt-historian-ch-test.sh` | ClickHouse I-01 benchmark |
| `scripts/lab-emqtt-remote.sh` | emqtt on loadgen via SSH |

Metrics: `GET /api/v1/platform/metrics` → `automation.variableHistoryFlushedTotal`, `variableHistoryQueueSize`.

## Related documents

- [load-testing](load-testing.md) — general MQTT / historian scenarios
- [lab-mqtt-gateway-ingress](lab-mqtt-gateway-ingress.md) — I-02 gateway dispatch → child historian
- [lab-mqtt-event-journal-ingress](lab-mqtt-event-journal-ingress.md) — I-03 EVENT_JOURNAL_ONLY → Scylla journal (smoke / peak / 400k fan-out)
- [lab-event-journal-stress](lab-event-journal-stress.md) — EVENT_JOURNAL_ONLY (events/s, not samples)
- [variable-history](variable-history.md) — historian model and stores
- [demostands](demostands.md) — prod deploy profiles
- [0026-elastic-telemetry-ingress](decisions/0026-elastic-telemetry-ingress.md) — ingress pipeline
