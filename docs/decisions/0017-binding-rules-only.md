# ADR-0017: Binding rules only (v0.8.0)

Статус: **Accepted**  
Дата: 2026-06-23

## Контекст

Переменная с полем `bindingExpression` не поддерживала cross-object propagation: при изменении telemetry на device hub-переменные с `refAt(...)` не пересчитывались, т.к. `propagateBindings()` работал только внутри одного объекта.

До 1.0 допустим breaking change.

## Решение

1. **Удалить** `Variable.bindingExpression`, `BindingEvaluator` object-loop, `propagateBindings()`, REST/agent param `bindingExpression`.
2. **Единственный механизм** — `BindingRule` + `BindingRuleEngine` + `@bindingRules` JSON на объекте.
3. **Cross-object** — `BindingDependencyIndex` + `BindingPropagationListener` на `VARIABLE_UPDATED` (включая driver telemetry).
4. **Модели** — `ModelBindingRule` вместо `ModelBindingDefinition` / `defaultBinding`.
5. **Миграция** — `BindingExpressionMigrationRunner` (JDBC read `binding_expr` → rules).

## Последствия

- API и UI: вкладка «Привязки», CRUD `/binding-rules`.
- Agent: `create_binding_rule` вместо binding на `create_variable`.
- Документация: [BINDINGS.md](../BINDINGS.md) переписан.
- Колонка `binding_expr` в БД остаётся nullable до post-migration cleanup (Flyway drop — отдельный релиз).

## Связанные материалы

- [BINDINGS.md](../BINDINGS.md)
- [OBJECT_MODEL.md](../OBJECT_MODEL.md)
- FW-48 / virt-cluster playbook
