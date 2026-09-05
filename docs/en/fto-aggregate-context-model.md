> **Language:** Canonical English. Russian edition: [ru/fto-aggregate-context-model.md](../ru/fto-aggregate-context-model.md).

# FTO memo — context / object-tree model vs AggreGate

> **Status:** Internal — Engineering IP inventory for counsel. **Not legal advice.**  
> **Hub:** [doc-status.md](doc-status.md) · Related: [license-compliance](license-compliance.md), [object-model](object-model.md), [bindings](bindings.md).

## Purpose

Document **conceptual overlap** between ISPF’s object tree (variables / functions / events, bindings, expressions) and Tibbo **AggreGate**’s publicly described Unified Data Model, and give counsel a **Freedom-to-Operate (FTO)** worklist.

**Product honesty:** ISPF’s high-level architecture was **inspired by** the industry pattern that AggreGate popularized (hierarchical contexts with variables, functions, events, and declarative bindings). Inspiration of *ideas* is normal; counsel must still separate:

| Layer | Risk if mishandled |
|-------|-------------------|
| **Ideas / architecture patterns** | Usually not copyright; patents only if valid claims cover the *specific* mechanism |
| **Expression of code, docs, APIs, protocols** | Copyright / trade secret |
| **Trademarks / marketing copy** | Brand / unfair competition |
| **Patented implementations** (if any) | Patent infringement — claim chart required |

## Public AggreGate claims (marketing)

Sources (public web; verify dates/URLs before counsel filing):

- [Unified Data Model](https://aggregate.digital/technology/architecture/unified-data-model.html) — marketing calls **object normalization** “patented”: hierarchical **contexts**; each has **variables**, **functions**, **events** + metadata.
- Universal type: almost everything is a **data table** (variable values, function I/O, event payloads).
- [Bindings](https://aggregate.digital/technology/analytics/bindings.html) — expressions, triggers (startup / change / event / periodic), UI and model wiring.
- [Models / digital twins](https://aggregate.digital/technology/analytics/models.html) — relative/absolute models with V/F/E + bindings.
- Early Tibbo launch materials historically used **patent-pending** language for device-as-object (properties / methods / events).

**Engineering note:** Marketing “patented” is not the same as identified patent numbers or claim scope. A light public search did **not** confirm reliable patent numbers for Tibbo/AggreGate “object normalization”. Counsel must search Rospatent / USPTO / EPO / CNIPA under assignees **Tibbo**, **AggreGate**, related entities and inventors.

## ISPF side (what we ship)

Canonical docs: [object-model](object-model.md), [bindings](bindings.md), [application-principles](application-principles.md).

| Concept | ISPF realization |
|---------|------------------|
| Hierarchy | Dot-path **object tree** (`root.platform…`) |
| Node contents | **Variables**, **Functions**, **Events** (+ blueprints / instance types) |
| Typed values | `DataSchema` / `DataRecord` (fields + rows) — **not** “everything is AggreGate data table” |
| Logic | Declarative **`@bindingRules`** + **CEL** ([bindings](bindings.md)) |
| Device vs logic | Hard rule: orchestration / twin logic is **not** `ObjectType.DEVICE` ([AGENTS.md](../../AGENTS.md), [application-principles](application-principles.md)) |
| Drivers | Separate **driver packs**, Apache-2.0 clean-room codecs where former GPL stacks existed |
| License | Platform **AGPL-3.0** + optional Enterprise dual-license |

## Similarity map (honest)

| Theme | Overlap | Differentiation (document for FTO) |
|-------|---------|-------------------------------------|
| Tree of objects/contexts | **High** | Industry pattern (OPC UA, BACnet, NMS, digital twins); ISPF paths/API/docs are original |
| Variables + functions + events on a node | **High** | Same *class* as OOP device models; ISPF descriptors, REST, and storage are original |
| Bindings / expressions | **High (role)** | ISPF uses **CEL** + `@bindingRules` JSON; not AggreGate expression language or binding UI |
| “Normalize any device into one model” | **High (role)** | Drivers → tree; no AggreGate Agent protocol |
| Universal data-table type | **Low** | ISPF uses schema/record; tables are not the single universal wire type |
| Terminology | Partial | Prefer ISPF terms: *object tree*, *binding rules*, *blueprints* — **avoid** AggreGate slogans (*object normalization*, *context tree* as product claim, copying their doc examples) |

**Verdict (engineering, not counsel):** Category-level similarity is **real and acknowledged**. That alone does **not** establish patent or copyright infringement. Residual risk is **claim-specific** (patents) and **copy-specific** (code/docs/API).

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

- [ ] Search patents/applications: assignee/applicant Tibbo Technology, AggreGate, related RU/TW/US entities; keywords: *context tree*, *object normalization*, *device normalization*, *unified data model*, *bindings*, *data table*.
- [ ] Jurisdictions of sale: **RU**, **EAEU**, export targets (US/EU/CN as applicable).
- [ ] For each live patent: map **independent claims** to ISPF modules (`object tree`, `BindingRuleEngine`, CEL, driver packs, historian).
- [ ] Flag any claim that requires **universal data-table** semantics or a **specific agent protocol** — likely non-overlap if ISPF lacks those elements.
- [ ] Check expiry, family, and opposition/invalidity prior art.

### B. Copyright / clean-room hygiene

- [ ] Confirm no AggreGate / Tibbo **source**, Agent SDK, or proprietary docs were copied into the repo.
- [ ] Confirm UI strings, doc examples, and tutorial scenarios are **not** verbatim from AggreGate pages.
- [ ] Keep a short **provenance note** for the object model (inspired by industry pattern; independent implementation; list prior-art refs).
- [ ] Contributor CLA already allows dual-licensing ([CLA.md](../../CLA.md)); ensure contributors did not paste third-party proprietary code.

### C. Trademark / go-to-market

- [ ] Do not use **AggreGate**, **Tibbo**, or confusingly similar marks in product name or domain.
- [ ] Marketing: describe ISPF on its own terms; comparison tables are OK if factual and non-disparaging per local law.
- [ ] Avoid claiming “compatible with AggreGate” unless a deliberate, licensed interop exists.

### D. Contract / Enterprise EULA

- [ ] Enterprise EULA: no IP warranty broader than counsel approves; optional FTO schedule for named jurisdictions.
- [ ] Bundle/driver SKUs stay on separate EULAs ([commercial-licensing](commercial-licensing.md)).

## Safe engineering / docs practice

1. **Keep differentiating design choices visible** in ADRs (DEVICE vs logic objects, CEL, binding-rules-only, driver packs).
2. **Do not rebrand** ISPF features using AggreGate marketing terms.
3. When adding features that look “AggreGate-like” (models, relative twins, binding concurrency), record **independent design rationale** in an ADR.
4. Re-run this memo when entering a new country or signing a large exclusive deal.

## Recommended counsel deliverable

Short written opinion covering:

1. Patent FTO (RU + primary export markets) for object-tree + V/F/E + bindings.
2. Confirmation that public “patented object normalization” maps (or does not map) to enforceable claims against ISPF.
3. Copyright/trademark hygiene sign-off for docs and UI.
4. Optional: defensive publication / Rospatent software registration (copyright certificate — **not** a patent).

## Related

- [license-compliance](license-compliance.md)
- [object-model](object-model.md)
- [bindings](bindings.md)
- [pid-symbols-legal](pid-symbols-legal.md)
- [russian-software-registry](russian-software-registry.md)
- [CLA.md](../../CLA.md)
