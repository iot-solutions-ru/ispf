# ADR-0007: Bundle = tree packaging

Статус: **Accepted**  
Дата: 2026-06-21

## Контекст

Solution developers доставляют конфигурацию через один JSON manifest (`bundle.json`). Нужно зафиксировать, что bundle — это **упаковка дерева объектов и связанных артефактов**, а не отдельный runtime или fork platform.

## Решение

1. **Единый manifest** — `POST /api/v1/applications/{appId}/deploy` принимает JSON с секциями `objects`, `models`, `variables`, `functions`, `workflows`, `dashboards`, `events`, `alertRules`, `correlators`, `operatorUi`, `migrations`, optional `license`.
2. **Tree-first** — после deploy узлы появляются под `root.platform.*` (и app-specific paths); бизнес-логика вызывается через object tree (`BFF invoke`, tree functions), не через hardcoded routes в `main`.
3. **App schema отдельно** — SQL миграции приложения (`migrations[]`) применяются к app schema (`schemaName`, `tablePrefix`), не к platform Flyway.
4. **Версионирование bundle** — поле `version` (semver); история deploy хранится per `appId`; зависимости через `requires[]` с `minVersion`.
5. **Commercial** — optional секция `license` (см. [0003](0003-commercial-bundle-licensing.md)); Apache reference bundles без `license`.

## Последствия

- [APPLICATIONS.md](../APPLICATIONS.md) и [SOLUTION_DEVELOPER_PUBLIC_API.md](../SOLUTION_DEVELOPER_PUBLIC_API.md) описывают стабильный контракт manifest.
- AI artifact generation валидирует manifest через `BundleManifestValidator` перед deploy.
- Отраслевой Java в `ispf-server` остаётся запрещён ([0001](0001-app-platform-boundary.md)).

## Связанные материалы

- REQ-PF-01, Phase 5 tree-first — [ROADMAP.md](../ROADMAP.md)
- [examples/](../examples/) — reference bundles
