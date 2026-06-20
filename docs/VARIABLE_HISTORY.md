# История переменных — этапы

## Этап 1 — Хранение и запись (готово)

- Таблица `variable_samples`, запись на `VARIABLE_UPDATED` с debounce
- Флаги на переменной: `historyEnabled`, `historyRetentionDays`
- REST: `GET/PATCH .../variables/history`
- Очистка по расписанию с учётом retention каждой переменной

## Этап 2 — Настройка в UI (готово)

- Редактор объекта, инспектор, редактор модели
- Диалог настроек переменной

## Этап 3 — Просмотр истории (готово)

- Компонент `VariableHistoryPanel` с выбором периода (1 ч … 7 д … всё)
- В инспекторе объекта: кнопка **График** для переменных с `historyEnabled`
- Хук `useVariableHistory` для произвольных экранов

## Этап 4 — Интеграция с дашбордами (готово)

- `useTrendSeries` загружает серверную историю только если `historyEnabled`
- Виджеты chart/sparkline показывают подсказку, если история отключена
- API query возвращает пустой массив, если история выключена

## Этап 5 — Синхронизация с моделью (готово)

- При старте: `syncAllModelBackedVariableMetadata()` для всех объектов с `templateId`
- Метаданные истории подтягиваются из модели и сохраняются в БД

## Этап 6 — Экспорт и поле схемы (готово)

- REST: `GET .../variables/history/export?format=csv|json` (до 10 000 точек, `Content-Disposition: attachment`)
- Параметр `field` — выбор поля схемы переменной (как в query)
- В `VariableHistoryPanel`: выпадающий список полей (если в схеме несколько числовых) и кнопки **CSV** / **JSON**

## Этап 7 — Агрегации (готово)

- REST: `GET .../variables/history/aggregate?bucket=1m|5m|15m|30m|1h|6h|1d`
- Ответ: `{ buckets: [{ ts, avg, min, max, count }] }`
- Период: `from` / `to` (если `from` не задан — от retention переменной)
- UI: для диапазонов **7 д** и **Всё** график строится по средним (`1h` и `6h` соответственно)

## Этап 8 — Historian на дашбордах (готово)

- Виджеты **chart** и **sparkline**: свойство `historyRange` (`live`, `1h` … `all`) в редакторе виджета
- `live` — последние N точек с live-хвостом (как раньше)
- Периоды **1h–24h** — сырые точки с сервера; **7d** / **all** — агрегаты avg/min/max
- Кнопка **История** на виджете → модальное окно с полной панелью (график, период, поле, экспорт)

Historian **завершён** для текущего scope. Мониторинг состояния платформы — вкладка **Система** в консоли admin (`GET /api/v1/platform/metrics`), не отдельные Prometheus-метрики historian. Scrape `/actuator/prometheus` остаётся для внешнего мониторинга JVM/Spring.

## Роадmap (вне historian)

Следующие темы **не входят** в модуль истории переменных — отдельные объекты и сервисы платформы:

| Тема | Примечание |
|------|------------|
| Алерты по порогам / трендам | Отдельный объект (правило алерта), связь с переменной и historian — позже; см. [AUTOMATION.md](./AUTOMATION.md), `alert_rules` |
| Корреляторы событий | Уже есть API `/api/v1/correlators` — см. [AUTOMATION.md](./AUTOMATION.md) |

Historian только **хранит и отдаёт** временные ряды; генерация событий и эскалация — слой автоматизации.

## Конфигурация

```yaml
ispf:
  variable-history:
    enabled: true
    min-interval-ms: 5000
    retention-days: 90   # default, если у переменной retention = null
```

См. также [OBJECT_MODEL.md](./OBJECT_MODEL.md), [API.md](./API.md).
