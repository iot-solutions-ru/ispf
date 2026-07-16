> **Язык:** русская версия (вычитка). Канонический английский: [en/symbol-marketplace.md](../en/symbol-marketplace.md).

# Symbol marketplace (BL-185)

> **Статус:** Draft — Listing API stub. Теги: [doc-status](../en/doc-status.md).

Каталог и распространение пакетов символов SCADA/HMI — примитивы P&ID, значки оборудования, анимированные состояния и привязки мнемосхем, совместимые с [scada](scada.md) и `diagramJson` v2.

Связанные: [marketplace](marketplace.md), [scada-mimic](scada-mimic.md), [roadmap](roadmap.md) (BL-146, BL-185).

**Статус (0.9.102):** справочный пакет `ispf-pid-v1` (218 символов) поставляется в веб-консоли. API листинга/установки сервера — **заглушка** (`MarketplaceSymbolListingService`, `"source": "stub"`). См. [competitive-scorecard](competitive-scorecard.md).

---

## Объем

| В объеме | Вне области применения (v1) |
|----------|-------------------|
| Библиотеки символов SVG (резервуары, клапаны, насосы, трубы) | Полное хранилище 3D-активов |
| Манифест пакета символов + метаданные лицензии | Процесс покупки в редакторе |
| Установить через API маркетплейса платформы | Редактор пользовательских символов SaaS |
| Подсказки по привязке для мнемовидных виджетов | Автоматический P&ID из импорта CAD |

---

## Формат пакета символов

```json
{
  "packId": "ispf-symbols-pid-v2",
  "version": "2.0.0",
  "displayName": "P&ID Symbol Library v2",
  "license": "SYMBOL-PACK-PID",
  "symbols": [
    {
      "id": "tank-vertical",
      "category": "equipment",
      "tags": ["tank", "storage", "pid"],
      "svgPath": "symbols/tank-vertical.svg",
      "defaultSize": { "width": 80, "height": 120 },
      "bindingSlots": ["fillLevel", "alarmState"]
    }
  ],
  "categories": ["equipment", "valves", "instruments", "piping"]
}
```

| Поле | Требуется | Описание |
|-------|:--------:|-------------|
| `packId` | ✓ | Stable identifier |
| `version` | ✓ | Semver |
| `license` | ✓ | SPDX or custom symbol license ref |
| `symbols[].id` | ✓ | Unique within pack |
| `symbols[].svgPath` | ✓ | Relative path inside pack archive |
| `symbols[].bindingSlots` | — | Suggested variable bindings for mimic editor |

Юридическая информация: см. [apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md](../../apps/web-console/public/legal/SYMBOL-PACK-PID-LICENSE.md).

---

## Распределение

Пакеты символов поставляются по тому же контракту маркетплейса, что и пакеты приложений ([marketplace](marketplace.md)):

| Шаг | Действие |
|------|--------|
| 1 | Vendor publishes listing with `artifactKind: symbol-pack` |
| 2 | Platform downloads pack archive to `ISPF_SYMBOL_PACKS_DIR` |
| 3 | Web Console mimic editor loads catalog from `GET /api/v1/scada/symbol-packs` |
| 4 | Платные пакеты требуют активации прав (так же, как и пакеты приложений) |

Конфигурация (планируется):

```yaml
ispf:
  scada:
    symbol-packs-dir: ${ISPF_SYMBOL_PACKS_DIR:/opt/ispf/symbol-packs}
    marketplace-enabled: true
```

---

## Интеграция с веб-консолью

| Поверхность | Поведение |
|---------|----------|
| Палитра редактора мнемосхем | Просмотрите установленные пакеты символов по категориям |
| Перетаскивание | Вставить символ размера по умолчанию + слоты для привязки |
| Панель «Маркетплейс» | Просматривайте листинги символов; установка бесплатно / активация платно |
| Agent `save_mimic_diagram` | May reference `symbolPackId` + `symbolId` in element metadata |

---

## Требования к поставщику

1. SVG оптимизирован для Интернета (без встроенных скриптов).
2. Файл лицензии включен в корень пакета.
3. Минимальный набор значков для категории (например, более 20 примитивов P&ID для тега `pid`).
4. Тест взаимодействия: один эталонный образ, использующий символы пакета, проходит снимок CI.

---

## Путь OEM-партнера

Поставщики символов присоединяются к [Партнерской программе](partner-program.md) на уровне **OEM**:

- Загрузка подписанного пакета
- Обзор листинга на маркетплейсе
- Доля дохода от платных пакетов символов

---

## Дорожная карта

| ID | Результат |
|----|-------------|
| БЛ-146 | Библиотека символов P&ID v2 (справочный пакет платформы) |
| БЛ-183 | Marketplace readiness (совместный процесс установки/активации) |
| БЛ-185 | Документы маркетплейса символов + договор о листинге (данный документ) |

---

## Сопутствующие документы

- [scada](scada.md) — архитектура mimic
- [widgets](widgets.md) — `scada-mimic` widget
- [marketplace](marketplace.md) — API каталога
