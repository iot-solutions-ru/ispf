# Привязки переменных (bindings)

**Binding** — вычисляемое значение переменной объекта. Задаётся полем `bindingExpression` в модели или на экземпляре объекта. При каждом изменении любой переменной на том же объекте `BindingEvaluator` пересчитывает все binding-переменные, сохраняет результат и рассылает `VARIABLE_UPDATED` через WebSocket.

Bindings — **read-only derived state** by default: они не вызывают события и не пишут в другие объекты напрямую. Исключение — platform bindings **`callFunction`** / **`callFunctionAt`**, которые могут вызывать функции объекта (с guard от рекурсии). Чтение других объектов — через **`refAt`**. Side effects вне bindings — через alert rules, correlators, workflows и REST API.

См. также: [OBJECT_MODEL.md](OBJECT_MODEL.md), [MODELS.md](MODELS.md), [VARIABLE_HISTORY.md](VARIABLE_HISTORY.md).

---

## Два вида выражений

| Вид | Синтаксис | Когда использовать |
|-----|-----------|-------------------|
| **CEL** | Infix-выражение Google CEL | Логика, сравнения, арифметика над текущими полями `self.*` |
| **Platform binding** | Один вызов функции: `funcName(...)` | Трансформы телеметрии, stateful-расчёты (rate, delta) |

Строка `bindingExpression` — **либо** CEL, **либо** один platform binding (целиком). Смешивать CEL и platform function в одной строке пока нельзя.

Проверка синтаксиса (Web Console → «Проверить», REST):

```http
POST /api/v1/expressions/validate
Content-Type: application/json

{ "expression": "scale(temperature, -20, 50, 0, 100)" }
```

---

## Общие правила platform bindings

### Имена

- **sourceVariable** — имя переменной **того же объекта**, без префикса `self.`
- **field** — имя поля в `DataRecord` source-переменной; по умолчанию `value`
- Идентификаторы: `[A-Za-z_][A-Za-z0-9_]*`
- Пробелы вокруг скобок и запятых допустимы: `counterRate( ifInOctets )`

### Источник данных

Platform binding читает **текущее значение** source-переменной (`firstRow()`). Если переменная отсутствует, пуста или поле не число (где требуется число) — binding **не обновляется** (остаётся предыдущее или default значение).

### Запись результата

Результат маппится в схему **целевой** binding-переменной:

| Схема цели | Поведение |
|------------|-----------|
| Одно поле (`value`) | Скalar записывается в это поле |
| `snmpNumeric` (`value`, `raw`, `type`) | Число → `value`; `raw` = строковое представление числа; остальные поля — defaults |
| STRING | Строка → поле `value` (для `format`) |
| Map (CEL) | Поля маппятся по именам схемы |

### Пересчёт и зависимости

- Триггер: любое `setVariableValue` / `setComputedValue` на объекте → `propagateBindings()`
- Один проход по всем binding-переменным; **порядок зависимостей не гарантирован** (A→B→C может дать stale value за один цикл)
- Ошибки (`ExpressionException`) **не логируются** — binding остаётся на предыдущем значении

### Stateful bindings

`counterRate`, `rate`, `delta`, `counterDelta`, `movingAvg`, `movingMin`, `movingMax`, `deadband`, `hysteresis` хранят состояние в памяти JVM keyed по `objectPath|targetVariable` (`BindingStateStore`). Состояние **не переживает рестарт** и **не реплицируется** между узлами кластера.

### Cross-object и function bindings

- **`refAt("objectPath", variableName[, field])`** — читает поле переменной другого объекта через `BindingEvaluationContext.readRemoteField()`
- **`callFunction(functionName)`** / **`callFunction(functionName, sourceVar[, field])`** — вызывает функцию на том же объекте
- **`callFunctionAt("objectPath", functionName)`** / **`callFunctionAt("objectPath", functionName, sourceVar[, field])`** — вызывает функцию на другом объекте; input берётся из source-переменной **binding-объекта**

Путь объекта в кавычках: `"root.platform.devices.foo"`. На сервере используется `ServerBindingEvaluationContext` с guard от рекурсивного вызова функций из binding.

---

## CEL-привязки

Движок: [Google CEL](https://github.com/google/cel-spec) (`packages/ispf-expression`).

### Контекст

| Переменная | Содержимое |
|------------|------------|
| `self` | Map имя_переменной → map полей (`firstRow()` каждой переменной с непустым значением) |
| `parent` | Пустой map (зарезервировано для иерархических bindings) |

Доступ к полям: dot-notation — `self.temperature.value`, `self.threshold.value`.

### Типичные операции

- Арифметика: `+`, `-`, `*`, `/`, `%`
- Сравнения: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Логика: `&&`, `\|\|`, `!`
- Условие: `условие ? a : b`
- Стандартная библиотека CEL для строк, списков и т.д.

### Пример: alarm по порогу

Модель `mqtt-sensor-v1`, переменная `alarmActive`:

```
self.temperature.value > self.threshold.value
```

При превышении температуры порога `alarmActive.value` становится `true`.

### Пример: производная в CEL

```
self.temperature.value * 1.8 + 32
```

(для явного Fahrenheit лучше отдельная переменная со схемой `temperature` и unit в поле `unit`.)

### Ограничения CEL

- Только переменные **текущего** объекта (`self.*`)
- Нет доступа к истории (`variable_samples`)
- Нет stateful-функций (rate, moving average) — для этого platform bindings
- Нет side effects

---

## Platform bindings — справочник функций

Реестр: `PlatformBindingRegistry` в `packages/ispf-expression`.

---

### `selectField`

**Назначение:** вернуть одно поле source-переменной как значение binding-переменной.

**Сигнатура:**

```
selectField(sourceVariable[, field])
```

| Параметр | Обязательный | Описание |
|----------|--------------|----------|
| `sourceVariable` | да | Имя переменной на том же объекте |
| `field` | нет | Имя поля; default: `value` |

**Тип:** stateless.

**Результат:** значение поля как есть (Number, Boolean, String — по типу поля source).

**Примеры:**

```
selectField(temperature)
selectField(ifInOctets, value)
selectField(sysDescr, raw)
```

**Заметка:** для доступа к полю в логике достаточно CEL (`self.temperature.value`). `selectField` удобен, когда binding-переменная должна **повторять** одно поле другой переменной без CEL.

---

### `scale`

**Назначение:** линейное отображение числового поля из диапазона `[inMin, inMax]` в `[outMin, outMax]`.

**Сигнатура:**

```
scale(sourceVariable, inMin, inMax, outMin, outMax[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `inMin`, `inMax` | Входной диапазон (числа, допускается `-` и `.`) |
| `outMin`, `outMax` | Выходной диапазон |
| `field` | Поле source; default: `value` |

**Формула:**

```
outMin + (value - inMin) * (outMax - outMin) / (inMax - inMin)
```

**Тип:** stateless.

**Особенности:**

- Если `inMin == inMax` — результат не вычисляется (binding не обновляется)
- Значения **вне** `[inMin, inMax]` не обрезаются — для ограничения используйте `clamp`

**Пример из `mqtt-sensor-v1`:**

Переменная `temperaturePercent` (0…100 %):

```
scale(temperature, -20, 50, 0, 100)
```

При `temperature.value = 15` → `40` (линейная шкала от −20…50 °C к 0…100 %).

---

### `clamp`

**Назначение:** ограничить числовое поле диапазоном `[min, max]`.

**Сигнатура:**

```
clamp(sourceVariable, min, max[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `min`, `max` | Границы (если `min > max`, поведение как у `Math.max(min, Math.min(max, value))`) |
| `field` | Поле source; default: `value` |

**Тип:** stateless.

**Примеры:**

```
clamp(hrProcessorLoad, 0, 100)
clamp(rawAdc, 0, 4095, value)
```

---

### `format`

**Назначение:** сформировать строку по шаблону `String.format` (Locale.US) из значения поля source.

**Сигнатура:**

```
format("pattern", sourceVariable[, field])
```

| Параметр | Описание |
|----------|----------|
| `pattern` | Шаблон в двойных кавычках; синтаксис `String.format` |
| `sourceVariable` | Source-переменная |
| `field` | Поле source; default: `value` |

**Тип:** stateless.

**Схема цели:** STRING (поле `value`).

**Примеры:**

```
format("%.1f °C", temperature, value)
format("CPU: %.0f%%", hrProcessorLoad, value)
format("%s", sysDescr, raw)
```

**Ограничение:** pattern — только в двойных кавычках `"..."`; escape `\"` внутри pattern не поддерживается парсером.

---

### `delta`

**Назначение:** разность между **текущим** и **предыдущим** числовым sample source-поля. Без деления на время, без логики переполнения счётчика.

**Сигнатура:**

```
delta(sourceVariable[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `field` | Поле source; default: `value` |

**Тип:** stateful (хранит предыдущее числовое значение).

**Поведение:**

1. Первый успешный sample → binding **не обновляется** (нет предыдущего значения)
2. Следующие обновления → `current - previous`
3. Подходит для **Gauge**-метрик (температура, load), не для Counter32 с wrap

**Примеры:**

```
delta(temperature, value)
delta(ifInOctets, value)
```

**Отличие от `counterRate`:** `delta` не делит на Δt и не обрабатывает wrap 2³².

---

### `counterRate`

**Назначение:** скорость изменения SNMP-style **Counter32** (или аналога) в **единицах поля в секунду** (для `ifInOctets` / `ifOutOctets` — **байт/с, B/s**).

**Сигнатура:**

```
counterRate(sourceVariable[, maxCounter[, field]])
```

| Параметр | Default | Описание |
|----------|---------|----------|
| `sourceVariable` | — | Source-переменная (обычно `ifInOctets`, `ifOutOctets`) |
| `maxCounter` | `4294967296` (2³²) | Верхняя граница счётчика для детекции wrap |
| `field` | `value` | Числовое поле source |

**Тип:** stateful.

**Алгоритм:**

1. Берётся `updatedAt` source-переменной и числовое значение поля
2. Первый sample → binding не обновляется
3. Если Δt < **500 ms** → пропуск (защита от шума poll)
4. Δcounter с учётом wrap: если `current < previous` и `previous > 0.75 * maxCounter` → `maxCounter - previous + current`
5. Если `current < previous` без признаков wrap (сброс счётчика) → пропуск
6. Результат: `delta / (Δt в секундах)`

**Примеры из `snmp-agent-v1`:**

Переменные `ifInOctetsRate` / `ifOutOctetsRate` (с `historyEnabled`):

```
counterRate(ifInOctets)
counterRate(ifOutOctets)
```

С явным max и полем:

```
counterRate(ifInOctets, 4294967296, value)
```

**Схема цели:** обычно `snmpNumeric` или одно поле `value` с unit `B/s` в модели.

См. [DASHBOARDS.md](DASHBOARDS.md) — виджеты net ↓ / net ↑ на dashboard SNMP.

---

### `rate`

**Назначение:** скорость изменения **Gauge**-метрики (без логики переполнения счётчика) в единицах поля в секунду.

**Сигнатура:**

```
rate(sourceVariable[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `field` | Поле source; default: `value` |

**Тип:** stateful.

**Алгоритм:** как `counterRate`, но без wrap/reset логики; Δt берётся из `updatedAt` source-переменной; минимальный Δt **500 ms**.

**Примеры:**

```
rate(temperature)
rate(powerDraw, value)
```

**Отличие от `counterRate`:** для монotonic counter с wrap используйте `counterRate`; для gauge — `rate`.

---

### `counterDelta`

**Назначение:** приращение SNMP-style Counter32 с учётом wrap, **без** деления на время.

**Сигнатура:**

```
counterDelta(sourceVariable[, maxCounter[, field]])
```

| Параметр | Default | Описание |
|----------|---------|----------|
| `sourceVariable` | — | Source-переменная |
| `maxCounter` | `4294967296` | Верхняя граница счётчика для wrap |
| `field` | `value` | Числовое поле source |

**Тип:** stateful.

**Примеры:**

```
counterDelta(ifInOctets)
counterDelta(ifOutOctets, 4294967296, value)
```

---

### `movingAvg`

**Назначение:** скользящее среднее за окно времени.

**Сигнатура:**

```
movingAvg(sourceVariable, windowSec[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `windowSec` | Длина окна в секундах |
| `field` | Поле source; default: `value` |

**Тип:** stateful (samples с timestamp в `BindingStateStore`).

**Пример:**

```
movingAvg(temperature, 300)
```

---

### `movingMin` / `movingMax`

**Назначение:** минимум / максимум за скользящее окно.

**Сигнатура:**

```
movingMin(sourceVariable, windowSec[, field])
movingMax(sourceVariable, windowSec[, field])
```

**Тип:** stateful.

**Примеры:**

```
movingMin(temperature, 60)
movingMax(hrProcessorLoad, 300, value)
```

---

### `deadband`

**Назначение:** подавление мелких колебаний — binding обновляется только когда `|current − lastEmitted| ≥ band`.

**Сигнатура:**

```
deadband(sourceVariable, band[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `band` | Минимальное изменение для эмиссии |
| `field` | Поле source; default: `value` |

**Тип:** stateful (хранит last emitted value).

**Пример:**

```
deadband(temperature, 0.5)
```

---

### `hysteresis`

**Назначение:** boolean-выход с раздельными порогами включения/выключения (anti-chatter).

**Сигнатура:**

```
hysteresis(sourceVariable, onThreshold, offThreshold[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `onThreshold` | Значение ≥ → `true` |
| `offThreshold` | Значение ≤ → `false` |
| `field` | Поле source; default: `value` |

**Тип:** stateful (boolean state). Между порогами сохраняется предыдущее состояние.

**Пример:**

```
hysteresis(temperature, 80, 70)
```

**Схема цели:** BOOLEAN (`value`).

---

### `unitConvert`

**Назначение:** конвертация температуры между единицами C, F, K (регистр не важен).

**Сигнатура:**

```
unitConvert(sourceVariable, fromUnit, toUnit[, field])
```

| Параметр | Описание |
|----------|----------|
| `sourceVariable` | Source-переменная |
| `fromUnit` | `C`, `F` или `K` |
| `toUnit` | `C`, `F` или `K` |
| `field` | Поле source; default: `value` |

**Тип:** stateless.

**Примеры:**

```
unitConvert(temperature, C, F)
unitConvert(rawProbe, f, K, value)
```

---

### `refAt`

**Назначение:** прочитать поле переменной **другого** объекта.

**Сигнатура:**

```
refAt("objectPath", variableName[, field])
```

| Параметр | Описание |
|----------|----------|
| `objectPath` | Полный путь объекта в двойных кавычках |
| `variableName` | Имя переменной на remote-объекте |
| `field` | Поле; default: `value` |

**Тип:** stateless (требует server context).

**Пример:**

```
refAt("root.platform.devices.sensor-1", temperature)
refAt("root.platform.devices.snmp-localhost", ifInOctetsRate, value)
```

---

### `callFunction`

**Назначение:** вызвать функцию **того же** объекта из binding.

**Сигнатура:**

```
callFunction(functionName)
callFunction(functionName, sourceVariable[, field])
```

| Параметр | Описание |
|----------|----------|
| `functionName` | Имя функции на binding-объекте |
| `sourceVariable` | Опционально: input = текущий `DataRecord` source-переменной |
| `field` | Зарезервировано (input — полная запись source) |

**Тип:** stateless (side effect: вызов функции). Результат маппится через `BindingResultHelper.mapFunctionOutput`.

**Пример:**

```
callFunction(computeStatus)
callFunction(enrichReading, rawSensor)
```

---

### `callFunctionAt`

**Назначение:** вызвать функцию **другого** объекта; input из source-переменной binding-объекта.

**Сигнатура:**

```
callFunctionAt("objectPath", functionName)
callFunctionAt("objectPath", functionName, sourceVariable[, field])
```

| Параметр | Описание |
|----------|----------|
| `objectPath` | Путь remote-объекта в кавычках |
| `functionName` | Имя функции на remote-объекте |
| `sourceVariable` | Source на **binding-объекте** для input |

**Тип:** stateless (side effect через server context).

**Пример:**

```
callFunctionAt("root.platform.services.calculator", normalize, rawInput)
```

---

## Сводная таблица

| Функция | Stateful | Вход | Выход | Типичное применение |
|---------|----------|------|-------|---------------------|
| `selectField` | нет | любое поле | то же значение | Прокси поля, snmp `raw` |
| `scale` | нет | число | число | ADC → инженерные единицы, % шкала |
| `clamp` | нет | число | число | Ограничение 0…100, sanity bounds |
| `format` | нет | любое поле | STRING | Подпись для HMI / текстовый индикатор |
| `delta` | да | число | число | Δ между poll (Gauge) |
| `counterRate` | да | Counter32 | DOUBLE (B/s) | SNMP throughput, IF-MIB octets |
| `rate` | да | число (Gauge) | DOUBLE (/s) | Скорость gauge-метрики |
| `counterDelta` | да | Counter32 | число | Приращение counter с wrap |
| `movingAvg` | да | число | число | Сглаживание, тренд |
| `movingMin` / `movingMax` | да | число | число | Min/max за окно |
| `deadband` | да | число | число | Фильтр шума |
| `hysteresis` | да | число | BOOLEAN | Alarm с anti-chatter |
| `unitConvert` | нет | число | число | C ↔ F ↔ K |
| `refAt` | нет | remote field | любое | Cross-object read |
| `callFunction` | нет | function | по output schema | Derived via function |
| `callFunctionAt` | нет | function @ remote | по output schema | Cross-object function |

---

## Примеры в built-in моделях

| Модель | Переменная | Binding |
|--------|------------|---------|
| `mqtt-sensor-v1` | `alarmActive` | `self.temperature.value > self.threshold.value` (CEL) |
| `mqtt-sensor-v1` | `temperaturePercent` | `scale(temperature, -20, 50, 0, 100)` |
| `snmp-agent-v1` | `ifInOctetsRate` | `counterRate(ifInOctets)` |
| `snmp-agent-v1` | `ifOutOctetsRate` | `counterRate(ifOutOctets)` |

---

## История и графики

Binding-переменные могут иметь `historyEnabled = true` — тогда каждое изменение пишется в `variable_samples` и доступно для trend-виджетов:

```http
GET /api/v1/objects/by-path/variables/history?path=...&name=ifInOctetsRate&field=value&limit=500
```

Рекомендуется включать history для `counterRate` / `delta`, если нужны графики скорости или приращений.

---

## Расширение платформы

Новая platform function:

1. Класс, реализующий `PlatformBinding` (`matches`, `evaluate`)
2. Регистрация в `PlatformBindingRegistry`
3. Unit-тесты в `packages/ispf-expression/src/test/java/`
4. Запись в этот документ

---

## Известные ограничения

| Ограничение | Обход |
|-------------|-------|
| Нет порядка зависимостей между bindings | Не строить длинные цепочки A→B→C на одном объекте |
| `parent` пуст в CEL | Только `self.*` |
| Stateful state in-memory | После рестарта первый sample stateful binding снова «холодный» |
| `callFunction` / `callFunctionAt` | Рекурсия из binding блокируется; ошибки функции → binding не обновляется |
| Cross-object bindings | Требуют server runtime (`ServerBindingEvaluationContext`) |
| `format` — только `"..."` pattern | Использовать простые шаблоны |
| Silent errors | Проверять binding через `/expressions/validate` и смотреть, обновляется ли переменная в UI |
