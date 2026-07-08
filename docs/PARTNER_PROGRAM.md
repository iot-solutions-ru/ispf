# Partner program (BL-184)

ISPF certified integrator and solution partner program. **Partner Portal** — отдельный сервис:

- Repo: [Partner-portal](https://github.com/Michaael/Partner-portal)
- API: `GET /api/v1/tiers`, applications, directory, training progress
- In-server stub `GET /api/v1/partners/tiers` в `ispf-server` — legacy placeholder; canonical catalog на Partner Portal

Related: [MARKETPLACE.md](MARKETPLACE.md), [CERTIFICATION.md](CERTIFICATION.md), [SOLUTION_DEVELOPER_GUIDE.md](SOLUTION_DEVELOPER_GUIDE.md).

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

1. Object model and blueprints ([OBJECT_MODEL.md](OBJECT_MODEL.md))
2. Bundle lifecycle ([APPLICATIONS.md](APPLICATIONS.md))
3. Operator HMI ([OPERATOR_GUIDE.md](OPERATOR_GUIDE.md))
4. Lab: deploy `demo-app` + custom dashboard

### Professional track (~24 h)

1. Drivers and virtual devices ([DRIVERS.md](DRIVERS.md))
2. SCADA mimics ([SCADA.md](SCADA.md))
3. Automation and workflows ([AUTOMATION.md](AUTOMATION.md), [WORKFLOWS.md](WORKFLOWS.md))
4. Lab: SCADA or MES reference walkthrough

### Expert track (~32 h)

1. Cluster and federation ([CLUSTER.md](CLUSTER.md), [FEDERATION.md](FEDERATION.md))
2. Historian tiers ([HISTORIAN_TIERS.md](HISTORIAN_TIERS.md))
3. AI agent and solution generator ([AI_DEVELOPMENT.md](AI_DEVELOPMENT.md))
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
- Marketplace bundles require signed manifest and license metadata ([MARKETPLACE.md](MARKETPLACE.md)).
- GPL/LGPL driver packs excluded from default prod profile unless customer legal review.

---

## Roadmap

| Milestone | Target |
|-----------|--------|
| Partner portal (apply, training progress) | [Partner-portal](https://github.com/Michaael/Partner-portal) MVP |
| First 5 certified integrators | Phase 32 GA |
| OEM symbol + driver marketplace GA | BL-183, BL-185 |

See [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md) — BL-184, BL-190.
