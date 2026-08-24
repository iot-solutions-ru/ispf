> **Язык:** русская версия (вычитка). Канонический английский: [en/hmi-offline-field-soak.md](../en/hmi-offline-field-soak.md).

# Полевой soak HMI offline (BL-151 / Post-S33)

> **Статус:** Lab — PWA offline validation. Хаб: [doc-status.md](doc-status.md).

Процедура **offline operator PWA** на video wall / планшете. CI FPS/Lighthouse **Готово**; хвост — **2 ч / 8 ч** на объекте.

---

## Уровни

| Уровень | Длительность |
| ------- | ------------ |
| CI | `npm run pwa:offline-evidence` |
| Field min | **2 ч** offline |
| Stretch | **8 ч** offline |

---

## Процедура

1. Прогреть dashboards/mimics online.
2. Отключить сеть (airplane / VLAN).
3. Каждые 30 мин — проверка экранов + offline banner.
4. Reconnect — синхронизация ≤ 5 мин.

Журнал: [hmi-offline-soak-journal.template.md](../evidence/hmi-offline-soak-journal.template.md)

См. [operator-pwa-android-smoke.md](operator-pwa-android-smoke.md)
