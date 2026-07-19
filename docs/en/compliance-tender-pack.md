> **Language:** Canonical English. Russian summary: [ru/compliance-tender-pack.md](../ru/compliance-tender-pack.md).

# Compliance tender pack (BL-192)

> **Status:** Stable — Documentation pack for enterprise tenders. Hub: [doc-status.md](doc-status.md).

Honest mapping of **what ISPF already ships** against common tender checklists (IEC 62443-oriented controls + GAMP-lite). This is **not** a product certification, validated CSV package, or pen-test report.

| | |
| --- | --- |
| **Backlog** | [BL-192](roadmap.md#bl-191193--domain-audit-follow-ups) — **Done** (docs pack published) |
| **Related** | [security](security.md), [multi-tenant](multi-tenant.md), [collaboration](collaboration.md) (config audit), [ai-development](ai-development.md) (AI audit), [certification](certification.md), [license-compliance](license-compliance.md), [air-gap-deployment](air-gap-deployment.md), [competitive-scorecard](competitive-scorecard.md) |
| **Do not claim** | IEC 62443 certified product · GAMP validated system · third-party pen-test pass · 21 CFR Part 11 certified |

---

## How to use this pack in a tender

1. Attach this page (or export PDF) as the **compliance posture** annex.
2. Point evaluators at the linked runbooks for MFA, tenancy, audit export, and air-gap.
3. Copy open rows from the [gap register](#gap-register) into the RFP response as **planned / Partial** — do not rephrase gaps as Done.
4. Separate **platform security features** (this pack) from **site validation** (customer IQ/OQ/PQ, GAMP lifecycle) and from **license** obligations ([license-compliance](license-compliance.md)).

**Evidence classes** (same vocabulary as the scorecard):

| Class | Meaning |
| ----- | ------- |
| **Exists** | Runtime path + docs; usable in production with stated caveats |
| **Partial** | Foundation shipped; named gaps remain |
| **Gap** | Not shipped / stub / customer-owned only |
| **N/A (site)** | Belongs to integrator / owner validation, not the OSS platform binary |

---

## IEC 62443 mapping (lite)

Lite mapping to common **IEC 62443-3-3 / 4-2** style themes (FR / SR families). Not a clause-by-clause certificate matrix.

| Theme (62443-oriented) | ISPF posture | Evidence | Class |
| ---------------------- | ------------ | -------- | ----- |
| Identification & authentication (I&A) | Local bearer login; OIDC/Keycloak in `dev`/`prod`; TOTP MFA enrollment + optional `required-for-admin` + login `totpCode` | [security](security.md) — MFA, auth profiles | **Exists** (TOTP GA, BL-153 Done); WebAuthn / IdP OTP → **BL-194** |
| Use control / authorization | HTTP RBAC; per-object ACL; per-variable roles; role-template ISA-95 `scopePathPrefixes` on REST | [security](security.md) — access matrix, ACL, variables | **Exists** (RBAC + ACL + BL-157 templates) |
| System integrity | Signed bundles / license enforce flags; `StartupSecurityGuard` warnings; driver pack profiles | [deployment](deployment.md), [license-compliance](license-compliance.md), [plugins](plugins.md) | **Partial** |
| Data confidentiality | TLS at ingress (ops); JWT/OIDC; secrets via env/vault guidance — no built-in field-level encryption product | [security](security.md) production recommendations | **Partial** (ops-owned TLS) |
| Restricted data flow / zones | Logical tenant path namespaces; federation orthogonal to tenant; air-gap / DMZ runbooks | [multi-tenant](multi-tenant.md), [federation](federation.md), [air-gap-deployment](air-gap-deployment.md) | **Partial** — no formal zone/conduit product model |
| Timely response to events | Alert rules, correlators, work queue; security audit categories (`auth`, `mfa`, `acl`, `object`) | [automation](automation.md), `GET /api/v1/audit/events` | **Partial** |
| Resource availability | Cluster / historian tiers / observability — separate maturity | [cluster](cluster.md), [historian-tiers](historian-tiers.md), [observability](observability.md) | **Partial** (see scorecard) |
| Component authenticity / SBOM | Third-party notices + SBOM guidance for releases | [license-compliance](license-compliance.md), [third-party-notices](third-party-notices.md) | **Exists** (process); continuous attestation **Gap** |
| Multi-tenancy isolation | **Logical** SaaS A≠B (path+API+`tenant-admin`) **Exists**; OIDC `tenant_id` claim; **hard** mode schema provision/drop; platform table routing still optional | [multi-tenant](multi-tenant.md) | **Exists** (logical SaaS); hard table routing **Partial** |

### Zone / conduit note

ISPF does **not** ship a certified 62443 zone architecture. Typical tender response: map customer zones to **network segmentation + Keycloak + tenant namespaces + air-gap edge**, then cite this pack for software controls inside the application zone.

---

## GAMP-lite checklist

GAMP 5–style categories for computerized systems. ISPF is a **configurable industrial application platform** — category assignment for a site system is owned by the customer validation team.

| GAMP-lite topic | Platform contribution | Site / integrator | Class |
| --------------- | --------------------- | ----------------- | ----- |
| Intended use / URS | Product docs: [product](product.md), [architecture](architecture.md), [roadmap](roadmap.md) | Customer URS | **Exists** (docs) / **N/A (site)** |
| Risk assessment | Competitive + domain gap honesty; OT maturity labels | Customer FMEA / risk file | **Partial** / **N/A (site)** |
| Supplier assessment | Open source + dual license; partner curriculum stub | Vendor audit questionnaire | **Partial** — [certification](certification.md), [partner-program](partner-program.md) |
| Spec / design | ADRs under `docs/en/decisions/`, object model, API | Design qualification | **Exists** (engineering docs) |
| Configuration mgmt | Object tree revisions, If-Match; config audit trail | Change control SOP | **Exists** — [collaboration](collaboration.md) |
| Build / release | Gradle CI, release dogfood, demostand pins | IQ of deployed build | **Partial** — [release-dogfood](release-dogfood.md), [testing](testing.md) |
| Access control | RBAC + MFA + ACL (see above) | SOPs, joiner/leaver | **Exists** / **Partial** |
| Audit trail | Security audit events + CSV export + SIEM webhook; object config audit; AI tool audit | Retention / review SOP | **Exists** (BL-156 Done); WORM/retention still ops |
| Backup / restore | Deploy / demostand / air-gap runbooks | DR test evidence | **Partial** / **N/A (site)** |
| IQ / OQ / PQ | Lab training + certification path stubs | Formal protocols | **Gap** (platform) / **N/A (site)** |
| Deviation / CAPA | Alarm/work-queue workflows only | QMS outside ISPF | **Gap** (as QMS) |
| Electronic records / signatures | Auth + audit events; **not** Part 11 / Annex 11 certified e-sign | Legal assessment | **Gap** — do not claim |

---

## Gap register

Open items that tenders often treat as blockers. Track engineering work under Phase 27 BL rows — this register is the tender-facing view.

| ID | Topic | Today | Gap | Owner BL / doc |
| -- | ----- | ----- | --- | -------------- |
| G-01 | **Independent pen-test** | Hardening guidance + unit/API tests | No published third-party pen-test report; Phase 27 metric “pentest pass” **not met** | Phase 27 metric; customer or hired assessment |
| G-02 | **Audit trail GA** | Append audit + CSV export + SIEM webhook (`ISPF_AUDIT_SIEM_WEBHOOK_URL`) | Retention/WORM not productized; not a full GxP audit package | [BL-156](roadmap.md#phase-27--enterprise-security) **Done**; [collaboration](collaboration.md); [ai-development](ai-development.md) |
| G-03 | **Hard multi-tenancy** | Logical SaaS A≠B + `tenant-admin` **Done**; hard mode schema provision/drop + OIDC claim | Platform object tables still shared — **do not claim A≠B DB row isolation** | [BL-155](roadmap.md#phase-27--enterprise-security); [multi-tenant](multi-tenant.md) |
| G-04 | **MFA completeness** | Persisted TOTP + `required-for-admin` + login UX **Exists** (BL-153 Done) | WebAuthn / Keycloak OTP → BL-194; default MFA off | [BL-153](roadmap.md#phase-27--enterprise-security); [security](security.md) |
| G-05 | **Per-variable / custom roles** | Variable roles + history ACL + event/function `invokeRoles` API/UI + ISA-95 template scopes on REST | Analytics/federation alternate paths may bypass member ACL | [BL-154](roadmap.md#phase-27--enterprise-security) **Done**, [BL-157](roadmap.md#phase-27--enterprise-security) **Done** |
| G-06 | **IEC 62443 certification** | This lite mapping | No accredited 62443 product certificate | Out of scope for BL-192 docs; future if sponsored |
| G-07 | **GAMP / CSV package** | This checklist + engineering docs | No supplier IQ/OQ templates or validated-state claim | Customer validation; [certification](certification.md) training only |
| G-08 | **Security doc drift** | Scorecard / code: TOTP REAL | `[security.md](security.md)` may lag on MFA wording — prefer code + this pack + tests for tender evidence | Docs maintenance |

---

## Evidence quick links

| Need | Where |
| ---- | ----- |
| RBAC / MFA / ACL | [security](security.md) |
| Tenant isolation modes | [multi-tenant](multi-tenant.md) |
| Object change history | [collaboration](collaboration.md) — `object_config_audit` |
| Security audit API | `GET /api/v1/audit/events`, `GET /api/v1/audit/events/export` |
| AI tool audit | [ai-development](ai-development.md) — `GET .../sessions/{id}/audit` |
| Offline / DMZ install | [air-gap-deployment](air-gap-deployment.md) |
| License / SBOM obligations | [license-compliance](license-compliance.md) |
| Training (not compliance cert) | [certification](certification.md) |
| Score honesty | [competitive-scorecard](competitive-scorecard.md) — Security **7.5 PARTIAL** |
| Roadmap / DoD | [roadmap](roadmap.md#definition-of-done--1010-overall) item 10 |

---

## Acceptance (BL-192)

| Criterion | Status |
| --------- | ------ |
| IEC 62443 mapping lite published | **Met** — this page |
| GAMP-lite checklist published | **Met** — this page |
| Gap register (pen-test, audit trail, hard tenancy, …) | **Met** — this page |
| Linked from DoD item 10 + competitive scorecard | **Met** |
| Product certified / pen-test passed | **Not claimed** |
