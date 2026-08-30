> **Language:** Russian summary. Canonical changelog: [../../CHANGELOG.md](../../CHANGELOG.md) (Keep a Changelog, English).

# Журнал изменений (ISPF)

Краткое русское зеркало. Полные формулировки и ссылки — в корневом `CHANGELOG.md`.

**Область:** ядро платформы, server, web-console, драйверы, AI agent.  
Changelog отдельных application bundles — в манифестах пакетов.

## [Unreleased]

## [0.9.199] - 2026-08-30

### Исправлено

- Alert soft-fail + idle gate до `ObjectManager.isInitialized()`.
- Periodic binding `fireDue` удаляет stale schedule при ObjectNotFound.

### Добавлено

- Prometheus gauge `ispf.object_tree.ready`.

## [0.9.198] - 2026-08-30

### Исправлено

- Analytics tag catalog не сканирует `@bindingRules` до `ObjectManager.isInitialized()`.

## [0.9.197] - 2026-08-30

### Исправлено

- Historian/periodic `@bindingRules` только для существующих object_nodes.
- Cascade disable SQL bindings при DELETE объекта.
- UTF-8 mojibake в AI agent UI-строках.
- Prometheus gauge `ispf.websocket.clients`.

## [0.9.196] - 2026-08-30

### Исправлено

- MEMBER ACL на tree-variables reports (API/agent/export); CEL smoke evidence 0.9.195.

## [0.9.195] - 2026-08-30

### Исправлено

- Scheduler idle gate — periodic/application SQL/analytics ждут `ObjectManager.isInitialized()`.
- SQL binding soft-fail — отсутствующий target → warn + skip.
- Legacy `root.users.<user>` → `root.platform.security.users.<user>` при syncUser.

## [0.9.194] - 2026-08-30

### Исправлено

- **WebSocket `/ws/objects`** — local `JwtDecoder`/`example.invalid` больше не роняет handshake (500 + log spam); opaque platform tokens не идут в JWT decode.

### Ранее Unreleased

- CI nightly ghost failures на push в `main` — лёгкий `push-ack`, тяжёлые job только schedule/dispatch.
- Live FPS gate на реальном operator mimic + evidence demostand 0.9.193.

## [0.9.193] - 2026-08-30

### Исправлено / изменено

- Общий `parsePrivateKey` для PEM signing; docs honesty Post-S33.

### Изменено

- Версия платформы **0.9.193**.

## [0.9.192] - 2026-08-30

### Исправлено

- **PEM в EnvironmentFile** — signing/verify терпят литеральные `\n`
  (AI apply 503 `Illegal base64 character 5c`); VPS enable script пишет
  single-line PEM.

### Evidence

- Soft re-soak demostand **0.9.191** HVAC/MES/SCADA (~19s, signed).

### Изменено

- Версия платформы **0.9.192**.

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
