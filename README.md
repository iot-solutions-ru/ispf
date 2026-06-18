# IoT Solutions Platform Framework (ISPF)

Современная IoT/SCADA платформа на cloud-native стеке 2026 года.

## Концепция

ISPF строится вокруг **иерархического дерева объектов** с типизированными переменными, событиями, функциями и вычисляемыми привязками:

| Концепция | Реализация |
|-----------|------------|
| Дерево объектов | `ObjectTree` / REST API |
| Типизированные данные | `DataRecord` + `DataSchema` |
| Вычисляемые привязки | Google CEL (`ispf-expression`) |
| Драйверы устройств | `DeviceDriver` SPI (MQTT, Modbus, …) |
| Хранение | PostgreSQL + TimescaleDB, Redis, NATS |
| UI | Spring Boot 3.4 + React 19 (Vite) |
| Интеграция | REST + WebSocket + (planned) gRPC |
| Масштабирование | Kubernetes + NATS JetStream |

## Структура монорепозитория

```
iot-solutions-platform-framework/
├── packages/
│   ├── ispf-core/           # Домен: объекты, DataRecord, шаблоны
│   ├── ispf-expression/     # CEL-движок и binding evaluator
│   ├── ispf-driver-api/     # SPI для драйверов устройств
│   ├── ispf-driver-mqtt/    # MQTT-драйвер (Eclipse Paho)
│   ├── ispf-driver-modbus/  # Modbus TCP-драйвер (j2mod)
│   ├── ispf-plugin-model/   # Models plugin — шаблоны объектов
│   └── ispf-server/         # Spring Boot API-сервер
├── apps/
│   └── web-console/         # React-консоль администратора
├── deploy/                  # Mosquitto, Helm (план)
├── docs/ARCHITECTURE.md
└── docker-compose.yml       # PostgreSQL, Redis, NATS, MQTT, Keycloak
```

## Быстрый старт

### Требования

- JDK 21+
- Docker Desktop (для инфраструктуры)
- Node.js 20+ (для web-console)

### 1. Инфраструктура

```bash
docker compose up -d
```

### 2. Сборка и запуск сервера

```bash
./gradlew :packages:ispf-server:bootRun --args='--spring.profiles.active=dev'
```

Сервер: http://localhost:8080  
Публичный endpoint: `GET /api/v1/info`  
Health: `GET /actuator/health`

### 3. Web-консоль администратора

```bash
cd apps/web-console
npm install
npm run dev
```

Консоль: http://localhost:5173

Возможности администрирования:
- Дерево объектов с раскрытием/поиском
- Просмотр и редактирование свойств объекта
- Таблица переменных с редактированием значений
- Создание и удаление объектов
- Просмотр событий объекта

> Для работы без OAuth запускайте API с `--spring.profiles.active=local`

### RBAC (роли admin / operator)

| Профиль | Механизм |
|---------|----------|
| `local` | Заголовок `X-ISPF-Role: admin` или `operator` (в консоли — селектор «Роль» в шапке) |
| `dev` | JWT Keycloak, realm `ispf`, роли `admin` и `operator` |

**operator** — work queue, журнал событий, вызов функций, чтение объектов/дашбордов.  
**admin** — полный доступ, включая alert rules и correlators.

Keycloak (docker compose, порт 8180): создайте realm `ispf`, клиент для web/API, realm roles `admin` и `operator`, назначьте пользователям.

`GET /api/v1/auth/me` — текущий principal и роли.

### 4. Тесты

```bash
./gradlew test
```

## API (v1)

| Method | Path | Описание |
|--------|------|----------|
| GET | `/api/v1/info` | Информация о платформе (публичный) |
| GET | `/api/v1/objects` | Список объектов |
| GET | `/api/v1/objects/by-path?path=...` | Объект по пути |
| POST | `/api/v1/objects` | Создать объект |
| GET | `/api/v1/objects/by-path/variables?path=...` | Переменные объекта |
| PUT | `/api/v1/objects/by-path/variables?path=...&name=...` | Записать переменную |
| GET | `/api/v1/drivers` | Список доступных драйверов устройств |
| GET | `/api/v1/dashboards/by-path?path=...` | Дашборд (layout + метаданные) |
| PUT | `/api/v1/dashboards/by-path/layout?path=...` | Сохранить layout JSON |
| GET | `/api/v1/workflows/by-path?path=...` | Workflow (BPMN + состояние) |
| PUT | `/api/v1/workflows/by-path/bpmn?path=...` | Сохранить BPMN XML |
| PUT | `/api/v1/workflows/by-path/status?path=...` | DRAFT / ACTIVE / STOPPED |
| POST | `/api/v1/workflows/by-path/run?path=...` | Запустить workflow |
| GET | `/api/v1/work-queue` | Очередь задач оператора |
| POST | `/api/v1/work-queue/claim?taskId=...` | Взять задачу |
| POST | `/api/v1/work-queue/complete?taskId=...` | Завершить user task |
| POST | `/api/v1/objects/by-path/functions/invoke` | Вызов функции объекта |
| GET | `/api/v1/events?objectPath=&limit=` | Журнал событий |
| POST | `/api/v1/events/fire` | Публикация события |
| GET | `/api/v1/alert-rules` | Правила алертов (CEL) |
| POST | `/api/v1/alert-rules` | Создать правило алерта |
| PUT | `/api/v1/alert-rules/{id}` | Обновить правило |
| DELETE | `/api/v1/alert-rules/{id}` | Удалить правило |
| GET | `/api/v1/correlators` | Корреляторы событий |
| POST | `/api/v1/correlators` | Создать коррелятор |
| PUT | `/api/v1/correlators/{id}` | Обновить коррелятор |
| DELETE | `/api/v1/correlators/{id}` | Удалить коррелятор |
| POST | `/api/v1/expressions/validate` | Валидация CEL-выражения |
| GET | `/api/v1/auth/me` | Текущий пользователь и роли |

> Защищённые endpoints требуют JWT (OAuth2 / Keycloak) или заголовок `X-ISPF-Role` в профиле `local`. Realm `ispf`: роли `admin`, `operator`.

## Models Plugin

Шаблоны структуры объектов (variables, events, bindings). Подробнее: [docs/MODELS.md](docs/MODELS.md)

## Дорожная карта

- [x] Персистентность объектов (JPA ↔ PostgreSQL/H2)
- [x] WebSocket live-updates переменных и событий
- [x] Драйвер Modbus TCP (`ispf-driver-modbus`)
- [x] Dashboard builder (low-code HMI) — drag-and-drop, графики/тренды
- [x] BPMN visual editor (bpmn-js) в Workflow Builder
- [x] Driver runtime + virtual simulator (стенд без железа)
- [x] Events: публикация + журнал (`event_history`)
- [x] Functions runtime (`acknowledgeAlarm` на demo-sensor)
- [x] Workflow v2 — gateway, userTask, условия, очередь оператора
- [x] Workflow v3 — messageTask, parallelGateway (fork/join)
- [x] Parallel branches with execution tokens (user tasks inside fork/join)
- [x] Operator HMI — режим `?mode=operator`, work queue, виджет «Функция»
- [x] Alert rules (CEL) + журнал событий в Operator HMI
- [x] Event correlators → workflow (окно, cooldown)
- [x] Admin UI: вкладка «Автоматизация» (alert rules + correlators)
- [x] RBAC: роли admin/operator (Keycloak + local header)
- [x] Admin UI: формы создания alert rules и correlators
- [ ] Драйверы: OPC UA, SNMP
- [ ] Edge Agent (Rust) с offline-sync
- [x] Workflow engine (BPMN / NATS) — MVP + user tasks
- [ ] Correlators: multi-event patterns (в планах — расширенные паттерны)
- [ ] Module marketplace
- [ ] Kubernetes Helm chart

## Лицензия

Proprietary — укажите лицензию по вашему выбору.
