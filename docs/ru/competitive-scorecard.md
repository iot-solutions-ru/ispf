> **Язык:** русская версия (вычитка). Канонический английский: [en/competitive-scorecard.md](../en/competitive-scorecard.md).

# Конкурентный scorecard ISPF (BL-189)

Матрица публичной готовности для Excellence Program (фазы 25–33). **Актуальный столбец:** **Проверено по коду** — доказательства из `main`, исходников и тестов (релиз **0.9.102**, июль 2026).

Шкала **1–10** относительно лучших в классе: Ignition, Kepware/KEPServerEX, OSIsoft PI, Siemens Opcenter, Tulip, зрелые IIoT-платформы с контекстным деревом.

| # | Измерение | Baseline (фаза 24) | **Проверено по коду (0.9.102)** | Цель | Фаза / BL |
|---|-----------|:-------------------:|:-------------------------------:|:------:|------------|
| 1 | Единая модель данных (дерево объектов) | 9.0 | **8.5** | **10** | [25](roadmap.md#этап-25--ot-trust), [29](roadmap.md#этап-29--платформа-mes), [30](roadmap.md#этап-30--глубина-автоматизации) — BL-164…168, BL-173 |
| 2 | SCADA / HMI / мнемосхемы | 7.0 | **7.5** | **10** | [26](roadmap.md#этап-26--совершенство-hmi) — BL-146…152 |
| 3 | OT/IT connectivity (drivers.md) | 6.0 | **6.5** | **10** | [25](roadmap.md#этап-25--ot-trust) — BL-140…145 |
| 4 | Historian / time-series | 7.0 | **7.0** | **10** | [28](roadmap.md#фаза-28--историк-в-масштабе) — BL-159…163; [33](roadmap.md#фаза-33--аналитическая-платформа-af-capable) БЛ-200…210 |
| 5 | Automation / alarms | 7.5 | **7.5** | **10** | [27](roadmap.md#этап-27--безопасность-предприятия), [30](roadmap.md#этап-30--глубина-автоматизации) — BL-153…157, BL-171…174 |
| 6 | Workflow / BPMN | 6.5 | **7.5** | **10** | [30](roadmap.md#этап-30--глубина-автоматизации) — BL-176 |
| 7 | MES / ISA-95 | 5.5 | **6.5** | **10** | [29](roadmap.md#этап-29--платформа-mes) — BL-164…170 |
| 8 | Скорость low-code | 8.0 | **8.0** | **10** | [26](roadmap.md#этап-26--совершенство-hmi), [31](roadmap.md#фаза-31--автопилот-ии) — BL-146…152, BL-177…180 |
| 9 | AI-assisted разработка | 9.0 | **6.5** | **10** | [31](roadmap.md#фаза-31--автопилот-ии) — BL-177…182 |
| 10 | Security / RBAC / мультитенантность | 6.5 | **7.5** | **10** | [27](roadmap.md#этап-27--безопасность-предприятия) — BL-153…157 |
| 11 | Deploy / scale / edge | 8.0 | **7.0** | **10** | [25](roadmap.md#этап-25--ot-trust), [28](roadmap.md#фаза-28--историк-в-масштабе), [32](roadmap.md#этап-32--экосистема-и-рынок) — BL-144…145, BL-186…187 |
| 12 | Экосистема / маркетплейс | 4.0 | **5.0** | **10** | [32](roadmap.md#этап-32--экосистема-и-рынок) — BL-183…185 |
| 13 | Documentation / DX | 9.0 | **8.5** | **10** | [32](roadmap.md#этап-32--экосистема-и-рынок) — BL-189, BL-190 |
| 14 | Stack modernity | 9.0 | **9.5** | **10** | maintain — Spring Boot 4, React 19, NATS, опция ClickHouse |

**Итого (проверено по коду): ~7.4/10** — простое среднее по 14 измерениям.

**Program wave 8 (июль 2026)** ранее заявляла ~9.8/10 по отгруженным BL-артефактам; **аудит кода (0.9.102)** заменяет эту цифру. См. [§ Доказательства аудита кода](#code-audit-evidence-0102) и [§ Разрыв до цели](#gaps-to-target).

---

## Definition of done (10/10 overall)

Из [roadmap.md](roadmap.md) (Программа совершенствования / DoD). Статус на **0.9.102**:

| Критерий | Статус |
|----------|--------|
| Все **14 измерений ≥9.5**, ни одно ≤8 (BL-189) | **Не выполнено** — среднее ~7.4; максимум 9.5 (stack modernity) |
| Agent regression **≥95% green** с live LLM (BL-178) | **Не выполнено** — CI валидирует только JSON-схемы; nightly использует `nightly-stub-results.json` |
| Marketplace GA checklist complete (BL-183) | **Частично** — локальная установка bundle реальна; partner/symbol/cert — stub |
| Competitive scorecard published per release (BL-189) | **Выполнено** — этот документ |

---

## Доказательства аудита кода (0.9.102)

Классы доказательств: **REAL** (runtime + тесты), **PARTIAL** (ядро работает, известные пробелы), **STUB** (явная заглушка в коде).

| # | Измерение | Оценка | Вердикт | Ключевые доказательства |
|---|-----------|:-----:|---------|-------------------------|
| 1 | Unified data model | 8.5 | **REAL** | Object tree, CEL, blueprints; `AlertRuleListener` → `AlertRuleService`; correlators в `EventCorrelatorService` |
| 2 | SCADA / HMI | 7.5 | **REAL** | `ispf-pid-v1` manifest `totalSymbols: 218`; `ScadaMimicEditor.tsx`; video wall в `dashboardLayoutPresets.ts`; e2e FPS в `quality-gates.spec.ts` (mocked operator API) |
| 3 | OT/IT drivers | 6.5 | **PARTIAL** | 20 `PRODUCTION` в `DriverProductionMatrix`; Milo OPC UA, j2mod, S7, BACnet4J — реальные; `opc-da` в коде **stub**, но в матрице PRODUCTION; DNP3 `writePoint` throws |
| 4 | Historian | 7.0 | **PARTIAL** | `ClickHouseVariableHistoryStore` HTTP insert/query; JDBC по умолчанию; нет in-repo throughput benchmark уровня PI |
| 5 | Automation / alarms | 7.5 | **PARTIAL** | Alert rules + correlators **REAL**; `AlarmShelfApprovalService` in-memory **STUB** |
| 6 | Workflow / BPMN | 7.5 | **REAL** | `WorkflowEngineSubProcessTest`, `WorkflowEngineMessageTest`; не полная BPMN 2.0 |
| 7 | MES / ISA-95 | 6.5 | **PARTIAL** | `MesPlatformBootstrap` + bundle JSON/SQL/script BFF; `MesPlatformProductionBundleSmokeTest` — нет отдельного MES-модуля |
| 8 | Low-code velocity | 8.0 | **REAL** | Dashboard builder, bundle deploy (`MarketplaceLocalBundleService.installLocalBundle`), spreadsheets |
| 9 | AI-assisted dev | 6.5 | **PARTIAL** | Agent tools мутируют платформу (`AgentE2eDeployIntegrationTest` без LLM); `AiSolutionGeneratorService` → `"mode": "stub"`; `AgentRegressionCiTest` — только схема |
| 10 | Security / RBAC | 7.5 | **PARTIAL** | TOTP MFA + `required-for-admin` **REAL**; `TenantIsolationValidator` **STUB** |
| 11 | Deploy / scale / edge | 7.0 | **PARTIAL** | Federation в `com.ispf.server.federation.*`; Helm skeleton; нет CI load proof для cluster scale |
| 12 | Ecosystem / marketplace | 5.0 | **PARTIAL** | Локальная установка каталога **REAL**; `PartnerProgramService` / `MarketplaceSymbolListingService` → `"source": "stub"` |
| 13 | Documentation / DX | 8.5 | **REAL** | Docs + ADR; в коде заглушки помечены честно |
| 14 | Stack modernity | 9.5 | **REAL** | Spring Boot 4, React 19, опция ClickHouse в `gradle.properties` / `application.yml` |

### Известные проблемы целостности кода (исправить до повышения OT-оценки)

| Проблема | Где |
|----------|-----|
| `opc-da` в PRODUCTION, но driver — stub | `DriverProductionMatrix.java`, `OpcDaDeviceDriver.java` |
| DNP3 PRODUCTION без write | `Dnp3DeviceDriver.writePoint()` |
| Nightly pass rate агента не измеряется | `tools/agent-regression/nightly-stub-results.json`, `run-nightly.sh` |
| Partner enroll синтетический | `PartnerProgramService.java` — hardcoded demo partners |

---

## Разрыв до цели

Приоритетные исправления для движения **проверено по коду** к 10/10 (не маркетинг). Полный аудит доменов: [roadmap.md § Аудит доменов](roadmap.md#аудит-доменов--iot--scada--mes--erp-09072026).

1. **OT drivers (6.5 → 9+):** честность матрицы — `opc-da` stub / DNP3 write (**БЛ-191**); field pilot sign-offs после **именованной полевой задачи** (БЛ-140 Частичный).
2. **ERP L4 / MES (6.5 → 9+):** живой коннектор 1C или SAP (**БЛ-169** P0); production MES sites; genealogy lite (**БЛ-193**).
3. **AI (6.5 → 9+):** live LLM regression в CI (`validate-scenarios.mjs --live --enforce-rate`); убрать stub nightly; реальный solution generator или явно keyword-only.
4. **Ecosystem (5.0 → 9+):** persist partner enrollments; установка symbol pack beyond in-memory stub; внешние signed bundles.
5. **Historian (7.0 → 9+):** lab benchmark + CI gate (БЛ-161); AF-capable analytics plane ([analytics-platform-roadmap.md](analytics-platform-roadmap.md) БЛ-200…210).
6. **HMI (7.5 → 9+):** FPS gate на live WebSocket mimic; persistence alarm shelving.
7. **Compliance:** tender pack IEC 62443 / GAMP-lite (**БЛ-192**).

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
