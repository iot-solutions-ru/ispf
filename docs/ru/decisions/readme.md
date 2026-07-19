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

**Цепочки тем (порядок чтения):** [en/decisions/readme.md § Topic chains](../../en/decisions/readme.md#topic-chains-read-in-order).
