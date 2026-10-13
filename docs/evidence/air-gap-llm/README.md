# Air-gap local LLM evidence (stub)

> **Status:** template only. **No dated site run is archived here.**  
> Playbook: [air-gap deployment § Local LLM demostand](../../en/air-gap-deployment.md#local-llm-demostand-air-gapped-site).

This folder is the place to drop a demostand journal after an on-site model is actually used. Until a subfolder exists, do not treat the air-gap / sovereignty scorecard axis as complete.

## Claim boundary

| Claim | Today |
|-------|--------|
| Agent LLM traffic follows `ISPF_AI_BASE_URL` | **Exists** in code (`ispf.ai.base-url`) |
| Prompts stay on site when that URL is site-local | **Conditional** — operator must set the URL; ISPF does not block a public base URL |
| Air-gap bundle install (`air-gap-pack.sh` / `air-gap-apply.sh`) | **Exists** — platform artifacts, not model weights |
| Model weights shipped inside the air-gap tar | **Gap** — bring the runtime and weights separately |
| Cosign / sigstore image signature | **Gap** — not implemented in this repo. `CHECKSUMS.sha256` is a corruption check |
| Dated demostand soak (NIC without default route, redacted base URL, model id) | **Gap** — add `docs/evidence/air-gap-llm/<site-or-lab>-<date>/README.md` when a run exists |

## When a run exists, record

```text
docs/evidence/air-gap-llm/<id>/
  README.md          # date, site or lab name, provider, model id, base URL host (no secrets)
  notes.md           # whether the host had a default route; what was transferred by sneakernet
```

Do not commit API keys, license private keys, or customer payloads.
