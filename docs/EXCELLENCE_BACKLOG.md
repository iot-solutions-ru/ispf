# Excellence backlog (REQ-EX) — «★★★★★ по всем осям»

Стратегический беклог развития ISPF до уровня **лидерства по каждой оси** сравнения с Ignition, AWS/Azure IoT, ThingsBoard и SkySpark. Дополняет закрытые волны BL-01…77 ([CODE_AUDIT_BACKLOG.md](CODE_AUDIT_BACKLOG.md)).

**North star:** open, self-hosted **industrial application platform** — единое дерево объектов + SCADA HMI + automation + vertical apps + AI для внедрения. Не копировать AWS IoT как SaaS; **догнать** по scale и **обогнать** по app platform и AI-on-tree.

**Связанные документы:**

| Документ | Роль |
| -------- | ---- |
| [ROADMAP.md § Phase 23](ROADMAP.md#phase-23--platform-excellence-req-ex) | Фазы и статусы |
| [CODE_AUDIT_BACKLOG.md § Wave J…S](CODE_AUDIT_BACKLOG.md#wave-j--ex-driver-production-depth) | Сводные таблицы BL-78…132 |
| [GAP_REGISTRY.md](GAP_REGISTRY.md) | Sprint planning |
| [0002 dogfooding gate](decisions/0002-dogfooding-gate.md) | Новые драйверы и API |

**Правило:** каждый BL-XX закрывается только при выполнении **Acceptance** ниже. Статус `Done` → PR + строка в GAP + ROADMAP Phase 23.

---

## Матрица осей и целевое состояние

| Ось (сравнение) | Эталон рынка | REQ-EX | BL | Целевое ★★★★★ |
| ----------------- | ------------ | ------ | -- | ------------- |
| Единая object-модель | — | EX-MODEL | — | Удерживать; не размывать tree-first |
| SCADA HMI + mimic | Ignition Perspective | EX-HMI | 86–95 | Тренды, alarming UI, mimic 60fps, tablet PWA |
| Ширина протоколов | Kepware | EX-DRIVER | 78–85 | Top-10 PRODUCTION + demand-driven expansion |
| Глубина протоколов | OPC UA / BACnet stacks | EX-DRIVER | 79–84 | Discovery, subscribe, quality, observedAt |
| Workflow / MES-lite | Camunda + MES | EX-MES | 121–124 | OEE, work orders, escalation без Java |
| App platform | — (наш лидер) | EX-APP | 96–100 | Marketplace, semver bundle, 3+ reference apps |
| Edge + federation | AWS IoT Edge | EX-FED | 117–120 | Store-forward, peer SLO, selective sync |
| Масштаб telemetry | EMQX / AWS IoT | EX-SCALE | 111–116 | Ingress tier, CI load gate, CH prod path |
| UX / mobile | Commercial HMI | EX-HMI | 90–95 | Operator PWA, offline, a11y |
| Open / self-host | ThingsBoard | EX-OPS | 127–128 | One-click prod, air-gap runbook |
| AI engineering | — (наша ниша) | EX-AI | 106–110 | Spec→deploy, audit, SLO, safe mutate |
| Semantic / BMS | SkySpark / FIN | EX-SEM | 101–105 | Haystack query runtime на дереве |
| QA / trust | — | EX-QA | 129–132 | Live e2e, i18n gate, visual smoke |

---

## Горизонты (порядок инвестиций)

```text
Горизонт 1 — Trust (без доверия нет ★★★★★)
  Wave J EX-DRIVER + Wave K EX-ALARM/HMI (часть) + EX-SCALE-114 ops

Горизонт 2 — Velocity (обогнать всех в time-to-solution)
  Wave L EX-APP + Wave M EX-SEM + Wave N EX-AI

Горизонт 3 — Scale & federation (по demand)
  Wave O EX-SCALE + Wave P EX-FED + Wave R EX-OPS/TENANT

Постоянно: EX-QA (Wave S), удержание EX-MODEL
```

Параллельно вести **2–3 волны**, не все 11 осей сразу.

---

## Wave J — EX-DRIVER: production depth (BL-78…85)

**Цель:** глубина Modbus / OPC UA / BACnet / MQTT / S7 на уровне промышленных стеков; ширина stub-драйверов — только по [0002](decisions/0002-dogfooding-gate.md).

### BL-78 — ADR: Driver Production Matrix

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- ADR-0022 (новый): критерии `PRODUCTION` vs `BETA` vs `STUB`.
- Матрица capability: poll, subscribe, write, discovery, quality, observedAt, loopback test class.
- `DriverMaturityRegistry` синхронизировать с матрицей (расширение BL-27).

**Acceptance:**

- [ ] ADR Accepted в `docs/decisions/`
- [ ] Таблица top-10 драйверов с целевыми capability и владельцем
- [ ] CI job `driver-matrix-check` падает при badge PRODUCTION без loopback test

---

### BL-79 — observedAt rollout (top protocols)

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-78, BL-69 (SPI Done) |

**Scope:**

- 3-arg `updateVariable(name, value, observedAt)` в: **modbus-tcp/rtu**, **opc-ua**, **bacnet**, **s7**, **snmp** (где применимо).
- Historian пишет `observed_at`; UI trend показывает source time при наличии.

**Acceptance:**

- [ ] Каждый из 5 драйверов: loopback/integration test с non-`Instant.now()` timestamp
- [ ] `DRIVERS.md` — таблица «observedAt support»
- [ ] Virtual unified + MQTT (BL-74) остаются эталоном

---

### BL-80 — OPC UA: discovery + subscriptions

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-78 |

**Scope:**

- Browse/discovery endpoint → suggest point mappings в UI (inspector или wizard).
- Subscription mode (где библиотека позволяет) вместо pure poll для mapped nodes.
- Certificate / security mode documented для prod.

**Acceptance:**

- [ ] Demo: connect к public OPC UA test server, browse → import mappings JSON
- [ ] Subscription path с fallback на poll
- [ ] Loopback test в CI (embedded или testcontainers)

---

### BL-81 — BACnet: device discovery + readProperty

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-78 |

**Scope:**

- Who-Is / I-Am discovery (опционально в config).
- Стабильный read path для analog/binary/multi-state с unit metadata → Haystack `unit` tag.

**Acceptance:**

- [ ] `BacnetDeviceDriverNetworkTest` или BACnet loopback расширен discovery smoke
- [ ] Документация config keys в `DRIVERS.md`

---

### BL-82 — Quality flags (GOOD / UNCERTAIN / BAD)

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-79 |

**Scope:**

- Расширение `DataRecord` или parallel `quality` field для telemetry variables.
- Drivers map protocol status → platform quality.
- Historian + charts: не рисовать BAD или показывать gap (config).

**Acceptance:**

- [ ] ADR или раздел в `OBJECT_MODEL.md`
- [ ] Минимум OPC UA + virtual demo quality transitions
- [ ] Chart widget respects quality (skip or dashed segment)

---

### BL-83 — Driver interop CI matrix

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-78 |

**Scope:**

- GitHub Actions matrix: modbus, mqtt, snmp, opc-ua (loopback), bacnet (network test), http loopback.
- Отчёт в CI summary: pass/fail per driver pack.

**Acceptance:**

- [ ] `.github/workflows/driver-interop.yml` на PR при изменении `packages/ispf-driver-*`
- [ ] Badge или comment в PR при regression

---

### BL-84 — Point mapping validation UI

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-59 (Haystack mappings Done) |

**Scope:**

- Inspector: validate `driverPointMappingsJson` — unknown keys, duplicate variables, Haystack tag hints.
- «Test read» кнопка для single point (reuse runtime API).

**Acceptance:**

- [ ] UI errors для invalid JSON schema
- [ ] Test read вызывает `readPoints` / single variable refresh
- [ ] i18n en/ru/de/zh

---

### BL-85 — Top-10 PRODUCTION promotion gate

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-78…84 |

**Scope:**

- Фиксированный список: virtual, mqtt, modbus-tcp, opc-ua, snmp, bacnet, s7, http, jdbc, kafka (уточняется в ADR).
- Каждый: PRODUCTION badge только при green CI matrix + write path (где применимо) + docs.

**Acceptance:**

- [ ] Все 10 — `PRODUCTION` в `DriverMaturityRegistry`
- [ ] `GET /api/v1/info` или driver catalog отражает maturity
- [ ] Нет PRODUCTION без теста в `packages/ispf-driver-*/src/test`

---

## Wave K — EX-HMI & alarming (BL-86…95)

**Цель:** operator experience уровня Ignition Perspective — alarming, тренды, mobile/PWA, mimic performance.

### BL-86 — Alarm shelving

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Shelve alarm by operator (duration / until resume) на alert rule fires или event journal entries.
- API + operator sidebar / alarm bar UI.
- Audit: who shelved, when, why (optional comment).

**Acceptance:**

- [ ] Shelved alarm не эскалирует notification (BL-44) в период shelve
- [ ] Journal entry или dedicated shelve log
- [ ] Operator UI: list active shelves

---

### BL-87 — Priority classes + ack workflow

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-86 |

**Scope:**

- Priority на alert rules (P1…P4 или critical/high/medium/low).
- Operator acknowledge / unack; correlator может требовать ack перед close.
- Flood control: max N fires per minute per rule (расширение rateLimitSeconds).

**Acceptance:**

- [ ] Model fields + inspector UI
- [ ] Operator alarm bar сортировка по priority
- [ ] Тест: flood → suppression

---

### BL-88 — Operator alarm bar 24/7 polish

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-87 |

**Scope:**

- Sticky alarm bar, цветовая кодировка, optional browser notification (permission).
- Звук optional (user setting, off by default).
- WebSocket push без polling lag.

**Acceptance:**

- [ ] Новое событие HIGH появляется в bar &lt; 2s после fire (local test)
- [ ] Настройка в operator preferences
- [ ] Не ломает mobile drawer (BL-52)

---

### BL-89 — Trend client (industrial grade)

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-40, BL-70 |

**Scope:**

- Dedicated trend view или расширение chart widget: multi-pen, pan/zoom, cursors, export PNG/CSV.
- Calendar range + user TZ (BL-70).
- Live + historian aggregate bands.

**Acceptance:**

- [ ] Operator может открыть trend с object-table row (context menu)
- [ ] 4 pens на одном графике без UI freeze (10k points)
- [ ] Документация в `DASHBOARDS.md`

---

### BL-90 — Operator PWA shell

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-52 |

**Scope:**

- `manifest.webmanifest`, service worker (cache shell assets only).
- Install prompt на supported browsers.
- Safe-area insets для notch devices.

**Acceptance:**

- [ ] Lighthouse PWA checklist: installable
- [ ] `npm run build` включает manifest icons
- [ ] Operator mode работает installed на Android Chrome smoke

---

### BL-91 — Offline cache (critical screens)

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-90 |

**Scope:**

- Cache last-known dashboard layout + last variable values for configured «offline screens».
- Banner «offline / stale data» when API unreachable.
- Read-only; no write offline.

**Acceptance:**

- [ ] Airplane mode: operator видит cached dashboard с watermark stale
- [ ] Reconnect → refresh without full reload

---

### BL-92 — SCADA mimic performance budget

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Profiling mimic editor + runtime: 30+ symbols, animation bindings.
- Target: 60fps на mid-range tablet для demo mimic.
- Debounce binding updates; `will-change` только где нужно ([skill](.cursor/skills/make-interfaces-feel-better/SKILL.md)).

**Acceptance:**

- [ ] Demo mimic `examples/` или lab path documented
- [ ] Chrome performance trace: no long tasks &gt; 50ms sustained при live data 1Hz

---

### BL-93 — Accessibility baseline (WCAG 2.1 AA partial)

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Focus order, aria-labels на operator nav, alarm bar, login.
- Color contrast tokens audit (light/dark).
- Keyboard navigation explorer tree (admin).

**Acceptance:**

- [ ] axe-core smoke на login + operator launcher (0 critical)
- [ ] Документ «known gaps» если полный AA не достигнут

---

### BL-94 — SCADA symbol library expansion

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | [PID_SYMBOLS_LEGAL.md](PID_SYMBOLS_LEGAL.md) |

**Scope:**

- Расширение P&ID pack; valve/motor/pump variants.
- Legal review checklist complete.

**Acceptance:**

- [ ] `LICENSE.md` / `THIRD_PARTY_NOTICES` обновлены
- [ ] Editor palette показывает новые symbols

---

### BL-95 — Operator performance CI gate

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-90 |

**Scope:**

- Lighthouse CI на operator launcher (performance ≥ 85, accessibility ≥ 90).
- Bundle size budget warning в `vite build`.

**Acceptance:**

- [ ] `.github/workflows/ci.yml` или отдельный job `web-console-lighthouse`
- [ ] Fail on regression &gt; 10% без explicit bump

---

## Wave L — EX-APP: marketplace & velocity (BL-96…100)

**Цель:** удержать и усилить лидерство app platform — время от идеи до operator HMI без Java в core.

### BL-96 — Solution catalog UI

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | bundle export (merged) |

**Scope:**

- System или Explorer: каталог published bundle metadata (appId, version, description, screens).
- Import from catalog URL or uploaded zip (reuse validate/import API).

**Acceptance:**

- [ ] Admin видит список apps + versions из platform DB
- [ ] One-click «install demo bundle» для reference apps

---

### BL-97 — Bundle semver contract

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Manifest `version` semver required; deploy rejects invalid.
- Changelog field in manifest optional; shown in catalog UI.
- Migration notes on major bump (validate warning).

**Acceptance:**

- [ ] `ApplicationBundleDeployService` validates semver
- [ ] Test: deploy 1.0.0 then 1.1.0 upgrade path
- [ ] Document in `APPLICATIONS.md`

---

### BL-98 — Integrator CI template

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-96, BL-97 |

**Scope:**

- `examples/ci-template/`: GitHub Actions для `validate` + `dry-run deploy` against ISPF API.
- Script `tools/bundle-validate-cli` (optional thin wrapper).

**Acceptance:**

- [ ] README: integrator copies template, runs against local ISPF
- [ ] Used in at least one `examples/*` workflow

---

### BL-99 — Third reference app (building or energy)

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | [0002](decisions/0002-dogfooding-gate.md) |

**Scope:**

- Новый `examples/building-hvac-app` или `examples/energy-metering-app`: models, dashboards, operator UI, Haystack tags.
- Dogfooding: zero new REQ-PF без gate.

**Acceptance:**

- [ ] Bundle deploy smoke in CI
- [ ] Walkthrough doc like `REFERENCE_MES_WALKTHROUGH.md`
- [ ] Operator `?mode=operator&app=...` demo path

---

### BL-100 — Bundle trust (signing optional)

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-97 |

**Scope:**

- Optional manifest signature (RSA) verify on import.
- Platform setting: `requireSignedBundles` for prod.

**Acceptance:**

- [ ] Signed bundle imports; tampered bundle rejected
- [ ] Document key rotation in `DEPLOYMENT.md`

---

## Wave M — EX-SEM: semantic runtime (BL-101…105)

**Цель:** Haystack/FIN-class интеграции без замены object tree ([ADR-0021](decisions/0021-haystack-semantic-overlay.md)).

### BL-101 — ADR: Haystack query runtime

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-56…62 Done |

**Scope:**

- ADR-0023: subset Haystack filter syntax over in-memory tag index built from tree + mappings.
- Out of scope: full Haxall runtime, replacement of dot-path.

**Acceptance:**

- [ ] ADR Accepted с примерами filter (`point and temp`, `equip and ahu`)

---

### BL-102 — API `GET /platform/haystack/query`

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-101 |

**Scope:**

- Endpoint: filter → list of `{path, tags, curVal?}`.
- Pagination, tenant scope, ACL on paths.

**Acceptance:**

- [ ] Integration test: lab device with haystack tags queryable
- [ ] OpenAPI snippet in `API.md`

---

### BL-103 — Dashboard auto-bind by query (extend BL-62)

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-102 |

**Scope:**

- Dashboard builder: «bind widgets from haystack query» wizard.
- Reuse `HaystackBindDialog` patterns.

**Acceptance:**

- [ ] Demo dashboard from `equip and point and temp` за &lt; 5 min admin time

---

### BL-104 — Brick class inference

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-60 |

**Scope:**

- Mapping table haystack markers → suggested `brickClass`.
- Inspector: suggest brick class; export TTL includes inferred classes.

**Acceptance:**

- [ ] 20 common mappings (AHU, VAV, Sensor, Meter)
- [ ] Unit test inference

---

### BL-105 — Semantic roundtrip test

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-102, BL-58 |

**Scope:**

- Test: tree with tags → export JSON → import to empty subtree (scoped) → query returns equivalent set.

**Acceptance:**

- [ ] Gradle test green; documents limitations in ADR

---

## Wave N — EX-AI: production agent (BL-106…110)

**Цель:** AI как **инженерный ускоритель**, без сюрпризов в prod SCADA.

### BL-106 — Mutating tools: explicit approval mode

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-75, AgentPlanGuard |

**Scope:**

- Platform setting `ispf.ai.agent.require-approval-for-mutate` default `true` in prod profile.
- UI: pending plan steps cannot execute until user clicks Approve (extend existing plan UI).

**Acceptance:**

- [ ] create_object / deploy без approve → blocked
- [ ] Audit log records approver username

---

### BL-107 — Agent audit export

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- `GET /api/v1/ai/agent/sessions/{id}/audit` → JSON (tools, args redacted secrets, timestamps).
- Export CSV for compliance.

**Acceptance:**

- [ ] Admin-only; matches `ai_tool_audit` DB rows
- [ ] Document retention in `AI_DEVELOPMENT.md`

---

### BL-108 — Reference scenario catalog (10 paths)

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-106 |

**Scope:**

- `docs/agent-scenarios/` или ContextPack: 10 сценариев spec→validate→deploy.
- Automated tests with mocked LLM returning fixed plan JSON (`SpecIntakeScenarioTest` style).

**Acceptance:**

- [ ] 10 Gradle tests green
- [ ] Listed in AI Studio help panel

---

### BL-109 — Operator agent hard allowlist

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | OperatorAgentTurnGuard |

**Scope:**

- Formal allowlist: list_reports, run_report, get_variable_history, finish — no mutate tools.
- Fuzz test: operator profile cannot invoke create_object.

**Acceptance:**

- [ ] `AgentToolGuardContractTest` extended for OPERATOR profile
- [ ] MCP operator scope documented

---

### BL-110 — Agent SLO dashboard

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-75 |

**Scope:**

- System metrics cards: turns/hour, rate limited count, guard blocks by type, avg steps/turn.
- Prometheus → UI (reuse System metrics pattern).

**Acceptance:**

- [ ] Cards visible when `ispf.ai.enabled=true`
- [ ] Metrics documented in `AI_DEVELOPMENT.md`

---

## Wave O — EX-SCALE: telemetry & historian (BL-111…116)

**Цель:** путь к 50k+ events/s и prod historian без стыда перед ThingsBoard/AWS.

### BL-111 — ADR: telemetry ingress tier

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- ADR-0024: отдельный ingest path (MQTT/Kafka) → normalize → historian/event bus.
- Monolith остаёт для SCADA poll; burst — через ingress.

**Acceptance:**

- [ ] Diagram в ADR; comparison with current meter-bus path

---

### BL-112 — MQTT ingress worker (stateless)

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-111 |

**Scope:**

- Optional module or sidecar: subscribe topics → map to device paths → coalesce → POST internal API or direct CH insert.
- Reuse `mqtt-ingress-load-test` patterns.

**Acceptance:**

- [ ] docker-compose profile `ingress` in repo
- [ ] Load test report committed (≥ 10k msg/s single node baseline)

---

### BL-113 — CI load test gate

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | [LOAD_TESTING.md](LOAD_TESTING.md) |

**Scope:**

- Nightly or manual workflow: `events-internal-load-test` + threshold from `ISPF_LOAD_P99_CEILING_MS`.
- Fail workflow on regression.

**Acceptance:**

- [ ] `.github/workflows/load-test.yml` workflow_dispatch
- [ ] Results artifact uploaded

---

### BL-114 — ClickHouse variable history prod playbook

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned (ops) |
| **Зависимости** | BL-40 Done |

**Scope:**

- `DEPLOYMENT.md` section: enable `ISPF_VARIABLE_HISTORY_STORE=clickhouse`, migration, verify script.
- Align with `deploy/vps-clickhouse-verify.sh`.

**Acceptance:**

- [ ] Runbook complete; no code required for Done
- [ ] Checklist in GAP_REGISTRY marked ops-ready

---

### BL-115 — Horizontal scale documentation

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-41, BL-42 |

**Scope:**

- Architecture doc: N replicas, NATS fan-out, sticky sessions for WS, leader election schedulers.
- Limits and known single-writer constraints.

**Acceptance:**

- [ ] `DEPLOYMENT.md` multi-instance section updated with tested topology

---

### BL-116 — Historian dual-write migration

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-114 |

**Scope:**

- Optional phase: write JDBC + CH simultaneously; backfill job from PG to CH.
- Tooling for cutover window.

**Acceptance:**

- [ ] Spike script in `deploy/` or `tools/`
- [ ] Document risks (disk, consistency)

---

## Wave P — EX-FED: edge excellence (BL-117…120)

**Цель:** federation уровня edge IoT platform — автономность при разрыве связи.

### BL-117 — Edge store-and-forward

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | Phase 7 tunnel Done |

**Scope:**

- Outbound agent буферизует telemetry/events при разрыве WS; replay с ordering guard.
- Config: max buffer MB, drop policy.

**Acceptance:**

- [ ] Integration test: disconnect 60s → reconnect → hub receives backlog
- [ ] `FEDERATION.md` updated

---

### BL-118 — Federation peer health SLO

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Per-peer: last successful proxy, latency, tunnel state.
- System UI cards + warn when stale &gt; N minutes.

**Acceptance:**

- [ ] `GET /federation/peers/{id}/health` or extend auth-status
- [ ] Explorer federation panel shows red/yellow/green

---

### BL-119 — Selective subtree sync (config)

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-45 |

**Scope:**

- Export/import federation subtree (models + dashboards + devices) scoped by rootPath.
- Conflict policy per object type.

**Acceptance:**

- [ ] API draft; UI «sync this folder» from explorer
- [ ] Test: bind + layout roundtrip

---

### BL-120 — Federation chaos tests

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-117, BL-118 |

**Scope:**

- Automated: kill tunnel mid-proxy-write; verify no corrupt local tree.
- Cycle detection stress test.

**Acceptance:**

- [ ] Gradle or Testcontainers test documented in `FEDERATION.md`

---

## Wave Q — EX-MES: workflow patterns (BL-121…124)

**Цель:** MES-lite без Java — типовые производственные сценарии на дереве.

### BL-121 — OEE reference pattern

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-99 or MES examples |

**Scope:**

- Dashboard + variables for availability/performance/quality; script functions for OEE calc.
- Document in `REFERENCE_MES_WALKTHROUGH.md` extension.

**Acceptance:**

- [ ] Demo OEE dashboard path in tree
- [ ] No new Java in server

---

### BL-122 — BPMN timer boundary events

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Timer boundary в embedded BPMN editor + engine support if missing.
- ISPF service task catalog entry.

**Acceptance:**

- [ ] Workflow test: escalation after PT15M
- [ ] `WORKFLOWS.md` example

---

### BL-123 — Escalation workflow templates

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-87, BL-122 |

**Scope:**

- Template BPMN: alert → user task → escalate → notify (email/webhook).
- Importable from System templates folder.

**Acceptance:**

- [ ] One-click deploy template in admin
- [ ] Correlator demo wires to template

---

### BL-124 — ISA-95 catalog documentation

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Map ISPF object types to ISA-95 levels (enterprise/site/area/work center).
- Guidance for integrators (doc only).

**Acceptance:**

- [ ] `OBJECT_MODEL.md` section with diagram
- [ ] No mandatory code change

---

## Wave R — EX-OPS & tenant (BL-125…128)

**Цель:** ★★★★★ open/self-host и опциональный SaaS.

### BL-125 — Tenant isolation hardening

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | multi-tenant spike |

**Scope:**

- Audit all APIs for `TenantScopeService` coverage.
- Tests: tenant A cannot read tenant B paths.

**Acceptance:**

- [ ] Checklist 100% admin APIs; gaps filed as sub-issues
- [ ] Integration test suite `TenantIsolationTest`

---

### BL-126 — Per-tenant quotas (optional SaaS)

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | BL-125 |

**Scope:**

- Quotas: max devices, max historian points/day, max agent turns/hour per tenant.
- Enforce at API layer.

**Acceptance:**

- [ ] 429 on exceed with clear message
- [ ] System UI super-admin quotas

---

### BL-127 — One-click production deploy

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Helm chart **или** улучшенный `deploy/` compose stack: server + postgres + redis + clickhouse optional + nginx.
- Single command from clean VM (non-VPS-specific doc).

**Acceptance:**

- [ ] `DEPLOYMENT.md` «Production quick start» ≤ 30 min on fresh Linux
- [ ] Health checks pass script

---

### BL-128 — Air-gap deployment guide

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-127 |

**Scope:**

- Offline bundle: jars, driver packs, web-console zip, docker images export.
- Update procedure without internet.

**Acceptance:**

- [ ] Checklist doc; matches commercial licensing flow

---

## Wave S — EX-QA & i18n (BL-129…132)

**Цель:** доверие к релизам — live tests, нет регрессий UI/copy.

### BL-129 — Playwright live: operator + alarming

| | |
| - | - |
| **P** | P1 |
| **Статус** | Planned |
| **Зависимости** | BL-77 |

**Scope:**

- Live tests: operator app launch, alarm bar visible, dashboard render.
- `data-testid` on operator shell.

**Acceptance:**

- [ ] 3+ tests in `e2e/live-operator.spec.ts`
- [ ] Secrets documented

---

### BL-130 — Scheduled staging e2e

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-129 |

**Scope:**

- `e2e-live.yml` optional weekly cron + manual dispatch.
- Slack/email notify on fail (optional).

**Acceptance:**

- [ ] Workflow yaml committed; disabled by default without secrets

---

### BL-131 — Visual regression smoke

| | |
| - | - |
| **P** | P3 |
| **Статус** | Planned |
| **Зависимости** | — |

**Scope:**

- Playwright screenshot compare: login, explorer, operator launcher (tolerance 2%).
- Store baselines in repo or CI artifact.

**Acceptance:**

- [ ] One job on PR for `apps/web-console` UI changes

---

### BL-132 — i18n zero hardcoded gate

| | |
| - | - |
| **P** | P2 |
| **Статус** | Planned |
| **Зависимости** | BL-76 |

**Scope:**

- Extend `npm run i18n:check` or new script: grep Cyrillic/Latin UI strings in TSX (allowlist).
- Fail CI on new hardcoded operator-facing strings.

**Acceptance:**

- [ ] CI step; baseline allowlist for legacy debt shrinking over time

---

## Sprint planning (рекомендация)

```text
Sprint EX-1 (Trust — drivers + alarm foundation)
  BL-78, BL-79, BL-86, BL-87, BL-114 (docs)

Sprint EX-2 (Operator HMI)
  BL-89, BL-90, BL-88, BL-129

Sprint EX-3 (AI production)
  BL-106, BL-108, BL-110

Sprint EX-4 (App velocity)
  BL-96, BL-97, BL-99, BL-98

Sprint EX-5 (Semantic)
  BL-101, BL-102, BL-103

Sprint EX-6 (Scale spike)
  BL-111, BL-112, BL-113

Backlog по demand
  BL-80, BL-81, BL-100, BL-117…128, BL-131, BL-132
```

---

## История

| Дата | Изменение |
| ---- | --------- |
| 2026-06-30 | Первая версия REQ-EX: BL-78…132, Phase 23 в ROADMAP |
