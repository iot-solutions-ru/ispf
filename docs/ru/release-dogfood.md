> **Язык:** русская версия (вычитка). Канонический английский: [en/release-dogfood.md](../en/release-dogfood.md).

# Чеклист dogfood перед релизом

> **Статус:** Internal — Чеклист релиза. Теги: [doc-status](../en/doc-status.md).

Короткий шлюз перед тегом релиза. Лучше провалить шаг, чем пропустить его.

Связано: [demostands](demostands.md), [observability](observability.md), [golden-path-alarm-smoke](../../deploy/tools/golden-path-alarm-smoke.py).

## Шлюз (вручную или CI)

| # | Проверка | Как |
|---|--------|-----|
| 1 | **Golden alarm path** | `python deploy/tools/golden-path-alarm-smoke.py` (fixtures / demostand с `demo-sensor-01`) |
| 2 | **Self-diagnostics** | Admin → System → Metrics: карточка hot-path показывает числа; открыть дашборд `root.platform.dashboards.platform-metrics` |
| 3 | **Operator starters** | `?mode=operator` → Alarm Console / Work Queue / HMI Wall открываются (или Install starters) |
| 4 | **Dashboard bind** | Открыть `demo-sensor` (или любой HMI): live value обновляется без полной перезагрузки страницы |
| 5 | **Mimic** | Открыть mimic (если есть): canvas загружается, без ошибок в console |
| 6 | **Workflow** | Запустить или просмотреть `demo-alarm-handler` (или site workflow): instance появляется / завершается |
| 7 | **Federation (если используется)** | Peer health / connect wizard smoke — пропустить на single-node |

## Критерии выхода

- Шаги 1–3 зелёные на целевом стенде (local fixtures или staging).
- Шаги 4–6 зелёные для product surfaces, которые вы отгружаете в этом теге.
- Нет известных P0 в journal / WS silence / auth.

## Не этот чеклист

- Полный load-test suite ([load-testing](load-testing.md))
- Внешний импорт Grafana (только опциональный export)
- Clean-install без fixtures (starters устанавливаются через API/launcher)
