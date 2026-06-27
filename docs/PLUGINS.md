# Плагины и прикладные решения

## Принцип

| Слой | Репозиторий / ветка | Лицензия |
|------|---------------------|----------|
| **Ядро ISPF** | `main` | GNU AGPL-3.0 (+ optional Enterprise EULA) |
| **Device driver packs** | `packages/ispf-driver-*` → `build/driver-packs/` | Per-pack `licenseType` in `driver-pack.json` |
| **Reference / отраслевые стенды** | Отдельная ветка или репозиторий (не `main`) | Указана в поставке |
| **Коммерческие плагины** | Отдельный репозиторий или артефакт | Commercial / EULA — **явно в пакете** |
| **Проект заказчика** | Репозиторий проекта | По договору |

Ядро **не содержит** отраслевую бизнес-логику в Java, **не включает device drivers в server JAR** и **не смешивается** с коммерческими модулями без явной границы лицензии.

**Основной принцип ISPF:** бизнес-логика решения живёт **на платформе** — в моделях, переменных, событиях, функциях и workflow дерева объектов. Ядро поставляет только generic-движки; решение — declarative-конфигурацию. См. [ARCHITECTURE.md](ARCHITECTURE.md#основной-принцип-бизнес-логика-в-механизмах-платформы).

## Как подключать расширения (без Java в ядре)

Прикладное решение наполняет механизмы платформы **через API и bundle deploy**, не через merge отраслевого Java в `ispf-server`:

1. `POST /api/v1/applications` — регистрация `appId`
2. `POST /api/v1/applications/{appId}/data/migrate` — SQL-таблицы приложения
3. `POST /api/v1/applications/{appId}/functions/deploy` — script-функции
4. `POST /api/v1/applications/{appId}/deploy` — bundle одним запросом
5. Объекты, dashboards, BPMN — через существующие REST API платформы

См. [APPLICATIONS.md](APPLICATIONS.md).

## Коммерческий плагин: требования к поставке

Каждый коммерческий плагин **обязан** включать:

- `LICENSE` или `license.json` с явным типом лицензии
- `README` с ограничениями использования и контактом правообладателя
- Версию и список совместимых версий ISPF

Commercial bundle с секцией `license` — см. [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) и [0003](decisions/0003-commercial-bundle-licensing.md).

Плагин **не коммитится** в `packages/ispf-server/` и **не** вливается в `main` без отдельного решения о open-source.

## LLM providers (AI Layer, FW-40)

Как и device drivers, LLM adapters живут **вне** `ispf-server` core:

| Модуль | Назначение |
|--------|------------|
| `packages/ispf-ai-api` | SPI `LlmProvider` |
| `packages/ispf-ai-openai-compatible` | OpenAI-compatible HTTP API |
| `packages/ispf-ai-ollama` | Ollama local API |

`ispf-server` содержит только registry, ToolRegistry, audit и admin REST. Конфигурация — Spring profile/env (`ispf.ai.*`). См. [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md), [0004](decisions/0004-ai-artifact-generation-gates.md).

## Чеклист перед PR в `main`

- [ ] Нет отраслевого Java и industry-specific BFF routes
- [ ] Нет Flyway-миграций таблиц приложения в `packages/ispf-server/.../db/migration/`
- [ ] Нет `examples/<industry-app>/` в корне репозитория
- [ ] Новые возможности платформы — только generic REQ-PF / API / docs

## Связь

- [LICENSE.md](LICENSE.md) — AGPL платформы, driver packs, коммерческие модули
- [APPLICATIONS.md](APPLICATIONS.md) — deploy API
- [decisions/](decisions/) — ADR (0008 boundary, 0009 gate, 0010 licensing)
