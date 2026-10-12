# ADR-0053: ISPF Template Filler (POI) replaces YARG for spreadsheet templates

## Status

**Accepted** (2026-07-31) — **Done** (2026-10): YARG and `docx4j` removed from the server classpath; spreadsheet Band1 fill is POI-only.

## Context

Report export previously used [YARG](https://github.com/cuba-platform/yarg) 2.2.22 for Band1 Office templates. XLSX formatting depended on `docx4j-JAXB-ReferenceImpl` 8.3.x + JAXB. Dependabot major bumps to docx4j 17 broke template tests; YARG upstream was unmaintained for this stack.

ISPF already owned SQL/tree → rows, CSV/HTML/XLSX table export, and LibreOffice conversion. YARG was only the template filler.

## Decision

1. Introduce **`ReportTemplateEngine` SPI** with `PoiSpreadsheetTemplateEngine` — Band1 `${Band1.FIELD}` fill for `.xls` / `.xlsx` via Apache POI.
2. Default spreadsheet validate/fill through POI; PDF-from-xlsx = POI fill → LibreOffice PDF.
3. **Do not** build a Jasper-like designer, nested bands, or adopt JasperReports (different template DSL).
4. **Remove** YARG / docx4j from the build once spreadsheet cutover is complete (landed). DOCX/HTML template paths that still need a non-POI engine are a separate follow-up if product demand appears — not a YARG pin.

## Consequences

- Spreadsheet templates do not pull docx4j; Band1 contract and Report Builder upload API stay stable.
- Maintenance of POI Band1 expansion (named range + placeholders) is on ISPF; exotic YARG-only Excel features are unsupported.
- Stale Dependabot ignore rules for `org.docx4j:docx4j-JAXB-ReferenceImpl` should be removed when present.

### Risks

- Formula ranges below Band1 (e.g. `SUM` over a one-row band) need explicit expansion — mitigated by tests on Band1 fixtures.

## Related

- [reports](../reports.md)
- [third-party-notices](../third-party-notices.md)
- [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) — document registry ≠ this filler
