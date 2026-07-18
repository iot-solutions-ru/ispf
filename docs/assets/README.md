# README / launch assets

Screenshots used by the root [README](../../README.md) and product docs under `docs/en/` / `docs/ru/`. Prefer **English UI** for global OSS (console language switcher → **EN** before capture).

**GitHub gotcha:** the file bytes must match the extension. A JPEG saved as `.png` is served as `Content-Type: image/png` with `X-Content-Type-Options: nosniff` — browsers refuse to paint it (looks like a broken image). Same for fake `.gif`. Verify with: `python -c "from pathlib import Path; b=Path('docs/assets/FILE').read_bytes()[:8]; print(b.hex())"` — PNG starts `89504e47`, JPEG `ffd8ff`, GIF `474946`.

## Hero GIF (highest conversion)

| | |
| --- | --- |
| **File** | `docs/assets/ispf-hero.gif` (or `.webp` if you prefer — then update README `src`) |
| **Length** | 8–12 seconds, loop once or seamless |
| **Size** | target **&lt; 8 MB** (ideally 2–5 MB); 1280×720 or 1600×900 |
| **Tooling** | Windows Game Bar / OBS / CapCut; or [ScreenToGif](https://www.screentogif.com/) |

After the file exists, in root `README.md` change the hero `<img src=...>` from `ispf-operator-hmi.png` to `ispf-hero.gif`.

### Recording script (local profile)

Prereqs (two terminals):

```bash
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
cd apps/web-console && npm run dev
```

Optional — ensure the virtual sensor is running:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "Authorization: Bearer $TOKEN"
```

**UI language:** switch console to **English** before pressing Record.

| Time | Action | What the viewer should see |
| ---- | ------ | -------------------------- |
| 0:00 | Open http://localhost:5173 — role **admin**, tab **Explorer** | Object tree on the left |
| 0:01 | Expand `devices` → select `demo-sensor-01` | Variables: temperature / threshold / alarm |
| 0:03 | Pause 1s on live temperature changing | “real telemetry” beat |
| 0:04 | Expand `alert-rules` → select `temperature-threshold-exceeded` | CEL rule in inspector |
| 0:06 | Double-click `dashboards` → `demo-sensor` | Dashboard / charts |
| 0:08 | Open http://localhost:5173?mode=operator (or role **operator**) | Operator HMI shell |
| 0:10 | Show alarm bar / event if active; otherwise open demo dashboard | Payoff: operator view |
| 0:12 | Stop | — |

**Do not** show: login secrets, Keycloak admin, personal hostnames, unfinished stub screens.

### Optional second clip (AI — only if demostand is clean)

Same length, English UI: Operator Mini-CHP → open **AI assistant** → one short prompt (e.g. “summarize station KPIs”) → answer appears. Save as `ispf-hero-ai.gif` for the website; keep GitHub hero on the simpler alarm path above.

## Static shots (in this folder)

Captured / curated **2026-07-19**. Prefer English UI · Dark · ~1600×900.

| File | Scene | Embedded in |
| ---- | ----- | ----------- |
| `ispf-operator-hmi.png` | Operator Mini-CHP + AI assistant | README, product, operator-guide, web-console |
| `ispf-scada-snmp.png` | Mimic editor · oil pipeline P&ID | README, scada |
| `ispf-dashboard-builder.png` | Dashboard editor · charts / graph / sheet | README, dashboards, widgets, web-console |
| `ispf-bpmn-workflow.png` | BPMN · `mes-work-order-dispatch` | README, workflows, web-console |
| `ispf-object-tree.png` | Explorer · MQTT Meter Bus | getting-started, web-console |
| `ispf-alert-rule.png` | CEL alert inspector | automation |
| `ispf-marketplace.png` | System → Solutions catalog | marketplace |
| `ispf-ai-studio.png` | AI Studio → Agent | ai-development, web-console |
| `ispf-explorer-en.png` | Spare (same family as object-tree) | — |
| `ispf-mimic-editor.png` | Mimic · Facility Overview | scada-mimic |

Paths from `docs/en/*.md` and `docs/ru/*.md`: `../assets/<file>.png`.

**Website [ispf.ai](https://ispf.ai):** marketing images are **embedded base64** in the site build (not this folder). Re-export from these PNGs when updating the website package.

## Still open

| File | Capture | Target |
| ---- | ------- | ------ |
| `ispf-widget-inspector.png` | Inspector open on a chart / KPI widget | [widgets.md](../en/widgets.md) |
| `ispf-hero.gif` | 8–12s product walkthrough | root README hero |

**Do not** ship RU-only screenshots in the root README hero row. Localize separately under `docs/assets/ru/` if needed later.

## Social / OG

Crop hero frame to **1200×630** for LinkedIn / Twitter / HN link previews → `ispf-og-1200x630.png` (host on [ispf.ai](https://ispf.ai) or add here later).
