> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0002-dogfooding-gate.md](../../en/decisions/0002-dogfooding-gate.md).

# ADR-0002: Dogfooding gate для изменений platform

Статус: **Принято**  
Дата: 2026-06-19 (формализовано 2026-06-22)

## Контекст

Потребности app-команд легко приводят к отраслевому коду в platform repo. Нужен обязательный фильтр: расширять механизм object tree, а не добавлять частный Java.

## Решение

Развитие platform идёт через **dogfooding** с gate перед каждым PR в `main`:

```text
Потребность app-команды → REQ-PF (generic) → PR в platform → bundle deploy → smoke
```

### Gate обобщения (все три — «да»)

| # | Вопрос | Если «нет» |
|---|--------|------------|
| 1 | Потребность выражается через **механизм object tree** (или обобщённый REQ-PF), без отраслевых имён в Java? | Оставить в declarative-конфигурации solution |
| 2 | App-команда использует только **deploy REST**, без fork server? | Доработать API |
| 3 | Есть **второй** сценарий на том же API? | Переформулировать абстракцию |

Критерий нового REQ-PF: *можно ли выразить потребность через существующий или обобщённый механизм object tree?* Если да — расширяем механизм; если нет — gate 0002 (отдельное ADR или отказ).

## Последствия

- Platform developer backlog — единый реестр REQ-PF.
- Reference bundles (`warehouse-app`, `lab-training`, `mes-reference`) — доказательство dogfooding, не код в server.

## Связанные материалы

- [roadmap](../roadmap.md)
- [0001-app-platform-boundary](0001-app-platform-boundary.md)
