> **Язык:** русская версия (вычитка). Канонический английский: [en/semantic-demo.md](../en/semantic-demo.md).

# Семантическое демонстрационное прохождение (S23-04/BL-105)

Цель: первая семантическая дашборд из запроса Haystack за **≤5 минут** (ускорение KPI S23-04).

## Предварительные условия

- ISPF server + Web Console running (`local` profile or lab fixtures).
- Роль оператора (семантические API требуют аутентифицированного сеанса в продукте; тестовый профиль позволяет использовать MockMvc/CI).

## шагов (≈5 мин)

### 1. Применить миксины (новое устройство)

В **Проводнике** → Устройство → **Чертежи** примените:

| Миксин | Цель |
| ----- | ------- |
| `haystack-metadata-v1` | `haystackTags`, `haystackRef`, `haystackKind` |
| `brick-metadata-v1` | `brickClass` URI |

Lab shortcut: `root.platform.devices.lab-userA-01` already has both mixins from bootstrap.

```http
POST /api/v1/relative-blueprints/{blueprintId}/apply?objectPath=root.platform.devices.my-device
```

### 2. Установите теги Haystack

Вкладка инспектора **Стога сена** → множественный выбор маркера: `equip`, `lab`, `site` на устройстве; удлините `driverPointMappingsJson` с помощью тегов точек (`point`, `sensor`, `temp`) и единиц измерения.

Demo device tags `sineWave` with `temp` → °C.

### 3. Выведите и установите класс Brick

Вкладка **Кирпич** → **Вывод** (вызов `GET /api/v1/platform/brick/infer?objectPath=…`) → применить предложение → сохранить `brickClass` (демо: `brick:Sensor`).

Вывод только по тегам (без объекта):

```http
GET /api/v1/platform/brick/infer?tags=equip,meter,energy&haystackKind=equip
```

### 4. Запрос Haystack

```http
GET /api/v1/platform/haystack/query?filter=point%20and%20temp&rootPath=root.platform.devices.lab-userA-01&entityKind=point
```

Экспортировать сетку (тот же индекс тега):

```http
GET /api/v1/platform/haystack/export?rootPath=root.platform.devices.lab-userA-01&includePoints=true
```

### 5. Автоматическая привязка панели управления

Конструктор дашбордов → Диалоговое окно **Привязка Haystack** → фильтр `point and temp` → привязка виджетов значений/диаграмм к совпадающим точкам (BL-103).

### 6. Семантический экспорт (необязательно)

Платформа → **Семантический экспорт**: Haystack JSON + Brick JSON-LD/Turtle для поддерева.

```http
GET /api/v1/platform/brick/export?rootPath=root.platform.devices.lab-userA-01&format=jsonld&includePoints=true
```

Brick graph uses `ispf:path` and `urn:ispf:platform:…` IRIs aligned with `BrickExportService.entityIri`; `temp` points map to `brick:Temperature_Sensor`.

## API туда и обратно (CI/S23-03)

`SemanticRoundtripIntegrationTest` verifies for `lab-userA-01`:

- Haystack export ↔ `haystack/query` filter results
- Блок JSON-LD `@graph` узлов для каждого отмеченного оборудования (когда `brickClass` установлен) и `temp` очков
- `@id` / `ispf:path` alignment with `BrickExportService.entityIri`
- `GET /api/v1/platform/brick/infer` for object path and tag-only mode

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.platform.SemanticRoundtripIntegrationTest"
```

## Скрипт дыма

Против работающего сервера:

```bash
bash tools/semantic-demo-check.sh
# or: ISPF_BASE_URL=${ISPF_BASE_URL:-https://ispf.example.invalid} bash tools/semantic-demo-check.sh
```

## Ссылки

- [ADR-0021 Семантическое наложение Haystack](decisions/0021-haystack-semantic-overlay.md)
- [ADR-0023 Время выполнения запроса Haystack](decisions/0023-haystack-query-runtime.md)
