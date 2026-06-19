# Дашборды и виджеты

## Обзор

Дашборд — объект типа `DASHBOARD` с моделью `dashboard-v1`. Layout хранится в переменной `layout` (JSON string).

Переменные модели:

| Имя | Описание |
|-----|----------|
| `title` | Заголовок экрана |
| `layout` | JSON сетки виджетов |
| `refreshIntervalMs` | Интервал опроса (мс), по умолчанию 5000 |

Демо: `root.platform.dashboards.demo-sensor` — layout из `DashboardLayouts.java`.

## Layout JSON

```json
{
  "columns": 12,
  "rowHeight": 72,
  "widgets": [
    {
      "id": "temp-value",
      "type": "value",
      "title": "Температура",
      "x": 0, "y": 0, "w": 3, "h": 2,
      "objectPath": "root.platform.devices.demo-sensor-01",
      "variableName": "temperature",
      "valueField": "value",
      "decimals": 1,
      "unit": "°C"
    }
  ]
}
```

- Сетка: 12 колонок, позиция `x,y`, размер `w,h` в единицах сетки
- `rowHeight` — высота строки в пикселях

## Типы виджетов

| type | Описание | Ключевые поля |
|------|----------|---------------|
| `value` | Число/текст | `objectPath`, `variableName`, `decimals`, `unit` |
| `indicator` | Индикатор bool | `trueLabel`, `falseLabel`, `trueColor` |
| `toggle` | Переключатель (write) | `trueLabel`, `falseLabel` |
| `chart` | График (line/area) | `chartStyle`, `maxPoints`, `color` |
| `sparkline` | Мини-тренд | `maxPoints`, `color` |
| `function` | Кнопка вызова функции | `functionName`, `buttonLabel`, `inputJson` |
| `function-form` | Форма → invoke | `functionName`, `fieldsJson` |
| `progress` | Прогресс-бар | `currentVariable`, `maxVariable`, `unit` |
| `object-table` | Таблица дочерних объектов | `parentPath`, `columnsJson`, `selectionKey` |
| `event-feed` | Лента событий | `objectPathPrefix`, `eventNamesJson`, `maxItems` |
| `work-queue` | Очередь BPMN-задач | `operatorId`, `maxItems` |
| `status-badge` | Badge статуса | `variableName`, `selectionKey` |
| `gauge` | Шкала min–max | `minVariable`, `maxVariable`, `unit` |
| `card-grid` | Карточки объектов | `parentPath`, `variablesJson` |

Исходники типов: `apps/web-console/src/types/dashboard.ts`  
View-компоненты: `apps/web-console/src/components/dashboard/widgets/`

## Связанный выбор (`selectionKey`)

Виджеты `object-table` записывают путь выбранной строки в контекст дашборда (`DashboardContext`).

Другие виджеты с `selectionKey: "order"` берут `objectPath` из выбора вместо статического пути.

Пример: таблица нарядов + progress + function-form на выбранном наряде.

## function-form fieldsJson

```json
[
  {
    "name": "devicePath",
    "label": "Устройство",
    "type": "select",
    "optionsFrom": "root.platform.devices.demo-sensor-01",
    "defaultValue": "demo-sensor-01"
  },
  {
    "name": "threshold",
    "label": "Порог",
    "type": "number"
  }
]
```

## object-table columnsJson

```json
[
  { "variable": "orderNo", "label": "№" },
  { "variable": "status", "label": "Статус" }
]
```

## Dashboard Builder (UI)

- **Режим просмотра** — live-данные, refresh по `refreshIntervalMs`
- **Режим редактирования** — drag-and-drop, resize, панель свойств виджета
- Сохранение: `PUT /api/v1/dashboards/by-path/layout`

Компоненты: `DashboardBuilder`, `DashboardGrid`, `WidgetEditorPanel`.

## Operator HMI

`?mode=operator` — только просмотр layout, без редактора.  
Боковая панель: work queue + event journal.

Привязка дашборда: query param или выбор в operator shell (см. `OperatorView.tsx`).

## API

```http
GET /api/v1/dashboards/by-path?path=root.platform.dashboards.demo-sensor
PUT /api/v1/dashboards/by-path/layout?path=...  (body: layout JSON string)
PUT /api/v1/dashboards/by-path/title?path=...     (body: { "title": "..." })
```

## Стили

CSS виджетов: `apps/web-console/src/styles.css` (секция `dash-*`, `function-form-*`).
