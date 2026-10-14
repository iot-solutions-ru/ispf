> **Язык:** русская версия (вычитка). Канонический английский: [en/air-gap-deployment.md](../en/air-gap-deployment.md).

# Развертывание воздушного зазора (БЛ-128)

> **Статус:** Stable — Офлайн-установка. Теги: [doc-status](../en/doc-status.md).

Runbook для установки и обновления ISPF на хостах **без исходящего Интернета**. Дополняет [deployment](deployment.md) (быстрый онлайн-старт) и [commercial-licensing](commercial-licensing.md) (лицензии пакета RSA/пакета драйверов).

## Когда использовать

| Сценарий | Подход |
|----------|----------|
| Lab / factory DMZ, no egress | Air-gap bundle + `air-gap-apply.sh` |
| Connected Linux + Docker | [Production quick start](deployment.md) (`prod-quickstart.sh`) |
| Managed VPS with SSH | `deploy/vps-deploy-direct.ps1` (direct SCP) |

## Архитектура

```text
[Build host — has internet]
  air-gap-pack.sh → ispf-airgap-<version>.tar.gz
        │  (USB / sneakernet / internal file share)
        ▼
[Target host — no internet]
  air-gap-apply.sh → docker load + compose up
```

Содержимое комплекта:

| Путь | Описание |
|------|-------------|
| `artifacts/ispf-server.jar` | Platform server |
| `artifacts/web-console/` | Built SPA (also `web-console.zip`) |
| `artifacts/driver-packs.tar.gz` | Optional; extracted to `artifacts/drivers/` |
| `images/prod-stack.tar` | `docker save` закрепленных postgres, redis, temurin JRE, nginx (+ опционально ClickHouse) |
| `deploy/docker-compose.air-gap.yml` | Self-contained stack |
| `deploy/air-gap-images.env` | Pinned image tags used at pack time |
| `MANIFEST.json` / `CHECKSUMS.sha256` | Version + integrity (corruption check) |

Необязательный `--with-clickhouse` добавляет `clickhouse/clickhouse-server:24.8` к tar-образу образа (включите переменные CH env отдельно — см. [DEPLOYMENT.md § ClickHouse](deployment.md)).

---

## Контрольный список — сторона сборки (подключено)

Предварительные требования для **машины сборки**: git checkout, JDK 25, Node.js 20+, Docker, оболочка Gradle.

- [ ] Choose release version (e.g. `0.9.32`)
- [ ] Комплект сборки:

```bash
bash deploy/air-gap-pack.sh --version 0.9.32
```

- [ ] Optional flags: `--skip-build`, `--skip-driver-packs`, `--with-clickhouse`
- [ ] Verify output: `build/air-gap/ispf-airgap-0.9.32.tar.gz`
- [ ] Запись **внеполосного** SHA256 архива для обеспечения целостности передачи (КОНТРОЛЬНЫЕ СУММЫ внутри пакета ≠ защита от несанкционированного доступа)
- [ ] Optional: `--with-clickhouse` adds ClickHouse image + compose profile
- [ ] **Коммерческие приложения**: подписывайте манифесты пакета на стороне сборки перед упаковкой приложений (см. § Лицензирование ниже).
- [ ] Скопировать архив на съемный носитель или во внутренний репозиторий артефактов.

---

## Контрольный список — целевая сторона (с воздушным зазором)

Предварительные требования на **целевом хосте**: только Docker Engine + Compose v2 (JDK/Node не требуется).

- [ ] Transfer `ispf-airgap-<version>.tar.gz`
- [ ] Убедитесь, что архив SHA256 соответствует записи на стороне сборки.
- [ ] Установить среду коммерческой лицензии (если применимо) — см. § Лицензирование.
- [ ] Применять:

```bash
bash deploy/air-gap-apply.sh /media/ispf-airgap-0.9.32.tar.gz
```

Или сначала извлеките, а затем примените каталог:

```bash
tar -xzf ispf-airgap-0.9.32.tar.gz
bash ispf-airgap-0.9.32/deploy/air-gap-apply.sh ispf-airgap-0.9.32/
```

- [ ] Confirm health: `curl http://localhost:8080/api/v1/info`
- [ ] Open UI: http://localhost:8088/
- [ ] Note `installationId` for future license issuance: `GET /api/v1/platform/installation-id`

---

## Обновление без интернета

1. На хосте сборки: создайте более новую версию `ispf-airgap-<new-version>.tar.gz`.
2. Трансфер к цели.
3. Достигнув цели, остановите выполнение стека (сохраняет том БД):

```bash
cd /path/to/current/ispf-airgap-<old>/
docker compose -f deploy/docker-compose.air-gap.yml down
```

4. Примените новый пакет (загружает изображения, если они были изменены, заменяет JAR/UI/драйверы):

```bash
bash deploy/air-gap-apply.sh /media/ispf-airgap-<new-version>.tar.gz
```

5. Миграция Flyway запускается автоматически при запуске сервера. Для установки в стиле systemd/VPS используйте `deploy/apply-platform-update.sh` с промежуточным каталогом, содержащим `ispf-server.jar`, `web-console.zip`, необязательно `driver-packs.tar.gz`.

**Rollback:** keep previous `.tar.gz`; re-apply after `docker compose down`.

---

## Порядок коммерческого лицензирования (RSA — Done)

Compose air-gap по умолчанию включает prod license posture: `ISPF_LICENSE_ENFORCE=true` и `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true`. Экспортируйте публичный ключ **до** `air-gap-apply.sh`. Канон: [en/air-gap-deployment.md](../en/air-gap-deployment.md).

```bash
export ISPF_LICENSE_PUBLIC_KEY_PEM="$(cat license-public.pem)"
bash deploy/air-gap-apply.sh /media/ispf-airgap-<version>.tar.gz
# optional smoke: bash deploy/tools/air-gap-signed-gate-smoke.sh
```

Lab override: `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false`. Evidence: [air-gap-signing](../evidence/air-gap-signing/README.md).

### Подписи издателя (Cosign) — Gap

RSA app-bundle gate — **Done**. Cosign / sigstore для Docker-образов — **Gap** (не в репозитории). `CHECKSUMS.sha256` — только проверка целостности, не publisher signature.

---

## установка systemd (нет Docker на хосте приложения)

Некоторые сайты запускают JAR + nginx на «голом железе» и используют Docker для PostgreSQL только на соседнем хосте.

1. Извлечь пакет; копировать `artifacts/ispf-server.jar` → `/opt/ispf/`
2. Разархивируйте `artifacts/web-console.zip` → `/opt/ispf/web-console/`.
3. Извлечь `artifacts/driver-packs.tar.gz` → `/opt/ispf/data/drivers/`
4. Настройте `/opt/ispf/ispf-server.env` (URL-адрес базы данных, варианты лицензий)
5. Используйте `deploy/remote-setup-ispf.sh` или существующие файлы модулей из [DEPLOYMENT.md § Удаленный хост](deployment.md).
6. Обновления: режиссура + `deploy/apply-platform-update.sh /opt/ispf/staging/<version>`

---

## Список артефактов вручную (без сценария упаковки)

If you cannot run `air-gap-pack.sh` on Linux, assemble the same layout manually:

| Артефакт | Источник |
|----------|--------|
| `ispf-server.jar` | `./gradlew :packages:ispf-server:bootJar -Pversion=<ver>` |
| `web-console.zip` | `apps/web-console/dist` → zip |
| `driver-packs.tar.gz` | `./gradlew syncAllDriverPacks` → tar `build/driver-packs/` |
| Docker images | `docker pull` + `docker save` for images listed in `MANIFEST.json` |

---

## Поиск неисправностей

| Симптом | Проверить |
|---------|-------|
| `docker load` fails | Archive truncated; re-verify out-of-band SHA256 |
| `Invalid bundle kind` | Archive is not an ISPF air-gap bundle |
| Health check timeout | `docker compose logs ispf-server`; DB credentials |
| 403 on app deploy | License / `require-signed-bundles`; installation ID mismatch |
| Empty driver list | `artifacts/drivers/` not populated; rebuild with driver packs |

---

## Локальная LLM на демостенде (воздушный зазор)

Кратко. Канон: [en/air-gap-deployment.md](../en/air-gap-deployment.md) § Local LLM demostand. Статус: **Partial**.

- Агент ходит только на `ISPF_AI_BASE_URL` (`ispf.ai.base-url`). OpenAI-совместимый сервер на площадке: `ISPF_AI_PROVIDER=openai-compatible` и URL с `/v1` (или хост без пути — клиент дописывает `/v1`). Нативный Ollama: `ISPF_AI_PROVIDER=ollama`, `ISPF_AI_BASE_URL=http://127.0.0.1:11434` (это `/api/chat`, не `/v1`).
- Офлайн-установка платформы — `deploy/air-gap-pack.sh` / `air-gap-apply.sh`. Веса модели в tar **не** входят; их переносят отдельно.
- Граница утверждения «данные не покидают площадку»: промпты агента уходят на настроенный base URL. Это не доказательство нулевого egress для marketplace/лицензий и не подпись образов. **Cosign в репозитории нет** (Gap). Журнал прогона — заготовка [evidence/air-gap-llm](../evidence/air-gap-llm/README.md); пока там нет датированного прогона, ось air-gap в scorecard остаётся **Partial**.

## Сопутствующие документы

- [deployment](deployment.md) — онлайн-развертывание, подписание бандла, ClickHouse
- [commercial-licensing](commercial-licensing.md) — заявки на лицензию, флаги принудительного применения
- [licensed-driver-packs](licensed-driver-packs.md) — макет пакета драйверов.
- [tools/license-builder/README.md](readme.md) — подписывать пакеты в автономном режиме на стороне поставщика.
