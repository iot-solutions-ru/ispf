> **Язык:** полный русский перевод. Канонический английский: [en/widgets.md](../en/widgets.md).

# Справочник виджетов дашборда

Полное описание всех типов виджетов ISPF: назначение, использование в HMI, расположение полей и примеры.

**См. также:** [DASHBOARDS.md](dashboards.md) (сетка, `selectionKey`, навигация), [OPERATOR_GUIDE.md](operator-guide.md) (роль оператора), [BINDINGS.md](bindings.md) (привязки платформы в запросе), [SPREADSHEET_WIDGET.md](spreadsheet-widget.md) (формулы и таблицы).

---

## Общие поля (все виджеты)

Каждый виджет в `layout.widgets[]` имеет позицию на сетке и опциональную привязку к данным.

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | string | Уникальный идентификатор в layout (напр. `temp-value`) |
| `type` | string | Тип виджета (`value`, `chart`, …) |
| `title` | string | Заголовок карточки на HMI |
| `x`, `y` | number | Позиция на сетке (0-based) |
| `w`, `h` | number | Ширина и высота в единицах сетки (`columns` обычно 12) |
| `objectPath` | string | Статический путь объекта (`root.platform.devices.foo`) |
| `selectionKey` | строка | Выбор имени слота; путь берётся из `selection[key]` (см. [DASHBOARDS.md](dashboards.md)) |
| `variableName` | string | Имя переменной на объекте |
| `valueField` | строка | Поле `DataRecord`: `value` (по умолчанию), `raw`, `online`, `unit`, `int`, `string` |
| `paramKey` | string | Читать значение из `session.params[key]` |
| `contextPathKey` | string | Путь объекта из `session.params[key]` |
| `modelHintPath` | string | Только редактор: образец объекта для dropdown переменных |
| `stylesJson` | строка | JSON стилей элементов карточки (см. [DASHBOARDS.md § styleJson](dashboards.md)) |
| `demoPreviewJson` | string | Превью в редакторе без live-данных |

**Приоритет пути к объекту:** если задано `selectionKey` и в сеансе есть выбор → используется выбранный путь; иначе `objectPath`.

### JSON-поля (всегда строки в макете)

| Поле | Содержимое |
|------|------------|
| `columnsJson` | Колонки `object-table` |
| `variablesJson` | Массив имён переменных |
| `fieldsJson` | Поля `function-form` / `input-form` |
| `childrenJson` | Вложенные виджеты |
| `tabsJson` | Вкладки `tab-panel` |
| `slidesJson` | Слайды `carousel` |
| `stepsJson` | Шаги `steps-panel` |
| `itemsJson` | Пункты `nav-menu` |
| `eventNamesJson` | Фильтр имён событий |
| `contextSelectionJson` / `contextParamsJson` | Контекст при открытии дашборда |
| `rowParamsJson` / `cardParamsJson` | Params при клике строки/карточки |
| `sheetConfigJson` | Конфиг spreadsheet |
| `stylesJson` | Inline CSS по элементам карточки |

---

## Категории виджетов

| Категория | Привязка данных | Примеры |
|-----------|-----------------|---------|
| объект-переменная | Переменные одного объекта | значение, диаграмма, датчик |
| только для объектов | Объект для вызова/записи | функция, форма-функции, форма-ввода |
| parent-catalog | Дети папки `parentPath` | object-table, map, card-grid |
| внешний | Отчёты, события, поворот, другие дашборды | отчет, рабочая очередь, ссылка на панель мониторинга |
| сеанс / статический | Сессия или статический контент | этикетка, изображение, панировочные сухари |
| композиция | Вложенные виджеты | панель, панель вкладок, навигационное меню |

---

## объект-переменная — показатели и графики

### значение — Значение

**Назначение:** крупное значение числа или текста из переменной объекта.

**Оператор:** только просмотр (если не настроение пишите в другое место).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** Имя переменной |
| `valueField` | Поле записи (по умолчанию `value`) |
| `decimals` | Знаков после запятой для чисел |
| `unit` | Статическая единица (`°C`, `%`) |
| `unitField` | Взять unit из поля переменной (напр. `unit`) |

**Пример:** датчик температуры

```json
{
  "id": "temp",
  "type": "value",
  "title": "Температура",
  "x": 0, "y": 0, "w": 3, "h": 2,
  "objectPath": "root.platform.devices.demo-sensor-01",
  "variableName": "temperature",
  "valueField": "value",
  "decimals": 1,
  "unit": "°C"
}
```

**Сценарий:** SNMP monitoring — `selectionKey: "device"` + `variableName: "sysName"` для имени выбранного хоста.

---

Индикатор ### — Индикатор (лампа)

**Назначение:** булево или строковое состояние (ВКЛ/ВЫКЛ, будильник).

**Оператор:** просмотр; цвет и стоимость по конфигурации.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** |
| `valueField` | Для online-статуса: `variableName: status`, `valueField: online` |
| `trueLabel` / `falseLabel` | Подписи состояний |
| `trueColor` / `falseColor` | CSS-цвета |
| `alarmMode` | `true` = true означает тревогу (красный); иначе true = OK (зелёный) |

**Пример:**

```json
{
  "type": "indicator",
  "title": "Связь",
  "variableName": "status",
  "valueField": "online",
  "trueLabel": "Online",
  "falseLabel": "Offline"
}
```

---

### переключатель — Переключатель

**Назначение:** запись bool-переменной (доступной для записи) по клику оператора.

**Оператор:** щелчок переключает значение размера.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** Writable bool |
| `trueLabel` / `falseLabel` | Подписи ON/OFF |

---

### график — График / тренд

**Назначение:** история переменных (линия/площадь/столбец и др.) или многопараметрических режимов `bubble` / `radar`.

**Оператор:** просмотр; переключите переключатель на виджете (если он включен в пользовательский интерфейс).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно** для линии/площади/бара/диапазона/свечи. На переменном нужен `historyEnabled: true` |
| `chartStyle` | `line` \| `area` (не для bubble/radar) |
| `chartType` | `line`, `area`, `bar`, `candlestick`, `bubble`, `radar`, `range` |
| `historyRange` | `live` (скользящее окно), `1h`, `6h`, `24h`, `7d`, `all` |
| `maxPoints` | Макс. точек (~120 по умолчанию) |
| `color` | Цвет линии |
| `decimals`, `unit`, `unitField` | Формат оси/подписи |
| `bubbleXVariable`, `bubbleYVariable` | **bubble:** траектория X/Y по live/history трендам |
| `bubbleSizeVariable`, `bubbleDefaultSize` | **bubble:** размер маркера (Z); default 80 |
| `bubblePointsJson` | **bubble:** snapshot JSON `[{label,xVariable,yVariable,sizeVariable?}]` — перекрывает trajectory |
| `radarAxesJson` | **radar:** JSON `[{label,variableName,valueField?,max?}]`, минимум 3 оси |

**Ограничения пузырька/радара:** траектория zip по индексу точек (без слияния по временной метке); радар — только последний снимок, а не история.

**Пример:**

```json
{
  "type": "chart",
  "title": "Температура",
  "w": 8, "h": 4,
  "selectionKey": "device",
  "variableName": "temperature",
  "historyRange": "24h",
  "chartStyle": "area",
  "decimals": 1,
  "unit": "°C"
}
```

---

###спарклайн — Спарклайн

**Назначение:** компактный мини-тренд без осей (в карточке метрики).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** + `historyEnabled` |
| `historyRange`, `maxPoints`, `color`, `decimals` | Как у `chart` |

---

### манометр — Радиальная шкала

**Назначение:** значение по шкале мин–макс.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** Текущее значение |
| `minValue` / `maxValue` | Константы границ |
| `minVariable` / `maxVariable` | Или границы из других переменных того же объекта |
| `unit`, `decimals` | Подпись и точность |

**Ошибка:** задать только `minValue` без `maxValue` — шкала не отрисуется.

---

###liner-gale — Линейная шкала

**Назначение:** горизонтальная шкала; поля как у `gauge`.

---

### жидкостный манометр — Манометр «Жидкий»

**Назначение:** вертикальный уровень «жидкости»; поля как у `gauge` (`minValue`/`maxValue` или переменные).

---

###прогресс — Прогресс-бар

**Назначение:** отношение к текущему значению к максимальной (загрузке, выполнению).

| Поле | Описание |
|------|----------|
| `currentVariable` | **Обязательно.** Не `variableName`! |
| `maxVariable` | **Обязательно.** |
| `unit`, `decimals` | Подпись и точность |

**Пример:**

```json
{
  "type": "progress",
  "title": "Выполнение",
  "objectPath": "root.platform.orders.o-1",
  "currentVariable": "doneQty",
  "maxVariable": "orderQty",
  "unit": "шт"
}
```

---

### status-badge — Значок воздействия

**Назначение:** компактный текстовый/цветной статус (водитель, фаза).

| Поле | Описание |
|------|----------|
| `variableName` | По умолчанию `status` |
| `valueField` | По умолчанию `value` |

---

### Круговая диаграмма — Круговая диаграмма

**Назначение:** доли из `RECORD_LIST` (несколько строк = секторы).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** RECORD_LIST |
| `labelField` | Поле подписи сектора |
| `valueField` | Поле значения (если не `value`) |
| `decimals` | Точность |

---

### History-table — Таблица истории (5 мин)

**Назначение:** последние образцы переменные + среднее за окно.

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** + `historyEnabled` |
| `valueField`, `decimals` | Формат |

---

### таймер — Таймер

**Назначение:** обратное отсчёт или прошедшее время.

| Поле | Описание |
|------|----------|
| `mode` | `countdown` — от `durationSeconds`; `elapsed` — от timestamp в `variableName` |
| `durationSeconds` | Для countdown |
| `variableName` | Для elapsed — переменная со временем старта |

**Оператор:** обратный отсчет показывает оставшееся время; elapsed — сколько прошло с моментом в переменной.

---

###диаграмма Ганта — Диаграмма Ганта

**Назначение:** горизонтальные полосы из `RECORD_LIST` (задачи, интервалы).

| Поле | Описание |
|------|----------|
| `variableName` | **Обязательно.** RECORD_LIST |
| `labelField` | Подпись строки |
| `startField` / `endField` | Поля начала и конца (epoch ms, epoch s или числовая шкала) |
| `interactive` | Показатель панорамирования/масштабирования в режиме оператора (по умолчанию `true`; в редакторе отключено) |
| `allowBarDrag` | Перетаскивание полос при writable RECORD_LIST (по умолчанию `true`) |

**Оператор:** колёсико — масштабирование, перетаскивание по шкале — панорамирование, двойной клик — сбросить область просмотра; перетащите полосы — начало/конец перехода с сохранением через `setVariable`.

---

### network-graph — Граф сети

**Назначение:** узлы и устройства из двух функций RECORD_LIST.

| Поле | Описание |
|------|----------|
| `nodesVariable` | RECORD_LIST узлов |
| `edgesVariable` | RECORD_LIST рёбер |
| `labelField` | Подпись узла |

---

###svg-виджет — SVG-кнопка

**Назначение:** интерактивная SVG-иконка (лаборатория, имитация).

**Оператор:** нажатие вызывает функцию или переключает переменную.

| Поле | Описание |
|------|----------|
| `svgUrl` | **Обязательно.** URL SVG |
| `clickAction` | `function` \| `toggle` |
| `functionName` | При `function` |
| `toggleVariable` | При `toggle` |
| `confirmMessage` | Подтверждение перед действием |

---

### электронная таблица — Электронная таблица

**Назначение:** сетка A1 с формулами (встроенный движок ISPF).

**Полное описание:** [SPREADSHEET_WIDGET.md](spreadsheet-widget.md). Операторский сценарий — [OPERATOR_GUIDE.md](operator-guide.md).

| Поле | Описание |
|------|----------|
| `sheetMode` | `free` (по умолчанию) \| `configured` |
| `sheetConfigJson` | rows, cols, cells, filters, dataRegion |
| `persistMode` | `session` \| `variable` |
| `valuesVariable` | Переменная RECORD_LIST при `variable` persist |
| `sessionKey` | Ключ session (default `sheet:{id}`) |
| `editable` | `false` — только просмотр |

---

### mini-tec-sld — Однолинейная схема mini-TEC *(удалено)*

**Статус:** виджет удален. Используйте [`scada-mimic`](#scada-mimic--scada-мнемосхема) + объект `MIMIC` или встроенный `diagramJson`. См. [SCADA.md](scada.md).

---

### scada-mimic — SCADA мнемосхема

**Назначение:** конфигурируемая мнемосхема / P&ID / однолинейная схема с каталогом символов и live-привязками к переменным платформам.

**Оператор:** просмотреть состояние; панорамирование/масштабирование; клик по элементу с `actions` — управление (toggle/setVariable/vokeFunction).

| Поле | Описание |
|------|----------|
| `diagramJson` | JSON документа схемы (если не задан `mimicPath`) |
| `mimicPath` | Путь объекта `MIMIC` — diagram загружается с сервера |
| `defaultZoom` | Масштаб (default `1`) |
| `panEnabled` | Масштабирование колёсиком (default `true`) |
| `showGrid` | Сетка в preview (reserved) |

**Создание MIMIC:** Explorer → `root.platform.mimics` → «Создать мнемосхему».

**Редактор:** Dashboard Builder → «Открыть редактор мнемосхемы»; Проводник → объект `MIMIC`. Инструменты: выбор/размещение/подключение, **выравнивание и распределение**, переворот/поворот, **манипуляторы изменения размера**, интеллектуальная привязка, переключение сетки, множественный выбор (Shift), Del для удаления. Подробно — [SCADA.md § Редактор](scada.md).

**См.:** [SCADA.md](scada.md), [SCADA_MIMIC.md](scada-mimic.md).

---

## только для объектов — действия и формы

Функция ### — Функция вызова кнопки

**Назначение:** один клик → `POST .../functions/{name}/invoke`.

**Оператор:** нажать кнопку; при `confirmMessage` — подтвердить диалог.

| Поле | Описание |
|------|----------|
| `functionName` | **Обязательно.** |
| `buttonLabel` | Текст кнопки |
| `confirmMessage` | Диалог подтверждения |
| `inputJson` | Статические input rows для invoke |
| `workflowPath` | Альтернатива: запуск workflow вместо function |
| `objectPath` / `selectionKey` | Объект для invoke |

**Пример:**

```json
{
  "type": "function",
  "title": "Сброс",
  "objectPath": "root.platform.devices.demo-sensor-01",
  "functionName": "resetAlarm",
  "buttonLabel": "Сбросить alarm"
}
```

---

###-функция-форма — Форма → Функция

**Назначение:** ввод поля + кнопка → вызвать с аргументами из формы.

**Оператор:** заполнить поля → «Отправить»; Действия многошагового мастера.

| Поле | Описание |
|------|----------|
| `functionName` | **Обязательно.** |
| `fieldsJson` | Массив полей (см. ниже) |
| `buttonLabel` | Текст кнопки |
| `wizardStepsJson` | `[{ "id", "label" }]` — шаги мастера |
| `validateFunctionName` | BFF-валидация на «Далее» |
| `paramBindingsJson` | Поле формы ← session param |
| `requireSessionParamsJson` | Обязательные params перед submit |
| `syncFieldsToSessionJson` | Синхронизация полей в session |
| `clearSessionParamsJson` | Очистка params после успеха |
| `closeModalOnSuccess` | Закрыть модальное окно (default true в modal) |

**Поля `fieldsJson`:**

| Свойство | Описание |
|----------|----------|
| `name` | Имя аргумента функции |
| `label` | Подпись на форме |
| `type` | `text`, `number`, `select`, `multiselect`, `time`, `checkbox`, `textarea` |
| `optionsFrom` | parentPath — options = дети каталога |
| `optionsFromReport` | Options из SQL-отчёта |
| `staticOptions` | Фиксированный список |
| `defaultValue` | Значение по умолчанию |
| `required`, `hidden`, `hint` | UX |
| `step` | id шага wizard |
| `showWhenJson` | Условное отображение |
| `colSpan` | 1 = половина строки, 2 = полная |

**Пример (строка добавления лаборатории):**

```json
{
  "type": "function-form",
  "title": "Append row",
  "objectPath": "root.platform.devices.lab-userA-01",
  "functionName": "appendTableRow",
  "buttonLabel": "Append",
  "fieldsJson": "[{\"name\":\"int\",\"label\":\"Int\",\"type\":\"number\"},{\"name\":\"string\",\"label\":\"String\",\"type\":\"text\"}]"
}
```

---

### input-form — Форма записи в переменные

**Назначение:** оператор вводит значения → `setVariable` на объект (HMI-форма без функции).

| Поле | Описание |
|------|----------|
| `fieldsJson` | Поля формы |
| `buttonLabel` | Кнопка сохранения |

**Поля:** `name`, `label`, `type` (`text`, `number`, `textarea`, `select`, `slider`, `checkbox`, `radio`, `datetime`, `time`), `variableName` (куда писать), `optionsFrom`, `min`/`max`/`step`, `defaultValue`.

---

###variable-editor — Редактор запросов

**Назначение:** запросите список объектов со встроенным редактированием записываемых полей.

**Оператор:** изменить значение в таблице → сохранение на сервере.

| Поле | Описание |
|------|----------|
| `variablesJson` | JSON-массив имён; пусто = все переменные объекта |
| `objectPath` / `selectionKey` | Объект |

---

## родительский-каталог — управляемые объекты

### объект-таблица — Таблица объектов

**Назначение:** таблица **дочерних** объектов папки `parentPath`.

**Оператор:** просмотр; щелкните по строке → выбор (`selectionKey`) и/или перейдите на другую панель управления.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** Папка, не device |
| `columnsJson` | Колонки (см. ниже) |
| `selectionKey` | Публиковать `selection[key]=path` при клике |
| `namePattern` | Glob по имени (`gpu-*`) |
| `objectType` | Фильтр типа (`DEVICE`, …) |
| `rowTargetDashboard` | Открыть дашборд при клике |
| `rowOpenMode` | `navigate` \| `modal` |
| `rowSelectionKey` | Ключ selection в целевом дашборде |
| `rowParamsJson` | Доп. params при клике |

**Колонка `columnsJson`:**

```json
[
  { "variable": "sysName", "label": "Имя" },
  { "variable": "status", "label": "On", "field": "online", "trueLabel": "OK", "falseLabel": "—" },
  { "objectField": "displayName", "label": "Объект" }
]
```

| Свои колонки | Описание |
|------------------|----------|
| `variable` | Имя переменной на строке-объекте |
| `label` | Заголовок |
| `field` | Поле DataRecord (default `value`) |
| `objectField` | `displayName` или `path` без variable |

---

### card-grid — Карточки объектов

**Назначение:** плитки дочерних объектов с несколькими переменными на карте.

**Оператор:** клик по карточке → выбор/навигация.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** |
| `variablesJson` | `["temperature","status"]` — что показать |
| `cardTargetDashboard`, `cardOpenMode` | Навигация |
| `cardSelectionKey`, `cardParamsJson` | Контекст при клике |

---

### карта — Карта

**Назначение:** маркеры для каждого ребёнка `parentPath` (MapLibre + OSM).

**Оператор:** клик по маркеру → выбор / дашборд.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** |
| `latVariable` | Переменная с geo на child |
| `latField` / `lonField` | Поля координат (default `lat`/`lon`) |
| `labelVariable` | Подпись маркера |
| `zoom`, `centerLat`, `centerLon` | Вид карты |
| `tileUrl`, `mapStyleUrl`, `tileAttribution` | Кастомные тайлы |
| `rowTargetDashboard`, `rowOpenMode`, `rowSelectionKey`, `rowParamsJson` | Как у таблицы |

---

###дерево объектов — Дерево объектов

**Назначение:** иерархия под `parentPath` для навигации/выбора.

| Поле | Описание |
|------|----------|
| `parentPath` | **Обязательно.** |
| `selectionKey` | Выбор узла |
| `maxDepth` | Глубина обхода |

---

## внешний — платформа и навигация

### Dashboard-link — Переход на дашборд

**Назначение:** кнопка открывает другую дашборд (вкладку или модальную панель).

**Оператор:** нажать кнопку; при `requireSessionParamsJson` кнопка неактивна без нужного контекста.

| Поле | Описание |
|------|----------|
| `targetDashboardPath` | **Обязательно.** |
| `openMode` | `navigate` \| `modal` |
| `buttonLabel` | Текст кнопки |
| `modalTitle` | Заголовок modal |
| `confirmMessage` | Подтверждение |
| `contextSelectionJson` | Передать selection |
| `contextParamsJson` | Передать params |
| `requireSessionParamsJson` | JSON-массив обязательных keys |

---

### субпанель — Вложенный дашборд

**Назначение:** встроить другой макет внутри виджета.

| Поле | Описание |
|------|----------|
| `targetDashboardPath` | Статический путь |
| `targetDashboardPathKey` | Путь из `session.params[key]` |
| `inheritContext` | `true` — передать selection родителя |

---

###отчёт — SQL-отчёт

**Назначение:** таблица из платформы REPORT.

**Оператор:** просмотр; экспорт CSV/PDF/XLSX; опционально выбор строки.

| Поле | Описание |
|------|----------|
| `reportPath` | **Обязательно.** |
| `emptyMessage` | Текст при пустом результате |
| `parametersJson` | Статические параметры run |
| `contextParamsJson` | session param → report param |
| `showCsv`, `showPdf`, `showXlsx`, `showHtml` | Кнопки экспорта |
| `selectable` | Клик по строке → session |
| `rowSelectionKey`, `rowParamsFromRowJson` | Маппинг колонок в params |
| `autoSelectFirstRow` | Выбрать первую строку при загрузке |
| `statusDotColumnsJson` | Колонки со status-dot |

---

###лента событий — Лента событий

**Назначение:** последние события платформы (автоматизация журнала).

| Поле | Описание |
|------|----------|
| `objectPathPrefix` | Фильтр по префиксу пути |
| `eventNamesJson` | `["thresholdExceeded", …]` |
| `payloadFilterExpr` | Клиентский фильтр payload |
| `maxItems` | Лимит строк |

---

###work-queue — Очередь BPMN-задачи

**Назначение:** пользовательские задачи для оператора (заявить/выполнить).

**Оператор:** Заявка → Восстановление действия → Завершено.

| Поле | Описание |
|------|----------|
| `operatorId` | Фильтр по оператору |
| `operatorAppId` | Фильтр по operator app |
| `maxItems` | Лимит задач |

---

##сессия/статика — оформление

###этикетка — Метка

**Назначение:** статический или трогательный текст.

| Поле | Описание |
|------|----------|
| `text` | Фиксированный текст |
| `textJson` | JSON для шаблона |
| `paramKey` | Подставить `session.params[key]` |

---

###сухарики — Хлебные крошки

**Назначение:** путь из сеанса (выбор или параметры).

| Поле | Описание |
|------|----------|
| `pathKey` | Ключ в selection/params |
| `separator` | Разделитель (default `/`) |

---

### context-list — Контекст (отладка)

**Назначение:** показывает `selection` и `params` дашборда. Для разработки HMI.

---

### изображение — Изображение

| Поле | Описание |
|------|----------|
| `imageUrl` | URL картинки |
| `alt` | Alt-текст |

---

### html-фрагмент — HTML-фрагмент

| Поле | Описание |
|------|----------|
| `htmlJson` | HTML (осторожно с XSS — только доверенный контент) |

---

## композиция — экран композиции

### панель — Панель-контейнер

**Назначение:** группа вложенных виджетов с общим заголовком.

| Поле | Описание |
|------|----------|
| `childrenJson` | Массив виджетов |
| `collapsible` | Сворачиваемая панель |
| `variant` | `simple` |

---

###вкладка-панель — Вкладки

**Назначение:** несколько вкладок, каждый со своим набором виджетов.

| Поле | Описание |
|------|----------|
| `tabsJson` | `[{ "id", "label", "children": [ …widgets ] }]` |

**Оператор:** переключать вкладки кликом.

---

###панель-ящик — Выдвижная панель

| Поле | Описание |
|------|----------|
| `drawerLabel` | Подпись кнопки открытия |
| `childrenJson` | Содержимое drawer |

---

###карусель — Карусель

| Поле | Описание |
|------|----------|
| `slidesJson` | `[{ "title", "children": [ … ] }]` |
| `autoplayMs` | Автопрокрутка (0 = выкл) |

---

###ступенчатая панель — Пошаговая панель

| Поле | Описание |
|------|----------|
| `stepsJson` | `[{ "id", "label", "children": [ … ] }]` |
| `activeStepKey` | Активный шаг (или из session) |

---

###composite-widget — Композитный виджет

**Назначение:** плоский список вложенных виджетов без обёртки панели.

| Поле | Описание |
|------|----------|
| `childrenJson` | Массив виджетов |

---

### nav-menu — Меню навигации

**Назначение:** список ссылок на дашборды внутри экрана.

| Поле | Описание |
|------|----------|
| `itemsJson` | `[{ "label", "dashboardPath" }]` |

---

## Типичные ошибки

| Ошибка | Решение |
|--------|---------|
| `progress` с `variableName` | Использовать `currentVariable` + `maxVariable` |
| `object-table` с `objectPath` | Нужен `parentPath` (папка) |
| Пустой `chart` | Включить `historyEnabled` на переменной |
| `gauge` без max | Задать `maxValue` или `maxVariable` |
| `pie-chart` не RECORD_LIST | Переменная должна быть таблицей строк |
| Колонка «—» | Имя переменной не соответствует модели |
| Два виджета с одним `selectionKey` | Нормально для детализации; разные ключи для разных выборов |
| `columnsJson` как объект в API | В layout — **escaped string** |

---

## Рекомендуемые размеры (ш × в)

| Виджет | Размер |
|--------|--------|
| значение, индикатор, переключатель | ш=2–4, ч=2 |
| диаграмма | ш=6–12, ч=4–6 |
| объект-таблица | ш=12, час=4–6 |
| функция-форма | ш=4–6, ч=4 |
| электронная таблица | ш=8–12, ч=5–8 |
| карта | ш=8–12, ч=6–8 |

---

## Исходники

| Что | Где |
|-----|-----|
| TypeScript типы | `apps/web-console/src/types/dashboard.ts` |
| View-компоненты | `apps/web-console/src/components/dashboard/widgets/` |
| Редактор свойств | `widgetEditorFields.tsx`, `WidgetEditorPanel` |
| Образцы из палитры | `widgetSamples.ts` |
| AI / agent spec | `AgentWidgetPropertiesGuide.java` |
