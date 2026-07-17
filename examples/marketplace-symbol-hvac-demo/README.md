# HVAC Equipment Symbol Pack (BL-185)

Demo marketplace symbol pack for local install:

```bash
# API
curl -X POST /api/v1/marketplace/symbols/hvac-equipment-v1/install
curl /api/v1/scada/symbol-packs/hvac-equipment-v1
```

Install writes under `ISPF_SYMBOL_PACKS_DIR` (default `./data/symbol-packs/hvac-equipment-v1`).
The mimic editor palette loads installed packs from `GET /api/v1/scada/symbol-packs`.
