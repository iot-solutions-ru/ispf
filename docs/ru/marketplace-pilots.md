> **Язык:** русская версия. Канон: [en/marketplace-pilots.md](../en/marketplace-pilots.md).

# Marketplace / solution пилоты (рабочие reference)

> **Статус:** установки из каталога — **рабочие / REAL** (CI `validate-catalog.mjs`, dogfood, operator UI). Это **не** evidence драйвера `FIELD` — см. [ADR-0067](decisions/0067-field-certified-maturity.md) §1b.

Industry- и solution-пилоты лежат в [`examples/marketplace-catalog/`](../../examples/marketplace-catalog/). Установка: **System → Solutions → Marketplace** или deploy API. См. [marketplace](marketplace.md).

## Полосы честности

| Полоса | Смысл | Сегодня |
|--------|--------|---------|
| **Эта страница** | Installable solution / SPA / workflow packs | Много **рабочих** |
| Lab OT Trust (BL-140) | Soak протоколов на lab VLAN | Lab evidence; не завод |
| Driver FIELD / CERTIFIED | Именованный **завод** + soak + OT sign-off по **драйверу** | **0** строк матрицы |

Зелёный marketplace install **не** поднимает драйвер до FIELD.

## Как доказано «working»

1. Listing + bundle (или ui-pack zip) в `examples/marketplace-catalog/<slug>/`
2. CI `marketplace-catalog` → `node tools/marketplace-catalog/validate-catalog.mjs`
3. Dogfood на demostand / operator для product-reference паков ниже

## Product reference (по доменам)

### Buildings / energy / OT SCADA

`building-hvac`, `tank-farm`, `oil-control` (+ ui), `mini-tec`, `predictive` (+ ui), `ot-alarm-classify`, `mqtt-temperature`

### MES / ISA-95

`mes-platform` / `mes-platform-production`, `mes-reference` / `mes-oee-reference`, `mes-cto`, `mes-integration-catalog`, `mes-ogp-events`, `erp-mes-*`, `laminate-flow-mes`, `mes-wrangel-cps`, `woodmes` (+ ui где есть)

### Twins / retail / logistics

`farmtwin`, `storetwin`, `warehousetwin` / `warehouse-reference` (+ ui)

### IT / training / demos

`it-infra-monitoring`, `lab-training`, `demo-app`, `marketplace-demo`, `spreadsheet-demo`, `simulator-profiles`, `claims-control`, `coffegate`, `doom`

Полный список папок: `examples/marketplace-catalog/`.

## Что **не** заявляется

- Customer-plant FIELD драйвера ([ADR-0067](decisions/0067-field-certified-maturity.md))
- Live ERP 1C/SAP (**P-ERP** / BL-169)
- Partner Portal SaaS sync

## Связанное

- [marketplace](marketplace.md), [competitive-scorecard](competitive-scorecard.md), [0067](decisions/0067-field-certified-maturity.md), [field-pilot-playbook](field-pilot-playbook.md)
