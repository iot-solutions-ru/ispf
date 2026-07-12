# Documentation audit (2026-07-08)

## Structure

| Path | Purpose |
|------|---------|
| [docs/README.md](../README.md) | Bilingual hub (EN + RU index links) |
| [docs/en/](.) | **English (canonical)** — kebab-case filenames |
| [docs/ru/](../ru/) | **Russian** — proofread editions |

## Naming convention

- **Before:** `SCADA_MIMIC.md`, `ROADMAP_PHASE25.md`
- **After:** `scada-mimic.md`, then `roadmap-phase-25.md` (Phases 25–33 split)
- **2026-07-09:** Phases 25–33 merged back into single [roadmap](roadmap.md); `roadmap-phase-25.md` is a redirect stub only
- **ADRs:** `decisions/0001-app-platform-boundary.md` (unchanged pattern, lowercase)

## Link audit

- Internal `.md` links under `docs/en` and `docs/ru`: **0 broken** (verified by `audit_links.py`)
- Repo-wide references updated to `docs/en/...` via `fix_repo_links.py`
- ADR cross-links: `../roadmap.md`, `../deployment.md`, etc.
- Repo-root legal files: `../../LICENSE-COMMERCIAL.md`, `../../CLA.md`

## Language policy

| Location | Rule |
|----------|------|
| `docs/en/` | Canonical technical English (no Russian body text) |
| `docs/ru/` | Proofread Russian editions with language banner linking to EN |
| RU-primary sources (glossary, operator-guide, product, widgets, …) | Full RU in `ru/`; full EN restored in `en/` |

## Anonymization policy (public docs)

Committed markdown must not contain production hostnames, public IPs, SSH users/ports, or personal GitHub org names. Use placeholders:

| Real (never commit) | Placeholder |
|---------------------|-------------|
| Production ISPF URL | `${ISPF_BASE_URL:-https://ispf.example.invalid}` |
| Lab / edge host | `lab-edge.example.invalid` or `198.51.100.x` (RFC 5737) |
| MQTT broker | `mqtt-broker.example.invalid` |
| SSH user | `lab-operator` / `deploy-user@production-host` |
| Marketplace vendor id | `default-publisher` |

Lab runbooks: ship templates under [`examples/`](../../examples/); operator-specific `deploy/lab-*` stays gitignored.

Re-run bulk pass (all committed text files — configs, scripts, Java, JSON, workflows):

```bash
python deploy/tools/anonymize-repo.py
```

Markdown-only (legacy):

```bash
python deploy/tools/anonymize-docs.py
```

Verify (expect no hits outside `deploy/tools/anonymize-repo.py` and this audit doc):

```bash
rg 'ispf\.iot-solutions|84\.42|iot-solutions\.ru|m5\.wqtt|Michaael/' --glob '!deploy/tools/anonymize-repo.py' --glob '!docs/*/documentation-audit.md'
```

## Maintenance scripts

```bash
python tools/docs-audit/migrate_docs.py      # re-run full migration (destructive)
python tools/docs-audit/fix_repo_links.py    # update repo references to docs/en/
python tools/docs-audit/fix_remaining_links.py
python tools/docs-audit/audit_links.py       # CI gate: exit 1 if broken internal links
python tools/docs-audit/proofread_ru_terms.py  # RU terminology fixes
python tools/docs-audit/fix_en_banners.py    # EN language banners
python tools/docs-audit/strip-neuro-slang.py # remove LLM/marketing phrasing
python tools/docs-audit/polish-docs.py       # normalize links, trim boilerplate
```

## Known follow-ups

- Regenerate context pack: `python tools/ai-pack/build.py`
