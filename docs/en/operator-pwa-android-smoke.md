# Operator PWA — Android Chrome smoke checklist (BL-90)

Manual acceptance for installed operator mode on Android. **Automated coverage (CI):** Playwright Pixel 5 + vite preview (`e2e/pwa-android.spec.ts`) — manifest, SW, rotation, offline banner with SW reload. Mobile viewport + offline cache in `e2e/live-operator.spec.ts`.

## Prerequisites

- HTTPS origin (prod `${ISPF_BASE_URL:-https://ispf.example.invalid}` or local tunnel with valid cert)
- Demo operator app available (`/?mode=operator&app=demo`)
- Android device with Chrome 120+ (optional — for release sign-off only)

## Install

1. Open the site in Chrome → menu → **Install app** / **Add to Home screen**
2. Confirm app name **ISPF** and icon appear on launcher
3. Launch from home screen — URL bar should be minimal (standalone display)

## Operator smoke (5 min)

| Step | Expected | CI automated |
|------|----------|--------------|
| App opens to operator start URL (`start_url` contains `mode=operator`) | Shell loads, no admin tree | ✓ manifest + operator shell e2e |
| Sign in (if required) | Session persists after app restart | mocked session in e2e |
| Navigate manifest tabs / dashboards | Top bar + nav visible, safe-area not clipped (notch) | ✓ Pixel 5 safe-area + nav |
| Rotate portrait ↔ landscape | Layout reflows; no horizontal overflow | ✓ viewport rotation test |
| Enable airplane mode | Offline banner + cached shell (BL-91) | ✓ SW offline reload (preview) |
| Disable airplane mode | Banner clears; data refreshes without full reload | ✓ reconnect test |

## Regression notes

- Service worker caches shell only; API uses network (offline uses localStorage cache)
- If install prompt missing: check `manifest.webmanifest`, `display: standalone`, icons 192/512
- Record device model, Chrome version, and screenshot of offline banner for release notes

## Sign-off

- [x] Automated: Pixel 5 + preview build (`npm run test:e2e:preview`) — Sprint S16
- [ ] Optional: tested on physical Android device (release notes)
- [x] Standalone manifest + SW registration OK
- [x] Offline stale banner OK (preview + dev navigator.onLine)
- Tester / date: CI / 2026-07-03
