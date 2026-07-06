# SCADA — мнемосхемы ISPF

Конфигурируемые мнемосхемы / P&ID / однолинейные схемы с каталогом SVG-символов, live-привязками к переменным object tree и управлением с HMI.

**См. также:** [SCADA_MIMIC.md](SCADA_MIMIC.md) (схема `diagramJson`, REST API), [WIDGETS.md § scada-mimic](WIDGETS.md#scada-mimic--scada-мнемосхема), [DASHBOARDS.md](DASHBOARDS.md), [BINDINGS.md](BINDINGS.md).

---

## Назначение

SCADA mimic в ISPF закрывает типичные задачи операторского HMI:

| Задача | Механизм |
|--------|----------|
| Однолинейная / технологическая схема | Документ `diagramJson` + символы |
| Live-отображение уровней, состояний клапанов, тревог | `bindings` на переменные devices/models |
| Управление с мнемосхемы | `actions` (setVariable / toggle / invokeFunction) |
| Переиспользование схемы на нескольких дашбордах | Объект `MIMIC` в `root.platform.mimics.*` |
| Связь с контекстом дашборда | `selectionKey` в binding (как у виджетов) |

Логика остаётся в **object tree** (devices, bindings, functions). Мнемосхема — **представление**, не отдельный runtime.

---

## Три уровня сущностей

Не путать три разных «объекта»:

| Уровень | Где живёт | Пример | Как создаётся |
|---------|-----------|--------|---------------|
| **Platform object** | Дерево `root.platform.*` | DEVICE, DASHBOARD, **MIMIC** | Explorer, API, bundle, agent tools |
| **Diagram element** | Внутри `diagramJson` | танк, клапан, подпись | Редактор → инструмент **Place** |
| **Данные на схеме** | Переменные platform object | `level`, `open`, `alarm` | Explorer → device/model; на схеме только **binding** |

Редактор мнемосхемы **не создаёт** devices и не пишет телеметрию — он рисует символы и указывает `objectPath` + `variableName`.

---

## Объекты MIMIC в дереве

| Путь | Тип | Модель |
|------|-----|--------|
| `root.platform.mimics` | `MIMICS` | каталог |
| `root.platform.mimics.{name}` | `MIMIC` | `mimic-v1` |

Переменные объекта `MIMIC`:

| Variable | Назначение |
|----------|------------|
| `title` | Заголовок |
| `diagram` | JSON документа схемы (`diagramJson`) |
| `refreshIntervalMs` | Интервал опроса bindings (default 5000) |

### Создание мнемосхемы

1. Explorer → **SCADA Mimics** (`root.platform.mimics`)
2. ПКМ → **Создать мнемосхему** (или кнопка «+»)
3. Имя сегмента пути, например `my-plant` → `root.platform.mimics.my-plant`
4. Открывается полноэкранный редактор; сохранение → `PUT /api/v1/mimics/by-path/diagram`

Альтернатива: `POST /api/v1/objects` с `type: MIMIC`, `templateId: mimic-v1`, `parentPath: root.platform.mimics`.

---

## Виджет `scada-mimic` на дашборде

Виджет отображает документ мнемосхемы на layout дашборда (builder + operator HMI).

| Поле | Описание |
|------|----------|
| `mimicPath` | Путь объекта `MIMIC` — diagram загружается с сервера (**рекомендуется** для переиспользования) |
| `diagramJson` | Inline JSON, если `mimicPath` не задан |
| `defaultZoom` | Начальный масштаб (default `1`) |
| `panEnabled` | Pan/zoom колёсиком (default `true`) |

**Режимы хранения:**

- **По ссылке** — `mimicPath: root.platform.mimics.my-plant`. Редактор в Dashboard Builder сохраняет на сервер.
- **Inline** — `diagramJson` в конфиге виджета. Удобно для быстрых черновиков без объекта в дереве.

Пример фрагмента layout:

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

| Инструмент | Действие | Горячая клавиша |
|------------|----------|-----------------|
| **Select** | Выбор, перетаскивание, resize | `V` |
| **Place** | Клик по палитре → клик на холст | `P` |
| **Connect** | Два клика по портам → ортогональная линия | `C` |
| Undo / Redo | История изменений документа | `Ctrl+Z` / `Ctrl+Y` |
| Delete | Удалить выделенные элементы или линию | `Del`, `Backspace` |
| Import / Export | JSON `diagramJson` | — |

### Тулбар: трансформация и выравнивание

| Группа | Кнопки | Условие |
|--------|--------|---------|
| **Transform** | Flip H/V, поворот ±90° | ≥ 1 выделенный элемент |
| **Align** | влево / центр H / вправо / вверх / середина V / вниз | ≥ 2 элемента |
| **Distribute** | равномерно по H / V | ≥ 3 элемента |
| **Grid** | показ сетки, snap к сетке (toggle) | всегда |

Выравнивание выполняется **внутри bounding box выделения** (как в Visio/Figma). После align/distribute и перемещения символов линии **пересчитываются** от портов.

### Выделение и перемещение

| Действие | Поведение |
|----------|-----------|
| Клик | Выбрать один элемент (или линию) |
| **Shift+клик** | Добавить / убрать элемент из выделения |
| Drag | Переместить выделенный элемент; если он уже в multi-select — двигается **вся группа** |
| Smart-snap | При drag края/центры/порты «липнут» к другим элементам (~10 px); пунктирные направляющие на холсте |
| Grid snap | При включённом toggle — координаты кратны `grid.size` (по умолчанию 10 px при первом включении) |

### Изменение размера

- **Handles** (8 точек) на рамке — только при **одном** выделенном элементе, инструмент Select.
- Угловые handles + **Shift** — сохранение пропорций.
- Размер записывается в `element.props.width` / `element.props.height` (px), `scale` сбрасывается в `1`.
- Минимальный размер: **16×16** px.
- Поля **W / H** в панели свойств синхронизированы с handles.

> Рамка выделения — **логический bbox** символа (`symbolSize()`). У части символов (например, вертикальный танк) рисунок занимает не всю область — подписи «T» и «— / 100» входят в bbox.

### Панель свойств

Координаты **X / Y**, размер **W / H**, поворот, слой, **bindings** (в т.ч. `qualityField`), **format rules**, **actions** (несколько на элемент: primary / context), подсказки (`tooltip`), custom SVG. Панель **Слои** — видимость, блокировка, активный слой для Place.

### Соединения (connections)

- Линии привязаны к **портам** символов (`from` / `to`: `elementId` + `port`).
- Маршрут **ортогональный**; при перемещении или повороте символа путь **пересчитывается заново** от текущих позиций портов.
- Bindings линии: `flowing`, `alarm` (цвет / анимация потока).

### Сетка редактора

| Поле `grid` | Default | Описание |
|-------------|---------|----------|
| `size` | `1` | Шаг сетки (px); при включении snap через тулбар — **10**, если был `1` |
| `snap` | `false` | Привязка placement и drag к сетке (toggle в тулбаре) |
| `visible` | `false` | Отображение сетки на холсте (toggle в тулбаре) |

Координаты элементов — **пиксели на artboard**, не ячейки dashboard grid.

---

## Привязки (bindings)

Элемент или линия ссылается на live-данные platform tree:

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
| `qualityField` | Поле качества OPC/надёжности на той же переменной (для `{key}Quality` в runtime) |
| `transform` | `bool` / `number` / `string` |

Ключ binding (`fillLevel`, `open`, `running`, …) должен совпадать с ключом в **`bindingSchema`** символа (в каталоге pack или в `customSymbols[]` документа) и с полем **`bind`** в `behaviors[]`, если поведение привязано к данным.

### Управление с мнемосхемы (actions)

На элементе можно задать `actions` — клик оператора на HMI:

| type | Поведение |
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

## Документ схемы (diagramJson)

Формат **version 2** (единственный поддерживаемый). Документы v1 при загрузке нормализуются в v2 без runtime-миграции layout.

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

Полная схема полей, REST API и re-export bootstrap JSON: [SCADA_MIMIC.md](SCADA_MIMIC.md).

---

## Каталог символов

Все символы на мнемосхеме — **SVG**. Рендерер один: `CustomSvgSymbol` + опциональный движок `behaviors`.

### Палитра редактора

| Категория | ID | Описание |
|-----------|-----|----------|
| **pack-valves**, **pack-pumps**, **pack-tanks**, **pack-pipes**, **pack-sensors**, **pack-electrical**, **pack-isa**, **pack-misc** | `pack.ispf-pid.*` | Стандартный P&ID-пак ISA/ISO (~57 шт.), статичная геометрия |
| **common** | `custom.svg` | Пустой шаблон; SVG задаётся в `element.props` |
| **Свои SVG** | `custom:{id}` | Только символы с `inUserLibrary: true` (см. ниже) |

Pack-символы: `apps/web-console/src/scada/symbols/packs/ispf-pid-v1/`. Генератор: [`tools/symbol-pack-isa`](../tools/symbol-pack-isa/README.md).

### Три способа размещения

| Способ | `symbolId` на элементе | Где хранится SVG |
|--------|------------------------|------------------|
| Из палитры pack | `pack.ispf-pid.vertical-tank` | В каталоге pack (не в документе) |
| Inline custom | `custom.svg` | `element.props.svg` |
| Ссылка на библиотеку документа | `custom:lib-gen-block` | `customSymbols[].svg` |

**«Свои SVG» в палитре** — только записи `customSymbols[]` с `"inUserLibrary": true`. Bootstrap-определения (mini-TEC, pipeline) и копии pack, созданные кнопкой «Редактировать как SVG», **не** попадают в палитру, пока вы не сохраните изменения («Обновить символ в библиотеке» или загрузка SVG).

### Workflow в редакторе

1. **Стандартный символ** — Place из категории pack → `symbolId: pack.ispf-pid.*`, привязки в панели свойств.
2. **Кастомизация pack** — выделить элемент → «Редактировать как SVG» → правка разметки → **«Обновить символ в библиотеке»** → символ появляется в «Свои SVG».
3. **Загрузка SVG** — категория «Свои SVG» → «Загрузить SVG» → сразу `inUserLibrary: true`.
4. **Import/Export** — полный `diagramJson` в панели импорта (удобно для bootstrap и CI).

---

## Custom SVG и behaviors

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

Элемент на схеме ссылается на библиотеку:

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

### Соглашения по SVG

| Правило | Зачем |
|---------|--------|
| `id="ispf-*"` на элементах, которые меняет behavior | Селектор `target: "#ispf-label"` |
| `data-ispf-accent="1"` на рамке символа | Подсветка `formatRules` (stroke/fill) |
| Внутренний SVG **без** корневого `<svg>` | В документ кладётся только inner markup |
| CSS-переменные `var(--border)`, `var(--bg-elevated)` | Тема light/dark (pack-символы) |
| `ports[]` — координаты в системе символа (0…width/height) | Инструмент Connect |

Переопределение SVG **только для одного экземпляра**: `element.props.svg` (кнопка «Применить к экземпляру»). Сброс — «Сбросить переопределение».

### Типы `behaviors`

Реализация: `apps/web-console/src/scada/svgSymbolEngine.ts`. Типы: `MimicSymbolBehavior` в `scadaMimic.ts`.

| `type` | Поля | Эффект |
|--------|------|--------|
| `text` | `bind`, `target`, `format` (`string` \| `number`), `suffix`, `decimals`, `formatPattern`, `qualityBind` | Текст в SVG-элементе; плохое качество → серый |
| `fill` | `bind`, `target`, `trueColor`, `falseColor` | Заливка по bool |
| `stroke` | `bind`, `target`, `trueColor`, `falseColor` | Обводка по bool |
| `visibility` | `bind`, `target`, `when` (`truthy` \| `falsy`) | `display: none` |
| `hidden` | то же | Инверсия visibility |
| `fillLevel` | `bind`, `target` (обычно `<rect>`), `maxBind`, `inset` | Высота прямоугольника по числу (уровень в ёмкости) |
| `blink` | `bind`, `target`, `when` | CSS-класс мигания при тревоге |

`target` — CSS-селектор (`#ispf-lamp`) или `data-ispf-bind-target` на узле SVG.

### `bindingSchema`

Список слотов, которые редактор показывает в панели привязок:

```json
{ "key": "open", "labelKey": "bindings.open", "type": "boolean", "optional": true }
```

Типы: `boolean`, `number`, `string`, `enum`. Ключ `key` = ключ в `element.bindings` и в `behaviors[].bind` (для динамики).

### Pack-символы и привязки

Символы pack по умолчанию **без** `behaviors` (статичная иконка). Для live-состояния:

- конвертировать в библиотеку документа и добавить `behaviors` + `bindingSchema`, или
- задать `customSymbols[]` в Import/Export по образцу mini-TEC.

### Генерация новых статичных символов

```bash
cd tools/symbol-pack-isa && npm install && npm run build
# → apps/web-console/src/scada/symbols/packs/ispf-pid-v1/
```

Юридически: [PID_SYMBOLS_LEGAL.md](PID_SYMBOLS_LEGAL.md).

---

## REST API

| Method | Path | Назначение |
|--------|------|------------|
| GET | `/api/v1/mimics/by-path?path=` | Загрузить mimic |
| PUT | `/api/v1/mimics/by-path/diagram?path=` | Сохранить `diagramJson` |
| PUT | `/api/v1/mimics/by-path/title?path=` | Сохранить title |

Требуется write-доступ к path (RBAC).

---

## Демо-схемы (bootstrap fixtures)

При `ispf.bootstrap.fixtures-enabled=true`:

| Объект MIMIC | Дашборд | Описание |
|--------------|---------|----------|
| `root.platform.mimics.mini-tec-single-line` | `root.platform.dashboards.mini-tec-single-line` | Однолинейная схема mini-TEC |
| `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` | Резервуарный парк (app `tank-farm-demo`, анонимизированное демо) |
| `root.platform.mimics.pipeline-rp` | `root.platform.dashboards.pipeline-scada-hmi` | СДКУ — экранная форма РП (РД-029 §6.4) |
| `root.platform.mimics.pipeline-*` (15 форм) | `root.platform.dashboards.pipeline-*` | Типовые ЭФ магистрального нефтепровода (РД-029) |

При `pipeline-scada` bootstrap путь `tank-farm-demo` может совпадать с диаграммой РП (deprecated alias). Отдельное приложение `tank-farm-demo` — устройства `root.platform.devices.tank-farm-demo.*`.

Приложение `pipeline-scada`, устройства: `root.platform.devices.pipeline-scada.*`. Re-export JSON:

```bash
cd apps/web-console && npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
```

Walkthrough mini-TEC: [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md).

Шаблоны в коде: `apps/web-console/src/scada/templates/`. В Dashboard Builder — кнопки **mini-TEC**, **СДКУ РП (РД-029)**, **tank farm**.

---

## Типовой workflow

### Новая мнемосхема для production

1. Создать devices/models с телеметрией и функциями управления.
2. Explorer → `root.platform.mimics` → **Создать мнемосхему**.
3. Place символы, Connect линии, bindings → device variables.
4. При необходимости actions на клапаны / насосы.
5. Dashboard Builder → виджет `scada-mimic` → `mimicPath` → размер на fine grid (`columns: 84`, `rowHeight: 8`).
6. Operator HMI: pan/zoom, клики по элементам с actions.

### Быстрый черновик без объекта MIMIC

1. Dashboard Builder → `scada-mimic` без `mimicPath`.
2. «Открыть редактор» → сохранить в `diagramJson` виджета.

### Bundle / agent

- Объект `MIMIC` в manifest `objects[]` с `templateId: mimic-v1` и начальным `diagram` в variables.
- Agent: `create_object` type `MIMIC` под `root.platform.mimics`, затем `set_variable` / mimic API для diagram.

---

## Связь с platform logic

- Bindings mimic используют те же переменные, что chart/value/function widgets.
- `selectionKey` работает через `@dashboardContext` — см. [PLATFORM_LOGIC.md](PLATFORM_LOGIC.md).
- События и alert rules — на объектах-источниках, не внутри mimic JSON.

---

## Исходный код (platform)

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
