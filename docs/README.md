# ISPF Documentation / Документация ISPF

ISPF (IoT Solutions Platform Framework) is a self-hosted platform for industrial telemetry, SCADA screens, alarm handling, time-series history, and shop-floor applications.

Choose your language:

| Language / Язык | Start here / С чего начать |
|-----------------|----------------------------|
| **Русский** | **[Оглавление документации](ru/readme.md)** · [Как устроен ISPF и с чего начать](ru/learn.md) · [Быстрый старт за 15 минут](ru/getting-started.md#попробовать-ispf-15-минут) |
| **English** (canonical) | **[Documentation index](en/readme.md)** · [How ISPF works & learning guide](en/learn.md) · [Try ISPF in 15 minutes](en/getting-started.md#try-ispf-15-minutes) |

GitHub Wiki (short reader guide): [github.com/iot-solutions-ru/ispf/wiki](https://github.com/iot-solutions-ru/ispf/wiki)

## Directory layout

```text
docs/
  en/           # Canonical English documentation
  ru/           # Russian documentation (same file names and structure)
  assets/       # Product UI screenshots and diagrams
  decisions/    # Architecture Decision Records (under en/decisions/ and ru/decisions/)
  evidence/     # Lab and test verification artifacts
```

**License:** the core platform is licensed under GNU AGPL v3 (with an optional commercial license) — see [en/license.md](en/license.md) / [ru/license.md](ru/license.md).

## Documentation checks (for contributors)

- [doc-status.md](en/doc-status.md) — meaning of `Stable`, `Beta`, `Draft`, `Charter`, `Lab`, and `Internal` tags
- [documentation-audit.md](en/documentation-audit.md) — file naming, link rules, and host anonymization
- `python tools/docs-audit/audit_links.py` — check all relative Markdown and repository links
- `python tools/docs-audit/audit_en_language.py` — verify `docs/en/` contains no accidental Cyrillic text
