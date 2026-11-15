> **Язык:** русская версия. Английский оригинал: [en/deployment.md](../en/deployment.md).

# Развёртывание и конфигурация сервера

> **Статус:** Stable — запуск в Docker Compose, переменные окружения, профили БД, Kubernetes, кластер и обновление сервера. Метки зрелости: [doc-status.md](doc-status.md).

## 1. Инфраструктура в Docker Compose

Для локальной разработки и стендов базовые сервисы запускаются одной командой:

```bash
docker compose up -d
```

| Сервис | Образ Docker | Порты | Учётные данные по умолчанию |
|--------|--------------|-------|-----------------------------|
| `postgres` | `timescale/timescaledb:latest-pg16` | `5432` | `ispf` / `ispf` |
| `redis` | `redis:7-alpine` | `6379` | — |
| `nats` | `nats:2.10-alpine` (`-js`) | `4222`, `8222` | — |
| `mosquitto` | `eclipse-mosquitto:2` | `1883` | конфигурация: `deploy/mosquitto/` |
| `keycloak` | `keycloak:26.0` (режим `dev`) | `8180` | `admin` / `admin` |

Том данных PostgreSQL: `ispf_pg_data`. Все сервисы из `docker-compose.yml` стартуют вместе без дополнительных профилей Compose.

---

## 2. Сервер Spring Boot (`ispf-server`)

Запуск из исходников: `./gradlew :packages:ispf-server:bootRun` или готовым JAR-файлом из `packages/ispf-server/build/libs/`.

### Основные переменные окружения

| Переменная | По умолчанию | Назначение |
|------------|--------------|------------|
| `ISPF_DB_URL` | `jdbc:postgresql://localhost:5432/ispf` | JDBC-строка подключения к основной БД |
| `ISPF_DB_USER` | `ispf` | Пользователь БД |
| `ISPF_DB_PASSWORD` | `ispf` | Пароль БД |
| `ISPF_SERVER_PORT` | `8080` | HTTP-порт сервера |
| `ISPF_OAUTH_ISSUER` | URL пространства Keycloak | Издатель JWT-токенов (OIDC Issuer) |
| `ISPF_MQTT_ENABLED` | `false` | Включение встроенной интеграции с брокером MQTT |
| `ISPF_MQTT_BROKER` | `tcp://localhost:1883` | Адрес брокера MQTT |
| `ISPF_NATS_ENABLED` | `false` | Включение шины NATS (межрепликовый обмен и задачи BPMN) |
| `ISPF_NATS_URL` | `nats://localhost:4222` | Адрес сервера NATS |
| `ISPF_BOOTSTRAP_FIXTURES_ENABLED` | `true` | Загрузка демонстрационных шаблонов и тестовых устройств (`mqtt-sensor-v1` и др., см. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md)). На боевых серверах обязательно выставляйте `false`. |

### Профили запуска Spring

| Профиль | Файл настроек | Когда используется |
|---------|---------------|--------------------|
| `default` | `application.yml` | Промышленная эксплуатация: PostgreSQL + проверка JWT (Keycloak) |
| `local` | `application-local.yml` | Локальная разработка и быстрый старт: файловая БД H2, вход по токену Bearer после `POST /api/v1/auth/login` (отладочный заголовок `X-ISPF-Role` по умолчанию выключен) |
| `dev` | `application-dev.yml` | Разработка с полным окружением (PostgreSQL + MQTT + NATS) |
| `prod` | `application-prod.yml` | Боевой профиль: `ispf.security.strict-startup=true` по умолчанию |
| `test` | `application-test.yml` | Автоматические тесты (БД H2 в оперативной памяти) |

#### Отгруженные entrypoint и профили

Compose **зависимостей** (`docker compose up` в корне) ISPF не стартует. Какой профиль грузит **сервер**:

| Entrypoint | Типичный Spring-профиль | `strict-startup` |
|------------|-------------------------|------------------|
| `docker run` GHCR / `Dockerfile` | `local` | Выкл. (guard пропущен в `local`) |
| `deploy/docker-compose.vps-single.yml`, `vps-cluster.yml`, `edge-arm.yml` | `local` | Выкл. (guard пропущен в `local`) |
| Helm `deploy/helm/ispf` defaults | `local` | Выкл. (guard пропущен в `local`) |
| `deploy/docker-compose.cluster.yml`, `analytics.yml`, `prod-stack.yml` | `dev` | Выкл. (только warn) |
| `deploy/docker-compose.air-gap.yml` | `prod` | **Вкл.** |

`StartupSecurityGuard` полностью пропускает `local` и `test` (без warn, env-флаг игнорируется). Перед сетевой экспозицией уйдите с `local` на `--spring.profiles.active=prod`, либо задайте `ISPF_SECURITY_STRICT_STARTUP=true` на профиле **вне** `local`/`test` — [security](security.md).

### База данных и режимы хранения

- **Миграции схемы (Flyway)** выполняются автоматически при старте сервера (`ddl-auto: validate`); набор скриптов выбирается по диалекту СУБД (`RelationalDialect`, см. [ADR-0037](decisions/0037-relational-core-portability.md)).
- **Профиль `local`:** файловая БД H2 в каталоге `./data/ispf-local` (в режиме совместимости с PostgreSQL).
- **Промышленный режим (`prod`):** PostgreSQL с расширением **TimescaleDB** (гипертаблицы `variable_samples` и `event_history`, автоматическая очистка старых записей по сроку хранения — см. [ADR-0009](decisions/0009-timescaledb-retention.md) и [ADR-0015](decisions/0015-event-history-timescale.md)).

| Режим хранения | Метаданные и дерево (`ISPF_DB_*`) | Журнал событий | История переменных |
|----------------|-----------------------------------|----------------|--------------------|
| **Единая БД (по умолчанию)** | PostgreSQL | `ISPF_EVENT_JOURNAL_STORE=jdbc` | `ISPF_VARIABLE_HISTORY_STORE=jdbc` |
| **Выделенное хранилище телеметрии** | PostgreSQL | `clickhouse` или `cassandra` | `clickhouse` или `cassandra` |

Для большинства внедрений достаточно **одной базы PostgreSQL** (в ней хранятся и дерево объектов, и история переменных, и журнал событий). Выносить историю и события в ClickHouse или ScyllaDB/Cassandra имеет смысл только при очень высоком потоке телеметрии (десятки тысяч значений в секунду).

При необходимости тип реляционной СУБД метаданных задаётся переменной `ISPF_METADATA_DB_KIND` (`postgresql`, `h2`, `mssql` — см. [совместимость СУБД](storage-portability-inventory.md)). Переход на другую СУБД метаданных выполняется развёртыванием чистой базы и импортом пакетов `bundle.json`.

**Источники данных для отчётов и приложений (`root.platform.data-sources.*`):**
- `internal` — изолированная схема внутри основной БД `ISPF_DB`;
- `external` — внешнее JDBC-подключение для SQL-отчётов и табличных выборок приложения.

### Брокеры сообщений

| Брокер | Параметр включения | Назначение |
|--------|--------------------|------------|
| **MQTT** | `ispf.mqtt.enabled=true` | Приём телеметрии и команды драйверов устройств |
| **NATS** | `ispf.nats.enabled=true` | Синхронизация реплик кластера и шаги `messageTask` в процессах BPMN |

---

## 3. Сборка веб-консоли (`apps/web-console`)

Сборка статических файлов для раздачи через nginx или другой обратный прокси:

```bash
cd apps/web-console && npm run build
```

Готовый каталог `dist/` раздаётся веб-сервером с проксированием `/api/` и `/ws/` на `ispf-server`. Для локальной отладки интерфейса используйте `npm run dev` (порт `5173`).

---

## 4. Быстрый запуск локального предпромышленного стенда (`prod-quickstart.sh`)

На Linux-хосте с установленным Docker, JDK 25 и Node.js 20+ одна команда собирает сервер и консоль из исходников и поднимает стек в контейнерах (PostgreSQL + Redis + `ispf-server` + nginx):

```bash
bash deploy/prod-quickstart.sh
```

> **Внимание:** этот скрипт предназначен для локальной проверки и лабораторного стенда. Порты привязаны к `127.0.0.1`, демонстрационные устройства отключены, но перед открытием доступа из внешней сети обязательно смените пароли БД (`ispf/ispf`) и настройте TLS. Подробнее о боевых профилях: [Профили развёртывания](demostands.md).

Что делает скрипт:
1. Собирает `ispf-server.jar` (`bootJar` без прогона тестов).
2. Собирает фронтенд `apps/web-console` (`npm ci && npm run build`).
3. Копирует JAR в `deploy/staging/ispf-server.jar`.
4. Запускает стек `deploy/docker-compose.prod-stack.yml`.
5. Дожидается готовности сервера через `deploy/health-check.sh`.

| Точка доступа | URL |
|---------------|-----|
| Сведения о сервере (API) | `http://127.0.0.1:8080/api/v1/info` |
| Проверка состояния (Actuator) | `http://127.0.0.1:8080/actuator/health` (не публикуется наружу через nginx) |
| Веб-консоль (через nginx) | `http://127.0.0.1:8088/` |

Остановка стенда (данные в томе `ispf_prod_pg` сохраняются; для полной очистки добавьте ключ `-v`):

```bash
docker compose -f deploy/docker-compose.prod-stack.yml down
```

| Файл | Назначение |
|------|------------|
| `deploy/docker-compose.prod-stack.yml` | Контейнеры `postgres`, `redis`, `ispf-server` (Temurin JRE + смонтированный JAR), `nginx` |
| `deploy/air-gap-images.env` | Зафиксированные версии базовых образов Docker |
| `deploy/nginx-local-prod.conf` | Конфигурация nginx: статика SPA и проксирование `/api/` и `/ws/` (без `/actuator/`) |
| `deploy/health-check.sh` | Скрипт проверки `/actuator/health` и `/api/v1/info` |

---

## 5. Развёртывание в Kubernetes (Helm-чарт)

Helm-чарт находится в каталоге [`deploy/helm/ispf`](../../deploy/helm/ispf/) (требуется Helm 3.14+).

```bash
# Проверка шаблонов чарта без подключения к кластеру
bash deploy/helm/ispf/validate.sh

# Установка в кластер Kubernetes
helm upgrade --install ispf deploy/helm/ispf \
  --namespace ispf --create-namespace \
  --set secrets.dbPassword='change-me' \
  --set ispf.bootstrap.fixturesEnabled=false

# Проверка запуска подов
kubectl -n ispf rollout status deploy/ispf --timeout=180s
kubectl -n ispf port-forward svc/ispf 8080:80 &
curl -sf http://127.0.0.1:8080/actuator/health
curl -sf http://127.0.0.1:8080/api/v1/info
```

| Файл чарта | Назначение |
|------------|------------|
| [`deploy/helm/ispf/Chart.yaml`](../../deploy/helm/ispf/Chart.yaml) | Паспорт чарта |
| [`deploy/helm/ispf/values.yaml`](../../deploy/helm/ispf/values.yaml) | Параметры по умолчанию (уровни архива историй, реплики, настройки периферийных узлов) |
| [`deploy/helm/ispf/validate.sh`](../../deploy/helm/ispf/validate.sh) | Скрипт проверки `helm lint` + `helm template` |
| [`deploy/helm/ispf/README.md`](../../deploy/helm/ispf/README.md) | Краткая памятка по параметрам чарта |

Для периферийных узлов ARM64 в Kubernetes предусмотрены параметры `edge.enabled=true` и `edge.arm64=true` (для одноплатных компьютеров и шлюзов с Docker Compose см. [`deploy/edge/arm64/README.md`](../../deploy/edge/arm64/README.md)).

---

## 6. Установка в закрытом контуре без интернета (air-gap)

Для цеховых сетей и ДМЗ без выхода в интернет предусмотрена сборка автономного архива (серверный JAR, веб-консоль, пакеты драйверов и образы Docker):

```bash
# На машине со связью: собрать автономный архив
bash deploy/air-gap-pack.sh --version 0.9.32

# На изолированном сервере: развернуть из архива
bash deploy/air-gap-apply.sh /path/to/ispf-airgap-0.9.32.tar.gz
```

Подробная инструкция: **[Установка без интернета (air-gap)](air-gap-deployment.md)**.

---

## 7. Проверка цифровой подписи пакетов решений (`bundle.json`)

Коммерческие пакеты решений могут содержать блок лицензии `license` с RSA-подписью. По умолчанию подпись не обязательна; чтобы запретить установку неподписанных или повреждённых пакетов на боевом сервере, задайте следующие настройки:

| Переменная окружения / свойство | По умолчанию | Описание |
|---------------------------------|--------------|----------|
| `ISPF_LICENSE_PUBLIC_KEY_PEM` / `ispf.license.public-key-pem` | пусто | Открытый ключ (или несколько ключей PEM для плавной ротации) |
| `ISPF_LICENSE_ENFORCE` / `ispf.license.enforce` | `false` | При `true` недействительная лицензия отклоняется с кодом HTTP `403` |
| `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES` / `ispf.license.require-signed-bundles` | `false` | При `true` пакет без блока `license` или с неверной подписью отклоняется с кодом `403` |

| Состояние пакета при установке | `require-signed-bundles=false` | `require-signed-bundles=true` |
|--------------------------------|--------------------------------|-------------------------------|
| Секция `license` отсутствует | Разрешено | Отказ (`HTTP 403`) |
| Подпись `license` корректна | Разрешено | Разрешено |
| Подпись повреждена или неверна | Предупреждение в лог (если `enforce=false`) или `403` | Отказ (`HTTP 403`) |

Подробнее: **[Лицензирование коммерческих пакетов](commercial-licensing.md)**.

---

## 8. Промышленный кластер из нескольких реплик

```text
                    ┌─────────────┐
   Клиенты ────────►│    nginx    │  балансировка REST + привязка сессий WebSocket
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ispf-server-1   ispf-server-2   ispf-server-N
           │               │               │
           └───────────────┼───────────────┘
                           ▼
              PostgreSQL + Redis + NATS JetStream
```

- Все реплики `ispf-server` работают с общим деревом объектов в PostgreSQL ([ADR-0028](decisions/0028-horizontal-active-active-cluster.md)).
- Каждое устройство опрашивается ровно одной репликой благодаря распределённым блокировкам в таблице `platform_driver_locks`.
- Фоновые планировщики используют лидерские блокировки в таблице `platform_leader_locks`.

Подробное устройство кластера, профили узлов (`edge-api`, `io`, `analytics`, `compute`, `hmi-read`) и порядок поэтапного запуска описаны в разделе **[Кластер ISPF](cluster.md)**.

### Быстрый запуск локального кластера (3 реплики в Docker)

```bash
bash deploy/cluster-quickstart.sh
# Веб-консоль и API за балансировщиком nginx: http://127.0.0.1:8088/
```

Файлы конфигурации: [`deploy/docker-compose.cluster.yml`](../../deploy/docker-compose.cluster.yml) и [`deploy/nginx-cluster.conf`](../../deploy/nginx-cluster.conf).

### Обязательные переменные окружения для каждой реплики кластера

| Переменная | Пример значения | Назначение |
|------------|-----------------|------------|
| `ISPF_REPLICA_ID` | `replica-1` | Уникальный идентификатор узла (отображается в `/api/v1/info`) |
| `ISPF_DB_URL` | `jdbc:postgresql://postgres:5432/ispf` | Общая база данных PostgreSQL для всех узлов |
| `ISPF_CLUSTER_ENABLED` | `true` | Включает распределение драйверов между узлами и API диагностики кластера |
| `ISPF_NATS_ENABLED` | `true` | Включает обмен событиями между репликами через NATS |
| `ISPF_NATS_REPLICA_EVENTS` | `true` | Синхронизация изменений дерева и WebSocket между репликами |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC` | `true` | Зеркалирование текущих значений телеметрии в памяти всех реплик через NATS ([ADR-0029](decisions/0029-cluster-live-variable-replica-sync.md)) |
| `ISPF_CLUSTER_PATH_INTEREST` | `true` | Учёт открытых подписок WebSocket по всему кластеру через Redis |
| `ISPF_CLUSTER_LIVE_VARIABLE_SYNC_COALESCE_MS` | `500` | Интервал укрупнения пакетов при рассылке значений между репликами по NATS |
| `ISPF_REDIS_ENABLED` | `true` | Включает Redis (окна корреляторов, кэш прав ACL и учёт подписок кластера) |

Дополнительные параметры блокировок драйверов: `ISPF_CLUSTER_DRIVER_LOCK_TTL_SECONDS` (по умолчанию `30` с) и `ISPF_CLUSTER_DRIVER_LOCK_RENEW_MS` (по умолчанию `10000` мс).

### Регламент обслуживания узлов кластера

**Добавление нового узла:**
1. Скопируйте блок сервиса `ispf-server-N` в файле Compose и задайте ему уникальный `ISPF_REPLICA_ID`.
2. Добавьте строку `server ispf-server-N:8080` в блоки `upstream ispf_backend` и `ispf_ws_backend` конфигурации nginx.
3. Запустите контейнер и перечитайте конфигурацию nginx: `docker compose -f deploy/docker-compose.cluster.yml up -d ispf-server-N nginx`.
4. Убедитесь, что новый узел появился в `GET /api/v1/platform/cluster/health`.

**Плановый вывод узла:**
1. Остановите контейнер `docker stop ispf-server-N` — nginx автоматически перестанет направлять на него запросы после `max_fails`.
2. По истечении TTL блокировки устройств с остановленного узла автоматически перейдут на оставшиеся реплики (`DriverOwnershipScheduler`).
3. Удалите узел из Compose и конфигурации nginx.

**Проверка отказоустойчивости после развёртывания:**
- [ ] На всех узлах включён `ISPF_CLUSTER_ENABLED=true` и задан уникальный `ISPF_REPLICA_ID`.
- [ ] Все узлы подключены к одной БД PostgreSQL, к NATS (`ISPF_NATS_REPLICA_EVENTS=true`) и к Redis.
- [ ] В nginx для REST API настроен `round-robin` или `least_conn`, а для `/ws/` — `ip_hash`.
- [ ] Метод `GET /api/v1/platform/cluster/health` показывает статус `UP` для всех реплик и список захваченных устройств.
- [ ] При остановке одной реплики запросы через балансировщик продолжают отвечать `200 OK`, а опрос её устройств подхватывается соседним узлом.
- [ ] Автоматическая проверка кластера: `bash deploy/cluster-smoke-test.sh --config-sync --live-var-lag`.

---

## 9. Эксплуатация на выделенном Linux-сервере (VPS)

Выбор профиля под размер сервера описан в документе **[Профили развёртывания](demostands.md)**, а краткая памятка администратора одноузлового сервера — в **[Обслуживании демостенда VPS](vps-demostand.md)**.

### Скрипты обслуживания одноузлового сервера

| Скрипт | Назначение |
|--------|------------|
| [`deploy/vps-single-rollout.sh`](../../deploy/vps-single-rollout.sh) | Выкладка новой версии JAR и веб-консоли на одноузловой сервер |
| [`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh) | Применение экономного профиля `prod-idle` с пересозданием контейнера (`recreate`, а не `docker restart`) |
| [`deploy/vps-demostand-tune-drivers.sh`](../../deploy/vps-demostand-tune-drivers.sh) | Перевод фоновых драйверов стенда в режим `TELEMETRY_ONLY` |
| [`deploy/vps-idle-thread-sample.py`](../../deploy/vps-idle-thread-sample.py) | Снятие среза потоков JVM по SSH для диагностики нагрузки на CPU |
| [`deploy/vps-deploy-direct.ps1`](../../deploy/vps-deploy-direct.ps1) | Сборка на рабочей станции Windows и отправка артефактов на сервер по SCP |

Пример обновления с рабочей станции:

```powershell
.\deploy\vps-deploy-direct.ps1 -Version <версия> -SkipTests -SkipDriverPacks
```

Команды на самом сервере после загрузки файлов:

```bash
bash /opt/ispf/bin/vps-single-rollout.sh /opt/ispf/staging/<версия>
curl -sf http://127.0.0.1:8080/api/v1/info
```

> Не используйте скрипт `apply-platform-update.sh` на сервере, где `ispf-server` запущен внутри Docker-контейнера: этот скрипт предназначен только для установки под управлением `systemd`.

### Скрипты обслуживания многоузлового кластера на VPS

| Скрипт | Назначение |
|--------|------------|
| [`deploy/vps-deploy-direct.ps1 -Cluster`](../../deploy/vps-deploy-direct.ps1) | Сборка и отправка JAR + UI для кластера |
| [`deploy/vps-cluster-rollout.sh`](../../deploy/vps-cluster-rollout.sh) | Поочерёдный перезапуск реплик кластера после загрузки артефактов |
| [`deploy/vps-cluster-bootstrap.sh`](../../deploy/vps-cluster-bootstrap.sh) | Первичное развёртывание кластера с нуля |
| [`deploy/vps-cluster-factory-reset.sh`](../../deploy/vps-cluster-factory-reset.sh) | Полный сброс БД PostgreSQL и очистка Scylla/Redis на стенде |
| [`deploy/vps-cluster-verify.sh`](../../deploy/vps-cluster-verify.sh) | Проверка состояния узлов и синхронизации конфигурации (`--config-sync`) |

---

## 10. Подключение ClickHouse, мониторинг и логирование

### Архив истории переменных и событий в ClickHouse
Пошаговая инструкция по переводу журнала событий и истории переменных на ClickHouse (включая режим параллельной записи `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true` и фоновую докачку) приведена в **[Руководстве по ClickHouse](clickhouse-prod-playbook.md)** и разделе **[Уровни хранения истории](historian-tiers.md)**.

### Точки мониторинга (Spring Boot Actuator)
- `/actuator/health` — проверка работоспособности процесса (liveness / readiness);
- `/actuator/prometheus` — метрики в формате Prometheus (требуется роль `admin`);
- `/actuator/metrics` — обзор метрик Micrometer.

Встроенная диагностика нагрузки в веб-консоли (**Система → Метрики → Диагностика нагрузки**) описана в разделе **[Мониторинг и диагностика](observability.md)**.

### Уровень логирования

```yaml
logging.level.com.ispf: INFO   # промышленный режим (по умолчанию)
logging.level.com.ispf: DEBUG  # подробный вывод для отладки
```

---

## 11. Установка без Docker (`systemd` + `nginx` или единый JAR)

### Запуск одним файлом (All-in-one JAR)
Релизная сборка (`-PembedWebConsole=true`) включает веб-консоль внутрь `ispf-server.jar`. На любой машине с JDK 25+ (Windows, Linux, macOS) сервер запускается одной командой:

```bash
java -jar ispf-server.jar --spring.profiles.active=local
```

После старта веб-консоль и API доступны по адресу `http://localhost:8080`.

### Установка как службы `systemd` на Linux
Скрипт `deploy/remote-setup-ispf.sh` настраивает чистый сервер Ubuntu 24.04+: устанавливает Temurin JDK 25, раскладывает `ispf-server.jar` и статику веб-консоли в `/opt/ispf`, регистрирует службу `ispf-server.service` (порт `8080`) и настраивает `nginx` на порту `80`.

```bash
./gradlew :packages:ispf-server:bootJar
cd apps/web-console && npm run build
scp packages/ispf-server/build/libs/ispf-server-*.jar user@host:/tmp/ispf-server.jar
scp -r apps/web-console/dist user@host:/tmp/ispf-web-console-dist
scp deploy/remote-setup-ispf.sh deploy/snmpd-ispf.conf user@host:/tmp/
ssh user@host 'bash /tmp/remote-setup-ispf.sh'
```

### Автоматическая проверка обновлений с GitHub Releases
Если в настройках службы включены `ISPF_UPDATE_CHECK_ENABLED=true` и `ISPF_UPDATE_APPLY_ENABLED=true`, консоль администратора проверяет выход новых релизов и показывает кнопку **«Обновить и перезапустить»** (вызывает `deploy/apply-platform-update.sh`). На локальных машинах разработчиков `apply-enabled` по умолчанию выключен.

---

## 12. Ориентиры для промышленной эксплуатации SCADA (на примере мини-ТЭЦ)

| Требование эксплуатации | Как реализуется в ISPF |
|-------------------------|------------------------|
| Доступность 99,9 % | Кластер из нескольких реплик `ispf-server` за балансировщиком nginx; см. [Кластер ISPF](cluster.md) |
| До 50 одновременных операторов | Общий кэш сессий и подписок в Redis, привязка WebSocket-сессий в nginx |
| Глубина архива до 7 лет | Политики хранения TimescaleDB + выгрузка в ClickHouse и Parquet ([Уровни хранения истории](historian-tiers.md)) |
| Резервное копирование | Регулярный `pg_dump` и снимки томов; автономный регламент в [air-gap-deployment.md](air-gap-deployment.md) |
| Безопасность сессий | Единый вход через Keycloak (OIDC), настройка времени неактивности и двухфакторной аутентификации ([Безопасность](security.md)) |
| Оповещения по Email и SMS | Настройка релея `ISPF_NOTIFICATIONS_EMAIL_RELAY_URL` и вызовов webhook в правилах тревог ([Автоматизация](automation.md)) |
| Опрос полевого оборудования | Пакеты промышленных драйверов (`opcua`, `modbus-tcp`, `iec104` и др.); см. [разбор примера мини-ТЭЦ](reference-mini-tec-walkthrough.md) |
