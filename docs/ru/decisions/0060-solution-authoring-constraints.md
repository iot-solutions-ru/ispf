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
> **Поправка (2026-10-13) — ship-default:** финальная приёмка поставки другая. `deploy_step_*` / `import_package`, `ispf validate|deploy --strict`, `POST .../deploy?strict=true` поднимают канон до **ERROR** и не применяют бандл (HTTP 400 + `shipCanon`). Judge — **REWORK** (`logic.host`). Голый `POST .../deploy` без `strict` остаётся WARNING (авторство). Канон: [EN ADR-0060](../../en/decisions/0060-solution-authoring-constraints.md).

## Связанные

- [application-principles](../application-principles.md) § Logic objects vs DEVICE
- [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md)
