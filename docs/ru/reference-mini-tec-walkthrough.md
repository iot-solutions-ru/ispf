> **Язык:** русская версия (вычитка). Канонический английский: [en/reference-mini-tec-walkthrough.md](../en/reference-mini-tec-walkthrough.md).

# Mini-TEC reference walkthrough

Сквозной **опциональный demo** ISPF для **АСУ ТП мини-ТЭЦ**: 3×ГПУ, ГРПБ, РУМБ, ДГУ, нагрузочный модуль, станционный hub, защиты, operator HMI с однолинейной схемой. Без custom Java в `ispf-server` (bootstrap + bundle). **Не входит в platform roadmap** — см. [ROADMAP.md § Phase 18](roadmap.md).

Артефакты: [examples/mini-tec/](../../examples/mini-tec/), `appId` = `mini-tec`.

См. также [examples/mini-tec/README.md](readme.md), agent playbook `miniTecReference()` в `AgentPlaybooks.java`.

## Домен

| Сущность | Object path | Blueprint |
|----------|-------------|--------|
| **ГПУ 1–3** | `root.platform.devices.mini-tec-plant.gpu-0N` | `mini-tec-gpu-v1` |
| **ГРПБ** | `...grpb` | `mini-tec-grpb-v1` |
| **РУМБ 10/0,4 кВ** | `...rumb-10kv` | `mini-tec-rumb-v1` |
| **ДГУ** | `...dgu` | `mini-tec-dgu-v1` |
| **Нагрузочный модуль** | `...load-module` | `mini-tec-load-module-v1` |
| **Станционный hub** | `...station-hub` | `mini-tec-station-hub-v1` |

Установленная мощность: **4440 кВт** (3×1480). Virtual driver profiles: `tec-gpu`, `tec-grpb`, `tec-rumb`, `tec-dgu`, `tec-load`.

## Шаги сценария

| # | Действие | API / UI | Эффект |
|---|----------|----------|--------|
| 1 | Старт server | `MiniTecPlatformBootstrap` | Модели, объекты, dashboards, workflows, automation, operator UI |
| 2 | Operator HMI | `?mode=operator&app=mini-tec` | **Интегрированная мнемосхема** `mini-tec-hmi` (по умолчанию) |
| 3 | Зоны схемы | вкладки Генерация / Газ / Электроснабжение | Mimics `mini-tec-single-line`, `mini-tec-zone-gas`, `mini-tec-zone-electrical` |
| 4 | Карточка узла | клик по блоку на схеме (`setSelection`) | Панель справа: статус, P, sparkline, chart |
| 5 | Станционная сводка | dashboard `mini-tec-overview` | KPI hub + ГПУ |
| 6 | Учебный пожар | `simulate_fire` на ГРПБ | Alarm bar, gas trip **всех** GPU, email/webhook (если relay настроен) |
| 7 | Недомощность | нагрузка > генерации | correlator → `mini-tec-load-module-auto-unload` |
| 8 | KPI / тренды | `mini-tec-kpi`, `mini-tec-trends` | OEE/MTBF/MTTR, historian charts |
| 9 | Суточный журнал | schedule `mini-tec-daily-journal-etl` | `aggregate_daily_journal` → `tec_daily_journal` |
| 10 | Redeploy bundle | `POST /api/v1/applications/mini-tec/deploy` | Idempotent sync из `examples/mini-tec/bundle.json` |

## Дашборды

| Path | Назначение |
|------|------------|
| `root.platform.dashboards.mini-tec-hmi` | **Операторская мнемосхема** (default) |
| `root.platform.dashboards.mini-tec-overview` | Станционная сводка |
| `root.platform.dashboards.mini-tec-single-line` | Однолинейная схема (SLD widget) |
| `root.platform.dashboards.mini-tec-kpi` | KPI (OEE, MTBF, MTTR) |
| `root.platform.dashboards.mini-tec-trends` | Сравнение трендов |
| `root.platform.dashboards.mini-tec-gpu-detail` | ГПУ — детально |
| `root.platform.dashboards.mini-tec-grpb` | ГРПБ (+ учебные симуляции) |
| `root.platform.dashboards.mini-tec-rumb` | РУМБ |
| `root.platform.dashboards.mini-tec-dgu` | ДГУ |
| `root.platform.dashboards.mini-tec-load-module` | Нагрузочный модуль |
| `root.platform.dashboards.mini-tec-protections` | Защиты |
| `root.platform.dashboards.mini-tec-exploitation` | Эксплуатация (все 3 ГПУ) |

## Привязка к REQ-PF / механизмам

| Механизм | Использование |
|----------|---------------|
| **Models** | 6 INSTANCE-моделей + binding rules на hub |
| **Virtual driver** | Профили `tec-*`, poll в `VirtualTecPoll.java` |
| **Binding rules** | Cross-object агрегаты на `station-hub` (0010) |
| **Workflow** | 5 BPMN: gas trip, load unload, GPU start, ack protection, shift handover |
| **Automation** | Alert rules + correlators на защитах |
| **PF-02** | App SQL: `tec_daily_journal`, `tec_consumer_load` |
| **Operator UI** | `root.platform.operator-apps` → mini-tec menus |

## Приёмка (smoke checklist)

| # | Проверка | Ожидание |
|---|----------|----------|
| 1 | `?mode=operator&app=mini-tec` | Интегрированный HMI `mini-tec-hmi`, alarm bar сверху |
| 2 | Клик ГПУ-2 на схеме | Карточка справа: P, статус, sparkline 1h, пуск/стоп |
| 3 | ГРПБ → «Пожар (учебный)» | Красная подсветка, звук, gas trip **всех** ГПУ |
| 4 | Нагрузка > генерации | `stationUnderpower`, авто-сброс, событие в event-feed |
| 5 | Логин `operator-gas` | `GET` variables `rumb-10kv` → 403; `grpb` → OK |
| 6 | Экспорт PNG на mimic; CSV тренда P за 24h | Файлы сохраняются |

## RBAC и смена

- `operator-gas` — ACL OWNER на `grpb`; `operator-electrical` — на `rumb-10kv`, `load-module`.
- `operator-engineer` — полный доступ к устройствам станции (без object ACL на ГПУ/hub).
- Workflow `mini-tec-shift-handover` — user task подтверждения активных алармов + `aggregate_daily_journal`.

## REST / WebSocket / OPC-UA

См. [examples/mini-tec/README.md](readme.md). OPC-UA lab: один ГПУ можно перевести на `opcua` driver profile вместо `virtual` ([drivers](drivers.md)).

## NFR (production)

Для промышленной эксплуатации мнемосхемы ТЭЦ см. [deployment](deployment.md).
