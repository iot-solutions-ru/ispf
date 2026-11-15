> **Язык:** русская версия. Английский оригинал: [en/getting-started.md](../en/getting-started.md).

# Быстрый старт

> **Статус:** Stable — первый запуск платформы и локальная проверка кода. Метки зрелости: [doc-status.md](doc-status.md).

В этом руководстве два раздела:

1. **[Попробовать ISPF (≈15 минут)](#попробовать-ispf-15-минут)** — запустить платформу на своей машине, войти в консоль и посмотреть встроенный пример с датчиком, тревогой и экраном оператора.
2. **[Контрибут: локальный dev & QA](#контрибут-локальный-dev--qa)** — как собирать проект и быстро гонять тесты, если вы дорабатываете саму платформу.

Если хотите сначала понять общую картину, откройте параллельно **[Как устроен ISPF](learn.md)**.

---

## Попробовать ISPF (≈15 минут)

### Вариант А — Готовый архив (быстрее всего, без установки СУБД)

1. Установите **JDK 25** и скачайте архив **`ispf-*-portable.zip`** со страницы [GitHub Releases](https://github.com/iot-solutions-ru/ispf/releases).
2. Распакуйте архив в любую папку и запустите стартовый скрипт:

```bash
# Windows: двойной клик по start.bat (или запуск из PowerShell / cmd)
start.bat

# Linux / macOS:
./start.sh
```

Устанавливать PostgreSQL для знакомства не нужно: переносная сборка хранит данные во **встроенной файловой базе H2** (файл `data/ispf-local.mv.db` появится автоматически при первом старте).

В архиве лежит **оценочный набор драйверов** в папке `drivers/` (`virtual`, `modbus`, `mqtt`, `http`, `jdbc` и тот же короткий список, что собирает `bootRun`). `start.bat` / `start.sh` сами выставляют `ISPF_DRIVER_PACKS_DIR` на эту папку, если переменная ещё не задана. Этого хватает, чтобы запустить `demo-sensor-01`. OPC UA, SNMP и остальные 160+ протоколов в архив не входят — их собирают командой `./gradlew syncAllDriverPacks` или кладут в air-gap-комплект.

Чтобы сбросить базу, остановите ISPF и удалите только `data/`. Папку `drivers/` не трогайте.

3. Откройте в браузере **http://localhost:8080** и войдите с логином **`admin`** и паролем **`admin`**. Далее переходите к [шагу 2 — Вход в систему](#2-вход-local) (используйте порт `8080`).

### Вариант Б — Запуск в Docker

```bash
docker pull ghcr.io/iot-solutions-ru/ispf-server:latest
docker run --rm -p 8080:8080 -v ispf-data:/opt/ispf/data ghcr.io/iot-solutions-ru/ispf-server:latest
```

Откройте **http://localhost:8080** и войдите под **`admin` / `admin`**.

В образ вшит тот же оценочный набор, каталог `/opt/ispf/drivers`. Том в команде выше — только данные H2. Монтирование `-v ispf-data:/opt/ispf/data` драйверы не подменяет. Полного каталога протоколов в образе нет.

### Вариант В — Запуск из исходного кода

#### Что понадобится

| Компонент | Версия | Примечание |
|-----------|--------|------------|
| JDK | **25** | Подтягивается и проверяется через Gradle Toolchain |
| Gradle | В репозитории (`./gradlew` / `gradlew.bat`) | Ставить отдельно не нужно |
| Node.js | **20+** | Для запуска фронтенда `apps/web-console` в режиме разработки |
| Docker | Необязательно | Нужен только для полного окружения с PostgreSQL, Keycloak, MQTT и NATS |

#### 1. Запустите сервер и веб-консоль

```bash
# Терминал 1 — сервер API в профиле local (файловая БД H2 + базовый набор драйверов)
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"

# Терминал 2 — веб-консоль React (проксирует /api и /ws на localhost:8080)
cd apps/web-console && npm install && npm run dev
```

| Адрес | Что открывается |
|-------|-----------------|
| `http://localhost:8080` | Консоль администратора (при запуске готового архива или Docker) |
| `http://localhost:5173` | Консоль администратора (при запуске через Vite dev) |
| `http://localhost:8080?mode=operator` | Рабочее место оператора (готовый архив / Docker) |
| `http://localhost:5173?mode=operator` | Рабочее место оператора (Vite dev) |
| `http://localhost:8080/api/v1/info` | Версия сервера и включённые модули |
| `http://localhost:8080/actuator/health` | Проверка работоспособности (Health check) |

При локальном `bootRun` автоматически собирается компактный набор драйверов разработки (`syncDevDriverPacks`: `virtual`, `mqtt`, `modbus`, `http`, `jdbc` и др.). Если нужен полный каталог из 160+ протоколов, добавьте параметр `-Dispf.driver.packs=all`.

### 2. Вход (`local`)

При первом запуске на чистой базе создаются три тестовые учётные записи:
- **`admin` / `admin`** — полный доступ к дереву объектов, конструкторам и системным настройкам;
- **`developer` / `developer`** — учётная запись инженера-разработчика решений;
- **`operator` / `operator`** — сразу открывает полноэкранный режим оператора (экраны, квитирование, очередь задач).

Для запросов к REST API из терминала получите токен авторизации:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/objects
```

> Примечание: отладочный заголовок `X-ISPF-Role` по умолчанию выключен (`ispf.security.local-role-header-enabled=false`). Для всех запросов используйте токен из `/api/v1/auth/login`. Подробнее: [Безопасность](security.md).

### 3. Первые шаги в веб-консоли

![Консоль администратора — дерево объектов после первого входа](../assets/ispf-object-tree.png)

1. В левой панели **проводника (Explorer)** раскройте ветку `root` → `platform`.
2. Откройте папку `devices` и выберите **`demo-sensor-01`**. Справа в **инспекторе** отобразятся переменные датчика: температура (`temperature`), уставка порога (`threshold`) и флаг аварии (`alarmActive`).
3. Откройте `dashboards` и дважды кликните по **`demo-sensor`** — откроется визуальный конструктор дашборда (**Dashboard Builder**).
4. Откройте `alert-rules` → **`temperature-threshold-exceeded`** — здесь задано условие срабатывания тревоги по температуре на языке CEL.
5. Откройте `workflows` и дважды кликните по **`demo-alarm-handler`** — откроется пример процесса обработки тревоги в нотации **BPMN**.
6. Перейдите по ссылке режима оператора (`http://localhost:8080?mode=operator` или `http://localhost:5173?mode=operator`), чтобы увидеть, как этот же экран выглядит на посту дежурной смены без административных меню.

Переключатель языка интерфейса (**Русский / English**) находится в верхней панели консоли.

### 4. Запустите опрос демо-датчика

Запустите драйвер симулятора на `demo-sensor-01` (кнопкой запуска драйвера в карточке устройства или командой `curl`):

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "Authorization: Bearer $TOKEN"
```

Драйвер начнёт генерировать синусоиду температуры. Как только значение превысит порог `threshold`, выставится `alarmActive = true`, сработает правило тревоги и в журнале появится событие.

### Куда двигаться дальше

- **[Как устроен ISPF](learn.md)** — пройдите **Маршрут Б**, чтобы за день собрать свой первый проект с оборудованием и расчётами.
- **[Руководство разработчика решений](solution-developer-guide.md)** — как оформить таблицы SQL, функции и экраны в переносимый пакет `bundle.json` (`ispf pack` / `validate` / `deploy`). Команды `ispf login` и `ispf doctor` описаны в [tools/ispf-cli/README.md](../../tools/ispf-cli/README.md).
- **[Частые ошибки при проектировании](anti-patterns.md)** — семь практических правил, которые уберегут от тормозов и путаницы в проекте.
- **[Разработка своего React-интерфейса](external-ide.md)** — как написать кастомный веб-интерфейс оператора (`ui-pack`) в Cursor или VS Code.

---

## Дополнительно: полный стек с PostgreSQL, MQTT и Keycloak (`dev`)

Если для разработки нужны PostgreSQL/TimescaleDB, брокер MQTT (Mosquitto), шина NATS JetStream и авторизация через Keycloak:

```bash
docker compose up -d
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=dev"
```

| Сервис | Порт | Назначение |
|--------|------|------------|
| PostgreSQL (TimescaleDB) | `5432` | Основная база данных и оперативный архив историй (`ispf`) |
| Redis | `6379` | Кэш |
| NATS JetStream | `4222`, `8222` | Шина сообщений и шагов процессов |
| Eclipse Mosquitto | `1883` | Локальный MQTT-брокер для проверки устройств |
| Keycloak | `8180` | Сервер единого входа OAuth2 / OIDC (профиль `dev`) |

### Профили запуска сервера

| Профиль Spring | База данных | Авторизация | MQTT / NATS |
|----------------|-------------|-------------|-------------|
| *(по умолчанию)* | PostgreSQL | JWT через Keycloak | По переменным окружения |
| `local` | Файловая H2 (`./data/ispf-local`) | Встроенный вход (`POST /api/v1/auth/login`) | Выкл. |
| `dev` | PostgreSQL (`localhost:5432`) | JWT через Keycloak (`localhost:8180`) | Вкл. |
| `test` | H2 в памяти | Выкл. (для автотестов) | Выкл. |

Подробное описание переменных окружения для продакшена: [Развёртывание](deployment.md) и [Безопасность](security.md).

### Как подключаются пакеты драйверов

Протокольные драйверы поставляются отдельными модулями вне `ispf-server.jar` и подгружаются при старте из каталога `ISPF_DRIVER_PACKS_DIR` (по умолчанию `./data/drivers`).

Готовый архив и образ GHCR сами указывают эту переменную на папку `drivers/` рядом с сервером (`./drivers` или `/opt/ispf/drivers`) и кладут туда оценочный набор (`syncDevDriverPacks`). Запуск из исходников так не делает: `bootRun` собирает тот же набор в `build/driver-packs` и смотрит уже туда.

```bash
./gradlew syncDevDriverPacks          # быстрый базовый набор (используется по умолчанию)
./gradlew syncAllDriverPacks          # сборка всех 160+ драйверов в build/driver-packs
```

Подробнее: [Драйверы устройств](drivers.md) и [Пакеты драйверов](licensed-driver-packs.md).

---

## Контрибут: локальный dev & QA

> **Важно:** **не запускайте** `./gradlew test` или `syncAllDriverPacks` при обычной разработке ядра или интерфейса, если вы не правите сами драйверы. Полная сборка всех 162 пакетов драйверов и свыше тысячи интеграционных тестов на холодной машине может занять **несколько часов** ([issue #65](https://github.com/iot-solutions-ru/ispf/issues/65)). Используйте быстрые команды ниже.

### Быстрая проверка перед отправкой PR (совпадает с CI `pr-fast`)

```bash
# Linux / macOS
./tools/ci/pr-fast.sh

# Windows PowerShell
.\tools\ci\pr-fast.ps1
```

Те же проверки по отдельности:

```bash
# Серверная часть (пропускает длительные нагрузочные и кластерные тесты)
./gradlew testPrFast \
  -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev

# Веб-консоль: тесты, проверка локализации и сборка статики
cd apps/web-console && npm test && npm run i18n:check && npm run build
```

### Точечный запуск одного теста (быстрее 2 минут)

```bash
./gradlew :packages:ispf-core:test --tests com.ispf.core.model.DataRecordTest

./gradlew :packages:ispf-server:test --tests com.ispf.server.alert.AlertRuleLatchTest \
  -Dispf.test.skipLoad=true
```

### Шпаргалка по наборам тестов

| Что вы меняли | Какую команду запустить |
|---------------|-------------------------|
| Логику сервера или ядра | `./gradlew testPrFast -Dispf.test.skipLoad=true -Dispf.test.skipFederation=true -Dispf.driver.packs=dev` |
| Базовые драйверы разработки | `./gradlew testDevDriverPacks syncDevDriverPacks` |
| Весь каталог драйверов протоколов | `./gradlew syncAllDriverPacks` |
| Нагрузочные тесты и федерацию (ночной прогон) | `./tools/ci/nightly.sh` |
| Все тесты `ispf-server` без пропусков | `./gradlew :packages:ispf-server:test` |

Настройки параллельности сборки описаны в [gradle.properties.example](../../gradle.properties.example), подробности о тестах — в разделе [Тестирование](testing.md).
