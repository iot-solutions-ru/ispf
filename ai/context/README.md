# ContextPack (FW-41 / BL-182)

Versioned AI context for bundle generation. Built from docs and `examples/*/bundle.json`.

```bash
python tools/ai-pack/build.py
```

Output:

- `ai/context/generated/ispf-context-pack.json`
- `packages/ispf-ai-agent/src/main/resources/ai/context-pack.json` (classpath for AI agent / server)

Includes **`competitiveGapIndex`** (readiness gaps from `docs/en/competitive-scorecard.md`). At runtime the server adds a **live overlay** (`livePlatform` on `GET /api/v1/ai/tools/context-pack`) — drivers, apps, object counts — refreshed via cache epoch / `POST .../context-pack/refresh`. Search: `search_context topic=gaps`.
