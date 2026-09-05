> **Language:** Canonical English. Russian edition: [ru/fto-object-tree-model.md](../ru/fto-object-tree-model.md).

# FTO memo — object-tree model (variables / functions / events / bindings)

> **Status:** Internal — Engineering IP inventory for counsel. **Not legal advice.**  
> **Hub:** [doc-status.md](doc-status.md) · Related: [license-compliance](license-compliance.md), [object-model](object-model.md), [bindings](bindings.md).

## Purpose

Document that ISPF’s **object tree** (variables / functions / events, declarative bindings, expressions) belongs to a well-known **IIoT / SCADA middleware product class**, and give counsel a **Freedom-to-Operate (FTO)** worklist that does **not** depend on naming any single commercial vendor.

**Product honesty:** ISPF’s high-level architecture follows an **industry pattern** common to context/object-tree platforms (hierarchical nodes with properties, operations, notifications, and declarative wiring). Inspiration of *ideas* is normal; counsel must still separate:

| Layer | Risk if mishandled |
|-------|-------------------|
| **Ideas / architecture patterns** | Usually not copyright; patents only if valid claims cover the *specific* mechanism |
| **Expression of code, docs, APIs, protocols** | Copyright / trade secret |
| **Trademarks / marketing copy** | Brand / unfair competition |
| **Patented implementations** (if any) | Patent infringement — claim chart required |

This memo stays **vendor-agnostic**. Competitive product names belong in counsel-only workpapers, not in public docs.

## Industry pattern (abstract)

Many integration / SCADA / IoT platforms expose a similar abstract model:

- Hierarchical **object / context / asset tree**
- Per-node **variables** (readable/writable state), **functions** (operations), **events** (async notifications) + metadata
- Optional **universal structured value** representation (records / tables / schemas)
- **Bindings** — expressions with triggers (startup / change / event / periodic) wiring data and UI
- **Models / digital twins** — templates or relative models that enrich devices with derived V/F/E behavior
- Drivers / agents that **normalize** heterogeneous devices into the common tree

Some vendors market subsets of this pattern as proprietary or “patented.” Marketing language is **not** claim scope. Counsel must identify actual patents (if any) in jurisdictions of sale.

## ISPF side (what we ship)

Canonical docs: [object-model](object-model.md), [bindings](bindings.md), [application-principles](application-principles.md).

| Concept | ISPF realization |
|---------|------------------|
| Hierarchy | Dot-path **object tree** (`root.platform…`) |
| Node contents | **Variables**, **Functions**, **Events** (+ blueprints / instance types) |
| Typed values | `DataSchema` / `DataRecord` (fields + rows) |
| Logic | Declarative **`@bindingRules`** + **CEL** ([bindings](bindings.md)) |
| Device vs logic | Hard rule: orchestration / twin logic is **not** `ObjectType.DEVICE` ([AGENTS.md](../../AGENTS.md), [application-principles](application-principles.md)) |
| Drivers | Separate **driver packs**, Apache-2.0 clean-room codecs where former GPL stacks existed |
| License | Platform **AGPL-3.0** + optional Enterprise dual-license |

## Similarity to the industry class (honest)

| Theme | Overlap with class | ISPF differentiation (document for FTO) |
|-------|--------------------|------------------------------------------|
| Tree of objects/contexts | **High** | Long-standing pattern (see prior art); ISPF paths/API/docs are original |
| Variables + functions + events on a node | **High** | Same *class* as OOP device models; ISPF descriptors, REST, and storage are original |
| Bindings / expressions | **High (role)** | ISPF uses **CEL** + `@bindingRules` JSON |
| “Normalize devices into one model” | **High (role)** | Drivers → tree; ISPF-owned pack protocol and codecs |
| Universal tabular wire type | **Low–medium** | ISPF uses schema/record; not a single vendor-specific table protocol |
| Terminology | Careful | Prefer ISPF terms: *object tree*, *binding rules*, *blueprints* — do not copy third-party slogans or doc examples |

**Verdict (engineering, not counsel):** Category-level similarity to other object-tree platforms is **expected**. That alone does **not** establish patent or copyright infringement. Residual risk is **claim-specific** (patents) and **copy-specific** (code/docs/API).

## Prior art anchors (for counsel brief)

Non-exhaustive; counsel should expand:

1. **OPC UA** AddressSpace — nodes, variables, methods, events.
2. **BACnet** object model — properties, services, event reporting.
3. **SNMP** MIB / managed objects.
4. **JavaBeans / .NET components / OSGi** — properties, methods, events.
5. **AWS IoT Device Shadow / Azure Digital Twins / DTDL** — twin graphs + properties.
6. **Ignition / Node-RED / Home Assistant** — tags, bindings, expressions.
7. **CEL** (Common Expression Language) — Google open specification used by ISPF bindings.

Use these to argue that V/F/E-on-a-tree is a **long-standing industrial pattern**, not a unique invention of one vendor.

## Counsel checklist (FTO)

### A. Patent search and claim chart

- [ ] Search patents/applications in jurisdictions of sale (**RU**, **EAEU**, export targets as applicable) for claims covering: hierarchical context/object trees with variables/functions/events; device normalization into a unified model; declarative bindings with expression languages; universal structured/table value types.
- [ ] Include assignee sweeps for major commercial object-tree IIoT vendors **only in counsel workpapers** (do not list competitor names in public docs).
- [ ] For each live patent: map **independent claims** to ISPF modules (`object tree`, `BindingRuleEngine`, CEL, driver packs, historian).
- [ ] Flag any claim that requires a **vendor-specific protocol**, **universal data-table wire format**, or other element ISPF does not implement — likely non-overlap.
- [ ] Check expiry, family, and opposition/invalidity prior art.

### B. Copyright / clean-room hygiene

- [ ] Confirm no third-party proprietary **source**, agent SDKs, or closed docs were copied into the repo.
- [ ] Confirm UI strings, doc examples, and tutorial scenarios are **not** verbatim from third-party product documentation.
- [ ] Keep a short **provenance note** for the object model (industry pattern; independent implementation; list prior-art refs).
- [ ] Contributor CLA already allows dual-licensing ([CLA.md](../../CLA.md)); ensure contributors did not paste third-party proprietary code.

### C. Trademark / go-to-market

- [ ] Do not use third-party product or company marks (or confusingly similar marks) in ISPF product name or domain.
- [ ] Marketing: describe ISPF on its own terms; comparison tables are OK if factual and non-disparaging per local law.
- [ ] Avoid claiming “compatible with &lt;vendor&gt;” unless a deliberate, licensed interop exists.

### D. Contract / Enterprise EULA

- [ ] Enterprise EULA: no IP warranty broader than counsel approves; optional FTO schedule for named jurisdictions.
- [ ] Bundle/driver SKUs stay on separate EULAs ([commercial-licensing](commercial-licensing.md)).

## Safe engineering / docs practice

1. **Keep differentiating design choices visible** in ADRs (DEVICE vs logic objects, CEL, binding-rules-only, driver packs).
2. **Do not rebrand** ISPF features using third-party marketing slogans.
3. When adding features common to the object-tree class (models, relative twins, binding concurrency), record **independent design rationale** in an ADR.
4. Re-run this memo when entering a new country or signing a large exclusive deal.
5. Keep **named competitor patent comparisons** out of public docs and main-branch marketing; use counsel-only annexes.

## Recommended counsel deliverable

Short written opinion covering:

1. Patent FTO (RU + primary export markets) for object-tree + V/F/E + bindings.
2. Claim charts against any identified patents in the object-tree / device-normalization class (counsel workpapers).
3. Copyright/trademark hygiene sign-off for docs and UI.
4. Optional: defensive publication / Rospatent software registration (copyright certificate — **not** a patent).

## Related

- [license-compliance](license-compliance.md)
- [object-model](object-model.md)
- [bindings](bindings.md)
- [pid-symbols-legal](pid-symbols-legal.md)
- [russian-software-registry](russian-software-registry.md)
- [CLA.md](../../CLA.md)
