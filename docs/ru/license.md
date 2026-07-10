> **Язык:** русская версия (вычитка). Канонический английский: [en/license.md](../en/license.md).

# Лицензия и граница поставки

## Платформа (ядро ISPF)

**IoT Solutions Platform Framework (ISPF)** — оболочка платформы и ядро ​​монорепозитория:

| Путь | Лицензия |
|------|----------|
| `packages/ispf-core`, `packages/ispf-server`, `packages/ispf-expression`, … | [GNU AGPL v3](../LICENSE) |
| `apps/web-console/` | [GNU AGPL v3](../LICENSE) |
| `docs/` | [GNU AGPL v3](../LICENSE) |

**Двойное лицензирование:** сообщество использует AGPL; предприятие — [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md) + `platform-license.json` (освобождение от обязательств AGPL по договору).

Копирайт: **© 2026 Основные участники ISPF** — см. [ЛИЦЕНЗИЯ](../LICENSE) и [УВЕДОМЛЕНИЕ](../NOTICE).

## Драйверы устройств — только пакеты драйверов

Драйверы **не входят** в `ispf-server.jar`. Каждый протокол — отдельный pack:

```text
${ISPF_DRIVER_PACKS_DIR}/ispf-driver-modbus/
  driver-pack.json    ← licenseType, driverId
  LICENSE
  ispf-driver-modbus.jar
```

Сборка: `.\gradlew syncAllDriverPacks` → `build/driver-packs/`.

Подробно: [licensed-driver-packs](licensed-driver-packs.md), ADR [0016-agpl-dual-licensing](decisions/0016-agpl-dual-licensing.md).

## Пакеты приложений — отдельный продукт

Комплект (объекты, дашборды, виджеты, функции) — **отдельный аксессуар** со **своей EULA**:

| Тип | Лицензия |
|-----|----------|
| Open reference (`lab-training`, …) | Apache / AGPL manifest, без RSA |
| Коммерческий артикул | Проприетарное лицензионное соглашение + дополнительный RSA в манифесте ([commercial-licensing](commercial-licensing.md)) |

Декларативный пакет JSON **не является** исходным кодом платформы; Платформа AGPL **не требует** его раскрытия.

## Границы лицензии (кратко)

| Артефакт | Раскрытие информации по AGPL | Коммерческая лицензия |
|----------|-----------------|-------------------|
| Форк / патчи `ispf-server` или web-console | Да (network use) | Enterprise EULA |
| Декларативный пакет приложений | Обычно нет | Пакетное лицензионное соглашение (+ RSA) |
| Driver pack | По `licenseType` pack + deps | Enterprise+ для signed packs |

## Что не входит в основной репозиторий AGPL

| Тип | Где | Лицензия |
|-----|-----|----------|
| Отраслевые ссылки-стенды | Отдельная ветка / репо | По доставке |
| Коммерческие плагины | Отдельное репо | Лицензионное соглашение в пакете |
| Заказчик пакета приложений | Репо проекта | По договору |

## Обязательства при распространении

1. Сохраните [ЛИЦЕНЗИЮ](../LICENSE) и [УВЕДОМЛЕНИЕ](../NOTICE).
2. Приложите [third-party-notices](third-party-notices.md) и следом [license-compliance](license-compliance.md).
3. Для пакетов драйверов — ЛИЦЕНЗИЯ и уведомления для каждого пакета.
4. Соблюдайте зависимости AGPL/GPL/LGPL/MPL в пакетах.

## Связанные документы

- [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md)
- [license-compliance](license-compliance.md)
- [commercial-licensing](commercial-licensing.md)
- [CLA](../../CLA.md)
- [plugins](plugins.md)
