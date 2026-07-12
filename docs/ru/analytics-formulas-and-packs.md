> **Язык:** русская версия (вычитка). Канонический английский: [en/analytics-formulas-and-packs.md](../en/analytics-formulas-and-packs.md).

# Формулы analytics и пакеты расширений

Как создавать, переиспользовать и распространять historian-вычисления сверх одного binding rule. Охватывает **Tier A** (встроенные), **Tier B** (свои формулы) и **Tier C** (Java-пакеты + маркетплейс).

**См. также:** [analytics-historian-cookbook](analytics-historian-cookbook.md) (рецепты), [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md) (архитектура), [analytics-tag-catalog](analytics-tag-catalog.md) (API развёрнутых тегов).

> **Важно:** [historian-tiers](historian-tiers.md) описывает **уровни хранения** (hot/warm/cold). Здесь — **уровни функций** A/B/C из ADR-0042; это разные темы.

---

## Три уровня функций

| Уровень | Что это | Кто создаёт | Пример |
|---------|---------|-------------|--------|
| **A — Встроенные** | Core helpers + historian aggregates + open packs | Команда платформы | `avg(ref, 5m)`, `energyDelta` (core-ext) |
| **B — Своя формула** | Параметризованный шаблон выражения | Оператор / разработчик решения | `rateOfChange({{levelPath}}, 1h) * {{tankArea}}` |
| **C — Пакет расширений** | JAR с SPI `AnalyticsFunctionProvider` | Партнёр / ISV | `mes-oee-pack`, `water-quality-pack` |

**Принцип:** каталог — для **поиска и вставки**; **binding rule** на устройстве — развёрнутый экземпляр.

```
Каталог (browse)  →  параметры  →  правило на устройстве  →  live-переменная + historian-тег
```

---

## Tier A — Встроенные функции

### Синтаксис helper (historian)

Источники — **PlatformRef** slash-адреса ([0043-unified-platform-ref](decisions/0043-unified-platform-ref.md)).

| Функция | Пример | Назначение |
|---------|--------|------------|
| `avg` | `avg(root.devices.pump01/temperature, 5m)` | Скользящее среднее |
| `min` / `max` / `last` / `sum` | `min(root.devices.pump01/temperature, 1h)` | Агрегаты по buckets |
| `live` | `live(@/temperature)` | Текущее live-значение |
| `rateOfChange` | `rateOfChange(root.devices.pump01/level, 1h)` | Дельта avg первого→последнего bucket |
| `oee` | `oee('root.../line-01', 'avail', 'perf', 'qual', 8h)` | OEE % |
| `totalizer` | `totalizer(root.devices.pump01/energy, 1h)` | Сумма за окно |

### CEL + historian (`helper: cel`)

```text
avg(root.devices.pump01/temperature, 5m)
min(root.devices.pump01/temperature/value, 1h)
(avg(root.devices.sensor-a/temperature, 5m) + avg(root.devices.sensor-b/temperature, 5m)) / 2.0
live(@/temperature)
```

Доступны: `avg`, `min`, `max`, `sum`, `last`, `live`.

В CEL используйте **литералы double** (`2.0`, не `2`) при смешении с historian expansions.

### Где найти

- **Web console:** инспектор объекта → **Вычисления** → **+ Правило** → **Historian** → редактор → **каталог функций**.
- **API:** `GET /api/v1/platform/analytics/catalog` и `GET .../catalog/{functionId}`.

Новый aggregate-примитив **нельзя** добавить из UI — функции historian регистрируются на сервере.

---

## Tier B — Свои формулы

Tier B — **переиспользуемый шаблон**, не новый runtime-примитив. При компиляции разворачивается в синтаксис Tier A.

### Синтаксис плейсхолдеров

```text
avg({{sourceRef}}, {{window}})
rateOfChange({{levelRef}}, 1h) * {{tankArea}}
avg({{deviceRef}}/{{variable}}, {{window}})
```

Имена: `[a-zA-Z][a-zA-Z0-9_]*`. Сервер и UI определяют параметры из выражения автоматически.

### Создание в web console

**Из выражения:**

1. Откройте редактор historian-выражения (Вычисления → **+ Правило** → Historian).
2. Напишите выражение с `{{param}}`.
3. **Сохранить как формулу**.
4. Укажите **id**, **название**, **kind** (`historian` или `reactive`).
5. Сохранение → `@analyticsFormulas` на `root.platform`.

**Менеджер формул:**

- **Система → Формулы analytics** — список, создание, правка, удаление формул площадки.

### Применение формулы

1. В каталоге редактора найдите формулу (pack `site` или `app:<appId>`).
2. **Применить** → заполните параметры (пути тегов, окна, числа).
3. **Проверить** → **Применить** к правилу.

Правила хранят `formulaRef` + `formulaParams`. При обновлении формулы связанные правила **переразворачиваются** автоматически.

### Области хранения

| Область | Где | Кто |
|---------|-----|-----|
| Площадка | `root.platform`, переменная `@analyticsFormulas` | Platform admin |
| Приложение | `analytics-formulas.json` в bundle | Разработчик приложения |
| Blueprint | `analyticsFormulasJson` в RELATIVE blueprint | Автор шаблона |

### REST API

Базовый путь: `/api/v1/platform/analytics/formulas`

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/formulas?scope=site` | Список формул площадки |
| GET | `/formulas?scope=app&appId=` | Формулы приложения |
| GET | `/formulas/{id}?scope=&appId=` | Одна формула |
| POST | `/formulas` | Создать |
| PUT | `/formulas/{id}?scope=&appId=` | Обновить; в ответе `reboundRules` |
| DELETE | `/formulas/{id}?scope=&appId=` | Удалить |
| POST | `/formulas/{id}/expand` | Подставить параметры в шаблон |

**Тело expand:**

```json
{
  "scope": "site",
  "appId": null,
  "parameters": {
    "levelPath": "root.platform.devices.tank01.level",
    "tankArea": "12.5"
  }
}
```

**Пример записи формулы:**

```json
{
  "id": "tank-fill-rate",
  "displayName": "Скорость заполнения бака (м³/ч)",
  "kind": "historian",
  "expression": "rateOfChange({{levelPath}}, 1h) * {{tankArea}}",
  "parameters": [
    { "name": "levelPath", "type": "tagPath", "required": true },
    { "name": "tankArea", "type": "number", "required": false, "defaultValue": "1" }
  ],
  "scope": "site",
  "version": 1
}
```

### Ограничения Tier B

| Задача | Альтернатива |
|--------|--------------|
| Новая семантика агрегации по buckets | Tier C Java pack |
| Процедурная логика / внешний IO на каждый tick | Reactive + `callFunction` (только live) |
| Произвольный JS в historian | Не поддерживается (ломает детерминизм backfill) |

---

## Tier C — Пакеты расширений (Java SPI)

Для **новых historian-safe функций** с собственной логикой по buckets (сертифицированные отраслевые KPI).

### Раскладка на диске (планируется GA)

```text
${ISPF_ANALYTICS_PACKS_DIR}/
  acme-mes-kpi/
    analytics-pack.json
    acme-mes-kpi.jar
```

### `analytics-pack.json`

| Поле | Обяз. | Описание |
|------|:-----:|----------|
| `packId` | ✓ | Стабильный id (поле `pack` в каталоге) |
| `version` | ✓ | Semver |
| `licenseType` | ✓ | SPDX или commercial ref |
| `minPlatformVersion` | ✓ | Минимальная версия ISPF |
| `jarFile` | ✓ | Имя JAR в каталоге пакета |
| `functions[]` | ✓ | Helper id, которые экспортирует пакет |
| `license` | commercial | RSA-подписанные claims (как у [licensed-driver-packs](licensed-driver-packs.md)) |

### SPI-контракт

1. Реализовать `com.ispf.analytics.spi.AnalyticsFunctionProvider`.
2. Зарегистрировать в `META-INF/services/com.ispf.analytics.spi.AnalyticsFunctionProvider`.
3. Вернуть `AnalyticsFunctionDescriptor` + `AnalyticsEvaluator` (historian — только через `HistorianPort`).

**Эталон:** `packages/ispf-analytics-core-ext` — open pack `core-ext`, функция `energyDelta`.

### Runtime (сегодня)

- Open packs в classpath сервера загружаются при старте (`AnalyticsPackLoader`, `ServiceLoader`).
- Drop-in каталог + проверка лицензии по аналогии с driver packs — **БЛ-216** (см. roadmap).

### Конфигурация (планируется GA)

```yaml
ispf:
  analytics:
    packs-dir: ${ISPF_ANALYTICS_PACKS_DIR:/opt/ispf/analytics-packs}
  license:
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

---

## Покупка Tier C через маркетплейс

Коммерческие и community **analytics extension packs** распространяются по тому же контракту, что application bundles и symbol packs ([marketplace](marketplace.md)).

**Статус:** контракт листинга и поток для оператора описаны здесь; установка в `ISPF_ANALYTICS_PACKS_DIR` на платформе — **БЛ-216** (в работе). Open pack `energyDelta` уже в ядре без маркетплейса.

### Поток для оператора

| Шаг | Действие |
|-----|----------|
| 1 | **Система → Решения → Маркетплейс** — поиск по `tags: analytics`, `artifactKind: analytics-pack` |
| 2 | **Бесплатный пакет** — **Установить** → скачивание → распаковка в `ISPF_ANALYTICS_PACKS_DIR` → функции в каталоге analytics |
| 3 | **Платный пакет** — покупка у вендора → код активации → **Активировать** (как у paid app bundles) |
| 4 | **Проверка** — `GET /api/v1/platform/analytics/catalog` показывает helpers с `pack: <packId>` |
| 5 | **Развёртывание** — historian-правила с новыми helpers (напр. `batchYield(path.output, 8h)`) |

Без валидного ключа платный листинг показывает контакт вендора; функции не регистрируются до успешной активации.

### Манифест листинга (вендор)

```json
{
  "slug": "acme-mes-kpi",
  "title": "Acme MES KPI Pack",
  "description": "Historian-safe batch yield, downtime %, scrap rate.",
  "pricing": "paid",
  "artifactKind": "analytics-pack",
  "appId": null,
  "packId": "acme-mes-kpi",
  "vendorName": "Acme Analytics",
  "priceCents": 49900,
  "latestVersion": "1.0.0",
  "minIspfVersion": "0.9.127",
  "tags": ["analytics", "mes", "kpi", "historian"],
  "bundleArtifact": "acme-mes-kpi-1.0.0.zip"
}
```

Содержимое архива:

```text
acme-mes-kpi-1.0.0.zip
  analytics-pack.json
  acme-mes-kpi.jar
  LICENSE
  NOTICE
```

### API платформы (через маркетплейс)

| Метод | Путь |
|-------|------|
| GET | `/api/v1/solutions/marketplaces/{id}/catalog?q=analytics&pricing=` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/install` |
| POST | `/api/v1/solutions/marketplaces/{id}/listings/{slug}/activate` |

Тело activate: `{ "activationCode": "..." }` — `installationId` добавляется сервером ([0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md)).

### Чеклист вендора

1. Реализовать SPI; тесты против `ispf-analytics-api`.
2. Собрать подписанный `analytics-pack.json` + JAR ([commercial-licensing](commercial-licensing.md)).
3. Опубликовать листинг на [ispf-marketplace](https://github.com/your-org/ispf-marketplace) с `artifactKind: analytics-pack`.
4. Interop CI: функции в каталоге после install на lab ISPF.
5. Партнёрская программа: OEM tier, revenue share ([partner-program](partner-program.md)).

### Offline / air-gapped

Скопировать zip пакета на сервер в `ISPF_ANALYTICS_PACKS_DIR`; положить RSA-лицензию по `analytics-pack.json`. Перезапуск ISPF или планируемый `POST /api/v1/platform/analytics/packs/reload`.

---

## Какой tier выбрать

| Цель | Tier | Сложность |
|------|------|-----------|
| Среднее / min / max за окно | A (`hist.*` или helper) | Минуты |
| Тот же расчёт на многих активах | B (своя формула) | Минуты |
| Композиция существующих функций | A (CEL) или B (шаблон) | Минуты–часы |
| Сертифицированный отраслевой KPI | C (Java pack) | Дни–недели |
| Купить KPI у вендора | C + маркетплейс | Покупка + install |

---

## Связанные документы

- [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md)
- [analytics-historian-cookbook](analytics-historian-cookbook.md)
- [analytics-tag-catalog](analytics-tag-catalog.md)
- [marketplace](marketplace.md)
- [licensed-driver-packs](licensed-driver-packs.md)
- [analytics-platform-roadmap](analytics-platform-roadmap.md) — БЛ-212…216
