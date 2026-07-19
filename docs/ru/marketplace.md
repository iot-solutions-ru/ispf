> **Язык:** русская версия (вычитка). Канонический английский: [en/marketplace.md](../en/marketplace.md).

# Интеграция с маркетплейсом

> **Статус: Partial (BL-183).** Просмотр remote catalog, free/paid install, подпись, версионирование и offline local install — реальны. Остаются для GA: **живые partner marketplace catalogs** (п. 11) и **CI validate-on-publish** (п. 12). Symbol **packs** — Done (BL-185, `ISPF_SYMBOL_PACKS_DIR` + scada API); `GET /api/v1/marketplace/symbols` отдаёт bundled/local (`source`: `bundled` | `local`), не remote partner store. Partner **directory** (3 seeded DB partners) — Done (BL-184) — см. [partner-program](partner-program.md). Не читайте каждый статус «Реализовано» как готовность внешних partner catalogs.

Платформа ISPF может просматривать **удалённые серверы маркетплейса**, устанавливать бесплатные bundle и активировать платные листинги с ключом entitlement.

URL маркетплейса по умолчанию **настраивается** (`ISPF_MARKETPLACE_DEFAULT_URL`). Для production укажите свой совместимый хост.

## Конфигурация

```yaml
ispf:
  marketplace:
    enabled: ${ISPF_MARKETPLACE_ENABLED:true}
    default-id: ${ISPF_MARKETPLACE_DEFAULT_ID:default-publisher}
    endpoints:
      - id: default-publisher
        name: IoT Solutions Marketplace
        base-url: ${ISPF_MARKETPLACE_DEFAULT_URL:https://marketplace.ispf.ai}
        contact-url: ${ISPF_MARKETPLACE_CONTACT_URL:https://vendor.example.invalid}
        default-endpoint: true
      - id: acme
        name: Acme Solutions Store
        base-url: https://marketplace.acme.example
        contact-url: mailto:sales@acme.example
```

| Переменная | По умолчанию | Описание |
|----------|---------|-------------|
| `ISPF_MARKETPLACE_ENABLED` | `true` | Включить удалённый каталог в System → Solutions |
| `ISPF_MARKETPLACE_DEFAULT_URL` | `https://marketplace.ispf.ai` | Базовый URL основного каталога |
| `ISPF_MARKETPLACE_CONTACT_URL` | сайт вендора | Запасная ссылка «связаться с вендором» |

## Web Console

**System → Solutions → Marketplace**

![Каталог Marketplace — просмотр и установка solution bundles](../assets/ispf-marketplace.png)

- Выбор endpoint маркетплейса
- Поиск и фильтрация (free / paid)
- **Free** — установка в один клик (платформа проксирует download + deploy). Unsigned-манифесты допускаются только на этом пути marketplace install при `ispf.license.require-signed-bundles=true`; прямой `POST .../deploy` по-прежнему требует подписанный блок `license`.
- **Paid** — ввод кода активации → deploy подписанного bundle; без ключа — ссылка на вендора

## Platform API

| Method | Path |
|--------|------|
| GET | `/api/v1/solutions/marketplaces` |
| GET | `/api/v1/solutions/marketplaces/{id}/catalog?q=&pricing=` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/install` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/activate` |

Paid activate body: `{ "activationCode": "..." }` — `installationId` добавляется на стороне сервера.

## Контракт сервера маркетплейса

Совместим с API совместимого marketplace-сервера:

- `GET /api/v1/catalog` → `{ listings: [...] }`
- `GET /api/v1/catalog/{slug}/download` (free) — опционально `?installationId=` возвращает RSA-подписанный bundle при настроенном ключе маркетплейса
- `POST /api/v1/entitlements/activate` (paid)

Поля листинга, используемые UI: `slug`, `title`, `description`, `pricing`, `appId`, `artifactKind`, `packId`, `vendorName`, `vendorLegalName`, `vendorInn`, `vendorSellerKind` (`company` | `individual`), `vendorContactPerson`, `vendorContactEmail`, `vendorContactPhone`, `priceCents`, `latestVersion`, `minIspfVersion`, `tags`.

### Виды артефактов (`artifactKind`)

| `artifactKind` | Куда устанавливается | Документ |
|----------------|----------------------|----------|
| *(по умолчанию / не указан)* | Deploy application bundle | [applications](applications.md) |
| `symbol-pack` | `ISPF_SYMBOL_PACKS_DIR` (REAL — BL-185) | [symbol-marketplace](symbol-marketplace.md) |
| `analytics-pack` | `ISPF_ANALYTICS_PACKS_DIR` | [analytics-formulas-and-packs](analytics-formulas-and-packs.md) |

Платные **analytics extension packs** (Tier C historian-функции) используют тот же install/activate API, что и приложения. После установки helpers появляются в `GET /api/v1/platform/analytics/catalog` с `pack: <packId>`.

Локальный symbol catalog (dev/lab, не remote partner store): `GET /api/v1/marketplace/symbols` → `MarketplaceSymbolListingService` (`source`: `bundled` | `local`). Drop-in install + mimic palette: BL-185 Done — см. [symbol-marketplace](symbol-marketplace.md).

## Чеклист готовности маркетплейса (BL-183)

База для готовности маркетплейса Phase 32. Отслеживание в планировании релизов; не все пункты обязательны для dev/lab browse. Не читать каждый статус «Реализовано» как полный внешний/partner GA.

**Остаётся для BL-183 Done:** п. 11 (живые partner catalogs) + п. 12 (CI gate на publish). Пункты 1–6, 8–10 — Реализовано; п. 7 — База (поля в демо-каталоге, без жёсткой schema-проверки).

| # | Пункт | Статус |
|---|------|--------|
| 1 | Просмотр удалённого каталога (System → Solutions) | Реализовано |
| 2 | Бесплатная установка в один клик (платформа проксирует download + deploy) | Реализовано |
| 3 | Платная активация с ключом entitlement | Реализовано |
| 4 | Проверка подписи bundle при install | Реализовано — paid activate; free `/download?installationId=` подписывает на маркетплейсе; trusted fallback при unsigned |
| 5 | Version pinning + upgrade path (`latestVersion`, semver) | Реализовано — `updateAvailable` в каталоге, `installedVersion` / `upgrade` при install |
| 6 | Проверка `minIspfVersion` перед install | Реализовано |
| 7 | Юридические поля вендора в манифесте листинга | База — есть в `examples/marketplace-catalog/` + демо; не schema-enforced |
| 8 | Offline/air-gapped импорт bundle (тот же манифест) | Реализовано через deploy API + local `/api/v1/marketplace/bundles` |
| 9 | Runbook reseed артефактов сервера маркетплейса | Реализовано — см. Troubleshooting |
| 10 | 10+ подписанных production bundle | Реализовано — `examples/marketplace-catalog/` (17 листингов); `publish-marketplace-catalog.ps1` |
| 11 | 3 внешних партнёра / partner catalogs | **Частично** — 3 DB partners seeded (`PartnerProgramService` `source=db`, BL-184 Done); живые partner marketplace catalogs (отдельные catalog hosts) ещё **Запланировано** |
| 12 | CI: bundle validate on publish | Частично — CLI `tools/bundle-validate-cli/validate.mjs` + example workflows; нет обязательного gate на publish marketplace-catalog |

### Манифест демо-листинга

Эталонный листинг + bundle для seeding сервера маркетплейса и интеграционных тестов:

| Файл | Назначение |
|------|---------|
| [examples/marketplace-demo/listing.manifest.json](../../examples/marketplace-demo/listing.manifest.json) | Поля записи каталога (slug, vendor, pricing) |
| [examples/marketplace-demo/bundle.json](../../examples/marketplace-demo/bundle.json) | Устанавливаемый application bundle (`appId=marketplace-demo`) |
| [examples/marketplace-analytics-pack-demo/listing.manifest.json](../../examples/marketplace-analytics-pack-demo/listing.manifest.json) | Листинг Tier C analytics pack (`artifactKind: analytics-pack`) |

Поток публикации (сервер маркетплейса):

1. Скопируйте `bundle.json` в хранилище артефактов маркетплейса как `marketplace-demo__1.0.0.json`
2. Зарегистрируйте `listing.manifest.json` в индексе каталога
3. Проверьте `GET /api/v1/catalog/marketplace-demo/download` и ISPF **Установить**

Массовая публикация каталога:

```powershell
.\deploy\tools\publish-marketplace-catalog.ps1
```

Платные analytics packs: marketplace `activate` подписывает `analytics-pack.json` внутри zip (`patch-marketplace-analytics-pack-signing.sh`).

## Troubleshooting

### `ENOENT ... warehouse-reference__1.0.0.json` on download / ISPF install 502

Каталог (`GET /api/v1/catalog`) работает, но **Скачать bundle** или ISPF **Установить** падает — JSON bundle отсутствует на сервере маркетплейса (несовпадение host path и Docker volume).

На **marketplace VPS** (`marketplace.example.invalid`):

```bash
cd /opt/ispf-marketplace
git pull origin main
bash deploy/vps-reseed-artifacts.sh
```

ISPF только проксирует download; исправление всегда на хосте маркетплейса (см. deploy runbook того сервера).

## См. также

- [competitive-scorecard](competitive-scorecard.md) — измерение 12 (экосистема / маркетплейс)
- [commercial-licensing](commercial-licensing.md)
- [plugins](plugins.md)
- [applications](applications.md)
- [analytics-formulas-and-packs](analytics-formulas-and-packs.md) — Tier C packs в маркетплейсе
