> **Язык:** русская версия. Канонический английский: [en/decisions/0055-cel-formal-verification-ai-gate.md](../../en/decisions/0055-cel-formal-verification-ai-gate.md).

# ADR-0055: Формальная верификация CEL как продуктовый gate AI и условий

## Статус

Accepted (2026-08-30) — **продукт** (не экспериментальный MVP)

## Контекст

ISPF — AI-first (ADR-0004/0005/0051). Инструменты LLM и люди одинаково пишут булев CEL для alerts, bindings и platform context rules. Проверка только компиляцией пропускает логические сбои (невыполнимые условия, тавтологии) и не доказывает, что рефакторинг безопасен.

В CEL-Java 0.14 есть `dev.cel:verifier` (Z3 через `z3-turnkey`).

## Решение

### Поверхность продукта

1. **Библиотека** (`ispf-expression`): `ExpressionFormalVerifier` + `FormalVerificationReport` со стабильными `codes` (`UNSATISFIABLE`, `TAUTOLOGY`, `EQUIVALENT`, …) для i18n UI и для AI.
2. **Сервис в runtime** (`ExpressionFormalVerificationService`) и настройки `ispf.expression.formal-verification.*` (hot-reload через platform runtime settings):
   - `enabled` (по умолчанию true)
   - `timeout-seconds` (по умолчанию 2)
   - `enforce-on-apply` / `enforce-on-validate` (по умолчанию true)
   - `reject-unsatisfiable` / `reject-tautology` (по умолчанию true)
3. **API**
   - `POST /api/v1/expressions/validate` — компиляция и formal-проверка; возвращает `warnings` и `verification`
   - `POST /api/v1/expressions/verify` — только formal-проверка
   - `POST /api/v1/expressions/verify-equivalence` — доказательство `left ≡ right`
   - Validate в analytics и catalog включает `verification` (historian helpers переписываются в коррелированные плейсхолдеры `self.__histN` для SMT)
4. **Enforce при apply (одна планка для человеческого REST и для AI)**
   - `AlertRuleService` (`conditionExpr`, `deactivateExpr`)
   - `BindingRulesController` (поле `condition` правила)
   - AI: `configure_alert`, `create_binding_rule`, `configure_platform_context_rule`
5. **Инструмент AI** `verify_cel_condition` (только чтение; allowlist оператора) — анализ или проверка эквивалентности.
6. **Web console** показывает результаты formal-проверки при валидации CEL (alerts / bindings).

### Non-goals (отслеживаемые follow-up)

- Пакеты CEL Policy (YAML диагностической политики multi-hit) — ещё не в продукте

### Follow-up, поставленные после первоначального accept

- Formal gate BPMN workflow на design-time: `saveBpmn` / activate (`ACTIVE`)
- Formal-переписывание historian helper (`avg`/`live`/… → коррелированный `self.__histN`) для булевых шаблонов

## Последствия

- Невыполнимые условия и тавтологии нельзя сохранить и нельзя применить через AI, пока флаги enforce включены.
- Проверка эквивалентности открывает безопасный рефакторинг через AI (`verify_cel_condition` + `equivalentTo`).
- Нативные библиотеки Z3 приходят через `tools.aqua:z3-turnkey`; мягкий timeout не даёт validate зависнуть в UI.
- Оператор может ослабить или выключить gate через runtime settings, без пересборки.

## Рассмотренные альтернативы

| Альтернатива | Отклонена, потому что |
|-------------|------------------|
| Только мягкие warnings | AI и продукт всё равно выпускают сломанные alerts |
| Gate только для AI | Человеческий REST обходил бы проверку; продукт был бы непоследовательным |
| Верифицировать развёрнутый historian CEL | Доказывает текущие выборки, а не шаблон |
| Ждать типизированные схемы `self.*` | DYN уже ловит противоречия, полезные в production |
