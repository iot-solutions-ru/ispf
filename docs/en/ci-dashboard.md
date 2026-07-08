# CI dashboard

Generated: `2026-07-04T23:58:16Z` · [Acceleration program](acceleration-program.md)

> Auto-updated by `python tools/acceleration/ci-dashboard.py` (S20-06).

## Workflow health (last 20 runs)

| Workflow | Role | Success | Avg wall | Last |
| -------- | ---- | ------- | -------- | ---- |
| `ci.yml` | PR pr-fast | 15.8% (19) | 30.4 min | failure |
| `ci-nightly.yml` | Nightly full | — (0) | — | — |
| `load-test.yml` | Load gate | 50.0% (2) | 4.1 min | failure |
| `cluster-load-test.yml` | Cluster gate | — (0) | — | — |
| `e2e-live.yml` | E2E live | 0.0% (20) | 0.0 min | failure |
| `driver-interop.yml` | Driver interop | 100.0% (12) | 3.3 min | success |

## Targets (acceleration)

| KPI | Target |
| --- | ------ |
| PR pr-fast wall | ≤25 min |
| CI success rate (14d) | ≥95% |
| Nightly full | green 7/7 days |

## Commands

```bash
python tools/acceleration/ci-dashboard.py
python tools/acceleration/collect-baseline.py
gh run list --workflow=ci.yml --limit 10
```
