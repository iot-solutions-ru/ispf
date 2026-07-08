> **Язык:** русская версия (вычитка). Канонический английский: [en/dashboards.md](../en/dashboards.md).

# Дашборды и виджеты

**Справочник всех виджетов** (назначение, настройки, примеры): **[WIDGETS.md](widgets.md)**.

## Обзор

Дашборд — объект типа `DASHBOARD` с моделью `dashboard-v1`. Макет хранится в переменной `layout` (строка JSON).

Переменные модели:

| Имя | Описание |
|-----|----------|
| `title` | Заголовок экрана |
| `layout` | JSON сетки виджетов |
| `refreshIntervalMs` | Интервал опроса (мс), по умолчанию 5000 |
| `@dashboardContext` | **Планируется** — сеанс JSON (выборка, параметры, виджеты); см. [PLATFORM_LOGIC.md](platform-logic.md) |

Демо:

| Дашборд | Назначение |
|---------|------------|
| `root.platform.dashboards.demo-sensor` | один объект, статический `objectPath` |
| `root.platform.dashboards.snmp-host-monitoring` | таблица устройств + `selectionKey: "device"` |

Layout по умолчанию: `packages/ispf-server/.../DashboardLayouts.java`.

## Привязка к объектам: `objectPath` и `selectionKey`

Виджеты (`value`, `indicator`, `chart`, …) читают переменные **конкретного** объекта платформы (`DEVICE`, `CUSTOM`, …). Путь к этому объекту задается двумя способами.

### Откуда берётся `objectPath`

`objectPath` — **поле в JSON-виджете** внутри переменного `layout` объекта `DASHBOARD`. Его не передаёт родительский React-компонент во время выполнения.

| Источник | Когда |
|----------|--------|
| Bootstrap | `DashboardLayouts.java` записывает layout при первом запуске |
| Dashboard Builder | админ выбирает объект в поле «Объект» (`WidgetEditorPanel`) → `PUT .../layout` |
| Ручное редактирование | исправление макета JSON |

Пример статической привязки (один датчик):

```json
{
  "type": "value",
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "valueField": "value"
}
```

### Что такое `selectionKey`

`selectionKey` — **имя слота** в общем состоянии дашборда (`DashboardContext`), а не замена поля `objectPath`.

```typescript
selection: Record<string, string>
// { "device": "root.platform.devices.snmp-localhost" }
```

При отрисовке виджета путь вычисляется так (`resolveWidgetPath` в `dashboardUtils.ts`):

1. Если задано `selectionKey` **и** `selection[selectionKey]` не пусто → используется **выбранный путь**.
2. Иначе → используется **статический** `objectPath` из макета.
3. Если оба пусты → виджет показывает подсказку «Выберите…» / «—».

`objectPath` не «перезаписан» ключом: ключ лишь указывает, из какого слота контекста следует выбрать путь, когда пользователь что-то выбрал.

### Публикация/подписка внутри дашборда

Связка **не** «виджет A → виджет B». Связка по **совпадению строки** `selectionKey`:

| Роль | Тип виджета | Поле | Действие |
|------|-------------|------|----------|
| **Источник выбора** | `object-table` | `selectionKey`, `parentPath` | при клике по строке: `setSelection(key, child.path)` |
| **Потребитель** | `value`, `indicator`, `chart`, `sparkline`, `progress`, `gauge`, `status-badge`, `function`, `function-form` | тот же `selectionKey` | читает `selection[key]` как `objectPath` |

Все виджеты с **одинаковым** `selectionKey` отображают данные **одного и того же** конкретного объекта — это нормально (выбор детализации).

Для **нескольких независимых выборов** на одном экране воспользуйтесь **разными названиями** слотов, например `"device"` и `"order"`.

### Пример: Мониторинг хоста SNMP

Дашборд `root.platform.dashboards.snmp-host-monitoring`:

```json
{
  "id": "device-table",
  "type": "object-table",
  "parentPath": "root.platform.devices",
  "selectionKey": "device",
  "columnsJson": "[{\"variable\":\"sysName\",\"label\":\"Имя хоста\"},{\"variable\":\"driverStatus\",\"label\":\"Драйвер\"}]"
}
```

Графики **net ↓ / net ↑** ссылаются на переменные объекты `ifInOctetsRate` / `ifOutOctetsRate` (Б/с). Они продолжаются привязка платформы `counterRate(ifInOctets)`/`counterRate(ifOutOctets)` в моделях `snmp-agent-v1` при каждом опросе SNMP; сырные Counter32 остаются в `ifInOctets` / `ifOutOctets`. Подробнее: [BINDINGS.md](bindings.md).

```json
{
  "id": "hostname-value",
  "type": "value",
  "selectionKey": "device",
  "variableName": "sysName",
  "valueField": "value"
}
```

- Таблица загружает детей `parentPath`, в колонках — переменные **каждой** строки.
- Клик по строке публикует путь в `selection.device`.
- Виджеты с `selectionKey: "device"` опрашивают переменные **только выбранного** `DEVICE`.

Статический `objectPath` у потребителей в этом макете **не задан** — путь полностью по выбору.

### Откуда значения функции (данные на экране)

Дашборд **не** запрашивает протоколы (SNMP, Modbus, …) напрямую.

```text
Драйвер (poll) → переменные DEVICE на сервере
       ↓
GET /api/v1/objects/by-path/variables?path=...
       ↓
Виджет: variableName + valueField → отображение
```

Для SNMP-устройства нужны: модель с переменными (`snmp-agent-v1`), активированный драйвер и `driverPointMappingsJson`. Имена в `columnsJson` / `variableName` должны **совпадать** с именами запроса объекта.

Обновление UI: polling по `refreshIntervalMs` + WebSocket `/ws/objects` (`VARIABLE_UPDATED` инвалидирует кэш `variables`).

### Типичные ошибки конфигурации

| Ситуация | Результат |
|----------|-----------|
| Таблица `selectionKey: "device"`, виджет `selectionKey: "device"` | Работает |
| Таблица `"device"`, виджет `"order"` | Не связаны |
| Две таблицы с одним `selectionKey` | Один слот; побеждает последний клик |
| `selectionKey` без таблицы-источника | Пустой слот → fallback на `objectPath` или «—» |
| Заданы и `objectPath`, и `selectionKey` | При непустом выборе **приоритет у selection** |
| В колонке переменное имя не соответствует модели | Ячейка «—» |

В редакторе виджета: поле **«Ключ выбора (selectionKey)»** (`WidgetEditorPanel`). Пустая строка отключает привязку к контексту.

## Навигация между дашбордами

Виджеты могут открываться другим приборной панелью **переходом** (`navigate`) или **модальным окном** (`modal`).

| Механизм | тип | Поля |
|----------|------|------|
| Кнопка | `dashboard-link` | `targetDashboardPath`, `openMode`, `buttonLabel`, `modalTitle` |
| Клик по строке таблицы | `object-table` | `rowTargetDashboard`, `rowOpenMode` (+ `selectionKey` для передачи выбора) |
| Клик по карточке | `card-grid` | `cardTargetDashboard`, `cardOpenMode`, `cardSelectionKey` |

Выбор контекста (`selectionKey`) **сохраняется** при переходе между дашбордами в режим оператора — детализирующий дашборд может прочитать тот же ключ.

Пример: Обзор SNMP → клик по устройству в таблице → переход на детальную панель с `selectionKey: "device"`.

```json
{
  "type": "dashboard-link",
  "title": "Детали",
  "targetDashboardPath": "root.platform.dashboards.snmp-host-monitoring",
  "openMode": "modal",
  "buttonLabel": "Мониторинг SNMP",
  "modalTitle": "SNMP Host Monitoring"
}
```

При переходе admin-consoli дашборд открывается в новую вкладку редактора; в режиме оператора — меняет активную вкладку приложения или показывает модное окно на поверхности HMI.

### Схема потока данных

```mermaid
sequenceDiagram
  participant T as object-table
  participant C as DashboardContext
  participant V as value / chart / indicator
  participant API as REST variables

  T->>API: variables для каждой строки
  User->>T: клик по строке
  T->>C: selection[device] = path
  V->>C: читает selection[device]
  V->>API: variables для выбранного path
```

## Логика дашборда (Правило Платформы)

**Статус:** спецификация (ADR [0019](decisions/0019-platform-rule-unification.md)); время выполнения — по фазам реализации.

Сегодня `DashboardContext` в web-console — React state + `sessionStorage`. Целевая модель:

1. **`@dashboardContext`** — зарезервированная JSON-переменная на объекте `DASHBOARD` (источник устойчивости на расстоянии).
2. **Обязательные правила** на том же объекте с `target.kind: context` или `event` — показать/скрыть, режимы, события в журнале.
3. **Виджеты** — только макет и данные адреса (`selectionKey`, `paramKey`); без `behaviorJson` / мини-DSL.

| Задача | Механизм (планируется) |
|--------|-------------------|
| Таблица → детали | `selectionKey` + publishers пишут `selection.*` в context |
| Скрыть панель при alarm | rule → `widgets.alarm-panel.visible = false` |
| Fire event при смене режима | rule → `target.kind: event` |
| Фильтр event-feed | CEL `condition`, не `payloadFilterExpr` |

Подробнее: [PLATFORM_LOGIC.md](platform-logic.md), [BINDINGS.md](bindings.md).

## Связанный выбор (`selectionKey`) — кратко

См. раздел **«Привязка к объектам»** выше. Исторический пример с нарядами: таблица + `progress` + `function-form` с `selectionKey: "order"`.

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

## Grid Layout: форма-функция-форма (лабораторное задание 6)

Пример сетки из пакета **Lab Training** — дашборд `root.platform.dashboards.lab-form-grid`. Один виджет `function-form` вызывает функцию `appendTableRow` на устройстве виртуальной лаборатории и вводит текст в переменную `table`:

```json
{
  "columns": 12,
  "rowHeight": 72,
  "widgets": [
    {
      "id": "append-row",
      "type": "function-form",
      "title": "Append table row",
      "x": 0,
      "y": 0,
      "w": 6,
      "h": 4,
      "objectPath": "root.platform.devices.lab-userA-01",
      "functionName": "appendTableRow",
      "buttonLabel": "Append",
      "fieldsJson": "[{\"name\":\"int\",\"label\":\"Int\",\"type\":\"number\"},{\"name\":\"string\",\"label\":\"String\",\"type\":\"text\"}]"
    }
  ]
}
```

Поля `fieldsJson` Соответствует аргументам функций модели `virtual-lab-v1`. Размер `w:6` на сетке 12 колонок — половина экрана; `h:4` задаёт высоту в строках сетки (`rowHeight` × 4 пикселя).

Импорт готового layout: `POST /api/v1/platform/packages/import?packageId=lab-training` (см. [LAB_TRAINING.md](lab-training.md)).

## Типы виджетов

Полный каталог с описанием каждого поля и примерами — **[WIDGETS.md](widgets.md)**.

Краткие указатели:

| тип | Описание | Документация |
|------|----------|--------------|
| `value`, `indicator`, `toggle` | Метрики и состояния | [§ value](widgets.md) |
| `chart`, `sparkline` | Тренды (historian) | [§ chart](widgets.md) |
| `gauge`, `linear-gauge`, `liquid-gauge`, `progress` | Шкалы и прогресс | [§ gauge](widgets.md) |
| `function`, `function-form`, `input-form` | Действия и формы | [§ function](widgets.md) |
| `object-table`, `card-grid`, ​​`map`, `object-tree` | Каталоги объектов | [§ таблица объектов](widgets.md) |
| `dashboard-link`, `sub-dashboard`, `nav-menu` | Навигация | [§ dashboard-link](widgets.md) |
| `report`, `event-feed`, `work-queue` | Отчёты, события, BPMN | [§ report](widgets.md) |
| `spreadsheet` | Таблица с формулами | [WIDGETS.md § электронная таблица](widgets.md) + [SPREADSHEET_WIDGET.md](spreadsheet-widget.md) |
| `panel`, `tab-panel`, `carousel`, … | Композиция экрана | [§ composition](widgets.md) |
| `label`, `image`, `html-snippet` | Оформление | [§ session/static](widgets.md) |
| `scada-mimic` | SCADA мнемосхема | [§ scada-mimic](widgets.md), [SCADA.md](scada.md) |

## Контекст дашборда (DashboardSession)

При открытии дашборда (навигация/модальное) передаётся **амарный** контекст:

- `selection` — `{ "device": "root...devices.snmp-01" }` для `selectionKey`
- `params` — произвольный JSON (`clusterPath`, …)

Расположение поля:

| Поле | Назначение |
|------|------------|
| `rowSelectionKey` / `rowParamsJson` | object-table / card-grid / map при клике |
| `contextSelectionJson` / `contextParamsJson` | dashboard-link |
| `paramKey` / `contextPathKey` | чтение из session в виджете |
| `modelHintPath` | образец объекта для dropdown переменных в редакторе |

Operator URL: `?ctx.device=root.platform.devices.d1` или `?ctx={"selection":{"device":"..."}}`

Исходники типа: `apps/web-console/src/types/dashboard.ts`  
Просмотр-компоненты: `apps/web-console/src/components/dashboard/widgets/`  
Разрешение пути: `apps/web-console/src/components/dashboard/dashboardUtils.ts` (`resolveWidgetPath`), `hooks/useWidgetObjectPath.ts`

## поля формы функцииJson

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

## столбцы таблицы объектовJson

Колонки ссылаются на **имя переменных** дочернего объекта (не OID и не поле драйвера). Значение для ячейки: поле `value` в `DataRecord` (см. `readFieldValue` в веб-консоли).

```json
[
  { "variable": "sysName", "label": "Имя хоста" },
  { "variable": "driverStatus", "label": "Драйвер" },
  { "variable": "sysUpTime", "label": "Uptime" }
]
```

Для привязки таблиц к детализирующим виджетам задайте `selectionKey` (см. § «Привязка к объектам»).

### Тренд на несколько перьев (BL-89)

В режиме runtime-режима (не в редакторе) правый клик по строке `object-table` с колонками `variable` открывает контекстное меню **«Тренд: …»**. Открывается `MultiPenTrendModal`:

| возможность | Описание |
|-------------|----------|
| До 4 перьев | Добавление других рекомендаций к той же строке из выпадающего списка |
| Диапазоны | 1ч, 6ч, 24ч, сегодня, 7д (календарные диапазоны с пользователем TZ — BL-70) |
| Pan/zoom | Recharts `Brush` под графиком; двойной клик — сброс |
| Экспорт | CSV всех перьев |
| Лимит точек | 1000 на перо (исторический API) |

Компоненты: `MultiPenTrendModal.tsx`, `useMultiPenTrendData.ts`, `types/trendPen.ts`.

## электронная таблицаConfigJson

Виджет **электронная таблица** — фиксированная сетка `rows × cols` с адресацией А1. Пересчет на клиента (встроенный `ispfSheetEval`).

## Электронная таблица

Виджет типа `spreadsheet` — HMI-таблица с формулами. Адаптация для смены калькуляторов, сводок, простых расчетов на экране оператора без Excel.

**Лаборатория:** `root.platform.dashboards.lab-calculator` (режим **настроен**).  
**Подробно:** [SPREADSHEET_WIDGET.md](spreadsheet-widget.md). Операторская версия: [OPERATOR_GUIDE.md](operator-guide.md).

### Интерфейс на экране

```
┌─ Calculator ─────────────────────────────────────────┐
│ [Отменить][Повтор][Копир.][Вставить][CSV]  ← free   │
│  B2  │ =A2*1.1                          ← formula bar│
├──────┴───────────────────────────────────────────────┤
│    │  A  │  B  │  C  │  D  │                        │
│  1 │ A  │ B  │ Sum│ ... │                        │
│  2 │ 10 │5.5 │15.5│ 11  │  ← значения / результаты │
└──────────────────────────────────────────────────────┘
```

| Область | Описание |
|---------|----------|
| **Строка формулы** | Слева — адрес (А1, В2…). Справа — сырое определение ячейки: число, текст или формула с `=`. |
| **Заголовки столбцов** | А, Б, В… (как в Excel). |
| **Номера строк** | 1, 2, 3… слева от сетки. |
| **Панельные инструменты** | Только в режиме **бесплатно**: отменить/повторить, копировать/вставить, выгрузка CSV. |
| **Фильтры столбцов** | Если заданы в макете — поля фильтра над таблицей (режим **настроен**). |

Ячейки **обязательные** (живые данные с объекта) представлены курсивом и **не редактируются**.

### Как пользоваться (оператор)

#### Режим «Свободная сетка» (`sheetMode: free`, по умолчанию)

Разработчик задаёт только размер сетки и начальные опциональные значения. **Формы и данные выводит оператор** на экран.

1. **Выделить ячейку** — одиночный клик. Адрес и интересы проявляются в строке формулы.
2. **Введите значение** — двойной клик, **F2** или **Enter** (при уже выделенной ячейке):
   - число: `10`, `3.14`
   - текст: `OK`, `Смена 1`
   - формула: начало с `=`, например `=A1+A2`, `=SUM(A1:A10)`, `=A2*1.1`
3. **Подтвердить** — **Ввод** (переход на ячку ниже) или кликните вне ячейки. В строке формулы — **Enter** / **Tab** / потеря фокуса.
4. **Отменить ввод** — **Esc** (в ячейке или в формуле строки).
5. **Перемещение** — **Tab** / **Shift+Tab**, направление ↑↓ ←→ (фокус должен находиться на таблице: клик по сетке).
6. **Копирование** — выделить ячейку → **Ctrl+C** или кнопка «Копировать» → выделить цель → **Ctrl+V** или «Вставить».
7. **Экспорт** — кнопка **CSV** загружает текущие **отображаемые** значения (формулы результатов, не сырые `=...`).

Формулы пересчитываются сразу при удалении зависимых ячеек (как в Excel).

**Пример сценария:** в A1 введите `100`, в B1 `=A1*0.2`, в C1 `=A1+B1` → C1 покажет `120`.

#### Режим «Настроенный шаблон» (`sheetMode: configured`)

Расположение задаёт клетки ячейки (`label`, `input`, `formula`, `binding`). Оператор редактирует только ячейку **`input`** (поля ввода в сетке). Формулы и данные, заданные разработчиком; строковая формула **только для просмотра**.

1. Нажмите на поле ввода → изменить значение → **Tab** / **Enter** для перехода к следующему полю.
2. Ячейки **формула** обновляются автоматически.
3. Ячейки **binding** отображают живое значение переменного объекта.

**Лабораторный калькулятор:** изменяются A2 и B2 → пересчитаются Сумма (C2), A×110% (D2) и итог по столбцу A (C4).

### Сохранение данных

| `persistMode` | Где хранится | Когда пропадает |
|---------------|--------------|-----------------|
| `session` (по умолчанию) | Параметры сессии дашборда (`sessionKey`, обычно `sheet:{widgetId}`) | При закрытии вкладки/сбросе сеанса |
| `variable` | Переменная объект (`valuesVariable`, RECORD_LIST `{cell, value}`) | Не пропадает при F5; общий для всех операторов этого объекта |

В режиме **free** в `value` сохраняются и формулы: `{ "cell": "B2", "value": "=A2*2" }`.

Автосохранение с небольшой задержкой (~400 мс) после редактирования. Если виджет с `editable: false` — таблица только для просмотра.

### Формулы

Поддерживается подмножество функций Excel через `ispfSheetEval`: `SUM`, `AVERAGE`, `IF`, `ROUND`, ссылки на ячейки (`A1`, `B2:B10`) и ISPF-функции.

**Функции платформы ISPF** (чтение данных объектов в формуле):

| функция | Пример | Назначение |
|---------|--------|------------|
| `ISPREF` | `=ISPREF("root.platform.devices.lab-userA-01","intValue","value")` | Текущее значение переменной |
| `ISPSUM` | `=ISPSUM("ordersTable","int")` | Сумма столбца RECORD_LIST (по binding-ячейкам) |
| `ISPHIST` | `=ISPHIST("root...device","temperature",5)` | Значение из historian за последние N минут |

Ошибки формулы отображаются как `#ERROR` в ячейке.

### Настройка в Dashboard Builder

| Поле | Описание |
|------|----------|
| `sheetMode` | `free` — свободная сетка; `configured` — шаблон с `kind` |
| `sheetConfigJson` | JSON: `rows`, `cols`, `cells`, опционально `columnFilters`, `dataRegion`, `conditionalStyles` |
| `persistMode` | `session` \| `variable` |
| `valuesVariable` | Имя переменной при `persistMode: variable` (напр. `sheetValues`) |
| `sessionKey` | Ключ session (по умолчанию `sheet:{id}`) |
| `editable` | `false` — запрет редактирования на HMI |
| `objectPath` | Объект для variable persist и binding |

Кнопки шаблонов в редакторе: «Свободная сетка», «Базовая сетка», «Калькулятор».

### Режимы (`sheetMode`) — справочник

| Режим | Поведение |
|-------|-----------|
| `free` (по умолчанию) | **Свободная сетка** — оператор вводит значение или формулу `=...` в любую ячейку; формульная строка редактируемая; F2 / двойной клик |
| `configured` | **Шаблон** — ячейки с `kind` в макете; редактируется только `input`; строка формул только для чтения |

В режиме `free` `cells` в `sheetConfigJson` — только **начальные семенные**-значения (опционально) и `binding` для live-данных.

### настроено: примерsheetConfigJson

```json
{
  "rows": 6,
  "cols": 4,
  "frozenRows": 1,
  "cells": {
    "A1": { "kind": "label", "text": "A" },
    "A2": { "kind": "input", "default": "10" },
    "B2": { "kind": "formula", "expr": "=A2*2" },
    "C2": { "kind": "formula", "expr": "=SUM(A2:A10)" },
    "D3": {
      "kind": "binding",
      "objectPath": "root.platform.devices.lab-userA-01",
      "variableName": "intValue",
      "valueField": "value"
    }
  },
  "columnFilters": [{ "column": "A" }]
}
```

| `kind` | Назначение |
|--------|------------|
| `label` | Статический заголовок |
| `input` | Ввод оператора |
| `formula` | Выражение `=SUM(A1:A10)` и т.п. |
| `readonly` | Только отображение |
| `binding` | Live-значение переменной объекта |

**Персистенция:** `persistMode: "session"` (ключ `sessionKey`, по умолчанию `sheet:{widgetId}`) или `"variable"` (`valuesVariable` — строка RECORD_LIST `[{cell, value}]`). В режиме `free` в `value` главе и формуле (`"=A2*2"`).

**Функции платформы в формулах:** `ISPREF(path, var, [field])`, `ISPSUM(tableVar, column)`, `ISPHIST(path, var, [minutes])`.

**dataRegion** (опционально): блок строк из переменной RECORD_LIST — `variableName`, `startRow`, `startCol`, `columnFields`.

**условные стили**: `[{ "when": "=B2>80", "style": { "backgroundColor": "#fee" } }]` — простые условия `>`, `<` для подсветки ячеек.

Lab demo: `root.platform.dashboards.lab-calculator` (`sheetMode: configured`), переменная `sheetValues` на lab device.

**Эталонный пакет:** [examples/spreadsheet-demo](../examples/spreadsheet-demo/) — модель `sheet-storage-v1`, устройство `root.platform.devices.sheet-demo-01`, два дашборда (сессия/переменная persist).

Полный справочник полей `sheetConfigJson`, `dataRegion`, `conditionalStyles` — в [SPREADSHEET_WIDGET.md](spreadsheet-widget.md).

## Стили элементов (`stylesJson`)

В любом виджете в макете можно разместить поле **`stylesJson`** — JSON-объект со стилями отдельных частей карточки. Редактируется в Dashboard Builder (поле «Стили элементов») или вручную в макете.

### Ключи элементов

| Ключ | Элемент | Типы виджетов |
|------|---------|---------------|
| `card` | Корневая карточка | все |
| `title` | Заголовок | все |
| `body` | Основной контейнер | все |
| `value` | Значение / кнопка / текущее число | значение, диаграмма, спарклайн, датчик, прогресс, функция |
| `unit` | Единица измерения | value, progress |
| `meta` | Подпись внизу | value, gauge |
| `label` | Текст статуса | indicator |
| `dot` | Точка индикатора | indicator |
| `badge` | Badge статуса | status-badge |
| `table` | Таблица | object-table |
| `chart` | Область графика | chart, sparkline |

Значения — **camelCase CSS-свойства** (`fontSize`, `color`, `whiteSpace`, `overflowY`, `display`…). Неразрешённые свойства теряются.

### Пример: длинный текст в значении

```json
{
  "value": {
    "fontSize": "0.82rem",
    "whiteSpace": "normal",
    "overflowY": "auto"
  },
  "meta": {
    "display": "none"
  }
}
```

В макете виджета:

```json
{
  "type": "value",
  "title": "Описание ОС",
  "variableName": "sysDescr",
  "selectionKey": "device",
  "stylesJson": "{\"value\":{\"fontSize\":\"0.82rem\",\"whiteSpace\":\"normal\",\"overflowY\":\"auto\"},\"meta\":{\"display\":\"none\"}}"
}
```

Стили из `stylesJson` **до разделения** классов по умолчанию (`dash-widget-metric`, `dash-widget-text`…), а не заменяют их. Для чисел по умолчанию — крупный шрифт; для строки — компактный; длинный текст — прокрутка внутри карточки.

Исходники: `widgetStyles.ts`, `DashWidgetShell.tsx`.

## Конструктор дашбордов (пользовательский интерфейс)

- **Режим просмотра** — live-данные, refresh по `refreshIntervalMs`
- **Режим редактирования** — перетаскивание, изменение размера, свойство панели виджета.
- Сохранение: `PUT /api/v1/dashboards/by-path/layout`
- **Объединенные дашборды** (`root.platform.federation.*` или привязка к `DASHBOARD`): сохранение макета/заголовка проксируется на удаленном узле; пути виджетов в пользовательском интерфейсе локальными (см. [FEDERATION.md](federation.md))

Компоненты: `DashboardBuilder`, `DashboardGrid`, `WidgetEditorPanel`.

## HMI оператора

`?mode=operator&app=<appId>` — навигация по дашбордам из `operatorUi`, только макет просмотра (`DashboardBuilder` + `operatorMode`).  
Боковая панель: очередь работ + журнал событий.

Привязка дашборда: query param `dashboard` или вкладки в `OperatorDashboardApp` (см. `OperatorView.tsx`).

## API

```http
GET /api/v1/dashboards/by-path?path=root.platform.dashboards.demo-sensor
PUT /api/v1/dashboards/by-path/layout?path=...  (body: { "layoutJson": "..." })
PUT /api/v1/dashboards/by-path/title?path=...     (body: { "title": "..." })
```

Для федеративного пути (`federationProxy=true`) то же самое `PUT` — сервер unmap'ит макет и записывает на удаленный компьютер.

## Стили

CSS виджетов: `apps/web-console/src/styles.css` (секция `dash-*`, `function-form-*`).
