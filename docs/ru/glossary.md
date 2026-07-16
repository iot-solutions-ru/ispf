> **Язык:** русская версия (вычитка). Канонический английский: [en/glossary.md](../en/glossary.md).

# Глоссарий ISPF

> **Статус:** Stable — Термины. Теги: [doc-status](doc-status.md).

Краткий словарь терминов платформы. Обзор продукта: [product](product.md).

---

## Сквозной принцип

**Бизнес-логика в механизмах платформы** — архитектурный принцип ISPF: правила и поведение приложения описываются декларативной конфигурацией **дерева объектов** (модели, переменные, события, функции, workflow, alert rules, correlators), а не отраслевым Java в `ispf-server`. Платформа реализует generic-движки; bundle deploy доставляет конфигурацию в эти механизмы. См. [architecture](architecture.md).

---

## A

**Alert rule** — правило автоматизации: CEL-условие на переменной объекта. При истинности событие публикуется автоматически. Узел `ALERT` в `root.platform.alert-rules`.

**Application (deploy application)** — зарегистрированное прикладное решение с изолированной SQL-схемой, JSON-функциями и опциональным bundle. Регистрация: `POST /applications`. В дереве — под `root.platform.applications`.

**Application function** — функция приложения как JSON-скрипт (шаги `selectOne`, `update`, …). Выполняется в песочнице без Java в ядре.

---

## B

**Binding** — правило вычисления значений переменных (`BindingRule` в `@bindingRules`). Пересчёт через `BindingRuleEngine` по активаторам (локальным и межобъектным). Выражение — Google **CEL** или platform function. См. [bindings](bindings.md).

**BFF (Backend-for-Frontend)** — шлюз `POST /bff/invoke` для вызова функций приложения из UI. Wire profile `ispf-operator-v1` — контракт для legacy manifest.

**Blueprint** — см. **Model**.

**Bundle** — JSON-манифест приложения (SQL migrations, functions, operator UI, reports, objects). Deploy: `POST /api/v1/applications/{id}/deploy` (`Content-Type: application/json`). Multipart ZIP upload не поддерживается.

**BPMN** — Business Process Model and Notation. Нотация для описания workflow. ISPF использует BPMN 2.0 XML с расширениями namespace `http://ispf.io/bpmn`.

---

## C

**CEL (Common Expression Language)** — язык выражений Google. Используется в bindings, alert rules и условиях workflow gateways. Полный справочник ISPF: [expression-language](expression-language.md).

**Claim** — действие оператора в work queue: закрепление user task за собой.

---

## D

**Dashboard** — объект типа `DASHBOARD` с layout JSON (сетка виджетов). Создаётся в Dashboard Builder, отображается в operator HMI.

**DataRecord** — типизированная запись на переменной объекта. Содержит `DataSchema` (поля) и значения.

**DeviceDriver** — SPI-интерфейс драйвера устройства. Реализации: mqtt, modbus, snmp, virtual, … (58 модулей).

**Driver runtime** — сервис опроса устройств. Start/stop: `POST /drivers/runtime/start|stop`.

---

## E

**Event** — типизированное уведомление от объекта. Имеет дескриптор (имя, схема payload, уровень). Публикация: `POST /events/fire` или alert rule.

**Event correlator** — правило: цепочка событий → старт workflow. Узел `CORRELATOR` в `root.platform.correlators`.

**Explorer** — панель свойств выбранного узла дерева в admin console.

---

## F

**Flyway** — инструмент SQL-миграций платформы. Миграции приложений **не** используют Flyway — только API `data/migrate`.

**Function (platform function)** — исполняемая функция на объекте платформы (не application function). Описывается в модели объекта.

---

## H

**HMI (Human-Machine Interface)** — интерфейс оператора. В ISPF — operator HMI на базе дашбордов.

---

## I

**Inspector** — панель деталей объекта: свойства, переменные, события, функции.

**Instance (workflow instance)** — запущенный экземпляр BPMN-процесса. Статусы: `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`.

---

## L

**Layout** — JSON-описание сетки виджетов дашборда. Хранится в переменной `layout` объекта `DASHBOARD`.

**Legacy manifest** — устаревший формат operator shell (`operatorManifest` в JSON). Заменён на `operatorUi` + дашборды платформы.

---

## M

**Model (BlueprintDefinition)** — шаблон объекта: переменные, события, функции, bindings. Типы: `RELATIVE`, `ABSOLUTE`, `INSTANCE`. RELATIVE-миксины auto-apply при создании только при непустом CEL (*Applicability condition* / `suitabilityExpression`). Явное применение — через `templateId` или API.

**Fixture model** — демо/лабораторная модель (`mqtt-sensor-v1`, `mqtt-gateway-v1`, …), регистрируется при `ispf.bootstrap.fixtures-enabled=true`. Не входит в core built-in registry. См. [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

**Model engine** — плагин `ispf-plugin-blueprint`, применяющий модели к объектам.

---

## O

**Object tree** — иерархия узлов платформы с dot-path адресацией (`root.platform.devices.sensor-01`).

**ObjectType** — тип узла: `DEVICE`, `DASHBOARD`, `WORKFLOW`, `ALERT`, `CORRELATOR`, `MODEL`, `APPLICATION`, `USER`, `PLATFORM`, `ALERT_RULES`, … Системные папки имеют семантический тип, не `CUSTOM`.

**Operator app** — конфигурация operator UI для конкретного приложения. Хранится в `operator_app_ui`, редактируется в `root.platform.operator-apps`.

**Operator HMI** — режим Web Console для операторов: read-only дашборды, work queue, журнал событий.

**Operator UI** — JSON-конфиг: title, список дашбордов, default dashboard. Загрузка: `GET /operator-apps/{id}/ui`.

---

## P

**Platform object** — узел дерева объектов ISPF (в отличие от записи приложения в таблице `applications`).

**Plugin** — расширение платформы. В репозитории: `ispf-plugin-blueprint`, `ispf-plugin-workflow`. Коммерческие плагины живут вне `main`.

---

## R

**RBAC** — Role-Based Access Control. Роли: `admin`, `operator`.

**REQ-PF** — требования к application platform layer. Статус: [roadmap.md § Part A](roadmap.md).

---

## S

**selectionKey** — имя слота динамического выбора объекта на дашборде. Виджет с `selectionKey: "device"` читает путь из `selection.device`, заданный кликом по строке таблицы.

**Service task** — элемент BPMN: автоматическое действие (LOG, SET_VARIABLE, INVOKE_FUNCTION, PUBLISH_NATS).

**SPI (Service Provider Interface)** — контракт расширения. Пример: `DeviceDriver` для драйверов.

---

## U

**User task** — элемент BPMN: задача для оператора. Появляется в Work Queue до claim/complete.

---

## V

**Variable** — именованное значение на объекте. Типизация через `DataRecord`. Может иметь CEL binding.

**Virtual driver** — драйвер-симулятор. Генерирует тестовые данные (например, синусоидальную температуру для `demo-sensor-01`).

---

## W

**Web Console** — React-приложение admin + operator UI. Каталог: `apps/web-console/`.

**WebSocket** — `WS /ws/objects` — live-обновления переменных и событий.

**Widget** — элемент дашборда: value, chart, object-table, spreadsheet, work-queue, … Справочник: [widgets](widgets.md).

**Work Queue** — очередь BPMN user tasks для операторов.

**Workflow** — объект типа `WORKFLOW` с BPMN XML. Статусы объекта: `DRAFT`, `ACTIVE`, `STOPPED`.

**Workflow engine** — чистый Java в `ispf-plugin-workflow` (без Camunda/Flowable).

---

## Сокращения

| Сокращение | Расшифровка |
|------------|-------------|
| ISPF | IoT Solutions Platform Framework |
| HMI | Human-Machine Interface |
| SCADA | Supervisory Control and Data Acquisition |
| MES | Manufacturing Execution System |
| OPC UA | Open Platform Communications Unified Architecture |
| SNMP | Simple Network Management Protocol |
| MQTT | Message Queuing Telemetry Transport |
| JWT | JSON Web Token |
| OIDC | OpenID Connect |
