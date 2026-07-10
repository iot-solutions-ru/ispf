> **Язык:** русская версия (вычитка). Канонический английский: [en/getting-started.md](../en/getting-started.md).

# Быстрый старт

## Требования

| Компонент | Версия |
|-----------|--------|
| JDK | 21+ |
| Gradle | Wrapper в репозитории |
| Node.js | 20+ |
| Docker Desktop | Для инфраструктуры (PostgreSQL, NATS, MQTT, Keycloak) |

## Быстрый локальный цикл (dev & QA)

**Не начинайте** с `./gradlew test` или `syncAllDriverPacks`, если не меняете драйверы и не гоняете полную регрессию. Эти пути собирают **все ~58 driver packs** и сериализуют **1000+** тестов — на холодной машине часто **часы** ([issue #65](https://github.com/Michaael/IoT-Solutions-Platform/issues/65)).

### Рекомендуемый первый запуск (< 30 мин с тёплым кэшем)

```bash
# API (профиль local, только dev driver packs — virtual/mqtt/modbus/http/…)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# UI (hot reload, без production build)
cd apps/web-console && npm install && npm run dev
```

`bootRun` и `:packages:ispf-server:test` по умолчанию вызывают **`syncDevDriverPacks`** (8 packs). Полный каталог: `-Dispf.driver.packs=all` → `syncAllDriverPacks`.

### Проверка перед push (как CI pr-fast)

```bash
# Linux/macOS
./tools/ci/pr-fast.sh

# Windows
.\tools\ci\pr-fast.ps1
```

Эквивалент backend в Gradle:

```bash
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev
```

Web console: `cd apps/web-console && npm test && npm run i18n:check && npm run build`.

### Точечная проверка (< 2 мин)

Только core/platform — **без driver packs**:

```bash
./gradlew :packages:ispf-core:test --tests com.ispf.core.model.DataRecordTest
```

Интеграционный тест сервера:

```bash
./gradlew :packages:ispf-server:test --tests com.ispf.server.alert.AlertRuleLatchTest \
  -Dispf.test.skipLoad=true
```

### Когда нужен полный pipeline

| Цель | Команда |
|------|---------|
| Все driver packs (как prod, работа над драйвером) | `./gradlew syncAllDriverPacks` или `-Dispf.driver.packs=all` |
| PR-fast backend tier (Gradle task) | `./gradlew testPrFast -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev` |
| Nightly backend tier (load + federation отдельно) | `./tools/ci/nightly.sh` или `./gradlew testNightlyBackend -Dispf.test.skipLoad=true -Dispf.driver.packs=dev` |
| Полная backend-регрессия (CI nightly) | `./gradlew :packages:ispf-server:test` (без `skipLoad`) |
| Всё | `./gradlew build` (медленно — не для ежедневной итерации) |

**Уровни тестов (issue #65):** PR-fast пропускает `@Tag("load")` и `@Tag("federation")`; nightly гоняет их явно (`tools/ci/nightly.sh`, [ci-nightly.yml](../../.github/workflows/ci-nightly.yml)). Тесты подпроектов локально **параллельны** (глобальный `mustRunAfter` снят); сериализация по желанию: `-Dispf.test.serializeSubprojects=true`. CI кэширует `build/driver-packs` (`ISPF_DRIVER_PACKS_PREBUILT=true` при cache hit).

Опционально: скопируйте [gradle.properties.example](../../gradle.properties.example) для большего числа Gradle workers.

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

## 2. Driver packs

Драйверы протоколов **не встроены** в `ispf-server.jar`. Для локальной разработки **`syncDevDriverPacks` вызывается автоматически** перед `bootRun` и тестами сервера (virtual, mqtt, modbus, http, cwmp, flexible, gps-tracker, application).

Ручная синхронизация при необходимости:

```bash
./gradlew syncDevDriverPacks          # по умолчанию — быстрый local QA
./gradlew syncAllDriverPacks          # все packs — разработка драйверов / prod deploy
```

Каталог по умолчанию: `./data/drivers` (или `ISPF_DRIVER_PACKS_DIR`).  
Gradle использует `build/driver-packs` после sync.

Скопируйте packs на сервер:

```bash
cp -r build/driver-packs/* /opt/ispf/data/drivers/
```

Подробно: [licensed-driver-packs](licensed-driver-packs.md).

## 3. Запуск API-сервера

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

**Избегайте** `./gradlew test` в корне репозитория в ежедневной работе — запускаются все subprojects и полный server test suite.

```bash
./tools/ci/pr-fast.sh    # рекомендуется перед push (см. § Быстрый локальный цикл)
```

Интеграционные тесты сервера используют профиль `test` (H2 in-memory, RBAC отключён). Load/scale gates: без `-Dispf.test.skipLoad=true` (nightly CI).

## Профили Spring

| Профиль | БД | Auth | MQTT/NATS |
|---------|-----|------|-----------|
| *(default)* | PostgreSQL | JWT Keycloak | выкл. |
| `local` | H2 file | `X-ISPF-Role` | выкл. |
| `dev` | PostgreSQL | JWT Keycloak localhost:8180 | вкл. |
| `test` | H2 memory | выкл. | выкл. |

Подробнее: [deployment](deployment.md), [security](security.md).

## Следующие шаги

- [Модель объектов](object-model.md) — как устроены пути, переменные и привязки
- [api](api.md) — полный справочник
- [Дашборды](dashboards.md) — создание HMI-экранов
