> **Язык:** русская версия. Канонический английский: [en/troubleshooting.md](../en/troubleshooting.md).

# Если первая сессия не завелась

> **Статус:** Stable — сбои первого часа. Теги: [doc-status.md](doc-status.md).

Эта страница — для ноутбука, на котором установка не дошла до демо-датчика. В каждом разделе: что видно, что проверить и что ответ означает. Правила проекта, который вы уже собираете, — в [Частых ошибках при проектировании](anti-patterns.md). Что платформа сознательно не обещает — в [Известных ограничениях](known-limitations.md).

Проверки ниже рассчитаны на порт `8080`. Если задан `ISPF_SERVER_PORT`, подставьте его.

---

## Браузер не открывает консоль

**Какой URL.** Архив / Docker → **http://localhost:8080**. Из исходников (вариант В) → **http://localhost:5173** после API **и** `npm run dev` в `apps/web-console`. Один «живой» API — ещё не полная первая установка — [Быстрый старт](getting-started.md#попробовать-ispf-15-минут). Разделение (API `8080` + Vite `5173`) сделано для hot-reload — [Почему два процесса](getting-started.md#почему-два-процесса-и-два-порта).

**Проверка.** С той же машины:

```bash
curl -sS http://localhost:8080/api/v1/info
curl -sS http://localhost:8080/actuator/health
```

`/api/v1/info` отвечает без токена и содержит `version`. `/actuator/health` тоже отвечает без токена. `{"status":"UP"}` значит, что процесс обслуживает запросы.

**Отказ соединения или таймаут.** Процесс ISPF не слушает порт. Архив запускают через `start.bat` / `start.sh`. Образ — командой `docker run` из [Быстрого старта](getting-started.md). Из исходников локальный профиль такой:

```bash
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

**`/api/v1/info` отвечает, а `http://localhost:8080` отдаёт 404.** Для `bootRun` из исходников это ожидаемо: на 8080 только API. Запустите веб-консоль (`cd apps/web-console && npm install && npm run dev`) и откройте **http://localhost:5173**. Архив и контейнер отдают консоль на 8080. Только API без консоли — [продвинутый путь](getting-started.md#продвинутый-путь-только-api).

**Порт отвечает, но это не ISPF.** Порт 8080 занят другой программой, либо ещё работает прошлый процесс ISPF. Остановите его или задайте этому экземпляру другой `ISPF_SERVER_PORT`.

---

## Процесс завершается, не открыв порт

**Профиль по умолчанию — не ноутбучный.** Без `local` сервер ждёт PostgreSQL на `localhost:5432/ispf` (пользователь `ispf`, пароль `ispf`, если не заданы `ISPF_DB_URL` / `ISPF_DB_USER` / `ISPF_DB_PASSWORD`) и OIDC-издателя `http://localhost:8180/realms/ispf`. Архив и контейнер уже стартуют в локальном профиле: встроенный H2 в `data/ispf-local` (в контейнере `/opt/ispf/data/ispf-local`), без PostgreSQL. Каталог исходников — нет. Возьмите команду `bootRun` выше либо поднимите PostgreSQL и Keycloak через `docker compose up -d` и профиль `dev`. Таблица профилей: [Быстрый старт](getting-started.md#профили-запуска-сервера).

**В журнале есть `Startup security guard refused boot`.** Старт остановил `ispf.security.strict-startup=true` (профиль `prod`). В той же строке названа причина: источники WebSocket `*`, оставленный отладочный заголовок роли или выключенный RBAC. Исправьте эту настройку. Не отключайте страж, чтобы «просто посмотреть демо». Подробности: [Безопасность](security.md).

---

## Вход не принимает `admin` / `admin`

**Проверка.**

```bash
curl -sS http://localhost:8080/api/v1/auth/config
```

| `mode` | Что делать |
|--------|------------|
| `local` | На новой локальной базе есть `admin` / `admin`, `developer` / `developer` и `operator` / `operator`. Учётная запись `operator` открывает экран оператора и не редактирует дерево объектов. Это роль, а не сломанная установка. Если пароль на этом ноутбуке уже меняли, остановите ISPF и удалите `data/` (каталог `drivers/` оставьте) — учётные записи создадутся заново. |
| `oidc` | Встроенные пароли на этом сервере не вход. `localLoginEnabled` равен false. Входите через издателя и client id из того же ответа. |

Неверный пароль — HTTP 401, `Invalid username or password`. После 5 неудач для той же учётной записи и того же адреса клиента следующая попытка — HTTP 429, `Too many failed login attempts`, на 15 минут.

`admin` / `admin` — для локальной оценки. Смените пароль до того, как машина станет доступна в сети. [Безопасность](security.md).

Заголовок `X-ISPF-Role` не выполняет вход. Он выключен, пока не задано `ISPF_LOCAL_ROLE_HEADER_ENABLED=true`.

---

## `demo-sensor-01` не меняет значения

Демо-устройству нужны драйвер `virtual` и запущенный опрос.

**Проверка** (токен с `POST /api/v1/auth/login` на сервере `local`):

```bash
curl -sS -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/drivers
```

HTTP 401 — токена нет или он выдан другим сервером. Список JSON без `"id":"virtual"` — оценочные пакеты драйверов не загружены.

| Как запускали | Где лежат пакеты |
|----------------|------------------|
| Переносной архив | `drivers/` рядом с JAR. `start.bat` / `start.sh` выставляют `ISPF_DRIVER_PACKS_DIR`, если вы его не задали. |
| Контейнер | `/opt/ispf/drivers` внутри образа. Том на `/opt/ispf/data` драйверы не подменяет. |
| `bootRun` | `build/driver-packs`, его заполняет `syncDevDriverPacks`. |

Удаление `data/` сбрасывает базу H2 и не стирает `drivers/`. Удаление `drivers/` или пустой `ISPF_DRIVER_PACKS_DIR` убирает протоколы.

В оценке — короткий набор (`virtual`, `modbus`, `mqtt`, `http`, `jdbc` и остальные из этого набора). OPC UA, SNMP и остальной каталог в архив и образ не входят. Их собирают `./gradlew syncAllDriverPacks` или привозят отдельным пакетом для закрытого контура. [Быстрый старт](getting-started.md#как-подключаются-пакеты-драйверов).

Когда `virtual` есть в списке, запустите опрос демо-датчика:

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "Authorization: Bearer $TOKEN"
```

Дальше `temperature` идёт синусоидой. Значение пересекает `threshold`, `alarmActive` становится true, учебное правило пишет событие в журнал.

---

## MQTT-устройство молчит на локальном профиле

Профиль `local` брокер не поднимает, а собственный MQTT-клиент платформы выключен (`ispf.mqtt.enabled=false`). `DEVICE` с драйвером `mqtt` всё равно нужен брокер, который запускаете вы. Профиль `dev` ждёт Mosquitto из `docker compose` на порту `1883`. Бандл, у которого хост брокера — заглушка вроде `mqtt-broker.example.invalid`, не подключится, пока этот хост не станет брокером, который вы запустили.

---

## `ispf validate --strict` завершается с ошибкой

Из каталога исходников, после `npm install` в `tools/ispf-cli`:

```bash
node tools/ispf-cli/bin/ispf.mjs validate <каталог-решения> --local --strict
```

`LOGIC_HOST_DEVICE` значит, что прикладная логика лежит на объекте типа `DEVICE`. `DEVICE` — это драйвер и его теги. Оркестратор — `SINGLETON` (один узел на решение) или `INSTANCE` (свой двойник на каждую установку). Без `--strict` при авторской проверке это предупреждение. `--strict` и каталог marketplace делают это ошибкой. [Правила архитектуры](application-principles.md), [Частые ошибки при проектировании](anti-patterns.md).

`HEAVY_WIDGET_EXPRESSION` значит, что поле виджета `expression`, `condition` или `script` длиной 400 символов или больше. Перенесите расчёт в привязку или функцию узла-оркестратора. Виджет только показывает переменную.

---

## Тренд пустой

История пишется только если у переменной включён `historyEnabled`. На локальном профиле хранилище — файл H2, не ClickHouse.

Дисковый буфер перед единственным хранилищем ClickHouse (`ispf.variable-history.spool`) есть и **выключен**. К JDBC и к двойной записи он не относится. Это не вторая копия буфера на другом узле ISPF, и кластер ClickHouse платформа не создаёт. Метрика `oldest_age_ms` у догонки двойной записи — возраст незакрытого диапазона догонки, а не отставание ClickHouse от PostgreSQL. [История переменных](variable-history.md), [Известные ограничения](known-limitations.md).

---

## Выкладка и остальные вызовы API отвечают 401

Возьмите токен у того сервера, который вызываете:

```bash
curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

Передавайте его как `Authorization: Bearer <token>`. `ISPF_BASE_URL` должен быть тем же адресом (по умолчанию `http://localhost:8080`). Токен с другого хоста здесь не принимается. На сервере с `oidc` нужен токен поставщика идентификации, не `admin` / `admin`.

---

## Связанное

- [Быстрый старт](getting-started.md)
- [Безопасность](security.md)
- [Драйверы устройств](drivers.md)
- [Известные ограничения](known-limitations.md)
