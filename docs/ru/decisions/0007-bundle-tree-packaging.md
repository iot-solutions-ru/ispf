> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0007-bundle-tree-packaging.md](../../en/decisions/0007-bundle-tree-packaging.md).

# ADR-0007: Bundle = tree packaging

Статус: **Принято**  
Дата: 2026-06-21

## Контекст

Разработчики solution доставляют конфигурацию через единый JSON manifest (`bundle.json`). Нужно зафиксировать, что bundle — это **упаковка object tree и связанных артефактов**, а не отдельный runtime или fork platform.

## Решение

1. **Единый manifest** — `POST /api/v1/applications/{appId}/deploy` принимает JSON с секциями `objects`, `models`, `variables`, `functions`, `workflows`, `dashboards`, `events`, `alertRules`, `correlators`, `operatorUi`, `migrations`, optional `license`.
2. **Tree-first** — после deploy узлы появляются под `root.platform.*` (и app-specific paths); бизнес-логика вызывается через object tree (`BFF invoke`, tree functions), не через hardcoded routes в `main`.
3. **App schema отдельно** — SQL migrations приложения (`migrations[]`) применяются к app schema (`schemaName`, `tablePrefix`), не к platform Flyway.
4. **Версионирование bundle** — поле `version` (semver); история deploy хранится per `appId`; зависимости через `requires[]` с `minVersion`.
5. **Commercial** — optional секция `license` (см. [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)); Apache reference bundles без `license`.

## Последствия

- [applications](../applications.md) и [solution-developer-public-api](../solution-developer-public-api.md) описывают стабильный контракт manifest.
- AI artifact generation валидирует manifest через `BundleManifestValidator` перед deploy.
- Отраслевой Java в `ispf-server` остаётся запрещён ([0001-app-platform-boundary](0001-app-platform-boundary.md)).

## Связанные материалы

- REQ-PF-01, Phase 5 tree-first — [roadmap](../roadmap.md)
- [examples/](../../../examples/) — reference bundles
