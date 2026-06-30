---
name: scada-mimic
description: >-
  Build and edit ISPF SCADA mimic diagrams (MIMIC objects, scada-mimic widget,
  diagramJson v2, bindings). Use when creating P&ID/HMI mimics, tank-farm or
  pipeline-scada demos, re-exporting bootstrap JSON, or anonymizing demo labels.
---

# SCADA mimic (ISPF)

## Quick refs

| Topic | Location |
|-------|----------|
| Overview | `docs/SCADA.md` |
| diagramJson v2, REST | `docs/SCADA_MIMIC.md` |
| Agent routing | `docs/AGENT_KNOWLEDGE.md` § Bootstrap SCADA demos |
| Server playbook | `AgentPlaybooks.scadaMimicGuide()` |

## Bootstrap demos (fixtures)

| Demo | appId | Mimic | Dashboard |
|------|-------|-------|-----------|
| Tank farm | `tank-farm-demo` | `root.platform.mimics.tank-farm-demo` | `root.platform.dashboards.tank-farm-hmi` |
| Pipeline SCADA | `pipeline-scada` | `root.platform.mimics.pipeline-rp` | `root.platform.dashboards.pipeline-scada-hmi` |

Requires `ispf.bootstrap.fixtures-enabled=true` on server start.

## Re-export after TS edits

```bash
cd apps/web-console && npx tsx src/scada/templates/exportTankFarmMimic.ts
cd apps/web-console && npx tsx src/scada/templates/pipeline-scada/exportPipelineScadaMimics.ts
```

TypeScript sources: `apps/web-console/src/scada/templates/`. P&ID palette symbols: regenerate with `cd tools/symbol-pack-isa && npm run build` (original ISA/ISO artwork — do not use deprecated `tools/symbol-import`).

## Anonymization

Demo diagrams must **not** contain real company names, personal data, or geo-specific labels. Use generic IDs (Резервуар №11, ST-1, Коллектор). Never add `transneft-*` paths or filenames.

## Typical workflow

1. Create devices/models with telemetry variables.
2. `create_object` MIMIC under `root.platform.mimics` (template `mimic-v1`).
3. Edit diagram in Scada Mimic Editor (Dashboard Builder → scada-mimic → Open editor, or Explorer).
4. Bind symbol keys to `objectPath` + `variableName` (check with `list_variables`).
5. Dashboard widget `scada-mimic` with `mimicPath` (prefer over inline `diagramJson`).
6. Operator: `?mode=operator&app=<appId>&dashboard=<path>`.

## Programmatic layout (large demos)

Use `buildTankFarmMimic.ts` pattern: builder functions → `diagramJson` v2 → export script writes `packages/ispf-server/src/main/resources/bootstrap/*.json`.

## Collision note

When both `TankFarmPlatformBootstrap` and `PipelineScadaPlatformBootstrap` run, `root.platform.mimics.tank-farm-demo` may be overwritten by the RP diagram alias. For full СДКУ use `pipeline-rp`; for standalone tank-farm app use `tank-farm-demo` devices folder.
