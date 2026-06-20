# Развёртывание и конфигурация

## Docker Compose

```bash
docker compose up -d
```

| Сервис | Image | Порты | Credentials |
|--------|-------|-------|-------------|
| postgres | timescale/timescaledb:latest-pg16 | 5432 | ispf/ispf |
| redis | redis:7-alpine | 6379 | — |
| nats | nats:2.10-alpine (-js) | 4222, 8222 | — |
| mosquitto | eclipse-mosquitto:2 | 1883 | config: deploy/mosquitto/ |
| keycloak | keycloak:26.0 dev | 8180 | admin/admin |

Volume: `ispf_pg_data`.

Profiles Compose **не используются** — все сервисы стартуют вместе.

## Spring Boot Server

Артефакт: `:packages:ispf-server:bootRun` или JAR из `build/libs/`.

### Переменные окружения

| Переменная | Default | Описание |
|------------|---------|----------|
| `ISPF_DB_URL` | `jdbc:postgresql://localhost:5432/ispf` | JDBC URL |
| `ISPF_DB_USER` | ispf | DB user |
| `ISPF_DB_PASSWORD` | ispf | DB password |
| `ISPF_SERVER_PORT` | 8080 | HTTP port |
| `ISPF_OAUTH_ISSUER` | Keycloak realm URL | JWT issuer |
| `ISPF_MQTT_ENABLED` | false | MQTT integration |
| `ISPF_MQTT_BROKER` | tcp://localhost:1883 | Broker URL |
| `ISPF_NATS_ENABLED` | false | NATS integration |
| `ISPF_NATS_URL` | nats://localhost:4222 | NATS URL |

### Профили

| Profile | Файл | Сценарий |
|---------|------|----------|
| default | application.yml | PostgreSQL + JWT |
| local | application-local.yml | H2 file, X-ISPF-Role |
| dev | application-dev.yml | Full stack + MQTT/NATS |
| test | application-test.yml | H2 memory, tests |

### База данных

- **Flyway** — миграции при старте (`ddl-auto: validate`)
- **local:** H2 `./data/ispf-local` (PostgreSQL compatibility mode)
- **prod:** PostgreSQL; TimescaleDB extension (docker image `timescale/timescaledb`) — hypertable `variable_samples` и retention 90d настраиваются при старте (`TimescaleHypertableInitializer`)

### Messaging

| Broker | Включение | Использование |
|--------|-----------|---------------|
| MQTT | `ispf.mqtt.enabled=true` | Device drivers |
| NATS | `ispf.nats.enabled=true` | Workflow messageTask |

## Web Console

Static build:

```bash
cd apps/web-console && npm run build
```

`dist/` — раздавать через nginx/ingress с proxy на API.

Dev: `npm run dev`, proxy на backend.

## Production topology (target)

```
                    ┌─────────────┐
   Clients ────────►│   Ingress   │
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ispf-server x N   web-console    Keycloak
           │
           ├── PostgreSQL (managed)
           ├── Redis
           └── NATS JetStream
```

- Stateless replicas `ispf-server`

## Мониторинг

Actuator endpoints:

- `/actuator/health` — liveness/readiness
- `/actuator/prometheus` — metrics
- `/actuator/metrics` — Micrometer

## Логирование

```yaml
logging.level.com.ispf: INFO   # default
logging.level.com.ispf: DEBUG  # local/dev
```

## Локальные данные

| Профиль | Путь данных |
|---------|-------------|
| local | `./data/ispf-local.mv.db` |
| dev | PostgreSQL volume `ispf_pg_data` |

Очистка стенда: удалить H2 file или `docker compose down -v` (PostgreSQL).
