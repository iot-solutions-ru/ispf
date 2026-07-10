> **Язык:** русская версия (вычитка). Канонический английский: [../../en/decisions/0042-analytics-function-catalog.md](../../en/decisions/0042-analytics-function-catalog.md).



# ADR-0042: Единый каталог analytics-функций и расширяемость



## Статус



**Принято** (2026-07-10)



## Контекст



В PI Asset Framework — **большая библиотека analyses**, **плагины**, **единый каталог формул** и **пользовательские функции**. В ISPF после ADR-0041 возможности есть, но каталог был **разрознен**: Java-хелперы, `hist.*` CEL, reactive CEL, статические списки в UI.



Цель: **каталог и расширение как в PI и расширения**, без второго дерева активов — deployment по-прежнему через **binding rules** на объектах.



## Решение



### Три уровня каталога



| Уровень | Источник | Кто создаёт | Runtime |

|---------|----------|-------------|---------|

| **A — Встроенные** | Core registry + open packs в classpath | Команда платформы | Java evaluators + CEL `hist.*` |

| **B — Пользовательские формулы** | `@analyticsFormulas` / bundle приложения | Оператор / разработчик решения | Шаблон + параметры; expand при compile |

| **C — Плагины** | JAR по SPI (AGPL / commercial) | Партнёр / ISV | `AnalyticsFunctionProvider`, как Tier A |



Каталог — для **поиска, документации, валидации и вставки**; экземпляр на устройстве — **правило** `kind: historian` | `reactive`.



**Пошаговый гайд:** [analytics-formulas-and-packs.md](../analytics-formulas-and-packs.md).



### API каталога (BL-212)



```

GET /api/v1/platform/analytics/catalog

GET /api/v1/platform/analytics/catalog/{functionId}

POST /api/v1/platform/analytics/catalog/validate

```



Единый ответ: `id`, `syntax`, `parameters`, `kinds`, `tags`, `pack`, ссылки на cookbook.



UI: **браузер формул** в модальном редакторе выражений ([ADR-0040](0040-unified-computations-ui.md)).



### Свои формулы Tier B (BL-214)



JSON на `root.platform` (`@analyticsFormulas`) или в bundle приложения:



```json

{

  "id": "tank-fill-rate",

  "displayName": "Скорость заполнения бака (м³/ч)",

  "kind": "historian",

  "expression": "rateOfChange({{levelPath}}, 1h) * {{tankArea}}",

  "parameters": [

    { "name": "levelPath", "type": "tagPath" },

    { "name": "tankArea", "type": "number", "defaultValue": "1" }

  ],

  "scope": "site",

  "version": 1

}

```



**Применение:** каталог → **Применить** → параметры → binding rule с `formulaRef` + expand.



**API:** `GET/POST/PUT/DELETE /api/v1/platform/analytics/formulas`, `POST .../formulas/{id}/expand`.



| Область | Где | RBAC |

|---------|-----|------|

| Площадка | `root.platform` `@analyticsFormulas` | platform admin |

| Приложение | `analytics-formulas.json` в bundle | app developer |

| Blueprint | `analyticsFormulasJson` | blueprint author |



Это **не bytecode UDF** — декларативные выражения (helper + CEL). Процедурная логика: application functions (reactive) или Java-пакет (historian).



### Плагины analytics Tier C (BL-213)



- `packages/ispf-analytics-api` — SPI `AnalyticsFunctionProvider`;

- `packages/ispf-analytics-<domain>` — optional JAR packs;

- `ispf-server` — `AnalyticsPackLoader` + `AnalyticsCatalogRegistry`.



Эталон: `ispf-analytics-core-ext` (`energyDelta`).



Pack deliverable: `analytics-pack.json` + JAR + `META-INF/services/...AnalyticsFunctionProvider`.



### Маркетплейс для Tier C (BL-216)



Коммерческие analytics packs распространяются через [MARKETPLACE.md](../marketplace.md) с `artifactKind: analytics-pack` (аналог symbol packs и driver packs):



1. Вендор публикует листинг (free / paid).

2. Оператор: **Система → Решения → Маркетплейс** → Install / Activate.

3. Платформа распаковывает в `ISPF_ANALYTICS_PACKS_DIR`, проверяет RSA-лицензию ([ADR-0003](0003-commercial-bundle-licensing.md)).

4. Функции появляются в `GET .../analytics/catalog` с `pack: <packId>`.



Подробности: [analytics-formulas-and-packs.md § Маркетплейс](../analytics-formulas-and-packs.md#покупка-tier-c-через-маркетплейс).



### Что historian не может



| Задача | Механизм | Historian-safe? |

|--------|----------|-----------------|

| Простая математика / окна | Tier B или CEL + `hist.*` | Да |

| Цепочка KPI | Несколько binding rules | Да |

| Процедурная логика / external IO | Tree function + `callFunction` (reactive) | Нет |

| Тяжёлый / сертифицированный KPI | Tier C Java pack | Да |



### Чего не копируем из PI



- отдельную БД AF;

- язык PI Analytics — у нас CEL + helpers;

- 200+ функций в ядре — **lean core** + packs + формулы пользователя.



## Фазы



| Фаза | BL | Результат | Статус |

|------|-----|-----------|--------|

| 1 | BL-212a | Catalog API + dormant evaluators | Готово |

| 2 | BL-212b | UI браузер формул | Готово |

| 3 | BL-213 | SPI + первый open pack | Готово |

| 4 | BL-214 | CRUD `@analyticsFormulas` | Готово |

| 5 | BL-215 | `formulaRef`, blueprint sharing | Готово |

| 6 | BL-216 | Marketplace analytics packs + `packs-dir` loader | В работе |



## Связанные документы



- [ADR-0038](0038-analytics-platform-architecture.md)

- [ADR-0041](0041-multi-tag-historian-computations.md)

- [analytics-formulas-and-packs.md](../analytics-formulas-and-packs.md)

- [analytics-historian-cookbook.md](../analytics-historian-cookbook.md)

- [MARKETPLACE.md](../marketplace.md)

- [analytics-platform-roadmap.md](../analytics-platform-roadmap.md)

