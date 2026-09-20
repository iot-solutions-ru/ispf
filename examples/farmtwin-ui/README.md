# farmtwin-ui — hosted SPA pack (ADR-0054)

| | |
|---|---|
| **packId** | `farmtwin-ui` |
| **appId** | `farmtwin` |
| **basePath** | `/apps/farmtwin/` |
| **version** | `1.0.0` |
| **artifact** | `../marketplace-catalog/farmtwin-ui/farmtwin-ui-1.0.0.zip` (single tracked copy) |

Companion of application listing `farmtwin` (`uiPackSlug: farmtwin-ui`).

## Contents

Zip root: `ui-pack.json` + `index.html` + `assets/` (+ static media).

## Install

- Marketplace: install listing `farmtwin` (pulls this pack) or `farmtwin-ui` alone
- Drop-in: unpack zip into `ISPF_UI_PACKS_DIR/farmtwin/`

After install Operator shows **Open app UI** → `/apps/farmtwin/`.
