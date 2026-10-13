# Architecture Decision Records (ADR)

> **Status:** Stable — Architecture decision records. Hub: [../doc-status.md](../doc-status.md).

English ADRs for ISPF. Russian mirror: [../../ru/decisions/readme.md](../../ru/decisions/readme.md).

## Writing style

Engineering record — not marketing. Prefer Status / Context / Decision / Consequences / Related; use `Risks:` bullets under Consequences. Avoid hype scores and template **Positive** / **Negative** blocks.

**Number uniqueness:** new ADRs must use the next free number (**0077+**; **0067** reserved / gap until FIELD maturity ADR lands on main). Historical collisions **0016** and **0025** keep dual files; cite them as **0016a/b** and **0025a/b** (or by filename). Do not renumber existing files.

Regressions: [`strip-neuro-slang.py`](../../../tools/docs-audit/strip-neuro-slang.py), then [`polish-docs.py`](../../../tools/docs-audit/polish-docs.py).

**Tension resolution:** claim-boundary program — [adr-tension-resolution](../adr-tension-resolution.md) (Closed vs Planned waves for contradictions between ADRs / AGENTS / scorecard).

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
| [0011-model-type-semantics](0011-model-type-semantics.md) | Blueprint kinds MIXIN / INSTANCE / SINGLETON |
| [0012-visual-groups](0012-visual-groups.md) | Visual groups |
| [0013-web-console-i18n](0013-web-console-i18n.md) | Web console i18n |

### Automation, telemetry, rules (0014–0019)

| ID | Title |
|----|-------|
| [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) | Automation pipeline evolution |
| [0015-event-history-timescale](0015-event-history-timescale.md) | Event history (Timescale) |
| [0016-clickhouse-event-journal](0016-clickhouse-event-journal.md) | **0016a** ClickHouse event journal |
| [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md) | **0016b** AGPL dual licensing |
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
| [0025-telemetry-quality-flags](0025-telemetry-quality-flags.md) | **0025a** Telemetry quality flags |
| [0025-cassandra-scylla-timeseries-store](0025-cassandra-scylla-timeseries-store.md) | **0025b** Cassandra/Scylla timeseries (experimental) |

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
| [0038-analytics-platform-architecture](0038-analytics-platform-architecture.md) | Analytics platform architecture — **Accepted** |
| [0039-unified-alarm-architecture](0039-unified-alarm-architecture.md) | Alert rule evolution (`alert-rule-v1`) — **Accepted** |
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
| [0052-ispf-workflow-diagram-editor](0052-ispf-workflow-diagram-editor.md) | ISPF Workflow Diagram Editor (no bpmn.io) — **Accepted** |
| [0053-ispf-template-filler](0053-ispf-template-filler.md) | ISPF Template Filler (POI) replaces YARG for spreadsheet — **Accepted** |
| [0054-hosted-ui-packs](0054-hosted-ui-packs.md) | Hosted UI packs (`/apps/<appId>/`) for marketplace SPAs — **Accepted** |
| [0055-cel-formal-verification-ai-gate](0055-cel-formal-verification-ai-gate.md) | CEL formal verification as AI condition gate — **Accepted** |
| [0056-webauthn-idp-mfa](0056-webauthn-idp-mfa.md) | WebAuthn / IdP OTP MFA follow-up (BL-194) — **Proposed** |
| [0057-ot-trust-wave1-dnp3-poll-only](0057-ot-trust-wave1-dnp3-poll-only.md) | OT Trust Wave 1 — DNP3 stays PRODUCTION poll-only — **Accepted** |
| [0058-mixin-reevaluation-and-detach](0058-mixin-reevaluation-and-detach.md) | MIXIN opt-in reevaluation + ownership-aware detach — **Accepted** |
| [0059-toolchain-version-policy](0059-toolchain-version-policy.md) | Toolchain / dependency version policy (N or N-1, pins with owners, heavy deps in modules) — **Accepted** |
| [0060-solution-authoring-constraints](0060-solution-authoring-constraints.md) | AI-first solution authoring constraints (schema, SINGLETON hubs, W1–W6) — **Accepted** |
| [0061-high-rate-telemetry-history-defaults](0061-high-rate-telemetry-history-defaults.md) | MQTT / journal / previousValue safe defaults (field pain) — **Accepted** |
| [0062-correlator-keyed-dispatch](0062-correlator-keyed-dispatch.md) | Correlator keyed lanes, per-correlator `REQUIRES_NEW`, async actions, event time — **Accepted** |
| [0063-application-git-lifecycle](0063-application-git-lifecycle.md) | APPLICATION Git tab: Connect/Status/Pull/Push→PR shipped — **Accepted** |
| [0064-release-compatibility-lts](0064-release-compatibility-lts.md) | Release, compatibility, LTS; bundle `formatVersion` — **Accepted** |
| [0065-secrets-key-rotation](0065-secrets-key-rotation.md) | Secrets-key envelope (`ispf1.<kid>.…`), previous-key, re-encrypt — **Accepted** |
| [0066-security-model](0066-security-model.md) | Platform security model (auth, RBAC, tenancy, MEMBER ACL, secrets) — **Accepted** |
| [0068-backup-and-dr](0068-backup-and-dr.md) | Backup tiers T1–T4 and RPO/RTO honesty — **Accepted** |
| [0069-cluster-consistency](0069-cluster-consistency.md) | Cluster consistency contract (not linearizable live) — **Accepted** |
| [0070-cluster-rolling-upgrade](0070-cluster-rolling-upgrade.md) | Cluster image N→N+1 Wave 3 gate — **Accepted** |
| [0071-northbound-uns](0071-northbound-uns.md) | Northbound / UNS scope vs lab drivers — **Accepted** |
| [0072-edge-fleet-rollout](0072-edge-fleet-rollout.md) | Edge profile vs S&F vs fleet GitOps lanes — **Accepted** |
| [0073-hosted-ui-pack-trust](0073-hosted-ui-pack-trust.md) | Ui-pack trust / CSP residual (amends 0054) — **Accepted** |
| [0074-historian-quality-persistence](0074-historian-quality-persistence.md) | Historian quality on jdbc + ClickHouse (CH TCK opt-in; amends 0025a) — **Accepted**; Scylla experimental |
| [0075-observability-slo-otel](0075-observability-slo-otel.md) | First-party metrics + OTel taxonomy / SLO honesty — **Accepted** |
| [0076-external-secrets-providers](0076-external-secrets-providers.md) | External KMS/HSM/Vault/GOST non-claims (amends 0065) — **Accepted** |
| [0077-contextpack-hybrid-retrieval](0077-contextpack-hybrid-retrieval.md) | ContextPack hybrid lexical+embedding `search_context` — **Accepted** |

## Topic chains (read in order)

| Topic | ADRs | Runbook |
|-------|------|---------|
| Cluster HA | [0028-horizontal-active-active-cluster](0028-horizontal-active-active-cluster.md) → [0029-cluster-live-variable-replica-sync](0029-cluster-live-variable-replica-sync.md) → [0030-cluster-config-structure-replica-sync](0030-cluster-config-structure-replica-sync.md) → [0031-cluster-replica-roles-platform-jobs](0031-cluster-replica-roles-platform-jobs.md) → [0032-replica-profiles-and-capabilities](0032-replica-profiles-and-capabilities.md) → [0069-cluster-consistency](0069-cluster-consistency.md) → [0070-cluster-rolling-upgrade](0070-cluster-rolling-upgrade.md) | [cluster](../cluster.md), [deployment](../deployment.md), [release-upgrade](../release-upgrade.md) |
| Historian / analytics | [0035-historian-dual-write](0035-historian-dual-write.md) → [0038-analytics-platform-architecture](0038-analytics-platform-architecture.md) → [0041-multi-tag-historian-computations](0041-multi-tag-historian-computations.md) → [0042-analytics-function-catalog](0042-analytics-function-catalog.md) → [0074-historian-quality-persistence](0074-historian-quality-persistence.md) | [historian-tiers](../historian-tiers.md), [analytics-platform-roadmap](../analytics-platform-roadmap.md) |
| Automation / alarms | [0014-automation-pipeline-evolution](0014-automation-pipeline-evolution.md) → [0039-unified-alarm-architecture](0039-unified-alarm-architecture.md) | [automation](../automation.md) |
| Workflow / BPMN | [0047-custom-bpmn-subset-engine](0047-custom-bpmn-subset-engine.md) → [0049-ot-automation-excellence](0049-ot-automation-excellence.md) → [0052-ispf-workflow-diagram-editor](0052-ispf-workflow-diagram-editor.md) | [workflows](../workflows.md) |
| Server modularization | [0048-server-modularization-seams](0048-server-modularization-seams.md) → [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) | — |
| Manufacturing solutions | [0001-app-platform-boundary](0001-app-platform-boundary.md) → [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md) → [0050-manufacturing-patterns-as-solutions](0050-manufacturing-patterns-as-solutions.md) | [manufacturing-patterns](../manufacturing-patterns.md), [mes](../mes.md) |
| Release / compatibility | [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md) → [0037-relational-core-portability](0037-relational-core-portability.md) → [0064-release-compatibility-lts](0064-release-compatibility-lts.md) → [0070-cluster-rolling-upgrade](0070-cluster-rolling-upgrade.md) | [applications](../applications.md), [deployment](../deployment.md), [release-upgrade](../release-upgrade.md) |
| Secrets / credentials | [0049-ot-automation-excellence](0049-ot-automation-excellence.md) → [0065-secrets-key-rotation](0065-secrets-key-rotation.md) → [0076-external-secrets-providers](0076-external-secrets-providers.md) | [security](../security.md), [tutorial-ot-credentials-vault](../tutorial-ot-credentials-vault.md) |
| Security model | [0066-security-model](0066-security-model.md) → [0056-webauthn-idp-mfa](0056-webauthn-idp-mfa.md) → [0065-secrets-key-rotation](0065-secrets-key-rotation.md) | [security](../security.md), [multi-tenant](../multi-tenant.md) |
| Backup / DR | [0068-backup-and-dr](0068-backup-and-dr.md) | [deployment](../deployment.md), [air-gap-deployment](../air-gap-deployment.md) |
| Northbound / UNS | [0071-northbound-uns](0071-northbound-uns.md) → [0022-driver-production-matrix](0022-driver-production-matrix.md) | [drivers](../drivers.md) |
| Edge / fleet | [0072-edge-fleet-rollout](0072-edge-fleet-rollout.md) → [0063-application-git-lifecycle](0063-application-git-lifecycle.md) | [federation](../federation.md), [demostands](../demostands.md) |
| Hosted UI trust | [0054-hosted-ui-packs](0054-hosted-ui-packs.md) → [0073-hosted-ui-pack-trust](0073-hosted-ui-pack-trust.md) | [marketplace](../marketplace.md), [external-ide](../external-ide.md) |
| Observability | [0075-observability-slo-otel](0075-observability-slo-otel.md) → [0034-agent-observability-and-session-knowledge](0034-agent-observability-and-session-knowledge.md) | [observability](../observability.md) |
| Agent quality (poka-yoke) | [0004-ai-artifact-generation-gates](0004-ai-artifact-generation-gates.md) → [0005-tree-first-ai-agent](0005-tree-first-ai-agent.md) → [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md) → [0051-poka-yoke-constraints-over-guards](0051-poka-yoke-constraints-over-guards.md) → [0055-cel-formal-verification-ai-gate](0055-cel-formal-verification-ai-gate.md) | [application-principles](../application-principles.md) P7, [ai-development](../ai-development.md), [agent-regression](../agent-regression.md) |
| Haystack | [0021-haystack-semantic-overlay](0021-haystack-semantic-overlay.md) → [0023-haystack-query-runtime](0023-haystack-query-runtime.md) | [semantic-demo](../semantic-demo.md) |
