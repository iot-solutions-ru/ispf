# README / launch assets

Screenshots used by the root [README](../../README.md). Prefer **English UI** for global OSS (console language switcher → **EN** before capture).

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

## Static shots (already in this folder)

Captured **2026-07-19** from demostand [ispf.iot-solutions.ru](https://ispf.iot-solutions.ru/) · English UI · Dark · 1600×900 · v0.9.177.

| File | Role | Scene |
| ---- | ---- | ----- |
| `ispf-operator-hmi.png` | README hero (until GIF) | Operator · UI Pump Station + facility mimic |
| `ispf-scada-snmp.png` | README row 1 | Admin View · `ui-pump-station-overview` (filename kept for links) |
| `ispf-object-tree.png` | README row 1 | Explorer · Pump 1 · Variables |
| `ispf-bpmn-workflow.png` | README row 2 | BPMN Editor · `mes-work-order-dispatch` |
| `ispf-alert-rule.png` | README row 2 | Alert · `ui-pump-high-pressure` CEL |
| `ispf-dashboard-builder.png` | Spare / docs | Dashboard Editor · widget palette |
| `ispf-marketplace.png` | Spare / docs | System → Solutions catalog |
| `ispf-ai-studio.png` | Spare / docs | AI Studio → Agent |
| `ispf-explorer-en.png` | Spare / docs | Same as object-tree (EN Explorer) |
| `ispf-mimic-editor.png` | Spare / docs | SCADA Mimic editor · Facility Overview |

**Website [ispf.ai](https://ispf.ai):** marketing images are **embedded base64** in the site build (not this folder). They still show older RU UI (SNMP Host Monitoring, Mini-TEC, MQTT Meter Bus, Demo Alarm Handler). Re-export from these PNGs when updating the website package.

## Builder EN pack (docs + marketing)

Use the same English UI rule. Prefer **native window resolution** (not chat-compressed). Point docs at these files when they exist; until then keep existing assets.

| File | Capture | Used by |
| ---- | ------- | ------- |
| `ispf-dashboard-builder.png` | Dashboards → Editor (widget palette + facility mimic) | [dashboards.md](../en/dashboards.md), README spare |
| `ispf-mimic-editor.png` | SCADA mimic editor · Facility Overview + P&ID pack | [scada.md](../en/scada.md), [web-console.md](../en/web-console.md) |
| `ispf-widget-inspector.png` | Inspector open on a chart / KPI widget | [widgets.md](../en/widgets.md) — still open |
| `ispf-ai-studio.png` | AI Studio Agent (full window) | README spare, [ai-development.md](../en/ai-development.md) |
| `ispf-explorer-en.png` | Explorer + device variables | README spare, [web-console.md](../en/web-console.md) |

**Do not** ship RU-only screenshots in the root README hero row. Localize separately under `docs/assets/ru/` if needed later.

## Social / OG

Crop hero frame to **1200×630** for LinkedIn / Twitter / HN link previews → `ispf-og-1200x630.png` (host on [ispf.ai](https://ispf.ai) or add here later).
