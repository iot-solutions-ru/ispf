# README / launch assets

Screenshots for the root [README](../../README.md) and product docs under `docs/en/` / `docs/ru/`. Prefer **English UI** for global OSS (console language → **EN** before capture).

**GitHub gotcha:** bytes must match the extension. JPEG saved as `.png` breaks with `nosniff`. Verify: PNG magic `89504e47`.

## Where shots are used

| File | Scene | Primary docs |
| ---- | ----- | ------------ |
| `ispf-operator-hmi.png` | Operator Mini-CHP + AI | README, product, operator-guide, web-console |
| `ispf-scada-snmp.png` | Mimic · oil pipeline P&ID | README, scada |
| `ispf-mimic-editor.png` | Mimic · Facility Overview (EN) | scada-mimic, scada-symbol-library |
| `ispf-dashboard-builder.png` | Dashboard · charts / graph / sheet | README, dashboards, web-console |
| `ispf-widget-inspector.png` | Dashboard Editor + Widget editor | widgets |
| `ispf-bpmn-workflow.png` | BPMN · MES work-order dispatch | README, workflows, web-console |
| `ispf-object-tree.png` | Explorer · platform tree | getting-started, object-model, web-console |
| `ispf-device-inspector.png` | Device · Flow Meter variables | drivers |
| `ispf-alert-rule.png` | CEL alert inspector | automation |
| `ispf-correlator.png` | New event correlator dialog | automation |
| `ispf-ai-studio.png` | AI Studio Agent | ai-development, ai-agent, web-console |
| `ispf-marketplace.png` | System → Solutions catalog | marketplace, solution-developer-guide |
| `ispf-system-metrics.png` | System → Metrics | observability, web-console |
| `ispf-federation.png` | Federation peers | federation |
| `ispf-reports.png` | Reports catalog | reports |
| `ispf-blueprints.png` | Instance Types | blueprints |
| `ispf-applications.png` | Applications catalog | applications |
| `ispf-operator-apps.png` | Operator Apps tree catalog | — |
| `ispf-operator-apps-launcher.png` | Operator mode app launcher | operator-apps |
| `ispf-security.png` | Security tree branch | security |
| `ispf-spreadsheet-widget.png` | Spreadsheet demo HMI | spreadsheet-widget |
| `ispf-operator-building-hvac.png` | Building HVAC operator | reference-building-hvac-walkthrough |
| `ispf-operator-pump-station.png` | UI Pump Station mimic | lab-training |
| `ispf-explorer-en.png` | Spare explorer frame | — |

Paths from `docs/en/*.md` and `docs/ru/*.md`: `../assets/<file>.png`.

**Capture (2026-07-19):** demostand [ispf.iot-solutions.ru](https://ispf.iot-solutions.ru/) via Playwright, EN UI, Dark, ~1600×900.

**Not illustrated (by design):** API/CEL reference tables, ADRs, CI/runbooks, legal/license, pure architecture prose — screenshots would not help.

## Still open

| File | Capture | Target |
| ---- | ------- | ------ |
| `ispf-hero.gif` | 8–12s product walkthrough | root README hero |

## Social / OG

Crop hero frame to **1200×630** → `ispf-og-1200x630.png` (later).
