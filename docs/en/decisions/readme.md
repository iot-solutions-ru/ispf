# Architecture Decision Records (ADR)

> **Status:** Stable — Architecture decision records. Hub: [../doc-status.md](../doc-status.md).

English ADRs for ISPF. Russian mirror: [../../ru/decisions/readme.md](../../ru/decisions/readme.md).

## Writing style

Engineering record — not marketing. Prefer Status / Context / Decision / Consequences / Related; use `Risks:` bullets under Consequences. Avoid hype scores and template **Positive** / **Negative** blocks.

Regressions: [`strip-neuro-slang.py`](../../../tools/docs-audit/strip-neuro-slang.py), then [`polish-docs.py`](../../../tools/docs-audit/polish-docs.py).

## Index

### Platform boundary, licensing, AI (0001–0008)

| ID | Title |
|----|-------|
| [0001-app-platform-boundary](0001-app-platform-boundary.md) | App vs platform boundary |
| [0002-dogfooding-gate](0002-dogfooding-gate.md) | Dogfooding gate |
| [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md) | Commercial bundle licensing |
| [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md) | AI artifact generation gates |
| [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) | Tree-first AI agent |
| [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md) | MCP agent tool adapter |
| [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md) | Bundle tree packaging |
| [0008-federation-topology](0008-federation-topology.md) | Federation topology |

### Storage, model, UI (0009–0013)

| ID | Title |
|----|-------|
| [0009-timescaledb-retention](0009-timescaledb-retention.md) | TimescaleDB retention |
| [0010-binding-rules-only](0010-binding-rules-only.md) | Binding rules only |
| [0011-model-type-semantics](0011-model-type-semantics.md) | Model type semantics |
| [0012-visual-groups](0012-visual-groups.md) | Visual groups |
| [0013-web-console-i18n](0013-web-console-i18n.md) | Web console i18n |

### Automation, telemetry, rules (0014–0019)

| ID | Title |
|----|-------|
| [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) | Automation pipeline evolution |
| [0015-event-history-timescale](0015-event-history-timescale.md) | Event history (Timescale) |
| [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) | ClickHouse event journal |
| [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md) | AGPL dual licensing |
| [0017-telemetry-ingest-pipeline](0017-telemetry-ingest-pipeline.md) | Telemetry ingest pipeline |
| [0018-fixture-models-and-cel-applicability](0018-fixture-models-and-cel-applicability.md) | Fixture models and CEL applicability |
| [0019-platform-rule-unification](0019-platform-rule-unification.md) | Platform rule unification |

### Time, Haystack, drivers (0020–0025)

| ID | Title |
|----|-------|
| [0020-time-and-timezones](0020-time-and-timezones.md) | Time and timezones |
| [0021-haystack-semantic-overlay](0021-haystack-semantic-overlay.md) | Haystack semantic overlay |
| [0022-driver-production-matrix](0022-driver-production-matrix.md) | Driver production matrix |
| [0023-haystack-query-runtime](0023-haystack-query-runtime.md) | Haystack query runtime |
| [0024-demand-driven-variable-change-pubsub](0024-demand-driven-variable-change-pubsub.md) | Demand-driven variable pub/sub |
| [0025-telemetry-quality-flags](0025-telemetry-quality-flags.md) | Telemetry quality flags |
| [0025-cassandra-scylla-timeseries-store](0025-cassandra-scylla-timeseries-store.md) | Cassandra/Scylla timeseries store |

### Ingress, cluster, historian (0026–0037)

| ID | Title |
|----|-------|
| [0026-elastic-telemetry-ingress](0026-elastic-telemetry-ingress.md) | Elastic telemetry ingress |
| [0027-event-journal-ingress-fast-path](0027-event-journal-ingress-fast-path.md) | Event journal ingress fast path |
| [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) | Horizontal active-active cluster |
| [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) | Cluster live variable replica sync |
| [0030-cluster-config-structure-replica-sync](0030-cluster-config-structure-replica-sync.md) | Cluster config/structure replica sync |
| [0031-cluster-replica-roles-platform-jobs](0031-cluster-replica-roles-platform-jobs.md) | Replica roles and platform jobs |
| [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md) | Replica profiles and capabilities |
| [0033-prod-idle-demostand-tuning](0033-prod-idle-demostand-tuning.md) | Prod-idle demostand tuning |
| [0034-agent-observability-and-session-knowledge](0034-agent-observability-and-session-knowledge.md) | Agent observability and session knowledge |
| [0035-historian-dual-write](0035-historian-dual-write.md) | Historian dual-write |
| [0036-bundle-ip-balanced-protection](0036-bundle-ip-balanced-protection.md) | Bundle IP-balanced protection |
| [0037-relational-core-portability](0037-relational-core-portability.md) | Relational core portability |

### Analytics, alarms, UI (0038–0042)

| ID | Title |
|----|-------|
| [0038-analytics-platform-architecture](0038-analytics-platform-architecture.md) | Analytics platform architecture |
| [0039-unified-alarm-architecture](0039-unified-alarm-architecture.md) | Alert rule evolution (`alert-rule-v1`) |
| [0040-unified-computations-ui](0040-unified-computations-ui.md) | Unified Computations tab |
| [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) | Multi-tag historian binding rules |
| [0042-analytics-function-catalog](0042-analytics-function-catalog.md) | Analytics function catalog |
| [0043-unified-platform-ref](0043-unified-platform-ref.md) | Unified PlatformRef addressing |
| [0044-object-query](0044-object-query.md) | Object Query (OQ) |
| [0045-java-function-sandbox](0045-java-function-sandbox.md) | Java function sandbox (phase 1) |
| [0046-nats-cluster-package](0046-nats-cluster-package.md) | NATS cluster package + TRANSIENT persist skip |

### Workflow engine, server seams, solution boundary, agent quality (0047–0051)

| ID | Title |
|----|-------|
| [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md) | Custom BPMN subset engine (no Camunda) — **Accepted** |
| [0048-server-modularization-seams](0048-server-modularization-seams.md) | ObjectTreePort → AI module → ObjectManager — **Accepted** |
| [0049-ot-automation-excellence](0049-ot-automation-excellence.md) | OT Automation Excellence (journal, AI-BPMN, analytics AI) — **Accepted** |
| [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) | Manufacturing patterns as solution / marketplace configuration — **Accepted** |
| [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md) | Poka-yoke: constraints over guards — **Accepted** |

## Topic chains (read in order)

| Topic | ADRs | Runbook |
|-------|------|---------|
| Cluster HA | [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) → [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) → [0030-cluster-config-structure-replica-sync](0030-cluster-config-structure-replica-sync.md) → [0031-cluster-replica-roles-platform-jobs](0031-cluster-replica-roles-platform-jobs.md) → [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md) | [cluster](../cluster.md), [deployment](../deployment.md) |
| Historian / analytics | [0035-historian-dual-write](0035-historian-dual-write.md) → [0038-analytics-platform-architecture](0038-analytics-platform-architecture.md) → [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) → [0042-analytics-function-catalog](0042-analytics-function-catalog.md) | [historian-tiers](../historian-tiers.md), [analytics-platform-roadmap](../analytics-platform-roadmap.md) |
| Automation / alarms | [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) → [0039-unified-alarm-architecture](0039-unified-alarm-architecture.md) | [automation](../automation.md) |
| Workflow / BPMN | [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md) → [0049-ot-automation-excellence](0049-ot-automation-excellence.md) | [workflows](../workflows.md) |
| Server modularization | [0048-server-modularization-seams](0048-server-modularization-seams.md) → [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) | — |
| Manufacturing solutions | [0001-app-platform-boundary](0001-app-platform-boundary.md) → [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md) → [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) | [manufacturing-patterns](../manufacturing-patterns.md), [mes](../mes.md) |
| Agent quality (poka-yoke) | [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md) → [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) → [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md) → [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md) | [application-principles](../application-principles.md) P7, [ai-development](../ai-development.md), [agent-regression](../agent-regression.md) |
| Haystack | [0021-haystack-semantic-overlay](0021-haystack-semantic-overlay.md) → [0023-haystack-query-runtime](0023-haystack-query-runtime.md) | [semantic-demo](../semantic-demo.md) |
