> **Language:** Canonical English. Russian edition: [ru/partner-program.md](../ru/partner-program.md).

# Partner program (BL-184)

ISPF certified integrator and solution partner program. **Partner Portal** is a separate service:

- Repo: [Partner-portal](https://github.com/Michaael/Partner-portal)
- API: `GET /api/v1/tiers`, applications, directory, training progress
- In-server API `GET /api/v1/partners/*` — **stub** (`PartnerProgramService`, `"source": "stub"`); demo partners hardcoded; enroll is synthetic until Partner Portal sync

Related: [marketplace.md](marketplace.md), [certification.md](certification.md), [solution-developer-guide.md](solution-developer-guide.md).

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
| **OEM** | Product vendors (drivers, symbols) | Legal + interop lab | Driver pack signing, marketplace listing, support SLA |

---

## Training curriculum (draft)

### Associate track (~16 h)

1. Object model and blueprints ([object-model.md](object-model.md))
2. Bundle lifecycle ([applications.md](applications.md))
3. Operator HMI ([operator-guide.md](operator-guide.md))
4. Lab: deploy `demo-app` + custom dashboard

### Professional track (~24 h)

1. Drivers and virtual devices ([drivers.md](drivers.md))
2. SCADA mimics ([scada.md](scada.md))
3. Automation and workflows ([automation.md](automation.md), [workflows.md](workflows.md))
4. Lab: SCADA or MES reference walkthrough

### Expert track (~32 h)

1. Cluster and federation ([cluster.md](cluster.md), [federation.md](federation.md))
2. Historian tiers ([historian-tiers.md](historian-tiers.md))
3. AI agent and solution generator ([ai-development.md](ai-development.md))
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
| OEM | Submit driver pack + marketplace manifest | Interop CI green; listing validates |

---

## Legal and branding

- Partners may use **ISPF Certified Partner** badge per level guidelines (TBD).
- Marketplace bundles require signed manifest and license metadata ([marketplace.md](marketplace.md)).
- GPL/LGPL driver packs excluded from default prod profile unless customer legal review.

---

## Roadmap

| Milestone | Target |
|-----------|--------|
| Partner portal (apply, training progress) | [Partner-portal](https://github.com/Michaael/Partner-portal) MVP |
| First 5 certified integrators | Phase 32 GA |
| OEM symbol + driver marketplace GA | BL-183, BL-185 |

See [roadmap-phase-25.md](roadmap-phase-25.md) — BL-184, BL-190.
