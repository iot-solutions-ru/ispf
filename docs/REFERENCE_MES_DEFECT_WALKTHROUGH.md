# MES defect distribution walkthrough

Сквозной reference-сценарий ISPF: **событие брака с линии → привязка к ордеру → прогноз маршрута → подтверждение диспетчером → запись в MES**.

Все данные **вымышленные** (демо-линии, ордера `DEMO-*`, нейтральные коды маршрутов).

Артефакты: [examples/mes-defect-demo/](../examples/mes-defect-demo/), bundle `appId` = `mes-defect-demo`.

Референс BPMN (drawio): [docs/assets/mes-defect-bpmn.drawio.xml](assets/mes-defect-bpmn.drawio.xml).

## Домен (упрощённый MES)

| Сущность | Описание |
|----------|----------|
| **Линия** (`mes_line`) | Тип A/B/D, демо-загрузки узлов |
| **Ордер** (`mes_order`) | Мок ERP: статус `open` / `closing` / `closed`, накопленный брак |
| **Событие брака** (`mes_defect_event`) | Объём, флаг особого типа, переходный остаток |
| **Рекомендация** (`mes_recommendation`) | Маршрут: `REWORK_A` / `FEED_B` / `TRANSPORT_HUB` / `SPECIAL_ROUTE` |
| **MES Hub** | `root.platform.devices.mes-hub-01` — BFF, bindings, журнал событий |
| **Workflow** | `root.platform.workflows.mes-defect-distribution` |

## Типы линий

| Тип | Линия | Прогноз (демо) |
|-----|-------|----------------|
| A | LINE-A01 | Переработка на линии или перенос на соседнюю |
| B | LINE-B01 | Подача или очередь |
| D | LINE-D01 | Транспорт на хаб; особый тип → альтернативный маршрут |

## Шаги сценария

| # | Действие | API / UI | Эффект |
|---|----------|----------|--------|
| 1 | Deploy bundle | `POST /api/v1/applications/mes-defect-demo/deploy` | schema, functions, workflow, dashboards |
| 2 | Список линий | BFF `mes_listLines` @ `mes-hub-01` | 3 демо-линии |
| 3 | Симуляция SCADA | Дашборд `mes-defect-simulator` → `mes_simulateDefect` | INSERT event |
| 4 | Запуск BPMN | Кнопка «Запустить распределение» | workflow до user task |
| 5 | Прогноз | `mes_calculateRoute` в BPMN | INSERT `mes_recommendation` |
| 6 | Подтверждение | Work queue Operator App `mes-defect-demo` | `mes_confirmRoute` |
| 7 | Финализация | `mes_finalizeDefect` + `mesDefectRouted` | status `routed` |

## Демо-кейсы

1. **LINE-A01**, 12 кг, `active` → `REWORK_A` → задача в work-queue.
2. **LINE-A01**, `orderScenario=closing`, объём > 10 кг → transitional remainder.
3. **LINE-B01**, 8 кг → `FEED_QUEUE`.
4. **LINE-D01**, обычный брак → `TRANSPORT_HUB`.
5. **LINE-D01**, `isSpecialScrap=1` → `SPECIAL_ROUTE`.

## CI

`MesDefectDemoBundleSmokeTest` — deploy, list lines, simulate + workflow run + work-queue, special scrap → `SPECIAL_ROUTE`.

## Связанные документы

- [WORKFLOWS.md](WORKFLOWS.md) — BPMN на ISPF
- [APPLICATIONS.md](APPLICATIONS.md) — bundle deploy
