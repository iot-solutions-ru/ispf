# Мини-ТЭЦ (эталон) — marketplace bundle

Цифровой двойник АСУ ТП. **Не сидится** на пустой платформе / fixtures — только через бандл.

## Install

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @bundle.json \
  "$ISPF/api/v1/applications/mini-tec/deploy"
```

Regenerate: `node generate_bundle.mjs`

## What the bundle contains

- INSTANCE blueprints: GPU / GRPB / RUMB / DGU / load / station-hub  
- Devices under `root.platform.devices.mini-tec-plant.*`  
- SQL `gpu_sim` + schedule `mini-tec-sim-tick` (`mini_tec_sim_tick`)  
- SQL bindings → live GPU telemetry  
- Overview dashboard + operator UI  

Full SCADA mimics/HMI from the former Java fixture can be added in a later bundle revision (post-deploy `save_mimic_diagram`).

## Simulation

Domain physics is **not** in `virtual` driver profiles. GPU load ramp lives in app SQL updated every 5s.
