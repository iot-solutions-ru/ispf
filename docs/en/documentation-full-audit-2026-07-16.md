> **Language:** Canonical English. Companion: [documentation-audit](documentation-audit.md) (structure/links/anonymize).

# Full documentation audit — 2026-07-16

> **Status:** Internal — Content honesty pass. Hub: [doc-status.md](doc-status.md).

**Scope:** ~149 EN + ~148 RU markdown pages + ADR index (not every line of every lab report). Method: automated link/language sweeps + layered human review (public / builder / ops-ecosystem) against code and [competitive-scorecard](competitive-scorecard.md).

**Pass status of tooling (this date):**

| Check | Result |
|-------|--------|
| Internal `.md` links (`audit_links.py`) | **0 broken** among `docs/*/*.md` cross-links |
| Cyrillic in `docs/en/` | Clean |
| Relative links `docs/en` → `../packages|examples|LICENSE|…` | **~56 broken** (need `../../…`) — not covered by current link auditor |
| Anonymize patterns (`ispf.iot-solutions`, public IPs) | No live hits in committed docs |

**Already on `main` (Wave A — hub entry):** role-based `docs/*/readme.md`, Try vs Contribute in getting-started, AGPL wording in product/hub. **This branch may lag `main`** — verify against `origin/main` before fixing again.

---

## Executive summary

Docs are **deep and professionally maintained** as an internal platform wiki. For open-source discovery they still fail in three ways:

1. **License / maturity honesty** — Apache leftovers, PRODUCTION labels on stub drivers, marketplace/partner sold as GA while scorecard says stub.  
2. **Broken builder contracts** — grid 12 vs 84, deploy/BFF shape drift, wrong SPI snippets, broken `../packages` links.  
3. **Newcomer truth** — local auth story (`X-ISPF-Role` / Role selector) does not match default config (Bearer login; `local-role-header-enabled` defaults **false**); JDK **21+** in getting-started vs toolchain **Java 25**.

---

## P0 — fix before promoting OSS further

| # | Area | Finding | Primary files | Fix |
|---|------|---------|---------------|-----|
| 1 | License | Remaining “Apache 2.0 `main`” framing | `architecture.md`, ADR `0001`/`0003` (banner), any stale product/hub copies | AGPL + dual-license; ADR supersession banners → `0016` |
| 2 | Auth | Getting-started still teaches `X-ISPF-Role` + Role selector; default local uses Bearer; header auth off | `getting-started.md` EN/RU, `security.md` | Document login + Bearer; header as opt-in flag |
| 3 | Java | Public docs say JDK 21+; Gradle toolchain is **25** | `getting-started.md`, root README, `product.md`, `architecture.md` | Align on **JDK 25** (or document real 21 path) |
| 4 | OT honesty | `opc-da` / `opc-bridge` / DNP3 gaps labeled **PRODUCTION** | `drivers.md`, `driver-promotion.md`, `demostands.md` | Downgrade or caveats; never stub=PRODUCTION |
| 5 | HMI grid | Widgets/apps samples use 12-col; dashboards mandate **84×8** | `widgets.md`, `applications.md`, `dashboards.md` | Unify samples to 84-grid |
| 6 | Links | `../packages`, `../examples`, `../LICENSE` from `docs/en/` resolve wrong | `license.md`, `driver-ddk.md`, `ai-*.md`, MES refs, … (~56) | Bulk `../` → `../../` |
| 7 | Deploy/BFF | ZIP+multipart vs JSON deploy; divergent BFF body shapes | `solution-developer-guide.md`, `applications.md` | One canonical contract; mark legacy |
| 8 | Ecosystem | Partner benefits / marketplace GA checklist read as live; APIs stub | `partner-program.md`, `marketplace.md` | **Draft / Partial** banners; match scorecard dim 12 |
| 9 | RU product | Severe MT/drift (“слово”, “автоматический пожар”, …) | `docs/ru/product.md` | Re-translate from current EN; drop “proofread” claim until done |
| 10 | Drivers claim | “58 built-in drivers” vs packs-not-in-JAR + many STUB/BETA | `product.md`, `glossary.md`, README | “~60 packs; maturity varies — see matrix” |

---

## P1 — high value next

| # | Finding | Files | Fix |
|---|---------|-------|-----|
| 11 | MES “Wave 8 complete / certified” vs scorecard MES 6.5 PARTIAL, ERP stub | `reference-mes-platform.md`, walkthroughs | Soften; certified = smoke, not plant MES |
| 12 | AI ≥95% / BL-178 framed as target but easy to read as met | `ai-agent.md`, `ai-development.md`, `operator-guide.md` | Explicit **not met**; one-shot REAL only |
| 13 | AI doc self-contradictions (step cap 96 vs 256; in-memory vs Postgres sessions) | `ai-development.md` | Single defaults table |
| 14 | `@dashboardContext` Planned vs principles treat as required | `dashboards.md`, `application-principles.md`, `platform-logic.md` | One readiness status |
| 15 | BPMN “2.0” without subset caveat; subProcess stub | `workflows.md` | Document supported subset |
| 16 | Alarm shelving stub omitted from automation guide | `automation.md` | Caveat + scorecard link |
| 17 | OPC UA “production” + SecurityPolicy None only | `drivers.md` | Lab vs field security prerequisites |
| 18 | Field pilot leads with PRODUCTION; ready-for-field buried | `field-pilot-playbook.md` | Lead with playbook-ready gate |
| 19 | Symbol count ~57 vs pack 218 | `scada.md` | One count + subset meaning |
| 20 | Web console “14 widgets” vs “40+” | `web-console.md` | Fix stale number |
| 21 | MFA `/verify` called stub; scorecard/code say REAL TOTP | `security.md` | Update to persisted MFA; residual gaps only |
| 22 | BL-210 Done vs historian scorecard still 7.0 | `analytics-platform-roadmap.md`, scorecard | Partial until Enterprise L sign-off dated |
| 23 | Cluster sold as default prod; demostand `clusterEnabled=false`; no CI load proof | `demostands.md`, `cluster.md` | Capability vs current prod caveat |
| 24 | Federation still “spike” while cert/ops treat as skill | `federation.md`, `certification.md` | Maturity label |
| 25 | `your-org` placeholders | marketplace, partner, analytics-packs, getting-started (if any remain) | Real org or `example.invalid` |
| 26 | RU stubs/drift: `operator-apps`, `bindings`, `driver-promotion` | `docs/ru/*` | Sync or EN-only banners |
| 27 | Russian registry on public EN path | `russian-software-registry.md` | Rights-holder / RU-canonical; not product hub |
| 28 | Labs without Internal banner | `lab-mqtt-*.md`, stress docs | Top banner: lab ops, not GA |
| 29 | Object model: ROLE typed CUSTOM; TENANT unmarked planned | `object-model.md` | Match code + planned badge |
| 30 | Broken `commercial-licensing` → license-builder link | `commercial-licensing.md` | `../../tools/license-builder/README.md` |
| 31 | Wrong ADR numbers in plugins | `plugins.md` | 0001/0002/0003 or 0016 |
| 32 | Scorecard pinned 0.9.102 vs roadmap 0.9.105 | `competitive-scorecard.md` | Re-audit / bump |

---

## P2 — polish / hygiene

- Duplicate ADR IDs (0016, 0025) noted in `decisions/readme.md` — disambiguate.  
- ADR index section “0038–0042” includes 0043–0046 — rename header.  
- `deployment.md` hostname mix (`ai.example.invalid` vs `ispf.example.invalid`).  
- Load-testing archaeology / historical baselines — label clearly.  
- Driver count ~58 vs ~62 packs — generate from catalog.  
- Screenshot convention missing in Builder docs (EN UI).  
- Soften `docs/README.md` “RU proofread” claim.  
- Default compose creds — bold “change before expose” everywhere.

---

## Layer health (subjective)

| Layer | Health | Notes |
|-------|--------|-------|
| Root README + screenshots | Good | Best OSS surface |
| Docs hub (on `main`) | Good | Role router + catalog |
| Getting started | Needs P0 auth/JDK | Try path exists; truth gaps remain |
| Product / architecture | Mixed | Depth good; license/Java/driver claims |
| Builder (HMI/OT/apps) | Weak for newcomers | Contract drift + overclaims |
| AI / MES | Mixed | Honest sections exist; headlines oversell |
| Ops / labs | Strong for operators | Too visible as “product docs” |
| Ecosystem | Weak | Stub vs GA |
| ADR set | Strong | Needs supersession banners for Apache-era ADRs |
| RU mirror | Uneven | product.md critical; some stubs |

---

## Recommended fix waves

### Wave B (1–2 days) — honesty + links
1. Bulk-fix `../packages|examples|LICENSE|NOTICE|tools` → `../../…` under `docs/en` and `docs/ru`.  
2. AGPL banners on `architecture.md` + ADR 0001/0003.  
3. Getting-started auth + JDK 25.  
4. Partner/marketplace Draft banners.  
5. Driver PRODUCTION honesty for opc-da / opc-bridge / DNP3 write.

### Wave C (3–5 days) — builder contracts
1. Unify 84-grid samples.  
2. Canonical deploy + BFF in solution-developer-guide.  
3. Replace obsolete `DeviceDriver` SPI snippet.  
4. MES/AI headline soften vs scorecard.  
5. Re-translate `docs/ru/product.md`.

### Wave D (ongoing) — status tags
1. Add `Status: Stable | Beta | Draft | Internal | Lab` to every hub-listed page.  
2. Extend `audit_links.py` to resolve repo-root relatives from `docs/{en,ru}/`.  
3. Screenshot pack for Builder EN UI.  
4. Re-pin competitive-scorecard to current release.

---

## What we did **not** do in this pass

- Line-by-line review of every lab report JSON / ordered-suite appendix.  
- Full EN↔RU paragraph sync for all 148 RU files.  
- Runtime verification of every REST example against a live server.  
- Legal review of AGPL/commercial wording (engineering audit only).

---

## Sign-off

| | |
|---|---|
| Date | 2026-07-16 |
| Scorecard reference | ~7.4/10 code-verified (audit baseline **0.9.102**; prod often **0.9.105+**) |
| Next action | Optional: capture Builder EN pack / hero GIF; regenerate AI context pack; deeper RU mid-body polish on huge files (`api.md`, `drivers.md`, `cluster.md`) |

## Wave D+ changelog (applied)

- Status one-liners on **all** EN hub pages + mirrored RU hub pages (`docs/ru/doc-status.md` pointer).
- Quick links / operator + web-console: dual ports JAR `:8080` + Vite `:5173`.
- Leftover honesty P0/P1: Bearer-default auth, JSON deploy (not ZIP), remove `your-org` URLs, 84-grid widget sizes, pack license ≠ platform AGPL, JDK 25 registry, marketplace GA soften, ADR-0006/0016 AGPL tense.
- RU honesty sync: product/platform core, SCADA/OT/historian (19), AI/ops/ecosystem (marketplace/scorecard/load-testing/release-dogfood/…).
- Residual: not full paragraph EN↔RU parity on enormous mid-bodies; no hero GIF; scorecard scores not re-audited.

## Wave D changelog (applied)

- New [doc-status.md](doc-status.md) vocabulary; **Status** column on EN/RU hub catalogs.
- Status one-liners on `product`, `getting-started`, `dashboards`, `web-console` (marketplace/partner already bannered).
- `audit_links.py`: also validates repo-root relatives (`packages/`, `examples/`, `LICENSE`, …).
- Screenshot convention: Builder EN pack table in [assets/README](../assets/README.md); pointers from dashboards / web-console.
- Competitive scorecard: dual pin **0.9.102** (audit) vs **0.9.105+** (prod/roadmap); DX score not inflated by docs-only work.
- Cleared remaining broken relatives surfaced by expanded auditor (kebab-case ADRs, `../../gradle`, analytics/operator stubs, missing `.cursor` rule → deployment pointers).

## Wave C changelog (applied)

- 84-grid samples: `widgets.md`, `applications.md`, `ai-agent.md` reference layout (EN/RU).
- Canonical JSON deploy + tree-first BFF in `solution-developer-guide.md` (EN/RU); removed multipart ZIP myth.
- `DeviceDriver` SPI snippet aligned with `ispf-driver-api` (EN/RU).
- MES / AI headlines softened vs competitive-scorecard; AI step cap default **256** + Postgres sessions.
- `docs/ru/product.md` re-translated from current EN.

## Wave B changelog (applied)

- Bulk-fixed ~324 broken `../packages|examples|LICENSE|…` links under `docs/en` + `docs/ru` (`tools/docs-audit/fix_repo_relative_links.py`).
- Getting-started EN/RU: JDK **25**, Bearer login (`admin`/`admin`), demoted `X-ISPF-Role`.
- Root README + product: Java 25 / driver-packs wording.
- Architecture + ADR-0001/0003 EN/RU: AGPL supersession notes.
- Partner + marketplace EN/RU: Draft / Partial banners; removed `your-org` portal links.
- Drivers + driver-promotion EN/RU: honesty for `opc-da` / `opc-bridge` / DNP3 write gap.
- `commercial-licensing.md`: fixed license-builder relative link.
