> **Язык:** русская версия. Канонический английский: [en/decisions/0060-solution-authoring-constraints.md](../../en/decisions/0060-solution-authoring-constraints.md).

# ADR-0060: Constraints авторства solution (AI-first)

## Статус

**Accepted** (2026-09-21) — расширяет [0051](0051-poka-yoke-constraints-over-guards.md).

## Контекст

Объекты с логикой должны быть SINGLETON или INSTANCE, никогда `ObjectType.DEVICE`. Правило жило только в промптах; эталонные бандлы размещали BFF-функции на хабах DEVICE.

## Решение

| Волна | Поставка |
|------|----------|
| **W1** | Каноническое предупреждение `LOGIC_HOST_DEVICE`; `bundle.schema.json`; хабы SINGLETON в examples |
| **W2** | Словарь и ошибки инструментов `{code,path,hint,docRef}` |
| **W3** | Параметризованный SHAPE (`sqlBindings` / `alertRules` с `${self.*}`) |
| **W4** | CLI solution-as-repo (`ispf pack/validate/diff/deploy`) |
| **W5** | Нативный function calling LLM |
| **W6** | `tests[]` + `test_function` / judge по реальным результатам |

### Правила W1

1. `functions[].objectPath` → DEVICE = WARNING `LOGIC_HOST_DEVICE`
2. SINGLETON blueprint с `targetObjectType=DEVICE` = WARNING `LOGIC_HOST_DEVICE`
3. Неизвестный хост = WARNING `LOGIC_HOST_UNKNOWN`
4. `bindings[]` на DEVICE по-прежнему разрешены
5. Канонический хаб: SINGLETON blueprint → `root.platform.singleton-blueprints.{name}`
6. Схема в `schema/bundle.schema.json`; MCP `contextpack://bundle-schema`
7. Бандлы `examples/` не содержат `LOGIC_HOST_DEVICE` (`ExamplesBundleValidationTest`) — это эталонный материал для агента

> **Поправка (2026-10-06):** на **авторском** validate/deploy замечания канона сообщаются и не блокируют применение. Интерактивный `validate_bundle` и HTTP validate/deploy без `strict` оставляют `LOGIC_HOST_DEVICE` как WARNING.
>
> **Поправка (2026-10-13) — ship-default (Partial):** финальная приёмка поставки другая. `deploy_step_validate` / `deploy_step_dry_run` / `import_package` / `deploy_step_import` поднимают канон до **ERROR** и не применяют бандл. Judge возвращает **REWORK** (`logic.host`). Остаётся: голый `POST .../deploy` без `strict` по-прежнему WARNING. Инструменты `deploy_tree_function` / `deploy_app_function` требуют `hostKind` = `SINGLETON` | `INSTANCE` (DEVICE в enum нет). Канон: [EN ADR-0060](../../en/decisions/0060-solution-authoring-constraints.md).

## Связанные

- [application-principles](../application-principles.md) § Logic objects vs DEVICE
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md)
