> **Язык:** русская версия (вычитка). Канонический английский: [../../en/decisions/0042-analytics-function-catalog.md](../../en/decisions/0042-analytics-function-catalog.md).

# ADR-0042: Единый каталог analytics-функций и расширяемость

## Статус

**Принято** (2026-07-10)

## Контекст

В PI Asset Framework — **большая библиотека analyses**, **плагины**, **единый каталог формул** и **пользовательские функции**. В ISPF после ADR-0041 возможности есть, но каталог **разрознен**: Java-хелперы, `hist.*` CEL, reactive CEL, статические списки в UI. Свои historian-функции сегодня — только Java в ядре.

Запрос: **такой же опыт discovery и расширения**, без второго дерева активов — deployment по-прежнему через **binding rules** на объектах.

## Решение

### Три уровня каталога

| Уровень | Источник | Кто создаёт |
|---------|----------|-------------|
| **A — Встроенные** | Ядро + open packs | Команда платформы |
| **B — Пользовательские формулы** | `@analyticsFormulas` / bundle приложения | Оператор / разработчик решения |
| **C — Плагины** | JAR по SPI (как драйверы/LLM) | ISV / отраслевые пакеты |

Каталог — для **поиска, документации и вставки**; экземпляр на устройстве — **правило** `kind: historian` | `reactive`.

### API каталога (BL-212)

```
GET /api/v1/platform/analytics/catalog
GET /api/v1/platform/analytics/catalog/{functionId}
POST /api/v1/platform/analytics/catalog/validate
```

Единый ответ: `id`, `syntax`, `parameters`, `kinds`, `tags`, `pack`, ссылки на cookbook.

UI: **браузер формул** в модальном редакторе выражений (вместо дублирующих TS-списков).

### Свои формулы (BL-214)

JSON на `root.platform` (`@analyticsFormulas`) или в bundle приложения:

- параметризованное `expression` (`{{levelPath}}`, `{{window}}`);
- «Сохранить как формулу» / «Создать правило из формулы»;
- RBAC: platform admin / app developer.

Это **не bytecode UDF** — декларативные выражения. Процедурная логика: application functions (reactive) или Java-пакет (historian).

### Плагины analytics (BL-213)

- `ispf-analytics-api` — SPI `AnalyticsFunction`;
- отдельные JAR-пакеты (AGPL / commercial);
- быстрый win: подключить в compiler `totalizer`, `min`, `max`, `last`.

### Чего не копируем из PI

- отдельную БД AF;
- язык PI Analytics — у нас CEL + helpers;
- 200+ функций в ядре — **lean core** + packs + формулы пользователя.

## Фазы

| Фаза | BL | Результат |
|------|-----|-----------|
| 1 | BL-212a | Catalog API + dormant evaluators |
| 2 | BL-212b | UI браузер формул |
| 3 | BL-213 | SPI + первый pack |
| 4 | BL-214 | CRUD пользовательских формул |
| 5 | BL-215 | Blueprint / marketplace |

## Связанные документы

- [ADR-0038](0038-analytics-platform-architecture.md)
- [ADR-0041](0041-multi-tag-historian-computations.md)
- [analytics-historian-cookbook.md](../analytics-historian-cookbook.md)
- [analytics-platform-roadmap.md](../analytics-platform-roadmap.md)
