# Analytics platform gaps (BL-210)

**Positioning:** ISPF Phase 33 is **AF-capable** — derived tags, calculation engine, multi-tag historian query, event frames, and tag catalog/lineage on the object tree. This is **not** a claim of full parity with OSIsoft PI Asset Framework, PI Analytics, or PI Vision.

Do **not** use PI trademarks in marketing copy. Use: «AF-capable», «historian + derived analytics», «ISA-95 object tree analytics».

## Delivered (BL-200…211)

| Capability | ISPF | Notes |
|------------|------|-------|
| Derived tag runtime | ✅ | `derivedValue`, `oeePct`, engine DAG |
| Template catalog | ✅ | `root.platform.analytics` |
| Multi-tag query API | ✅ | `POST /platform/analytics/query` |
| Historian tiers | ✅ | hot / warm / cold |
| Event frames | ✅ | shift, batch, downtime (lightweight) |
| Tag catalog & lineage | ✅ | `analytics-tag-v1`, Explorer inspector |
| Analytics replicas | ✅ | `ReplicaProfile.ANALYTICS` |
| CEL-over-historian | ✅ | `analyticsHelper=cel`, `hist.*` in expressions, validate/evaluate API, Tag Inspector editor (BL-211) |

## Gap register vs enterprise AF / PI-class

| Area | Gap | Future BL |
|------|-----|-----------|
| Expression language | No full PI Analytics syntax; built-in helpers + CEL `hist.*` on derived tags (BL-211) | [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md) — unified catalog, user formulas, analytics packs (BL-212–215) |
| AF database | No duplicate asset tree; tree-first model | By design (ADR-0038) |
| PI Vision graphics | No PI Vision–class graphics | Phase 26 HMI |
| ML on tags | Neural inference deferred; rule-based `anomalyScore` / zScore landed (ADR-0049 Wave 2) | BL-175 for ML inference |
| Event frames | Lightweight windows, not full PI Event Frames | BL-211+ |
| 50k+ lab proof | Gates **defined**; sign-off requires lab run | BL-210 scripts |

## Historian scorecard

[competitive-scorecard](competitive-scorecard.md) Historian row remains **7.0 (code verified)** until Enterprise L lab gates pass on documented hardware. Target **≥9.5** after:

1. `tools/historian-scale/analytics-scale-gate.sh` — multi-tag JVM + optional catalog/1B CH **PASS**
2. `tools/historian-scale/historian-scale-benchmark.sh` — BL-161 aggregate **PASS** (also in `load-test.yml`)
3. Dated Enterprise L sign-off in release notes (scorecard ≥9.5; Phase 28 BL-159/161/162 **Done** on JVM + I-03 evidence without waiting for 1B CH)

## Related

- [analytics-platform-roadmap](analytics-platform-roadmap.md)
- [decisions/0038-analytics-platform-architecture.md](decisions/0038-analytics-platform-architecture.md)
- [examples/analytics-platform/](../../examples/analytics-platform/)
