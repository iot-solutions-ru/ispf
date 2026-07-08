# Documentation audit (2026-07-08)

## Structure

| Path | Purpose |
|------|---------|
| [docs/README.md](../README.md) | Bilingual hub (EN + RU index links) |
| [docs/en/](.) | **English (canonical)** — kebab-case filenames |
| [docs/ru/](../ru/) | **Russian** — proofread editions |

## Naming convention

- **Before:** `SCADA_MIMIC.md`, `ROADMAP_PHASE25.md`
- **After:** `scada-mimic.md`, `roadmap-phase-25.md`
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

## Maintenance scripts

```bash
python tools/docs-audit/migrate_docs.py      # re-run full migration (destructive)
python tools/docs-audit/fix_repo_links.py    # update repo references to docs/en/
python tools/docs-audit/fix_remaining_links.py
python tools/docs-audit/audit_links.py       # CI gate: exit 1 if broken internal links
python tools/docs-audit/proofread_ru_terms.py  # RU terminology fixes
python tools/docs-audit/fix_en_banners.py    # EN language banners
```

## Known follow-ups

- Regenerate context pack: `python tools/ai-pack/build.py`
