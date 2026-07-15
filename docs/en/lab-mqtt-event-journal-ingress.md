> **Language:** Canonical English. Russian edition: [ru/lab-mqtt-event-journal-ingress.md](../ru/lab-mqtt-event-journal-ingress.md).

# Lab: MQTT event journal ingress (scenario I-03)

Scenario **I-03-mqtt-event-journal** validates **N× mqtt driver → `EVENT_JOURNAL_ONLY` → `fireIngress` → Scylla `event_history`**, without historian, object-change bus, or alert rules.

**Purpose:** confirm the driver-native journal fast path ([0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)) under load on **split topology** (ISPF on the application host, Mosquitto + emqtt + Scylla on the loadgen/DB host).

**Where to run:** same split lab as I-01 / I-02. Addresses in git use [RFC 5737](https://datatracker.ietf.org/doc/html/rfc5737) (`198.51.100.x`); real SSH hosts stay in local `deploy/lab-*.env` only.

## Pipeline

```text
emqtt-bench (loadgen host)
    → Mosquitto (tcp://<loadgen-host>:1883)
    → N× mqtt driver (EVENT_JOURNAL_ONLY, ingressCoalesceEnabled=false)
    → TelemetryEventJournalFastPath → EventService.fireIngress
    → EventJournalAsyncWriter → Scylla event_history
```

**Not in path:** `variable_samples`, alert CEL, HTTP `/events/fire`, object-change bus.

Ordered suite smoke defaults: **4** devices, **~500 msg/s** per device, **60 s** measure, **20 s** warmup.

## Run (split topology)

Templates: [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) — copy `scripts/`, `env/`, `compose/` to `~/ispf` on lab hosts. Seed helper `setup-mqtt-event-journal-devices.py` lives in **gitignored** `deploy/` (copy to `~/ispf/loadtest/`).

```bash
cd ~/ispf
# shellcheck source=lab-loadgen.env
source lab-loadgen.env

# Smoke / regression (PROFILE=smoke by default)
bash lab-single-mqtt-event-journal-test.sh

# Peak per-topic (16 devices, own topic each)
bash lab-run-event-journal-peak.sh

# Target ~400k events/s (fan-out — see below)
bash lab-run-event-journal-400k.sh
```

Repeat without re-seed:

```bash
SKIP_DEVICE_SETUP=true bash lab-run-event-journal-peak.sh
```

From ordered suite (single topology):

```bash
python deploy/run_lab_ordered_suite.py --topology single --only I-03 --reset soft
```

## PASS criteria (script)

| Check | Condition |
|-------|-----------|
| Journal throughput | `eventsFiredTotal` delta ≥ `MIN_RATE_EVENTS × PHASE` |
| Historian leak | `variable_samples` stays **0** |
| Queue | `eventJournalQueueSize` → ≤1000 after drain (warn if elevated) |
| Sync fallback | `eventJournalSyncFallbackTotal` delta should stay ~0 |

Primary metric: **`eventsFiredTotal`** and **`eventJournalFlushedTotal`** deltas over the measure window. Scylla `COUNT(*)` on `event_history` may return **0** or timeout after large runs — trust platform metrics.

A successful run prints `=== I-03 PASS ===`.

## Profiles

| Profile | Script | Devices | Load shape | PASS threshold (default) |
|---------|--------|---------|------------|--------------------------|
| **smoke** | `lab-single-mqtt-event-journal-test.sh` | 4 | 4×500 msg/s, 2 emqtt shards | ≥50 events/s |
| **peak** | `lab-run-event-journal-peak.sh` | 16 | 16×32k msg/s (one topic per device), 8 shards | ≥80k events/s |
| **400k** | `lab-run-event-journal-400k.sh` | 16 | fan-out: ~26k publish/s × 16 subscribers | ≥400k events/s |

Environment overrides: `DEVICES`, `RATE_PER_DEVICE`, `PHASE`, `WARMUP`, `EMQTT_SHARD_MAX`, `INTERVAL_MS`, `MIN_RATE_EVENTS`, `SHARED_TOPIC`, `MQTT_PUBLISH_RATE`, `EMQTT_CPU_LIMIT`.

## Baseline (split lab, ISPF 0.9.144, Scylla journal, stress env)

Stand: application host `198.51.100.11`, loadgen/DB `198.51.100.10`, `ISPF_EVENT_JOURNAL_STORE=scylla`, journal queue 5M, 24 writer threads, MQTT callback threads 256 (server-wide).

| Run | Mode | eventsFired (measure) | Notes |
|-----|------|----------------------|-------|
| Smoke | 4×500, per-topic | **~250 events/s** | Chain regression |
| Peak v3 | 16×32k, per-topic | **~319k events/s** | Mosquitto ~270k msg/s; flushed ≈100% |
| 400k fan-out | 16 subs, 1 topic, ~26k publish/s | **~403k events/s** | flushed 100.2%; MQTT publish ~26k msg/s |

Absolute events/s depend on hardware and stress profile — **not prod SLA**. Use for regression on one stand.

### Per-topic vs fan-out ceiling

With **one MQTT topic per device** (16 independent ingress paths), this lab tops out around **~330–345k events/s** — Mosquitto can exceed that, but **MQTT driver L0 / ISPF ingress** saturates first.

To reach **≥400k events/s**, use **broker fan-out**: seed all 16 drivers on the **same** topic (`--shared-topic` / `SHARED_TOPIC=ispf/loadtest/journal-fanout/temperature`). One emqtt publish is delivered to 16 subscribers → **one MQTT message → 16 journal events**. Publish rate ≈ `target_events / devices` (~26k msg/s for 400k).

## What the benchmark script does

| Step | Action |
|------|--------|
| 1 | `setup-mqtt-event-journal-devices.py` — N× `loadtest-mqtt-dev-*`, `EVENT_JOURNAL_ONLY`, `ingressEventName=messageReceived`, historian disabled on `temperature` |
| 2 | Optional `--shared-topic` for fan-out mode |
| 3 | Stabilize drivers (`STABILIZE_SEC`, default 15–90 s); remote emqtt cleanup |
| 4 | emqtt on loadgen via `lab-emqtt-remote.sh` |
| 5 | Warmup, then measure `PHASE` s: deltas for `eventsFiredTotal`, `eventJournalFlushedTotal`, Mosquitto `$SYS` |
| 6 | Drain `eventJournalQueueSize` |

## Pitfalls (I-03)

| Problem | Symptom | Fix |
|---------|---------|-----|
| Broker `tcp://mqtt:1883` on split lab | ~0 events | `ISPF_MQTT_BROKER_HOST` from `lab-loadgen.env` |
| Corrupt / CRLF shell scripts on edge | emqtt log empty, ~500 msg/s only | Upload with `scp`; ensure Unix LF (`file script.sh` → no CRLF) |
| `lab-loadgen-common.sh` `\r` errors | emqtt never starts | `tr -d '\r' < file > file.tmp && mv file.tmp file` |
| Scylla `COUNT(*)` = 0 after peak | FAIL in old scripts | Use `eventsFiredTotal` / `eventJournalFlushedTotal` |
| Per-topic 400k target | FAIL at ~330–345k | Use fan-out (`lab-run-event-journal-400k.sh`) |
| Missing seed script | `setup-mqtt-event-journal-devices.py` not found | Copy from `deploy/` to `~/ispf/loadtest/` (gitignored) |
| Long runs, async Scylla counter | meta lags metrics | Normal with `ISPF_EVENT_JOURNAL_CASSANDRA_ASYNC_COUNTER_UPDATE=true` |

## Anonymization (git)

Committed docs and [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) use `198.51.100.x` and placeholder passwords. Operator-only: `deploy/lab_ssh.py`, real `lab-loadgen.env`, `deploy/setup-mqtt-event-journal-devices.py`, `deploy/mqtt_loadtest_lib.py`. Before push: `python deploy/tools/anonymize-repo.py`.

## Related

- [lab-event-journal-stress](lab-event-journal-stress.md) — legacy single-host multi-device peak (~110k, Scylla-saturated)
- [lab-mqtt-historian-stress](lab-mqtt-historian-stress.md) — I-01 historian (TELEMETRY_ONLY)
- [lab-mqtt-gateway-ingress](lab-mqtt-gateway-ingress.md) — I-02 gateway → child historian
- [load-testing](load-testing.md) — ordered suite I-01…I-08; anonymized results in [`examples/.../reports/ordered-suite-i01-i08.md`](../../examples/lab-mqtt-historian-stress/reports/ordered-suite-i01-i08.md)
- [0027-event-journal-ingress-fast-path](decisions/0027-event-journal-ingress-fast-path.md)
