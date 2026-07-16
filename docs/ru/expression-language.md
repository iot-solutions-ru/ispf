> **Язык:** полный русский перевод. Канонический английский: [en/expression-language.md](../en/expression-language.md).

# Справочник языка выражений

> **Статус:** Stable — пользовательский справочник CEL, platform bindings и historian helpers. Теги: [doc-status.md](doc-status.md).

Полный справочник языка выражений ISPF: правила привязок, условия алертов, gateway в BPMN и historian-вычисления.

| Связанные документы | |
|---------------------|---|
| Правила привязок / активаторы / targets | [bindings](bindings.md) |
| Модель WHEN → IF → THEN | [platform-logic](platform-logic.md) |
| Рецепты historian | [analytics-historian-cookbook](analytics-historian-cookbook.md) |
| Пакеты формул / catalog API | [analytics-formulas-and-packs](analytics-formulas-and-packs.md) |
| Живой каталог | `GET /api/v1/platform/analytics/catalog` |
| Валидация | `POST /api/v1/expressions/validate` |

**Источник истины в коде:** `PlatformBindingRegistry` / `PlatformBindingCatalog` (`ispf-expression`), `AnalyticsEvaluatorRegistry` + `HistorianCelPreprocessor`, Google CEL через `ExpressionEngine`.

---

## 1. Два режима выражений

| Режим | Когда | Ограничение |
|-------|-------|-------------|
| **Platform binding** | Вся строка — один builtin (`movingAvg(...)`, `read(...)`, …) | **Только целиком** — нельзя смешивать с `+` / CEL в той же строке |
| **CEL** | Всё остальное, что компилируется как Google CEL | `self` / `context` / `input`; внутри CEL нет зарегистрированных ISPF-функций |

Historian-правила (`kind: historian`) используют **helpers** (`avg`, `oee`, …) и/или **CEL-композиции**, где helpers сначала раскрываются в числа.

---

## 2. Литералы и операторы (CEL)

Используется **Google CEL**. Числа телеметрии в `self` приводятся к **double**.

### Литералы

| Тип | Примеры | Заметки |
|-----|---------|---------|
| Boolean | `true`, `false` | |
| Null | `null` | |
| Integer | `42`, `-1` | При смешении с double / historian лучше **`42.0`** |
| Double | `3.14`, `2.0`, `0.0` | **Предпочтительный** вид для арифметики |
| String | `"alarm"`, `'ok'` | Экранирование `\"` / `\'` |
| List | `[1.0, 2.0]` | |
| Map | `{"a": 1.0}` | |

### Операторы

| Класс | Операторы |
|-------|-----------|
| Арифметика | `+`, `-`, `*`, `/`, `%` |
| Сравнение | `==`, `!=`, `<`, `<=`, `>`, `>=` |
| Логика | `&&`, `\|\|`, `!` |
| Тернарный | `cond ? thenExpr : elseExpr` |

Стандартные builtins CEL (`size()` и др.) — см. [спецификацию CEL](https://github.com/google/cel-spec).

### Доступ к полям

| Форма | Пример | Заметки |
|-------|--------|---------|
| Точка | `self.temperature.value` | Предпочтительна |
| Скобки | `self.temperature["value"]` | Перед компиляцией переписывается в точку |

---

## 3. Идентификаторы в CEL

| Имя | Когда появляется | Смысл |
|-----|------------------|--------|
| `self` | Есть `self` в тексте | Локальные переменные → карты полей (`value`, …). Числа — `double`. |
| `parent` | Упомянут | **Всегда пустой `{}`** — не использовать |
| `context` | Упомянут | Входной map контекста вызова |
| `input` | Упомянут | **Тот же map, что `context`** |
| `payload` | Только `evaluateWithPayload` | Payload алерта/события — **нет** в обычном binding CEL |

---

## 4. PlatformRef (адреса)

| Вид | Форма | Пример |
|-----|-------|--------|
| Variable | `<object>/<name>[/<field>]` | `@/temperature` |
| Function | `<object>/fn/<name>` | `@/fn/calculate` |
| Event | `<object>/evt/<name>` | `@/evt/alarmRaised` |
| Tag | `<object>/tag/<ruleId>` | `.../tag/avg-temp-5m` |

`@` = объект, на котором выполняется правило. Подробнее: [bindings](bindings.md).

---

## 5. Reactive platform bindings

Выражение должно быть **целиком** одним вызовом. Stateful-состояние — в `@bindingState`.

### Сигнал

| Функция | Синтаксис | Описание | Пример |
|---------|-----------|----------|--------|
| `selectField` | `selectField(<source>[, field])` | Поле DataRecord | `selectField(temperature)` |
| `scale` | `scale(<source>, inMin, inMax, outMin, outMax[, field])` | Линейный масштаб | `scale(level, 0, 100, 0, 1)` |
| `clamp` | `clamp(<source>, min, max[, field])` | Ограничение диапазона | `clamp(pressure, 0, 100)` |
| `format` | `format("<printf>", <source>[, field])` | Число → строка | `format("%.1f", temperature)` |
| `unitConvert` | `unitConvert(<source>, from, to[, field])` | Температура `C`/`F`/`K` | `unitConvert(temperature, C, F)` |

### Stateful

| Функция | Синтаксис | Описание | Пример |
|---------|-----------|----------|--------|
| `delta` | `delta(<source>[, field])` | Разница с предыдущим | `delta(flowRate)` |
| `rate` | `rate(<source>[, field])` | Скорость в секунду | `rate(counter)` |
| `counterRate` | `counterRate(<source>[, wrapBits?][, field])` | Счётчик SNMP с wrap | `counterRate(ifInOctets)` |
| `counterDelta` | `counterDelta(<source>[, wrapBits?][, field])` | Дельта счётчика | `counterDelta(ifInOctets)` |
| `movingAvg` | `movingAvg(<source>, windowSec[, field])` | Скользящее среднее — **секунды** | `movingAvg(temperature, 60)` |
| `movingMin` | `movingMin(<source>, windowSec[, field])` | Скользящий минимум | `movingMin(pressure, 30)` |
| `movingMax` | `movingMax(<source>, windowSec[, field])` | Скользящий максимум | `movingMax(pressure, 30)` |
| `deadband` | `deadband(<source>, band[, field])` | Подавление мелких изменений | `deadband(level, 1.0)` |
| `hysteresis` | `hysteresis(<source>, on, off[, field])` | Гистерезис (bool) | `hysteresis(level, 80, 70)` |

### Cross-object / действия

| Функция | Синтаксис | Описание | Пример |
|---------|-----------|----------|--------|
| `read` | `read(<ref>)` | Живое чтение переменной | `read(root.platform.devices.a/temperature)` |
| `write` | `write(<ref>, <value>)` | Запись поля | `write(@/setpoint, 42)` |
| `call` | `call(<fnRef>[, <inputRef>])` | Вызов функции дерева | `call(@/fn/ack, @/payload)` |
| `fire` | `fire(<evtRef>)` | Публикация события | `fire(@/evt/alarmRaised)` |
| `queryScalar` | `queryScalar(<spec>, "<agg>"[, "<field>"])` | Object Query скаляр | `queryScalar(@/downIfSpec, "count")` |
| `queryRows` | `queryRows(<spec>)` | Object Query строки (JSON) | `queryRows(@/downIfSpec)` |
| `executeQuery` | `executeQuery(<spec>)` | Алиас `queryRows` | |
| `countScan` | `countScan("<pattern>"[, "<filter>"])` | Число объектов по шаблону | `countScan("root.platform.devices.*")` |
| `sumScan` | `sumScan("<pattern>", "<ref>"[, "<filter>"])` | Сумма поля по скану | |
| `sumRecordField` | `sumRecordField(<tableVar>, "<field>")` | Сумма колонки таблицы | `sumRecordField(orders, "amount")` |

---

## 6. Historian helpers

В правилах `kind: historian`. Окна — **duration-токены**, не секунды.

### Standalone-оценщики

| Функция | Синтаксис | Описание | Пример |
|---------|-----------|----------|--------|
| `avg` | `avg(<ref>, <bucket?>)` | Среднее по окну | `avg(@/temperature, 5m)` |
| `min` / `max` | `min|max(<ref>, <bucket?>)` | Экстремумы | `min(@/pressure, 30m)` |
| `last` | `last(<ref>)` | Последний сэмпл (+ live fallback) | `last(@/temperature)` |
| `rateOfChange` | `rateOfChange(<ref>, <bucket?>)` | Δ средних бакетов | `rateOfChange(@/level, 1h)` |
| `totalizer` | `totalizer(<ref>, <bucket?>)` | Накопление | `totalizer(@/energy, 1h)` |
| `oee` | `oee('<path>', '<A>', '<P>', '<Q>', '<bucket?>')` | OEE % | см. EN / cookbook |

Устаревшее имя **`rollingAvg`** → **`avg`**. Префикс `hist.` в runtime **не используется**.

### CEL-композиции

Внутри CEL на historian-правиле: `avg` / `min` / `max` / `last` / `sum` / `live` раскрываются в числа, затем считается CEL.

```cel
(avg(root.platform.devices.a/temperature, 5m) + avg(root.platform.devices.b/temperature, 5m)) / 2.0
```

`sum` — **только** в CEL-композиции. `live` лучше внутри CEL (не как единственный helper).

### Бакеты длительности

Допустимые: **`1m`**, **`5m`**, **`15m`**, **`30m`**, **`1h`**, **`6h`**, **`8h`**, **`1d`** (≥1m и ≤7d).

| Контекст | Единица окна |
|----------|--------------|
| `movingAvg(x, 60)` | **Секунды** |
| `avg(x, 5m)` | **Duration bucket** |

---

## 7. Где пишут выражения

| Место | Пример | Режим |
|-------|--------|-------|
| Binding `expression` | `movingAvg(@/temperature, 60)` | Platform или CEL |
| Binding `condition` | `self.temperature.value > 80.0` | CEL |
| Alert condition | `self.temperature.value > self.threshold.value` | CEL |
| Workflow gateway | CEL | CEL |
| Historian rule | `avg(@/temperature, 5m)` | Historian / CEL |

---

## 8. Примеры

**Алерт по порогу**

```cel
self.temperature.value > self.threshold.value
```

**Масштаб 0–100 → 0–1**

```text
scale(level, 0, 100, 0, 1)
```

**Гистерезис**

```text
hysteresis(temperature, 80, 70)
```

**Зеркало удалённого значения**

```text
read(root.platform.devices.cluster.dev-03/sineWave)
```

**Historian среднее**

```text
avg(root.platform.devices.sensor-a/temperature, 5m)
```

**Вызов функции**

```text
call(@/fn/dispatch, @/lastIngress)
```

---

## 9. Частые ошибки

1. Platform binding = **целое** выражение; `counterRate(...) + 1.0` так не работает.
2. `read(...)` — не CEL-функция; для локального CEL используйте `self.var.field`.
3. Пишите **`2.0`**, не `2`, рядом с live/historian.
4. `parent` пустой — не опирайтесь на него.
5. `movingAvg(..., 60)` ≠ `avg(..., 5m)` (секунды vs бакет).
6. `sum` / часто `live` — в CEL-композиции; для «последнего» удобен `last`.
7. Без префикса `hist.` — `avg(...)`.
8. Проверяйте **Validate** в UI или `POST /api/v1/expressions/validate`.

---

## 10. Каталог в продукте

| Место | Что даёт |
|-------|----------|
| Computations → редактор выражений | Сниппеты + function catalog |
| `GET /api/v1/platform/analytics/catalog` | Метаданные Tier A/B/C |
| System → Analytics formulas | Пользовательские формулы Tier B |

Если каталог API и эта страница расходятся — для установленных packs доверяйте **API**; правьте справочник в том же PR, что и registry.
