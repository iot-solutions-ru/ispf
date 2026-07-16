> **Язык:** русская версия (вычитка). Канонический английский: [en/plugins.md](../en/plugins.md).

# Плагины и прикладные решения

> **Статус:** Stable — Core vs packs vs bundles. Теги: [doc-status](../en/doc-status.md).

## Принцип

| Слой | Репозиторий / ветка | Лицензия |
|------|---------------------|----------|
| **Ядро ISPF** | `main` | GNU AGPL-3.0 (+ optional Enterprise EULA) |
| **Device driver packs** | `packages/ispf-driver-*` → `build/driver-packs/` | Per-pack `licenseType` in `driver-pack.json` |
| **Справочники/отраслевые стенды** | Отдельная ветка или репозиторий (не `main`) | Указано в поставке |
| **Коммерческие плагины** | Отдельные репозитории или документы | Коммерческое / Лицензионное соглашение — **явно в пакете** |
| **Заказчик проекта** | Репозиторий проекта | По договору |

Ядро **не содержит** отраслевую бизнес-логику на Java, **не включает драйверы устройств в серверный JAR** и **не меняет** с коммерческими модулями без явной границы лицензии.

**Основной принцип ISPF:** бизнес-логика решений живёт **на платформе** — в моделях, функциях, событиях, функциях и дереве объектов рабочих процессов. Ядро предлагает только generic-движки; Решение — декларативная конфигурация. См. [architecture](architecture.md).

## Как подключить расширения (кроме Java во ядре)

Прикладное решение выполняет механизмы платформы **через API и пакетное развертывание**, а не через слияние отраслевого Java в `ispf-server`:

1. `POST /api/v1/applications` — регистрация `appId`
2. `POST /api/v1/applications/{appId}/data/migrate` — SQL-таблицы приложения
3. `POST /api/v1/applications/{appId}/functions/deploy` — скрипт-функции
4. `POST /api/v1/applications/{appId}/deploy` — связать один запрос
5. Объекты, дашборды, BPMN — через события платформы REST API

См. [applications](applications.md).

## Коммерческий плагин: требования к поставкам

Каждый коммерческий плагин **обязан** включает:

- `LICENSE` или `license.json` с явным типом лицензии
- `README` с ограничениями использования и контактом правообладателя
- Версия и список совместимых версий ISPF

Рекламный пакет с секцией `license` — см. [commercial-licensing](commercial-licensing.md) и [0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md).

Плагин **не коммитится** в `packages/ispf-server/` и **не** вливается в `main` без отдельного решения о open-source.

## Поставщики LLM (уровень AI, FW-40)

Как и device drivers, LLM adapters живут **вне** `ispf-server` core:

| Модуль | Назначение |
|--------|------------|
| `packages/ispf-ai-api` | SPI `LlmProvider` |
| `packages/ispf-ai-openai-compatible` | OpenAI-compatible HTTP API |
| `packages/ispf-ai-ollama` | Ollama local API |

`ispf-server` содержит только реестр, ToolRegistry, аудит и администратор REST. Конфигурация — Профиль Spring/env (`ispf.ai.*`). См. [ai-development](ai-development.md), [0004-ai-artifact-generation-gates](decisions/0004-ai-artifact-generation-gates.md).

## Чеклист перед PR в `main`

- [ ] Нет отраслевого Java и отраслевых маршрутов BFF.
- [ ] Нет Flyway-миграций таблиц приложения в `packages/ispf-server/.../db/migration/`
- [ ] Нет `examples/<industry-app>/` в корне репозитория
- [ ] Новые возможности платформы — только общий REQ-PF/API/docs

## Связь

- [license](license.md) — платформа AGPL, пакеты драйверов, коммерческие модули
- [applications](applications.md) — развернуть API
- [decisions/](decisions/) — ADR (0008 boundary, 0009 gate, 0010 licensing)
