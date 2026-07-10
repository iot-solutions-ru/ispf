# ISPF Documentation / Документация ISPF

Technical documentation lives in language-specific trees:

| Language | Index |
|----------|-------|
| **English** (canonical) | [en/readme.md](en/readme.md) |
| **Русский** | [ru/readme.md](ru/readme.md) |

## Layout

```
docs/
  en/           # Canonical English (kebab-case filenames)
  ru/           # Russian editions (proofread, mirror structure)
```

ADR: [en/decisions/](en/decisions/) · [ru/decisions/](ru/decisions/)

## Maintenance

- [documentation-audit](en/documentation-audit.md) — structure, naming, link policy
- `python tools/docs-audit/audit_links.py` — verify internal links
- `python tools/docs-audit/audit_en_language.py` — no Cyrillic in `docs/en/`
