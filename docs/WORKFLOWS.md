# Workflow и BPMN

## Обзор

Workflow — объект типа `WORKFLOW` с моделью `workflow-v1`. Движок: чистый Java в `ispf-plugin-workflow` (без Camunda/Flowable).

Переменные workflow-объекта:

| Переменная | Описание |
|------------|----------|
| `title` | Название |
| `status` | `DRAFT` / `ACTIVE` / `STOPPED` |
| `bpmnXml` | BPMN 2.0 XML |
| `triggerJson` | Триггер по изменению переменных |
| `instanceState` | JSON состояния последнего экземпляра |
| `lastRunAt` | Время последнего запуска |
| `lastAction` | Последнее действие service task |

## Жизненный цикл

**Workflow object:**
- `DRAFT` — редактирование, запуск вручную
- `ACTIVE` — слушает триггеры + ручной run
- `STOPPED` — не запускается

**Instance (`InstanceStatus`):**
- `RUNNING` — выполняется
- `WAITING` — ожидает user task
- `COMPLETED` — завершён
- `FAILED` — ошибка

## Поддерживаемые элементы BPMN

| Элемент | Поддержка |
|---------|-----------|
| `startEvent` | Да |
| `endEvent` | Да |
| `serviceTask` | LOG, SET_VARIABLE, PUBLISH_NATS |
| `userTask` | Очередь оператора, claim/complete |
| `messageTask` | Публикация в NATS (если enabled) |
| `exclusiveGateway` | Условные переходы (CEL) |
| `parallelGateway` | Fork/join, execution tokens |
| `sequenceFlow` | `ispf:condition`, `ispf:default` |

Namespace расширений: `http://ispf.io/bpmn` (префикс `ispf:`).

## ISPF-атрибуты

### serviceTask

```xml
<serviceTask id="log1" name="Log alarm"
             ispf:action="log"
             ispf:message="Threshold exceeded"/>
```

| action | Параметры |
|--------|-----------|
| `log` | `ispf:message` |
| `set_variable` | `ispf:targetPath`, `ispf:variable`, `ispf:value` |
| `publish_nats` | `ispf:subject`, `ispf:message`, `ispf:channel` |

### userTask

```xml
<userTask id="approve" name="Approve"
          ispf:title="Подтвердить аларм"
          ispf:instructions="Проверьте датчик"
          ispf:assigneeRole="operator"
          ispf:targetObjectPath="root.platform.devices.demo-sensor-01"
          ispf:function="acknowledgeAlarm"/>
```

### sequenceFlow

```xml
<sequenceFlow sourceRef="gw" targetRef="approve" ispf:condition="needsApproval"/>
<sequenceFlow sourceRef="gw" targetRef="end" ispf:default="true"/>
```

Условие — CEL в контексте переменных workflow instance.

### messageTask

```xml
<messageTask id="notify" name="Notify"
             ispf:subject="ispf.ops.alarm"
             ispf:message="Alarm fired"
             ispf:channel="nats"/>
```

## Work Queue

User tasks попадают в `GET /api/v1/work-queue`.

| Статус задачи | Действие |
|---------------|----------|
| `OPEN` | claim → `CLAIMED` |
| `CLAIMED` | complete → workflow продолжается |

```http
POST /api/v1/work-queue/claim?taskId=...&operatorId=operator
POST /api/v1/work-queue/complete?taskId=...&operatorId=operator
```

При `ispf:function` на user task — функция вызывается при complete.

## Триггеры

`triggerJson` на workflow-объекте. `WorkflowTriggerListener` сравнивает изменения переменных с условием и запускает ACTIVE workflow.

## Event Correlators → Workflow

Коррелятор с action `RUN_WORKFLOW` и `actionTarget` = path workflow. См. [AUTOMATION.md](AUTOMATION.md).

## Демо workflow

`root.platform.workflows.demo-alarm-handler` — BPMN с gateway, user task, service log.

Определение: `WorkflowDefinitions.DEMO_ALARM_HANDLER`.

## UI

- **WorkflowBuilder** — статус, run, редактор BPMN (bpmn-js)
- **BpmnDiagramEditor** / **BpmnDiagramViewer** — custom moddle `ispf-moddle.json`

## API

```http
GET  /api/v1/workflows/by-path?path=...
PUT  /api/v1/workflows/by-path/bpmn?path=...
PUT  /api/v1/workflows/by-path/status?path=...   body: { "status": "ACTIVE" }
POST /api/v1/workflows/by-path/run?path=...
```

## Персистентность

Таблицы: `workflow_instances`, `workflow_user_tasks` (Flyway V2).

## Тесты

`WorkflowEngineTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test`, `BpmnParserTest`, `WorkflowApiTest`, `WorkQueueApiTest`.
