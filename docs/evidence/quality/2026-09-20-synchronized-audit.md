# `synchronized` audit + ObjectManager sync benchmark (2026-09-20)

> Closes the F-08 residue from the 2026-09 code analysis: the ObjectManager lock split (#232) was
> verified only by concurrency tests, and the other `synchronized` sites in `ispf-server` had not been
> reviewed. **Not** a load test of a running cluster — the benchmark is in-process with a simulated DB.

## Benchmark — `ObjectManagerPathSyncBenchmarkTest`

Workload: 8 follower threads, 20 `reloadPathFromDatabase(path)` each on 8 **distinct** paths, the mocked
`ObjectTreeLoadSyncService` parks 2 ms per sync (stand-in for the JDBC round-trip that dominates a real
sync). Same `ObjectManager` instance, two lock shapes, best of 3 after warm-up.

| Variant | Wall time | Notes |
|---------|-----------|-------|
| instance monitor — `synchronized (objectManager)` (pre-F-08 `synchronized` methods) | 402.9 ms | ≈ serial sum 8 × 20 × 2 ms = 320 ms + scheduling |
| per-path — `ReentrantReadWriteLock` read side + per-path monitor (current) | 49.0 ms | ≈ 20 × 2 ms per thread, threads overlap |
| **speedup** | **8.2×** | ideal 8× for 8 paths; the test asserts ≥ 2× so shared CI runners never flap |

Measured on Windows / JDK 25 / 8 threads. Reproduce:
`./gradlew :packages:ispf-server:test --tests "*ObjectManagerPathSyncBenchmarkTest" -i` — the line
`F-08 ObjectManager path sync: …` carries the numbers. Same-path syncs still serialise and a whole-tree
`reloadFromDatabase()` still excludes them (`ObjectManagerTreeSyncLockTest`).

What this means for a cluster: follower catch-up after a burst of structural events on N devices is
bounded by the slowest single device sync, not by the sum — the effect grows with device count and DB
latency.

## Audit — remaining `synchronized` in `ispf-server/src/main` (76 sites, 33 classes)

Method: every site classified by (a) what it protects, (b) whether unrelated work is serialised behind it,
(c) how hot the call path is. Verdict **OK** = mutex is the right tool and holds for µs on in-memory state;
**watch** = correct today, has a cheap upgrade if the call rate grows.

| Area | Sites | Protects | Hot path? | Verdict |
|------|-------|----------|-----------|---------|
| `object.ObjectManager` `lockForNode` / `lockForVariable` (+ `ObjectVariableService`, `ObjectBindingStatePort`, `BindingRulesService`) | 3 + 1 + 6 + 1 | per-path / per-variable monitors | yes | **OK** — already keyed by path; independent paths never contend (this is the F-08 shape) |
| `cluster.NatsReplicaEventProcessor` `ingressGate` | 3 | coalescing maps for live / structural lanes | yes (every replica event) | **OK** — a coalescing gate is a single mutex by design; body is a map put + size check, no I/O |
| `event.RecentEventCache` | 4 | ring buffer (default 2000) | yes — `append` per event; `findLatest` per correlator match | **watch** — `append` is O(1); `findLatest` / `query` scan ≤ 2000 entries under the same lock. Upgrade to `StampedLock` optimistic reads only if the event rate goes past ~10k/s |
| `federation.FederationOutboundEventBuffer` | 6 | bounded deque + byte budget | per federated change | **OK** — O(1) enqueue, drain is a copy; bounded queue needs a mutex |
| `federation.FederationTunnelAgentService` | 2 | state + `connectLocks` per agent | connect only | **OK** — per-agent keyed lock |
| `agent.AgentStoreForwardService` `persistLock` | 1 | disk flush of pending buffer | timer | **OK** — serialises file writes only |
| `automation.AutomationRuleIndex`, `workflow.WorkflowEventTriggerIndex`, `object.BindingDependencyIndex`, `application.binding.ApplicationSqlBindingEventIndex`, `plugin.blueprint.WatchMixinIndex` | 8 + 3 + 3 + 1 + 1 | index rebuild / mutate | rebuild on config change; reads go through a `volatile` immutable snapshot | **OK** — writers only, copy-on-write for readers |
| `platform.analytics.pack.AnalyticsExtensionRegistry` | 6 | registered pack functions | `evaluatorArray()` on engine (re)build, `containsHelper` on install | **OK** — not per evaluation |
| `platform.analytics.engine.AnalyticsTagCatalogService` `catalogLock` | 2 | tag catalog refresh | scheduled | **OK** |
| `platform.analytics.AnalyticsQueryRateLimiter` `window` | 1 | sliding window | per analytics query | **OK** — µs body |
| `security.LoginAttemptLimiter` `history` | 2 | attempt history | per login | **OK** |
| Drop-in loaders (`DropInUiPackLoader`, `DropInSymbolPackLoader`, `DropInAnalyticsPackLoader`) | 2 + 3 + 4 | install / uninstall of a pack | operator action | **OK** — install must be exclusive |
| Start-once / reschedule (`TelemetryIngressDispatcher.start`, `RuntimeTelemetryCoalescer.ensureSchedulerStarted`, `BindingRuleAsyncExecutor.ensureStarted`, `BindingPeriodicScheduler.reschedule`, `AnalyticsEngineScheduler.reschedule`, `MqttGatewayIngressDispatchService.ensureWorkersStarted`, `BlueprintApplicationRunner.ensure*Fixtures`) | 8 | lifecycle | startup / settings change | **OK** |
| `license.*` (`PlatformLicenseService`, `InstallationIdService`), `object.VisualGroupService`, `function.java.JavaFunctionCompileClasspath` | 4 | lazy init caches | first use | **OK** |
| `websocket.ObjectWebSocketHandler` `synchronized (session)` | 1 | one `WebSocketSession.sendMessage` at a time | per push | **OK** — Spring requires serialised sends per session; consider `ConcurrentWebSocketSessionDecorator` if a slow client ever blocks the broadcaster |

**Result:** no other site serialises independent work the way the pre-F-08 ObjectManager did. One
**watch** item (`RecentEventCache` read scans) with a documented trigger; everything else is either keyed
per resource, cold, or protecting a structure that needs a mutex anyway.
