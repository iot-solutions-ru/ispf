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

- [documentation-audit](en/documentation-audit.md) — structure, naming, link policy, **anonymization**
- `python deploy/tools/anonymize-repo.py` — replace real hosts/users in all committed text files
- `python deploy/tools/anonymize-docs.py` — markdown-only (wrapper)
- `python tools/docs-audit/audit_links.py` — verify internal links
- `python tools/docs-audit/audit_en_language.py` — no Cyrillic in `docs/en/`
