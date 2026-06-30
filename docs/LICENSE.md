# Лицензия и границы поставки

## Platform (ISPF core)

**IoT Solutions Platform Framework (ISPF)** — platform shell и monorepo core:

| Путь | Лицензия |
|------|----------|
| `packages/ispf-core`, `packages/ispf-server`, `packages/ispf-expression`, … | [GNU AGPL v3](../LICENSE) |
| `apps/web-console/` | [GNU AGPL v3](../LICENSE) |
| `docs/` | [GNU AGPL v3](../LICENSE) |

**Dual licensing:** community использует AGPL; enterprise — [LICENSE-COMMERCIAL.md](../LICENSE-COMMERCIAL.md) + `platform-license.json` (exemption от AGPL obligations по договору).

Copyright: **© 2026 ISPF Core Contributors** — см. [LICENSE](../LICENSE) и [NOTICE](../NOTICE).

## Device drivers — driver packs only

Драйверы **не входят** в `ispf-server.jar`. Каждый протокол — отдельный pack:

```text
${ISPF_DRIVER_PACKS_DIR}/ispf-driver-modbus/
  driver-pack.json    ← licenseType, driverId
  LICENSE
  ispf-driver-modbus.jar
```

Сборка: `.\gradlew syncAllDriverPacks` → `build/driver-packs/`.

Подробно: [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md), ADR [0016](decisions/0016-agpl-dual-licensing.md).

## Application bundles — отдельный продукт

Bundle (objects, dashboards, widgets, functions) — **отдельный артефакт** со **своей EULA**:

| Тип | Лицензия |
|-----|----------|
| Open reference (`lab-training`, …) | Apache / AGPL manifest, без RSA |
| Commercial SKU | Proprietary EULA + optional RSA в manifest ([COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md)) |

Declarative bundle JSON **не является** исходным кодом platform; AGPL platform **не требует** его раскрытия.

## License boundaries (кратко)

| Артефакт | AGPL disclosure | Commercial license |
|----------|-----------------|-------------------|
| Форк / патчи `ispf-server` или web-console | Да (network use) | Enterprise EULA |
| Declarative application bundle | Обычно нет | Bundle EULA (+ RSA) |
| Driver pack | По `licenseType` pack + deps | Enterprise+ для signed packs |

## Что не входит в AGPL core repo

| Тип | Где | Лицензия |
|-----|-----|----------|
| Отраслевые reference-стенды | Отдельная ветка / repo | По поставке |
| Коммерческие плагины | Отдельный repo | EULA в пакете |
| App bundle заказчика | Project repo | По договору |

## Обязательства при распространении

1. Сохраните [LICENSE](../LICENSE) и [NOTICE](../NOTICE).
2. Приложите [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) и следуйте [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md).
3. Для driver packs — LICENSE и notices каждого pack.
4. Соблюдайте AGPL / GPL / LGPL / MPL зависимостей в packs.

## Связанные документы

- [LICENSE-COMMERCIAL.md](../LICENSE-COMMERCIAL.md)
- [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md)
- [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md)
- [CLA.md](../CLA.md)
- [PLUGINS.md](PLUGINS.md)
