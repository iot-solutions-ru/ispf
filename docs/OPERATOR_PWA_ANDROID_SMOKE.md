# Operator PWA — Android Chrome smoke checklist (BL-90)

Manual acceptance for installed operator mode on Android. Automated coverage: Playwright mobile viewport + offline cache (`e2e/live-operator.spec.ts`).

## Prerequisites

- HTTPS origin (prod `https://ispf.iot-solutions.ru` or local tunnel with valid cert)
- Demo operator app available (`/?mode=operator&app=demo`)
- Android device with Chrome 120+

## Install

1. Open the site in Chrome → menu → **Install app** / **Add to Home screen**
2. Confirm app name **ISPF** and icon appear on launcher
3. Launch from home screen — URL bar should be minimal (standalone display)

## Operator smoke (5 min)

| Step | Expected |
|------|----------|
| App opens to operator start URL (`start_url` contains `mode=operator`) | Shell loads, no admin tree |
| Sign in (if required) | Session persists after app restart |
| Navigate manifest tabs / dashboards | Top bar + nav visible, safe-area not clipped (notch) |
| Rotate portrait ↔ landscape | Layout reflows; no horizontal overflow |
| Enable airplane mode | Offline banner + cached shell (BL-91) |
| Disable airplane mode | Banner clears; data refreshes without full reload |

## Regression notes

- Service worker caches shell only; API uses network (offline uses localStorage cache)
- If install prompt missing: check `manifest.webmanifest`, `display: standalone`, icons 192/512
- Record device model, Chrome version, and screenshot of offline banner for release notes

## Sign-off

- [ ] Tested on physical Android device
- [ ] Standalone launch OK
- [ ] Offline stale banner OK
- [ ] Tester / date: _______________
