# Documentation tooling

Scripts for bilingual `docs/en` + `docs/ru` layout.

| Script | Purpose |
|--------|---------|
| `migrate_docs.py` | Move flat `docs/*.md` → `en/` + `ru/`, kebab-case, redirect stubs |
| `fix_repo_links.py` | Rewrite `docs/FOO.md` → `docs/en/foo.md` across repository |
| `fix_remaining_links.py` | Fix ADR and legal cross-links inside docs |
| `audit_links.py` | Fail if broken relative `.md` links in en/ru (exit code 1) |

See [../en/documentation-audit.md](../en/documentation-audit.md).
