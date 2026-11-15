> **Язык:** русская версия. Английский оригинал: [en/platform-logic.md](../en/platform-logic.md).

# Единая модель правил платформы (`КОГДА → ЕСЛИ → ТО`)

> **Статус:** Beta — единая модель правил для пересчёта переменных, управления состоянием экрана оператора (`@dashboardContext`) и публикации событий ([ADR-0019](decisions/0019-platform-rule-unification.md)). Метки зрелости: [doc-status.md](doc-status.md).

В ISPF правила пересчёта переменных, динамическое поведение экранов оператора и генерация событий строятся по одной и той же понятной схеме:

```text
КОГДА сработал активатор  →  ЕСЛИ выполнено условие (CEL)  →  ТО записать результат в цель (target)
```

---

## 1. Три вида цели правила (`target.kind`)

### 1. Запись в переменную объекта (`kind: "variable"`, по умолчанию)

Стандартное правило расчёта на узле логики или устройстве:

```json
{
  "id": "avg-temp",
  "enabled": true,
  "order": 10,
  "activators": {
    "onVariableChange": [{ "ref": "@/temperature" }]
  },
  "condition": "",
  "expression": "self.temperature.value",
  "target": {
    "kind": "variable",
    "variableName": "avgTemp",
    "field": "value"
  }
}
```

### 2. Запись в контекст экрана (`kind: "context"`)

Правило на объекте дашборда (`DASHBOARD`), управляющее параметрами экрана или видимостью виджетов через переменную `@dashboardContext`:

```json
{
  "id": "alarm-mode",
  "enabled": true,
  "order": 20,
  "activators": {
    "onVariableChange": [
      { "objectPath": "self", "variableName": "@dashboardContext" }
    ],
    "onContextChange": true
  },
  "condition": "context.selection.device != \"\" && read(context.selection.device + \"/temperature\") > 80.0",
  "expression": "\"alarm\"",
  "target": {
    "kind": "context",
    "path": "params.mode"
  }
}
```

### 3. Публикация события (`kind: "event"`)

Когда условие выполняется, правило формирует полезную нагрузку и публикует событие в журнал и шину автоматизации:

```json
{
  "id": "fire-alarm",
  "enabled": true,
  "order": 30,
  "activators": { "onContextChange": true },
  "condition": "context.params.mode == \"alarm\"",
  "expression": "{\"device\": context.selection.device, \"mode\": \"alarm\"}",
  "target": {
    "kind": "event",
    "eventName": "hmi.dashboard.alarm"
  }
}
```

---

## 2. Активаторы правила (`activators`)

| Поле активатора | Когда запускается расчёт |
|-----------------|--------------------------|
| `onStartup` | Один раз при запуске сервера или применении шаблона к узлу |
| `onVariableChange` | При изменении любой из указанных переменных |
| `onEvent` | При возникновении указанного события на объекте |
| `periodicMs` | Периодически по таймеру с заданным шагом в миллисекундах (см. [Правила расчёта](bindings.md)) |
| `onContextChange` | При изменении контекста экрана `@dashboardContext` (выбор строки в таблице, смена фильтра оператором) |

---

## 3. Структура контекста экрана (`@dashboardContext`)

На каждом объекте `DASHBOARD` (шаблон `dashboard-v1`) зарезервирована системная JSON-переменная `@dashboardContext`, синхронизируемая с сессией экрана оператора:

| Поле | Тип | Назначение |
|------|-----|------------|
| `selection` | `Record<string, string>` | Выбранные оператором объекты по ключам выбора (`device`, `order`, `line` и т.д.) |
| `params` | `Record<string, unknown>` | Параметры фильтрации, режимы отображения, аргументы отчётов |
| `widgets` | `Record<string, { visible?: boolean }>` | Динамическое управление видимостью отдельных виджетов по их `id` |
| `updatedAt`, `updatedBy` | Строки | Время и автор последнего изменения контекста |

- **Кто записывает в контекст:** таблицы объектов (`object-table`), сетки карточек (`card-grid`), карты (`map`), формы ввода (`function-form`), кнопки навигации (`dashboard-link`) и адресная строка браузера (`?ctx=`).
- **Кто читает из контекста:** виджеты с привязками `selectionKey`, `paramKey`, `contextPathKey`, а также правила видимости `widgets.<id>.visible`.

---

## 4. Пример: динамическая панель тревоги на экране мониторинга

На дашборде `root.platform.dashboards.snmp-host-monitoring` размещены таблица устройств (`selectionKey: "device"`) и скрытая по умолчанию панель деталей аварии (`id: "alarm-panel"`). Четыре простых правила на самом объекте дашборда управляют экраном без единой строчки кода внутри виджетов:

1. Когда температура выбранного в таблице устройства превышает `80.0` → записать `"alarm"` в `params.mode`.
2. Когда `context.params.mode == "alarm"` → включить видимость панели `widgets.alarm-panel.visible = true`.
3. Когда `context.params.mode == "normal"` → скрыть панель `widgets.alarm-panel.visible = false`.
4. При переходе в режим `"alarm"` → опубликовать событие `hmi.dashboard.alarm`.

**Связанные разделы:** [Правила расчёта (`bindings.md`)](bindings.md) · [Дашборды и виджеты (`dashboards.md`)](dashboards.md) · [ADR-0019](decisions/0019-platform-rule-unification.md).
