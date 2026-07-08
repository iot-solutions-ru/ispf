> **Язык:** русская версия (вычитка). Канонический английский: [en/solution-developer-public-api.md](../en/solution-developer-public-api.md).

﻿# Публичный API для решений разработчиков

Стабильная граница между **платформой** (ядро ISPF) и **решением** (ваш пакет). Подробный рабочий процесс — [SOLUTION_DEVELOPER_GUIDE.md](solution-developer-guide.md). Архитектурные границы — [0001](decisions/0001-app-platform-boundary.md).

---

## Разрешено (стабильный контракт)

| Область | API / ссылки | Примечание |
|---------|----------------|------------|
| Регистрация app | `POST /api/v1/applications` | `appId`, `schemaName`, `tablePrefix` |
| SQL schema app | `POST .../data/migrate`, `.../data/seed` | Не Flyway platform |
| Bundle deploy | `POST .../deploy`, `POST /api/v1/platform/packages/import` | Один manifest JSON |
| Script functions | `POST .../functions/deploy`, tree path `{appId}.functions.*` | JSON script steps |
| Invoke | `POST .../functions/invoke`, `POST /api/v1/bff/invoke` | Operator wire profile |
| Object tree | `GET/POST/PATCH /api/v1/objects/**` | CRUD, variables, functions |
| Models | `POST /api/v1/blueprints`, apply to device | Blueprint |
| Панели мониторинга/рабочие процессы | Дерево объектов + REST | Макет JSON, BPMN XML |
| Автоматизация | Правила оповещения, корреляторы в деревне | CEL, шаблоны корреляторов |
| Operator UI | `operatorUi` / `dashboards[]` в bundle | `GET .../operator-ui` |
| Reports | Tree-first `root.platform.reports.*` | SQL + optional YARG |
| Вебсокет | `/ws/objects` — `subscribe`, `subscribe_events` | Параметр запроса токена; см. [MESSAGING.md](messaging.md) |
| Event catalog | `events[]` в bundle, `GET .../events` | Роли для WS subscribe (FW-31) |
| Bundle dependencies | `requires[]` в bundle | minVersion другого appId (FW-12) |
| Drivers | SPI `DeviceDriver` в отдельном JAR | [DRIVERS.md](drivers.md) |
| Инструменты искусственного интеллекта (администратор платформы) | `POST /api/v1/ai/tools/*`, Студия | [AI_DEVELOPMENT.md](ai-development.md) — не меняет контракт на стабильный пакет |

## Запрещено

| Действие | Почему |
|----------|--------|
| Java в `packages/ispf-server/` | [0001](decisions/0001-app-platform-boundary.md) |
| Таблицы приложений Flyway на платформе | Схема приложения только через API миграции |
| Отраслевые BFF routes | Только `/api/v1/bff/invoke` |
| Сервер платформы Fork для одного клиента | Пакетное развертывание + настройка |

## Манифест пакета (semver)

Поле `version` в манифесте — **semver** (`MAJOR.MINOR.PATCH`). Сравнение при `requires[].minVersion` и `license.minPlatformVersion` — числовой семвер (платформа `GET /api/v1/info`).

| Изменение | Уровень | Пример |
|-----------|---------|--------|
| Удаление/переименование function, event, object | **MAJOR** | удалён `mes_listOrders` |
| Изменение схемы ввода/вывода относительной функции | **ГЛАВНЫЙ** | новое обязательное поле ввода |
| Добавление optional events, objects, dashboards | **MINOR** | новый `events[]` id |
| Описания исправлений, начальные данные, неразрывный скрипт | **ИСПРАВЛЕНИЕ** | текстовый операторUi |

| Поле | Стабильность |
|------|--------------|
| `version` | Semver bundle; история deploy |
| `schemaName`, `migrations`, `functions` | Stable |
| `objects`, `dashboards`, `workflows`, `models` | Stable (Phase 5 tree-first) |
| `alertRules`, `correlators`, `operatorUi` | Stable |
| `events[]` | Stable — id, roles, optional payloadSchema |
| `requires[]` | Stable — appId, minVersion |
| `license` | Необязательный; коммерческое — [COMMERCIAL_LICENSING.md](commercial-licensing.md) |

## Версия платформы

- `GET /api/v1/info` → `version` — для `minPlatformVersion` в license.
- Совместимость: platform ≥ `minPlatformVersion` в commercial license.

## Коммерческий пакет

Секция `license` в манифесте — см. [0003](decisions/0003-commercial-bundle-licensing.md). Эталонные пакеты Apache (`warehouse`, `lab-training`, `mes-reference`) — без `license`.

## Связанные документы

- [APPLICATIONS.md](applications.md) — полный API развертывания.
- [MESSAGING.md](messaging.md) — асинхронность и синхронизация, субъекты NATS, события WS
- [AI_DEVELOPMENT.md](ai-development.md) — уровень AI (FW-40…43), инструменты администратора.
- [API.md](api.md) — справочник REST
- [PLUGINS.md](plugins.md) — границы `main`
