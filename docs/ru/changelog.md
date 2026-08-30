> **Language:** Russian summary. Canonical changelog: [../../CHANGELOG.md](../../CHANGELOG.md) (Keep a Changelog, English).

# Журнал изменений (ISPF)

Краткое русское зеркало. Полные формулировки и ссылки — в корневом `CHANGELOG.md`.

**Область:** ядро платформы, server, web-console, драйверы, AI agent.  
Changelog отдельных application bundles — в манифестах пакетов.

## [Unreleased]

## [0.9.191] - 2026-08-30

### Исправлено

- **BL-154: остаток Object Query/function/binding** — interactive invoke/evaluate
  в `MEMBER`; OQ omit denied live/historian; ref write / script bridge ACL;
  backup admin-only regression.
- CI nightly больше не стартует на каждый push в `main` (только schedule + dispatch).

### Изменено

- Версия платформы **0.9.191**.

## [0.9.190] - 2026-08-30

### Исправлено

- **BL-154 trusted-channel close** — `MEMBER` ACL на analytics, agent, WebSocket,
  editor, expression, Haystack/Brick.
- **Federation tunnel on-behalf-of** — делегированный principal; anonymous
  value/history запрещены.
- **HTTP peer on-behalf-of (G-05)** — заголовки `X-ISPF-On-Behalf-Of-*`,
  пересечение ролей канала ∩ claimed (без escalation).
- **Hub federated proxy fail-closed** — omit remote-only vars без local ACL;
  proxy write без local var → 403 (кроме admin).

### Изменено

- Версия платформы **0.9.190**; обновлены security/federation/compliance docs.

## [0.9.189] - 2026-08-30

### Добавлено

- **Формальная верификация CEL** (продуктовый gate, ADR-0055): unsatisfiable / tautology,
  equivalence, runtime-настройки, REST `/expressions/verify` (+ equivalence),
  AI-tool `verify_cel_condition`, enforce на apply алертов/bindings.
- **Design-time gate для BPMN workflow** — условия sequence flow на `saveBpmn` / activate.
- **Historian formal rewrite** — `avg`/`live`/… → коррелированные `self.__histN` для SMT.
- **97 protocol stub-драйверов** отдельными packs (Apache-2.0, STUB) — каталог **162** packs.
- Корневой Keep a Changelog + это русское зеркало.

### Изменено

- CEL **0.14.0**; protobuf-java **4.36.0**.
- Dependabot: Spring Boot 4.1.1, Gradle 9.7.1, web-console npm и др.
- **`@vitejs/plugin-react` → 6.1.0** (+ `.npmrc` `legacy-peer-deps=true`).
- Context pack парсит полный каталог драйверов из docs.
- Версия платформы **0.9.189**.

### Исправлено

- V89 `random_uuid` на H2 (placeholder block comment).
- Парсер catalog в `tools/ai-pack/build.py` после переписывания `drivers.md`.

## [0.9.188] - 2026-08-25

### Добавлено

- Signed bundles / VPS evidence; MES GA smoke; Flyway V89 для PostgreSQL.

### Исправлено

- Проверка `contentSha256` лицензии бандла по raw JSON.

### Изменено

- Версия платформы **0.9.188**.
