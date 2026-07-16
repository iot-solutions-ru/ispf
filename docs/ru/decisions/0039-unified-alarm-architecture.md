> **Язык:** русская версия (вычитка). Канонический английский: [../../en/decisions/0039-unified-alarm-architecture.md](../../en/decisions/0039-unified-alarm-architecture.md).

# ADR-0039: Эволюция alert rule

## Статус

**Предлагается** (2026-07-09)

## Контекст

Поведение тревог в ISPF разнесено по **alert rules** (`ALERT` / `alert-rule-v1`), корреляторам, bindings, workflow, alarm shelf и process program. Зрелые SCADA-стеки объединяют триггеры, экземпляры, эскалацию и уведомления в одном определении; в ISPF это пока разбито по нескольким механизмам.

Нужна та же **семантика** без второй модели. В ISPF автоматизация уже живёт в **дереве + intrinsic blueprint** ([automation](../automation.md)). Отдельный `alarm-v2`, JSON `@alarmDefinition` или новый `ObjectType` раздвоили бы Explorer, bundle deploy, MCP `configure_alert` и `AutomationTreeService`.

**Process program (BL-172)** — управление, не эволюция alert rule.

## Решение

**Расширять существующий alert rule целиком**: тот же `ObjectType.ALERT`, blueprint **`alert-rule-v1`**, каталог `root.platform.alert-rules`, API `/api/v1/alert-rules`. Новое поведение — **опциональные поля blueprint** + доработка `AlertRule` / `AlertRuleService` / `AlertRuleListener`. Пустые поля = поведение как сегодня.

- Не вводить новое имя blueprint.
- Не заменять правило JSON-блобом.
- Корреляторы и BPMN — отдельно.

### Существующие поля (без переименования)

`targetObjectPath`, `watchVariable`, **`conditionExpr`** (условие **активации**), `eventName`, `payloadVariable`, `enabled`, `edgeTrigger`, `delaySeconds`, `sustainWhileTrue`, `rateLimitSeconds`, `priority`, `ackRequired`, уведомления, `anomalyModelId`, runtime-поля.

### Фаза B — latch переменной

Новые поля на **`alert-rule-v1`**:

| Поле | Назначение |
|------|------------|
| `deactivateExpr` | CEL снятия тревоги |
| `deactivateDelaySeconds` | Задержка перед снятием |
| `pollIntervalMs` | Период опроса (0 = только по изменению `watchVariable`) |
| `triggerMessage` | Текст причины в payload |
| `clearEventName` | Событие снятия (опц.) |

Движок: расширить `AlertRuleRuntimeStore` (latch active/clear), периодический индекс по аналогии с binding periodic.

### Фаза C — экземпляры и ack

Таблица **`platform_alert_instances`** с ключом **`alertRulePath`** (не новая модель узла).

Runtime на узле правила: `activeInstanceCount`, `pendingInstanceCount`, `escalated`.

### Фаза D — event-триггер на том же blueprint

`triggerKind`: `VARIABLE` | `EVENT`; при `EVENT` — `watchEventName`, `eventFilterExpr`, пара deactivate-событий, порог count/window. Отдельный listener на `EVENT_FIRED`, тот же узел `ALERT`.

### Фаза E — маска и продвинутое

`sourceObjectMask`, `recordKeyExpr`, flapping, **`alarmGroupId`** — группа sibling-правил как OR-триггеры без JSON-массива.

### Фаза F — эскалация полями правила

`escalationEnabled`, пороги count/time, webhook/workflow — сложный SLA по-прежнему в BPMN.

### Пример temperature > 80

См. [examples/alert-rule-evolution/](../../../examples/alert-rule-evolution/) — тот же `POST /api/v1/alert-rules`, дополнительные ключи.

### Вне scope

`alarm-v2`, `@alarmDefinition`, слияние CEP-коррелятора в alert rule, process program как опросчик.

## Связанные документы

- [automation](../automation.md), ADR [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md), [reference-escalation-templates](../reference-escalation-templates.md)
