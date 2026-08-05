# Marketplace — Ойл Контроль

## Артефакт

| Поле | Значение |
|---|---|
| Package / appId | `oil-control` |
| Display name | Ойл Контроль |
| Bundle | `examples/oil-control/bundle.json` (+ sql V1–V8) |
| Версия | `0.5.0` |
| Frontend | отдельный пакет `oil-control-azs-web` (не в marketplace JAR) |
| Catalog | `examples/marketplace-catalog/oil-control/` |

## Что кладём в marketplace

1. Signed bundle (`license` + `installationId` инстанса заказчика)
2. Карточка: АЗС / баланс / РГС–ТРК / качество / логистика / активы / KPI
3. Требования: ISPF ≥ 0.9.x, PostgreSQL app schema
4. После install: operator fallback UI + URL React SPA

## Не включать в marketplace JAR

- React SPA исходники (отдельный релиз)
- Demo seed можно оставить как `profile=demo`
