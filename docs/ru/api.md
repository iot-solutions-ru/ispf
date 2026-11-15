> **Язык:** русская версия. Английский оригинал: [en/api.md](../en/api.md).

# Справочник REST API (v1)

> **Статус:** Stable — основные HTTP-маршруты и WebSocket-каналы сервера ISPF. Метки зрелости: [doc-status.md](doc-status.md).

Базовый адрес по умолчанию: `http://localhost:8080`

**Аутентификация:** в заголовке `Authorization: Bearer <токен>` передаётся токен, полученный после вызова `POST /api/v1/auth/login` (в локальном профиле создаются тестовые учётные записи `admin`/`admin`, `developer`/`developer`, `operator`/`operator`; в промышленном профиле также поддерживаются JWT-токены от Keycloak). Подробная таблица прав по ролям приведена в разделе **[Безопасность и RBAC](security.md)**.

---

## 1. Общие сведения о платформе и настройки сервера

| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/info` | публичный | Имя, версия сервера, версии Java и Spring Boot, список включённых возможностей (`capabilities[]`) |
| `GET` | `/api/v1/auth/me` | публичный | Текущий аутентифицированный пользователь и его роли |
| `POST` | `/api/v1/auth/login` | публичный | Вход по логину и паролю (и коду TOTP, если у пользователя включена двухфакторная защита) |
| `GET` | `/api/v1/platform/metrics` | `admin` | Сводные метрики сервера (JVM, БД, дерево объектов, драйверы, очереди автоматизации, архив историй) |
| `GET` | `/api/v1/platform/effective-config` | `admin` | Активный профиль развёртывания и фактические размеры пулов потоков |
| `GET` | `/api/v1/platform/runtime-settings` | `admin` | Чтение изменяемых настроек времени выполнения |
| `PATCH` | `/api/v1/platform/runtime-settings` | `admin` | Сохранение настроек времени выполнения |
| `POST` | `/api/v1/platform/runtime-settings/restart` | `admin` | Запланировать перезапуск процесса сервера |
| `GET` | `/api/v1/platform/update/status` | `admin` | Статус проверки новых релизов на GitHub |
| `POST` | `/api/v1/platform/update/check` | `admin` | Принудительная проверка наличия новой версии |
| `POST` | `/api/v1/platform/update/apply` | `admin` | Скачивание релиза и перезапуск службы (при `apply-enabled=true`) |
| `POST` | `/api/v1/expressions/validate` | `admin` | Проверка синтаксиса и формальной выполнимости выражения CEL |
| `GET` | `/api/v1/platform/haystack/export` | `operator+` | Выгрузка поддерева в формате Project Haystack (`rootPath`, `includePoints`) |
| `GET` | `/api/v1/platform/haystack/query` | `operator+` | Поиск объектов и точек по фильтру Haystack (`filter`, `rootPath`, `entityKind`, `offset`, `limit`) |

Пример запроса по семантическим тегам Haystack:

```http
GET /api/v1/platform/haystack/query?filter=point+and+temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

---

## 2. Дерево объектов и переменные (`/api/v1/objects/**`)

| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/objects` | `operator+` | Список объектов (`?parent=<путь>` возвращает прямых потомков узла) |
| `GET` | `/api/v1/objects/by-path` | `operator+` | Свойства узла по его полному пути (`?path=...`) |
| `GET` | `/api/v1/objects/by-path/editor` | `operator+` | Полное описание узла вместе с переменными, событиями и функциями |
| `POST` | `/api/v1/objects` | `admin` | Создать новый узел в дереве |
| `PATCH` | `/api/v1/objects/by-path` | `admin` | Изменить отображаемое имя (`displayName`) и описание узла |
| `DELETE` | `/api/v1/objects/by-path` | `admin` | Удалить узел вместе с его поддеревом |
| `PUT` | `/api/v1/objects/reorder` | `admin` | Изменить порядок дочерних узлов внутри папки (`parentPath`, `orderedPaths`) |
| `GET` | `/api/v1/objects/by-path/variables` | `operator+` | Список всех переменных узла и их текущих значений |
| `GET` | `/api/v1/objects/by-path/variables/detail` | `operator+` | Метаданные и значение одной переменной (`?path=...&name=...`) |
| `PUT` | `/api/v1/objects/by-path/variables` | `operator+` / `admin` | Записать новое значение переменной (для оператора — если переменная помечена как `writable`) |
| `GET` | `/api/v1/objects/by-path/variables/history` | `operator+` | Сырые точки истории переменной за период (`path`, `name`, `field`, `from`, `to`, `limit`) |
| `GET` | `/api/v1/objects/by-path/variables/history/aggregate` | `operator+` | Агрегаты истории по интервалам (`bucket`, `from`, `to`, `limit`: `avg`, `min`, `max`, `count`) |
| `GET` | `/api/v1/objects/by-path/variables/history/export` | `operator+` | Выгрузка истории переменной в файл (`format=csv\|json`, до 10 000 точек) |
| `PATCH` | `/api/v1/objects/by-path/variables/history` | `admin` | Включить запись истории (`historyEnabled`) и задать срок хранения (`historyRetentionDays`) |

### Пример создания устройства

```http
POST /api/v1/objects
Content-Type: application/json

{
  "parentPath": "root.platform.devices",
  "name": "pump-01",
  "type": "DEVICE",
  "displayName": "Насос №1",
  "description": "Подающий насос первого контура",
  "templateId": "mqtt-sensor-v1",
  "driverId": "mqtt",
  "autoApplyMixinBlueprints": true
}
```

---

## 3. Функции, дашборды и процессы BPMN

### Вызов функций на объектах
| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `POST` | `/api/v1/objects/by-path/functions/invoke` | `operator+` | Вызвать функцию узла дерева (`path`, `name`, входной `DataRecord`) |
| `POST` | `/api/v1/bff/invoke` | `operator+` | Универсальный шлюз BFF для вызова прикладных функций из интерфейса (`wireProfile: "ispf-operator-v1"`) |

### Дашборды (`DASHBOARD`)
| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/dashboards/by-path` | `operator+` | Получить заголовок, JSON-макет (`layoutJson`) и период обновления дашборда |
| `PUT` | `/api/v1/dashboards/by-path/layout` | `admin` | Сохранить макет виджетов дашборда |
| `PUT` | `/api/v1/dashboards/by-path/title` | `admin` | Изменить заголовок дашборда |

### Процессы BPMN (`WORKFLOW`) и очередь задач смены (`Work Queue`)
| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/workflows/by-path` | `operator+` | Получить XML-схему BPMN, статус процесса и список активных экземпляров |
| `PUT` | `/api/v1/workflows/by-path/bpmn` | `admin` | Сохранить диаграмму BPMN XML |
| `PUT` | `/api/v1/workflows/by-path/status` | `admin` | Переключить статус процесса (`DRAFT`, `ACTIVE`, `STOPPED`) |
| `POST` | `/api/v1/workflows/by-path/run` | `admin` | Запустить новый экземпляр процесса |
| `POST` | `/api/v1/workflows/instances/{instanceId}/cancel` | `operator+` | Отменить активный экземпляр процесса |
| `GET` | `/api/v1/work-queue` | `operator+` | Список открытых задач оператора из процессов BPMN |
| `POST` | `/api/v1/work-queue/claim` | `operator+` | Взять задачу в работу (`taskId`, `operatorId`) |
| `POST` | `/api/v1/work-queue/complete` | `operator+` | Завершить задачу и продвинуть процесс BPMN дальше |

---

## 4. Прикладные решения (`bundle.json`), отчёты и расписания

Подробное описание контракта пакетов приведено в разделе **[Прикладные решения (`applications.md`)](applications.md)**.

| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `POST` | `/api/v1/applications` | `admin` | Зарегистрировать новое приложение и его SQL-схему |
| `POST` | `/api/v1/applications/{appId}/deploy` | `admin` | Развернуть полный пакет решения `bundle.json` |
| `POST` | `/api/v1/applications/{appId}/bundle/validate` | `admin` | Проверить манифест `bundle.json` (`?strict=true\|false`) |
| `GET` | `/api/v1/applications/{appId}/export` | `admin` | Выгрузить установленный манифест приложения |
| `POST` | `/api/v1/applications/{appId}/data/migrate` | `admin` | Выполнить SQL-миграции в изолированной схеме приложения |
| `GET` | `/api/v1/applications/{appId}/data/status` | `admin` | Статус применённых SQL-миграций приложения |
| `POST` | `/api/v1/applications/{appId}/functions/deploy` | `admin` | Зарегистрировать или обновить скриптовую функцию |
| `GET` | `/api/v1/applications/{appId}/reports` | `admin` | Список отчётов приложения |
| `POST` | `/api/v1/applications/{appId}/reports/{reportId}/run` | `operator+` | Выполнить отчёт и получить строки выборки |
| `GET` | `/api/v1/applications/{appId}/reports/{reportId}/export` | `operator+` | Скачать отчёт в формате CSV, XLSX, PDF или HTML |
| `GET / POST` | `/api/v1/schedules` | `admin` | Список и создание фоновых расписаний |

---

## 5. События, правила тревог, корреляторы, драйверы и шаблоны

### Журнал событий (`/api/v1/events/**`)
| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/events` | `operator+` | Чтение журнала событий (`objectPath`, `limit` ≤ 200) |
| `POST` | `/api/v1/events/fire` | `operator+` | Опубликовать событие на объекте |
| `POST` | `/api/v1/events/journal/purge` | `admin` | Очистить записи журнала событий старше указанной даты (`olderThan`) |

### Правила тревог (`ALERT`) и корреляторы (`CORRELATOR`)
| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET / POST` | `/api/v1/alert-rules` | `admin` | Список и создание правил тревог |
| `GET / PUT / DELETE` | `/api/v1/alert-rules/by-path?path=` | `admin` | Чтение, изменение и удаление правила тревоги по пути |
| `GET / POST` | `/api/v1/correlators` | `admin` | Список и создание корреляторов событий |
| `GET / PUT / DELETE` | `/api/v1/correlators/by-path?path=` | `admin` | Чтение, изменение и удаление коррелятора по пути |

### Драйверы устройств и шаблоны (`Blueprints`)
| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/drivers` | `operator+` | Каталог установленных драйверов протоколов |
| `GET` | `/api/v1/drivers/runtime/status` | `operator+` | Состояние цикла опроса устройства (`?devicePath=...`) |
| `PUT` | `/api/v1/drivers/runtime/configure` | `admin` | Сохранить параметры подключения и привязку регистров драйвера |
| `POST` | `/api/v1/drivers/runtime/start` (`/stop`) | `admin` | Запустить или остановить опрос устройства |
| `GET / POST / PUT / DELETE` | `/api/v1/blueprints/**` | `admin` | Управление шаблонами объектов (`SINGLETON`, `INSTANCE`, `MIXIN`) |

---

## 6. Аналитические запросы по истории (`/api/v1/platform/analytics/**`)

| Метод | Путь | Роль | Назначение |
|-------|------|------|------------|
| `GET` | `/api/v1/platform/analytics/tags?path=` | `operator+` | Каталог развёрнутых аналитических тегов (`kind: historian`) |
| `GET` | `/api/v1/platform/analytics/tags/by-path?path=` | `operator+` | Метаданные и граф зависимостей одного аналитического тега |
| `POST` | `/api/v1/platform/analytics/query` | `operator+` | Выровненный по времени агрегирующий запрос сразу по нескольким тегам (до 20 тегов) |
| `POST` | `/api/v1/platform/analytics/query/export?format=csv\|parquet` | `operator+` | Выгрузка результата многотегового запроса в CSV или Parquet |
| `POST` | `/api/v1/platform/analytics/tags/backfill` | `admin` | Пересчёт исторического тега за прошлый период |
| `GET` | `/api/v1/platform/analytics/catalog` | `operator+` | Единый каталог аналитических функций (уровни A, B, C) |
| `POST` | `/api/v1/platform/analytics/expression/validate` | `operator+` | Проверка аналитического выражения |

---

## 7. WebSocket и стандартные коды ответов

- **Подписка на живые обновления дерева объектов:** `WS /ws/objects` (с заголовком `Sec-WebSocket-Protocol: ispf-bearer, <токен>`). Подробный формат команд подписки приведён в разделе **[Шины сообщений (`messaging.md`)](messaging.md)**.
- **Коды ответов HTTP:** `200 OK` (успех), `400 Bad Request` (ошибка валидации входных данных или манифеста), `401 Unauthorized` / `403 Forbidden` (недостаточно прав), `404 Not Found` (узел дерева не найден), `409 Conflict` (конфликт версий `revision`), `423 Locked` (ветка дерева взята в аренду другим инженером).
