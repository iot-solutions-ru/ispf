> **Язык:** русская версия. Канонический английский: [en/decisions/0053-ispf-template-filler.md](../../en/decisions/0053-ispf-template-filler.md).

# ADR-0053: ISPF Template Filler (POI) заменяет YARG для spreadsheet-шаблонов

## Статус

**Accepted** (2026-07-31) — **Done** (2026-10): YARG и `docx4j` удалены из classpath сервера; заполнение spreadsheet Band1 — только POI.

## Контекст

Экспорт отчётов раньше использовал [YARG](https://github.com/cuba-platform/yarg) 2.2.22 для Office-шаблонов Band1. Оформление XLSX зависело от `docx4j-JAXB-ReferenceImpl` 8.3.x + JAXB. Мажорные обновления Dependabot до docx4j 17 ломали тесты шаблонов; апстрим YARG для этого стека не сопровождался.

ISPF уже владел путём SQL/tree → rows, табличным экспортом CSV/HTML/XLSX и конвертацией LibreOffice. YARG был только filler шаблона.

## Решение

1. SPI **`ReportTemplateEngine`** с `PoiSpreadsheetTemplateEngine` — заполнение Band1 `${Band1.FIELD}` для `.xls` / `.xlsx` через Apache POI.
2. Validate и fill spreadsheet по умолчанию через POI; PDF из xlsx = заполнение POI → PDF LibreOffice.
3. **Не** строить дизайнер в духе Jasper, вложенные bands и **не** брать JasperReports (другой DSL шаблонов).
4. **Удалить** YARG / docx4j из сборки после завершения перехода spreadsheet (сделано). Пути шаблонов DOCX/HTML, которым ещё нужен движок не на POI, — отдельный follow-up, если появится спрос продукта, а не пин YARG.

## Последствия

- Spreadsheet-шаблоны не тянут docx4j; контракт Band1 и API загрузки Report Builder остаются стабильными.
- Сопровождение расширения POI Band1 (named range + placeholders) на ISPF; экзотические возможности Excel только из YARG не поддерживаются.
- Устаревшие правила ignore Dependabot для `org.docx4j:docx4j-JAXB-ReferenceImpl` нужно снять, если они ещё есть.

### Риски

- Диапазоны формул под Band1 (например `SUM` по однострочному band) нужно расширять явно — это закрыто тестами на фикстурах Band1.

## Связанные

- [reports](../reports.md)
- [third-party-notices](../third-party-notices.md)
- [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) — реестр документов ≠ этот filler
