> **Язык:** русская версия (вычитка). Канонический английский: [en/ci-dashboard.md](../en/ci-dashboard.md).

# Панель управления CI

Generated: `2026-09-08T08:14:35Z` · [Acceleration program](acceleration-program.md)

> Auto-updated by `python tools/acceleration/ci-dashboard.py` (S20-06).

## Состояние рабочего процесса (последние 20 запусков)

| Рабочий процесс | Роль | Успех | Средняя стена | Последний |
| -------- | ---- | ------- | -------- | ---- |
| `ci.yml` | PR pr-fast | 90.0% (20) | 7.1 min | success |
| `nightly.yml` | Nightly full | 50.0% (20) | 5.2 min | success |
| `load-test.yml` | Load gate | 80.0% (20) | 5.0 min | success |
| `cluster-load-test.yml` | Cluster gate | 100.0% (7) | 3.5 min | success |
| `e2e-live.yml` | E2E live | 38.9% (18) | 0.0 min | success |
| `driver-interop.yml` | Driver interop | 95.0% (20) | 6.6 min | success |

## Цели (ускорение)

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
