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

| File | Role |
| ---- | ---- |
| `ispf-operator-hmi.png` | README hero (until GIF) |
| `ispf-scada-snmp.png` | Admin + SNMP dashboard |
| `ispf-object-tree.png` | Object tree / MQTT device |
| `ispf-bpmn-workflow.png` | BPMN / MES |
| `ispf-alert-rule.png` | Alert rule editor |
| `ispf-dashboard-builder.png` | Dashboard builder (spare) |
| `ispf-marketplace.png` | Solution catalog (spare) |
| `ispf-ai-studio.png` | AI Studio Agent (English) — **re-export at full window resolution** (current file is ~400×197 from chat compress) |
| `ispf-explorer-en.png` | Explorer + device properties (English) — ~1024×503; better if re-exported at native window size |

**Still worth capturing (English):** mimic / P&ID editor → `ispf-mimic-editor.png`; full-res AI Studio (`ispf-ai-studio.png`); optional sharper Explorer overwrite.

## Builder EN pack (docs + marketing)

Use the same English UI rule. Prefer **native window resolution** (not chat-compressed). Point docs at these files when they exist; until then keep existing assets.

| File | Capture | Used by |
| ---- | ------- | ------- |
| `ispf-dashboard-builder.png` | Dashboards → edit layout (84×8 grid visible) | [dashboards.md](../en/dashboards.md), README spare |
| `ispf-mimic-editor.png` | SCADA mimic editor with 2–3 symbols bound | [scada.md](../en/scada.md), [web-console.md](../en/web-console.md) |
| `ispf-widget-inspector.png` | Inspector open on a chart / KPI widget | [widgets.md](../en/widgets.md) |
| `ispf-ai-studio.png` | AI Studio Agent (full window) | README, [ai-development.md](../en/ai-development.md) |
| `ispf-explorer-en.png` | Explorer + device properties | README, [web-console.md](../en/web-console.md) |

**Do not** ship RU-only screenshots in the root README hero row. Localize separately under `docs/assets/ru/` if needed later.

## Social / OG

Crop hero frame to **1200×630** for LinkedIn / Twitter / HN link previews → `ispf-og-1200x630.png` (host on [ispf.ai](https://ispf.ai) or add here later).
