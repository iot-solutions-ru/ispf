> **Язык:** русская версия (вычитка). Канонический английский: [en/workflows.md](../en/workflows.md).

# Рабочий процесс и BPMN

## Обзор

Рабочий процесс — объект типа `WORKFLOW` с моделью `workflow-v1`. Движок: чистая Java в `ispf-plugin-workflow` (без Camunda/Flowable).

Переменные объект рабочего процесса:

| Переменная | Описание |
|------------|----------|
| `title` | Название |
| `status` | `DRAFT` / `ACTIVE` / `STOPPED` |
| `bpmnXml` | BPMN 2.0 XML |
| `triggerJson` | Триггер по изменению переменных |
| `operatorAppId` | Operator App для user task в sidebar |
| `instanceState` | JSON состояния последнего экземпляра |
| `lastRunAt` | Время последнего запуска |
| `lastAction` | Последнее действие service task |

## Жизненный цикл

**Объект рабочего процесса:**
- `DRAFT` — редактирование, запуск вручную
- `ACTIVE` — слушает триггеры + ручной run
- `STOPPED` — не запускается

**Instance (`InstanceStatus`):**
- `RUNNING` — выполняется
- `WAITING` — ожидает user task, signal catch или timer
- `COMPLETED` — завершён
- `FAILED` — ошибка

## Поддерживаемые элементы BPMN

| Элемент | Поддержка |
|---------|-----------|
| `startEvent` | Да |
| `endEvent` | Да |
| `serviceTask` | LOG, SET_VARIABLE, PUBLISH_NATS, **INVOKE_FUNCTION** |
| `userTask` | Очередь оператора, claim/complete |
| `intermediateCatchEvent` | Ожидание сигнала (`ispf:signal`) или таймера (`ispf:durationSeconds`) |
| `boundaryEvent` | Таймер на user task (`attachedToRef`, `ispf:durationSeconds`, `cancelActivity`) |
| `messageTask` | Публикация в NATS (если enabled) |
| `exclusiveGateway` | Условные переходы (CEL) |
| `parallelGateway` | Fork/join, execution tokens |
| `subProcess` | Встроенный подпроцесс — вход во внутреннее начало, выход во внутренний конец (заглушка BL-176) |
| `sequenceFlow` | `ispf:condition`, `ispf:default` |

Namespace расширений: `http://ispf.io/bpmn` (префикс `ispf:`).

## ISPF-атрибуты

### сервисная задача

```xml
<serviceTask id="log1" name="Log alarm"
             ispf:action="log"
             ispf:message="Threshold exceeded"/>
```

| действие | Параметры |
|--------|-----------|
| `log` | `ispf:message` |
| `set_variable` | `ispf:targetPath`, `ispf:variable`, `ispf:value` |
| `publish_nats` | `ispf:subject`, `ispf:message`, `ispf:channel` |
| `invoke_function` | `ispf:objectPath`, `ispf:functionName`, `ispf:inputMap`, `ispf:outputMap` |

Пример `invoke_function` (прикладные функции — [applications](applications.md)):

```xml
<serviceTask id="assign" name="Assign tank"
             ispf:action="invoke_function"
             ispf:objectPath="root.platform.devices.demo-sensor-01"
             ispf:functionName="myapp_acknowledge"
             ispf:inputMap="orderId=${workflow.orderId}"
             ispf:outputMap="assignResult=result"/>
```

### пользовательская задача

```xml
<userTask id="approve" name="Approve"
          ispf:title="Подтвердить аларм"
          ispf:instructions="Проверьте датчик"
          ispf:assigneeRole="operator"
          ispf:targetObjectPath="root.platform.devices.demo-sensor-01"
          ispf:function="acknowledgeAlarm"/>
```

### последовательность потоков

```xml
<sequenceFlow sourceRef="gw" targetRef="approve" ispf:condition="needsApproval"/>
<sequenceFlow sourceRef="gw" targetRef="end" ispf:default="true"/>
```

Условие — экземпляр рабочего процесса CEL в большей степени.

### задача сообщения

```xml
<messageTask id="notify" name="Notify"
             ispf:subject="ispf.ops.alarm"
             ispf:message="Alarm fired"
             ispf:channel="nats"/>
```

### промежуточныйCatchEvent (сигнал)

```xml
<intermediateCatchEvent id="waitIncident" name="Wait incident"
                        ispf:signal="incidentRegistered"/>
```

Экземпляр переходит в `WAITING` к доставке сигнала. Альтернатива отмене через API отмены — инцидент может «разбудить» процесс и продолжить ветку обработки.

### промежуточныйCatchEvent (таймер)

```xml
<intermediateCatchEvent id="waitDelay" name="Wait delay"
                        ispf:durationSeconds="300"/>
```

Экземпляр ждёт истечения срока; продолжение — `POST .../timer` (см. ниже) или планировщик.

### borderEvent (таймер пользовательской задачи)

```xml
<boundaryEvent id="ackTimeout" attachedToRef="operatorAck"
               cancelActivity="true" ispf:durationSeconds="300"/>
<sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
```

Пока пользовательская задача находится в `WAITING`, параллельно отсчитывается SLA. При реализации таймера достижения ветки эскалации (прерывания). Шаблон: [reference-escalation-templates](reference-escalation-templates.md).

## Рабочая очередь

User tasks попадают в `GET /api/v1/work-queue`.

### Привязка к приложению Оператора

На объекте рабочего процесса (модель `workflow-v1`) задаётся переменная **`operatorAppId`** — идентификатор приложения-оператора, в боковой панели «Задачи», который попадает в задачу пользователя в этом процессе.

- Настройка: редактор workflow → панель **Operator App** (или `PUT /api/v1/workflows/by-path/operator-app`)
- При создании user task значение копируется в `workflow_user_tasks.operator_app_id`
- Фильтр sidebar: `GET /api/v1/work-queue?operatorAppId=platform`

Если `operatorAppId` пусто — задача **не отображается** в боковой панели «Приложения оператора» (виджет `work-queue` на дашборде по-прежнему показывает все).

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

`triggerJson` на объекте рабочего процесса. `WorkflowTriggerListener` сравнивает изменения функций с условиями и запускает АКТИВНЫЙ рабочий процесс.

## Корреляторы событий → Рабочий процесс

Коррелятор с действием `RUN_WORKFLOW` и `actionTarget` = путь рабочего процесса. См. [automation](automation.md).

## Демонстрационный рабочий процесс

`root.platform.workflows.demo-alarm-handler` — BPMN с gateway, user task, service log.

Определение: `WorkflowDefinitions.DEMO_ALARM_HANDLER`.

## Пользовательский интерфейс

- **WorkflowBuilder** — статус, запуск, редактор BPMN (bpmn-js)
- **BpmnDiagramEditor** / **BpmnDiagramViewer** — custom moddle `ispf-moddle.json`

### Диаграмма без разметки (DI)

BPMN из движка или скриптов часто содержит только логику процесса, ** без раздела `bpmndi`** (Diagram Interchange).  
`bpmn-js` в этом случае выдает ошибку `no diagram to display`.

Веб-консоль автоматически вызывает **`bpmn-auto-layout`** (`src/bpmn/ensureDiagram.ts`) перед включением.  
Шаблон нового процесса (`EMPTY_BPMN` в `constants.ts`) уже включает минимальную разметку.

Если диаграмма не отображается: внешняя вкладка **Исходник**, убедитесь, что `bpmnXml` не пустой, сохраните — макет будет сгенерирован при следующем открытии.

## Отмена экземпляра

```http
POST /api/v1/workflows/instances/{instanceId}/cancel
Content-Type: application/json

{
  "reason": "incident",
  "detailJson": "{\"incidentId\":\"...\"}",
  "cancelledBy": "operator-1"
}
```

Экземпляр переводится в `FAILED`, запись пишется в `workflow_cancel_journal`.  
Роли: `operator`, `admin`.

## Сигнал доставки

```http
POST /api/v1/workflows/instances/{instanceId}/signal
Content-Type: application/json

{"signal":"incidentRegistered","operatorId":"operator-1"}
```

Broadcast ко всем `WAITING` экземплярам workflow, ожидающим этот сигнал:

```http
POST /api/v1/workflows/signal
Content-Type: application/json

{
  "workflowPath": "root.platform.workflows.signal-demo",
  "signal": "incidentRegistered",
  "operatorId": "operator-1"
}
```

## Срабатывание таймера

```http
POST /api/v1/workflows/instances/{instanceId}/timer
Content-Type: application/json

{"operatorId":"operator-1"}
```

Продолжается экземпляр, если таймер границы крайнего срока или отлов промежуточного таймера уже наступил.

## API

```http
GET  /api/v1/workflows/by-path?path=...
PUT  /api/v1/workflows/by-path/bpmn?path=...
PUT  /api/v1/workflows/by-path/status?path=...   body: { "status": "ACTIVE" }
POST /api/v1/workflows/by-path/run?path=...
POST /api/v1/workflows/instances/{instanceId}/cancel
POST /api/v1/workflows/instances/{instanceId}/signal
POST /api/v1/workflows/instances/{instanceId}/timer
POST /api/v1/workflows/signal
```

## Персистентность

Таблицы: `workflow_instances`, `workflow_user_tasks` (Flyway V2).

## Тесты

`WorkflowEngineTest`, `WorkflowEngineV2Test`, `WorkflowEngineV3Test`, `WorkflowEngineSignalTest`, `WorkflowEngineTimerTest`, `EscalationTemplateSmokeTest`, `BpmnParserTest`, `WorkflowApiTest`, `WorkflowSignalApiTest`, `WorkQueueApiTest`.
