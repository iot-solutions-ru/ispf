# ADR-0052: ISPF Workflow Diagram Editor (no bpmn.io runtime)

## Status

**Accepted** (2026-07-24)

## Context

Workflow editing used `bpmn-js` (bpmn.io). Its license requires a visible bpmn.io watermark. Product direction is a first-party UX (like SCADA SVG normalize): own editor, own engine subset, foreign BPMN imported via adapt—not an embedded Camunda/Flowable runtime.

ADR-0047 keeps the custom subset engine. ADR-0049 allows new `serviceTask` actions without new BPMN element types. This ADR covers the **console editor** and **foreign BPMN import**.

## Decision

1. **Ship an ISPF Workflow Diagram Editor** (React + SVG) that edits an in-memory document model and serializes to BPMN 2.0 XML + `bpmndi` + `ispf:*` attributes. Do **not** use `bpmn-js` / `diagram-js` in the product UI.
2. **Foreign BPMN** (Camunda/Flowable/etc.) is accepted through **`adaptForeignBpmn`**: rewrite/map/stub into the ISPF model with structured warnings (same spirit as SVG sanitize/normalize). Silent accept of unsupported constructs is forbidden.
3. **Engine remains** `ispf-plugin-workflow`. Expanding executable elements (e.g. limited multi-instance) requires parser + engine work and an update to the supported table in [workflows](../workflows.md)—not an embed of Camunda. `callActivity` (wait for child WORKFLOW) is supported.
4. **Properties panel** is first-party (tabs for General / Implementation / Events / Conditions). XML tab remains for AI and edge cases.
5. **Marketplace extension** for “external elements” is palette presets (`bpmn-element-pack` / bundle section) that create ISPF nodes (`serviceTask` / `userTask` / …) with defaults—typically `invoke_function`—not third-party BPMN element types in the parser.

## Consequences

- No bpmn.io watermark in the console; full control of palette and properties UX.
- Import of foreign diagrams is best-effort with an explicit report; not “full BPMN 2.0 execution.”
- Maintenance of editor/layout/selection is on ISPF; scope stays the supported subset (+ planned engine expansions).
- ADR-0047 element-type freeze is amended only when the workflows supported table and engine land together.

### Risks

- Custom editor MVP will be thinner than Camunda Modeler (bend points, advanced DI)—accepted for subset-first product.
- Adapt maps may miss vendor-specific extensions—mitigated by warnings + XML tab.

## Related

- [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md)
- [0049-ot-automation-excellence](0049-ot-automation-excellence.md)
- [workflows](../workflows.md)
