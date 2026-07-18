# ADR-0047: Custom BPMN subset engine (no Camunda/Flowable)

## Status

**Accepted** (2026-07-18) — Wave 5 freeze: BL-176 embedded `subProcess` + message catch/throw; unsupported elements rejected at parse.

## Context

ISPF workflows are `WORKFLOW` objects (`workflow-v1`) driven by BPMN XML stored on the tree. Execution lives in `packages/ispf-plugin-workflow` (token-based `WorkflowEngine` + `BpmnParser`), integrated with CEL conditions, operator work queue, NATS message tasks, correlator `RUN_WORKFLOW`, and ISPF `serviceTask` actions (`log`, `set_variable`, `publish_nats`, `invoke_function`).

Embedding Camunda or Flowable would bring full BPMN 2.0 / DMN surface, but also a second process runtime, heavier dependencies, and impedance with tree-first ISPF actions and ACL. Docs already state “pure Java, no Camunda/Flowable”; this ADR records that choice and the **subset freeze** rules so implementers do not silently expand toward full BPMN.

Related backlog: BL-176 (subprocess / message events). Product docs: [workflows](../workflows.md).

## Decision

1. **Keep a custom subset engine** in `ispf-plugin-workflow`. Do **not** embed Camunda, Flowable, or another full BPMN product in this program.
2. **Supported set** is exactly the table in [workflows § Supported BPMN elements](../workflows.md#supported-bpmn-elements). New element types require an ADR amendment (or a successor ADR), not silent parser growth.
3. **BL-176 (done):** embedded `subProcess` runtime (enter inner start, exit on inner end, including nested embedded subprocesses); message catch wait + `deliverMessage`; message throw via intermediate throw with `messageEventDefinition`. Boundary timer escalation stays documented as supported.
4. **Explicit non-goals (reject at parse):** `callActivity`, multi-instance, `inclusiveGateway`, `eventBasedGateway`, compensation, event subprocess, DMN / `businessRuleTask`. Non-message intermediate throw fails at parse. Parser and WorkflowBuilder palette must match the docs “Not supported” table; unsupported XML fails with a clear error. Palette filter: `apps/web-console/src/bpmn/ispfPaletteFilter.ts` (pools, data objects/stores, generic `task`, group removed).
5. **UI honesty:** WorkflowBuilder / docs remain **Beta — BPMN subset**, not “full BPMN 2.0”.

## Consequences

- Integrators design processes against the subset table; full BPMN import is not a goal.
- Maintenance cost stays on ISPF (semantics edge cases), but footprint and license surface stay small and actions stay first-class Java.
- After freeze, scorecard Workflow/BPMN dimension must cite this ADR + reject-list tests, not imply Camunda-class coverage.
- Revisit only if a named customer requirement needs full BPMN/DMN — that would be a **new** ADR (embed vs continue subset), not a quiet dependency add.

### Risks

- Subset gaps surprise users who paste generic BPMN — mitigated by parse reject + UI Beta + docs Not supported table.
- Long-term parity pressure vs mature engines — accepted; freeze stops unbounded scope.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Embed Camunda/Flowable now | Large dependency/license surface; weak fit for ISPF `serviceTask` / tree ACL; second runtime to operate |
| Grow silently toward full BPMN | Unbounded maintenance; docs/UI lie about coverage |
| Drop BPMN, JSON-only flows | Breaks existing WORKFLOW objects, bpmn-js editor, and correlator demos |

## Related

- [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) — workflow triggers on the automation bus
- [0001-app-platform-boundary](0001-app-platform-boundary.md) — engines in platform, solutions declarative
- [0048-server-modularization-seams](0048-server-modularization-seams.md) — parallel modularization program (does not change BPMN choice)
