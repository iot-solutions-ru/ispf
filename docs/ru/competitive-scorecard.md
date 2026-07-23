> **Язык:** русская версия (вычитка). Канонический английский: [en/competitive-scorecard.md](../en/competitive-scorecard.md).

# Конкурентный scorecard ISPF (BL-189)

> **Статус:** Stable — Готовность по коду. Теги: [doc-status](../en/doc-status.md).

Матрица публичной готовности для Phases 25–33 (фазы 25–33).

**Версии (не смешивать):**

| Pin | Смысл |
|-----|--------|
| **0.9.102** | Последний **полный code audit** этой матрицы (июль 2026). Оценки и evidence ниже зафиксированы на этом аудите. |
| **0.9.105+** | Типичная метка текущего `main` / prod demostand в roadmap и deploy. **Не** пересчитанная матрица. |

**Актуальный столбец:** **Проверено по коду** — доказательства из `main` на baseline аудита **0.9.102**.

Шкала **1–10** относительно лучших в классе: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, зрелые IIoT-платформы с контекстным деревом.

| # | Измерение | Baseline (фаза 24) | **Проверено по коду (0.9.102)** | Цель | Фаза / BL |
|---|-----------|:-------------------:|:-------------------------------:|:------:|------------|
| 1 | Единая модель данных (дерево объектов) | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#этап-25--ot-trust), [roadmap](roadmap.md#этап-29--платформа-mes), [roadmap](roadmap.md#этап-30--глубина-автоматизации) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / мнемосхемы | 7.0 | **7.5** | **10** | [roadmap](roadmap.md#этап-26--совершенство-hmi) — BL-146…152 |
| 3 | OT/IT connectivity (drivers.md) | 6.0 | **6.5** | **10** | [roadmap](roadmap.md#этап-25--ot-trust) — BL-140…145 |
| 4 | Historian / time-series | 7.0 | **7.0** | **10** | [roadmap](roadmap.md#фаза-28--историк-в-масштабе) — BL-159…163; [roadmap](roadmap.md#фаза-33--аналитическая-платформа-af-capable) БЛ-200…210 |
| 5 | Automation / alarms | 7.5 | **7.5** | **10** | [roadmap](roadmap.md#этап-27--безопасность-предприятия), [roadmap](roadmap.md#этап-30--глубина-автоматизации) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.5** | **10** | [roadmap](roadmap.md#этап-30--глубина-автоматизации) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.5** | **10** | [roadmap](roadmap.md#этап-29--платформа-mes) — BL-164…170 |
| 8 | Скорость low-code | 8.0 | **8.0** | **10** | [roadmap](roadmap.md#этап-26--совершенство-hmi), [roadmap](roadmap.md#фаза-31--автопилот-ии) — BL-146…152, BL-177…180 |
| 9 | AI-assisted разработка | 9.0 | **9.0** | **10** | [roadmap](roadmap.md#фаза-31--автопилот-ии) — BL-177…182 |
| 10 | Security / RBAC / мультитенантность | 6.5 | **8.0** | **10** | [roadmap](roadmap.md#этап-27--безопасность-предприятия) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.0** | **10** | [roadmap](roadmap.md#этап-25--ot-trust), [roadmap](roadmap.md#фаза-28--историк-в-масштабе), [roadmap](roadmap.md#этап-32--экосистема-и-рынок) — BL-144…145, BL-186…187 |
| 12 | Экосистема / маркетплейс | 4.0 | **5.0** | **10** | [roadmap](roadmap.md#этап-32--экосистема-и-рынок) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [roadmap](roadmap.md#этап-32--экосистема-и-рынок) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, опция ClickHouse |

**Итого (проверено по коду): ~7.6/10** — простое среднее по 14 измерениям.

**Post-audit delta (19.07.2026):** AI-assisted разработка **7.0 → 8.5** после полного live suite (БЛ-178: 52/52 @100%), затем **8.5 → 9.0** после БЛ-177/180 multi-app / multi-domain live smoke harness Готово (opt-in `ISPF_LLM_SMOKE`; без выдуманных multi-app/multi-domain live pass counts). Остальные измерения — baseline аудита **0.9.102**.

**Program wave 8 (июль 2026)** ранее заявляла ~9.8/10 по отгруженным BL-артефактам; **аудит кода (0.9.102)** заменяет эту цифру. См. [§ Доказательства аудита кода](#code-audit-evidence-0102) и [§ Разрыв до цели](#gaps-to-target).

---

## Definition of done (10/10 overall)

Из [roadmap](roadmap.md) (Фазы 25–33 / DoD). Статус на **0.9.102**:

| Критерий | Статус |
|----------|--------|
| Все **14 измерений ≥9.5**, ни одно ≤8 (BL-189) | **Не выполнено** — среднее ~7.6; максимум 9.5 (stack modernity) |
| Agent regression **≥95% green** с live LLM (BL-178) | **Выполнено** — полный live suite `AGENT_LIVE_SUITE_MODE=full` через `run-live-suite.sh`: **52/52 @100%** (`build/agent-regression/live-suite-results.json`, ~2026-07-18/19). Nightly CI по-прежнему режим **platform**. `nightly-stub-results.json` **устарел** (не proof) |
| Marketplace GA checklist complete (BL-183) | **Выполнено** — browse/install/sign/version + честные partner multi-endpoint (11) + CI catalog gate (12); BL-184/185 Done; Partner Portal external |
| Competitive scorecard published per release (BL-189) | **Выполнено** — этот документ |

---

## Доказательства аудита кода (0.9.102)

Классы доказательств: **REAL** (runtime + тесты), **PARTIAL** (ядро работает, известные пробелы), **STUB** (явная заглушка в коде).

| # | Измерение | Оценка | Вердикт | Ключевые доказательства |
|---|-----------|:-----:|---------|-------------------------|
| 1 | Unified data model | 8.5 | **REAL** | Object tree, CEL, blueprints; `AlertRuleListener` → `AlertRuleService`; correlators в `EventCorrelatorService` |
| 2 | SCADA / HMI | 7.5 | **REAL** | `ispf-pid-v1` manifest `totalSymbols: 218`; `ScadaMimicEditor.tsx`; video wall в `dashboardLayoutPresets.ts`; e2e FPS в `quality-gates.spec.ts` (mocked operator API) |
| 3 | OT/IT drivers | 7.0 | **PARTIAL** | 28 `PRODUCTION` в `DriverProductionMatrix` (честность BL-191; партия A: dnp3, haystack, kafka, coap, icmp, ip-host, telnet, modem-at, ssh, file, folder, application); Milo OPC UA, j2mod, S7, BACnet4J — реальные; `opc-da` / `opc-bridge` / `ethernet-ip` честно **BETA** (оболочки / poll-only); DNP3 `writePoint` по-прежнему throws |
| 4 | Historian | 7.0 | **PARTIAL** | `ClickHouseVariableHistoryStore` HTTP insert/query; JDBC по умолчанию; lab gates BL-210 + JVM multi-tag gate **определены** (`analytics-scale-gate.sh`); оценка **≥9.5** после Enterprise L lab sign-off |
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators **REAL**; alarm shelving approval **persisted** (BL-158) |
| 6 | Workflow / BPMN | 7.5 | **REAL** | `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`; не полная BPMN 2.0 |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | MES — marketplace product (`mes-platform`, vendor IoT Solutions); база не сидит `root.platform.mes`. Bundle JSON/SQL/script BFF; нет отдельного MES-модуля |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy (`MarketplaceLocalBundleService.installLocalBundle`), spreadsheets |
| 9 | AI-assisted dev | 9.0 | **REAL** | БЛ-178 полный live suite **52/52 @100%** (`build/agent-regression/live-suite-results.json`, `mode=full`, ~2026-07-18/19); БЛ-177 multi-app `AgentLiveDeploySmokeTest` matrix (`mes-platform`, `building-hvac`, `platform-primitive`); БЛ-180 multi-domain `AiSolutionGeneratorLiveSmokeTest` (HVAC/MES/SCADA, `composition=primitives`); draft fallback `mode=draft` (не stub); live smoke требуют `ISPF_LLM_SMOKE=true` |
| 10 | Security / RBAC | 8.0 | **PARTIAL** | TOTP MFA GA **Done** (BL-153); per-var/event/function ACL **Done** (BL-154); audit SIEM + role scopes **Done** (BL-156/157); hard tenancy schema+OIDC REAL, A≠B open (BL-155); WebAuthn → BL-194 |
| 11 | Deploy / scale / edge | 7.5 | **PARTIAL** | Federation MoM usable path **REAL** (BL-188); Helm lint/template + ARM edge compose **Готово** (BL-186/187); нет CI load proof для cluster / 10+ peer scale |
| 12 | Ecosystem / marketplace | 6.5 | **PARTIAL** | Marketplace GA **Готово** (BL-183 — multi-endpoint + CI catalog gate); partner directory + enroll `"source": "db"` (BL-184); symbol packs BL-185 Done; Partner Portal sync всё ещё external |
| 13 | Documentation / DX | 8.5 | **REAL** | Docs + ADR; в коде заглушки помечены честно |
| 14 | Stack modernity | 9.5 | **REAL** | Spring Boot 4, React 19, опция ClickHouse в `gradle.properties` / `application.yml` |

### Известные проблемы целостности кода (исправить до повышения OT-оценки)

| Проблема | Где |
|----------|-----|
| DNP3 poll-only (BETA) — `writePoint` не реализован | `Dnp3DeviceDriver.writePoint()` — maturity честный; write открыт |
| Partner Portal external | `PartnerProgramService` persists directory/enroll (`source=db`); portal sync вне repo |

---

## Разрыв до цели

Приоритетные исправления для движения **проверено по коду** к 10/10 (не маркетинг). Полный аудит доменов: [roadmap.md § Аудит доменов](roadmap.md#аудит-доменов--iot--scada--mes--erp-09072026).

1. **OT drivers (7.0 → 9+):** честность матрицы **закрыта (БЛ-191)**; остаётся field pilot sign-off + DNP3 write / полный DA после **именованной полевой задачи** (БЛ-140 Частичный).
2. **ERP L4 / MES (6.5 → 9+):** живой коннектор 1C или SAP (**БЛ-169** P0); production MES sites. Genealogy lite (**БЛ-193**) отгружен в `mes-platform` (seed-граф + Operator report).
3. **AI (9.0 → 10):** БЛ-177…180 Готово (harness + БЛ-178 52/52); soft-budget **evidence path** отгружен (`run-live-generator-oneshot.sh` → `live-generator-results.json`, чеклист в [ai-agent](ai-agent.md)). Остаётся: датированный реальный oneshot с `softBudgetMet: true` + journal field soak на именованной площадке (lab oneshot ≠ field Done). Полный suite on-demand (`AGENT_LIVE_SUITE_MODE=full`).
4. **Ecosystem (6.5 → 9+):** sync Partner Portal + live partner-hosted catalogs (вне repo); повышение после первого внешнего partner catalog onboarding.
5. **Historian (7.0 → 9+):** прогнать Enterprise L lab gates (`deploy/local/tools/analytics-scale-gate.sh`, catalog 50k, CH 1B) — BL-210; затем обновить scorecard до **≥9.5** с датированным sign-off.
6. **HMI (7.5 → 9+):** FPS gate на live WebSocket mimic; persistence alarm shelving.
7. **Compliance:** tender pack IEC 62443 / GAMP-lite (**БЛ-192**) — **docs Готово:** [compliance-tender-pack](compliance-tender-pack.md) (канон EN). Остаются: pen-test, audit-trail GA (SIEM), hard tenancy A≠B; без заявления о сертификации продукта.

---

## История program wave (отгруженные артефакты)

Исторические **program**-оценки отражали скорость delivery BL; это **не** конкурентная готовность. Сохранено для археологии релизов.

<details>
<summary>Столбцы wave 1–8 (заменены «проверено по коду»)</summary>

| # | Измерение | W1 | W2 | W3 | W4 | W5 | W6 | W7 | W8 (program) |
|---|-----------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:------------:|
| 1 | Unified data model | 9.2 | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.9 |
| 2 | SCADA / HMI | 7.8 | 7.9 | 8.0 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 3 | OT/IT connectivity | 6.8 | 6.9 | 7.0 | 8.5 | 8.7 | 9.3 | 9.5 | 9.7 |
| 4 | Historian | 7.5 | 7.6 | 7.7 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 5 | Automation / alarms | 8.0 | 8.1 | 8.2 | 8.6 | 8.7 | 9.1 | 9.4 | 9.6 |
| 6 | Workflow / BPMN | 7.0 | 7.1 | 7.2 | 8.5 | 8.6 | 9.0 | 9.3 | 9.6 |
| 7 | MES / ISA-95 | 6.2 | 6.4 | 6.6 | 8.5 | 8.8 | 9.5 | 9.6 | 9.7 |
| 8 | Low-code velocity | 8.5 | 8.6 | 8.7 | 8.8 | 9.0 | 9.5 | 9.6 | 9.7 |
| 9 | AI-assisted development | 9.4 | 9.6 | 9.7 | 9.8 | 9.85 | 9.95 | 9.97 | 9.98 |
| 10 | Security / RBAC | 7.2 | 7.3 | 7.4 | 8.5 | 8.8 | 9.3 | 9.5 | 9.7 |
| 11 | Deploy / scale / edge | 8.3 | 8.6 | 9.0 | 9.2 | 9.3 | 9.5 | 9.6 | 9.7 |
| 12 | Ecosystem / marketplace | 5.0 | 5.5 | 6.0 | 8.5 | 8.7 | 9.0 | 9.3 | 9.6 |
| 13 | Documentation / DX | 9.3 | 9.4 | 9.5 | 9.6 | 9.7 | 9.8 | 9.85 | 9.9 |
| 14 | Stack modernity | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.0 | 9.5 |

Wave 8 program mean ~9.8 — **отозвано** после аудита кода 2026-07-08.

</details>

---

## Процесс обновления

1. После каждого release tag — повторный **аудит кода**: матрица drivers, grep stub (`source.*stub`, `mode.*stub`), CI gates, integration tests.
2. Обновить столбец **Проверено по коду** в этом файле; зафиксировать delta в release notes.
3. Program wave columns (если используются) — только shipment BL, **не** замена code verified.
4. Связывать доказательства с тестами и путями в `packages/`, а не только с roadmap.
