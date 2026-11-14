> **Язык:** русская версия. Английский оригинал: [en/external-ide.md](../en/external-ide.md).

# Разработка собственного React-интерфейса оператора (внешняя IDE и `ui-pack`)

> **Статус:** Stable — пакеты веб-интерфейса ([ADR-0054](decisions/0054-hosted-ui-packs.md)) и подключение IDE по MCP ([ADR-0006](decisions/0006-mcp-agent-tool-adapter.md)). Метки зрелости: [doc-status.md](doc-status.md).

Встроенный конструктор дашбордов и редактор мнемосхем закрывают типовые пульты диспетчера и схемы P&ID без программирования фронтенда. Если же вашему проекту нужен полностью индивидуальный веб-интерфейс — интерактивные геокарты, 3D-сцены цеха, фирменная дизайн-система или плотные производственные доски MES — вы пишете обычное **одностраничное приложение на React + Vite (SPA)** в любой удобной среде разработки (Cursor, VS Code, WebStorm) и устанавливаете его на сервер ISPF как пакет **`ui-pack`**.

По этой схеме построены готовые отраслевые решения на платформе (`Oil Control`, `FarmTwin`, `StoreTwin`, `WarehouseTwin`, мониторинг ИТ-инфраструктуры).

Связанные разделы: [Руководство разработчика решений](solution-developer-guide.md) · [Правила архитектуры приложений](application-principles.md) · [API приложений](applications.md) · [Каталог решений (Marketplace)](marketplace.md).

---

## Как разделена ответственность

Исходный код вашего фронтенда живёт в собственном репозитории — **никогда** не добавляйте отраслевые экраны внутрь `apps/web-console` или Java-контроллеры внутрь `packages/ispf-server`:

| Слой | Что разрабатываете вы | Что даёт сервер ISPF |
|------|-----------------------|----------------------|
| **Веб-интерфейс** | Приложение Vite + React, собранное в zip-архив `ui-pack` | Раздача статики по адресу `/apps/<appId>/` (`HostedUiPackFilter`) |
| **Логика и данные** | Функции на узле логики (`script` / SQL), формулы CEL, тревоги, BPMN (`bundle.json`) | Шлюз `POST /api/v1/bff/invoke`, изолированная схема БД, архив историй, WebSocket |
| **Поставка на стенд** | `bundle.json` + архив `<app>-ui.zip` | Проверка пакета, атомарный деплой и установка из каталога решений |

```text
Ваше React-приложение (/apps/<appId>/)   Сервер ISPF
  POST /api/v1/auth/login        ──►     Авторизация и выдача токена
  POST /api/v1/bff/invoke        ──►     Вызов функций на узле SINGLETON / INSTANCE
  WS /ws/objects                 ──►     Живые обновления переменных и тревог (по желанию)
```

### Когда выбирать `ui-pack`, а когда встроенные дашборды

| Критерий | Собственное приложение React (`ui-pack`) | Встроенные дашборды и мнемосхемы SCADA |
|----------|------------------------------------------|----------------------------------------|
| **Для каких задач** | Тиражируемый отраслевой продукт, карты, 3D, сложные формы и таблицы | Технологические схемы P&ID, графики трендов, быстрый пульт КИПиА/SNMP |
| **Где создаётся** | Отдельный фронтенд-репозиторий в IDE | Прямо в браузере (Dashboard / Mimic Builder) |
| **Как открывает оператор** | `/apps/<appId>/` (или кнопка **Open app UI** в режиме оператора) | `?mode=operator&app=<appId>` |

Оба подхода можно сочетать: основной интерфейс поставлять как `ui-pack`, а в `bundle.json` оставить простой обзорный дашборд на случай, если веб-пакет ещё не установлен на стенде.

---

## 1. Структура репозиториев проекта

Держите фронтенд-репозиторий отдельно от папки конфигурации ISPF:

```text
my-app-web/                         # Репозиторий фронтенда (React + Vite)
  src/
  vite.config.ts                    # base: '/apps/my-app/'
  openapi.yaml                      # Спецификация функций узла логики для фронтенда
  package.json                      # Скрипт "pack:ui" для сборки zip-архива

my-app-solution/                    # Папка серверного решения ISPF (под ispf-cli)
  solution.json
  sql/
  functions/
  bundle.json                       # Результат команды `ispf pack`
  ui-pack/
    ui-pack.json
    my-app-ui-1.0.0.zip
```

---

## 2. Подключение IDE (Cursor / VS Code) к серверу по MCP (по желанию)

Если вы используете ИИ-помощника в IDE, подключите его к штатному интерфейсу **MCP** вашего локального или тестового сервера ISPF — тогда IDE сможет сама смотреть пути объектов, схемы переменных и проверять `bundle.json` ([ИИ-студия и MCP](ai-development.md)):

1. Включите профиль `mcp` на сервере (`spring.profiles.include=mcp` или `ispf.mcp.enabled=true`).
2. Получите токен администратора через `POST /api/v1/auth/login`.
3. Добавьте подключение в настройки MCP вашей IDE:

```json
{
  "mcpServers": {
    "ispf": {
      "url": "http://localhost:8080/api/v1/ai/mcp",
      "headers": {
        "Authorization": "Bearer <admin-token>"
      }
    }
  }
}
```

---

## 3. Обмен данными между React и ISPF (HTTP и WebSocket)

### Авторизация

Собственные веб-пакеты выполняют вход стандартным запросом:

```http
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "operator", "password": "operator" }
```

Сохраните полученный `token` и передавайте заголовок `Authorization: Bearer <token>` во всех запросах (при ответе `401` возвращайте пользователя на страницу входа).
*(Примечание: сторонние партнёрские пакеты с ограниченным уровнем доверия `partner` / `signedPartner` не вызывают `/api/v1/auth/login` напрямую, а работают по ограниченному BFF-токену с полем `uiPackAppId` — см. [ADR-0073](../en/decisions/0073-hosted-ui-pack-trust.md).)*

### Вызов серверных функций (`POST /api/v1/bff/invoke`)

Все данные для экранов и команды записи проходят через единый метод `/api/v1/bff/invoke`, указывающий на ваш узел логики типа **`SINGLETON`** или **`INSTANCE`** (но не `DEVICE`):

```http
POST /api/v1/bff/invoke
Authorization: Bearer <token>
Content-Type: application/json

{
  "objectPath": "root.platform.singleton-blueprints.my-app-hub",
  "functionName": "my_listItems",
  "input": {
    "schema": { "name": "in", "fields": [] },
    "rows": [{}]
  },
  "wireProfile": "ispf-operator-v1"
}
```

При успешном вызове сервер возвращает `error_code === "OK"` и данные в поле `result`. Подробности протокола: [API приложений § BFF](applications.md#bff-req-pf-06). Рекомендуется давать функциям префикс проекта (`my_getKpi`, `my_ackAlarm`).

### Живые обновления показаний (WebSocket)

Чтобы не опрашивать сервер таймерами из браузера, откройте соединение `WS /ws/objects` с заголовком `Sec-WebSocket-Protocol: ispf-bearer, <token>` и подпишитесь на нужные пути устройств и узлов расчёта. Подробнее: [Шины сообщений](messaging.md).

### Настройка Vite для локальной разработки

Задайте в `vite.config.ts` базовый путь `'/apps/<appId>/'` (и такой же `basename` у React Router), а запросы `/api` и `/ws` проксируйте на локальный сервер ISPF:

```ts
import { defineConfig } from 'vite';

export default defineConfig({
  base: '/apps/my-app/',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
```

При локальной вёрстке интерфейс будет доступен по адресу `http://localhost:5173/apps/my-app/`.

---

## 4. Описание маршрутов экранов в `bundle.json` (`operatorUi`)

Перечислите маршруты вашего React-приложения и вызываемые ими функции в секции `operatorUi` файла `bundle.json`:

```json
"operatorUi": {
  "title": "Моё приложение",
  "spaNav": [
    { "to": "/", "label": "Обзор", "bff": ["my_getKpi", "my_listItems"] },
    { "to": "/alarms", "label": "Тревоги", "bff": ["my_listAlarms", "my_ackAlarm"] }
  ],
  "externalSpaUrl": "/apps/my-app/",
  "uiPack": {
    "packId": "my-app-ui",
    "version": "1.0.0",
    "entry": "index.html"
  }
}
```

Добавление нового экрана сводится к трём понятным шагам: маршрут в React + строка в `spaNav` + функция на узле логики. Никаких новых HTTP-маршрутов на самом сервере создавать не нужно.

---

## 5. Сборка и установка архива `ui-pack`

В **корне** zip-архива (рядом с `index.html` и папкой `assets/`, без лишней вложенной папки `dist/`) должен лежать паспорт `ui-pack.json`:

```json
{
  "appId": "my-app",
  "packId": "my-app-ui",
  "version": "1.0.0",
  "entry": "index.html",
  "basePath": "/apps/my-app/",
  "title": "Моё приложение",
  "minIspfVersion": "0.9.30"
}
```

Установить собранный пакет можно через API каталога:

```http
POST /api/v1/marketplace/ui-packs/{id}/install
```

либо распаковав содержимое архива на сервере в каталог `ISPF_UI_PACKS_DIR/my-app/`.

> **Важно при работе за внешним nginx:** если статика веб-консоли раздаётся через отдельный nginx, префикс `location ^~ /apps/` обязательно должен проксироваться (`proxy_pass`) на JVM-сервер `ispf-server` так же, как `/api/`. Иначе nginx отдаст вместо вашего приложения страницу консоли администратора ([ADR-0054](decisions/0054-hosted-ui-packs.md)).

---

## 6. Проверка готовности перед сдачей

- [ ] По адресу `/apps/<appId>/` открывается ваше приложение, а не `<title>ISPF Admin Console</title>`
- [ ] На всех страницах из `spaNav` вызовы `POST /api/v1/bff/invoke` возвращают `error_code === "OK"`
- [ ] Операции записи (создание записей, квитирование тревог) сохраняются в SQL-схеме приложения и сразу обновляют экран
- [ ] Кнопка **Open app UI** в режиме оператора (`/?mode=operator&app=<appId>`) переводит на `/apps/<appId>/`
- [ ] Вся серверная логика расположена на узле `SINGLETON` или `INSTANCE` (не `DEVICE`), а в `apps/web-console` и `packages/ispf-server` нет проектных правок
