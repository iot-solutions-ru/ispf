# Sprint backlog ускорения и конкурентного отрыва (8 недель)

Операционный план реализации после аудита REQ-EX. Цель: ускорить delivery, усилить операторский UX и закрепить enterprise-доверие на edge/federation.

Связанные документы:

- [ROADMAP.md § Phase 23](ROADMAP.md#phase-23--platform-excellence-req-ex)
- [EXCELLENCE_BACKLOG.md](EXCELLENCE_BACKLOG.md)
- [CODE_AUDIT_BACKLOG.md](CODE_AUDIT_BACKLOG.md)
- [GAP_REGISTRY.md](GAP_REGISTRY.md)

---

## Hotfix: elastic ingress pipeline (EX-INGRESS-01) — Done (0.9.87)

Цель: убрать фиксированные пулы на L0/L1/L5′ и поднять sustained throughput event-journal path на prod VPS.

| Слой | Компонент | Дефолт | Env |
| --- | --- | --- | --- |
| L0 | MQTT callback / FIFO | elastic 4→32 | `ISPF_DRIVER_MQTT_CALLBACK_*` |
| L1 | `DriverIngressBuffer` (ServerDriverObject) | elastic 2→32 | `ISPF_DRIVER_INGRESS_BUFFER_*` |
| L3 | `TelemetryIngressDispatcher` | elastic 4→32 | уже было |
| L5 | `VariableHistoryAsyncWriter` | elastic 4→32 | уже было |
| L5′ | `EventJournalAsyncWriter` | elastic 4→32 | `ISPF_EVENT_JOURNAL_ELASTIC_*` |

**Результат (VPS prod, 1 device, Scylla 1 SMP):** sustained **~1878 events/s** (65s, 20×10ms emqtt) после elastic; было **~384/s** с fixed L0=4. Journal peak tuning (`deploy/vps-event-journal-peak-tuning.sh`) убрал sync fallback на overload.

Документация: [ADR-0026](decisions/0026-elastic-telemetry-ingress.md), [ADR-0027](decisions/0027-event-journal-ingress-fast-path.md), [LOAD_TESTING.md](LOAD_TESTING.md).

---

## 1) Цели программы и KPI

| KPI | Baseline (замер Sprint 0) | Target к концу Sprint 4 | Владелец |
| --- | --- | --- | --- |
| PR lead time (median) | TBD | -30% | DevOps lead |
| CI duration на PR (p50) | TBD | -30% | DevOps lead |
| Re-run rate (flaky failures) | TBD | -40% | QA lead |
| Operator Lighthouse Performance | TBD | >= 85 | Frontend lead |
| Operator Lighthouse Accessibility | TBD | >= 90 | Frontend lead |
| Mimic FPS (mid-range tablet) | TBD | ~60fps stable | Frontend lead |
| Federation reconnect success | TBD | >= 99% | Platform lead |
| MTTR federation incidents | TBD | -30% | SRE |
| Time-to-first-dashboard (semantic wizard path) | TBD | -40% | Platform + Frontend |

Definition of Success:

- delivery быстрее без роста инцидентов;
- операторский UX проходит performance/a11y gate;
- federation выдерживает fail/reconnect сценарии;
- semantic path сокращает время сборки приложений.

---

## 2) Роли и ownership

| Роль | Ответственность |
| --- | --- |
| Program owner | Приоритеты, scope-control, weekly scorecard |
| DevOps lead | CI/CD скорость, кеши, quality gates |
| Frontend lead | Mimic perf, operator UX, Lighthouse/A11y |
| Platform lead | Federation selective sync + chaos hardening |
| QA lead | Автотесты, стабильность, quality gates |
| SRE | NFR, инциденты, эксплуатационные runbook |

---

## 3) Sprint 0 (2-3 дня): калибровка

Цель: зафиксировать baseline и договориться о правилах принятия решений.

### Бэклог Sprint 0

| ID | Задача | SP | Owner | Acceptance |
| --- | --- | --- | --- | --- |
| EX0-01 | Собрать baseline метрик (CI/PR/flake/Lighthouse/FPS/federation) | 3 | DevOps + QA | Sheet с baseline + источники данных |
| EX0-02 | Утвердить scorecard и weekly review cadence | 1 | Program owner | Формат review и owner каждого KPI |
| EX0-03 | Freeze scope на 8 недель (доп. задачи только через change-control) | 1 | Program owner | Утвержденный scope документ |

---

## 4) Sprint 1 (недели 1-2): Speed Engine

Цель: ускорить цикл разработки, не ломая качество релизов.

Связь с backlog: поддержка BL-113 (load CI), подготовка gates под BL-95.

### Эпики и задачи

| ID | Задача | SP | Owner | Зависимости | Acceptance |
| --- | --- | --- | --- | --- | --- |
| EX1-01 | Разделить CI на `pr-fast` и `nightly-full` | 5 | DevOps | EX0-01 | PR pipeline <= целевого времени; nightly содержит полный прогон |
| EX1-02 | Path-based запуск jobs (backend/web/driver-interop) | 3 | DevOps | EX1-01 | Изменения в web не запускают полный backend suite без необходимости |
| EX1-03 | Gradle/npm cache optimization | 3 | DevOps | EX1-01 | Повторные PR прогоны заметно быстрее baseline |
| EX1-04 | Отдельный flaky triage loop и quarantine policy | 3 | QA lead | EX0-01 | Документирован flow "flake -> owner -> fix SLA" |
| EX1-05 | Clean build artifacts policy (`dist`, local zip) | 2 | Frontend lead | - | Чистый git flow без шумовых артефактов в PR |
| EX1-06 | CI observability dashboard (duration/failure reasons) | 2 | DevOps + SRE | EX1-01 | Dashboard с weekly trend |

Сумма: **18 SP**

### Definition of Done Sprint 1

- median CI и PR lead time улучшены относительно baseline;
- flaky reruns снижены;
- PR-пайплайн предсказуем и быстрее.

### Риски и mitigation

- Риск: скрытая регрессия из-за агрессивного slimming PR checks.  
  Mitigation: nightly-full + обязательный nightly triage.

---

## 5) Sprint 2 (недели 3-4): HMI Moat

Цель: закрыть ключевой продающий разрыв по operator UX.

Связь с backlog: **BL-92**, **BL-93**, **BL-95**.

### Эпики и задачи

| ID | Задача | SP | Owner | Зависимости | Acceptance |
| --- | --- | --- | --- | --- | --- |
| EX2-01 | Mimic perf profiling + hotspot map | 3 | Frontend lead | EX1-01 | Профиль узких мест для live 1Hz сценариев |
| EX2-02 | Оптимизации рендера mimic (debounce/update scheduling/layering) | 8 | Frontend | EX2-01 | Trace без sustained long tasks > 50ms |
| EX2-03 | Operator Lighthouse CI gate (perf + a11y thresholds) | 5 | QA + Frontend | EX1-01 | CI fail на регрессии > 10% без явного bypass |
| EX2-04 | A11y baseline для login/operator launcher/alarm bar | 5 | Frontend + QA | EX2-03 | axe smoke: 0 critical |
| EX2-05 | Bundle size budget warning и контроль регрессий | 2 | Frontend | EX2-03 | Build/CI предупреждает о budget breach |
| EX2-06 | Документация known gaps и UX runbook | 2 | Frontend + QA | EX2-04 | Документирован список допущений и хвостов |

Сумма: **25 SP**

### Definition of Done Sprint 2

- Lighthouse thresholds стабильно держатся;
- mimic демонстрирует целевую плавность на mid-range планшете;
- a11y critical issues устранены.

### Риски и mitigation

- Риск: perf оптимизации ломают визуальную/функциональную точность mimic.  
  Mitigation: visual regression smoke и сценариные e2e до/после оптимизаций.

---

## 6) Sprint 3 (недели 5-6): Edge Trust

Цель: поднять надежность federation для enterprise-сценариев.

Связь с backlog: **BL-119**, **BL-120**.

### Эпики и задачи

| ID | Задача | SP | Owner | Зависимости | Acceptance |
| --- | --- | --- | --- | --- | --- |
| EX3-01 | API draft selective subtree sync + conflict policy | 5 | Platform lead | EX0-03 | RFC + API contract согласованы |
| EX3-02 | Реализация selective sync в backend | 8 | Platform | EX3-01 | Sync scoped subtree проходит интеграционные тесты |
| EX3-03 | Explorer UI "sync this folder" + conflict preview | 5 | Frontend | EX3-02 | UI сценарий export/import/sync работает end-to-end |
| EX3-04 | Chaos tests: tunnel kill mid-write, reconnect, replay safety | 8 | QA + Platform | EX3-02 | Автотесты подтверждают отсутствие corruption |
| EX3-05 | Runbook recovery и SLO alerts для federation | 3 | SRE | EX3-04 | Док runbook + alerts + owner on-call |

Сумма: **29 SP**

### Definition of Done Sprint 3

- reconnect/sync сценарии стабильны;
- corruption и silent divergence не воспроизводятся в chaos tests;
- у SRE есть практический recovery runbook.

### Риски и mitigation

- Риск: конфликтные merge-policy по объектам ведут к непредсказуемому UX.  
  Mitigation: явный preview диффа + ручное подтверждение для risky object types.

---

## 7) Sprint 4 (недели 7-8): Differentiation Layer

Цель: сделать трудно копируемый moat через semantic runtime + AI-assisted velocity.

Связь с backlog: **BL-104**, **BL-105**.

### Эпики и задачи

| ID | Задача | SP | Owner | Зависимости | Acceptance |
| --- | --- | --- | --- | --- | --- |
| EX4-01 | Mapping haystack markers -> suggested `brickClass` (top-20) | 5 | Platform architect | - | Unit tests inference + documented mappings |
| EX4-02 | Inspector suggestions + export TTL with inferred classes | 5 | Frontend + Platform | EX4-01 | Пользователь видит/принимает предложенный class |
| EX4-03 | Semantic roundtrip test (export -> import -> query equivalence) | 8 | QA + Platform | EX4-01 | Gradle integration green; ограничения задокументированы |
| EX4-04 | Demo-flow "query -> auto-bind -> operator dashboard < 5 min" | 5 | Frontend + Solutions | EX4-02 | Повторяемый демо walkthrough |
| EX4-05 | KPI instrumentation для time-to-first-dashboard | 3 | DevOps + Frontend | EX4-04 | Метрика собирается и видна в scorecard |

Сумма: **26 SP**

### Definition of Done Sprint 4

- semantic roundtrip стабилен;
- inference покрывает частые инженерные классы;
- demo path воспроизводится быстро и предсказуемо.

### Риски и mitigation

- Риск: переусложнение semantic слоя и рост когнитивной нагрузки.  
  Mitigation: opt-in UX, простые defaults, docs на 1 страницу для integrator.

---

## 8) Capacity и sequencing

Рекомендация по команде (ориентир):

- Platform: 2 инженера
- Frontend: 2 инженера
- QA automation: 1 инженер
- DevOps/SRE: 1 инженер

Примерная емкость: **22-30 SP/спринт на поток** при 2-недельном ритме.

---

## 9) Weekly cadence

- **Пн:** план/реплан по блокерам, сверка KPI с baseline.
- **Ср:** risk review (top-3 рисков, нужны ли scope cuts).
- **Пт:** demo + retrospective + обновление scorecard.

Обязательные артефакты конца недели:

- KPI-скриншот/таблица;
- список закрытых задач с acceptance;
- список рисков с owner/ETA.

---

## 10) Go/No-Go критерии по завершению программы

Go, если одновременно выполняются:

1. CI/lead-time targets достигнуты или отклонение <= 10% от целевых.
2. HMI gates (perf + a11y) стабильно green 2 недели подряд.
3. Federation chaos suite green на nightly.
4. Semantic roundtrip test green, demo-flow <= 5 минут.

No-Go (нужен еще один stabilization sprint), если:

- любой из trust-гейтов системно red;
- есть незакрытые critical issues по operator UX или federation integrity.

---

## 11) Что брать в планирование сразу

Приоритет первого planning-мита:

1. EX0-01..03 (калибровка и scope freeze)
2. EX1-01..04 (ускорение delivery и стабилизация CI)
3. EX2-01 (быстрый старт профилирования mimic, не ждать конца Sprint 1)

Это дает ранний сигнал по скорости выполнения и снижает риск срыва следующей волны.

---

## 12) Sprint EX-18 — Horizontal active-active cluster (4 недели)

Цель: единое дерево на N репликах с общей БД, nginx round-robin, driver ownership и failover.

Связь с backlog: **BL-133…139**, parent **BL-115**.

| Недели | Задачи | SP |
| --- | --- | --- |
| 1–2 | BL-133 ADR + BL-134 compose + BL-135 nginx | ~15 |
| 3–4 | BL-136 driver ownership + BL-137 tests + BL-138 load gate + BL-139 ops | ~25 |

**KPI Sprint EX-18:**

- Failover REST: 0 downtime при падении 1 из 3 нод
- Driver failover: < 2× lock TTL до resume poll
- Scale-out API: >= 1.8× throughput при 3 нодах (self-hosted / manual)

**Зависимости:** BL-41/42 (Redis/NATS health Done), BL-111 (demand-driven Done).
