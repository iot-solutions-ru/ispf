> **Язык:** перевод. Канонический текст: [en/anti-patterns.md](../en/anti-patterns.md).

# Анти-паттерны (полевые грабли → дизайн ISPF)

> **Статус:** Stable — poka-yoke только там, где у ISPF **уже есть** enforcement. Хаб: [doc-status](../en/doc-status.md).

Страница перечисляет полевые ошибки, у которых есть ответ в ISPF (валидатор, движок биндингов, дефолт драйвера или ADR). Это не дамп чужой документации и не список «хотелок».

См. также: [learn](learn.md), [application-principles](application-principles.md), [bindings](../en/bindings.md)#execution, [variable-history](../en/variable-history.md), [ADR-0061](../en/decisions/0061-high-rate-telemetry-history-defaults.md), [solution-developer-guide](../en/solution-developer-guide.md).

---

## 1. Тяжёлая логика в виджетах / клиентских выражениях

| Полевая грабля | ISPF |
|----------------|------|
| Ветвления и многошаговая логика в виджетах дашборда | Виджеты биндят переменные; логика на **SINGLETON / INSTANCE** hub (функции объекта, binding rules) |
| Та же логика размазана так, что HMI нельзя переиспользовать | Hosted SPA вызывает hub через `POST /api/v1/bff/invoke` |

**Enforced:** `BundleManifestValidator` предупреждает `HEAVY_WIDGET_EXPRESSION`, если поле виджета `expression` / `condition` / `script` ≥ **400** символов. Тяжёлые выражения правил — `BindingCascadeAnalyzer` при сохранении.

**Правило:** ветвления / несколько шагов → функция hub через `call(@/fn/…)` (или binding, пишущий display-переменную). См. [expression-language](expression-language.md).

---

## 2. Каскад биндингов / очередь активаций

| Полевая грабля | ISPF |
|----------------|------|
| Несколько правил на одно изменение идут очередью; поздние видят промежуточные записи и блокируют друг друга | На объекте: сортировка по **`order`**; **multi-pass** до стабилизации (`MAX_PASSES=8`); межобъектная глубина **`MAX_DEPTH=16`** |
| Нет предупреждения о циклах write↔activate | `BindingCascadeAnalyzer` при сохранении; обрезки логируются |

**Правило:** один владелец записи на переменную; `activators.async=true` только для независимых side effects; ветвления — в hub-функциях.

Подробности: [bindings.md § Execution](../en/bindings.md#execution).

---

## 3. Безлимитный MQTT / high-rate журнал событий

| Полевая грабля | ISPF |
|----------------|------|
| Высокий поток хранится как полная история событий → UI/БД тают | MQTT: по умолчанию **`eventToVariable=false`** (last-value / coalesce). Новые объекты: **`eventJournalEnabled=false`** |
| Журнал на catch-all топиках без retention | Включать journal только с явным audit + retention (ADR-0061) |

**Правило:** не включать object event journal на flood-путях в проде без retention.

---

## 4. Предыдущее значение для логики изменений

| Полевая грабля | ISPF |
|----------------|------|
| Previous на каждом update (размер payload / нагрузка) | **`Variable.includePreviousValueInEvent`** — opt-in; при включении WS/automation могут получить `previousValue` |

**Правило:** только на переменных, где это нужно правилам или аудиту; не глобально.

---

## 5. Самодельные time buckets как события

| Полевая грабля | ISPF |
|----------------|------|
| Кастомные «гранулы» как события устройства с ad-hoc bucket-выражениями | Historian **materialized rollups**: rules с `kind: historian`, `windowBucket` / **`rollupBuckets`**, плюс aggregate API |

Предпочитайте historian rules, а не поток событий ради бакетов. См. [analytics-historian-cookbook](../en/analytics-historian-cookbook.md).

---

## 6. Ручное копирование дерева вместо ship-артефакта

| Полевая грабля | ISPF |
|----------------|------|
| «Снапшот всего» копированием живого дерева / ad-hoc export | **Solution-as-repo**: `ispf pack` / `validate` / `diff` / `deploy` → `bundle.json` (+ опционально ui-pack) |

Не копируйте деревья между стендами руками. SHIP = bundle ([ADR-0060](../en/decisions/0060-solution-authoring-constraints.md)). Гайд: [solution-developer-guide](../en/solution-developer-guide.md).

---

## 7. Именование при авторинге

| Практика | Зачем |
|----------|-------|
| `lowerCamelCase` для переменных, функций, id правил | Сортировка, удобство в выражениях |
| Глаголы для функций (`getTelemetry`, `ackAlarm`) | Смысл на call site |
| Без префикса типа в имени (`pump01`, не `devicePump01`) | Тип — path / `ObjectType` |
| Descriptions у функций и неочевидных переменных | Операторы + AI |

Хост логики — **SINGLETON / INSTANCE**, никогда `ObjectType.DEVICE` ([application-principles](application-principles.md) § Logic objects vs DEVICE). Bundle: предупреждение `LOGIC_HOST_DEVICE`.

---

## Чеклист агента

Перед сдачей решения:

1. Нет `LOGIC_HOST_DEVICE` — функции hub вместо скриптов в виджетах.
2. Нет неразрешённого `HEAVY_WIDGET_EXPRESSION` — логика на hub.
3. Flood-пути: `eventJournalEnabled=false`; MQTT `eventToVariable=false`, если не нужен осознанный fan-out.
4. Циклы биндингов: предупреждения `BindingCascadeAnalyzer` закрыты; `order` осмысленный.
5. Артефакт: `ispf pack` / `validate_bundle` зелёный.

---

*Добавляйте строку только если грабля уже закрыта валидатором, лимитом движка или accepted ADR — не если у чужого рецепта нет аналога в ISPF.*
