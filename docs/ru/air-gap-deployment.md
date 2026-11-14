> **Язык:** русская версия. Английский оригинал: [en/air-gap-deployment.md](../en/air-gap-deployment.md).

# Установка в закрытом контуре без интернета (air-gap)

> **Статус:** Stable — автономное развёртывание и обновление в изолированных сетях. Метки зрелости: [doc-status.md](doc-status.md).

Инструкция по установке и обновлению сервера ISPF на площадках **без доступа в интернет** (технологические сети, ДМЗ предприятия). Онлайн-установка в Docker описана в разделе [Развёртывание](deployment.md), подпись коммерческих пакетов — в [Коммерческом лицензировании](commercial-licensing.md).

---

## Когда использовать этот сценарий

| Сценарий | Подходящий способ |
|----------|-------------------|
| Закрытый цеховой сегмент / ДМЗ без выхода во внешнюю сеть | Автономный архив `ispf-airgap-*.tar.gz` + скрипт `air-gap-apply.sh` |
| Linux-сервер с доступом в интернет и Docker | Онлайн-запуск из [Развёртывания](deployment.md) |
| Выделенный сервер с доступом по SSH | Прямая выкладка артефактов по SCP/SSH |

---

## Схема подготовки и переноса

```text
[Машина сборки — с интернетом]
  air-gap-pack.sh  ──►  ispf-airgap-<версия>.tar.gz + контрольная сумма SHA-256
                              │
               (перенос на съёмном носителе / через шлюз)
                              ▼
[Целевой сервер — без интернета]
  air-gap-apply.sh  ──►  docker load + docker compose up
```

Что входит в автономный архив:

| Путь внутри архива | Содержимое |
|--------------------|------------|
| `artifacts/ispf-server.jar` | Сервер платформы ISPF |
| `artifacts/web-console/` | Собранная статика веб-консоли (также `web-console.zip`) |
| `artifacts/driver-packs.tar.gz` | Пакеты протокольных драйверов (распаковываются в `artifacts/drivers/`) |
| `images/prod-stack.tar` | Сохранённые образы Docker (`docker save`): PostgreSQL/Timescale, Redis, JRE Temurin, nginx (и опционально ClickHouse) |
| `deploy/docker-compose.air-gap.yml` | Автономный файл запуска Compose |
| `deploy/air-gap-images.env` | Зафиксированные версии образов |
| `MANIFEST.json` / `CHECKSUMS.sha256` | Паспорт сборки и контрольные суммы файлов для проверки целостности при копировании |

Флаг `--with-clickhouse` дополнительно включает образ `clickhouse/clickhouse-server:24.8` в архив (переменные подключения ClickHouse включаются отдельно — см. [Развёртывание](deployment.md)).

---

## Шаг 1. Сборка архива (на машине с интернетом)

Требования на сборочной машине: Git-репозиторий ISPF, JDK 25, Node.js 20+, Docker.

1. Соберите автономный пакет нужной версии:
   ```bash
   bash deploy/air-gap-pack.sh --version 0.9.32
   ```
   Необязательные флаги: `--skip-build` (взять уже собранные артефакты), `--skip-driver-packs`, `--with-clickhouse`.
2. Проверьте созданный файл `build/air-gap/ispf-airgap-0.9.32.tar.gz` и отдельно сохраните его контрольную сумму `sha256sum` для сверки после переноса на площадку.
3. Если поставляются коммерческие прикладные пакеты, подпишите их манифесты до переноса (см. раздел о лицензиях ниже).
4. Скопируйте архив на защищённый носитель или во внутреннее файловое хранилище предприятия.

---

## Шаг 2. Установка на целевом сервере (без интернета)

На целевом Linux-сервере требуется только **Docker Engine и Docker Compose v2** (устанавливать JDK и Node.js не нужно).

1. Скопируйте `ispf-airgap-<версия>.tar.gz` на сервер и сверьте контрольную сумму `sha256sum`.
2. Если используется проверка подписи пакетов (включена в профиле air-gap по умолчанию), задайте публичный ключ до запуска:
   ```bash
   export ISPF_LICENSE_PUBLIC_KEY_PEM="$(cat license-public.pem)"
   ```
3. Запустите установку:
   ```bash
   bash deploy/air-gap-apply.sh /media/ispf-airgap-0.9.32.tar.gz
   ```
   *(Либо сначала распакуйте `tar -xzf ispf-airgap-0.9.32.tar.gz` и передайте путь к распакованной папке.)*
4. Проверьте готовность сервера:
   - API: `curl http://localhost:8080/api/v1/info`
   - Веб-консоль: `http://localhost:8088/`
   - Идентификатор инсталляции (для привязки лицензии): `GET /api/v1/platform/installation-id`

---

## Обновление установленного сервера без интернета

1. Соберите архив новой версии `ispf-airgap-<новая-версия>.tar.gz` и перенесите на целевой сервер.
2. Остановите контейнеры текущей версии (том базы данных PostgreSQL при этом сохраняется):
   ```bash
   cd /path/to/current/ispf-airgap-<старая-версия>/
   docker compose -f deploy/docker-compose.air-gap.yml down
   ```
3. Примените новый архив (обновятся изменившиеся Docker-образы, JAR сервера, консоль и драйверы; миграции БД Flyway выполнятся автоматически при старте):
   ```bash
   bash deploy/air-gap-apply.sh /media/ispf-airgap-<новая-версия>.tar.gz
   ```

Для отката достаточно остановить стек (`docker compose down`) и повторно применить предыдущий архив `.tar.gz`.

---

## Установка через `systemd` (без Docker на сервере приложения)

Если политика безопасности запрещает Docker на сервере приложения, ISPF запускается напрямую как системная служба Linux:

1. Распакуйте архив и скопируйте `artifacts/ispf-server.jar` в `/opt/ispf/`.
2. Распакуйте `artifacts/web-console.zip` в `/opt/ispf/web-console/`.
3. Распакуйте `artifacts/driver-packs.tar.gz` в `/opt/ispf/data/drivers/`.
4. Заполните настройки подключения к СУБД и ключи лицензии в `/opt/ispf/ispf-server.env`.
5. Настройте unit-файл `systemd` и конфигурацию nginx по инструкции [Развёртывание](deployment.md) (скрипт `deploy/remote-setup-ispf.sh`, обновление через `deploy/apply-platform-update.sh`).

---

## Проверка подписей и лицензирование

В профиле `docker-compose.air-gap.yml` по умолчанию включена строгая проверка: `ISPF_LICENSE_ENFORCE=true` и `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true` (для лабораторного стенда без ключей можно явно выставить `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false`). Подробнее об артефактах проверки RSA и статусе подписи образов Cosign см. [en/air-gap-deployment.md](../en/air-gap-deployment.md) и [air-gap-signing](../evidence/air-gap-signing/README.md).

---

## Локальная языковая модель (LLM) в изолированном контуре

Встроенный ИИ-помощник обращается только по адресу `ISPF_AI_BASE_URL`:
- локальный OpenAI-совместимый сервер (vLLM и аналоги): `ISPF_AI_PROVIDER=openai-compatible`, `ISPF_AI_BASE_URL=http://<внутренний-хост>:8000/v1`;
- локальный сервер Ollama: `ISPF_AI_PROVIDER=ollama`, `ISPF_AI_BASE_URL=http://127.0.0.1:11434`.

Веса языковой модели в архив `ispf-airgap-*.tar.gz` не входят и доставляются во внутренний контур отдельно.

---

## Диагностика частых проблем

| Симптом | Что проверить |
|---------|---------------|
| Ошибка при `docker load` | Архив скопировался не полностью — сверьте `sha256sum` с исходным файлом |
| Таймаут ожидания запуска | Логи контейнера `docker compose logs ispf-server` и параметры подключения к PostgreSQL |
| Ошибка `403` при деплое пакета приложения | Не задан `ISPF_LICENSE_PUBLIC_KEY_PEM` или пакет не подписан при включённом `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true` |
| Пустой каталог драйверов | Архив собирался с `--skip-driver-packs` или папка `artifacts/drivers/` не примонтирована |
