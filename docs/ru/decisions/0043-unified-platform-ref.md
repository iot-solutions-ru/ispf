> **Язык:** русская версия. Канонический английский: [en/decisions/0043-unified-platform-ref.md](../../en/decisions/0043-unified-platform-ref.md).

# ADR-0043: Единый адрес PlatformRef

## Статус

**Принято** (2026-07-12)

## Контекст

В ISPF были **параллельные форматы** ссылок на сущности дерева: голые имена в выражениях, `{ objectPath, variableName }` в activators, отдельные ключи analytics-тегов и REST `path` + `name` + `field`. Это усложняло cross-object bindings, historian-теги и конфиги UI.

ADR [0019](0019-platform-rule-unification.md) унифицировал *эффекты* правил; этот ADR унифицирует **адреса** (variables, functions, events, historian tags).

## Решение

### Модель `PlatformRef`

`object` (путь или `@`), `kind` (variable | function | event | tag), `name`, `field` (по умолчанию `value`).

### Slash-грамматика

| Kind | Форма | Пример |
|------|--------|--------|
| variable | `<object>/<name>[/<field>]` | `@/temperature` |
| function | `<object>/fn/<name>` | `@/fn/calculate` |
| event | `<object>/evt/<name>` | `@/evt/alarmRaised` |
| tag | `<object>/tag/<ruleId>` | `root.../tag/avg-temp-5m` |

### Операции

| Op | Пример |
|----|--------|
| `read(ref)` | `read(root.../temperature)` |
| `call(ref[, inputRef])` | `call(@/fn/f, @/x)` |
| `fire(ref)` | `fire(@/evt/e)` |
| Historian | `avg(ref, 5m)`, `live(ref)` |

### JSON

Каноническое поле **`ref`** (slash-строка). Раздельные поля (`objectPath` + `variableName`) принимаются при **чтении** сохранённых конфигов; при **записи** предпочтителен `ref`:

```json
{ "ref": "root.platform.devices.demo/temperature" }
```

### Документация и примеры

Вся документация, UI, agent prompts и examples используют только PlatformRef. См. [bindings](../bindings.md).

## Последствия

- Одна грамматика для людей, AI, REST и JSON.
- `PlatformRefParser` (`ispf-core`), `PlatformRefExecutor` (`ispf-server`).
- Путь historian-тега: `objectPath/tag/ruleId`.

## Связанные документы

- [bindings](../bindings.md), ADR [0019](0019-platform-rule-unification.md), [0041](0041-multi-tag-historian-computations.md)
