# Customer demostand playbook (30–60 min)

> **Status:** Sales / pre-sales agenda. Hub: [doc-status.md](doc-status.md).  
> Russian: [ru/customer-demo-playbook.md](../ru/customer-demo-playbook.md).

**Audience:** show ISPF to a customer on a demostand — not engineer Path A/B training.  
**Host:** demo-idle VPS or local stack — [demostands](demostands.md) § Demo / idle · [vps-demostand](vps-demostand.md).  
**Not this page:** FIELD plant certification, Cosign image signing, KMS — only if the tender asks.

Pick **one** track. Do not mix both in the same hour unless the room asks for a second pass.

| Track | Buyer | Time | One-sentence story |
|-------|-------|------|--------------------|
| **A — MES live** | Ops / MES / IT+OT | ~30–40 min | From dispatch to alarm on one production walkthrough |
| **B — OT + HMI + air-gap story** | OT / SCADA / ИБ-curious | ~40–60 min | Live mimic + “we can run without internet; apps are RSA-signed” |

---

## Before the meeting (15 min prep)

- [ ] Demostand up; `GET /api/v1/info` OK; login works (admin + operator if you use operator mode)
- [ ] Confirm idle profile: `GET /api/v1/platform/effective-config` → `profile.deployProfile` = demo-idle (or equivalent)
- [ ] Track A: `mes-platform-production` (or marketplace MES) installed and operator UI opens
- [ ] Track B: one RUNNING virtual/demo device + one mimic/dashboard; optional air-gap tar **story** (need not re-apply live)
- [ ] Browser tabs ready: Admin Explorer, Operator UI, one dashboard/mimic — no random open PRs on screen
- [ ] Honesty card ready (say aloud if asked): MES ERP stub is **simulated**; driver FIELD count is **0** until a named plant pack; Cosign for images is **not** claimed

Deep ops: [reference-mes-platform](reference-mes-platform.md), [mes-field-pilot-playbook](mes-field-pilot-playbook.md), [air-gap-deployment](air-gap-deployment.md), [air-gap-signing evidence](../evidence/air-gap-signing/README.md).

---

## Track A — MES live (~30–40 min)

Talk track: *“One application bundle: tree, functions, operator UI — not a separate MES product silo.”*

| Min | Show | Do / say |
|-----|------|----------|
| 0–5 | Login → Operator | Open operator mode; name the site (“this is the demostand, not your plant yet”) |
| 5–15 | MES walkthrough | Run the short loop from [reference-mes-walkthrough](reference-mes-walkthrough.md) (dispatch → tank/rack → visible state) |
| 15–25 | Alarm / work queue | Trigger or point at an alarm path; show operator action if available |
| 25–30 | Ship story | “We ship as a signed/validated application package” — point at Applications / marketplace install, not Java jars |
| 30–40 | Q&A buffer | ERP: stub/`simulated` until live 1C/SAP ([scorecard](competitive-scorecard.md) MES). Pilot: [mes-field-pilot-playbook](mes-field-pilot-playbook.md) |

**Done when:** customer can retell “bundle → operator UI → live loop” without Admin jargon.

---

## Track B — OT + HMI + air-gap (~40–60 min)

Talk track: *“Sensors and HMI are live; logic stays on SINGLETON/INSTANCE hubs; offline install is a first-class path.”*

| Min | Show | Do / say |
|-----|------|----------|
| 0–5 | Tree | DEVICE under a hub (never claim DEVICE holds orchestration) — [application-principles](application-principles.md) § Logic objects |
| 5–20 | Live I/O + HMI | RUNNING driver → variable → dashboard or SCADA mimic; optional FPS honesty if asked ([hmi-quality-gates](hmi-quality-gates.md), evidence under `docs/evidence/hmi-fps/`) |
| 20–35 | Automation light | One alert or binding — keep widgets thin ([anti-patterns](anti-patterns.md)) |
| 35–50 | Air-gap story | Walk [air-gap-deployment](air-gap-deployment.md) checklist verbally: pack → sneakernet → apply; **RSA require-signed default** on air-gap compose; Cosign for Docker images = Gap unless their ИБ requires it |
| 50–60 | Q&A | Local LLM optional ([air-gap-deployment § Local LLM](air-gap-deployment.md#local-llm-demostand-air-gapped-site)); FIELD only with named plant |

**Done when:** customer hears a clear split: live OT/HMI demo vs offline install story vs “we will certify FIELD on your site.”

---

## Optional 5-minute agent teaser

Only if the buyer asked about AI. One short ask in Admin Copilot / Studio — e.g. explain a dashboard or list devices. If the model returns a parse error, **skip** — do not debug LLM in front of the customer. Engineer path: [ai-development](ai-development.md).

---

## After the meeting

- [ ] Send links only: this playbook + the track’s walkthrough + [learn](learn.md) Path A if they want hands-on
- [ ] If they want a pilot: schedule [field-pilot-playbook](field-pilot-playbook.md) / MES field — do not promise FIELD=1 from the demostand
- [ ] Capture asks that need a tender (Cosign, KMS/GOST, live ERP) — those are not demostand gaps

---

## Related

- [demostands](demostands.md) · [vps-demostand](vps-demostand.md) · [learn](learn.md)
- [reference-mes-walkthrough](reference-mes-walkthrough.md) · [reference-building-hvac-walkthrough](reference-building-hvac-walkthrough.md) · [reference-mini-tec-walkthrough](reference-mini-tec-walkthrough.md)
- [air-gap-deployment](air-gap-deployment.md) · [competitive-scorecard](competitive-scorecard.md)
