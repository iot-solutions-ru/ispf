# Analytics scale gate

| Gate | Status | Evidence |
|------|--------|----------|
| historian-aggregate-1M | PASS | `build/analytics-scale/historian-aggregate-1M.log` |
| analytics-multi-tag | PASS | `build/analytics-scale/analytics-multi-tag.log` |
| catalog (≥50000) | SKIP | `ISPF_ANALYTICS_BENCH_BASE_URL` |
| clickhouse samples (≥1000000000) | SKIP | `ISPF_ANALYTICS_BENCH_CH_URL` |

| Field | Value |
|-------|-------|
| Overall | **PASS** |
| Analytics p95 ceiling | 3000 ms |
| When | 2026-09-04T12:22:32Z |

JVM gates are sufficient for Phase 28 **Done** in CI. Catalog/CH rows are Enterprise L lab sign-off (scorecard ≥9.5).
