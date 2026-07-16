> **Language:** Canonical English. Russian edition: [ru/partner-program.md](../ru/partner-program.md).

# Partner program (BL-184)

> **Status: Draft / not GA.** Curriculum and tiers below are the design target. In-server `GET /api/v1/partners/*` is a **stub** (`PartnerProgramService`, `"source": "stub"`). There is no shipping Partner Portal in this repository yet. See [competitive-scorecard](competitive-scorecard.md) dimension 12.

ISPF certified integrator and solution partner program (planned). Design notes:

- External Partner Portal — **not in this repo** (configure separately when available)
- Planned API surface: `GET /api/v1/tiers`, applications, directory, training progress
- Today: demo partners hardcoded; enroll is synthetic until a real portal syncs

Related: [marketplace](marketplace.md), [certification](certification.md), [solution-developer-guide](solution-developer-guide.md).

---

## Program goals

| Goal | Target |
|------|--------|
| Certified integrators | 5+ by Phase 32 GA |
| Time-to-first-demo (with AI Studio) | ≤ 2 hours |
| Supported verticals | SCADA, MES, HVAC, warehouse |
| Marketplace listings from partners | 3+ signed bundles |

---

## Certification levels

| Level | Audience | Prerequisites | Validates |
|-------|----------|---------------|-----------|
| **Associate** | Developers new to ISPF | Platform fundamentals course | Object tree, bundles, operator UI deploy |
| **Professional** | Solution integrators | Associate + 1 shipped project | Drivers, dashboards, automation, federation basics |
| **Expert** | Lead architects / OEM partners | Professional + 2 production sites | Cluster, historian tiers, MES bundle, agent playbooks |
| **OEM** | Product vendors (drivers, symbols, analytics KPI packs) | Legal + interop lab | Driver / `analytics-pack` signing, marketplace listing, support SLA |

---

## Training curriculum (draft)

### Associate track (~16 h)

1. Object model and blueprints ([object-model](object-model.md))
2. Bundle lifecycle ([applications](applications.md))
3. Operator HMI ([operator-guide](operator-guide.md))
4. Lab: deploy `demo-app` + custom dashboard

### Professional track (~24 h)

1. Drivers and virtual devices ([drivers](drivers.md))
2. SCADA mimics ([scada](scada.md))
3. Automation and workflows ([automation](automation.md), [workflows](workflows.md))
4. Lab: SCADA or MES reference walkthrough

### Expert track (~32 h)

1. Cluster and federation ([cluster](cluster.md), [federation](federation.md))
2. Historian tiers ([historian-tiers](historian-tiers.md))
3. AI agent and solution generator ([ai-development](ai-development.md))
4. Lab: production-ready site with alerts + operator app

---

## Partner benefits

| Benefit | Associate | Professional | Expert | OEM |
|---------|:---------:|:------------:|:------:|:---:|
| Partner directory listing | ✓ | ✓ | ✓ | ✓ |
| Marketplace revenue share | — | ✓ | ✓ | ✓ |
| Early access builds | — | ✓ | ✓ | ✓ |
| Co-marketing / case studies | — | — | ✓ | ✓ |
| Driver pack co-signing | — | — | — | ✓ |
| Priority support channel | — | — | ✓ | ✓ |

---

## Application process

1. **Apply** — contact form + company profile (legal entity, regions, verticals).
2. **Screening** — technical interview + NDA.
3. **Training** — self-paced modules + instructor-led labs (remote).
4. **Exam** — practical deploy scenario (bundle + operator UI + one automation artifact).
5. **Certification** — badge + listing in partner directory; renewal annually.

---

## Exam scenarios (outline)

| Level | Scenario | Pass criteria |
|-------|----------|---------------|
| Associate | Deploy `lab-training` bundle, add dashboard widget | Operator app loads; widget shows live value |
| Professional | SNMP or virtual device + alert rule | Driver RUNNING; alert fires on threshold |
| Expert | Federation bind + MES dispatch screen | Remote device readable; BFF invoke OK |
| OEM | Submit driver pack or analytics pack + marketplace manifest | Interop CI green; listing validates; catalog shows new helpers |

---

## Legal and branding

- Partners may use **ISPF Certified Partner** badge per level guidelines (TBD).
- Marketplace bundles require signed manifest and license metadata ([marketplace](marketplace.md)).
- GPL/LGPL driver packs excluded from default prod profile unless customer legal review.

---

## Roadmap

| Milestone | Target |
|-----------|--------|
| Partner portal (apply, training progress) | External MVP — **not in this repo** |
| First 5 certified integrators | Phase 32 GA |
| OEM symbol + driver marketplace GA | BL-183, BL-185 |

See [roadmap](roadmap.md) — BL-184, BL-190.
