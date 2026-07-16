> **Язык:** русская версия (вычитка). Канонический английский: [en/reference-mes-walkthrough.md](../en/reference-mes-walkthrough.md).

# MES reference walkthrough

> **Статус:** Lab — Сквозной MES-путь. Теги: [doc-status](../en/doc-status.md).

Сквозной reference-сценарий ISPF для разработчиков решений: **наряд на отгрузку → резервуар → эстакада → завершение**. Без custom Java в `ispf-server`.

Артефакты: [examples/mes-reference/](../../examples/mes-reference/), bundle `appId` = `mes-reference`.

## Домен (упрощённый MES)

| Сущность | Описание |
|----------|----------|
| **Наряд** (`mes_dispatch_order`) | Заказ на отгрузку: номер, резервуар, объём, статус |
| **Резервуар** (`mes_tank`) | Уровень заполнения (демо: `T-01`, 72%) |
| **Эстакада** | Object path `root.platform.devices.demo-sensor-01` — BFF-функции и журнал событий |
| **Устройство rack** | `root.platform.devices.mes-rack-01` — создаётся из `objects[]` в bundle |

Статусы наряда: `pending` → `filling` → `completed`.

## Шаги сценария

| # | Действие | Object path / API | Событие / эффект | Operator |
|---|----------|-------------------|------------------|----------|
| 1 | Deploy bundle | `POST /api/v1/applications/mes-reference/deploy` | schema `app_mes_ref`, migrations, functions | Admin |
| 2 | Список нарядов | BFF `mes_listOrders` @ `demo-sensor-01` | SQL read | Operator UI journal path |
| 3 | Старт налива | BFF `mes_startFilling` + `orderNo` | status → `filling` | Кнопка формы (будущий dashboard) |
| 4 | Виртуальный счётчик | `virtual` driver profile `meter` + `filling=true` | `meterLiters`, `flowRate` | См. [MesPlatformApiTest](../../packages/ispf-server/src/test/java/com/ispf/server/mes/MesPlatformApiTest.java) |
| 5 | Завершение | BFF `mes_completeFilling` | status → `completed` | Operator confirm |
| 6 | Перегрев эстакады | alert `mesRackOverTemp` при `temperature > 85` | correlator → `alarmActive=true` | Alarm panel |
| 7 | *(опционально)* BPMN workflow | `workflows[]` в bundle → deploy | side-effect через `publish_nats` или `fire_event` | Admin / automation |

## Привязка к REQ-PF

| Механизм | Использование в MES reference |
|----------|-------------------------------|
| **PF-01** bundle deploy | `migrations`, `functions`, `operatorUi` |
| **PF-05** objects[] | `mes-rack-01` device |
| **PF-02** BFF invoke | `mes_listOrders`, `mes_startFilling`, `mes_completeFilling` |
| **Correlators** | `mesRackOverTemp` → SET_VARIABLE |
| **Alert rules** | temperature guard на эстакаде |
| **PF-09** virtual driver | опциональный шаг 4 (meter profile) |

## Критерий готовности

- [x] Bundle деплоится на `test` profile без custom Java
- [x] `mes_listOrders` возвращает 2 seed-наряда
- [x] Lifecycle `startFilling` → `completeFilling` для `DO-1001`
- [x] CI: `MesReferenceBundleSmokeTest`

## Команды smoke

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesReferenceBundleSmokeTest"
```

Локально (server running):

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"mes_listOrders","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'
```

## Связанные документы

- [solution-developer-guide](solution-developer-guide.md)
- [solution-developer-public-api](solution-developer-public-api.md)
- [applications](applications.md)
