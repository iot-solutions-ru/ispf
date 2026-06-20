# Быстрый старт

## Требования

| Компонент | Версия |
|-----------|--------|
| JDK | 21+ |
| Gradle | Wrapper в репозитории |
| Node.js | 20+ |
| Docker Desktop | Для инфраструктуры (PostgreSQL, NATS, MQTT, Keycloak) |

## 1. Инфраструктура

```bash
docker compose up -d
```

Поднимаются сервисы:

| Сервис | Порт | Назначение |
|--------|------|------------|
| PostgreSQL (TimescaleDB) | 5432 | Основная БД `ispf` |
| Redis | 6379 | Кэш (зарезервировано) |
| NATS JetStream | 4222, 8222 | Messaging |
| Mosquitto | 1883 | MQTT broker |
| Keycloak | 8180 | OAuth2 (профиль `dev`) |

## 2. Запуск API-сервера

### Локальная разработка без OAuth (рекомендуется)

H2 file DB, RBAC через заголовок `X-ISPF-Role`:

```bash
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

Данные сохраняются в `./data/ispf-local.mv.db`.

### Разработка с полным стеком

PostgreSQL + Keycloak + MQTT + NATS:

```bash
docker compose up -d
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=dev"
```

### Проверка

```bash
curl http://localhost:8080/api/v1/info
curl http://localhost:8080/actuator/health
```

## 3. Web Console

```bash
cd apps/web-console
npm install
npm run dev
```

Консоль: http://localhost:5173

Vite проксирует `/api` и `/ws` на `http://localhost:8080`.

### Режим оператора (HMI)

http://localhost:5173?mode=operator

Открывает дашборд в режиме просмотра + боковую панель (work queue, журнал событий).

### Выбор роли (профиль `local`)

В шапке консоли — селектор **Роль**: `admin` или `operator`.  
Отправляется заголовок `X-ISPF-Role`.

## 4. Первые шаги в UI

1. Откройте дерево объектов слева — ветка `root.platform`.
2. Раскройте `devices` → `demo-sensor-01` — переменные температуры, порога, аларма.
3. Дважды кликните `dashboards.demo-sensor` — откроется **Dashboard Builder**.
4. Дважды кликните `workflows.demo-alarm-handler` — **Workflow Builder** с BPMN.
5. Раскройте `alert-rules` и `correlators` — правила автоматизации в дереве; inspector справа для редактирования.

## 5. Запуск драйвера устройства

Для `demo-sensor-01` virtual driver стартует автоматически при первом `start`:

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "X-ISPF-Role: admin"
```

Температура симулируется синусоидой; при превышении порога срабатывает binding `alarmActive` и может сработать alert rule.

## 6. Тесты

```bash
./gradlew test
```

Интеграционные тесты сервера используют профиль `test` (H2 in-memory, RBAC отключён).

## Профили Spring

| Профиль | БД | Auth | MQTT/NATS |
|---------|-----|------|-----------|
| *(default)* | PostgreSQL | JWT Keycloak | выкл. |
| `local` | H2 file | `X-ISPF-Role` | выкл. |
| `dev` | PostgreSQL | JWT Keycloak localhost:8180 | вкл. |
| `test` | H2 memory | выкл. | выкл. |

Подробнее: [DEPLOYMENT.md](DEPLOYMENT.md), [SECURITY.md](SECURITY.md).

## Следующие шаги

- [Модель объектов](OBJECT_MODEL.md) — как устроены пути, переменные и привязки
- [REST API](API.md) — полный справочник
- [Дашборды](DASHBOARDS.md) — создание HMI-экранов
