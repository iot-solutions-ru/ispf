# Certification paths (BL-190)

Stub curriculum for ISPF certification. Full exams and proctoring ship with Phase 32 GA; labs below are runnable today on lab/VPS hosts.

Aligns with [PARTNER_PROGRAM.md](PARTNER_PROGRAM.md) commercial tiers.

---

## Tracks overview

| Track | Audience | Outcome badge |
| ----- | -------- | ------------- |
| **Solution developer** | Integrators, OEM app authors | Deploy production bundles without core-team support |
| **Platform admin** | IT/OT ops, DevOps | Hardened deploy, security, historian, observability |

Additional tracks (operator, MES specialist) — draft modules at end of document.

---

## Solution developer track

Maps to Partner **Associate → Professional** in [PARTNER_PROGRAM.md](PARTNER_PROGRAM.md).

### Level 1 — Foundation (~16 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Object tree | [OBJECT_MODEL.md](OBJECT_MODEL.md) | Create DEVICE + `list_variables` |
| Bundles | [APPLICATIONS.md](APPLICATIONS.md) | Deploy `demo-app` |
| Dashboards | [DASHBOARDS.md](DASHBOARDS.md) | Value + chart widgets |
| Operator UI | [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) | Configure operator app |

**Exam (stub):** Deploy bundle with one dashboard; operator mode loads without admin console.

### Level 2 — Automation (~24 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Drivers | [DRIVERS.md](DRIVERS.md) | SNMP or virtual device `RUNNING` |
| Field pilots | [FIELD_PILOT_PLAYBOOK.md](FIELD_PILOT_PLAYBOOK.md) | Complete one OT scenario checklist |
| Alerts | [AUTOMATION.md](AUTOMATION.md) | `configure_alert` + fire event |
| Workflows | [WORKFLOWS.md](WORKFLOWS.md) | User task in work queue |

**Exam (stub):** Alert → correlator → operator notification path.

### Level 3 — Production (~32 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| SCADA mimics | [SCADA.md](SCADA.md) | Mimic with live bindings |
| Federation | [FEDERATION.md](FEDERATION.md) | Bind remote device |
| AI agent | [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md) | Solution generator scenario (BL-177) |

**Exam (stub):** End-to-end agent deploy — spec to operator UI without manual tree edits.

---

## Platform admin track

Maps to internal ops onboarding and Partner **Expert** infrastructure modules.

### Core modules (~24 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Security | [SECURITY.md](SECURITY.md) | RBAC, MFA enrollment, audit export |
| Deploy | [DEPLOYMENT.md](DEPLOYMENT.md) | VPS direct or Helm skeleton |
| Historian | [HISTORIAN_TIERS.md](HISTORIAN_TIERS.md) | Hot tier + Parquet export (BL-163) |
| Observability | [OBSERVABILITY.md](OBSERVABILITY.md) | Metrics scrape + diagnostics bundle |

**Exam (stub):** Hardened single-node deploy + backup/restore drill; ClickHouse verify script green.

### Advanced modules (~16 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Cluster | [CLUSTER.md](CLUSTER.md) | Two-replica lab |
| Multi-tenant | [MULTI_TENANT.md](MULTI_TENANT.md) | Tenant isolation write test |
| Federation hub | [FEDERATION.md](FEDERATION.md) | Hub with 2+ peers (BL-188) |

**Exam (stub):** Failover drill; tenant A cannot read tenant B variables.

---

## Badge and renewal (draft)

| Item | Policy |
| ---- | ------ |
| Badge validity | 12 months |
| Renewal | Delta exam or continuing-education module |
| Revocation | Critical security incident or license violation |
| Proctoring | TBD — Phase 32 GA |

---

## Agent regression alignment

Certification labs feed the [agent regression suite](AGENT_REGRESSION.md). Target: ≥95% scenario pass rate (BL-178) before live agent grading on Expert exams.

---

## Other tracks (draft)

| Track | Audience | Key doc |
| ----- | -------- | ------- |
| Operator | Shift supervisors | [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) |
| MES specialist | Manufacturing engineers | [REFERENCE_MES_PLATFORM.md](REFERENCE_MES_PLATFORM.md) |

---

## Related

- [PARTNER_PROGRAM.md](PARTNER_PROGRAM.md) — commercial partner tiers
- [COMPETITIVE_SCORECARD.md](COMPETITIVE_SCORECARD.md) — dimension 13 Documentation/DX
- [ROADMAP_PHASE25.md](ROADMAP_PHASE25.md) — BL-190
