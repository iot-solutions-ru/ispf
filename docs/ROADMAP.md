# ISPF Platform Roadmap

Единый источник правды: фазы, спринты, REQ-PF/FW, BL-01…139.

| | |
| --- | --- |
| **Baseline** | `main`, июль 2026 |
| **Обновлено** | 2026-07-05 |
| **North star** | Open self-hosted industrial application platform — object tree + SCADA HMI + automation + apps + AI ([ARCHITECTURE.md](ARCHITECTURE.md)) |

---

## Сводка

| Категория | Всего | Done | Partial | Planned | Cancelled |
| --------- | ----- | ---- | ------- | ------- | --------- |
| REQ-PF | 13 | 13 | 0 | 0 | 0 |
| REQ-FW | 20 | 20 | 0 | 0 | 0 |
| BL-01…139 | 139 | 127 | 2 | 9 | 1 |
| Phase 0–23 | 23 | 23 | 0 | — | — |
| Sprint S01–S23 | 23 | 22 | 0 | 1 | — |

**Текущий спринт:** [S23 — Differentiation](#sprint-s23--differentiation) (Planned). **S21 — HMI Moat — Done.** **S22 — Edge Trust — Done.**

**Следующие приоритеты:** S23 semantic (BL-104, 105).

Программа acceleration: [ACCELERATION_PROGRAM.md](ACCELERATION_PROGRAM.md).

Деплой на VPS — только по запросу ([vps-deploy.mdc](../.cursor/rules/vps-deploy.mdc)).

---

## Соглашения

### Идентификаторы

| Префикс | Значение | Пример |
| ------- | -------- | ------ |
| **Phase N** | Тематическая волна roadmap (история + REQ-EX) | Phase 20, Phase 23 |
| **Sprint SN** | Единица delivery (~2 недели), единая нумерация S01… | S01, S18 |
| **HFNN** | Hotfix вне спринта | HF01 |
| **BL-NN** | Задача code audit / excellence | BL-80 |
| **PF-NN / FW-NN** | Требование application platform / framework | PF-03 |

### Статусы (везде одинаково)

| Статус | Смысл |
| ------ | ----- |
| Done | Acceptance выполнен |
| Partial | Основа есть, хвост открыт |
| In progress | Активная работа |
| Planned | Не начато |
| Cancelled | Отменено / superseded |
| Ops | Runbook/docs готовы; rollout по запросу |

### Маппинг legacy → Sprint SN

| Было | Стало |
| ---- | ----- |
| EX-1…EX-18 | S01…S18 |
| EX-INGRESS-01 | HF01 |
| EX0…EX4 (acceleration) | S19…S23 |

---

## Реестр спринтов

| Sprint | Phase | Тема | BL / scope | Статус |
| ------ | ----- | ---- | ---------- | ------ |
| [S01](#sprint-s01--trust-drivers--alarm) | 23 | Trust — drivers + alarm | BL-78, 79, 86, 87, 114 | Done |
| [S02](#sprint-s02--operator-hmi) | 23 | Operator HMI | BL-89, 90, 88, 129, 130 | Done |
| [S03](#sprint-s03--ai-production) | 23 | AI production | BL-106, 108, 110 | Done |
| [S04](#sprint-s04--app-velocity) | 23 | App velocity | BL-96…99 | Done |
| [S05](#sprint-s05--semantic) | 23 | Semantic runtime | BL-101…103 | Done |
| [S06](#sprint-s06--scale-spike) | 23 | Scale spike | BL-111; BL-112 Cancelled | Done |
| [S07](#sprint-s07--trust--load-gate) | 23 | Trust + load gate | BL-86, 87, 113 | Done |
| [S08](#sprint-s08--driver-production) | 23 | Driver production depth | BL-80, 83, 84, 85 | Done |
| [S09](#sprint-s09--bacnet) | 23 | BACnet discovery | BL-81 | Done |
| [S10](#sprint-s10--telemetry-quality) | 23 | Telemetry quality | BL-82 | Done |
| [S11](#sprint-s11--operator-offline) | 23 | Operator offline | BL-91, 90 | Done |
| [S12](#sprint-s12--audit--driver-ux) | 23 | Audit + driver UX | BL-107 | Done |
| [S13](#sprint-s13--production-gates) | 23 | Production gates + security | BL-85, 109, 90 | Done |
| [S14](#sprint-s14--bundle-trust--prod) | 23 | Bundle trust + prod quickstart | BL-100, 127 | Done |
| [S15](#sprint-s15--air-gap) | 23 | Air-gap ops | BL-128 | Done |
| [S16](#sprint-s16--qa-close-out) | 23 | QA close-out | BL-90, 131, 132 | Done |
| [S17](#sprint-s17--federation-edge) | 23 | Federation edge | BL-117, 118 | Done |
| [S18](#sprint-s18--horizontal-cluster) | 23 | Horizontal cluster | BL-133…139 | Done |
| [HF01](#hf01--elastic-ingress) | 23 | Elastic ingress hotfix | ADR-0026, 0027 | Done |
| [S19](#sprint-s19--calibration-acceleration) | 23 | Acceleration: calibration | baseline, scorecard, scope | Done |
| [S20](#sprint-s20--speed-engine) | 23 | Acceleration: CI speed | pr-fast, cache, flaky triage | Done |
| [S21](#sprint-s21--hmi-moat) | 23 | Acceleration: HMI moat | BL-92, 93, 95 | Done |
| [S22](#sprint-s22--edge-trust) | 23 | Acceleration: federation | BL-119, 120 | Done |
| [S23](#sprint-s23--differentiation) | 23 | Acceleration: semantic moat | BL-104, 105 | Planned |

---

## REQ-PF — Application platform (Done)

| ID | Capability | Phase | Статус |
| -- | ---------- | ----- | ------ |
| PF-01 | Application Function Runtime | 0–1, 5 | Done |
| PF-02 | Application Data Layer | 0–1 | Done |
| PF-03 | Application Package Deploy | 0–1, 6 | Done |
| PF-04 | BPMN `invoke_function` | 1 | Done |
| PF-05 | Platform Scheduler | 1 | Done |
| PF-06 | BFF Wire Gateway | 1 | Done |
| PF-07 | Model Registry Persistence | 5 | Done |
| PF-08 | Variable ↔ SQL sync | 5 | Done |
| PF-09 | Integration Simulator SPI | 6 | Done |
| PF-10 | Workflow cancel + signal | 1, 5 | Done |
| PF-11 | Function rollback / versions | 6 | Done |
| PF-12 | Tree-first SQL reports | 12–13 | Done |
| PF-12b | Report Builder UX, exports | 12–13 | Done |
| PF-13 | Federation | 4, 7–8 | Done |
| PF-14 | Driver catalog (58 `driverId`) | 3, 10 | Done |

Детали: [APPLICATIONS.md](APPLICATIONS.md).

---

## REQ-FW — Framework (Done)

| ID | Capability | Track | Phase | Статус |
| -- | ---------- | ----- | ----- | ------ |
| FW-01 | ADR `docs/decisions/` | DOC | 16 | Done |
| FW-02 | Gap-registry process | DOC | 16 | Done |
| FW-10 | RSA licensing | LIC | 16 | Done |
| FW-11 | `installationId` + LicenseBuilder | LIC | 16 | Done |
| FW-12 | Bundle dependency manifest | LIC | 16 | Done |
| FW-20 | MES reference walkthrough | REF | 16 | Done |
| FW-30 | Solution public API doc | API | 16 | Done |
| FW-31 | Event catalog в bundle | API | 16 | Done |
| FW-32 | Event bus vs sync RPC | NET | 16 | Done |
| FW-40…43 | AI Layer + Studio | AI | 16 | Done |
| FW-44…48 | Tree-first agent + tools | AI | 16–17 | Done |
| FW-49 | Agent trace + audit metrics | AI | 22 | Done |
| FW-50 | Session knowledge (agent chat docs) | AI | 22 | Done |
| FW-51 | Turn graph view (Web Console) | AI | 22 | Done |
| FW-52 | Agent metrics + prompt version | AI | 22 | Done |
| FW-53 | Plan depth LITE / FULL | AI | 22 | Done |
| FW-50 | Licensed driver JAR | DRV | 16 | Done |
| FW-60 | Time & timezone (BL-66…71) | TIME | 21 | Done |

Детали: [AI_DEVELOPMENT.md](AI_DEVELOPMENT.md), [decisions/](decisions/).

---

## Phase 0–22 — История платформы (Done)

<details>
<summary>Phase 0 — Stabilization</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 0.1 | GitHub Actions CI | Done |
| 0.2 | Gradle test memory limits | Done |
| 0.3 | PF-01c `map` / `buildRecord` | Done |
| 0.4 | PF-03 `models[]` в bundle | Done |
| 0.5 | Leader lock schedulers | Done |
| 0.6 | WebSocket auth | Done |
| 0.7 | OperatorUi `eventJournalObjectPath` | Done |
| 0.8 | Reference app warehouse | Done |
| 0.9 | System folder panels | Done |

</details>

<details>
<summary>Phase 1 — Application platform closure</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 1.1 | Acceptance PF-01 JSON+SQL | Done |
| 1.2 | Dogfooding warehouse | Done |
| 1.3 | PF-06 field labels | Done |
| 1.4 | Bundle rollback UX | Done |

</details>

<details>
<summary>Phase 2 — Production gate</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 2.1 | Keycloak/OIDC login | Done |
| 2.2 | TimescaleDB retention | Done |
| 2.3 | Per-object ACL | Done |
| 2.4 | NATS event bus | Done |

</details>

<details>
<summary>Phase 3 — Connectivity & HMI</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 3.1 | Driver maturity labels | Done |
| 3.2 | Stub → production по demand | Done |
| 3.3 | React Router / deep links | Done |
| 3.4 | Frontend smoke tests | Done |
| 3.5 | Legacy operator manifest deprecation | Done |

</details>

<details>
<summary>Phase 4 — Scale & topology</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 4.1 | PF-13 federation spike | Done |
| 4.2 | Multi-tenant namespaces | Done |

</details>

<details>
<summary>Phase 5 — Усиление механизмов</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 5.1 | Модели: bindings, inheritance, version | Done |
| 5.2 | Функции: script steps, SQL bindings | Done |
| 5.3 | События + correlators | Done |
| 5.4 | Workflow service tasks | Done |
| 5.5 | Bundle = упаковка дерева | Done |

</details>

<details>
<summary>Phase 6–17 — v0.3.0, federation, catalogs, REQ-FW</summary>

| Phase | Тема | Статус |
| ----- | ---- | ------ |
| 6 | Post-v0.2.0 production | Done |
| 7 | Federation auth + tunnel | Done |
| 8 | Federation bind | Done |
| 10 | Persistent binding state | Done |
| 11 | Multi-user collaboration | Done |
| 12 | Reports tree-first | Done |
| 13 | YARG export | Done |
| 14 | Tree-first catalogs | Done |
| 15 | Lab Training Package | Done |
| 16 | Platform evolution (REQ-FW) | Done |
| 17 | Post-baseline hardening | Done |

</details>

<details>
<summary>Phase 18 — Frontend e2e</summary>

| ID | Назначение | BL | Статус |
| -- | ---------- | -- | ------ |
| 18.1 | Playwright admin e2e | BL-50 | Done |
| 18.2 | Driver stub promotion | BL-26 | Done |

</details>

<details>
<summary>Phase 19 — Web Console i18n</summary>

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| 19.1 | react-i18next en/ru/de/zh | Done |
| 19.2 | Shell i18n | Done |
| 19.3 | Inspectors, editors i18n | Done |
| 19.4 | Operator HMI i18n | Done |
| 19.5 | LocaleSwitcher | Done |
| 19.6 | `npm run i18n:check` | Done |

</details>

<details>
<summary>Phase 20 — Code audit (UI, drivers, scale)</summary>

| ID | Назначение | BL | Статус |
| -- | ---------- | -- | ------ |
| 20.1 | Correlator actions UI | BL-01 | Done |
| 20.2 | Workflow actions UI | BL-02 | Done |
| 20.3 | RECORD inline editor | BL-03 | Done |
| 20.4 | Change Sets UI | BL-04 | Done |
| 20.5 | Edit lease indicator | BL-05 | Done |
| 20.6 | Chart types cleanup | BL-06 | Done |
| 20.7 | i18n widget types | BL-07 | Done |
| 20.8 | Event Catalog viewer | BL-08 | Done |
| 20.9 | Binding expression builder | BL-09 | Done |
| 20.10 | network-graph, gantt, history-table | BL-10…12 | Done |
| 20.11 | System settings toggles | BL-13 | Done |
| 20.12 | Automation index + journals | BL-14…16 | Done |
| 20.13 | Driver write Modbus/S7/OPC UA | BL-20…22 | Done |
| 20.14 | Driver write BACnet/IEC104/DNP3/DLMS | BL-23…25 | Done |
| 20.15 | Driver maturity + write UI | BL-27,28,30 | Done |
| 20.16 | ClickHouse variable history | BL-40 | Done |
| 20.17 | Redis/NATS health | BL-41…43 | Done |
| 20.18 | Notifications, federation, backup | BL-44…48 | Done |
| 20.19 | Playwright e2e | BL-50 | Done |
| 20.20 | Operator manifest + spreadsheet | BL-51…54 | Done |
| 20.21 | Frontend component tests | BL-55 | Done |
| 20.22 | Haystack/Brick semantic | BL-56…62 | Done |
| 20.23 | Chart range | BL-63 | Done |
| 20.24 | Chart candlestick | BL-64 | Done |
| 20.25 | Chart bubble/radar | BL-65 | Done |

</details>

<details>
<summary>Phase 21 — Time & timezones</summary>

| ID | Назначение | BL | Статус |
| -- | ---------- | -- | ------ |
| 21.1 | ADR-0020 | BL-66 | Done |
| 21.2 | User timezone | BL-67 | Done |
| 21.3 | Device timezone | BL-68 | Done |
| 21.4 | Historian observedAt | BL-69 | Done |
| 21.5 | Calendar-boundary queries | BL-70 | Done |
| 21.6 | Event occurredAt | BL-71 | Done |

</details>

<details>
<summary>Phase 22 — Tail & AI hardening</summary>

| ID | Назначение | BL | Статус |
| -- | ---------- | -- | ------ |
| 22.1 | Admin shell responsive | BL-72 | Done |
| 22.2 | Reports reportTimeZone | BL-73 | Done |
| 22.3 | Driver observedAt pilots | BL-74 | Done |
| 22.4 | AI rate limits | BL-75 | Done |
| 22.5 | i18n tails | BL-76 | Done |
| 22.6 | Playwright live docs | BL-77 | Done |
| 22.7 | ClickHouse prod rollout | — | Ops |
| 22.8 | Doc sync | — | Done |

</details>

---

## Phase 23 — Platform Excellence (REQ-EX)

Стратегическая волна: leadership vs Ignition, AWS/Azure IoT, ThingsBoard, SkySpark. Delivery — спринты [S01–S23](#реестр-спринтов).

### Матрица осей

| Ось | Track | BL | Готовность | Цель |
| --- | ----- | -- | ---------- | ---- |
| Object model | EX-MODEL | — | Done | Tree-first |
| SCADA HMI | EX-HMI | 86–95 | Partial | 60fps, PWA, a11y |
| Driver depth | EX-DRIVER | 78–85 | Done | Top-10 PRODUCTION |
| Workflow / MES | EX-MES | 121–124 | Planned | OEE, timers |
| App platform | EX-APP | 96–100 | Done | Marketplace |
| Edge + federation | EX-FED | 117–120 | Partial | Sync + chaos |
| Telemetry scale | EX-SCALE | 111–116 | Partial | Ingress, CH prod |
| Horizontal cluster | EX-CLUSTER | 133–139 | Done | Active-active HA |
| AI engineering | EX-AI | 106–110 | Done | Safe mutate, SLO |
| Semantic / BMS | EX-SEM | 101–105 | Partial | Haystack query |
| QA / trust | EX-QA | 129–132 | Done | Live e2e |
| Open / self-host | EX-OPS | 127–128 | Done | One-click, air-gap |

### Темы Phase 23

| ID | Назначение | Track | BL | Sprint | Статус |
| -- | ---------- | ----- | -- | ------ | ------ |
| 23.1 | Driver production matrix | EX-DRIVER | 78–79, 85 | S01, S08, S13 | Done |
| 23.2 | OPC UA + BACnet discovery | EX-DRIVER | 80–81 | S08, S09 | Done |
| 23.3 | Quality + interop CI | EX-DRIVER | 82–84 | S08, S10 | Done |
| 23.4 | Alarm shelving + ack | EX-HMI | 86–88 | S01, S07 | Done |
| 23.5 | Trends + PWA + offline | EX-HMI | 89–91 | S02, S11 | Done |
| 23.6 | Mimic perf + a11y + Lighthouse | EX-HMI | 92–95 | S21 | Partial |
| 23.7 | Solution catalog + semver | EX-APP | 96–98 | S04 | Done |
| 23.8 | Reference app + signing | EX-APP | 99–100 | S04, S14 | Done |
| 23.9 | Haystack query + auto-bind | EX-SEM | 101–103 | S05 | Done |
| 23.10 | Brick inference + roundtrip | EX-SEM | 104–105 | S23 | Planned |
| 23.11 | AI approval + audit | EX-AI | 106–108 | S03, S12 | Done |
| 23.12 | Operator allowlist + SLO | EX-AI | 109–110 | S03, S13 | Done |
| 23.13 | Demand-driven pub/sub + load gate | EX-SCALE | 111, 113 | S06, S07, HF01 | Done |
| 23.14 | CH playbook + cluster | EX-SCALE/CLUSTER | 114–115, 133–139 | S18 | Done |
| 23.15 | Historian dual-write | EX-SCALE | 116 | — | Planned |
| 23.16 | Store-forward + peer health | EX-FED | 117–118 | S17 | Done |
| 23.17 | Selective sync + chaos | EX-FED | 119–120 | S22 | Done |
| 23.18 | OEE + BPMN timers | EX-MES | 121–123 | — | Planned |
| 23.19 | ISA-95 docs | EX-MES | 124 | — | Planned |
| 23.20 | Tenant isolation + quotas | EX-OPS | 125–126 | — | Planned |
| 23.21 | One-click prod + air-gap | EX-OPS | 127–128 | S14, S15 | Done |
| 23.22 | Playwright live operator | EX-QA | 129–130 | S02 | Done |
| 23.23 | Visual regression + i18n gate | EX-QA | 131–132 | S16 | Done |
| 23.24 | Horizontal active-active | EX-CLUSTER | 133–139 | S18 | In progress |

### Горизонты инвестиций

| Горизонт | Фокус | Sprint / BL |
| -------- | ----- | ----------- |
| 1 Trust | Drivers, alarming, cluster, CH ops | S01–S18, BL-114 |
| 2 Velocity | App platform, semantic, AI | S03–S05, S23 |
| 3 Scale & federation | Ingress, federation, MES | S17, S22, BL-121…126 |
| Постоянно | QA, HMI tails, tree-first | S16, S21, BL-92…95 |

---

## Реестр BL-01…139

| ID | Назначение | P | Sprint | Статус |
| -- | ---------- | - | ------ | ------ |
| BL-01 | Correlator actions SET_VARIABLE, OPEN_OPERATOR_REPORT | P0 | Phase 20 | Done |
| BL-02 | Workflow actions log, publishNats | P0 | Phase 20 | Done |
| BL-03 | DataRecordValueEditor inline | P1 | Phase 20 | Done |
| BL-04 | Platform Change Sets UI | P1 | Phase 20 | Done |
| BL-05 | Edit lease indicator | P1 | Phase 20 | Done |
| BL-06 | Chart types cleanup | P1 | Phase 20 | Done |
| BL-07 | i18n widget types + binding hints | P1 | Phase 20 | Done |
| BL-08 | Application Event Catalog viewer | P1 | Phase 20 | Done |
| BL-09 | Binding expression builder | P2 | Phase 20 | Done |
| BL-10 | network-graph layout | P2 | Phase 20 | Done |
| BL-11 | gantt-chart interactive | P2 | Phase 20 | Done |
| BL-12 | history-table window | P2 | Phase 20 | Done |
| BL-13 | System settings toggles | P2 | Phase 20 | Done |
| BL-14 | Automation index dashboard | P2 | Phase 20 | Done |
| BL-15 | Object change history diff | P2 | Phase 20 | Done |
| BL-16 | Journal export CSV/JSON | P2 | Phase 20 | Done |
| BL-17 | Alert/correlator list view | P3 | Phase 20 | Done |
| BL-18 | Binding activators editor | P2 | Phase 20 | Done |
| BL-20 | Write path Modbus | P1 | Phase 20 | Done |
| BL-21 | Write path S7 | P1 | Phase 20 | Done |
| BL-22 | Write path OPC UA | P1 | Phase 20 | Done |
| BL-23 | Write path BACnet, IEC 104 | P2 | Phase 20 | Done |
| BL-24 | DNP3 Class poll | P2 | Phase 20 | Done |
| BL-25 | DLMS write | P2 | Phase 20 | Done |
| BL-26 | Stub promotion | P3 | Phase 18 | Done |
| BL-27 | DriverMaturityRegistry | P2 | Phase 20 | Done |
| BL-28 | Driver write UI | P2 | Phase 20 | Done |
| BL-29 | CWMP write | P3 | Phase 20 | Done |
| BL-30 | Loopback tests drivers | P2 | Phase 20 | Done |
| BL-40 | ClickHouse variable history | P2 | Phase 20 | Done |
| BL-41 | Redis health UI | P2 | Phase 20 | Done |
| BL-42 | NATS JetStream UI | P2 | Phase 20 | Done |
| BL-43 | YARG PDF hint | P2 | Phase 20 | Done |
| BL-44 | Notification webhook/email | P3 | Phase 20 | Done |
| BL-45 | Federation catalog sync UI | P3 | Phase 20 | Done |
| BL-46 | Federation dashboard write | P3 | Phase 20 | Done |
| BL-47 | Platform backup/restore | P3 | Phase 20 | Done |
| BL-48 | MCP admin UI | P3 | Phase 20 | Done |
| BL-50 | Playwright admin e2e | P1 | Phase 18 | Done |
| BL-51 | Operator manifest screens | P3 | Phase 20 | Done |
| BL-52 | Operator responsive | P3 | Phase 20 | Done |
| BL-53 | Spreadsheet Excel functions | P2 | Phase 20 | Done |
| BL-54 | Spreadsheet history binding | P3 | Phase 20 | Done |
| BL-55 | Frontend vitest + RTL | P2 | Phase 20 | Done |
| BL-56 | ADR Haystack overlay | P3 | Phase 20 | Done |
| BL-57 | haystack-metadata-v1 | P3 | Phase 20 | Done |
| BL-58 | Haystack export API | P3 | Phase 20 | Done |
| BL-59 | Driver Haystack tags | P3 | Phase 20 | Done |
| BL-60 | Brick Schema overlay | P3 | Phase 20 | Done |
| BL-61 | ispf-driver-haystack | P3 | Phase 20 | Done |
| BL-62 | Dashboard auto-bind tags | P3 | Phase 20 | Done |
| BL-63 | Chart range band | P2 | Phase 20 | Done |
| BL-64 | Chart candlestick | P2 | Phase 20 | Done |
| BL-65 | Chart bubble/radar | P3 | Phase 20 | Done |
| BL-66 | ADR time & timezones | P2 | Phase 21 | Done |
| BL-67 | User timeZone | P2 | Phase 21 | Done |
| BL-68 | Device timeZone | P2 | Phase 21 | Done |
| BL-69 | Historian observedAt | P3 | Phase 21 | Done |
| BL-70 | Calendar-boundary queries | P3 | Phase 21 | Done |
| BL-71 | Event occurredAt | P3 | Phase 21 | Done |
| BL-72 | Admin shell responsive | P2 | Phase 22 | Done |
| BL-73 | Reports reportTimeZone | P2 | Phase 22 | Done |
| BL-74 | Driver observedAt pilots | P3 | Phase 22 | Done |
| BL-75 | AI rate limits | P2 | Phase 22 | Done |
| BL-76 | i18n tails | P3 | Phase 22 | Done |
| BL-77 | Playwright live testids | P2 | Phase 22 | Done |
| BL-78 | ADR Driver Production Matrix | P1 | S01 | Done |
| BL-79 | observedAt rollout | P1 | S01 | Done |
| BL-80 | OPC UA discovery + subscribe | P1 | S08 | Done |
| BL-81 | BACnet Who-Is | P2 | S09 | Done |
| BL-82 | Quality flags | P2 | S10 | Done |
| BL-83 | Driver interop CI matrix | P2 | S08 | Done |
| BL-84 | Point mapping validation UI | P2 | S08 | Done |
| BL-85 | Top-10 PRODUCTION gate | P1 | S08, S13 | Done |
| BL-86 | Alarm shelving | P1 | S01, S07 | Done |
| BL-87 | Priority + ack + flood | P1 | S01, S07 | Done |
| BL-88 | Operator alarm bar polish | P2 | S02 | Done |
| BL-89 | Trend client | P1 | S02 | Done |
| BL-90 | Operator PWA shell | P1 | S02, S11, S16 | Done |
| BL-91 | Offline cache | P2 | S11 | Done |
| BL-92 | SCADA mimic 60fps | P2 | S21 | Done |
| BL-93 | Accessibility WCAG partial | P2 | S21 | Done |
| BL-94 | SCADA symbol library | P3 | S21 | Partial |
| BL-95 | Operator Lighthouse CI gate | P2 | S21 | Done |
| BL-96 | Solution catalog UI | P1 | S04 | Done |
| BL-97 | Bundle semver | P1 | S04 | Done |
| BL-98 | Integrator CI template | P2 | S04 | Done |
| BL-99 | Third reference app | P1 | S04 | Done |
| BL-100 | Bundle RSA signing | P3 | S14 | Done |
| BL-101 | ADR Haystack query | P2 | S05 | Done |
| BL-102 | API haystack/query | P2 | S05 | Done |
| BL-103 | Dashboard auto-bind query | P2 | S05 | Done |
| BL-104 | Brick class inference | P3 | S23 | Planned |
| BL-105 | Semantic roundtrip test | P3 | S23 | Planned |
| BL-106 | Mutating tools approval | P1 | S03 | Done |
| BL-107 | Agent audit export | P2 | S12 | Done |
| BL-108 | Reference scenario catalog | P1 | S03 | Done |
| BL-109 | Operator agent allowlist | P2 | S13 | Done |
| BL-110 | Agent SLO dashboard | P2 | S03 | Done |
| BL-111 | Demand-driven pub/sub | P2 | S06 | Done |
| BL-112 | MQTT ingress sidecar | P2 | S06 | Cancelled |
| BL-113 | CI load test gate | P2 | S07 | Done |
| BL-114 | ClickHouse prod playbook | P1 | S01 | Partial |
| BL-115 | Horizontal scale epic | P1 | S18 | Done |
| BL-116 | Historian dual-write | P3 | — | Planned |
| BL-117 | Edge store-and-forward | P2 | S17 | Done |
| BL-118 | Federation peer health | P2 | S17 | Done |
| BL-119 | Selective subtree sync | P3 | S22 | Done |
| BL-120 | Federation chaos tests | P3 | S22 | Done |
| BL-121 | OEE reference pattern | P2 | — | Planned |
| BL-122 | BPMN timer boundary | P2 | — | Planned |
| BL-123 | Escalation workflow templates | P3 | — | Planned |
| BL-124 | ISA-95 catalog docs | P3 | — | Planned |
| BL-125 | Tenant isolation | P3 | — | Planned |
| BL-126 | Per-tenant quotas | P3 | — | Planned |
| BL-127 | One-click prod deploy | P1 | S14 | Done |
| BL-128 | Air-gap deployment | P2 | S15 | Done |
| BL-129 | Playwright live operator | P1 | S02 | Done |
| BL-130 | Scheduled staging e2e | P2 | S02 | Done |
| BL-131 | Visual regression smoke | P3 | S16 | Done |
| BL-132 | i18n hardcoded gate | P2 | S16 | Done |
| BL-133 | ADR horizontal cluster | P1 | S18 | Done |
| BL-134 | Multi-replica compose | P1 | S18 | Done |
| BL-135 | Nginx RR + WS failover | P1 | S18 | Done |
| BL-136 | Driver cluster ownership | P1 | S18 | Done |
| BL-137 | Cluster failover tests | P2 | S18 | Done |
| BL-138 | Scale-out load gate 1.8× | P2 | S18 | Done |
| BL-139 | Cluster ops API + runbook | P2 | S18 | Done |
| BL-140 | ADR-0029 live variable replica sync | P1 | S19 | Done |
| BL-141 | Redis cluster WS path interest | P1 | S19 | Done |
| BL-142 | Cluster live-sync integration test | P2 | S19 | Done |
| BL-143 | Cluster config/structure replica sync + smoke `--config-sync` | P1 | S19 | Done |
| BL-144 | Cluster replica roles + platform job queue (async reports) | P1 | S19 | Done |
| BL-145 | Replica profiles + capabilities (ADR-0032) | P1 | S19 | Done |
| BL-146 | Metrics UI load diagnostics (cluster CPU + intra-node drill-down) | P1 | S19 | Done |

**Правило:** закрытие BL-XX → обновить эту таблицу + реестр спринтов + PR.

---

## Детали спринтов

### Sprint S01 — Trust drivers + alarm

| BL | Статус |
| -- | ------ |
| BL-78, 79, 86, 87, 114 | Done |

### Sprint S02 — Operator HMI

| BL | Статус |
| -- | ------ |
| BL-89, 90, 88, 129, 130 | Done |

### Sprint S03 — AI production

| BL | Статус |
| -- | ------ |
| BL-106, 108, 110 | Done |

### Sprint S04 — App velocity

| BL | Статус |
| -- | ------ |
| BL-96…99 | Done |

### Sprint S05 — Semantic

| BL | Статус |
| -- | ------ |
| BL-101…103 | Done |

### Sprint S06 — Scale spike

| BL | Статус |
| -- | ------ |
| BL-111 | Done |
| BL-112 | Cancelled |

### Sprint S07 — Trust + load gate

| BL | Статус |
| -- | ------ |
| BL-86, 87, 113 | Done |

### Sprint S08 — Driver production

| BL | Статус |
| -- | ------ |
| BL-80, 83, 84, 85 | Done |

### Sprint S09 — BACnet

| BL | Статус |
| -- | ------ |
| BL-81 | Done |

### Sprint S10 — Telemetry quality

| BL | Статус |
| -- | ------ |
| BL-82 | Done |

### Sprint S11 — Operator offline

| BL | Статус |
| -- | ------ |
| BL-91, 90 | Done |

### Sprint S12 — Audit + driver UX

| BL | Статус |
| -- | ------ |
| BL-107 | Done |

### Sprint S13 — Production gates

| BL | Статус |
| -- | ------ |
| BL-85, 109, 90 | Done |

### Sprint S14 — Bundle trust + prod

| BL | Статус |
| -- | ------ |
| BL-100, 127 | Done |

### Sprint S15 — Air-gap

| BL | Статус |
| -- | ------ |
| BL-128 | Done |

### Sprint S16 — QA close-out

| BL | Статус |
| -- | ------ |
| BL-90, 131, 132 | Done |

### Sprint S17 — Federation edge

| BL | Статус |
| -- | ------ |
| BL-117, 118 | Done |

### Sprint S18 — Horizontal cluster

| BL | Назначение | Статус |
| -- | ---------- | ------ |
| BL-133 | ADR-0028 active-active | Done |
| BL-134 | docker-compose ×3 + nginx + smoke CI | Done |
| BL-135 | nginx RR + WS failover (`least_conn`, `proxy_next_upstream`) | Done |
| BL-136 | DriverOwnershipService + kill-owner smoke | Done |
| BL-137 | ClusterFailoverIntegrationTest | Done |
| BL-138 | cluster-scale-load-test.py + workflow gate | Done |
| BL-139 | ClusterHealthCard + ops checklist | Done |

Lab: `deploy/cluster-smoke-test.sh`, `deploy/cluster-scale-load-test.py`, `deploy/run_lab_cluster_full_test.py`.

### HF01 — Elastic ingress

| | |
| --- | --- |
| **Версия** | 0.9.87 |
| **Результат** | ~1878 events/s sustained (было ~384/s) |
| **ADR** | [0026](decisions/0026-elastic-telemetry-ingress.md), [0027](decisions/0027-event-journal-ingress-fast-path.md) |
| **Bench** | [LOAD_TESTING.md](LOAD_TESTING.md) |

| Слой | Компонент | Env |
| ---- | --------- | --- |
| L0 | MQTT callback elastic 4→32 | `ISPF_DRIVER_MQTT_CALLBACK_*` |
| L1 | DriverIngressBuffer | `ISPF_DRIVER_INGRESS_BUFFER_*` |
| L5′ | EventJournalAsyncWriter | `ISPF_EVENT_JOURNAL_ELASTIC_*` |

### Sprint S19 — Calibration (acceleration)

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| S19-01 | Baseline метрик CI/PR/Lighthouse/FPS | Done |
| S19-02 | Scorecard + weekly cadence | Done |
| S19-03 | Scope freeze 8 недель | Done |

Артефакт: [ACCELERATION_PROGRAM.md](ACCELERATION_PROGRAM.md), `tools/acceleration/collect-baseline.py`.

### Sprint S20 — Speed Engine

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| S20-01 | pr-fast / nightly-full | Done |
| S20-02 | Path-based jobs | Done |
| S20-03 | Gradle/npm cache | Done |
| S20-04 | Flaky triage policy | Done — [CI_FLAKY_TRIAGE.md](CI_FLAKY_TRIAGE.md) |
| S20-05 | Clean build artifacts | Done |
| S20-06 | CI observability dashboard | Done — [CI_DASHBOARD.md](CI_DASHBOARD.md), `tools/acceleration/ci-dashboard.py` |

### Sprint S21 — HMI Moat

| ID | Назначение | BL | Статус |
| -- | ---------- | -- | ------ |
| S21-01 | Mimic perf profiling | BL-92 | Done — `e2e/fixtures/stressMimic.ts`, quality gate |
| S21-02 | Mimic render optimizations | BL-92 | Done — `MemoMimicElementNode`, memo connections, useMemo layers |
| S21-03 | Lighthouse CI gate | BL-95 | Done — `scripts/lighthouse-ci.mjs` |
| S21-04 | A11y baseline axe | BL-93 | Done — `e2e/quality-gates.spec.ts` |
| S21-05 | Bundle size budget | BL-95 | Done — `scripts/bundle-budget.mjs` |
| S21-06 | Known gaps doc | BL-93, 94 | Done — [HMI_QUALITY_GATES.md](HMI_QUALITY_GATES.md) |

### Sprint S22 — Edge Trust

| ID | Назначение | BL | Статус |
| -- | ---------- | -- | ------ |
| S22-01 | Selective sync API draft | BL-119 | Done — [FEDERATION.md § Selective subtree sync](FEDERATION.md#selective-subtree-sync-bl-119-s22) |
| S22-02 | Selective sync backend | BL-119 | Done — `subtree-sync-preview`, `sync-subtree` |
| S22-03 | Explorer sync UI | BL-119 | Done — peers panel + Explorer federation tab |
| S22-04 | Federation chaos tests | BL-120 | Done — `FederationChaosIntegrationTest` |
| S22-05 | Recovery runbook + SLO | BL-120 | Done — [FEDERATION.md § Recovery runbook](FEDERATION.md#recovery-runbook--slo-bl-120-s22-05) |

### Sprint S23 — Differentiation

| ID | Назначение | BL | SP |
| -- | ---------- | -- | -- |
| S23-01 | Brick class inference | BL-104 | 5 |
| S23-02 | Inspector suggestions | BL-104 | 5 |
| S23-03 | Semantic roundtrip test | BL-105 | 8 |
| S23-04 | Demo dashboard <5 min | BL-105 | 5 |
| S23-05 | KPI time-to-first-dashboard | — | 3 |

**Acceleration Go/No-Go:** CI ±10%; HMI gates green 2 нед.; federation chaos green; semantic demo ≤5 min.

---

## Готовность подсистем

| Подсистема | % | Главный пробел | Sprint / BL |
| ---------- | - | -------------- | ----------- |
| REQ-PF / REQ-FW | 100% | — | — |
| UI ↔ API parity | 100% | — | Phase 20 |
| Driver top-10 PRODUCTION | 100% | stub promotion по demand | BL-26 |
| Operator alarming / PWA | 95% | mimic, a11y | S21, BL-92…95 |
| App marketplace | 98% | — | S04 |
| AI agent production | 98% | — | S03 |
| Haystack query | 85% | inference, roundtrip | S23, BL-104…105 |
| Telemetry ingress | 90% | staging load job | HF01, S07 |
| Horizontal cluster | 100% | — | S18, BL-133…139 |
| Federation edge | 90% | sync, chaos | S22, BL-119…120 |
| ClickHouse variables | 95% | prod rollout | BL-114, Ops |
| Frontend e2e | 95% | Lighthouse | S21 |

---

## Platform baseline

| ID | Назначение | Статус |
| -- | ---------- | ------ |
| P.1 | Java 25 toolchain + CI | Done |
| P.2 | Spring Boot 4.0.7 | Done |
| P.3 | Jackson 3 native | Done |

---

## Связанные документы

| Документ | Назначение |
| -------- | ---------- |
| [APPLICATIONS.md](APPLICATIONS.md) | Deploy API, REQ-PF |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Prod, cluster runbook |
| [DRIVERS.md](DRIVERS.md) | Driver catalog |
| [FEDERATION.md](FEDERATION.md) | Edge federation |
| [LOAD_TESTING.md](LOAD_TESTING.md) | Load gates |
| [PLATFORM_EVOLUTION.md](PLATFORM_EVOLUTION.md) | История |
| [decisions/](decisions/) | ADR |

---

## История

| Дата | Изменение |
| ---- | --------- |
| 2026-07-05 | S20 Done + S21 gates: pr-fast/nightly, CI dashboard, Lighthouse/axe/bundle/mimic FPS |
| 2026-07-05 | S18 Done: cluster-smoke-test, scale gate 1.8×, nginx failover, ops checklist |
| 2026-07-05 | Единый стиль + нумерация спринтов S01–S23; EX-NN → SN; EX0–4 → S19–23 |
| 2026-07-05 | Объединение backlog-файлов в ROADMAP.md |
| 2026-07-05 | Code audit sync: BL-80/83/84/133/137 Done; cluster ~55% |
| 2026-07-03 | S17: BL-117 store-forward; BL-118 peer health |
| 2026-07-03 | S16: BL-131 visual regression; BL-132 i18n gate |
| 2026-06-30 | Phase 23 REQ-EX; Phase 21–22 Done |
