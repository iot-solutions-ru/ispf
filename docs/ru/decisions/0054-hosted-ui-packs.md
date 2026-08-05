# ADR-0054: Hosted UI packs для application solutions

## Status

**Proposed** (2026-08-05) — Docs / design gate. Реализации в этом изменении нет. Кандидат на dogfood acceptance: marketplace app `oil-control`.

## Context

Marketplace уже ставит **application bundles** (SQL, BFF script-функции, объекты дерева, дашборды `operatorUi`) и drop-in packs (`symbol-pack`, `analytics-pack`, workflow templates). Отраслевые решения со **своим React SPA** всё ещё требуют **второй хост** (static nginx + reverse proxy `/api` на ISPF).

Сейчас:

- `operatorUi` настраивает operator shell ISPF (пути дашбордов).
- `operatorUi.spaNav` (если есть) — **метаданные** маршрутов SPA ↔ BFF, UI не хостит и не запускает.
- У listing `artifactKind` нет значения для static UI assets.

Партнёры не могут поставить полное решение «фронт + BFF» одной установкой из Marketplace / Solutions. Это ослабляет air-gap и dogfood, при этом нельзя нарушать границу app/platform: отраслевой UI не должен попадать в `apps/web-console`, отраслевой Java — в `ispf-server`.

## Decision

Ввести общую платформенную возможность **hosted UI packs**, параллельно DropIn symbol/analytics packs:

1. Новый marketplace **`artifactKind: ui-pack`** (zip static assets + манифест пакета).
2. Корень установки: `ISPF_UI_PACKS_DIR/<appId>/` (versioned replace при upgrade).
3. HTTP: раздача `GET /apps/<appId>/**` из `ispf-server` с SPA fallback на `index.html` (тот же origin, что `/api`).
4. Operator launcher: deep-link / действие «Открыть UI» → `/apps/<appId>/`.
5. Опциональное поле бандла: `operatorUi.uiPack = { packId, version, entry }`.
6. Безопасность: sandbox путей, лимит размера, CSP; **без** server-side выполнения JS из пакета.
7. Подпись / `minIspfVersion` — как у существующих marketplace packs.

### Опциональный мост (A1)

Пока UI packs не готовы — поле `operatorUi.externalSpaUrl` (или в listing), чтобы launcher открывал внешний SPA. Для production dogfood предпочтителен A2 (hosted pack).

### Non-goals

- Вшивать отраслевой SPA в `apps/web-console`.
- Отраслевые REST-контроллеры в `ispf-server`.
- Заменять operator-дашборды на виджетах — оставлять как fallback без ui-pack.
- Запускать Node или произвольный backend из пакета.

### Platform capability gate

Это **общий** packaging/serving primitive (как symbol packs), не фича Oil Control. Oil Control (или любое приложение) — только первый acceptance case.

## Consequences

- One-click / air-gap install может покрыть BFF + SPA одним листингом (или составной установкой bundle + ui-pack).
- Авторы SPA используют relative `/api/v1` и Vite `base` под `/apps/<appId>/`.
- Docs и CI validate каталога должны знать `artifactKind: ui-pack`.
- В roadmap нужен явный BL до реализации (политика quality-over-features).

### Risks

- Ошибки subpath `base` ломают загрузку assets — нужен явный контракт пакета.
- Большие SPA zip увеличивают размер артефактов — лимиты размера.
- Iframe в operator shell требует CSP review; по умолчанию — top-level navigation.

## Acceptance (dogfood)

- Install листинга, который ставит application bundle + ui-pack из System → Solutions.
- SPA доступен на `https://<ispf-host>/apps/<appId>/`.
- Login + `POST /api/v1/bff/invoke` работают same-origin.
- Upgrade ui-pack по версии без ручного деплоя nginx.
- Operator-дашборды работают, если ui-pack отсутствует.
- `docs/en/marketplace.md` описывает `ui-pack`; пример листинга в `examples/`.

## Related

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [0002-dogfooding-gate](0002-dogfooding-gate.md)
- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [marketplace](../marketplace.md)
- [applications](../applications.md)
- [operator-apps](../operator-apps.md)
- [plugins](../plugins.md)
- PR бандла Oil Control: https://github.com/iot-solutions-ru/ispf/pull/64
