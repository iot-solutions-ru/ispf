# Архитектурные решения (ADR)

> **Статус:** Stable — Архитектурные решения. Теги: [doc-status](../../en/doc-status.md).

Русские ADR в этой папке. Канонический английский: [../../en/decisions/](../../en/decisions/).

## Стиль

Инженерная запись — см. [../../en/decisions/readme.md](../../en/decisions/readme.md) (стиль и цепочки тем).

## Индекс

Полный список: файлы `NNNN-*.md` здесь и в [en/decisions/](../../en/decisions/).

| ID | Тема |
|----|------|
| [0039-unified-alarm-architecture](0039-unified-alarm-architecture.md) | Эволюция alert rule (`alert-rule-v1`) |
| [0040-unified-computations-ui](0040-unified-computations-ui.md) | Вкладка «Вычисления» |
| [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) | Historian binding rules (мультитег) |
| [0042-analytics-function-catalog](0042-analytics-function-catalog.md) | Каталог analytics-функций |
| [0043-unified-platform-ref](0043-unified-platform-ref.md) | Единый адресный язык PlatformRef |
| [0044-object-query](0044-object-query.md) | Object Query (OQ) |
| [0045-java-function-sandbox](0045-java-function-sandbox.md) | Песочница Java-функций (фаза 1) |
| [0046-nats-cluster-package](0046-nats-cluster-package.md) | NATS в `cluster` + TRANSIENT persist skip |
| [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md) | Собственный subset BPMN (без Camunda) — **Accepted** |
| [0048-server-modularization-seams](0048-server-modularization-seams.md) | ObjectTreePort → AI-модуль → ObjectManager — **Accepted** |
| [0049-ot-automation-excellence](0049-ot-automation-excellence.md) | OT Automation Excellence (journal, AI-BPMN, analytics AI) — **Accepted** |
| [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) | Производственные паттерны как solution / marketplace configuration — **Accepted** |
| [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md) | Poka-yoke: constraints вместо гвардов — **Accepted** |
| [0052-ispf-workflow-diagram-editor](0052-ispf-workflow-diagram-editor.md) | Редактор workflow-диаграмм ISPF — **Accepted** |
| [0053-ispf-template-filler](0053-ispf-template-filler.md) | ISPF Template Filler (POI) вместо YARG для spreadsheet — **Accepted** |
| [0054-hosted-ui-packs](0054-hosted-ui-packs.md) | Hosted UI packs (`/apps/<appId>/`) для SPA из marketplace — **Accepted** |
| [0055-cel-formal-verification-ai-gate](0055-cel-formal-verification-ai-gate.md) | Формальная верификация CEL как gate условий AI — **Accepted** |
| [0056-webauthn-idp-mfa](0056-webauthn-idp-mfa.md) | WebAuthn / OTP IdP как продолжение MFA (BL-194) — **Proposed** |
| [0057-ot-trust-wave1-dnp3-poll-only](0057-ot-trust-wave1-dnp3-poll-only.md) | OT Trust Wave 1 — DNP3 остаётся PRODUCTION poll-only — **Accepted** |
| [0058-mixin-reevaluation-and-detach](0058-mixin-reevaluation-and-detach.md) | Opt-in reevaluation MIXIN + ownership-aware detach — **Accepted** |
| [0059-toolchain-version-policy](0059-toolchain-version-policy.md) | Политика версий тулчейна и зависимостей (N или N-1, пины с владельцами, тяжёлые зависимости в модулях) — **Accepted** |
| [0060-solution-authoring-constraints](0060-solution-authoring-constraints.md) | Constraints авторства solution (схема, хабы SINGLETON, W1–W6) — **Accepted** |
| [0061-high-rate-telemetry-history-defaults](0061-high-rate-telemetry-history-defaults.md) | Безопасные defaults истории high-rate телеметрии (MQTT / journal / previousValue) — **Accepted** |
| [0062-correlator-keyed-dispatch](0062-correlator-keyed-dispatch.md) | Полосы коррелятора по ключу, `REQUIRES_NEW` на коррелятор, async-действия, время события — **Accepted** |
| [0063-application-git-lifecycle](0063-application-git-lifecycle.md) | Git-вкладка APPLICATION: Connect/Status/Pull/Push→PR — **Accepted** |
| [0064-release-compatibility-lts](0064-release-compatibility-lts.md) | Релизы, совместимость, LTS; `formatVersion` bundle — **Accepted** |
| [0065-secrets-key-rotation](0065-secrets-key-rotation.md) | Envelope secrets-key (`ispf1.<kid>.…`), previous-key, re-encrypt — **Accepted** |
| [0066-security-model](0066-security-model.md) | Модель безопасности (auth, RBAC, tenancy, MEMBER ACL, secrets) — **Accepted** |

**Цепочки тем (порядок чтения):** [en/decisions/readme.md § Topic chains](../../en/decisions/readme.md#topic-chains-read-in-order).
