# Audit bugfix batch — 0.9.202

**Date:** 2026-08-31  
**Demostand:** https://ispf.iot-solutions.ru @ **0.9.202**  
**Commit:** `a60e230e` — `fix(platform): close audit bugs C1-C2 H2-H8 + idle gates`

## Scope closed

| ID | Fix | Test |
|----|-----|------|
| C1 | Analytics `markRan` on evaluation failure | `AnalyticsEngineSchedulerFailureTest` |
| C2 | Workflow trigger soft-fail | `WorkflowTriggerSoftFailTest` |
| H2 | Corrupt roles JSON → WARN | `PlatformRolesJsonDeserializeTest` |
| H3 | Binding periodic no schedule advance on error | `BindingPeriodicScheduleRegistry*Test` |
| H4 | Alert orphan disable-once | `AlertRuleSoftFailTest` |
| H5 | Agent `saveTurn` @Transactional | `AgentSessionRepositoryTransactionalTest` |
| H6 | Analytics quality per-tag soft-fail | `AnalyticsTagMetadataServiceSoftFailTest` |
| H7 | Process program ObjectNotFound WARN | `ProcessProgramRunnerGateTest` |
| H8 | Post-ready boot order constants | `PostObjectTreeReadyStartupOrderTest` |
| MED | Metrics probe + derived-tag idle gates | `*GateTest` |

## Regression matrix (local Gradle)

```bash
./gradlew :packages:ispf-server:test \
  --tests 'com.ispf.server.platform.analytics.engine.AnalyticsEngineSchedulerFailureTest' \
  --tests 'com.ispf.server.workflow.WorkflowTriggerSoftFailTest' \
  ... (13 suites) \
  :packages:ispf-ai-agent:test \
  --tests 'com.ispf.server.ai.agent.AgentSessionRepositoryTransactionalTest'
```

**Result:** BUILD SUCCESSFUL (13 server + 2 agent suites).

## Demostand post-deploy

| Check | Result |
|-------|--------|
| `/api/v1/info` version | 0.9.202 |
| Boot ERROR count | 0 |
| Self-diagnostics ready | yes |
| MES GA smoke | **8/8** — [`mes-field/2026-08-31-ispf-vps-0.9.202-ga-smoke.json`](../mes-field/2026-08-31-ispf-vps-0.9.202-ga-smoke.json) |

## Deferred (not in 0.9.202)

- H1 bundle deploy transactional rollback
- Workflow trigger index inline prune (rebuild-only today)
- Binding periodic disable-after-N-failures
- Full `:packages:ispf-server:test` suite (known scheduler/Hikari teardown flake)
