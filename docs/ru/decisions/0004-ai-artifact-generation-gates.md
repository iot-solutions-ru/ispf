> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0004-ai-artifact-generation-gates.md](../../en/decisions/0004-ai-artifact-generation-gates.md).

# ADR-0004: Генерация и валидация AI-артефактов

## Статус

Принято (2026-06-22)

## Контекст

REQ-FW-40…43 вводят AI Development Layer для разработчиков solution. Без ограничений вывод LLM может обойти 0001 (никакого solution Java в `ispf-server`) или задеплоить невалидный/частичный bundle.

## Решение

1. **Провайдеры LLM живут вне ядра `ispf-server`** через SPI `LlmProvider` (`packages/ispf-ai-api` + adapter modules), настраиваются Spring profile/env.
2. **AI генерирует только declarative-артефакты** — bundle JSON (migrations, functions, dashboards, `operatorUi`, `events` и т.д.). Никакого React/Java в platform `main`.
3. **Обязательные gate перед публикацией:**
   - `validate_bundle` — семантические проверки (scripts, SQL guards, events, layout JSON)
   - `dry_run_deploy` — та же валидация + план `wouldApply` без мутации БД
4. **ContextPack — dev-time, не секция bundle** — собирается из docs/examples (`tools/ai-pack/build.py`), не деплоится вместе с apps.
5. **Platform Studio** — тонкий admin UI поверх существующих tools и `POST /api/v1/platform/packages/import`.
6. **Audit** — `ai_tool_audit` записывает tool name, actor, request hash, status; без API keys и raw secrets.

## Последствия

- Validate/dry-run работают с `provider: noop` (внешний LLM для CI не нужен).
- Generate endpoint требует настроенный provider; ошибки возвращают понятные 503/400.
- Таблица semver manifest в public API doc без изменений; AI использует optional `metadata` для provenance.
- Подписание commercial license выполняется после финального AI-edited manifest.

## Рассмотренные альтернативы

| Альтернатива | Отклонена, потому что |
|-------------|------------------|
| Встроить OpenAI SDK в `ispf-server` | Нарушает границу как у driver; раздувает core |
| Auto-deploy без валидации | Риск partial deploy и невалидных scripts в PRODUCTION |
| Новая секция bundle `aiHints[]` | Лишний churn контракта; ContextPack достаточен |
