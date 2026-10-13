# ADR-0013: Web Console i18n

## Status

**Accepted** (2026-06-23) — English is canonical; other locales are derived with an explicit quality bar.

## Context

Web Console UI strings lived in ~120 TSX files, mostly Russian with mixed English product terms. Operators and admins need a consistent language switch across admin shell, operator HMI, editors, federation, and AI Studio.

## Decision

1. **Library:** `react-i18next` + `i18next` with namespace JSON under `apps/web-console/src/locales/{locale}/`.
2. **Canonical locale:** **English (`en`)** — new UI keys are added to `en/*.json` only; `fallbackLng: 'en'`.
3. **Supported locales:** `en`, `ru`, `de`, `zh` (Simplified Chinese). Ukrainian is excluded.
4. **Persistence:** `localStorage` key `ispf.ui.locale`, URL query `?lang=`, browser language as initial hint, default `en`.
5. **Switcher:** `LocaleSwitcher` in admin topbar, operator topbar, and login card.
6. **Derived locales:** `ru`, `de`, `zh` are generated from `en` via `tools/i18n/generate-locales.py` (batch MT) + glossary `tools/i18n/glossary.json`.
7. **CI:** `npm run i18n:check` compares key sets across locales (canonical = `en`).

### Honesty — Done / Partial / Gap

| Claim | Status | Notes |
|-------|--------|-------|
| `en` as source of truth + key parity CI | **Done** | `i18n:check` |
| Switcher + persistence | **Done** | |
| `ru` / `de` / `zh` present for all `en` keys | **Done** (machine-derived) | Not human-certified |
| **Reviewable RU** for primary market (operator-critical namespaces) | **Partial** | Glossary helps; no signed linguistic acceptance per release |
| Human-reviewed `de` / `zh` | **Gap** | MT only unless a named locale owner appears |

**Rule:** do not claim “native-quality RU/DE/ZH” from MT alone. Scorecard DX may cite `en` + key parity; RU product claims need a review checklist under `docs/evidence/i18n/` (follow-up).

## In scope

Static Web Console UI: labels, buttons, tabs, empty states, validation messages defined in frontend code.

## Out of scope

- Object tree `displayName` and user-authored content
- BPMN diagram text, app manifest / bundle dashboards
- Server `error.message` without error-code mapping
- AI agent reply text (LLM output language follows prompt / `uiLocale`, not this ADR’s JSON files)

## Consequences

- Adding UI text requires `en` key + `t()` + regenerate derived locales + `npm run i18n:check`.
- Bundle size grows with four locale JSON sets (acceptable for admin console).
- SCADA glossary terms should stay consistent with [glossary](../glossary.md).

## Related

- [roadmap](../roadmap.md) § Phase 19
- [web-console](../web-console.md) § Localization
