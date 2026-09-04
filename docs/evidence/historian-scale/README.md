# Historian / analytics scale evidence

| File | Scope | Result |
| ---- | ----- | ------ |
| [2026-09-04-jvm-analytics-scale-gate.md](./2026-09-04-jvm-analytics-scale-gate.md) | CI/laptop JVM gates | **PASS** aggregate 1M + multi-tag (Enterprise L catalog/CH **SKIP** without lab env) |

Enterprise L (50k tags / 1B CH rows) needs `ISPF_ANALYTICS_BENCH_BASE_URL` + `ISPF_ANALYTICS_BENCH_CH_URL` on a scaled lab — demostand catalog count was **1** as of 2026-09-04 (not a 50k site).

```bash
bash tools/historian-scale/analytics-scale-gate.sh
```
