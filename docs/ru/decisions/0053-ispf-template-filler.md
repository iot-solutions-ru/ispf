# ADR-0053: ISPF Template Filler вместо YARG для spreadsheet-шаблонов

## Status

**Accepted** (2026-07-31)

## Context

Экспорт отчётов использует [YARG](https://github.com/cuba-platform/yarg) 2.2.22 для Office-шаблонов Band1. XLSX зависит от `docx4j-JAXB-ReferenceImpl` 8.3.x + JAXB. Мажор docx4j 17 ломает `IncomesXlsxTemplateTest`. Апстрим YARG/JAXB для этого стека фактически не обновляется.

ISPF уже владеет SQL/tree → rows, табличным CSV/HTML/XLSX и LibreOffice. YARG — только filler шаблона.

## Decision

1. **Pin** YARG 2.2.22 и `docx4j-JAXB-ReferenceImpl:8.3.11`; ignore Dependabot major для этого артефакта.
2. SPI **`ReportTemplateEngine`**: POI для `.xls`/`.xlsx` Band1; YARG — временно для DOCX/HTML и fallback `ispf.reports.template-engine=yarg`.
3. Default `template-engine=poi` для spreadsheet; PDF из xlsx = POI → LibreOffice.
4. Не строить «свой Jasper» и не мигрировать на JasperReports.
5. Полное удаление YARG/docx4j — после замены DOCX/HTML (отдельный этап).

## Consequences

- Spreadsheet без docx4j; UI/docs без бренда «YARG» где возможно.
- Сопровождение POI Band1 на ISPF.

### Risks

- Формулы под Band1 требуют явного расширения диапазона — тесты на fixtures.
- DOCX пока на YARG.

## Related

- [reports](../reports.md)
- [0053 EN](../../en/decisions/0053-ispf-template-filler.md)
