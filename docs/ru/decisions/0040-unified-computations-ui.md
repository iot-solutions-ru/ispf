> **Язык:** русская версия (вычитка). Канонический английский: [../../en/decisions/0040-unified-computations-ui.md](../../en/decisions/0040-unified-computations-ui.md).

# ADR-0040: Единая вкладка «Вычисления» (привязки + historian)

## Статус

**Принято** (2026-07-09), дополнено [ADR-0041](0041-multi-tag-historian-computations.md)

## Контекст

Правила привязок и analytics derived tags выражают одну идею: **когда → выражение → целевая переменная**. В UI были дублирующие вкладки «Привязки» и «Analytics-тег».

## Решение

Одна вкладка **«Вычисления»** в инспекторе объекта:

| Секция | Содержимое |
|--------|------------|
| **Правила** | Единый список `@bindingRules` — reactive и `kind: historian` |
| **Статус historian** | Строки каталога для устройства (quality, выражение) |
| **Audit** | Журнал вызовов привязок |

Отладчик выражений — отдельная вкладка.

Historian-правила — **строки в той же таблице**, не отдельный инспектор ([ADR-0041](0041-multi-tag-historian-computations.md)).

## Последствия

- Документация: [bindings.md](../bindings.md), [analytics-tag-catalog.md](../analytics-tag-catalog.md), [analytics-historian-cookbook.md](../analytics-historian-cookbook.md)
- Целевая модель `kind: historian` — реализована в ADR-0041

## См. также

- [ADR-0041](0041-multi-tag-historian-computations.md)
