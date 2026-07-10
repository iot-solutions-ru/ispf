> **Язык:** русская версия (вычитка). Канонический английский: [en/scada.md](../en/scada.md).

# SCADA — мнемосхемы ISPF

Конфигурируемые мнемосхемы / P&ID / однолинейные схемы с каталогом SVG-символов, живыми привязками к переменному дереву объектов и управление с помощью HMI.

**См. также:** [scada-mimic](scada-mimic.md) (схема `diagramJson`, REST API), [WIDGETS.md § scada-mimic](widgets.md), [dashboards](dashboards.md), [bindings](bindings.md).

---

## Назначение

Имитация SCADA в ISPF закрывает типичные задачи операторского HMI:

| Задача | Механизм |
|--------|----------|
| Однолинейная / технологическая схема | Документ `diagramJson` + символы |
| Live-отображение уровней, ход клапанов, тревога | `bindings` на переменные устройства/модели |
| Управление с мнемосхемы | `actions` (setVariable / toggle / invokeFunction) |
| Переиспользование схемы на нескольких дашбордах | Объект `MIMIC` в `root.platform.mimics.*` |
| Связь с контекстом дашборда | `selectionKey` в binding (как у виджетов) |

Логика остается в **дереве объектов** (устройства, привязки, функции). Мнемосхема — **представление**, не отдельное время выполнения.

---

## Три уровня сущностей

Не путать три разных «объекта»:

| Уровень | Где живёт | Пример | Как создаётся |
|---------|-----------|--------|---------------|
| **Объект платформы** | Дерево `root.platform.*` | УСТРОЙСТВО, ПРИБОРНАЯ ПАНЕЛЬ, **МИМИЧЕСКАЯ** | Explorer, API, пакет, инструменты агента |
| **Элемент диаграммы** | Внутри `diagramJson` | бак, клапан, подпись | Редактор → инструмент **Место** |
| **Данные по схеме** | Переменные объект-платформа | `level`, `open`, `alarm` | Проводник → устройство/модель; на схеме только **обязательное** |

Редактор мнемосхемы **не создаёт** устройства и не пишет телеметрию — он рисует символы и обозначает `objectPath` + `variableName`.

---

## Объекты MIMIC в деревне

| Путь | Тип | Модель |
|------|-----|--------|
| `root.platform.mimics` | `MIMICS` | каталог |
| `root.platform.mimics.{name}` | `MIMIC` | `mimic-v1` |

Переменные объекта `MIMIC`:

| Переменная | Назначение |
|----------|------------|
| `title` | Заголовок |
| `diagram` | JSON документа схемы (`diagramJson`) |
| `refreshIntervalMs` | Интервал опроса bindings (default 5000) |

### Создание мнемосхемы

1. Проводник → **Мимики SCADA** (`root.platform.mimics`)
2. ПКМ → **Создать мнемосхему** (или кнопка «+»)
3. Имя сегмента пути, например `my-plant` → `root.platform.mimics.my-plant`
4. Открывается полноэкранный редактор; сохранение → `PUT /api/v1/mimics/by-path/diagram`

Альтернатива: `POST /api/v1/objects` с `type: MIMIC`, `templateId: mimic-v1`, `parentPath: root.platform.mimics`.

---

## Виджет `scada-mimic` на дашборде

Виджет отображает документ мнемосхемы на макете дашборда (строитель + оператор HMI).

| Поле | Описание |
|------|----------|
| `mimicPath` | Путь объект `MIMIC` — диаграмма загружается с сервера (**рекомендуется** для переиспользования) |
| `diagramJson` | Inline JSON, если `mimicPath` не задан |
| `defaultZoom` | Начальный масштаб (default `1`) |
| `panEnabled` | Pan/zoom колёсиком (default `true`) |

**Режимы хранения:**

- **По ссылке** — `mimicPath: root.platform.mimics.my-plant`. Редактор в Dashboard Builder сохраняет на сервер.
- **Inline** — `diagramJson` в конфиге виджета. Удобно для быстрых черновиков без мест в деревне.

Пример макета фрагмента:

```json
{
  "id": "m1",
  "type": "scada-mimic",
  "x": 0,
  "y": 0,
  "w": 84,
  "h": 56,
  "mimicPath": "root.platform.mimics.mini-tec-single-line",
  "defaultZoom": 1,
  "panEnabled": true
}
```

> Виджет `mini-tec-sld` **удалён**. Используйте только `scada-mimic`.

---

## Редактор мнемосхемы

Открытие:

- **Explorer** → объект `MIMIC`
- **Dashboard Builder** → виджет `scada-mimic` → «Открыть редактор мнемосхемы»

### Инструменты

| Инструмент | Действие | Горячие клавиши |
|------------|----------|-----------------|
| **Select** | Выбор, перетаскивание, resize | `V` |
| **Place** | Клик по палитре → клик на холст | `P` |
| **Connect** | Два клика по портам → ортогональная линия | `C` |
| Undo / Redo | История изменений документа | `Ctrl+Z` / `Ctrl+Y` |
| Delete | Удалить выделенные элементы или линию | `Del`, `Backspace` |
| Import / Export | JSON `diagramJson` | — |

### Тулбар: трансформация и соревнования

| Группа | Кнопки | Условие |
|--------|--------|---------|
| **Трансформировать** | Переворот по горизонтали/вертикали, поворот ±90° | ≥ 1 выделенный элемент |
| **Выровнять** | влево / центр H / вправо / вверх / середина V / вниз | ≥ 2 элемента |
| **Распространить** | расширить по Г/В | ≥ 3 элемента |
| **Сетка** | показ сетки, привязка к сети (переключение) | всегда |

Выравнивание результата **внутри выделения ограничивающей рамки** (как в Visio/Figma). После выравнивания/распределения и перемещения символы **пересчитываются** из портов.

### Выделение и перемещение

| Действие | Поведение |
|----------|-----------|
| Клик | Выбрать один элемент (или линию) |
| **Shift+клик** | Добавить/удалить элемент из выделения |
| Перетащить | Переместить выделенный элемент; если он уже в режиме множественного выбора — двигается **вся группа** |
| Смарт-оснастка | При перетаскивании краев/центров/портов «липнуть» к другим элементам (~10 пикселей); пунктирные направляющие на холсте |
| Привязка к сетке | При включенном переключателе — координаты кратны `grid.size` (по умолчанию 10 px при первом включении) |

### Изменение размера

- **Ручки** (8 точек) на рамке — только при **одном** выделенном элементе, инструмент Select.
- Угловые ручки + **Shift** — сохранение пропорций.
- Размер записывается в `element.props.width` / `element.props.height` (px), `scale` сбрасывается в `1`.
- Минимальный размер: **16×16** пикселей.
- Поля **W/H** в панели имеют функцию синхронизированы с ручками.

> Рамка выделения — **логический символ bbox** (`symbolSize()`). В части символов (например, вертикальный танк) рисунок занимает не всю область — данные «T» и «— / 100» включены в bbox.

### Свойство Панель

Координаты **X / Y**, размер **W / H**, поворот, слой, **привязки** (в т.ч. `qualityField`), **правила формата**, **действия** (несколько на элемент: основной/контекст), подсказки (`tooltip`), собственный SVG. Панель **Слои** — видимость, блокировка, активный слой для Place.

### Соединения (соединения)

- Линии привязаны к **портам** символов (`from` / `to`: `elementId` + `port`).
- Маршрут **ортогональный**; при перемещении или повороте символического пути **перечитывается заново** от текущих позиций портов.
- Bindings линии: `flowing`, `alarm` (цвет / анимация потока).

### Сетка редактора

| Поле `grid` | Default | Описание |
|-------------|---------|----------|
| `size` | `1` | Шаг сетки (пикселей); при включении snap через тулбар — **10**, если было `1` |
| `snap` | `false` | Привязка placement и drag к сетке (toggle в тулбаре) |
| `visible` | `false` | Отображение сетки на холсте (toggle в тулбаре) |

Координаты элементов — **пиксели на артборде**, без ячеек сетки дашборда.

---

## Привязки (bindings.md)

Элемент или линия ссылается на живые данные платформы:

```json
"bindings": {
  "fillLevel": {
    "objectPath": "root.platform.devices.demo-sensor-01",
    "variableName": "level",
    "valueField": "value",
    "transform": "number"
  }
}
```

| Поле | Описание |
|------|----------|
| `objectPath` | Статический путь объекта |
| `selectionKey` | Вместо `objectPath` — объект из контекста дашборда (строка таблицы и т.д.) |
| `variableName` | Имя переменной на объекте |
| `valueField` | Поле в record (обычно `value`) |
| `qualityField` | Поле качества OPC/надёжности на той же переменной (для `{key}Quality` во время выполнения) |
| `transform` | `bool` / `number` / `string` |

Привязка ключа (`fillLevel`, `open`, `running`, …) должна совпадать с ключом в **`bindingSchema`** символа (в пакете каталога или в `customSymbols[]` документе) и с полем ** `bind`** в `behaviors[]`, если поведение предписано к данным.

### Управление с мнемосхемы (действия)

На элементе можно задать `actions` — клик оператора на HMI:

| тип | Поведение |
|------|-----------|
| `setVariable` | Запись значения |
| `toggleVariable` | Инверсия bool |
| `invokeFunction` | `POST .../functions/{name}/invoke` |
| `navigate` | Переход на другой дашборд (`dashboardPath`) или URL |
| `toggleLayer` | Переключение видимости слоя на HMI |
| `cycleUnit` | Цикл единиц измерения в `element.props.unitMode` |
| `toggleExpand` | Разворот таблицы (`compact` ↔ `full` в props) |

Поддерживаются `trigger` (`primary` / `context`), `label` и `order` для контекстного меню, `objectPath` / `selectionKey`, `confirmMessage`.

---

## Схема документа (diagramJson)

Формат **версия 2** (единственный терапевтический). Документы v1 при включении нормализуются в v2 без макета runtime-миграции.

```json
{
  "version": 2,
  "width": 1600,
  "height": 900,
  "background": "var(--bg)",
  "grid": { "size": 1, "snap": false, "visible": false },
  "layers": [{ "id": "layer-default", "name": "Main", "visible": true }],
  "elements": [],
  "connections": [],
  "customSymbols": []
}
```

Полная схема полей, REST API и реэкспорт загрузочного JSON: [scada-mimic](scada-mimic.md).

---

## Каталог символов

Все символы на мнемосхеме — **SVG**. Рендерер один: `CustomSvgSymbol` + опциональный движок `behaviors`.

### Палитра редактора

| Категория | ID | Описание |
|-----------|-----|----------|
| **упаковочные клапаны**, **упаковочные насосы**, **упаковочные баки**, **упаковочные трубы**, **упаковочные датчики**, **упаковочные электрические устройства**, **pack-isa**, **pack-разное** | `pack.ispf-pid.*` | Стандартный P&ID-пак ISA/ISO (~57 шт.), статическая геометрия |
| **common** | `custom.svg` | Пустой шаблон; SVG задаётся в `element.props` |
| **Свои SVG** | `custom:{id}` | Только символы с `inUserLibrary: true` (см. ниже) |

Pack-символы: `apps/web-console/src/scada/symbols/packs/ispf-pid-v1/`. Генератор: [`tools/symbol-pack-isa`](readme.md).

**Полное руководство (загрузка, библиотека, поведение):** [scada-symbol-library](scada-symbol-library.md) (BL-94).

### Три выхода размещения

| Способ | `symbolId` на элементе | Где хранится SVG |
|--------|------------------------|------------------|
| Из палитры pack | `pack.ispf-pid.vertical-tank` | В каталоге pack (не в документе) |
| Inline custom | `custom.svg` | `element.props.svg` |
| Ссылка на библиотеку документа | `custom:lib-gen-block` | `customSymbols[].svg` |

**«Свои SVG» в палитре** — только записи `customSymbols[]` с `"inUserLibrary": true`. Bootstrap-определения (mini-TEC, конвейер) и пакета копий, созданные ключом «Редактировать как SVG», **не** использовать в палитру, пока вы не сохраните изменения («Обновить символ в библиотеке» или загрузку SVG).

### Рабочий процесс в редакторе

1. **Стандартный символ** — Место из категории пакета → `symbolId: pack.ispf-pid.*`, привязка к свойству панели.
2. **Пакет кастомизации** — выделить элемент → «Редактировать как SVG» → исправление разметки → **«Обновить символ в библиотеке»** → появление символа в «Свои SVG».
3. **Загрузка SVG** — категория «Свои SVG» → «Загрузить SVG» → сразу `inUserLibrary: true`.
4. **Импорт/Экспорт** — полный `diagramJson` в панели импорта (удобно для начальной загрузки и CI).

---

## Пользовательский SVG и поведение

Динамика на HMI (цвет, текст, уровень, видимость) задаётся связкой **SVG-разметка** + **`behaviors[]`** + **`bindings`** на элементе.

### Структура `customSymbols[]`

Запись библиотеки документа (фрагмент):

```json
{
  "id": "lib-gen-block",
  "name": "ГПУ",
  "svg": "<rect id=\"ispf-accent\" data-ispf-accent=\"1\" .../><text id=\"ispf-label\">GEN</text><text id=\"ispf-power\">— kW</text><circle id=\"ispf-status\" .../>",
  "width": 112,
  "height": 112,
  "viewBox": "0 0 112 112",
  "ports": [{ "id": "s", "x": 56, "y": 112 }],
  "bindingSchema": [
    { "key": "running", "labelKey": "bindings.running", "type": "boolean" },
    { "key": "power", "labelKey": "bindings.power", "type": "number" },
    { "key": "label", "labelKey": "bindings.label", "type": "string", "optional": true }
  ],
  "behaviors": [
    { "bind": "running", "type": "stroke", "target": "#ispf-accent", "trueColor": "#3fb950", "falseColor": "#484f58" },
    { "bind": "running", "type": "fill", "target": "#ispf-status", "trueColor": "#3fb950", "falseColor": "#484f58" },
    { "bind": "label", "type": "text", "target": "#ispf-label", "format": "string" },
    { "bind": "power", "type": "text", "target": "#ispf-power", "format": "number", "suffix": " kW", "decimals": 0 }
  ],
  "sourceSymbolId": "gen.block",
  "inUserLibrary": false
}
```

Элемент схемы ссылается на компонент:

```json
{
  "id": "gpu1",
  "symbolId": "custom:lib-gen-block",
  "x": 120,
  "y": 80,
  "props": { "label": "ГПУ-1", "width": 112, "height": 112 },
  "bindings": {
    "running": {
      "objectPath": "root.platform.devices.mini-tec-plant.gpu-01",
      "variableName": "running",
      "valueField": "value",
      "transform": "bool"
    },
    "power": {
      "objectPath": "root.platform.devices.mini-tec-plant.gpu-01",
      "variableName": "powerKw",
      "valueField": "value",
      "transform": "number"
    }
  }
}
```

Подпись **ГПУ-1** — через `props.label` и behavior `bind: "label"` (см. `lib-gen-block` в `mini-tec-mimic.json`).

Полный эталон: `packages/ispf-server/src/main/resources/bootstrap/mini-tec-mimic.json` → `customSymbols`.

### столкновение по SVG

| Правило | Зачем |
|---------|--------|
| `id="ispf-*"` на элементах, которые меняет behavior | Селектор `target: "#ispf-label"` |
| `data-ispf-accent="1"` на рамке символа | Подсветка `formatRules` (stroke/fill) |
| Внутренний SVG **без** корневого `<svg>` | В документе кладётся только внутренняя разметка |
| CSS-переменные `var(--border)`, `var(--bg-elevated)` | Тема light/dark (pack-символы) |
| `ports[]` — координаты в системе символа (0…width/height) | Инструмент Connect |

Переопределение SVG **только для одного экземпляра**: `element.props.svg` (кнопка «Применить к экземпляру»). Сброс — «Сброс переопределение».

### Типы `behaviors`

Реализация: `apps/web-console/src/scada/svgSymbolEngine.ts`. Типы: `MimicSymbolBehavior` в `scadaMimic.ts`.

| `type` | Поля | Эффект |
|--------|------|--------|
| `text` | `bind`, `target`, `format` (`string` \| `number`), `suffix`, `decimals`, `formatPattern`, `qualityBind` | Текст в SVG-элементе; плохое качество → серый |
| `fill` | `bind`, `target`, `trueColor`, `falseColor` | Заливка по bool |
| `stroke` | `bind`, `target`, `trueColor`, `falseColor` | Обводка по bool |
| `visibility` | `bind`, `target`, `when` (`truthy` \| `falsy`) | `display: none` |
| `hidden` | то же | Инверсия visibility |
| `fillLevel` | `bind`, `target` (обычно `<rect>`), `maxBind`, `inset` | Высота наклона по отношению (уровень в ёмкости) |
| `blink` | `bind`, `target`, `when` | CSS-класс мигания при тревоге |

`target` — CSS-селектор (`#ispf-lamp`) или `data-ispf-bind-target` на узле SVG.

### `bindingSchema`

Список слотов, которые редактор показывает в панели привязок:

```json
{ "key": "open", "labelKey": "bindings.open", "type": "boolean", "optional": true }
```

Типы: `boolean`, `number`, `string`, `enum`. Ключ `key` = ключ в `element.bindings` и в `behaviors[].bind` (для динамиков).

### Пакет-символы и привязки

Символы pack по умолчанию **без** `behaviors` (статичная иконка). Для live-состояния:

- конвертировать в библиотеку документа и добавить `behaviors` + `bindingSchema`, или
- задать `customSymbols[]` в Import/Export по образцу mini-TEC.

### Генерация новых статичных символов

```bash
cd tools/symbol-pack-isa && npm install && npm run build
# → apps/web-console/src/scada/symbols/packs/ispf-pid-v1/
```

Юридически: [pid-symbols-legal](pid-symbols-legal.md).

---

## ОТДЫХ API

| Метод | Путь | Назначение |
|--------|------|------------|
| GET | `/api/v1/mimics/by-path?path=` | Загрузить mimic |
| PUT | `/api/v1/mimics/by-path/diagram?path=` | Сохранить `diagramJson` |
| PUT | `/api/v1/mimics/by-path/title?path=` | Сохранить title |

Требуется доступ для записи по пути (RBAC).

---

## Демо-схемы (фикстуры начальной загрузки)

При `ispf.bootstrap.fixtures-enabled=true`:

| Объект МИМИЦ | Дашборд | Описание |
|--------------|---------|----------|
| `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-single-line` | Однолинейная схема mini-TEC |
| `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` | Резервуарный парк (app `tank-farm-demo`, анонимизированное демо) |
| `root.platform.mimics.pipeline-rp` | `root.platform.dashboards.pipeline-scada-hmi` | СДКУ — экранная форма РП (РД-029 §6.4) |
| `root.platform.mimics.pipeline-*` (15 форм) | `root.platform.dashboards.pipeline-*` | Типовые ЭФ магистрального нефтепровода (РД-029) |

При `pipeline-scada` путь начальной загрузки `tank-farm-demo` может совпадать с диаграммой РП (устаревший псевдоним). Отдельное приложение `tank-farm-demo` — устройство `root.platform.devices.tank-farm-demo.*`.

Приложение `pipeline-scada`, устройства: `root.platform.devices.pipeline-scada.*`. Re-export JSON:

```bash
cd apps/web-console && npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
```

Прохождение мини-TEC: [reference-mini-tec-walkthrough](reference-mini-tec-walkthrough.md).

Шаблоны в коде: `apps/web-console/src/scada/templates/`. В Dashboard Builder — кнопки **мини-ТЭЦ**, **СДКУ РП (РД-029)**, **резервуарный парк**.

---

## Типовой рабочий процесс

### Новая мнемосхема для производства

1. Создать устройства/модели с телеметрией и функциями управления.
2. Проводник → `root.platform.mimics` → **Создать мнемосхему**.
3. Разместить символы, Подключить линии, привязки → переменные устройства.
4. При необходимости действий на клапанах/насосах.
5. Dashboard Builder → виджет `scada-mimic` → `mimicPath` → размер по мелкой сетке (`columns: 84`, `rowHeight: 8`).
6. HMI оператора: панорамирование/масштабирование, клики по элементам с действиями.

### Быстрый черновик без объекта MIMIC

1. Dashboard Builder → `scada-mimic` без `mimicPath`.
2. «Открыть редактор» → сохранить в `diagramJson` виджета.

### Бандл/агент

- Объект `MIMIC` в manifest `objects[]` с `templateId: mimic-v1` и начальным `diagram` в variables.
- Agent: `create_object` type `MIMIC` под `root.platform.mimics`, затем `set_variable` / mimic API для diagram.

---

## Связь с логикой платформы

- Привязки симулируют использование тех же переменных, что и виджеты диаграммы/значений/функций.
- `selectionKey` работает через `@dashboardContext` — см. [platform-logic](platform-logic.md).
- События и правила оповещений — на объектах-источниках, а не внутри JSON.

---

## Исходный код (платформа)

| Компонент | Путь |
|-----------|------|
| Типы документа | `apps/web-console/src/types/scadaMimic.ts` |
| Parse / normalize | `apps/web-console/src/scada/document.ts` |
| Align / flip / resize | `apps/web-console/src/scada/layoutOps.ts` |
| Smart-snap при drag | `apps/web-console/src/scada/elementSnap.ts` |
| Routing линий | `apps/web-console/src/scada/connectionRouting.ts` |
| Каталог / pack SVG | `apps/web-console/src/scada/symbols/` |
| Движок behaviors | `apps/web-console/src/scada/svgSymbolEngine.ts` |
| Библиотека документа | `apps/web-console/src/scada/convertBuiltinToLibrary.ts` |
| Редактор | `apps/web-console/src/components/scada/` |
| Виджет HMI | `apps/web-console/src/components/dashboard/widgets/ScadaMimicWidgetView.tsx` |
| Mimic API | `packages/ispf-server/.../mimic/MimicService.java` |
| Bootstrap JSON | `packages/ispf-server/src/main/resources/bootstrap/*-mimic.json` |
