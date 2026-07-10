> **Язык:** русская версия (вычитка). Канонический английский: [en/variable-history.md](../en/variable-history.md).

# История применения — этапы

## Этап 1 — Хранение и запись (готово)

- Таблица `variable_samples`, запись на `VARIABLE_UPDATED` с debounce
- Флаги на переменной: `historyEnabled`, `historyRetentionDays`
- REST: `GET/PATCH .../variables/history`
- Очистка по расписанию с учётом сохранения каждой переменной.

## Этап 2 — Настройка пользовательского интерфейса (готово)

- Редактор объектов, инспектор, редактор моделей
-Диалог переменный

## Этап 3 — Просмотр истории (готово)

- Компонент `VariableHistoryPanel` с выбором периода (1 ч … 7 д … всё)
- В инспекторе объекта: кнопка **График** для переменных с `historyEnabled`
- Хук `useVariableHistory` для произвольных экранов

## Этап 4 — Интеграция с дашбордами (готово)

- `useTrendSeries` загружает серверную историю только если `historyEnabled`
- Виджеты диаграммы/спарклайна отображаются подсказкой, если история отключена.
- API-запрос возвращает пустой массив, если история выключена

## Этап 5 — Синхронизация с моделью (готово)

- При старте: `syncAllModelBackedVariableMetadata()` для всех объектов с `templateId`
- Метаданные истории подтягиваются из моделей и сохраняются в БД.

## Этап 6 — Экспорт и поле схемы (готово)

- REST: `GET .../variables/history/export?format=csv|json` (до 10 000 точек, `Content-Disposition: attachment`)
- Параметр `field` — выбор поля схемы переменной (как в query)
- В `VariableHistoryPanel`: выпадающий список полей (если в схеме несколько числовых) и кнопки **CSV**/**JSON**

## Этап 7 — Агрегации (готово)

- REST: `GET .../variables/history/aggregate?bucket=1m|5m|15m|30m|1h|6h|1d`
- Ответ: `{ buckets: [{ ts, avg, min, max, count }] }`
- Период: `from` / `to` (если `from` не задан — от retention переменной)
- Пользовательский интерфейс: для диапазонов **7 д** и **Всё** график строится по уровням (`1h` и `6h` соответственно)

## Этап 8 — Историк на дашбордах (готово)

- Виджеты **chart** и **sparkline**: свойство `historyRange` (`live`, `1h` … `all`) в редакторе виджета
- `live` — последние N точек с live-хвостом (как раньше)
- Периоды **1ч–24ч** — сырые точки с сервером; **7д** / **все** — агрегаты ср/мин/макс
- Кнопка **История** на виджете → модное окно с полной панелью (график, период, поле, экспорт)

Историк **завершён** для текущих задач. Мониторинг состояния платформы — вкладка **Система** в консоли администратора (`GET /api/v1/platform/metrics`), не реализована история Prometheus-метрики. Scrape `/actuator/prometheus` остается для внешнего Диптиха JVM/Spring.

## Дорожная карта (вне историка)

Следующие темы **не включены** в модуль истории адаптеров — платформы объектов и сервисов:

| Тема | Примечание |
|------|------------|
| Алерты по порогам / трендам | Узлы `ALERT` в деревенском; см. [automation](automation.md) |
| Корреляторы событий | Узлы `CORRELATOR` в деревенском; API `/api/v1/correlators` — см. [automation](automation.md) |

Историк только **хранит и отдаёт** временные ряды; генерация событий и эскалация — уровень автоматизации.

## Конфигурация

```yaml
ispf:
  variable-history:
    enabled: true
    min-interval-ms: 5000
    retention-days: 90   # default, если у переменной retention = null
    slo:
      aggregate-max-points: 1000000
      aggregate-max-latency-ms: 2000
      raw-query-max-points: 10000
      raw-query-max-latency-ms: 500
      export-max-points: 10000
```

## Запрос SLO (BL-161)

Документированные цели уровня обслуживания для запросов REST архиватора. Значения по умолчанию привязываются через `VariableHistorySloProperties` (`ispf.variable-history.slo`).

| Запрос | Область применения | Цель (стр.95) |
|-------|-------|--------------|
| **Aggregate** | ≤ 1M raw samples bucketed (`GET .../history/aggregate`) | **< 2 s** |
| **Raw trend** | ≤ 10k points (`GET .../history`) | **< 500 ms** |
| **Export** | ≤ 10k points (`GET .../history/export`) | best-effort; same point cap |

Лабораторные gates (этап 28): сценарии `deploy/run_lab_historian_*.py` должны утверждать совокупную задержку относительно `aggregate-max-latency-ms` при нагрузке `aggregate-max-points`.

Ссылка на дашборд: [examples/historian-sla-dashboard](../examples/historian-sla-dashboard/) (макет виджета BL-161 + BFF sketch).

Многоуровневые профили хранения и развертывания: [historian-tiers](historian-tiers.md).

См. также [object-model](object-model.md), [api](api.md).
