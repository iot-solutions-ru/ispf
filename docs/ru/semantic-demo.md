> **Язык:** русская версия. Английский оригинал: [en/semantic-demo.md](../en/semantic-demo.md).

# Быстрый старт семантической разметки оборудования (Project Haystack и Brick Schema)

> **Статус:** Stable — пошаговый пример разметки устройства тегами Project Haystack и классами Brick Schema с автоматической привязкой виджетов дашборда за 5 минут. Метки зрелости: [doc-status.md](doc-status.md).

В ISPF узлы дерева объектов и точки телеметрии можно снабжать стандартными семантическими метаданными **Project Haystack** и **Brick Schema**, чтобы находить однотипные датчики фильтр-запросами (`point and temp`) и автоматически привязывать их к виджетам на дашбордах ([ADR-0021](decisions/0021-haystack-semantic-overlay.md), [ADR-0023](decisions/0023-haystack-query-runtime.md)).

---

## Пошаговый сценарий (5 минут)

### Шаг 1. Подключите шаблоны-примеси (`MIXIN`) к устройству

Откройте нужное устройство в **Проводнике → Чертежи (Шаблоны)** и примените две стандартные примеси (на учебном устройстве `root.platform.devices.lab-userA-01` они уже подключены по умолчанию):

| Шаблон-примесь (`MIXIN`) | Какие переменные добавляет |
|--------------------------|----------------------------|
| `haystack-metadata-v1` | `haystackTags` (маркеры), `haystackRef`, `haystackKind` |
| `brick-metadata-v1` | `brickClass` (URI класса в онтологии Brick) |

То же действие через REST API:

```http
POST /api/v1/mixin-blueprints/{blueprintId}/apply?objectPath=root.platform.devices.my-device
```

### Шаг 2. Отметьте теги Project Haystack

На вкладке инспектора **«Haystack»** выберите маркеры уровня оборудования (`equip`, `lab`, `site`) и укажите теги точек измерения (`point`, `sensor`, `temp`) и единицы измерения (`°C` для переменной `sineWave`).

### Шаг 3. Автоматически определите класс Brick Schema

На вкладке **«Brick»** нажмите кнопку автоопределения (вызывает `GET /api/v1/platform/brick/infer?objectPath=...`) — сервер предложит подходящий класс онтологии (например, `brick:Sensor` или `brick:Temperature_Sensor`). Сохраните выбранный класс.

### Шаг 4. Проверьте поисковый запрос по тегам Haystack

```http
GET /api/v1/platform/haystack/query?filter=point%20and%20temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

Выгрузить полное семантическое описание ветки дерева:

```http
GET /api/v1/platform/haystack/export?rootPath=root.platform.devices.lab-userA-01&includePoints=true
```

### Шаг 5. Автоматически привяжите найденные точки к дашборду

В конструкторе дашбордов откройте окно **«Привязка по тегам Haystack»**, введите фильтр `point and temp` — найденные точки температуры автоматически привяжутся к карточкам значений и графикам.

### Шаг 6. Выгрузите граф в формате Brick JSON-LD / Turtle (при необходимости)

В разделе **Система → Семантический экспорт** или запросом API:

```http
GET /api/v1/platform/brick/export?rootPath=root.platform.devices.lab-userA-01&format=jsonld&includePoints=true
```

Проверить работу семантического контура автотестом:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.platform.SemanticRoundtripIntegrationTest"
```
