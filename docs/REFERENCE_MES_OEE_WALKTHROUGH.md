# MES OEE reference walkthrough (BL-121)

Сквозной reference-сценарий ISPF для solution developers: **смена линии → KPI OEE (Availability × Performance × Quality) → регистрация простоя**. Без custom Java в `ispf-server`.

Артефакты: [examples/mes-oee-reference/](../examples/mes-oee-reference/), bundle `appId` = `mes-oee-reference`.

## Домен (упрощённый OEE)

| Сущность | Описание |
|----------|----------|
| **Смена** (`mes_oee_shift`) | Линия, метка смены, план/простой (мин), идеальный цикл (с), выпуск total/good |
| **BFF hub** | Object path `root.platform.devices.demo-sensor-01` — функции и журнал событий Operator UI |
| **Устройство hub** | `root.platform.devices.mes-oee-hub-01` — создаётся из `objects[]` в bundle (опционально) |

### Формулы OEE (в SQL `mes_oee_getKpi`)

| Фактор | Формула |
|--------|---------|
| **Availability** | `(planned − downtime) / planned × 100` |
| **Performance** | `(ideal_cycle_sec × total_units) / run_time_sec × 100`, cap 100% |
| **Quality** | `good_units / total_units × 100` |
| **OEE** | `(planned − downtime) / planned × min(1, ideal×total / run_sec) × good / total × 100` |

Демо-seed **LINE-A01 / Morning**: planned 480 мин, downtime 45, ideal 12 с, total 2100, good 2050 → **OEE ≈ 85%**.

## Шаги сценария

| # | Действие | Object path / API | Эффект | Operator |
|---|----------|-------------------|--------|----------|
| 1 | Deploy bundle | `POST /api/v1/applications/mes-oee-reference/deploy` | schema `app_mes_oee`, migration, functions | Admin |
| 2 | Список смен | BFF `mes_oee_listShifts` @ `demo-sensor-01` | SQL read, 1 seed-смена | Operator UI |
| 3 | KPI по смене | BFF `mes_oee_getKpi` + `shiftId` (UUID) | OEE и компоненты A/P/Q | Dashboard / форма |
| 4 | Добавить простой | BFF `mes_oee_addDowntime` + `minutes` | `downtime_minutes += minutes` (cap = planned) | Operator confirm |
| 5 | Повтор KPI | `mes_oee_getKpi` | OEE снижается после простоя | Trend / alarm |

## Привязка к REQ-PF

| Механизм | Использование в OEE reference |
|----------|-------------------------------|
| **PF-01** bundle deploy | `migrations`, `functions`, `operatorUi` |
| **PF-05** objects[] | `mes-oee-hub-01` device |
| **PF-02** BFF invoke | `mes_oee_listShifts`, `mes_oee_getKpi`, `mes_oee_addDowntime` |
| **Operator UI** | `appId` = `mes-oee-reference`, event journal @ `demo-sensor-01` |

## Критерий готовности

- [x] Bundle деплоится на `test` profile без custom Java
- [x] `mes_oee_listShifts` возвращает seed-смену LINE-A01
- [x] `mes_oee_getKpi` возвращает `oeePct` > 80 для seed-данных
- [x] `mes_oee_addDowntime` увеличивает простой
- [x] CI: `MesOeeReferenceBundleSmokeTest`

## Команды smoke

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesOeeReferenceBundleSmokeTest"
```

Локально (server running):

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-oee-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-oee-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_oee_listShifts","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_oee_getKpi","input":{"schema":{"name":"in","fields":[{"name":"shiftId","type":"STRING"}]},"rows":[{"shiftId":"dddddddd-dddd-dddd-dddd-dddddddddddd"}]}}'
```

## Связанные документы

- [REFERENCE_MES_WALKTHROUGH.md](REFERENCE_MES_WALKTHROUGH.md) — dispatch / tank reference
- [SOLUTION_DEVELOPER_GUIDE.md](SOLUTION_DEVELOPER_GUIDE.md)
- [APPLICATIONS.md](APPLICATIONS.md)
