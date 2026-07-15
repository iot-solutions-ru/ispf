# Резервуарный парк (marketplace)

Self-contained ISPF application. **Not** seeded on an empty platform.

## Install

```bash
# after login — deploy bundle
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  --data-binary @bundle.json \
  "$ISPF/api/v1/applications/tank-farm-demo/deploy"
```

Or agent: `import_package` / `validate_bundle` with this manifest.

Regenerate: `node generate_bundle.mjs`

## Simulation

SQL tables `tank_sim` / `hub_sim` + schedule `tank-farm-sim-tick` → `tank_farm_tick`.  
Tree variables refreshed via SQL bindings (no virtual driver profiles).
