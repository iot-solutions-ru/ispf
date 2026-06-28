# Виджет spreadsheet

`spreadsheet` — HMI-таблица с адресацией ячеек A1 (`A1`, `B2`, `C10`) и формулами на клиенте. Подходит для сменных калькуляторов, сводок, простых расчётов и технологических таблиц без отдельного Excel.

Сводный каталог всех виджетов: [WIDGETS.md](WIDGETS.md). Основы дашбордов, `selectionKey` и layout: [DASHBOARDS.md](DASHBOARDS.md).

## Почему может быть `#NAME?`

`#NAME?` означает, что движок формул не распознал имя функции. В ISPF это может быть:

- опечатка в имени функции: `=SUMM(A1:A10)` вместо `=SUM(A1:A10)`;
- После исправления стандартные функции вроде `SUM`, `AVERAGE`, `MIN`, `MAX`, `IF`, `ROUND` поддерживаются **встроенным движком ISPF** (`ispfSheetEval`) вместе с `ISPREF`, `ISPSUM`, `ISPHIST`.

## Режимы

| Режим | Когда использовать | Поведение |
|-------|--------------------|-----------|
| `free` | Оператор сам заполняет таблицу | Любая неблокированная ячейка редактируется; formula bar, undo/redo, copy/paste, **импорт/экспорт XLSX**, CSV |
| `configured` | Разработчик задаёт шаблон | Редактируются только ячейки `input`; `label`, `formula`, `binding`, `readonly` задаются в `sheetConfigJson` |

`sheetMode` по умолчанию — `free`.

## Интерфейс

| Элемент | Описание |
|---------|----------|
| Строка формул | Слева адрес выделенной ячейки, справа сырое содержимое: текст, число или формула с `=` |
| Сетка | Показывает вычисленные значения; формулы пересчитываются сразу |
| Панель инструментов | В `free`: undo/redo, copy/paste, вставка/удаление строк и столбцов, **импорт XLSX**, **экспорт XLSX**, экспорт CSV |
| Вкладки листов | Несколько листов в одной книге; переключение вкладками; cross-sheet формулы (`=Sales!A1`) |
| Binding-ячейки | Live-данные переменных ISPF; не редактируются оператором |

Горячие клавиши в `free`:

| Клавиши | Действие |
|---------|----------|
| `F2` / двойной клик | Редактировать ячейку |
| `Enter` | Начать редактирование; после ввода сохранить и перейти вниз |
| `Esc` | Отменить редактирование |
| `Tab` / `Shift+Tab` | Следующая / предыдущая ячейка |
| Стрелки | Перемещение выделения |
| `Ctrl+Z` / `Ctrl+Y` | Undo / redo |
| `Ctrl+C` / `Ctrl+V` | Copy / paste |

## Формулы

Формула всегда начинается с `=`.

Примеры:

```text
=A1+B1
=A2*1.2
=SUM(A2:A10)
=AVERAGE(B2:B20)
=IF(C2>80,"ALARM","OK")
=ROUND(A2/3,2)
```

Диапазоны пишутся как в Excel: `A1:A10`, `A1:C5`, **`D:D`** (весь столбец до конца сетки), **`Sheet!D:D`** (столбец на другом листе). Поддерживаются английские имена функций и русские алиасы (`СУММ` → `SUM`, `ЕСЛИ` → `IF`). Разделитель аргументов — `,` или `;` (как в русской локали Excel).

### Импорт и экспорт XLSX

В режиме `free` на панели инструментов доступны **Импорт XLSX** и **Экспорт XLSX** (библиотека ExcelJS, MIT).

| Этап | Поведение |
|------|-----------|
| Импорт | Все листы книги → вкладки; ячейки, формулы (в т.ч. `Sheet!A1`), **объединения**, **базовые стили**; сетка до 500×52 |
| Экспорт | Вся книга → `.xlsx` с формулами, стилями и merge |
| Сессия | Значения, формулы, метаданные книги (листы, стили, merge) сохраняются в session / variable |
| Предупреждения | Неподдерживаемые функции дают `#NAME?`; после импорта показывается список функций с кнопкой закрытия (баннер не исчезает автоматически) |

Оператор конкатенации `&` поддерживается: `="A"&"B"`.

#### Поддерживаемые функции

| Категория | Функции |
|-----------|---------|
| Агрегаты | `SUM`, `AVERAGE`, `MIN`, `MAX`, `COUNT`, `COUNTA`, `COUNTBLANK`, `PRODUCT`, `MEDIAN`, `STDEV`, `STDEV.S`, `SUBTOTAL` |
| Условные | `IF`, `IFERROR`, `IFS`, `SUMIF`, `COUNTIF`, `AVERAGEIF`, `SUMIFS`, `COUNTIFS`, `AVERAGEIFS`, `MAXIFS`, `MINIFS`, `SUMPRODUCT` |
| Lookup | `VLOOKUP`, `HLOOKUP`, `INDEX`, `MATCH`, `XLOOKUP` |
| Математика | `ABS`, `MOD`, `POWER`, `SQRT`, `INT`, `ROUND`, `ROUNDUP`, `ROUNDDOWN`, `CEILING`, `FLOOR`, `TRUNC`, `MROUND`, `LOG`, `LN`, `LOG10`, `EXP`, `PI`, `SIGN`, `RAND`, `RANDBETWEEN` |
| Логика | `AND`, `OR`, `NOT`, `TRUE`, `FALSE`, `SWITCH`, `CHOOSE`, `IFNA` |
| Текст | `LEN`, `LEFT`, `RIGHT`, `MID`, `TRIM`, `UPPER`, `LOWER`, `PROPER`, `CHAR`, `CODE`, `REPT`, `CONCAT`, `CONCATENATE`, `TEXT`, `TEXTJOIN`, `FIND`, `SEARCH`, `SUBSTITUTE`, `REPLACE`, `VALUE`, `EXACT` |
| Дата/время | `TODAY`, `NOW`, `YEAR`, `MONTH`, `DAY`, `DATE`, `DAYS`, `WEEKDAY`, `HOUR`, `MINUTE`, `SECOND`, `TIME`, `EDATE`, `EOMONTH`, `DATEDIF`, `NETWORKDAYS`, `WORKDAY`, `DATEVALUE`, `TIMEVALUE`, `WEEKNUM`, `YEARFRAC` |
| Статистика | `VAR`, `VAR.S`, `PERCENTILE`, `QUARTILE`, `LARGE`, `SMALL`, `RANK` |
| Финансы | `NPV`, `IRR`, `PMT`, `FV`, `PV`, `NPER`, `RATE` |
| Тригонометрия | `SIN`, `COS`, `TAN`, `ASIN`, `ACOS`, `ATAN`, `ATAN2`, `RADIANS`, `DEGREES` |
| Адресация | `ROW`, `COLUMN`, `ROWS`, `COLUMNS` |
| Проверки | `ISBLANK`, `ISNUMBER`, `ISTEXT`, `ISERROR`, `ISNA`, `ISERR`, `NA`, `ISLOGICAL`, `ISODD`, `ISEVEN` |
| Текст (очистка) | `CLEAN` |
| ISPF | `ISPREF`, `ISPSUM`, `ISPHIST` |

Cross-sheet ссылки: `=Sheet2!A1`, `=SUM(Sales!A1:A10)`.

Примеры для IoT/отчётов:

```text
=SUMIFS(C2:C100, A2:A100, "sensor-01", B2:B100, ">80")
=XLOOKUP(E2, A2:A50, C2:C50, "—")
=IFS(A2>90,"ALARM", A2>70,"WARN", TRUE,"OK")
=TEXTJOIN(", ", TRUE, A2, B2, C2)
=NETWORKDAYS(DATE(2026,1,1), TODAY())
=MEDIAN(H2:H100)
=NPV(0.08,C2:C24)
=PMT(0.05/12,36,-10000)
=FIND("@",A2)
=DATEVALUE("2026-06-28")
=(E3-B3)/B3
```

Русские алиасы: `СУММ`, `ЕСЛИ`, `ЕСЛИМН`, `ПРОСМОТР`, `СМЕЩ`, `СУММЕСЛИМН`, `НАЙТИ`, `ПОДСТАВИТЬ`, `ДАТАЗНАЧ`, `ЧПС`, `ПЛТ`, `ДИСП`, `СТРОКА`, `СТОЛБЕЦ` и др.

Ограничения: `SUBTOTAL` не учитывает скрытые строки; wildcards в `XLOOKUP` не поддерживаются; `YEARFRAC` — упрощённые базы 0/1; `SEARCH` wildcards — базовые `?` и `*`.

## ISPF-функции

### `ISPREF(path, variableName, [field])`

Возвращает текущее значение переменной объекта.

```text
=ISPREF("root.platform.devices.demo-sensor-01","temperature")
=ISPREF("root.platform.devices.demo-sensor-01","status","online")
```

`field` по умолчанию — `value`. Частые поля: `value`, `raw`, `int`, `string`, `online`, `unit`.

Переменные из `ISPREF` / `ISPHIST` / `ISPSUM` **автоматически подтягиваются** из формул (binding-ячейка для кеша не обязательна, но удобна для отображения live-значения в сетке). При изменении переменной таблица пересчитывает зависимые формулы:

| Канал | Поведение |
|-------|-----------|
| WebSocket | `VARIABLE_UPDATED` / `EVENT_FIRED` на watched path → refetch bindings → `refreshComputed()` |
| Polling | `refreshIntervalMs` виджета, если WS недоступен |
| Binding-ячейки | Обновляют и отображаемое значение, и кеш формул |

Для `ISPHIST` historian опрашивается с тем же интервалом (или по событию через invalidate).

### `ISPSUM(tableVariable, column)`

Суммирует числовой столбец RECORD_LIST-переменной объекта дашборда (`objectPath` виджета).

```text
=ISPSUM("ordersTable","int")
=ISPSUM("ordersTable","value")
```

### `ISPHIST(path, variableName, [minutes])`

Берёт последнее историческое значение переменной за окно в минутах. Если historian недоступен, используется текущее значение из binding-кеша.

```text
=ISPHIST("root.platform.devices.demo-sensor-01","temperature",5)
```

## Конфигурация виджета

Поля виджета:

| Поле | Описание |
|------|----------|
| `sheetMode` | `free` или `configured` |
| `sheetConfigJson` | JSON конфигурации сетки |
| `persistMode` | `session` или `variable` |
| `valuesVariable` | Переменная RECORD_LIST для сохранения значений при `persistMode: variable` |
| `sessionKey` | Ключ в session; по умолчанию `sheet:{widgetId}` |
| `editable` | `false` — только просмотр |
| `objectPath` / `selectionKey` | Контекст объекта для binding и variable persist |

Минимальный `free`:

```json
{
  "type": "spreadsheet",
  "title": "Калькулятор",
  "sheetMode": "free",
  "sheetConfigJson": "{\"rows\":20,\"cols\":8,\"cells\":{}}"
}
```

`configured` с формулами и live binding:

```json
{
  "type": "spreadsheet",
  "title": "Сводка",
  "sheetMode": "configured",
  "sheetConfigJson": "{\"rows\":6,\"cols\":4,\"frozenRows\":1,\"cells\":{\"A1\":{\"kind\":\"label\",\"text\":\"Параметр\"},\"B1\":{\"kind\":\"label\",\"text\":\"Значение\"},\"A2\":{\"kind\":\"label\",\"text\":\"Температура\"},\"B2\":{\"kind\":\"binding\",\"objectPath\":\"root.platform.devices.demo-sensor-01\",\"variableName\":\"temperature\",\"valueField\":\"value\"},\"C2\":{\"kind\":\"formula\",\"expr\":\"=B2*1.8+32\"}}}"
}
```

## `sheetConfigJson`

| Поле | Тип | Описание |
|------|-----|----------|
| `rows` | number | Количество строк |
| `cols` | number | Количество столбцов |
| `frozenRows` / `frozenCols` | number | Закреплённые строки / столбцы |
| `colLabels` | string[] | Подписи столбцов вместо A, B, C |
| `cells` | object | Настройки ячеек по адресу A1 |
| `columnFilters` | array | Фильтры по столбцам в `configured` |
| `dataRegion` | object | Заполнение блока из RECORD_LIST |
| `conditionalStyles` | array | Условная подсветка |

Типы ячеек:

| `kind` | Поля | Назначение |
|--------|------|------------|
| `label` | `text`, `style` | Статическая подпись |
| `input` | `default`, `validation`, `format`, `style` | Ввод оператора |
| `formula` | `expr`, `format`, `style` | Формула |
| `readonly` | `default`, `format`, `style` | Нередактируемое значение |
| `binding` | `objectPath`, `variableName`, `valueField`, `historyMinutes?` | Live snapshot или historian (см. ниже) |

### Binding + historian (`historyMinutes`)

Для ячеек `kind: "binding"` можно задать `historyMinutes` (целое > 0): виджет запрашивает historian за окно и показывает **последний** числовой сэмпл в интервале (fallback на live snapshot, если historian пуст). То же окно используется в `ISPHIST(path, var, minutes)`.

```json
"B2": {
  "kind": "binding",
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "valueField": "value",
  "historyMinutes": 15
}
```

Ограничение: только latest в окне (не avg/min/max); максимум окна — 10080 мин (7 дней).

Пример `dataRegion`:

```json
{
  "rows": 20,
  "cols": 5,
  "cells": {},
  "dataRegion": {
    "variableName": "orders",
    "startRow": 1,
    "startCol": 0,
    "columnFields": ["name", "qty", "status"]
  }
}
```

`startRow` и `startCol` — 0-based.

## Сохранение

| `persistMode` | Где хранится | Когда удобно |
|---------------|--------------|--------------|
| `session` | Session дашборда | Черновые расчёты одного оператора |
| `variable` | RECORD_LIST-переменная объекта (`valuesVariable`) | Нужно пережить F5 или поделиться таблицей между операторами |

Эталонная конфигурация «объект + виджет»: [examples/spreadsheet-demo](../examples/spreadsheet-demo/README.md).

В `free` сохраняются и значения, и формулы:

```json
[
  { "cell": "A2", "value": "10" },
  { "cell": "C2", "value": "=SUM(A2:A10)" }
]
```

## Практические советы

- Для операторского калькулятора используйте `free` и `persistMode: variable`.
- Для регламентной формы используйте `configured`: подписи как `label`, редактируемые поля как `input`, расчёты как `formula`.
- Для live-данных заведите `binding`-ячейки, а расчёты стройте обычными ссылками (`=B2*1.2`) или ISPF-функциями.
- Если видите `#NAME?`, начните с проверки имени функции и обновления UI-сборки.
