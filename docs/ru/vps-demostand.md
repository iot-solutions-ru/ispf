> **Язык:** русская версия. Английский оригинал: [en/vps-demostand.md](../en/vps-demostand.md).

# Памятка по обслуживанию одноузлового демонстрационного сервера (VPS)

> **Статус:** Lab — эксплуатационная памятка для одноузлового демонстрационного сервера с профилем `demo-idle`. Метки зрелости: [doc-status.md](doc-status.md).

Эта памятка дополняет общие руководства **[Профили развёртывания (`demostands.md`)](demostands.md)** и **[Развёртывание и конфигурация (`deployment.md`)](deployment.md)** для случая одноузлового демонстрационного сервера в Docker за обратным прокси `nginx`.

---

## 1. Настройки экономного демонстрационного профиля (`demo-idle`)

На публичном демонстрационном стенде используется профиль `ISPF_DEPLOY_PROFILE=demo-idle` с файлом окружения [`deploy/ispf-server.prod-idle.env`](../../deploy/ispf-server.prod-idle.env):
- одиночный сервер без кластера (`ISPF_CLUSTER_ENABLED=false`, `ISPF_REPLICA_ROLE=all`);
- фиксированные малые пулы потоков без фонового раздувания очередей;
- в работе оставлены только демонстрационные драйверы (`snmp-localhost` в режиме `TELEMETRY_ONLY` и `demo-sensor-01` в режиме `FULL`).

Проверить применённый профиль и размеры пулов на работающем сервере:

```http
GET /api/v1/platform/effective-config
```

---

## 2. Типовая схема контейнеров

```text
Браузеры ──► nginx (:80 / :443) ──► контейнер ispf-server (:8081)
                                    PostgreSQL + Redis
```

Файл Compose: [`deploy/docker-compose.vps-single.yml`](../../deploy/docker-compose.vps-single.yml).

---

## 3. Основные скрипты обслуживания

| Задача | Скрипт |
|--------|--------|
| Собрать локально и отправить новую версию на сервер по SCP | [`deploy/vps-deploy-direct.ps1`](../../deploy/vps-deploy-direct.ps1) |
| Выложить загруженный JAR и веб-консоль на одноузловом сервере | [`deploy/vps-single-rollout.sh`](../../deploy/vps-single-rollout.sh) |
| Применить настройки `prod-idle.env` с пересозданием контейнера | [`deploy/vps-apply-prod-idle-env.sh`](../../deploy/vps-apply-prod-idle-env.sh) |
| Перевести фоновые драйверы стенда в экономный режим опроса | [`deploy/vps-demostand-tune-drivers.sh`](../../deploy/vps-demostand-tune-drivers.sh) |
| Снять профиль потоков JVM при анализе нагрузки на CPU | [`deploy/vps-idle-thread-sample.py`](../../deploy/vps-idle-thread-sample.py) |

> **Напоминание:** после любого изменения файла окружения `.env` обязательно пересоздавайте контейнер (`docker compose up -d --force-recreate`), так как обычный `docker restart` не подхватывает новые переменные окружения.
