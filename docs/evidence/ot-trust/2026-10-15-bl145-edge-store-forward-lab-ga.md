# BL-145 Agent edge store-forward — lab GA gate (2026-10-15)

**Status:** Lab Done. Field 30-day soak remains Gap.

## Acceptance mapping

| Requirement | Evidence |
|-------------|----------|
| Store-forward buffer | `AgentStoreForwardService` + `FederationOutboundEventBufferRegistry` |
| Offline disk persist | `AgentStoreForwardServiceTest#bufferSurvivesRestartViaDiskPersistence` |
| Stats API | `GET /api/v1/agent/store-forward/stats` — `AgentStoreForwardApiTest` |
| Disconnect → buffer → reconnect drain | `FederationStoreForwardIntegrationTest` |
| Operator / lab journal script | `deploy/tools/agent-store-forward-soak.sh` (was gitignored; now shipped) |

## Commands

```bash
./gradlew :packages:ispf-server:test --tests com.ispf.server.agent.AgentStoreForwardServiceTest --tests com.ispf.server.agent.AgentStoreForwardApiTest
./gradlew :packages:ispf-server:test --tests com.ispf.server.federation.FederationStoreForwardIntegrationTest

# Against a running edge/hub (smoke):
ISPF_SOAK_WALL_MINUTES=0 ISPF_SOAK_BASE_URL=http://127.0.0.1:8080 \
  bash deploy/tools/agent-store-forward-soak.sh
```

## Non-claims

- Do **not** claim field edge GA / 30 real days without `ISPF_SOAK_WALL_MINUTES=43200` journal on a named site.
- Soak script polls stats; tunnel disconnect/replay is proven in IT, not by synthesizing disconnect in the shell.
