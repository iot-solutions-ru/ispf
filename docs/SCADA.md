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

Ключ binding (`fillLevel`, `open`, …) должен соответствовать **binding schema** символа в каталоге (`apps/web-console/src/scada/symbols/`).

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

Встроенная библиотека (~50 SVG-символов):

| Категория | Примеры |
|-----------|---------|
| **process** | tanks, valves, pumps, pipes, sensors, compressors |
| **electrical** | breakers, busbars, transformers, motors |
| **common** | labels, tables, alarm banner, shapes |

Расширение: `apps/web-console/src/scada/symbols/` (React SVG + `ports` + `bindingSchema` + optional `behaviors`).

**Custom SVG:** загрузка SVG в редактор → символ `custom:{id}` или `custom.svg`; порты и behaviors задаются в `customSymbols[]` документа.

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
| `root.platform.mimics.pipeline-rp` | `root.platform.dashboards.pipeline-scada-hmi` | СДКУ — экранная форма РП (РД-029 §6.4) |
| `root.platform.mimics.pipeline-*` (15 форм) | `root.platform.dashboards.pipeline-*` | Типовые ЭФ магистрального нефтепровода (РД-029) |
| `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` | Устаревший алиас → `pipeline-rp` |

Приложение `pipeline-scada`, устройства: `root.platform.devices.pipeline-scada.*`. Re-export JSON:

```bash
cd apps/web-console && npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
```

Walkthrough mini-TEC: [REFERENCE_MINI_TEC_WALKTHROUGH.md](REFERENCE_MINI_TEC_WALKTHROUGH.md).

Шаблоны в коде: `apps/web-console/src/scada/templates/`. В Dashboard Builder — кнопки **mini-TEC**, **СДКУ РП (РД-029)**, **tank farm** (legacy).

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
| Редактор | `apps/web-console/src/components/scada/` |
| Виджет HMI | `apps/web-console/src/components/dashboard/widgets/ScadaMimicWidgetView.tsx` |
| Mimic API | `packages/ispf-server/.../mimic/MimicService.java` |
| Bootstrap JSON | `packages/ispf-server/src/main/resources/bootstrap/*-mimic.json` |
