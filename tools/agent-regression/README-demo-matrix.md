# Platform demo matrix (dual execution)

JSON scenarios under `scenarios/platform-*.json` (`domain=platform`, optional `tags[]`) plus two runners:

| Runner | LLM? | Role |
|--------|------|------|
| `run-platform-object-smoke.py` | No | Hard gate — wipe/create/verify object types via REST |
| `run-demo-scenarios.py` | Yes | Soft report — AI Studio execute session per scenario |

## Validate

```bash
node tools/agent-regression/validate-scenarios.mjs
```

## Deterministic smoke

```bash
python tools/agent-regression/run-platform-object-smoke.py \
  --base-url https://it-demo.iot-solutions.ru
# → build/agent-regression/platform-object-smoke.json
```

## LLM subset / full

```bash
# smoke subset
python tools/agent-regression/run-demo-scenarios.py --max 5

# filter
python tools/agent-regression/run-demo-scenarios.py --ids platform-virtual-devices,platform-mimic-pack
python tools/agent-regression/run-demo-scenarios.py --tags capability:historian --max 0

# all platform-* (slow)
python tools/agent-regression/run-demo-scenarios.py --max 0
# → build/agent-regression/demo-scenarios-results.json
```

Hub logic must live under `root.platform.singleton-blueprints.*` (never `ObjectType.DEVICE`). Mimic `symbolId`s must come from `list_mimic_symbols` / `pack.ispf-pid.*`.
