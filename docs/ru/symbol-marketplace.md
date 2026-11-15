> **Язык:** русская версия. Английский оригинал: [en/symbol-marketplace.md](../en/symbol-marketplace.md).

# Пакеты графических символов SCADA (`symbol-pack`)

> **Статус:** Stable — формат архивов графических символов P&ID и электросхем, локальная установка в `ISPF_SYMBOL_PACKS_DIR` и подключение к палитре редактора мнемосхем. Метки зрелости: [doc-status.md](doc-status.md).

В дополнение к встроенной библиотеке символов P&ID (`ispf-pid-v1`) в ISPF можно устанавливать дополнительные тематические наборы векторных символов (**`symbol-pack`**) — например, элементы вентиляции и климата (ОВиК), электроснабжения, водоподготовки или нефтегазового оборудования.

**Связанные разделы:** [Мнемосхемы SCADA (`scada.md`)](scada.md) · [Библиотека символов SCADA (`scada-symbol-library.md`)](scada-symbol-library.md) · [Формат мнемосхемы (`scada-mimic.md`)](scada-mimic.md) · [Витрина решений (`marketplace.md`)](marketplace.md).

---

## 1. Формат паспорта пакета символов (`symbol-pack.json`)

Пример демонстрационного пакета в репозитории: [`examples/marketplace-symbol-hvac-demo/`](../../examples/marketplace-symbol-hvac-demo/).

```json
{
  "packId": "ispf-symbols-pid-v2",
  "version": "2.0.0",
  "displayName": "Библиотека символов P&ID v2",
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

| Поле | Обязательно | Описание |
|------|:-----------:|----------|
| `packId` | ✓ | Уникальный идентификатор набора символов |
| `version` | ✓ | Версия пакета в формате SemVer (`MAJOR.MINOR.PATCH`) |
| `license` | ✓ | Идентификатор лицензии (SPDX или ссылка на файл лицензии в архиве) |
| `symbols[].id` | ✓ | Идентификатор символа внутри пакета |
| `symbols[].svgPath` | ✓ | Относительный путь к файлу SVG внутри архива пакета |
| `symbols[].bindingSlots` | — | Рекомендуемые точки привязки переменных для редактора мнемосхем (`fillLevel`, `alarmState` и др.) |

Требования к файлам SVG: чистая векторная разметка без встроенных тегов `<script>` и внешних сетевых зависимостей.

---

## 2. Установка и использование в редакторе мнемосхем

```yaml
ispf:
  scada:
    symbol-packs-dir: ${ISPF_SYMBOL_PACKS_DIR:/opt/ispf/symbol-packs}
```

1. **Установка:** пакет с `artifactKind: "symbol-pack"` устанавливается через раздел **Система → Решения → Маркетплейс** или копированием архива в каталог `ISPF_SYMBOL_PACKS_DIR` (`POST /api/v1/marketplace/symbols/{id}/install`).
2. **Палитра редактора:** при открытии редактора мнемосхемы консоль запрашивает `GET /api/v1/scada/symbol-packs` и добавляет все установленные категории и значки в левую палитру элементов.
3. **Размещение на холсте:** при перетаскивании символа на схему подставляются его размер по умолчанию (`defaultSize`) и заготовки точек привязки (`bindingSlots`).
