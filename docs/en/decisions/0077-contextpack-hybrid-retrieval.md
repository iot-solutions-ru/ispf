# ADR-0077: ContextPack hybrid retrieval

## Status

Accepted (2026-10-13)

## Context

Studio and MCP agents were steered toward platform docs via large prompt slices and repeated
`get_automation_schema` / `search_context` calls. Lexical `search_context` already existed
([ai-development](../ai-development.md) ContextPack), but ranking quality and “stuff the prompt”
habits still flooded turns. ADR-0034 deferred an external vector DB for **session** uploads;
platform docs need a different path: retrieve short hits without a second LLM inside MCP.

## Decision

1. **Keep one tool** — `search_context` (MCP mapping unchanged, ADR-0006). Optional `mode=hybrid|lexical|vector`.
2. **Build-time embeddings** in ContextPack `docChunks[]`:
   - Default offline model `ispf-hash-v1` (feature hash, 384-d) via `tools/ai-pack/build.py` — air-gap safe.
   - Optional remote OpenAI-compatible `/v1/embeddings` when `ISPF_CONTEXT_PACK_EMBED_URL` is set.
   - Pack metadata: `embeddingModel`, `embeddingDims`, `embeddingCoverage`.
3. **Runtime hybrid ranking** in `ContextPackSearchService`: blend lexical score with cosine similarity;
   query vectors from the same hash model, or from `ispf.ai.base-url` `/embeddings` when the pack
   used a remote embedder. If embeddings are missing/unavailable → lexical only (no regression).
4. **Hits stay short** — title + snippet + `docRef`; never return full markdown bodies as the default.
5. **Prompt policy** — Studio/Ask prompts stay playbook **indexes**; after search hits, continue with
   tree tools or finish (loop guard continuation updated).

## Consequences

- Context pack JSON grows (embeddings per chunk); hash vectors are small enough to ship in-repo.
- Session document search (`search_session_context`) remains keyword until a separate ADR.
- No Chroma / AnythingLLM / in-MCP chat model for retrieval.

## Alternatives considered

| Alternative | Rejected because |
|-------------|------------------|
| Separate `vector_search` MCP tool | Duplicates `search_context`; agents already know that name |
| External vector DB at runtime | Ops burden; air-gap demostand; ADR-0034 spirit |
| Small LLM “search model” in MCP | Wrong tool for ranking; duplicates provider stack |
| Inline all docs in system prompt | Context bloat; fights tree-first mutate flow |

## Related

- [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md) — curated ContextPack
- [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md) — MCP `search_context`
- [0034-agent-observability-and-session-knowledge](0034-agent-observability-and-session-knowledge.md) — session RAG stays keyword
- [ai-development](../ai-development.md) — ContextPack build
