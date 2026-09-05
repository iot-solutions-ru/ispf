# Historian / analytics scale evidence

| File | Scope | Result |
| ---- | ----- | ------ |
| [2026-09-04-jvm-analytics-scale-gate.md](./2026-09-04-jvm-analytics-scale-gate.md) | CI/laptop JVM gates | **PASS** aggregate 1M + multi-tag (Enterprise L catalog/CH **SKIP** without lab env) |
| [2026-09-05-lab-192.168.100.10-enterprise-l.md](./2026-09-05-lab-192.168.100.10-enterprise-l.md) | Private lab 192.168.100.10 | **PASS** 50k history-enabled + 1.000B CH rows + multi-tag p95≈49 ms |
| [2026-09-05-lab-192.168.100.10-multi-tag-slo.json](./2026-09-05-lab-192.168.100.10-multi-tag-slo.json) | Multi-tag SLO raw | p50≈44.5 ms / p95≈49 ms (n=8) |

Enterprise L lab sign-off archived 2026-09-05 (synthetic CH fill; demostand remains a small site). Parked board: [parked-backlog.md](../../en/parked-backlog.md).

```bash
bash tools/historian-scale/analytics-scale-gate.sh
```
