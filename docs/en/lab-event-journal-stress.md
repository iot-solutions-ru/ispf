> **Language:** Canonical English. Russian edition: [ru/lab-event-journal-stress.md](../ru/lab-event-journal-stress.md).

# Lab: event journal stress (Scylla + MQTT)

Load stand for **EVENT_JOURNAL_ONLY** fast path ([ADR-0027](decisions/0027-event-journal-ingress-fast-path.md)): mqtt driver → `fireIngress` → `EventJournalAsyncWriter` → Scylla `event_history`.

**Host:** `84.42.21.226`, SSH port `5031`, user `iot-solutions`  
**SSH alias:** `ispf-lab` (Ed25519 key `~/.ssh/ispf_lab_ed25519`)  
**HTTP:** `http://84.42.21.226:8000` (lab nginx)  
**Stack:** `~/ispf` — `deploy/lab-test-host-compose.yml` + `deploy/lab-stress.env`

### Workstation SSH (one-time)

```bash
# First run only — password via ISPF_LAB_PASSWORD env, never commit it
python deploy/local/tools/lab-ssh-install-key.py
ssh ispf-lab   # thereafter
```

Copy `deploy/local/lab_ssh.example.py` → `deploy/lab_ssh.py` (gitignored) if missing; Python lab scripts use `connect_ssh()`.

## Quick start

```bash
# On lab host (after deploy artifacts to ~/ispf)
cd ~/ispf
set -a && . lab-stress.env && set +a

# Full clean run (wipe volumes + benchmark)
bash lab-test-host-clean-run.sh

# Peak / max load (16×32k target, 8 emqtt shards)
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 SKIP_DEVICE_SETUP=false \
  bash lab-mqtt-event-journal-multi-test.sh

# Sustained (~calibrated rate)
python3 loadtest/../run_lab_sustained.py   # or from Windows: deploy/run_lab_sustained.py

# Auto-calibrate sustained target from actual journal
AUTO_CALIBRATE=true CALIBRATE_RUN_MAIN=true bash lab-mqtt-event-journal-multi-test.sh
```

From Windows (upload + run):

```powershell
python deploy/run_lab_max_load.py      # peak + summary
python deploy/run_lab_clean_run.py     # factory reset + sustained
python deploy/run_lab_peak_v088.py     # deploy JAR + peak
```

## Run architecture

```text
emqtt-bench (Docker, sharded --topics-payload)
    → Mosquitto (sys_interval 1, $SYS counters)
    → 16× mqtt driver (ingressCoalesceEnabled=false, EVENT_JOURNAL_ONLY)
    → TelemetryEventJournalFastPath → EventService.fireIngress
    → EventJournalAsyncWriter (24 threads) → Scylla event_history
```

**Not involved:** object-change bus (`ISPF_OBJECT_CHANGE_ASYNC_ENABLED=false`), historian, alert CEL, HTTP `/events/fire`.

### Ingress levels

| Level | Setting | Bench |
|-------|---------|-------|
| L0 MQTT driver | `ingressCoalesceEnabled` | **false** (`--bench-no-l0-coalesce`) |
| L1 server buffer | `usesDirectIngress` | **bypass** for `EVENT_JOURNAL_ONLY` |
| Journal queue | 5M capacity, 24 writers | not limit at peak |

## Scripts

| File | Purpose |
|------|---------|
| `deploy/lab-test-host-compose.yml` | postgres + scylla + mqtt + ispf-server + nginx |
| `deploy/lab-stress.env` | JVM, Scylla SMP/RAM, journal writer, MQTT callback defaults |
| `deploy/lab-mqtt-event-journal-multi-test.sh` | Multi-device benchmark + metrics |
| `deploy/lab-test-host-clean-run.sh` | `docker compose down -v` + benchmark |
| `deploy/lab-emqtt-cleanup.sh` | Stop orphaned emqtt-bench containers |
| `deploy/mqtt-emqtt-bench.sh` | emqtt-bench wrapper (sharded, CPU cap) |
| `deploy/setup-mqtt-event-journal-devices.py` | Seed N devices, EVENT_JOURNAL_ONLY |
| `deploy/run_lab_*.py` | Upload + SSH orchestration from Windows |

### Benchmark parameters

| Env | Default | Description |
|-----|---------|-------------|
| `DEVICES` | 8 | Number of mqtt loadtest devices |
| `RATE_PER_DEVICE` | 2000 | **Configured** MQTT target (msg/s per device) |
| `WARMUP` / `PHASE` | 15 / 60 | Warmup and measurement seconds |
| `INTERVAL_MS` | 1 | emqtt interval (1 ms → up to 1000 msg/s per client) |
| `EMQTT_SHARD_MAX` | 4 | Max emqtt Docker containers (lab-stress: 4; peak: 8) |
| `EMQTT_CPU_LIMIT` | 1.5 | `--cpus` per shard (protect ISPF/Scylla from starvation) |
| `BENCH_NO_L0_COALESCE` | true | `ingressCoalesceEnabled=false` on drivers |
| `AUTO_CALIBRATE` | false | Probe → `RATE_PER_DEVICE` from journal |
| `SKIP_DEVICE_SETUP` | false | Skip re-seed (after **mqtt** recreate needs false) |

## Metrics and efficiency

Script `lab-mqtt-event-journal-multi-test.sh` prints **three layers** of throughput for measurement phase:

| Metric | Source | Meaning |
|--------|--------|---------|
| Mosquitto PUBLISH in | `$SYS/broker/messages/received` | All PUBLISH from emqtt clients |
| Mosquitto delivered | `$SYS/broker/messages/sent` | Delivery to subscribers |
| ISPF eventsFired | `/api/v1/platform/metrics` | Accepted by fast path |
| Journal flushed | metrics | Written by async writer |
| Journal (Scylla meta) | `event_journal_meta.total_count` | Global counter (may lag with asyncCounter) |

### Efficiency (how to read)

| Line | Interpretation |
|------|----------------|
| **Journal vs configured target** | Journal / (`DEVICES × RATE_PER_DEVICE`). **Reference only** — emqtt often does not reach target (CPU cap, ceil clients). Stable "~17–22%" **does not mean** loss in ISPF. |
| **Journal vs emqtt formula** | Journal / (clients × 1000/interval × devices). Formula is **not a measurement**, script calculation. |
| **ISPF capture (fired/delivered)** | eventsFired / Mosquitto delivered. On QoS0 with many emqtt clients per topic may be <100% (subscriber cannot keep up). |
| **Scylla meta vs eventsFired** | Should be **~100%** after queue drain — no loss on journal path. |

**Primary no-loss metric:** `eventsFired ≈ flushed ≈ meta` (100%).

Mosquitto `$SYS` requires `sys_interval 1` in `deploy/mosquitto/mosquitto.conf`; after change: `docker compose ... up -d --force-recreate mqtt` and re-seed drivers.

## Baseline (lab, ISPF 0.9.88, Scylla 20 SMP / 48G)

Stand: 16 devices, `ingressCoalesceEnabled=false`, `callbackThreads=64`, journal store=scylla, async counter on.

| Scenario | Config target | Journal (eventsFired) | Per device | Bottleneck |
|----------|---------------|----------------------|------------|------------|
| Clean sustained | 16×5200 = 83k | ~14k/s | ~900/s | emqtt 4 shards, low clients |
| Peak (8 shards) | 16×32k = 512k | **~83–110k/s** | **~6.8–7.2k/s** | **Scylla ~108% CPU** |
| Max load confirm | 16×32k, 60s phase | **109 578/s** | 6 849/s | Scylla saturated; ISPF ~8% CPU |

### Conclusions (2026-07-04)

1. **Journal does not drop messages** on accepted ingress: meta vs eventsFired = **100%**, sync fallback = 0, queue drain ≈ 0.
2. **Maximum sustainable load** on this lab (~110k events/s, ~6.8k/device × 16) is limited by **Scylla write**, not journal queue or object-change bus.
3. **"17% efficiency"** — artifact of comparison to inflated configured target / emqtt formula, not pipeline loss.
4. Difference clean (~14k) vs peak (~110k) — **emqtt config** (shards, clients, CPU), not "dirty" DB.
5. After `docker compose ... recreate mqtt` drivers disconnect — re-seed needed (`SKIP_DEVICE_SETUP=false`).

### Recommended sustained target

```bash
# ~100k events/s aggregate (headroom under peak ~110k)
STRESS_SUSTAINED_RATE_PER_DEVICE=6800   # 16 × 6800 ≈ 109k
# or AUTO_CALIBRATE=true after probe
```

Peak emqtt: `EMQTT_SHARD_MAX=8`, `RATE_PER_DEVICE=32000` (formula 512k; actual journal ~110k).

## Runtime tuning (no rebuild)

`deploy/lab-stress.env` and hot-reload via runtime-settings (see `PlatformRuntimeSettingsCatalog`):

| Env | Lab value | Effect |
|-----|-----------|--------|
| `ISPF_EVENT_JOURNAL_WRITER_THREADS` | 24 | Parallel Scylla batch flush |
| `ISPF_EVENT_JOURNAL_BATCH_SIZE` | 5000 | Batch size |
| `ISPF_EVENT_JOURNAL_CASSANDRA_PARTITION_BATCH` | 200 | Partition-local UNLOGGED batches |
| `ISPF_EVENT_JOURNAL_CASSANDRA_PARALLEL_BATCHES` | 16 | Parallel partition writers |
| `ISPF_EVENT_JOURNAL_CASSANDRA_ASYNC_COUNTER_UPDATE` | true | Non-blocking meta counter (lag in measure — normal) |
| `ISPF_DRIVER_MQTT_CALLBACK_THREADS` | 64 | L0 FIFO workers per driver |
| `ISPF_OBJECT_CHANGE_ASYNC_ENABLED` | false | Bus off for stress |

## emqtt-bench

Client formula (script):

```text
clients_per_topic = ceil(rate_per_device × interval_ms / 1000)
formula_rate      = devices × clients_per_topic × (1000 / interval_ms)
```

Lines `ISPF_EMQTT_FORMULA_RATE=` in stdout — for benchmark parsing. Line `Done (formula estimate)` — **not** broker measurement.

Lab limits:

- `--cpus EMQTT_CPU_LIMIT` (default 1.5) — Erlang cannot sustain 1 ms interval at full formula (~15–21% of formula).
- Many clients per topic + QoS0 → broker `received` >> ISPF `eventsFired`.

Cleanup orphans: `bash lab-emqtt-cleanup.sh` (label `ispf.emqtt-bench=1`).

## Lab logs

| Path | Contents |
|------|----------|
| `~/ispf/loadtest/clean-run.log` | Factory reset benchmark |
| `~/ispf/loadtest/max-load-peak.log` | Peak / max load |
| `~/ispf/loadtest/peak-confirm.log` | Confirm without wipe |
| `~/ispf/loadtest/emqtt-pub-*.log` | emqtt stdout |

## Related documents

- [LOAD_TESTING.md](load-testing.md) — general MQTT / emqtt scenarios
- [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md) — EVENT_JOURNAL_ONLY
- [ADR-0026](decisions/0026-elastic-telemetry-ingress.md) — ingress pipeline
- [AUTOMATION.md](automation.md) — platform metrics API

## VPS prod comparison (ispf.iot-solutions.ru)

Same benchmark params as lab peak: **16×32k target**, **8 emqtt shards**, 60s measure, `EVENT_JOURNAL_ONLY`, Scylla journal.

| | Lab (84.42.21.226) | VPS prod |
|--|-------------------|----------|
| ISPF | 0.9.88 | 0.9.86 |
| Scylla | 20 SMP / 48G | **1 SMP / 750M** |
| Journal writers | 24 | **6** |
| **eventsFired** | **~110k/s** | **~349/s** |
| Per device | ~6.8k/s | ~19/s |
| meta vs eventsFired | 100% | ~88% (queue backlog) |
| Script | `lab-mqtt-event-journal-multi-test.sh` | `vps-mqtt-event-journal-multi-test.sh` |

```bash
# On VPS (after scripts in /opt/ispf/loadtest)
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 \
  bash /opt/ispf/loadtest/vps-mqtt-event-journal-multi-test.sh

# From dev machine
python deploy/run_vps_max_load.py
```

VPS Scylla is sized for prod footprint, not flood load — **do not compare absolute events/s with lab**. Journal path still works; bottleneck is Scylla capacity and MQTT ingress at QoS0 under overload.
