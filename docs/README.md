# ISPF Documentation / Документация ISPF

Technical documentation lives in language-specific trees:

| Language | Index |
|----------|-------|
| **English** (canonical) | [en/readme.md](en/readme.md) — **start here** (role paths + catalog) |
| **Русский** | [ru/readme.md](ru/readme.md) |

**New to ISPF?** [Try ISPF in ≈15 minutes](en/getting-started.md#try-ispf-15-minutes) · [Попробовать за ≈15 минут](ru/getting-started.md#попробовать-ispf-15-минут)

## Layout

```
docs/
  en/           # Canonical English (kebab-case filenames)
  ru/           # Russian editions (proofread, mirror structure)
  assets/       # Screenshots for the root README / launch
```

ADR: [en/decisions/](en/decisions/) · [ru/decisions/](ru/decisions/)

**License:** platform is GNU AGPL v3 — see [en/license.md](en/license.md).

## Maintenance

- [documentation-audit](en/documentation-audit.md) — structure, naming, link policy, **anonymization**
- `python deploy/tools/anonymize-repo.py` — replace real hosts/users in all committed text files
- `python deploy/tools/anonymize-docs.py` — markdown-only (wrapper)
- `python tools/docs-audit/audit_links.py` — verify internal links
- `python tools/docs-audit/audit_en_language.py` — no Cyrillic in `docs/en/`
