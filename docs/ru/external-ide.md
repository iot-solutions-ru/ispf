> **Язык:** русская версия (вычитка). Канонический английский: [en/external-ide.md](../en/external-ide.md).

# Разработка решений во внешней AI IDE

> **Статус:** Stable — Hosted UI packs (ADR-0054) + MCP (ADR-0006). Хаб: [doc-status.md](../en/doc-status.md).

**Ценность платформы:** отраслевые решения собираются **вне** `ispf-server` и `apps/web-console`. Партнёр работает в любой AI IDE (Cursor, VS Code, …) с неограниченным набором React-компонентов и поставляет **bundle** (логика) плюс **ui-pack** (SPA). Платформа хостит оба артефакта: BFF на дереве объектов и статику на `/apps/<appId>/`.

Так сделаны Oil Control, FarmTwin, StoreTwin и IT-мониторинг — это целевой путь, не обходной.

Связанные хабы: [application-principles](application-principles.md) (P2, P7, P9), [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md), [0054-hosted-ui-packs](decisions/0054-hosted-ui-packs.md), [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md), [solution-developer-guide](solution-developer-guide.md), [marketplace](marketplace.md).

**Агент:** `search_context(query=hosted ui-pack Cursor, topic=external-ide)`.

---

## Почему это возможность платформы

ISPF — middleware. Виджетный kit консоли (~42 типа, сетка 84×8) — **универсальный HMI** для лаборатории, SNMP и запасного SCADA. Продуктовый UI требует произвольный React: карты, 3D, design system, плотные таблицы, брендированная оболочка.

| Слой | Что принадлежит партнёру | Что принадлежит платформе |
|------|--------------------------|---------------------------|
| Пиксели | Vite/React SPA, любые компоненты | Раздача `/apps/<appId>/` (ui-pack) |
| Данные / правила | Script functions, SQL в схеме приложения, CEL, alerts, BPMN | Движки, BFF, дерево, historian, WS |
| Поставка | `bundle.json` + zip ui-pack | `validate → dry-run → deploy` / установка из marketplace |

**Запрещено в `main`:** отраслевой React в `apps/web-console`, отраслевая Java в `ispf-server`, захардкоженные BFF-маршруты, platform Flyway для таблиц приложения.

Два артефакта, одно решение:

```text
React SPA (внешняя IDE)           ISPF
  Vite base /apps/<appId>/          HostedUiPackFilter
  POST /api/v1/auth/login    ──►    сессия
  POST /api/v1/bff/invoke    ──►    script functions на хабе
  WS /ws/objects             ──►    живые переменные (опционально)
```

Эталоны: `examples/oil-control` + `oil-control-ui`, `examples/farmtwin` + `farmtwin-ui`, `examples/storetwin` + `storetwin-ui`, `examples/marketplace-ui-pack-demo`.

---

## Выбор UI (не смешивать)

| | **Hosted SPA (эта страница)** | **Виджетный kit платформы** |
|---|------------------------------|-----------------------------|
| Когда | Продуктовый UI оператора, любой React | Lab, SNMP, запасной SCADA, HMI AI Studio |
| AUTHOR | Внешняя IDE, **отдельный** репозиторий SPA | Admin Console / встроенный агент |
| SHIP | zip ui-pack + application bundle | `dashboards[]` + `operatorUi` |
| Оператор | **Open app UI** → `/apps/<appId>/` | дашборды `?mode=operator&app=` |
| Исходники | Никогда в `apps/web-console` | виджеты `DASHBOARD.layout` |

Тонкий Operator-дашборд можно оставить fallback, если pack не установлен (паттерн FarmTwin). Бизнес-логику в виджетах не дублировать.

Слои P7 те же: сборка SPA — **AUTHOR**; bundle + ui-pack — **SHIP**. См. [application-principles P7](application-principles.md).

---

## 1. Подключить IDE к ISPF (MCP)

Внешняя IDE должна вызывать инструменты **ISPF**, а не чужой SCADA MCP. Профиль `mcp` отдаёт тот же ACL-aware реестр, что и AI Studio ([ai-development](ai-development.md)).

1. Включить MCP на сервере (`spring.profiles.include=mcp`, `ispf.mcp.enabled=true`).
2. Логин: `POST /api/v1/auth/login` → Bearer (admin для мутаций).
3. Cursor / MCP host:

```json
{
  "mcpServers": {
    "ispf": {
      "url": "https://<ispf-host>/api/v1/ai/mcp",
      "headers": { "Authorization": "Bearer <admin-token>" }
    }
  }
}
```

Локально: `http://localhost:8080/api/v1/ai/mcp`.

4. Проверка: есть `list_objects`, `validate_bundle`, `import_package`, `get_example_bundle`. Срезы ContextPack: `contextpack://bundle-manifest`, `contextpack://example-summaries`.

MCP — для **дерева и бандла**. Файловая система IDE — для **SPA**. Не просите агента править `apps/web-console` ради отраслевых экранов.

---

## 2. Раскладка репозиториев

Исходники SPA не входят в JAR платформы:

```text
my-app-web/                      ← workspace Cursor для UI
  src/
  vite.config.ts                 ← base: '/apps/my-app/'
  openapi.yaml                   ← контракт SPA ↔ BFF
  package.json                   ← pack:ui

ispf/  или соседний checkout     ← только bundle + zip pack
  examples/my-app/bundle.json
  examples/my-app-ui/ui-pack.json
  examples/my-app-ui/my-app-ui-x.y.z.zip
```

История git SPA не должна жить в `ispf-server`. Копии в `examples/marketplace-catalog/` — листинги и zip, не исходники Vite.

---

## 3. HTTP-контракт (SPA ↔ платформа)

### Auth

```http
POST /api/v1/auth/login
{ "username": "admin", "password": "admin" }
```

Хранить `token`; заголовок `Authorization: Bearer`. При 401 — экран логина. Same-origin после хостинга pack; в dev — proxy Vite.

### BFF — единственный бизнес-API

**Не** добавляйте контроллеры `/api/v1/<app>/…`. Каждый экран вызывает:

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

Успех: `error_code === "OK"`. Списки часто приходят как массив в `result`. Wire: [applications](applications.md).

Зафиксируйте `objectPath` и `functionName` в OpenAPI (см. `examples/oil-control/api/openapi.yaml`). Префикс функций (`oc_`, `ft_`, `st_`, `my_`).

**Тип хаба:** оркестратор SINGLETON (предпочтительно `root.platform.singleton-blueprints.{app}-hub`) или близнец INSTANCE — **никогда** `ObjectType.DEVICE`. В dogfood-примерах путь может быть `root.platform.devices.{app}.hub`; путь — выбор реализации, тип хаба не DEVICE.

### Живая телеметрия (опционально)

`WS /ws/objects` с `Sec-WebSocket-Protocol: ispf-bearer, <token>`. Подписка на пути устройств; протоколы из браузера не опрашивать. См. [messaging](messaging.md).

### Vite

```ts
export default defineConfig({
  base: '/apps/my-app/',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
```

`basename` React Router совпадает с `base`. Dev: `http://localhost:5173/apps/my-app/`. Опционально `VITE_DATA_SOURCE=mock` (StoreTwin).

Мост до установки pack: в бандле `operatorUi.externalSpaUrl` = `http://127.0.0.1:5173` (см. `examples/m11-monitor-ui`).

---

## 4. Карта экранов до вёрстки

Для каждого экрана оператора:

| SPA route | Подпись | BFF |
|-----------|---------|-----|
| `/` | Обзор | `my_getKpi`, `my_listItems` |
| `/alarms` | Аварии | `my_listAlarms`, `my_ackAlarm` |

Ту же карту сохранить в бандле (`operatorUi.spaNav`). Роуты React **должны** совпадать с `to`.

```json
"operatorUi": {
  "title": "My App",
  "spaNav": [
    { "to": "/", "label": "Обзор", "bff": ["my_getKpi", "my_listItems"] }
  ],
  "externalSpaUrl": "/apps/my-app/",
  "uiPack": { "packId": "my-app-ui", "version": "1.0.0", "entry": "index.html" }
}
```

`spaNav` — метаданные для Open app UI и агентов, сами по себе SPA не хостят.

Новый экран = новый route + строка `spaNav` + функция(и) на хабе. Не новый URL сервера.

---

## 5. Bundle (SHIP логики)

Канон: [solution-developer-guide](solution-developer-guide.md).

1. Регистрация `appId`, изолированная `schemaName`, `tablePrefix`.
2. `migrations[]` — только SQL приложения (не Flyway платформы).
3. Объект-хаб + `functions[]` (шаги `selectMany` / `insert` / `return`).
4. По необходимости DEVICE-дети, alerts, workflows, запасные дашборды.
5. `operatorUi` как выше.
6. Gates **в одном прогоне:** `validate_bundle` → `dry_run_deploy` → `import_package` или `POST /api/v1/applications/{appId}/deploy`.

Если референс покрывает ≥70% ТЗ — `get_example_bundle`.

---

## 6. UI pack (SHIP пикселей)

`ui-pack.json` в **корне** zip вместе с `index.html` и `assets/` (не обёртка `dist/`):

```json
{
  "appId": "my-app",
  "packId": "my-app-ui",
  "version": "1.0.0",
  "entry": "index.html",
  "basePath": "/apps/my-app/",
  "title": "My App",
  "minIspfVersion": "0.9.30"
}
```

`appId` — каталог установки (`ISPF_UI_PACKS_DIR/<appId>/`). Slug листинга может отличаться (`oil-control-ui` vs appId `oil-control`).

Сборка: `vite build`, затем zip от корня dist. В SPA-репозиториях Oil Control / StoreTwin: `npm run pack:ui`.

Установка:

```http
POST /api/v1/marketplace/ui-packs/{id}/install
```

Или drop-in в `ISPF_UI_PACKS_DIR/my-app/`. Поле листинга приложения `uiPackSlug` ставит BFF + SPA вместе ([marketplace](marketplace.md)).

Smoke: `GET /apps/my-app/` **не** должен отдавать заголовок Admin Console. Split nginx **обязан** проксировать `location ^~ /apps/` на JVM — [ADR-0054](decisions/0054-hosted-ui-packs.md).

---

## 7. Промпт для IDE (копировать)

Вставить в workspace SPA, чтобы модель не воспринимала ISPF как «монолит React, который надо расширять»:

```text
Это hosted UI pack ISPF (ADR-0054), не админ-консоль.

Стек: Vite + React, base и basename Router '/apps/<appId>/'.
Данные: только POST /api/v1/auth/login и POST /api/v1/bff/invoke на хаб.
Не добавляй Java в ispf-server и страницы в apps/web-console.
Новый экран = route + пункт spaNav + BFF-функция с префиксом приложения.
Контракт: openapi.yaml (enum functionName). Опционально VITE_DATA_SOURCE=mock.
Сборка: pack:ui → zip с ui-pack.json в корне архива.
Хаб логики — SINGLETON или INSTANCE, никогда DEVICE.
```

Для checkout платформы — вторая сессия агента с MCP ISPF: миграции, functions, `operatorUi`.

---

## 8. Приёмка

- [ ] Логин на `/apps/<appId>/` успешен; на каждом маршруте `spaNav` BFF `error_code=OK`
- [ ] Запись (ack, create) сохраняется в схеме приложения и обновляет UI
- [ ] Operator `/?mode=operator&app=<appId>` → **Open app UI** открывает pack (не оболочку консоли)
- [ ] `curl /apps/<appId>/` — SPA, не `<title>ISPF Admin Console</title>`
- [ ] Нет отраслевых файлов в `apps/web-console` и `packages/ispf-server`

---

## Анти-паттерны

| Анти-паттерн | Правильно |
|--------------|-----------|
| Отраслевые страницы в `apps/web-console` | Отдельное Vite-приложение + ui-pack |
| `@RestController` / Flyway под домен | `functions[]` + `migrations[]` + `/bff/invoke` |
| `base: '/'` в production | `base: '/apps/<appId>/'` |
| Правила только в React | SQL/CEL/alerts на платформе; SPA рисует |
| Хаб логики типа DEVICE | SINGLETON или INSTANCE |
| AggreGate или чужой MCP вместо `/api/v1/ai/mcp` | MCP ISPF с admin Bearer |
| Десять `add_dashboard_widget` как продуктовый UI | Kit — fallback; SPA — продукт |

---

## Связанные документы

| Документ | Назначение |
|----------|------------|
| [0054-hosted-ui-packs](decisions/0054-hosted-ui-packs.md) | Раздача `/apps/<appId>/`, nginx, PWA denylist |
| [0001-app-platform-boundary](decisions/0001-app-platform-boundary.md) | Нет отраслевой Java/React в `main` |
| [0006-mcp-agent-tool-adapter](decisions/0006-mcp-agent-tool-adapter.md) | Мост инструментов для внешней IDE |
| [ai-development](ai-development.md) | Включение MCP, пример JSON для Cursor |
| [applications](applications.md) | Bundle, BFF wire, миграции |
| [marketplace](marketplace.md) | `artifactKind: ui-pack`, `uiPackSlug` |
| [operator-apps](operator-apps.md) | Оболочка оператора; Open app UI |
| [widgets](widgets.md) / [dashboards](dashboards.md) | Запасной HMI kit |
| [agent-knowledge](agent-knowledge.md) | Подход **I** (hosted UI pack) |
