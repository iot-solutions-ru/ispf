> **Язык:** русская версия (вычитка). Канонический английский: [en/glossary.md](../en/glossary.md).

# Глоссарий ISPF

Краткий словарь терминов платформы. Обзор продукта: [product](product.md).

---

## Сквозной принцип

**Бизнес-логика в механизмах платформы** — архитектурный принцип ISPF: правила и поведение прикладного решения описываются **declarative-конфигурацией дерева объектов** (модели, переменные, события, функции, workflow, правила оповещений, корреляторы), а не отраслевым Java в `ispf-server`. Платформа реализует **generic-движки**; **bundle deploy** доставляет в них конфигурацию. См. [architecture](architecture.md).

---

## А

**Правило оповещения (alert rule)** — правило автоматизации: CEL-условие на переменной объекта. При истинности автоматически генерируется событие (`POST /events/fire`). Узел типа `ALERT` в `root.platform.alert-rules`.

**Приложение (deploy application)** — зарегистрированное прикладное решение с изолированной SQL-схемой, JSON-функциями и опциональным bundle. Регистрация: `POST /applications`. В дереве — под `root.platform.applications`.

**Функция приложения (application function)** — функция, описанная JSON-скриптом (шаги `selectOne`, `update`, …). Выполняется в песочнице без Java в ядре.

---

## Б

**Привязка (binding)** — правило вычисления значения переменной (`BindingRule` в `@bindingRules`). Пересчёт через `BindingRuleEngine` по активаторам (локальным и межобъектным). Выражение — Google **CEL** или функция платформы. См. [bindings](bindings.md).

**BFF (Backend-for-Frontend)** — шлюз `POST /bff/invoke` для вызова функций приложения из UI. Профиль `ispf-operator-v1` — контракт legacy manifest.

**Blueprint** — см. **Модель**.

**Bundle** — ZIP-пакет приложения: manifest, SQL, функции, operator UI, отчёты. Деплой: `POST /applications/{id}/deploy`.

**BPMN** — Business Process Model and Notation. ISPF использует BPMN 2.0 XML с расширениями `http://ispf.io/bpmn`.

---

## В

**CEL (Common Expression Language)** — язык выражений Google. Используется в привязках, правилах оповещений, условиях шлюзов workflow.

**Claim** — действие оператора в work queue: закрепление user task за собой.

---

## Д

**Dashboard (дашборд)** — объект типа `DASHBOARD` с layout JSON (сетка виджетов). Создаётся в Dashboard Builder, отображается в operator HMI.

**DataRecord** — типизированная запись переменной объекта: `DataSchema` (поля) и значения.

**DeviceDriver** — SPI-интерфейс драйвера устройства. Реализации: mqtt, modbus, snmp, virtual, … (58 модулей).

**Driver runtime** — сервис опроса устройств. Старт/стоп: `POST /drivers/runtime/start|stop`.

---

## С

**Событие (event)** — типизированное уведомление от объекта. Дескриптор: имя, схема payload, уровень. Публикация: `POST /events/fire` или правило оповещения.

**Коррелятор событий (event correlator)** — правило: цепочка событий → запуск workflow. Узел `CORRELATOR` в `root.platform.correlators`.

**Explorer (проводник)** — панель свойств выбранного узла дерева в admin console.

---

## Ф

**Flyway** — миграции SQL платформы. Миграции приложений **не** через Flyway — только API `data/migrate`.

**Функция платформы (platform function)** — исполняемая функция на объекте платформы (не application function). Описывается в модели объекта.

---

## Ч

**HMI (Human-Machine Interface)** — операторский интерфейс. В ISPF — operator HMI на базе дашбордов.

---

## И

**Inspector (инспектор)** — панель деталей объекта: свойства, переменные, события, функции.

**Instance (экземпляр workflow)** — запущенный экземпляр BPMN-процесса. Статусы: `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`.

---

## Л

**Layout** — JSON-описание сетки виджетов дашборда. Хранится в переменной `layout` объекта `DASHBOARD`.

**Legacy manifest** — устаревший формат operator shell (`operatorManifest` в JSON). Заменён на `operatorUi` + дашборды платформы.

---

## М

**Модель (Model / BlueprintDefinition)** — шаблон объекта: переменные, события, функции, привязки. Типы: `RELATIVE`, `ABSOLUTE`, `INSTANCE`. `RELATIVE`-миксины применяются при создании только при непустом CEL (*условие применимости* / `suitabilityExpression`). Явное применение — `templateId` или API.

**Fixture-модель** — демо/лабораторная модель (`mqtt-sensor-v1`, `mqtt-gateway-v1`, …), регистрируется при `ispf.bootstrap.fixtures-enabled=true`. Не входит в встроенный реестр. См. [0018-fixture-models-and-cel-applicability](decisions/0018-fixture-models-and-cel-applicability.md).

**Model engine** — плагин `ispf-plugin-blueprint`, применяющий модели к объектам.

---

## О

**Object tree (дерево объектов)** — иерархия узлов платформы с dot-path адресацией (`root.platform.devices.sensor-01`).

**ObjectType** — тип узла: `DEVICE`, `DASHBOARD`, `WORKFLOW`, `ALERT`, `CORRELATOR`, `MODEL`, `APPLICATION`, `USER`, `PLATFORM`, `ALERT_RULES`, … Системные папки имеют семантический тип, не `CUSTOM`.

**Operator app** — конфигурация operator UI для конкретного приложения. Хранится в `operator_app_ui`, редактируется в `root.platform.operator-apps`.

**Operator HMI** — режим Web Console для операторов: дашборды read-only, work queue, журнал событий.

**Operator UI** — JSON: заголовок, список дашбордов, дашборд по умолчанию. Загрузка: `GET /operator-apps/{id}/ui`.

---

## П

**Объект платформы (platform object)** — узел дерева объектов ISPF (в отличие от записи в таблице `applications`).

**Plugin** — расширение платформы. В репозитории: `ispf-plugin-blueprint`, `ispf-plugin-workflow`. Коммерческие — вне `main`.

---

## Р

**RBAC** — Role-Based Access Control. Роли: `admin`, `operator`.

**REQ-PF** — требования к application platform layer. Статус: [roadmap.md § Часть A](roadmap.md).

---

## С

**selectionKey** — имя слота динамического выбора объекта на дашборде. Виджет с `selectionKey: "device"` читает путь из `selection.device`, заданный кликом по строке таблицы.

**Service task** — элемент BPMN: автоматическое действие (LOG, SET_VARIABLE, INVOKE_FUNCTION, PUBLISH_NATS).

**SPI (Service Provider Interface)** — контракт расширения. Пример: `DeviceDriver` для драйверов.

---

## П (user task)

**User task** — элемент BPMN: задача для оператора. Появляется в work queue до claim/complete.

---

## В

**Переменная (variable)** — именованное значение на объекте. Типизация через `DataRecord`. Может иметь CEL-привязку.

**Virtual driver** — драйвер-симулятор. Генерирует тестовые данные (например, синусоидальную температуру для `demo-sensor-01`).

---

## W

**Web Console** — React-приложение admin + operator UI. Каталог: `apps/web-console/`.

**WebSocket** — `WS /ws/objects` — live-обновления переменных и событий.

**Widget (виджет)** — элемент дашборда: value, chart, object-table, spreadsheet, work-queue, … Справочник: [widgets](widgets.md).

**Work queue (рабочая очередь)** — очередь BPMN user tasks для операторов.

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
