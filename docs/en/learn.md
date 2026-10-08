> **Language:** Canonical English. Russian edition: [ru/learn.md](../ru/learn.md).

# Learn ISPF — transparent entry

> **Status:** Stable — Learning hub. Hub tags: [doc-status.md](doc-status.md).

**One page to decide where you are and what to open next.**  
Everything else (guides, labs, certification) hangs off this map.

A clear learning spine is Getting started → Quick start → Tutorials → How-to → Troubleshooting. ISPF keeps that clarity, with **safer defaults** ([anti-patterns](anti-patterns.md)).

---

## 60-second orientation

| Question | Answer in ISPF |
|----------|----------------|
| Where does my solution live? | **Object tree** (`root.platform…`) — one runtime |
| Where does logic go? | **SINGLETON** hub or **INSTANCE** twin — never `DEVICE` |
| How do I ship? | **Bundle** (`ispf pack` / deploy) + optional **ui-pack** |
| What must I not do? | Heavy widget logic, MQTT journal flood, binding cycles — [anti-patterns](anti-patterns.md) |

---

## Path A — First hour (try + understand)

| Step | Time | Do this | Doc |
|------|------|---------|-----|
| 1 | 15 min | Run platform, sign in, open demo sensor + dashboard | [Getting started — Try](getting-started.md#try-ispf-15-minutes) |
| 2 | 10 min | Start demo driver; watch temperature / alarm | Getting started § First steps |
| 3 | 10 min | Operator mode (`?mode=operator`) | [Operator guide](operator-guide.md) |
| 4 | 15 min | Skim object tree concepts | [Object model](object-model.md) |
| 5 | 10 min | Read “what not to build” | [Anti-patterns](anti-patterns.md) |

**Done when:** you can explain tree → variable → binding/alert → dashboard without opening Java.

---

## Path B — First solution day (engineer)

Quick-start spine (device → data → viz → automation → ship):

| Step | Goal | Doc / lab |
|------|------|-----------|
| 1 | Hub + DEVICE child (or use lab pack) | [Application principles](application-principles.md) § Logic objects · [Lab training](lab-training.md) |
| 2 | Driver RUNNING | [Drivers](drivers.md) |
| 3 | Binding rule or hub function | [Bindings](bindings.md)#execution · [Expression language](expression-language.md) |
| 4 | Dashboard / SCADA widget | [Dashboards](dashboards.md) · [Widgets](widgets.md) |
| 5 | Alert → optional workflow | [Automation](automation.md) · [Workflows](workflows.md) |
| 6 | Pack & validate | [Solution developer guide](solution-developer-guide.md) § solution-as-repo |

**Done when:** `validate_bundle` is green and operator UI opens without Admin Explorer.

---

## Path C — By role

| I am… | Follow | Then certify |
|-------|--------|--------------|
| **Operator / HMI user** | Path A → [Operator guide](operator-guide.md) → [SCADA](scada.md) | — |
| **Solution developer** | Path A → B → [External AI IDE](external-ide.md) (optional SPA) | [Certification — Solution developer](certification.md#solution-developer-track) |
| **OT / drivers** | Path A → [Drivers](drivers.md) → [Field pilot](field-pilot-playbook.md) | Admin L2 modules in certification |
| **Platform admin** | [Deployment](deployment.md) → [Security](security.md) → [Observability](observability.md) | [Certification — Platform admin](certification.md#platform-admin-track) |
| **AI / agent author** | [AI development](ai-development.md) → [Agent knowledge](agent-knowledge.md) → [OT Automation tutorials](ot-automation-excellence-tutorials.md) | Solution developer L3 |

---

## Tutorials & walkthroughs (hands-on)

### Core OT automation (timed)

Hub: [OT Automation Excellence tutorials](ot-automation-excellence-tutorials.md) (~10–20 min each).

### Reference solutions (import & explore)

| Walkthrough | What you learn |
|-------------|----------------|
| [Lab training](lab-training.md) | Virtual device, dashboards, reports |
| [MES reference](reference-mes-walkthrough.md) | Manufacturing tree + operator app |
| [MES OEE](reference-mes-oee-walkthrough.md) | OEE KPIs |
| [mini-TEC](reference-mini-tec-walkthrough.md) | Energy / SLD HMI |
| [Building HVAC](reference-building-hvac-walkthrough.md) | Building automation pattern |

### Deep dives (when you need them)

| Topic | Doc |
|-------|-----|
| Historian / rollups | [Variable history](variable-history.md) · [Analytics cookbook](analytics-historian-cookbook.md) |
| Blueprints / SHAPE | [Blueprints](blueprints.md) |
| Collaboration / leases | [Collaboration](collaboration.md) |
| Troubleshooting production | [Observability](observability.md) · [Deployment](deployment.md) |

---

## Topic map → ISPF docs

| Topic | ISPF home |
|-------|-----------|
| Getting started / Install | [Getting started](getting-started.md) · [Deployment](deployment.md) |
| Quick start (device → viz → DB) | **Path B** above |
| Tutorial / video courses | Path C + OT tutorials + reference walkthroughs |
| How to | Topic docs + [expression-language](expression-language.md) recipes |
| How **not** to | [Anti-patterns](anti-patterns.md) |
| Troubleshooting | [Observability](observability.md) · lab stress docs · support via [product](product.md) |
| Glossary | [Glossary](glossary.md) |
| Certification | [Certification](certification.md) |

---

## Teaching tips (for trainers / partners)

1. **Demo first, theory second** — Path A before object-model lectures.  
2. **Show the anti-pattern** — one bad widget expression vs hub function (why ISPF is faster/safer).  
3. **Always end with ship** — pack/validate, not a snowflake tree.  
4. **One hub rule** — chant SINGLETON/INSTANCE vs DEVICE until it sticks.  
5. **Use lab packs** — `examples/lab-training`, `mes-reference` beat empty sandboxes.

Partner curriculum hours: [Certification](certification.md) · [Partner program](partner-program.md).

---

## Related

| Doc | Role |
|-----|------|
| [docs/en/readme](readme.md) | Full catalog |
| [Getting started](getting-started.md) | Boot + first login |
| [Anti-patterns](anti-patterns.md) | What not to recreate from field pain |
| [Application principles](application-principles.md) | P1–P10 for authors & agents |

*Keep this page short. Add links, not essays — detail lives in topic docs.*
