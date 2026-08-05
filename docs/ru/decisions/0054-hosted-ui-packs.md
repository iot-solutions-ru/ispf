# ADR-0054: Hosted UI packs для application solutions

## Статус

**Accepted** (2026-08-05) — Runtime: `DropInUiPackLoader`, `HostedUiPackFilter` (`/apps/<appId>/`), marketplace `artifactKind: ui-pack`, companion `uiPackSlug`. Демо: `examples/marketplace-ui-pack-demo/`. Production dogfood: Oil Control SPA (`oil-control-azs-web`).

## Контекст

Marketplace уже ставит **application bundles** (SQL, BFF, дерево, `operatorUi`) и drop-in packs (`symbol-pack`, `analytics-pack`). Отраслевой **React SPA** по-прежнему требовал **второй хост** (nginx + reverse proxy `/api` на ISPF).

До ADR:

- `operatorUi` — оболочка Operator (dashboard paths).
- `operatorUi.spaNav` — **метаданные** маршрутов SPA → BFF, без хостинга.
- Не было `artifactKind` для static UI.

Нельзя было отдать «front + BFF» одной установкой, не нарушая границу app/platform: отраслевой UI не вшивается в `web-console`, отраслевой Java — не в `ispf-server`.

## Решение

Универсальная платформенная возможность **hosted UI packs** (как symbol/analytics DropIn):

1. Marketplace **`artifactKind: ui-pack`** (zip + `ui-pack.json`).
2. Корень: `ISPF_UI_PACKS_DIR/<appId>/`.
3. HTTP: `GET /apps/<appId>/**` с SPA fallback на `index.html` (тот же origin, что `/api`).
4. Кнопка Operator **Open app UI** → `hostedUiUrl` или bridge `externalSpaUrl`.
5. Опционально в листинге приложения: `uiPackSlug` — free install тянет companion ui-pack.
6. Безопасность: sandbox путей, лимит размера zip; **без** server-side JS из пакета.

### Non-goals

- Вшивание отраслевого SPA в `apps/web-console`.
- Отраслевые REST-контроллеры в `ispf-server`.
- Замена operator dashboards — остаются fallback без ui-pack.
- Node / произвольный backend из пакета.

## Последствия

- One-click / air-gap: BFF + SPA.
- Авторы SPA: Vite `base: '/apps/<appId>/'`, same-origin `/api/v1`.

## Acceptance

- [x] Локальный install demo ui-pack.
- [x] SPA на `/apps/<appId>/`.
- [ ] Oil Control: bundle + ui-pack из `oil-control-azs-web` dist на marketplace.
- [x] Документация `marketplace.md` + пример.

## Связанные

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [marketplace](../marketplace.md)
- Oil Control: https://github.com/iot-solutions-ru/ispf/pull/64
