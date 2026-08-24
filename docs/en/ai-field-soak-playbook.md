# AI generator field soak playbook (BL-180 / Post-S33)

> **Status:** Lab — field evidence runbook. Hub: [doc-status.md](doc-status.md).

Named-site procedure for **live solution generator** (`POST /api/v1/ai/solutions/generate` with `apply:true`). Lab harness is **Done**; this playbook closes the Post-S33 gap: **dated evidence + integrator journal**, not new product surface.

See [ai-agent.md](ai-agent.md) · [field-pilot-playbook § ready-for-field](field-pilot-playbook.md#ready-for-field-gate-policy).

---

## When to use

| Situation | Action |
| --------- | ------ |
| CI / local Gradle smoke | `run-live-generator-oneshot.sh` + `ISPF_LLM_SMOKE=true` |
| Remote demostand (VPS) | `run-vps-field-soak.sh` or `vps-generator-oneshot.ps1` |
| Customer plant (named task) | Same scripts + 3-day journal + archived JSON |

**Do not** claim multi-domain live pass until **each** domain has its own dated JSON file.

---

## Prerequisites

- ISPF ≥ 0.9.100 with AI enabled (`ISPF_AI_*` on server or secrets for Gradle)
- LLM reachable from server (OpenAI-compatible)
- Configurator account (admin) — operator auto-start not required
- Optional: `ISPF_LICENSE_SIGNING_PRIVATE_KEY_PEM` for signed bundles

---

## One-shot (lab or VPS)

### Gradle (lab)

```bash
export ISPF_LLM_SMOKE=true
export ISPF_AI_PROVIDER=openai-compatible
export ISPF_AI_BASE_URL=https://…/v1
export ISPF_AI_MODEL=gpt-4o-mini
export ISPF_AI_API_KEY=…

export AGENT_LIVE_GENERATOR_DOMAIN=hvac   # or mes | scada
bash tools/agent-regression/run-live-generator-oneshot.sh
node tools/agent-regression/validate-generator-evidence.mjs --results build/agent-regression/live-generator-results.json
```

### VPS

```bash
export ISPF_VPS_URL=https://ispf.iot-solutions.ru
export ISPF_VPS_USER=admin
export ISPF_VPS_PASSWORD=…
bash tools/agent-regression/run-vps-field-soak.sh hvac
```

Evidence archives to `docs/evidence/ai-generator/YYYY-MM-DD-<site>-<domain>.json`.

---

## Evidence schema

Example: [`tools/agent-regression/live-generator-results.example.json`](../../tools/agent-regression/live-generator-results.example.json)

| Field | Meaning |
| ----- | ------- |
| `functionalOk` | Tree + dashboard + alert + operator UI HTTP 200 |
| `softBudgetMet` | All domains `elapsedMs` ≤ 900000 (15 min) |
| `domains[].appId` | Operator entry for spot-check |

Soft miss (`softBudgetMet: false`) is recorded honestly — warn in CI, hard-fail only with `--enforce-soft`.

---

## Multi-day field soak (named site)

1. **Day 0** — deploy ISPF, verify AI provider `GET /api/v1/ai/provider` → `available: true`
2. **Days 1–3** — one domain per day (hvac → mes → scada) or repeat same domain for stability
3. **Each run** — archive JSON + row in [ai-generator-soak-journal.template.md](../evidence/ai-generator-soak-journal.template.md)
4. **Operator spot-check** — open `?mode=operator&app=<appId>`, confirm dashboard widgets live
5. **Sign-off** — attach journal + JSON; update scorecard only with REAL evidence

---

## Honesty gates (quality path)

| Claim | Required proof |
| ----- | -------------- |
| BL-180 harness Done | `AiSolutionGeneratorLiveSmokeTest` in CI (no LLM) |
| Single-domain live | One dated `live-generator-results.json`, `functionalOk: true` |
| Soft &lt;15 min | Same file, `softBudgetMet: true` |
| Field Done | Named site + 3-day journal + no P0 incidents |

---

## Related

| Doc | Purpose |
| --- | ------- |
| [agent-regression.md](agent-regression.md) | BL-177/178 suite |
| [mes-field-pilot-playbook.md](mes-field-pilot-playbook.md) | MES plant loop |
| [roadmap § Post-S33](roadmap.md) | Policy: hardening over features |
