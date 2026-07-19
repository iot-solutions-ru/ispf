# Historian / analytics scale gates (Phase 28)

Tracked CI and lab entrypoints for **BL-159 / BL-161 / BL-162** (and BL-210 multi-tag). Operator-only copies under `deploy/local/tools/` or gitignored `deploy/tools/*-gate.sh` are optional; **this directory is the source of truth in git**.

| Script | BL | What it proves |
|--------|----|----------------|
| [`historian-scale-benchmark.sh`](historian-scale-benchmark.sh) | BL-161 | JVM aggregate gate: ≤1M points, p95 &lt; 2 s |
| [`analytics-scale-gate.sh`](analytics-scale-gate.sh) | BL-161 + BL-210 | Aggregate + multi-tag JVM gates; optional live CH/catalog checks |

## Quick start (CI / laptop)

```bash
# BL-161 only
bash tools/historian-scale/historian-scale-benchmark.sh

# Aggregate + analytics multi-tag (nightly load-test.yml equivalent)
bash tools/historian-scale/analytics-scale-gate.sh
```

Reports: `build/historian-scale/scale-benchmark.md`, `build/analytics-scale/analytics-scale-gate.md`.

## Optional lab gates (ClickHouse / Enterprise L)

Set when a lab ISPF + ClickHouse is reachable:

| Env | Default | Meaning |
|-----|---------|---------|
| `ISPF_ANALYTICS_BENCH_BASE_URL` | _(empty)_ | Live ISPF base URL for catalog/CH probes |
| `ISPF_ANALYTICS_BENCH_CH_URL` | _(empty)_ | ClickHouse HTTP URL |
| `ISPF_ANALYTICS_BENCH_CATALOG_MIN` | `50000` | Min history-enabled tags (Enterprise L) |
| `ISPF_ANALYTICS_BENCH_CH_MIN_SAMPLES` | `1000000000` | Min `variable_samples` rows |

Without these, the script still **PASS**es on JVM gates and records optional checks as **SKIP**.

## Related

- [historian-tiers](../../docs/en/historian-tiers.md) — BL-159 turnkey profile
- [variable-history](../../docs/en/variable-history.md) — BL-161 SLO
- [clickhouse-prod-playbook](../../docs/en/clickhouse-prod-playbook.md) — BL-162 petabyte path
- [examples/historian-tiers](../../examples/historian-tiers/) — `three-tier.env` / `hot-only.env`
- [examples/lab-mqtt-historian-stress](../../examples/lab-mqtt-historian-stress/) — I-03 event journal rate evidence
