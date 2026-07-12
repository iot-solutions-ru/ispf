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

Metrics: `GET /api/v1/platform/metrics` → `automation.variableHistoryFlushedTotal`, `variableHistory.flushedTotal`.

## Related documents

- [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — I-01 historian flood
- [load-testing](load-testing.md) — ordered suite, clean slate
- [decisions/0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md) — gateway dispatch
- [variable-history](variable-history.md) — `historySampleMode`, CHANGES_ONLY vs ALL_VALUES
