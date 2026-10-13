> **Язык:** русская версия. Канонический английский: [en/decisions/0064-release-compatibility-lts.md](../../en/decisions/0064-release-compatibility-lts.md).

# ADR-0064: Релизы, совместимость и LTS

## Статус

**Accepted** (2026-10-12) — волна 1 вводит `formatVersion` в application bundle; LTS платформы и заморозка публичного API остаются политикой до первого LTS-тега после 1.0.

## Контекст

ISPF на `0.9.x`. Нужен письменный контракт для:

1. Что ломается при апгрейде платформы (API, driver SPI, форма манифеста, Flyway).
2. Сколько живёт линейка релиза.
3. Как эволюционировать JSON bundle без тихой порчи (`additionalProperties: true` сегодня).

Связанные пробелы: [0037](0037-relational-core-portability.md) описывал «только baseline навсегда», хотя PostgreSQL уже отдаёт инкременты `V71+`; [0007](0007-bundle-tree-packaging.md) версионирует **продукт** semver `version`, но не **формат провода**.

## Решение

### 1. Три оси версий

| Ось | Поле / артефакт | Смысл |
|-----|-----------------|--------|
| Релиз платформы | `gradle.properties` / тег образа | Сборка продукта (`0.9.x` до 1.0) |
| Версия продукта bundle | `version` в манифесте (semver) | Ревизия решения; история деплоя по `appId` ([0007](0007-bundle-tree-packaging.md)) |
| **Формат** bundle | `formatVersion` (целое) | Схема JSON; независима от semver решения |

### 2. `formatVersion` (волна 1 — сделано)

1. Опциональное целое в корне `bundle.json`. **Нет поля ⇒ `1`**.
2. Текущее значение: **`1`**. Поддерживаемый диапазон этой сборки: **`1…1`** (`BundleFormatVersions`).
3. Validate/deploy **отклоняет** неподдерживаемые форматы с ясным текстом ошибки.
4. Pull-from-tree **пишет** `formatVersion: 1`.
5. JSON Schema фиксирует `formatVersion` с `const: 1`. Ужесточение `additionalProperties` — при будущем бампе формата.
6. Переход на `2`: смена схемы, dual-read или migrate, поднятие `MAX_SUPPORTED` только после читателей.

### 3. Совместимость платформы (до 1.0 и дальше)

До тега **1.0.0**:

- Semver платформы по Keep a Changelog; **ломающие** изменения на `0.9.x` допустимы, но обязаны быть в `CHANGELOG.md`.
- Публичный HTTP `/api/v1/**` и driver SPI без тихой смены семантики; предпочтительны аддитивные поля.
- После **1.0.0**: minor/patch обратно совместимы для документированных поверхностей; major может ломать со guide по миграции.

**LTS (после 1.0):** линейка LTS поддерживает major **N** и **N−1** для security-фиксов не менее **12 месяцев** с GA следующего major, если контракт заказчика не говорит иначе. Пересмотр — когда первый LTS-заказчик попросит N−2.

### 4. Flyway expand/contract (поправка к 0037)

Метаданные PostgreSQL:

1. `V1__baseline.sql` = squash legacy V1–V70.
2. **Инкременты `V71+`** — обычный путь апгрейда на том же движке.
3. Предпочтителен **expand/contract**: сначала аддитивный DDL; удаление колонок/таблиц только после релиза, который их больше не читает.
4. Смена движка (PG → MSSQL, …) — **greenfield + bundle import**, не live-миграция.
5. Пакеты не-PG (`mssql` / `mysql` / `oracle`) — **не начаты**, пока нет сертифицированного baseline.

### 5. Driver SPI и ui-pack

- Driver pack объявляет совместимость с major платформы; minor платформы не требует пересборки pack без явной записи в changelog.
- Hosted ui-pack ([0054](0054-hosted-ui-packs.md)) следует тем же правилам `/api/v1`; изоляция доверия — вне этого ADR.

## Последствия

- Fitness: `BundleFormatVersionTest`, `BundleUpgradeCompatGateTest`, `BundleSemverContractTest`, schema `bundle.schema.json`.
- Docs: [applications](../applications.md), [release-upgrade](../release-upgrade.md), [0007](0007-bundle-tree-packaging.md), [0037](0037-relational-core-portability.md).
- **Волна 2 (lab):** accept/reject format + install/redeploy marketplace-demo — [release-upgrade](../release-upgrade.md). **Cluster N → N+1** — всё ещё follow-up.

### Риски

- Путаница `version` и `formatVersion` — снимается документацией и текстом валидатора.
- `additionalProperties: true` на формате 1 всё ещё пропускает опечатки в корне; формат 2 должен ужесточить.

## Связанные

- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [0037-relational-core-portability](0037-relational-core-portability.md)
- [0054-hosted-ui-packs](0054-hosted-ui-packs.md)
- [0059-toolchain-version-policy](0059-toolchain-version-policy.md)
