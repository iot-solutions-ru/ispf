# Виджет spreadsheet

`spreadsheet` — HMI-таблица с адресацией ячеек A1 (`A1`, `B2`, `C10`) и формулами на клиенте. Подходит для сменных калькуляторов, сводок, простых расчётов и технологических таблиц без отдельного Excel.

Сводный каталог всех виджетов: [WIDGETS.md](WIDGETS.md). Основы дашбордов, `selectionKey` и layout: [DASHBOARDS.md](DASHBOARDS.md).

## Почему может быть `#NAME?`

`#NAME?` означает, что движок формул не распознал имя функции. В ISPF это может быть:

- опечатка в имени функции: `=SUMM(A1:A10)` вместо `=SUM(A1:A10)`;
- функция не поддерживается HyperFormula;
- старая сборка UI, где стандартные функции HyperFormula не были подключены вместе с ISPF-функциями.

После исправления стандартные функции вроде `SUM`, `AVERAGE`, `MIN`, `MAX`, `IF`, `ROUND` доступны вместе с `ISPREF`, `ISPSUM`, `ISPHIST`.

## Режимы

| Режим | Когда использовать | Поведение |
|-------|--------------------|-----------|
| `free` | Оператор сам заполняет таблицу | Любая неблокированная ячейка редактируется; доступны formula bar, undo/redo, copy/paste, CSV |
| `configured` | Разработчик задаёт шаблон | Редактируются только ячейки `input`; `label`, `formula`, `binding`, `readonly` задаются в `sheetConfigJson` |

`sheetMode` по умолчанию — `free`.

## Интерфейс

| Элемент | Описание |
|---------|----------|
| Строка формул | Слева адрес выделенной ячейки, справа сырое содержимое: текст, число или формула с `=` |
| Сетка | Показывает вычисленные значения; формулы пересчитываются сразу |
| Панель инструментов | В `free`: undo/redo, copy/paste, экспорт CSV |
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

Диапазоны пишутся как в Excel: `A1:A10`, `A1:C5`. Имена стандартных функций используются английские.

## ISPF-функции

### `ISPREF(path, variableName, [field])`

Возвращает текущее значение переменной объекта.

```text
=ISPREF("root.platform.devices.demo-sensor-01","temperature")
=ISPREF("root.platform.devices.demo-sensor-01","status","online")
```

`field` по умолчанию — `value`. Частые поля: `value`, `raw`, `int`, `string`, `online`, `unit`.

Важно: значения для `ISPREF` попадают в кеш формул через binding-ячейки текущего `sheetConfigJson`. Если нужно читать переменную в формуле, обычно добавьте для неё binding-ячейку (можно вынести её за видимую рабочую область или сделать неакцентной стилем).

### `ISPSUM(tableVariable, column)`

Суммирует числовой столбец RECORD_LIST-переменной, загруженной через binding.

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
| `binding` | `objectPath`, `variableName`, `valueField` | Live-значение переменной ISPF |

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
