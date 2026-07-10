# Certification paths (BL-190)

Stub curriculum for ISPF certification. Full exams and proctoring ship with Phase 32 GA; labs below are runnable today on lab/VPS hosts.

Aligns with [partner-program](partner-program.md) commercial tiers.

---

## Tracks overview

| Track | Audience | Outcome badge |
| ----- | -------- | ------------- |
| **Solution developer** | Integrators, OEM app authors | Deploy production bundles without core-team support |
| **Platform admin** | IT/OT ops, DevOps | Hardened deploy, security, historian, observability |

Additional tracks (operator, MES specialist) — draft modules at end of document.

---

## Solution developer track

Maps to Partner **Associate → Professional** in [partner-program](partner-program.md).

### Level 1 — Foundation (~16 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Object tree | [object-model](object-model.md) | Create DEVICE + `list_variables` |
| Bundles | [applications](applications.md) | Deploy `demo-app` |
| Dashboards | [dashboards](dashboards.md) | Value + chart widgets |
| Operator UI | [operator-guide](operator-guide.md) | Configure operator app |

**Exam (stub):** Deploy bundle with one dashboard; operator mode loads without admin console.

### Level 2 — Automation (~24 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Drivers | [drivers](drivers.md) | SNMP or virtual device `RUNNING` |
| Field pilots | [field-pilot-playbook](field-pilot-playbook.md) | Complete one OT scenario checklist |
| Alerts | [automation](automation.md) | `configure_alert` + fire event |
| Workflows | [workflows](workflows.md) | User task in work queue |

**Exam (stub):** Alert → correlator → operator notification path.

### Level 3 — Production (~32 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| SCADA mimics | [scada](scada.md) | Mimic with live bindings |
| Federation | [federation](federation.md) | Bind remote device |
| AI agent | [ai-development](ai-development.md) | Solution generator scenario (BL-177) |

**Exam (stub):** End-to-end agent deploy — spec to operator UI without manual tree edits.

---

## Exam question bank (stub)

Machine-readable banks live under [`examples/certification/`](../examples/certification/). Format: JSON with `track`, `level`, `version`, and `questions[]` (`id`, `type`, `topic`, `prompt`, `options`, `correctIndex`, `reference`).

| Bank file | Track | Level | Questions |
| --------- | ----- | ----- | --------- |
| [`solution-developer-l1.json`](../examples/certification/solution-developer-l1.json) | Solution developer | L1 Foundation | 8 |
| [`solution-developer-l2.json`](../examples/certification/solution-developer-l2.json) | Solution developer | L2 Automation | 6 |
| [`platform-admin-core.json`](../examples/certification/platform-admin-core.json) | Platform admin | Core | 8 |

**Grading (stub):** Partner Portal / LMS integration scores multiple-choice locally; practical labs remain instructor-verified until Phase 32 GA proctoring.

**Import example:**

```bash
curl -s examples/certification/solution-developer-l1.json | jq '.questions | length'
```

---

## Platform admin track

Maps to internal ops onboarding and Partner **Expert** infrastructure modules.

### Core modules (~24 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Security | [security](security.md) | RBAC, MFA enrollment, audit export |
| Deploy | [deployment](deployment.md) | VPS direct or Helm skeleton |
| Historian | [historian-tiers](historian-tiers.md) | Hot tier + Parquet export (BL-163) |
| Observability | [observability](observability.md) | Metrics scrape + diagnostics bundle |

**Exam (stub):** Hardened single-node deploy + backup/restore drill; ClickHouse verify script green.

### Advanced modules (~16 h)

| Module | Reference | Lab |
| ------ | --------- | --- |
| Cluster | [cluster](cluster.md) | Two-replica lab |
| Multi-tenant | [multi-tenant](multi-tenant.md) | Tenant isolation write test |
| Federation hub | [federation](federation.md) | Hub with 2+ peers (BL-188) |

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

Certification labs feed the [agent regression suite](agent-regression.md). Target: ≥95% scenario pass rate (BL-178) before live agent grading on Expert exams.

---

## Other tracks (draft)

| Track | Audience | Key doc |
| ----- | -------- | ------- |
| Operator | Shift supervisors | [operator-guide](operator-guide.md) |
| MES specialist | Manufacturing engineers | [reference-mes-platform](reference-mes-platform.md) |

---

## Related

- [partner-program](partner-program.md) — commercial partner tiers
- [competitive-scorecard](competitive-scorecard.md) — dimension 13 Documentation/DX
- [roadmap](roadmap.md) — BL-190
