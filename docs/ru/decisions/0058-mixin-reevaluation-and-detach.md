> **Язык:** русская версия. Канонический английский: [en/decisions/0058-mixin-reevaluation-and-detach.md](../../en/decisions/0058-mixin-reevaluation-and-detach.md).

# ADR-0058: Reevaluation MIXIN и ownership-aware detach

## Статус

Принято (2026-09-09)

## Контекст

ADR-0018: auto-apply MIXIN только при непустом CEL на create/instantiate. Нет unapply, ownership вклада и повторной проверки на старте сервера / появлении объектов.

Нужны опциональные watch-mixin’ы: перепроверять пригодность по триггерам и **снимать вклад**, если CEL стал `false` — без сканирования всех MIXIN на каждое событие дерева.

## Решение

### 1. Opt-in reevaluation

| Поле | Смысл |
|------|--------|
| `enabled` | false/нет — только ADR-0018 |
| `triggers` | v1: `OBJECT_CREATED`, `SERVER_READY` |

Auto по-прежнему требует непустой CEL. Explicit apply без изменений.

### 2. Attach / detach

Watch-mixin на каждом проходе: CEL true → attach если ещё не применён; CEL false → detach если применён.

### 3. Ownership (1B)

Манифест `blueprintContributions` на объекте. Detach удаляет имя только если mixin всё ещё владелец. Legacy без манифеста — fallback по объявлению модели. Historian purge — вне v1.

### 4. Runtime

Индекс watch-mixin’ов; сервис reevaluate; listener на `CREATED`; pass на `SERVER_READY`; API detach/reevaluate.

### 5. Вне scope (v2)

`VARIABLE_UPDATED`, `OBJECT_EVENT`; soft-detach; auto-detach без reevaluation.

## Связанное

- [0018](0018-fixture-models-and-cel-applicability.md), [0011](0011-model-type-semantics.md), [blueprints](../blueprints.md)
