# ClickHouse production playbook (BL-114)

> **Status:** Lab — Production rollout. Hub: [doc-status.md](doc-status.md).

Ops guide for enabling ClickHouse on ISPF production: **event journal**, **variable historian**, and **dual-write** migration path.

Related: [deployment](deployment.md), [0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md), [0035-historian-dual-write](decisions/0035-historian-dual-write.md), [0017-telemetry-ingest-pipeline](decisions/0017-telemetry-ingest-pipeline.md).

---

## When to use ClickHouse

| Workload | Default | ClickHouse |
|----------|---------|------------|
| Event journal | PostgreSQL/Timescale | `ISPF_EVENT_JOURNAL_STORE=clickhouse` @ ~100+ events/s |
| Variable historian | PostgreSQL/Timescale | `ISPF_VARIABLE_HISTORY_STORE=clickhouse` @ high sample rate |
| Migration | — | **Dual-write** (PG primary + CH secondary) before cutover |

Prod VPS baseline: `jdbc` for both until load requires CH ([roadmap](roadmap.md)).

---

## Phase 0 — Prerequisites

- ISPF server healthy (`curl ${ISPF_BASE_URL:-https://ispf.example.invalid}/api/v1/info`)
- Docker on host (VPS) or external CH cluster
- Admin SSH access to `/opt/ispf`

---

## Phase 1 — Install ClickHouse

```bash
# On VPS (from repo deploy/)
bash deploy/vps-clickhouse-setup.sh
```

Or compose locally: `docker compose -f deploy/docker-compose.clickhouse.yml up -d`

Artifacts:

- Container `ispf-clickhouse`
- Password file `/opt/ispf/clickhouse-password.txt`
- Database `ispf` (created on first ISPF write)

---

## Phase 2 — Event journal → ClickHouse

1. Set in `/opt/ispf/ispf-server.env`:

| Variable | Value |
|----------|-------|
| `ISPF_EVENT_JOURNAL_STORE` | `clickhouse` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_URL` | `http://127.0.0.1:8123` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_DATABASE` | `ispf` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_TABLE` | `event_history` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_USERNAME` | `default` |
| `ISPF_EVENT_JOURNAL_CLICKHOUSE_PASSWORD` | from `clickhouse-password.txt` |

2. `systemctl restart ispf-server`
3. Log line: `ClickHouse event journal ready`
4. Timescale hypertable for `event_history` is **skipped** when store=clickhouse

---

## Phase 3 — Variable historian

### Option A — Dual-write (recommended first step, BL-116)

PostgreSQL remains source of truth for reads; ClickHouse receives async copy.

```bash
bash /opt/ispf/bin/vps-variable-history-dual-write.sh
```

Env:

| Variable | Value |
|----------|-------|
| `ISPF_VARIABLE_HISTORY_STORE` | `jdbc` |
| `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED` | `true` |
| `ISPF_VARIABLE_HISTORY_CLICKHOUSE_*` | same as event journal |

Log: `ClickHouse dual-write secondary ready`

### Option B — Full cutover to ClickHouse

```bash
bash /opt/ispf/bin/vps-variable-history-clickhouse.sh
```

Sets `ISPF_VARIABLE_HISTORY_STORE=clickhouse`. Reads and writes use CH only.

### Rollback

| From | Action |
|------|--------|
| Dual-write | `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false` + restart |
| CH-only historian | `ISPF_VARIABLE_HISTORY_STORE=jdbc` + restart (CH data retained) |
| Event journal CH | `ISPF_EVENT_JOURNAL_STORE=jdbc` + restart |

---

## Phase 4 — Verify

```bash
bash /opt/ispf/vps-clickhouse-verify.sh [expected-version]
```

Checks:

- ClickHouse `/ping`
- ISPF `/api/v1/info`, `/actuator/health`
- `ISPF_EVENT_JOURNAL_STORE=clickhouse`
- Tables `ispf.event_history`, optional `ispf.variable_samples`
- Startup log markers
- Smoke: `POST /api/v1/events/fire` → row count increase (skipped with WARN on 404/403 when prod has no demo fixtures — `ISPF_BOOTSTRAP_FIXTURES_ENABLED=false`)
- If `store=clickhouse` or dual-write: variable sample smoke via setVariable (same fixture caveat)

From Windows deploy:

```powershell
.\deploy\vps-deploy-direct.ps1 -Version 0.9.x -SkipTests -VerifyClickHouse
```

Dual-write only (historian secondary, PG primary — S28):

```bash
ISPF_CLICKHOUSE_VERIFY_MODE=dual-write-only bash /opt/ispf/vps-clickhouse-verify.sh
```

---

## Phase 5 — Monitoring

| Signal | Where |
|--------|-------|
| CH disk / parts | `system.parts` on `ispf.*` tables |
| ISPF historian queue | `/actuator/prometheus` → `ispf_variable_history_queue_size` |
| Dual-write failures | `ispf-server` log: `ClickHouse dual-write append failed` |
| Event journal lag | `ispf_event_history_records` gauge |

Retention: CH TTL on `sampled_at` / `occurred_at` from `ISPF_VARIABLE_HISTORY_RETENTION_DAYS` / event journal config.

---

## Decision matrix

| Stage | Event journal | Variable historian | Risk |
|-------|---------------|-------------------|------|
| Default prod | jdbc | jdbc | Lowest |
| High event rate | clickhouse | jdbc + dual-write | Medium |
| High telemetry | clickhouse | dual-write → clickhouse | Medium–high |
| Analytics-only archive | jdbc | dual-write (CH read later) | Low |

---

## Files reference

| Script | Purpose |
|--------|---------|
| `deploy/vps-clickhouse-setup.sh` | Install CH + password |
| `deploy/vps-variable-history-clickhouse.sh` | Historian cutover |
| `deploy/vps-variable-history-dual-write.sh` | Dual-write enable |
| `deploy/vps-clickhouse-verify.sh` | Post-rollout verify |
| `deploy/docker-compose.clickhouse.yml` | Local / lab CH |
| `examples/historian-tiers/three-tier.env` | BL-159 turnkey historian tiers |
| `tools/historian-scale/analytics-scale-gate.sh` | BL-161 / BL-210 JVM (+ optional CH) gates |

---

## BL-162 — Event journal petabyte path (Done)

**Acceptance:** CH cutover playbook executable ≤5 ops steps; lab ingest **≥10M events/min**.

| Evidence | Path / result |
|----------|----------------|
| Cutover playbook | This doc Phases 1–4 + dual-write verify (`ISPF_CLICKHOUSE_VERIFY_MODE=dual-write-only`) — S28 executed on VPS |
| Local CH profile | `deploy/docker-compose.clickhouse.yml` + `ISPF_EVENT_JOURNAL_STORE=clickhouse` |
| ADR | [0016-clickhouse-event-journal](decisions/0016-clickhouse-event-journal.md) — `EventJournalStore` SPI |
| Lab rate (I-03) | Fan-out **~403k events/s** ≈ **24M/min** (≫ 10M/min) — [ordered-suite-i01-i08.md](../../examples/lab-mqtt-historian-stress/reports/ordered-suite-i01-i08.md), [lab-mqtt-event-journal-ingress](lab-mqtt-event-journal-ingress.md) |

**Honest scope:** I-03 lab proof uses **Scylla** `EVENT_JOURNAL_ONLY` fast path on the same SPI contract as ClickHouse. Petabyte **retention** is operational (CH TTL / cold export), not a single CI row-count. Enterprise L 1B-sample historian gate remains optional via `ISPF_ANALYTICS_BENCH_CH_*` on `tools/historian-scale/analytics-scale-gate.sh`.

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| verify fails ping | `docker ps`, firewall 8123 |
| count not increasing | historian enabled on device variable; `ISPF_VARIABLE_HISTORY_ENABLED=true` |
| login smoke skipped | set `ISPF_VERIFY_ADMIN_USER/PASS` or fixtures disabled on prod |
| PG + CH mismatch | dual-write: reads always PG until cutover |
