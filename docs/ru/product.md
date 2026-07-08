> **Язык:** русская версия (вычитка). Канонический английский: [en/product.md](../en/product.md).

﻿# ISPF — документация по продукту

**IoT Solutions Platform Framework (ISPF)** — промежуточное ПО-платформа для IoT, SCADA и промышленной автоматизации. Единая модель данных, HMI, автоматизация и уровень прикладных решений без отраслевого Java внутри.

Этот документ — **точка входа для всех ролей**. Технические детали реализации — в остальных разделах [docs/en/readme.md](readme.md).

---

## Для кого этот продукт

| Роль | Задачи | С чего начать |
|------|--------|---------------|
| **Оператор** | Мониторинг, управление, очередь работ, отчёты | [Руководство оператора](operator-guide.md) |
| **Администратор** | Дерево объектов, дашборды, рабочий процесс, пользователи | [Быстрый старт](getting-started.md) → [Веб-консоль](web-console.md) |
| **Разработчик решений** | Развертывание приложений, функций, пользовательского интерфейса оператора, отчёты | [Руководство разработчиков решений](solution-developer-guide.md) |
| **Разработчик платформы** | Драйверы, REQ-PF, расширение ядра | [Дорожная карта](roadmap.md) |
| **DevOps/SRE** | Развёртывание, профили, инфраструктура | [Развёртывание](deployment.md) |

---

## Что решает ISPF

Типичная SCADA/MES-система содержит следующие модули: OPC-сервер, архиватор, HMI, рабочий процесс, отчеты. ISPF объединяет их вокруг **одного дерева объектов** с единым API и пользовательским интерфейсом.

```mermaid
graph LR
    subgraph Sources["Источники данных"]
        DEV[Устройства / OPC / SNMP / MQTT]
        IT[HTTP / JDBC / Kafka]
    end

    subgraph ISPF["ISPF"]
        TREE[Дерево объектов]
        HMI[Дашборды HMI]
        WF[BPMN Workflow]
        APP[Прикладные функции]
    end

    subgraph Users["Пользователи"]
        OP[Оператор]
        ADM[Администратор]
    end

    DEV --> TREE
    IT --> TREE
    TREE --> HMI
    TREE --> WF
    TREE --> APP
    HMI --> OP
    WF --> OP
    APP --> OP
    HMI --> ADM
    WF --> ADM
```

### Основной принцип

**Бизнес-логика живёт на платформе** — в моделях, переменных, событиях, функциях и рабочих процессах **дерева объектов**. Платформа предоставляет generic-движки (CEL, привязки, BPMN, среда выполнения скриптов, драйверы); решение выполняет их декларативно-конфигурацию. Bundle Deploy — упаковка конфигурации, не зависящая от времени выполнения. Свод решения для разработчиков и агентов: [APPLICATION_PRINCIPLES.md](application-principles.md). Подробнее: [ARCHITECTURE.md](architecture.md). Следующая волна развития — [ROADMAP.md § Phase 5](roadmap.md) (модели, функции, события, рабочий процесс, пакет как упаковка дерева).

### Ключевые преимущества

- **Единая модель** — устройство, дашборд, рабочий процесс и правила оповещения — узлы одного дерева; логика решений выражается через их переменные, события, функции и BPMN.
- **Расширяемость без разветвления ядра** — отраслевые решения деплоятся как бандлы (модели, объекты, JSON-функции, BPMN, пользовательский интерфейс оператора) **в механизмах платформы**, без Java на сервере.
- **Облачный стек** — Spring Boot 4.0, Java 25, PostgreSQL/TimescaleDB, React 19, REST + WebSocket, опционально NATS/MQTT/Keycloak.
- **58 встроенных драйверов** — от Modbus и OPC UA до SNMP, Kafka и JDBC ([каталог](drivers.md)).
- **Apache 2.0 ядра** — коммерческие отраслевые пакеты — отдельно ([PLUGINS.md](plugins.md)).

---

## Возможности продукта

### 1. Древесные объекты

Центральная абстракция платформы. Каждый узел имеет слово (`root.platform.devices.pump-01`), тип, переменные, события и функции.

| Тип узла | Назначение |
|----------|------------|
| `PLATFORM`, `DEVICES`, `DASHBOARDS`, … | Системные каталоги (`root.platform.*`) |
| `DEVICE` | Физическое или виртуальное устройство с драйвером |
| `DASHBOARD` | HMI-экран (layout JSON + виджеты) |
| `WORKFLOW` | BPMN-процесс автоматизации |
| `ALERT` / `CORRELATOR` | Правила автоматизации (узлы в дереве) |
| `MODEL` | Шаблон (blueprint) для создания объектов |
| `APPLICATION` | Зарегистрированное deploy-приложение |
| `USER` / `ROLE` | Пользователи и роли (зеркало security API) |
| `CUSTOM` | Произвольный контейнер (fallback) |

Подробнее: [OBJECT_MODEL.md](object-model.md), [GLOSSARY.md](glossary.md).

### 2. Модели (шаблоны)

`BlueprintDefinition` описывает набор функций, событий, функций и CEL-привязок. ОТНОСИТЕЛЬНЫЕ миксины автоматически применяются только при заданном *Условии применимости* (CEL). Демо-модель `mqtt-sensor-v1` — приспособление, применяется через `templateId`.

Подробнее: [BLUEPRINTS.md](blueprints.md), [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

### 3. Драйверы устройства

SPI `DeviceDriver` подключает протоколы к переменному объекту. Администратор настраивает `driverId`, конфигурацию и отображение точек; среда выполнения опрашивает устройство и записывает значения в дереве.

Демо после первого запуска:

| Объект | Драйвер | Назначение |
|--------|---------|------------|
| `demo-sensor-01` | virtual | Синусоида температуры + alarm binding |
| `snmp-localhost` | snmp | SNMP-агент localhost |

Подробнее: [DRIVERS.md](drivers.md).

### 4. Дашборды и HMI

Dashboard Builder (администратор) и Операторский HMI (только для чтения) используют одни и те же виджеты:

| Категория | Виджеты |
|-----------|---------|
| Значения | `value`, `indicator`, `sparkline`, `chart`, `gauge` |
| Таблицы | `object-table`, `card-grid`, `work-queue` |
| Навигация | `dashboard-link` (переход между экранами) |
| SCADA | `scada-mimic` (мнемосхемы P&ID / однолинейные) |
| Прочее | `text`, `iframe`, `image`, `event-log`, `function-button` |

Связь виджетов с данными — через `objectPath` (статический) или `selectionKey` (динамический выбор строк таблицы).

Подробнее: [DASHBOARDS.md](dashboards.md), [SCADA.md](scada.md), справочник виджетов: [WIDGETS.md](widgets.md).

### 5. Рабочий процесс (BPMN)

Визуальный редактор BPMN в веб-консоли. Поддерживаются сервисные задачи (в т.ч. вызов прикладных функций), пользовательские задачи (очередь операторов), шлюзы с CEL-условиями, параллельные ветки, сигналы и NATS.

Подробнее: [WORKFLOWS.md](workflows.md).

### 6. Автоматизация

- **События** — типизированные уведомления с объектов; журнал + WebSocket.
- **Правила оповещений** — CEL-условие на переменную → автоматический пожар. Узлы `ALERT` в `root.platform.alert-rules`.
- **Event correlators** — цепочка событий → запуск workflow. Узлы `CORRELATOR` в `root.platform.correlators`.

Подробнее: [AUTOMATION.md](automation.md).

### 7. Прикладные решения (Платформа приложений)

Слой REQ-PF Позволяет разворачивать отраслевые приложения **без изменения ядра Java**:

| Этап | API |
|------|-----|
| Регистрация | `POST /applications` |
| Миграции SQL | `POST /applications/{id}/data/migrate` |
| Функции (JSON script) | `POST /applications/{id}/functions/deploy` |
| Bundle deploy | `POST /applications/{id}/deploy` |
| BFF для UI | `POST /bff/invoke` |
| Расписания | `GET/POST /schedules` |
| SQL-отчёты | `GET /applications/{id}/reports/{name}` |

Подробнее: [APPLICATIONS.md](applications.md), [REPORTS.md](reports.md).

### 8. Пользовательский интерфейс оператора

Оболочка оператора — полноэкранный HMI для операторов:

```
http://localhost:5173?mode=operator&app=<appId>
```

Конфигурация пользовательского интерфейса оператора хранится на расстоянии (`operator_app_ui`) и редактируется в админке → `root.platform.operator-apps`. Приоритет загрузки:

1. `GET /api/v1/operator-apps/{appId}/ui`
2. `GET /api/v1/applications/{appId}/operator-ui` (из bundle)
3. Legacy fallback `public/operator-apps/{appId}.ui.json`

Подробнее: [OPERATOR_GUIDE.md](operator-guide.md), [WEB_CONSOLE.md](web-console.md).

### 9. Безопасность

Два ролика: **admin** (полный доступ) и **operator** (просмотр, функции, рабочая очередь). Профиль `local` — Носитель-токен после входа в систему; профиль `dev`/prod — OAuth2 JWT через Keycloak.

Подробнее: [SECURITY.md](security.md).

---

## Режимы Веб-консоли

```mermaid
flowchart TD
    LOGIN[Вход в систему] --> ROLE{Роль?}
    ROLE -->|admin| ADMIN[Admin Console]
    ROLE -->|operator| OP[Operator HMI]
    ADMIN --> EXPLORER[Обозреватель объектов]
    ADMIN --> BUILDER[Dashboard / Workflow Builder]
    EXPLORER --> AUTO[Alert rules / Correlators в дереве]
    ADMIN --> OPBTN[Кнопка «Оператор · demo»]
    OPBTN --> OP
    OP --> DASH[Дашборды read-only]
    OP --> WQ[Work Queue]
    OP --> EJ[Журнал событий]
```

| Режим | URL-адрес | Кто видит |
|-------|-----|-----------|
| Admin | `http://localhost:5173` | admin (по умолчанию) |
| Operator HMI | `?mode=operator` | operator; admin по ссылке |
| Operator app | `?mode=operator&app=platform` | конкретное приложение |
| Admin явно | `?mode=admin` | admin даже с autostart |

---

## Типовые сценарии

### Сценарий 1: Датчик мониторинга

1. Администратор открывает `devices.demo-sensor-01` — видит температуру, порог, тревогу.
2. Дважды кликает `dashboards.demo-sensor` — редактирует HMI.
3. Оператор открывает `?mode=operator` — видит тот же дашборд без редактирования.
4. При превышении порога срабатывает правило оповещения → событие в журнале → опционально рабочий процесс.

### Сценарий 2: Обработка заявок оператором

1. Рабочий процесс BPMN содержит **задачу пользователя** «Подтвердить действие».
2. Задача открывается в **Рабочая очередь** на боковой панели оператора.
3. Оператор нажимает **Claim** → Выполнить → **Завершено**.
4. Рабочий процесс продолжается (служебная задача, шлюз и т. д.).

### Сценарий 3: Развёртывание отраслевого приложения

1. Разработчик регистрирует приложение (`POST /applications`).
2. Деплоит SQL-миграции и JSON-функции.
3. Загружает связку с `operatorUi` и отчётами.
4. Администратор создаёт приложение оператора в дереве → настраивает дашборды.
5. Операторы работают через `?mode=operator&app=my-terminal`.

Подробное пошаговое руководство: [SOLUTION_DEVELOPER_GUIDE.md](solution-developer-guide.md).

---

## Быстрый старт (5 минут)

```bash
# 1. API (H2 + local auth)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# 2. Web Console
cd apps/web-console && npm install && npm run dev
```

| URL-адрес | Назначение |
|-----|------------|
| http://localhost:5173 | Admin console (login: `admin` / `admin`) |
| http://localhost:5173?mode=operator | Operator HMI |
| http://localhost:8080/api/v1/info | Версия платформы |
| http://localhost:8080/actuator/health | Health check |

Полная инструкция: [GETTING_STARTED.md](getting-started.md).

---

## Архитектура (кратко)

```
Web Console (React)  ←→  REST / WebSocket  ←→  ispf-server (Spring Boot)
                                                      │
                    ObjectManager │ WorkflowService │ DriverRuntime
                    ApplicationPlatform │ EventService │ AlertRules
                                                      │
                    PostgreSQL/H2 │ Flyway │ NATS* │ MQTT*
```

Детали: [ARCHITECTURE.md](architecture.md).

---

## API

Базовый URL: `http://localhost:8080/api/v1`

| Группа | Примеры |
|--------|---------|
| Объекты | `GET /objects`, `PUT /objects/by-path/{path}/variables/{name}` |
| Дашборды | `GET /dashboards/by-path/{path}/layout` |
| Workflow | `POST /workflows/by-path/{path}/run` |
| Приложения | `POST /applications/{id}/deploy` |
| Operator apps | `GET /operator-apps/{id}/ui` |
| Драйверы | `POST /drivers/runtime/start?devicePath=...` |
| События | `GET /events`, `POST /events/fire` |

Полный справочник: [API.md](api.md).

---

## Лицензия и граница

| Компонент | Лицензия |
|-----------|----------|
| Ядро ISPF (`main`) | Apache 2.0 |
| Коммерческие плагины и app bundle | Отдельная лицензия, вне `main` |

Подробнее: [LICENSE.md](license.md), [PLUGINS.md](plugins.md).

---

## Карта документации

### Продуктовая документация

| Документ | Описание |
|----------|----------|
| **PRODUCT.md** (этот файл) | Обзор продукта, возможностей, сценариев |
| [OPERATOR_GUIDE.md](operator-guide.md) | Работа оператора с HMI |
| [SOLUTION_DEVELOPER_GUIDE.md](solution-developer-guide.md) | Создание прикладных решений |
| [ГЛОССАРИЙ.md](glossary.md) | Термины и определения |

### Техническая документация

| Документ | Описание |
|----------|----------|
| [GETTING_STARTED.md](getting-started.md) | Установка и первый запуск |
| [OBJECT_MODEL.md](object-model.md) | Дерево переменное, CEL |
| [DASHBOARDS.md](dashboards.md) | Планировка, подборКлюч, застройщик |
| [SCADA.md](scada.md) | Мнемосхемы, объекты MIMIC, редактор мнемосхем (выравнивание, распределение, переворот, изменение размера, интеллектуальная привязка) |
| [WIDGETS.md](widgets.md) | Справочник всех виджетов |
| [WORKFLOWS.md](workflows.md) | BPMN-движок |
| [APPLICATIONS.md](applications.md) | API развертывания REQ-PF |
| [DRIVERS.md](drivers.md) | Каталог драйверов |
| [SECURITY.md](security.md) | RBAC и аутентификация |
| [DEPLOYMENT.md](deployment.md) | Производство |

Полный индекс: [README.md](readme.md).
