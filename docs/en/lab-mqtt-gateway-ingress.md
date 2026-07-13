> **Language:** Canonical English. Russian edition: [ru/lab-mqtt-gateway-ingress.md](../ru/lab-mqtt-gateway-ingress.md).

# Lab: MQTT gateway ingress (scenario I-02)

Scenario **I-02-mqtt-gateway** validates **one MQTT driver → dispatch to child sensors → historian**, unlike **I-01**, where each device has its own mqtt driver.

**Purpose:** confirm `mqtt-gateway-v1` ingests MQTT, runs `dispatchTelemetry`, creates child sensors (lazy `instantiateModelIfMissing`), and writes historian samples under `TELEMETRY_ONLY` (no event journal growth).

**Where to run:** lab with **split topology** (ISPF on the application host, Mosquitto + emqtt on the loadgen host). Real addresses live in local `deploy/lab-*.env` only (do not commit).

## Pipeline

```text
emqtt-bench (loadgen host, NUMERIC_PAYLOAD)
    → Mosquitto (tcp://<loadgen-host>:1883)
    → 1× mqtt driver on loadtest-mqtt-gateway (TELEMETRY_ONLY)
    → lastIngress → dispatchTelemetry
    → up to N child sensors (loadtest-mqtt-sensor-00001…)
    → temperature → TelemetryHistorianFastPath → variable_samples (Scylla)
```

Ordered suite defaults: **4** topics, **~2000 msg/s** total, **60 s** measure phase, **20 s** warmup, gateway `telemetryCoalesceMs=50`.

## Run (single-node, split topology)

On the application host the broker is **not** `tcp://mqtt:1883` (no Mosquitto in the app compose). Use addresses from `lab-loadgen.env`:

```bash
cd ~/ispf/loadtest
# shellcheck source=../lab-loadgen.env
source ../lab-loadgen.env

ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
python3 mqtt-ingress-load-test.py \
  --base-url http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000} \
  --mode push \
  --broker-url "tcp://${ISPF_MQTT_BROKER_HOST}:${ISPF_MQTT_BROKER_PORT}" \
  --publisher emqtt-bench \
  --publish-via-ssh "${ISPF_LAB_LOADGEN_SSH}" \
  --remote-deploy-dir "${ISPF_LAB_LOADGEN_ROOT}/loadtest" \
  --emqtt-interval-ms 10 \
  --devices 4 --messages-per-second 2000 \
  --phase-seconds 60 --warmup-seconds 20 \
  --skip-monitor-setup \
  --gateway --telemetry-coalesce-ms 50
```

Via scenario wrapper (soft reset between runs):

```bash
cd ~/ispf
bash lab-single-scenario-run.sh --soft-reset I-02-mqtt-gateway -- \
  'cd ~/ispf/loadtest && source ../lab-loadgen.env && \
   ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
   python3 mqtt-ingress-load-test.py ...'   # as above
```

From a workstation (upload scripts + SSH):

```bash
python deploy/run_lab_ordered_suite.py --topology single --only I-02 --reset soft
```

## PASS criteria (script)

| Check | Condition |
|-------|-----------|
| Gateway driver | `RUNNING` + `connected=true` |
| Ingress | live MQTT before measure phase (gateway or child `temperature`) |
| Historian | flushed delta ≥ 50 over 60 s window, rate ≥ 0.5 samples/s |
| Event journal | `event_history` does not grow (`TELEMETRY_ONLY`) |

A successful run prints `=== I-02 PASS ===`.

## What `mqtt-ingress-load-test.py --gateway` does

| Step | Action |
|------|--------|
| 1 | Cleanup loadtest mqtt devices |
| 2 | Seed: 1× `loadtest-mqtt-gateway`; children created only on first dispatch |
| 3 | Stabilize gateway driver (~30 s) |
| 4 | emqtt on loadgen via SSH; `NUMERIC_PAYLOAD=true` (body `25.0`) |
| 5 | Warmup, wait for ingress on `loadtest-mqtt-sensor-00001` |
| 6 | PATCH `historySampleMode=ALL_VALUES` on all child sensors that appeared |
| 7 | Measure 60 s: delta from **`variableHistoryFlushedTotal`** (not `sampleCount`) |

## Pitfalls (I-02)

| Problem | Symptom | Fix |
|---------|---------|-----|
| Broker `tcp://mqtt:1883` on split lab | ingress ~0, idle gateway | `ISPF_MQTT_BROKER_HOST` + `--publish-via-ssh` from `lab-loadgen.env` |
| Historian metric `sampleCount` | FAIL with live historian after I-01 stress | Delta from **`variableHistoryFlushedTotal`** / `flushedTotal`; Scylla `COUNT(*)` returns 0/timeout |
| `CHANGES_ONLY` (default since 0.9.139) + `NUMERIC_PAYLOAD` | few or zero child samples | `ALL_VALUES` on child `temperature`; instance type `mqtt-gateway-sensor-v1` includes `historySampleMode: ALL_VALUES` |
| `NUMERIC_PAYLOAD` missing on remote emqtt | timestamp payload, unstable parse | Prefix `NUMERIC_PAYLOAD=true` in SSH `mqtt-emqtt-bench.sh` command |
| Short publisher duration | measure phase without traffic | duration ≥ warmup + wait + phase (~200 s) |
| Stale `lastIngress` + constant payload | ingress WARN with working dispatch | Accept live child `temperature` or waive gate when historian delta ≥ 50 |
| Soft reset, Scylla on remote DB host | preflight exit 1, `scylla_count=-1` | `lab-single-soft-reset.py` truncates via `ISPF_LAB_DB_SSH`; `--skip-scylla-verify` when COUNT is slow |
| Long load / API 403 | login or metrics 403 | Restart `ispf-server` + nginx on application host |

## Baseline (lab, split topology)

Stand: single-node ISPF on application host, Scylla + Mosquitto on loadgen host, historian store **Scylla**, stress env (8M historian queue). After I-01 runs on the same stand `variable_samples` is huge — trust **flushed** counters, not `sampleCount`.

| Parameter | Value |
|-----------|-------|
| Topics / devices | 4 |
| Publish rate | ~2000 msg/s |
| Historian rate (measure) | **~2.3k samples/s** (flushed delta) |
| Child sensors | 4 (lazy) |
| Gateway coalesce | 50 ms |
| Outcome | **I-02 PASS** |

Absolute samples/s depend on hardware and stress profile; the table is a **single-lab regression** reference, not prod SLA.

## Gateway scale (50k eager, split lab)

**1× mqtt-gateway → 50k pre-instantiated child sensors → `dispatchTelemetry` → historian** (2 metrics: `temperature`, `humidity`). Orchestrator on the application host; emqtt on loadgen.

**Anonymization (git):** committed docs use RFC 5737 addresses (`198.51.100.x`, see [`examples/lab-mqtt-historian-stress/env/lab-loadgen.env`](../../examples/lab-mqtt-historian-stress/env/lab-loadgen.env)) and placeholders only. Gateway scale scripts (`deploy/lab-*`, `deploy/setup-mqtt-gateway-scale-devices.py`, `deploy/mqtt_loadtest_lib.py`) are **gitignored** — real SSH hosts and secrets stay on the operator machine. Before push: `python deploy/tools/anonymize-repo.py` ([documentation-audit](documentation-audit.md#anonymization-policy-public-docs)).

**Required on ISPF:** `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=0` (otherwise 5000 ms debounce caps throughput).

**`MESSAGES_PER_SECOND`** is the **aggregate** publish rate across all topics, not per device. With 50k×2 metrics = 100k topics: `MESSAGES_PER_SECOND=100000` ≈ 2 msg/s per device (1 Hz per metric).

### Initial seed (hard reset)

```bash
cd ~/ispf
# scripts — copy of gitignored deploy/lab-* on the lab host
RESET_MODE=hard INSTANCES=50000 SEED_PARALLEL_WORKERS=8 \
  MESSAGES_PER_SECOND=10000 WARMUP=60 PHASE=120 EMQTT_SHARD_MAX=32 \
  bash lab-gateway-scale-run-from-loadgen.sh
```

### Measure-only (tree already seeded)

Do not recreate the gateway or 50k children — restart driver and benchmark only:

```bash
cd ~/ispf
bash lab-run-gateway-100k-measure.sh   # API wait + --ensure-driver-only + 90s stabilize + emqtt
```

Or manually on loadgen (without ensure-driver on app host):

```bash
EMQTT_LOCAL=1 SKIP_SEED=1 INSTANCES=50000 MESSAGES_PER_SECOND=100000 \
  WARMUP=60 PHASE=120 EMQTT_SHARD_MAX=64 \
  ISPF_LAB_API_BASE=http://198.51.100.11:8000 bash lab-single-mqtt-50k-gateway-test.sh
```

### 100k msg/s tuning + recreate

Patches `lab-stress.env`, then `docker compose recreate ispf-server`. Compose **must** see the PG password — load **`lab-db.env`** (`ISPF_DB_PASSWORD`, gitignored):

```bash
cd ~/ispf
bash lab-tune-gateway-100k.sh          # tune + recreate + API wait
bash lab-run-gateway-100k-measure.sh   # measure-only (no re-seed)
# or both:
bash lab-run-gateway-100k-v3.sh
```

Key env (v2/v3, lab 0.9.144): `INGRESS_DISPATCH_COALESCE=false`, `INGRESS_BYPASS_L3=true`, `MIN_INTERVAL_MS=0`, `OVERFLOW_COALESCE=false`, `MQTT_CALLBACK_THREADS=256`. Thread caps: v1 **64**, v2 **96**, v3 **128** (dispatch + writer + Scylla parallel batches).

### Results (lab 0.9.144, split topology, Jul 2026)

| Stage | Target | Mosquitto RX | Historian flushed | Queue | Outcome |
|-------|--------|--------------|-------------------|-------|---------|
| 10k baseline | 10k msg/s | ~11.2k | ~22.4k samples/s | ~0 | PASS |
| 100k defaults | 100k msg/s | ~109.5k | ~37.5k | ~0 | FAIL |
| v1 tune (64 workers) | 100k | ~109.4k | ~91.7k | 8M | PASS |
| v2 tune (96 workers) | 100k | ~109.4k | ~94.6k | 8M→5.5M (60s drain) | PASS |
| **v3 tune (128 workers)** | 100k | **~109.5k** | **~84.0k** | **8M→0** (120s drain) | **PASS** |

| Parameter | Value |
|-----------|-------|
| Child instances | 50,000 (eager) |
| MQTT topics | 100,000 (2 metrics) |
| Seed workers | **8** (16+ overloads nginx/API) |
| emqtt shards | 32 (10k) / **64** (100k) |
| PASS | flushed ≥ 25% target × metrics; mosquitto rx ≥ 25% target; emqtt `failed_workers=0` |

Remaining gap (~84–95k flushed vs ~109k MQTT): historian writer + Scylla ingest; 8M queue — tune `WRITER_THREADS_MAX`, `CASSANDRA_PARALLEL_BATCHES`, partition batches.

### After `recreate ispf-server`

The gateway driver does **not** reconnect automatically → historian **0** while Mosquitto RX may still be healthy.

**Correct:** `--ensure-driver-only` (restart driver, no delete/create of the tree):

```bash
python3 loadtest/setup-mqtt-gateway-scale-devices.py \
  --instances 50000 --telemetry-coalesce-ms 0 \
  --base-url http://127.0.0.1:8000 \
  --broker-url "tcp://${ISPF_MQTT_BROKER_HOST}:${ISPF_MQTT_BROKER_PORT}" \
  --ensure-driver-only
sleep 90
```

**Wrong:** calling setup with `--skip-cleanup` immediately after recreate on a cold API — nginx may return **502**; legacy paths attempted `_create_object` even when the gateway already existed.

`lab-run-gateway-100k-measure.sh` waits for API (up to 60×5s), runs `--ensure-driver-only`, then stabilizes 90s.

### Pitfalls (gateway scale)

| Issue | Symptom | Fix |
|-------|---------|-----|
| Stale `mqtt-emqtt-bench.sh` on loadgen | ~400 msg/s, `failed_workers=8` | sync via `lab-gateway-scale-run-from-loadgen.sh` |
| API purge 30k+ instances | hours | `RESET_MODE=hard`, not `purge` |
| 16+ parallel seed workers | connection reset mid-seed | `SEED_PARALLEL_WORKERS=8` |
| `recreate` without ensure driver | historian=0 | `--ensure-driver-only` + 90s stabilize |
| 502 right after recreate | setup fails on `POST /objects` | API wait; do not re-create existing gateway |
| PG password after compose | `password authentication failed` | `ISPF_DB_PASSWORD` from `lab-db.env` |
| CRLF from Windows upload | `set: pipefail: invalid option` | `tr -d '\r'` on lab or pipe via `bash -s` |

Scale scripts (**gitignored**, under `deploy/`): `lab-gateway-scale-run-from-loadgen.sh`, `lab-single-mqtt-50k-gateway-test.sh`, `lab-tune-gateway-100k.sh`, `lab-run-gateway-100k-measure.sh`, `lab-run-gateway-100k-v3.sh`, `setup-mqtt-gateway-scale-devices.py`, `mqtt_loadtest_lib.py` (`--eager`, `--ensure-driver-only`).

## I-01 vs I-02

| | I-01 | I-02 |
|---|------|------|
| MQTT connections | N drivers (per device) | 1 gateway |
| Historian target | `temperature` on each device | `temperature` on child after dispatch |
| Typical rate | 250k–500k msg/s (stress) | ~2k msg/s (ingress suite) |
| Primary metric | `variableHistoryFlushedTotal` | same (**not** `sampleCount`) |

See [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) for historian flood and loadtest `ALL_VALUES` seeding.

## Key files

| File | Role |
|------|------|
| `deploy/mqtt-ingress-load-test.py` | I-02/I-04/I-08 body; `--gateway`, PASS/FAIL |
| `deploy/mqtt_loadtest_lib.py` | Gateway seed, `enable_gateway_children_history`, flushed total |
| `deploy/mqtt-emqtt-bench.sh` | emqtt; `NUMERIC_PAYLOAD`, shards |
| `deploy/lab-single-scenario-run.sh` | Single-node wrapper + reset |
| `deploy/lab-single-soft-reset.py` | Soft reset + remote Scylla truncate |
| `deploy/run_lab_ordered_suite.py` | Suite I-01…I-08; `SINGLE_LOADTEST_COMMON` with loadgen broker |
| `deploy/lab-loadgen.env` | `ISPF_MQTT_BROKER_HOST`, `ISPF_LAB_LOADGEN_SSH` (local, gitignored) |
| `deploy/lab-tune-gateway-100k.sh` | Stress env patch + recreate ispf-server (100k gateway) **(gitignored)** |
| `deploy/lab-run-gateway-100k-measure.sh` | Measure-only: API wait + `--ensure-driver-only` + benchmark **(gitignored)** |
| `deploy/lab-run-gateway-100k-v3.sh` | tune + measure (v3, 128 threads) **(gitignored)** |
| `examples/lab-mqtt-historian-stress/env/lab-loadgen.env` | Anonymized split-lab topology (in git) |

Metrics: `GET /api/v1/platform/metrics` → `automation.variableHistoryFlushedTotal`, `variableHistory.flushedTotal`.

## Related documents

- [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — I-01 historian flood
- [load-testing](load-testing.md) — ordered suite, clean slate
- [decisions/0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md) — gateway dispatch
- [variable-history](variable-history.md) — `historySampleMode`, CHANGES_ONLY vs ALL_VALUES
