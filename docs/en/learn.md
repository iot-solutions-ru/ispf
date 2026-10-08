> **Language:** Canonical English. Russian edition: [ru/learn.md](../ru/learn.md).

# Learn ISPF — transparent entry

> **Status:** Stable — Learning hub. Hub tags: [doc-status.md](doc-status.md).

**One page to decide where you are and what to open next.**  
Everything else (guides, labs, certification) hangs off this map.

Learning spine: **Getting started → Quick start → Curriculum → How-to → How not to → Troubleshooting**.  
Text-first: read + do labs; no video track required.

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
| **Solution developer** | Path A → B → Curriculum below → [External AI IDE](external-ide.md) (optional SPA) | [Certification — Solution developer](certification.md#solution-developer-track) |
| **OT / drivers** | Path A → [Drivers](drivers.md) → [Field pilot](field-pilot-playbook.md) | Admin L2 modules in certification |
| **Platform admin** | [Deployment](deployment.md) → [Security](security.md) → [Observability](observability.md) | [Certification — Platform admin](certification.md#platform-admin-track) |
| **AI / agent author** | [AI development](ai-development.md) → [Agent knowledge](agent-knowledge.md) → [OT Automation tutorials](ot-automation-excellence-tutorials.md) | Solution developer L3 |

---

## Curriculum — deep track (text)

After Path A/B: work the modules in order. Each row is **read the doc → do the practice** (lab pack, demo tree, or mini exercise). Same progression as a classic platform course: goals → model → I/O → logic → history → UI → ship → ops.

| # | Module | Learn | Practice | Doc |
|---|--------|-------|----------|-----|
| 1 | Platform goals & layers | Why one object tree; what an application is | Sketch your plant as folders under `root.platform` | [Product](product.md) · [Architecture](architecture.md) |
| 2 | Object model | Objects, variables, events, functions | Name paths for hub + one DEVICE | [Object model](object-model.md) |
| 3 | Drivers & protocols | Connect OT/IT; start/stop; status | Bring demo or Modbus/MQTT device to RUNNING | [Drivers](drivers.md) |
| 4 | Unified model | Logic on SINGLETON/INSTANCE; DEVICE = I/O only | Create hub; put DEVICE under it | [Application principles](application-principles.md) § Logic objects · [Blueprints](blueprints.md) |
| 5 | Bindings & expressions | Rules, order, multi-pass; CEL | One binding writing a display variable | [Bindings](bindings.md)#execution · [Expression language](expression-language.md) |
| 6 | Storage & historian | Last-value vs history; rollups; journal off by default | Chart a variable; leave MQTT journal off | [Variable history](variable-history.md) · [Anti-patterns](anti-patterns.md) § MQTT |
| 7 | Alerts & automation | Threshold → alert → optional workflow | Reuse demo alarm; add one rule | [Automation](automation.md) · [Workflows](workflows.md) |
| 8 | Visualization | Dashboards, widgets, SCADA; thin client | Bind widgets to hub vars only | [Dashboards](dashboards.md) · [Widgets](widgets.md) · [SCADA](scada.md) |
| 9 | Access control | Roles, object/variable permissions | Operator vs admin login | [Security](security.md) · [Operator guide](operator-guide.md) |
| 10 | Ship the solution | Bundle, validate, deploy; optional ui-pack | `ispf pack` / `validate_bundle` green | [Solution developer guide](solution-developer-guide.md) · [Applications](applications.md) · [External AI IDE](external-ide.md) |
| 11 | Operations | Deploy, observe, recover | One observability check on a running stand | [Deployment](deployment.md) · [Observability](observability.md) |
| 12 | Authoring standard | Names, short expressions, dictionaries | Rename hub functions with verb prefixes | [Anti-patterns](anti-patterns.md) § Authoring naming · [Application principles](application-principles.md) |

**Done when:** you can build Path B from scratch **and** explain why each anti-pattern is blocked or warned.

Optional intensives (timed, still text): [OT Automation tutorials](ot-automation-excellence-tutorials.md).  
Reference packs: [Lab training](lab-training.md) · [MES](reference-mes-walkthrough.md) · [mini-TEC](reference-mini-tec-walkthrough.md) · [HVAC](reference-building-hvac-walkthrough.md).

---

## Topic map → ISPF docs

| Topic | ISPF home |
|-------|-----------|
| Getting started / Install | [Getting started](getting-started.md) · [Deployment](deployment.md) |
| Quick start | **Path B** |
| Deep curriculum | **Curriculum** above |
| How to | Topic docs + [expression-language](expression-language.md) |
| How **not** to | [Anti-patterns](anti-patterns.md) |
| Troubleshooting | [Observability](observability.md) · lab stress docs · [product](product.md) |
| Glossary | [Glossary](glossary.md) |
| Certification | [Certification](certification.md) |

---

## Teaching tips (for trainers / partners)

1. **Demo first, theory second** — Path A before object-model lectures.  
2. **Show the anti-pattern** — one bad widget expression vs hub function.  
3. **Always end with ship** — pack/validate, not a snowflake tree.  
4. **One hub rule** — SINGLETON/INSTANCE vs DEVICE until it sticks.  
5. **Use lab packs** — `examples/lab-training`, `mes-reference` beat empty sandboxes.  
6. **Text modules, not slides** — walk Curriculum #1–12 with docs open; assign one practice per module.

Partner hours: [Certification](certification.md) · [Partner program](partner-program.md).

---

## Related

| Doc | Role |
|-----|------|
| [docs/en/readme](readme.md) | Full catalog |
| [Getting started](getting-started.md) | Boot + first login |
| [Anti-patterns](anti-patterns.md) | What not to build + naming standard |
| [Application principles](application-principles.md) | P1–P10 for authors & agents |

*Keep this page short. Add links, not essays — detail lives in topic docs.*
