# Enterprise L — analytics platform profile (Scenario C)

**Target:** 5k–50k+ history-enabled tags, dedicated `analytics` replicas, ClickHouse cluster as warm read plane, **1B+** `variable_samples` rows for enterprise historian gate.

> **Lab scripts** (`seed-analytics-scale-catalog.py`, `*-gate.sh`) live in **`deploy/local/`** (gitignored). Copy [`deploy/local/README.example.md`](../../deploy/local/README.example.md) on first clone.

## Topology

| Component | Setting |
|-----------|---------|
| Replicas | `io` + `hmi-read` + `analytics`×N + ClickHouse cluster |
| Historian | Warm read primary; PG hot metadata |
| Analytics | `ISPF_REPLICA_PROFILE=analytics` on worker pods |
| Materializer | Only on nodes with `analytics` capability ([BL-207](../../docs/en/analytics-platform-roadmap.md)) |

Helm: `analytics.replicaCount`, affinity to CH zone — see `deploy/helm/ispf/templates/analytics-deployment.yaml`.

## Walkthrough (≤1 day on lab hardware)

Documented lab spec (8 vCPU ClickHouse node, 32 GB RAM, NVMe):

| Step | Duration (est.) | Action |
|------|-----------------|--------|
| 1 | 1 h | Deploy CH cluster + ISPF unified/io/analytics per compose/Helm |
| 2 | 2–4 h | Bulk ingest 1B samples (replay MQTT or CH `INSERT SELECT`) |
| 3 | 2–6 h | `seed-analytics-scale-catalog.py --tags 50000` |
| 4 | 30 min | Enable materializer; wait for rollup catch-up |
| 5 | 15 min | Run scale gates (below) |

### 1. Seed 50k history-enabled tags

```bash
python deploy/local/tools/seed-analytics-scale-catalog.py \
  --base-url http://127.0.0.1:8080 \
  --tags 50000 \
  --batch 200
```

For dry-run on laptop use `--tags 1000` first.

### 2. ClickHouse row count gate

```bash
export ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://localhost:8123
export ISPF_ANALYTICS_BENCH_SKIP_CH_GATE=false
export ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES=1000000000
bash tools/historian-scale/analytics-scale-gate.sh
```

Bulk ingest playbook: [clickhouse-prod-playbook.md](../../docs/en/clickhouse-prod-playbook.md) — dual-write validation, then replay lab MQTT at scale.

### 3. Catalog + multi-tag SLO gate

```bash
export ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE=false
export ISPF_ANALYTICS_BENCH_CATALOG_MIN_TAGS=50000
export ISPF_ANALYTICS_BENCH_MULTI_TAG_P95_MS=3000
export ISPF_ANALYTICS_BENCH_TAG_COUNT=10
bash tools/historian-scale/analytics-scale-gate.sh
```

Or run the full orchestrator (scale + materializer lag; historian optional):

```bash
export ISPF_ANALYTICS_BENCH_SKIP_MATERIALIZER_GATE=false
bash deploy/local/tools/run-enterprise-l-gates.sh
```

### 4. JVM regression gate (CI)

Nightly workflow `.github/workflows/load-test.yml` runs `AnalyticsMultiTagQueryLoadTest` (10 tags × 7d × 1h, p95 &lt; 3 s on H2).

## Enterprise SLO table

| Gate | Scope | Target (p95) |
|------|-------|--------------|
| Catalog | ≥ 50k history-enabled tags | count ≥ 50k |
| CH samples | `variable_samples` rows | ≥ 1B |
| Multi-tag query | 10 tags × 7d × 1h buckets | **&lt; 3 s** on 8 vCPU CH |
| Materializer lag | rollup vs historian head | **&lt; 5 min** |
| Single-tag aggregate | ≤ 1M pts | **&lt; 2 s** (BL-161) |

Full table: [variable-history.md](../../docs/en/variable-history.md) § Analytics SLO.

## AF-capable positioning

Enterprise L proves **AF-capable** tier (derived tags, DAG engine, OLAP rollups, event frames, catalog/lineage). Gaps vs full PI Analytics / PI Vision: [analytics-platform-gaps.md](../../docs/en/analytics-platform-gaps.md).

## Sign-off checklist

- [ ] Seed 50k tags (`seed-analytics-scale-catalog.py --tags 50000`)
- [ ] ClickHouse ≥ 1B `variable_samples` rows
- [ ] `ISPF_ANALYTICS_MATERIALIZER_ENABLED=true` on analytics replica; materializer lag < 5 min
- [ ] Run gates: `bash deploy/local/tools/run-enterprise-l-gates.sh` with `ISPF_ANALYTICS_BENCH_SKIP_CATALOG_GATE=false`, `ISPF_ANALYTICS_BENCH_SKIP_MATERIALIZER_GATE=false`, `ISPF_ANALYTICS_BENCH_SKIP_CH_GATE=false`
- [ ] JVM gate: `AnalyticsMultiTagQueryLoadTest` in CI (nightly `load-test.yml`)

- [ ] `build/analytics-scale/analytics-scale-gate.md` — all gates **PASS**
- [ ] `build/historian-scale/scale-benchmark.md` — BL-161 aggregate **PASS**
- [ ] Competitive scorecard Historian row updated to **≥9.5** with lab date (manual release step)
