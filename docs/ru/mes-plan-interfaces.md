> **Язык:** русская версия. Английский оригинал: [en/mes-plan-interfaces.md](../en/mes-plan-interfaces.md).

# Карта интерфейсов MES ↔ СПП (лаборатория)

> **Статус:** Lab — сопоставление IF-01…12 из ТЗ с BFF и очередями `mes-platform`. Живые CUTE, CIP, AVEVA, Kafka и корпоративная шина **сюда не подключены**. Метки: [doc-status.md](doc-status.md).

Приёмка: [mes-plan-execution.md](mes-plan-execution.md). Заметки адаптера IRS/.NET: [examples/mes-platform/adapters/README.md](../../examples/mes-platform/adapters/README.md).

| IF | Направление | Смысл в ТЗ | Лаборатория в `mes-platform` | Живой коннектор |
|---|---|---|---|---|
| IF-01 | MES → СПП | Срез мастер-модели | `mes_plan_if01_exportMaster` → outbox | Отложено |
| IF-02 | MES → СПП | Доступность ресурсов | `mes_plan_if02_exportResources` → outbox | Отложено |
| IF-03 | MES → СПП | PUB + факт | `mes_plan_if03_exportPlanFact` → outbox | Отложено |
| IF-04 | СПП → MES | Кандидат плана | `mes_plan_ingestCandidate` + inbox | Отложено |
| IF-05 | MES → СПП | Ответ на публикацию | пишется в `mes_plan_publish` / `republish` | Отложено |
| IF-06 | MES ↔ СПП | Обмен резервами | `reserveSoft` / `releaseReserve` / `listResources` | Отложено |
| IF-07 | MES → СПП | Запрос перепланирования | `mes_plan_requestReplan` → outbox | Отложено |
| IF-08 | СПП → MES | Подтверждённая доступность сырья | `mes_plan_if08_ingestMaterialAvail` | Отложено |
| IF-09 | MES → Trade | Готовность PUB для trade | `mes_plan_if09_exportTrade` | Отложено |
| IF-10 | MES → СПП | Мощность / активы | `mes_plan_if10_exportCapacity` | Отложено |
| IF-11 | MES → СПП / BI | KPI план–факт | `mes_plan_if11_exportKpi` | Отложено |
| IF-12 | СПП → BI | Сценарии / demand | вне MES | — |

Очереди: `mes_plan_listInbox`, `mes_plan_listOutbox`. `mes_erp_pollOutbox` по-прежнему ставит `simulated` (BL-169).
