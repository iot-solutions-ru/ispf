# Ordered suite baseline (I-01…I-08) — anonymized

Regression reference for the **split lab** topology (ISPF application host + loadgen/DB host). Addresses use [RFC 5737](https://datatracker.ietf.org/doc/html/rfc5737) (`198.51.100.x`). **Not prod SLA.** Absolute rates depend on CPU, stress env, and store backend.

| Field | Value |
|-------|-------|
| Topology | Application `198.51.100.11`, loadgen/DB `198.51.100.10` |
| Broker | Mosquitto `tcp://198.51.100.10:1883` |
| ISPF remasure | **0.9.147** (peaks below also cover 0.9.139…0.9.147) |
| Journal / historian store | Scylla (single-node stress profile) unless noted |
| Orchestrator | gitignored `deploy/run_lab_ordered_suite.py` (`--topology single`) |
| Remasure date | **2026-07-14** |

Operator copies with real SSH hosts stay under gitignored `local/` and `deploy/lab-*` — see [local/README.example.md](../local/README.example.md).

## Summary (remasure 0.9.147)

Ordered-suite / smoke remasure on a single stand. Peak/scale rows for I-01/I-03/I-04/I-06 remain prior same-stand references.

| Id | Scenario | Outcome | Key metric (measure window) |
|----|----------|---------|-----------------------------|
| **I-01** | MQTT historian (direct devices) | **PASS** | Smoke **~1777** samples/s (4×500); peak **~258k…579k** (16 devices; Scylla/CH) — [lab-mqtt-historian-stress](../../../docs/en/lab-mqtt-historian-stress.md) |
| **I-02** | MQTT gateway → child historian | **PASS** | **~4358** samples/s flushed @ ~2k msg/s, coalesce 50 ms (4 children) |
| **I-03** | MQTT `EVENT_JOURNAL_ONLY` | **PASS** | Smoke **~2418** events/s (4×500); peak ~319k/s; fan-out **~403k** events/s |
| **I-04** | MQTT FULL + CEL → journal | **PASS** | Smoke **~1801** alert/s @ ~2k msg/s, coalesce 5 ms; fan-out extension **~8.8k** alert/s |
| **I-05** | Virtual TELEMETRY_ONLY → historian | **PASS** | **~360** samples/s flushed (30 devices @ 500 ms poll) |
| **I-06** | Virtual FULL + CEL → journal | **PASS** (smoke + tick) | Smoke **~60** alert/s (60@1s); scale best **~7.3k** alert/s (1000@100 ms) |
| **I-07** | HTTP `POST /events/fire` | **PASS** | Best fail=0: **~1714** RPS (c=40, 60 s, p50/p95 ~20/50 ms). After leftover loadtest correlators / suite debris: **~505** RPS fail=0 (c=30) or **~1550** RPS with ~10% fail (c=40) |
| **I-08** | Gateway `lastIngress.raw` historian | **PASS** | Live ingress OK; **~3754** samples/s flushed @ ~2k msg/s |

**Ops notes from remasure:** prefer platform counters (`variableHistoryFlushedTotal`, `eventsFiredTotal`, `alertFiresTotal`). Soft reset does **not** disable loadtest correlators — purge/disable them (`PUT /api/v1/correlators/by-path?path=<id>` with `{"enabled":false}`) or API latency/403 storms spoil I-06/I-07. Do not judge MQTT ingress FAIL on absolute `event_history` when flushed/historian deltas prove the path.

## Per-scenario notes

### I-01 — mqtt-historian

Pipeline: `emqtt → Mosquitto → N× mqtt (TELEMETRY_ONLY) → historian → Scylla|ClickHouse`.

Ordered smoke remasure: `DEVICES=4 RATE_PER_DEVICE=500` → **~1777** samples/s flushed.

Peak reference (same stand, stress env, 16 devices, `minIntervalMs=0`):

| Store | Target | Historian flushed |
|-------|--------|-------------------|
| Scylla | 250k / 500k msg/s | **~258k / ~520–559k** samples/s |
| ClickHouse | 250k / 500k msg/s | **~288k / ~579k** samples/s |

Primary gate: **`variableHistoryFlushedTotal`** (+ Mosquitto `$SYS`). Scylla `COUNT(*)` may timeout after large tables — do not FAIL on count alone.

### I-02 — mqtt-gateway

One gateway mqtt driver → `dispatchTelemetry` → child `temperature` historian. Coalesce **50 ms**. Measure ~60 s after 20 s warmup at **~2000 msg/s**.

| Metric | Value |
|--------|-------|
| Historian rate (remasure) | **~4358 samples/s** |
| Outcome | **I-02 PASS** |

Earlier docs quoting ~2.3k on this stand were superseded by the remasure above (same topology / coalesce).

### I-03 — mqtt-event-journal

| Mode | eventsFired |
|------|-------------|
| Smoke 4×500 (remasure) | **~2418**/s |
| Peak 16×32k per-topic | **~319k**/s |
| Fan-out 16 subs / 1 topic | **~403k**/s (flushed ≈100% of fired) |

### I-04 — mqtt-automation

FULL telemetry + CEL alerts, coalesce **5 ms** (smoke) / **1 ms** (fan-out).

| Mode | Alert/events /s | Notes |
|------|-----------------|-------|
| Smoke 4 topics @ ~2k msg/s (remasure) | **~1801** | Live ingress OK |
| Fan-out 16 drivers @ ~40k publish/s | **~8770** | Ingress gate can warn; waived when automation delta proves path |

### I-05 — virtual-historian

30 virtual devices, `TELEMETRY_ONLY`, poll **500 ms**, measure **60 s**:

| Metric | Value |
|--------|-------|
| Flushed delta | 21600 (**360 samples/s**) |
| Event journal records | 0 |

Suite body with `--telemetry-mix-ratio 1` reports 0 events/s by design (no alerts); historian gate uses flushed samples.

### I-06 — virtual-automation

| Stage | Result |
|-------|--------|
| Smoke 60 devices @ 1000 ms poll (remasure) | **PASS** — **~60** alert/s (`alertFiresTotal` / `eventsFiredTotal`) |
| Tick correctness 20 @ 200 ms | **PASS** — fires/expected ≈ **1.0067** (tol 5%) |
| Scale 1000 @ 100 ms, IO=32 | **~7313** alert/s (automation queue saturated) |
| Denser poll 50 / 25 ms | ~7064 / collapse ~441 alert/s |

Binding coalesce default **off**; slim CEL `self` when unused (0.9.146). On this stand automation queue saturation limits denser poll rates. Seed with `--skip-automation-seed` (or zero correlator fixtures) before smoke; leftover enabled loadtest correlators break metrics/auth.

### I-07 — http-events

| Run | RPS | fail% | p50 / p95 | Notes |
|-----|-----|-------|-----------|-------|
| Best same-day fail=0 (c=40, 60 s) | **1714** | **0.0** | **20.3 / 50.4** ms | Example JSON shape |
| Remasure after correlator purge (c=30) | **~505** | **0.0** | ~41 / 156 ms | Clean but cooler API |
| Remasure hot (c=40) | **~1553** | **~10.6** | ~20 / 50 ms | High throughput; fails across fire/list |

Judge scripts must treat `fail_rate_pct=0.0` as success (not as “0 or 100”). Prefer a clean API (no 900× enabled loadtest correlators) before claiming the 1714-class number.

### I-08 — mqtt-ingress-history

Gateway only: historian on `lastIngress.raw`, no child dispatch. Coalesce **1 ms**, `ISPF_VARIABLE_HISTORY_MIN_INTERVAL_MS=0`.

| Metric | Value |
|--------|-------|
| Live ingress | OK (`lastIngress.raw` populated) |
| Rate (remasure) | **~3754 samples/s** |
| Version | **0.9.147** |

**Fixes that made PASS possible:**

1. Product: historian-only fast path must update **RAM** (`setDriverTelemetryValueInMemory`), not only the store — otherwise live-ingress gate fails.
2. Measure: use **`variableHistoryFlushedTotal`** for delta; absolute field history with `limit=10000` yields start=end=10000 after stress.

Example JSON shapes: [example-i07-http-events-report.json](example-i07-http-events-report.json), [example-i08-mqtt-ingress-history-report.json](example-i08-mqtt-ingress-history-report.json).

## Related docs

- [load-testing](../../../docs/en/load-testing.md) — suite orchestration and clean slate
- [lab-mqtt-historian-stress](../../../docs/en/lab-mqtt-historian-stress.md) — I-01
- [lab-mqtt-gateway-ingress](../../../docs/en/lab-mqtt-gateway-ingress.md) — I-02 / I-08
- [lab-mqtt-event-journal-ingress](../../../docs/en/lab-mqtt-event-journal-ingress.md) — I-03
