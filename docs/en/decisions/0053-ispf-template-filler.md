# ADR-0053: ISPF Template Filler replaces YARG for spreadsheet templates

## Status

**Accepted** (2026-07-31)

## Context

Report export uses [YARG](https://github.com/cuba-platform/yarg) 2.2.22 for Band1 Office templates. XLSX formatting depends on `docx4j-JAXB-ReferenceImpl` 8.3.x + JAXB. Dependabot major bump to docx4j 17 breaks `IncomesXlsxTemplateTest` (`Docx4JException` / NPE). YARG upstream is effectively unmaintained for this stack; waiting on a YARG/JAXB refresh is not viable.

ISPF already owns SQL/tree → rows, CSV/HTML/XLSX table export, and LibreOffice conversion. YARG is only the template filler.

## Decision

1. **Pin** YARG 2.2.22 and `org.docx4j:docx4j-JAXB-ReferenceImpl:8.3.11`. Ignore Dependabot **major** bumps of that artifact.
2. Introduce **`ReportTemplateEngine` SPI** with:
   - `PoiSpreadsheetTemplateEngine` — Band1 `${Band1.FIELD}` fill for `.xls` / `.xlsx` via Apache POI (direct dependency).
   - `YargTemplateEngine` — temporary adapter for DOCX/HTML and fallback when `ispf.reports.template-engine=yarg`.
3. Default `ispf.reports.template-engine=poi` for spreadsheet validate/fill; PDF-from-xlsx = POI fill → LibreOffice PDF.
4. **Do not** build a Jasper-like designer, nested bands, or adopt JasperReports (different template DSL).
5. Full removal of YARG/docx4j waits until DOCX/HTML template paths have a non-YARG implementation (out of this ADR’s cutover).

## Consequences

- Spreadsheet templates no longer need docx4j; Dependabot docx4j 17 stays closed.
- Band1 contract and Report Builder upload API stay stable; UI copy drops the “YARG” product name where user-facing.
- Maintenance of POI Band1 expansion (named range + placeholders) is on ISPF; exotic YARG-only Excel features are unsupported.

### Risks

- Formula ranges below Band1 (e.g. `SUM` over a one-row band) need explicit expansion — mitigated by tests on `incomes-band1.xlsx`.
- DOCX still on YARG until a later phase.

## Related

- [reports](../reports.md)
- [third-party-notices](../third-party-notices.md)
- [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) — document registry ≠ this filler
