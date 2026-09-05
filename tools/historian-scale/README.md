# Historian / analytics scale gates (Phase 28)

Tracked CI and lab entrypoints for **BL-159 / BL-161 / BL-162** (and BL-210 multi-tag). Operator-only copies under `deploy/local/tools/` or gitignored `deploy/tools/*-gate.sh` are optional; **this directory is the source of truth in git**.

| Script | BL | What it proves |
|--------|----|----------------|
| [`historian-scale-benchmark.sh`](historian-scale-benchmark.sh) | BL-161 | JVM aggregate gate: ≤1M points, p95 &lt; 2 s |
| [`analytics-scale-gate.sh`](analytics-scale-gate.sh) | BL-161 + BL-210 | Aggregate + multi-tag JVM gates; optional live CH/catalog checks |
| [`seed-analytics-scale-catalog.py`](seed-analytics-scale-catalog.py) | BL-210 | Seed N history-enabled devices (Enterprise L: 50k) |
| [`count-history-enabled.py`](count-history-enabled.py) | BL-210 | Count history-enabled vars under path prefix |
| [`run-enterprise-l-gates.sh`](run-enterprise-l-gates.sh) | BL-210 | Orchestrate scale gate (+ optional historian) for lab sign-off |

## Quick start (CI / laptop)

```bash
# BL-161 only
bash tools/historian-scale/historian-scale-benchmark.sh

# Aggregate + analytics multi-tag (nightly load-test.yml equivalent)
bash tools/historian-scale/analytics-scale-gate.sh
```

Reports: `build/historian-scale/scale-benchmark.md`, `build/analytics-scale/analytics-scale-gate.md`.

## Enterprise L lab (catalog + ClickHouse)

> **Do not seed 50k on the shared demostand** without intent — use a dedicated lab /
> laptop. Demostand dry-run: `--tags 100` then purge with `--fresh`.

### 1. Seed ≥50k history-enabled tags

```bash
python3 tools/historian-scale/seed-analytics-scale-catalog.py \
  --base-url http://127.0.0.1:8080 \
  --username admin --password admin \
  --tags 50000 --batch 200 --workers 12
```

Laptop dry-run: `--tags 1000` first. Resume: `--start-index 10001`. Fresh wipe: `--fresh`.

Verify count (history-enabled variables — **not** `/platform/analytics/tags`):

```bash
python3 tools/historian-scale/count-history-enabled.py \
  --base-url http://127.0.0.1:8080 \
  --path-prefix root.platform.devices.analytics-scale-lab
```

### 2. Run gates with live probes

```bash
export ISPF_ANALYTICS_BENCH_BASE_URL=http://127.0.0.1:8080
export ISPF_ANALYTICS_BENCH_USERNAME=admin
export ISPF_ANALYTICS_BENCH_PASSWORD=admin
export ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE=false
export ISPF_ANALYTICS_BENCH_CATALOG_MIN=50000
export ISPF_ANALYTICS_BENCH_TAG_PREFIX=root.platform.devices.analytics-scale-lab

# Optional ClickHouse 1B row gate
export ISPF_ANALYTICS_BENCH_CH_URL=http://127.0.0.1:8123
export ISPF_ANALYTICS_BENCH_SKIP_CH_GATE=false
export ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES=1000000000

bash tools/historian-scale/run-enterprise-l-gates.sh
```

Without live env vars, JVM gates still **PASS** and catalog/CH rows record as **SKIP**.

### Catalog count sources (priority)

1. Lab Postgres via `ISPF_ANALYTICS_BENCH_CATALOG_PG_COMPOSE` / `lab-cluster-compose.yml`
2. `GET /api/v1/platform/analytics/history-enabled-count?pathPrefix=` (SQL on server)
3. Fallback: objects API walk + variables batch (older builds)

### Optional lab env

| Env | Default | Meaning |
|-----|---------|---------|
| `ISPF_ANALYTICS_BENCH_BASE_URL` | _(empty)_ | Live ISPF base URL for catalog probe |
| `ISPF_ANALYTICS_BENCH_TOKEN` | _(empty)_ | Bearer token (or use USERNAME/PASSWORD) |
| `ISPF_ANALYTICS_BENCH_USERNAME` / `PASSWORD` | `admin` / `admin` | Login for catalog probe |
| `ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE` | `true` | Set `false` to enforce count ≥ min |
| `ISPF_ANALYTICS_BENCH_CATALOG_MIN` | `50000` | Min history-enabled tags |
| `ISPF_ANALYTICS_BENCH_TAG_PREFIX` | `root.platform.devices.analytics-scale-lab` | Catalog prefix |
| `ISPF_ANALYTICS_BENCH_CATALOG_MODE` | `history-enabled` | Only supported mode |
| `ISPF_ANALYTICS_BENCH_CH_URL` | _(empty)_ | ClickHouse HTTP URL |
| `ISPF_ANALYTICS_BENCH_SKIP_CH_GATE` | `true` | Set `false` to enforce sample count |
| `ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES` | `1000000000` | Min `variable_samples` rows |

## Related

- [historian-tiers](../../docs/en/historian-tiers.md) — BL-159 turnkey profile
- [variable-history](../../docs/en/variable-history.md) — BL-161 SLO
- [clickhouse-prod-playbook](../../docs/en/clickhouse-prod-playbook.md) — BL-162 petabyte path
- [examples/analytics-platform/enterprise-l](../../examples/analytics-platform/enterprise-l/) — Scenario C walkthrough
- [examples/historian-tiers](../../examples/historian-tiers/) — `three-tier.env` / `hot-only.env`
- [examples/lab-mqtt-historian-stress](../../examples/lab-mqtt-historian-stress/) — I-03 event journal rate evidence
