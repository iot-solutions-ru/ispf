# predictive (ISPF Marketplace)

**Система предиктивной аналитики** — application package `appId=predictive` (UI backend).

| Artifact | Role |
|----------|------|
| `bundle.json` | Schema, seed, hub functions, reports, operatorUi |
| `ui-backend.json` | UI screen → hub function contract |
| Companion | `predictive-ui` → `/apps/predictive/` |

## Install

1. Deploy application listing `predictive` (bundle.json).
2. Install ui-pack `predictive-ui` (or free-install with `uiPackSlug`).
3. Open `/apps/predictive/` or operator `?mode=operator&app=predictive`.
