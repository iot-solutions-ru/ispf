# Certification paths (BL-190)

Structured certification tracks for integrators, operators, and platform administrators. Aligns with [PARTNER_PROGRAM.md](PARTNER_PROGRAM.md) partner levels and internal lab walkthroughs.

---

## Overview

| Track | Audience | Outcome |
|-------|----------|---------|
| **Integrator** | Solution developers, SI partners | Deploy production-ready ISPF sites |
| **Operator** | Plant operators, shift supervisors | Safe HMI + agent copilot usage |
| **Platform admin** | IT/OT admins, DevOps | Secure multi-tenant platform ops |
| **MES specialist** | Manufacturing engineers | ISA-95 dispatch + OEE without custom Java |

---

## Integrator certification

### Level 1 — Foundation

| Module | Doc reference | Lab |
|--------|---------------|-----|
| Object tree | [OBJECT_MODEL.md](OBJECT_MODEL.md) | Create DEVICE + list_variables |
| Bundles | [APPLICATIONS.md](APPLICATIONS.md) | Deploy `demo-app` |
| Dashboards | [DASHBOARDS.md](DASHBOARDS.md) | Add value + chart widgets |
| Operator UI | [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) | Configure operator app |

**Exam:** Deploy bundle with one dashboard; operator mode loads without admin console.

### Level 2 — Automation

| Module | Doc reference | Lab |
|--------|---------------|-----|
| Drivers | [DRIVERS.md](DRIVERS.md) | SNMP or virtual device RUNNING |
| Alerts | [AUTOMATION.md](AUTOMATION.md) | configure_alert + fire event |
| Workflows | [WORKFLOWS.md](WORKFLOWS.md) | User task in work queue |
| Reports | [REPORTS.md](REPORTS.md) | SQL report in operator app |

**Exam:** Alert → correlator → operator notification path.

### Level 3 — Production

| Module | Doc reference | Lab |
|--------|---------------|-----|
| SCADA mimics | [SCADA.md](SCADA.md) | Mimic with live bindings |
| Federation | [FEDERATION.md](FEDERATION.md) | Bind remote device |
| Cluster | [CLUSTER.md](CLUSTER.md) | Two-replica lab |
| AI agent | [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) | Solution generator scenario |

**Exam:** End-to-end agent deploy (BL-177) — spec to operator UI without manual tree edits.

Maps to **Partner Professional / Expert** — see [PARTNER_PROGRAM.md](PARTNER_PROGRAM.md).

---

## Operator certification

| Module | Content |
|--------|---------|
| HMI navigation | Dashboard tabs, selectionKey drill-down |
| Work queue | Claim / complete BPMN tasks |
| Event journal | Alarm levels, filters |
| Agent copilot | Scoped read-only tools, memory, reports |

**Exam:** Complete shift checklist using operator HMI + agent (trend, report, work queue).

Reference: [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) — Operator agent section.

---

## Platform admin certification

| Module | Content |
|--------|---------|
| Security | RBAC, MFA, audit ([SECURITY.md](SECURITY.md)) |
| Deploy | VPS direct, Helm skeleton ([deploy/helm/ispf/](../deploy/helm/ispf/)) |
| Observability | Metrics, diagnostics ([OBSERVABILITY.md](OBSERVABILITY.md)) |
| Historian | Tier profiles ([HISTORIAN_TIERS.md](HISTORIAN_TIERS.md)) |

**Exam:** Hardened single-node deploy + backup/restore drill.

---

## MES specialist certification

| Module | Content |
|--------|---------|
| ISA-95 catalog | [ISA95_CATALOG.md](ISA95_CATALOG.md) |
| MES reference | [REFERENCE_MES_PLATFORM.md](REFERENCE_MES_PLATFORM.md) |
| OEE walkthrough | [REFERENCE_MES_OEE_WALKTHROUGH.md](REFERENCE_MES_OEE_WALKTHROUGH.md) |
| Bundle | `examples/mes-platform/` |

**Exam:** Deploy MES bundle ≤ 30 min; dispatch order through BFF; OEE screen live.

Maps to **BL-170** MES certification bundle acceptance.

---

## Badge and renewal

| Item | Policy |
|------|--------|
| Badge validity | 12 months |
| Renewal | Pass delta exam or complete continuing education module |
| Revocation | Critical security incident or license violation |

---

## Agent regression alignment

Certification labs feed the [agent regression suite](AGENT_REGRESSION.md). Target: ≥95% scenario pass rate (BL-178) before Expert integrator exam uses live agent grading.

---

## Related

- [PARTNER_PROGRAM.md](PARTNER_PROGRAM.md) — commercial partner tiers
- [COMPETITIVE_SCORECARD.md](COMPETITIVE_SCORECARD.md) — dimension 13 Documentation/DX
- [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md) — BL-190
