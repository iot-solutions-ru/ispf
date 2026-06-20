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

## Дальше (план)

| Этап | Содержание |
|------|------------|
| 6 | Экспорт CSV/JSON истории, фильтр по полю схемы |
| 7 | Агрегации (avg/min/max за интервал) в API |
| 8 | Алерты по порогам на основе истории |
| 9 | UI: мини-график в редакторе свойств объекта |
| 10 | Метрики Prometheus: `variable_samples_total`, lag записи |

## Конфигурация

```yaml
ispf:
  variable-history:
    enabled: true
    min-interval-ms: 5000
    retention-days: 90   # default, если у переменной retention = null
```

См. также [OBJECT_MODEL.md](./OBJECT_MODEL.md), [API.md](./API.md).
