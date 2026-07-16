> **Язык:** русская версия (вычитка). Канонический английский: [en/getting-started.md](../en/getting-started.md).

# Быстрый старт

Два трека:

1. **[Попробовать ISPF](#попробовать-ispf-15-минут)** — запуск и демо (новички).  
2. **[Контрибут](#контрибут-локальный-dev--qa)** — быстрый local QA / pre-push (контрибьюторы).

---

## Попробовать ISPF (≈15 минут)

### Требования

| Компонент | Версия |
|-----------|--------|
| JDK | 21+ |
| Gradle | Wrapper в репозитории |
| Node.js | 20+ |
| Docker Desktop | Опционально — только для полного стека PostgreSQL / Keycloak / MQTT |

### 1. Запуск API + консоли

```bash
# Терминал 1 — профиль local (H2, без OAuth; sync небольшого набора dev driver packs)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# Терминал 2 — Web Console
cd apps/web-console && npm install && npm run dev
```

| URL | Назначение |
| --- | ---------- |
| http://localhost:5173 | Консоль администратора |
| http://localhost:5173?mode=operator | Operator HMI |
| http://localhost:8080/api/v1/info | Версия / capabilities |
| http://localhost:8080/actuator/health | Health |

`bootRun` по умолчанию вызывает **`syncDevDriverPacks`** (≈8 packs). Полный каталог: `-Dispf.driver.packs=all`.

### 2. Первые шаги в UI

1. Откройте дерево объектов — ветка `root.platform`.  
2. Раскройте `devices` → `demo-sensor-01` — temperature, threshold, alarm.  
3. Дважды кликните `dashboards.demo-sensor` — **Dashboard Builder**.  
4. Раскройте `alert-rules` → `temperature-threshold-exceeded` — CEL-правило.  
5. Дважды кликните `workflows.demo-alarm-handler` — демо **BPMN**.  
6. Переключите роль на **operator** или откройте `?mode=operator`.

Язык: селектор в шапке консоли (**English** удобен для OSS-скриншотов).

### 3. Старт драйвера demo-sensor

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "X-ISPF-Role: admin"
```

Температура — синусоида; при превышении порога срабатывают `alarmActive` и alert rule.

### Выбор роли (профиль `local`)

Селектор **Role** / **Роль** в шапке: `admin` или `operator` (заголовок `X-ISPF-Role`).

### Дальше после демо

- [Обзор продукта](product.md) · [Модель объектов](object-model.md) · [Дашборды](dashboards.md) · [Автоматизация](automation.md)  
- [Разработчик решений](solution-developer-guide.md) — реальный bundle  
- [Архитектура](architecture.md) · [API](api.md)

---

## Опционально: полный локальный стек

```bash
docker compose up -d
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=dev"
```

| Сервис | Порт | Назначение |
|--------|------|------------|
| PostgreSQL (TimescaleDB) | 5432 | БД `ispf` |
| Redis | 6379 | Кэш (зарезервировано) |
| NATS JetStream | 4222, 8222 | Messaging |
| Mosquitto | 1883 | MQTT |
| Keycloak | 8180 | OAuth2 (`dev`) |

### Профили Spring

| Профиль | БД | Auth | MQTT/NATS |
|---------|-----|------|-----------|
| *(default)* | PostgreSQL | JWT Keycloak | выкл. |
| `local` | H2 file | `X-ISPF-Role` | выкл. |
| `dev` | PostgreSQL | JWT Keycloak localhost:8180 | вкл. |
| `test` | H2 memory | выкл. | выкл. |

Подробнее: [deployment](deployment.md), [security](security.md).

### Driver packs

Драйверы протоколов **не** внутри `ispf-server.jar`. Локальный `bootRun` / тесты сервера синхронизируют **dev packs** автоматически.

```bash
./gradlew syncDevDriverPacks          # по умолчанию — быстро
./gradlew syncAllDriverPacks          # все packs — работа над драйверами / prod
```

Каталог runtime: `./data/drivers` (или `ISPF_DRIVER_PACKS_DIR`). Gradle: `build/driver-packs`.  
Подробно: [licensed-driver-packs](licensed-driver-packs.md).

---

## Контрибут: локальный dev & QA

**Не начинайте** с `./gradlew test` или `syncAllDriverPacks`, если не меняете драйверы и не гоняете полную регрессию. Эти пути собирают **все ~58 driver packs** и могут прогнать **1000+** тестов — на холодной машине часто **часы** ([issue #65](https://github.com/Michaael/IoT-Solutions-Platform/issues/65)).

### Проверка перед push (как CI pr-fast)

```bash
# Linux/macOS
./tools/ci/pr-fast.sh

# Windows
.\tools\ci\pr-fast.ps1
```

Backend в Gradle:

```bash
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev
```

Web console: `cd apps/web-console && npm test && npm run i18n:check && npm run build`.

### Точечная проверка (&lt; 2 мин)

```bash
./gradlew :packages:ispf-core:test --tests com.ispf.core.model.DataRecordTest

./gradlew :packages:ispf-server:test --tests com.ispf.server.alert.AlertRuleLatchTest \
  -Dispf.test.skipLoad=true
```

### Когда нужен полный pipeline

| Цель | Команда |
|------|---------|
| Все driver packs | `./gradlew syncAllDriverPacks` или `-Dispf.driver.packs=all` |
| PR-fast backend | `./gradlew testPrFast -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev` |
| Nightly backend | `./tools/ci/nightly.sh` или `./gradlew testNightlyBackend …` |
| Полные тесты сервера | `./gradlew :packages:ispf-server:test` (без `skipLoad`) |
| Всё | `./gradlew build` (медленно — не каждый день) |

**Уровни тестов (issue #65):** PR-fast пропускает `@Tag("load")` и `@Tag("federation")`; nightly гоняет их (`tools/ci/nightly.sh`, [ci-nightly.yml](../../.github/workflows/ci-nightly.yml)). Подпроекты локально параллельны; сериализация: `-Dispf.test.serializeSubprojects=true`. CI может кэшировать `build/driver-packs` (`ISPF_DRIVER_PACKS_PREBUILT=true`).

Опционально: [gradle.properties.example](../../gradle.properties.example).

См. также: [testing](testing.md).
