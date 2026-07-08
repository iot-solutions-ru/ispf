> **Язык:** русская версия (вычитка). Канонический английский: [en/ci-dashboard.md](../en/ci-dashboard.md).

# Панель управления CI

Generated: `2026-07-04T23:58:16Z` · [Acceleration program](acceleration-program.md)

> Auto-updated by `python tools/acceleration/ci-dashboard.py` (S20-06).

## Состояние рабочего процесса (последние 20 запусков)

| Рабочий процесс | Роль | Успех | Средняя стена | Последний |
| -------- | ---- | ------- | -------- | ---- |
| `ci.yml` | PR pr-fast | 15.8% (19) | 30.4 min | failure |
| `ci-nightly.yml` | Nightly full | — (0) | — | — |
| `load-test.yml` | Load gate | 50.0% (2) | 4.1 min | failure |
| `cluster-load-test.yml` | Cluster gate | — (0) | — | — |
| `e2e-live.yml` | E2E live | 0.0% (20) | 0.0 min | failure |
| `driver-interop.yml` | Driver interop | 100.0% (12) | 3.3 min | success |

## Цели (ускорение)

| КПИ | Цель |
| --- | ------ |
| PR-pr-быстрая стена | ≤25 мин |
| Уровень успеха CI (14d) | ≥95% |
| Ночной полный | зелёный 7/7 дней |

## Команды

```bash
python tools/acceleration/ci-dashboard.py
python tools/acceleration/collect-baseline.py
gh run list --workflow=ci.yml --limit 10
```
