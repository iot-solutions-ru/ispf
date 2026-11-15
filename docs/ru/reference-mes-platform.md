> **Язык:** русская версия. Английский оригинал: [en/reference-mes-platform.md](../en/reference-mes-platform.md).

# Эталонное прикладное решение MES (`mes-platform`)

> **Статус:** Beta — демонстрационный и учебный пакет MES из каталога решений: диспетчеризация сменных заданий, расчёт OEE, статистический контроль качества (SPC), рецептурные партии (ISA-88) и генеалогия партий. Метки зрелости: [doc-status.md](doc-status.md).

Пакет **`mes-platform`** (и его расширенный вариант **`mes-platform-production`**) показывает, как на базе стандартных механизмов ISPF — без единой строчки отраслевого Java-кода в ядре сервера — собирается решение для цехового учёта (MES):

- учёт производственных линий и расчёт OEE за смену (`Доступность × Производительность × Качество`);
- выдача и подтверждение производственных заказов через маршрут BPMN и рабочую очередь смены (`Work Queue`);
- контроль качества и контрольные карты Шухарта (SPC);
- проведение рецептурных партий по фазам (ISA-88);
- прослеживаемость (генеалогия) партий сырья, заказов и паспортов качества;
- таблица исходящих сообщений (`outbox`) для стыковки с внешними системами класса ERP (1С / SAP).

> **Как поставляется решение:** MES не зашит внутрь базового сервера `ispf-server`, а устанавливается как прикладной пакет `bundle.json` из каталога `examples/mes-platform/` или витрины решений.

**Связанные разделы:** [Краткая памятка оператора MES](mes.md) · [Сопоставление со стандартом ISA-95](isa95-catalog.md) · [Производственные шаблоны проектирования](manufacturing-patterns.md) · [Разбор минимального примера OEE](reference-mes-oee-walkthrough.md).

---

## 1. Два варианта пакета в репозитории

| Идентификатор `appId` | Каталог в репозитории | Назначение |
|-----------------------|-----------------------|------------|
| `mes-platform` | [`examples/mes-platform/`](../../examples/mes-platform/) | Базовый каркас MES с демонстрационными таблицами и функциями |
| `mes-platform-production` | [`examples/mes-platform-production/`](../../examples/mes-platform-production/) | Полный комплект с включённым планировщиком обмена, экранами оператора и сквозным автотестом |

Установка одной командой (локально или на стенде):

```bash
bash deploy/tools/mes-platform-production-deploy.sh
```

Либо прямым HTTP-запросом:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-platform-production/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-platform-production/bundle.json
```

После установки пульт оператора открывается по адресу `?mode=operator&app=mes-platform-production` (вкладки **Диспетчеризация**, **OEE**, **Качество**, **Партии**, **Генеалогия**).

---

## 2. Структура узлов дерева в пакете `mes-platform`

При установке пакет создаёт ветку `root.platform.mes` и предзаполненные демонстрационные объекты:

| Путь в дереве объектов | Тип узла | Назначение |
|------------------------|----------|------------|
| `root.platform.mes` | `CUSTOM` | Корневая папка цехового учёта |
| `root.platform.mes.work-orders.wo-line-a01-001` | `CUSTOM` | Производственный заказ на линию `LINE-A01` (шаблон `work-order-v1`) |
| `root.platform.mes.operations.op-assemble-a01` | `CUSTOM` | Технологическая операция сборки |
| `root.platform.mes.lots.batch-line-a01-001` | `CUSTOM` | Партия продукции по стандарту ISA-88 (шаблон `batch-v1`) |
| `root.platform.mes.shifts.shift-morning-a01` | `CUSTOM` | Утренняя смена линии `LINE-A01` |
| `root.platform.mes.quality-records.qr-line-a01-001` | `CUSTOM` | Запись контроля качества и учёта брака |
| `root.platform.mes.instances.plant-a...line-a01` | `DEVICE` | Иерархия оборудования по ISA-95 (площадка → участок → линия) |

---

## 3. Основные функциональные модули пакета

### Диспетчеризация заказов (`Work Order Dispatch`)
- Процесс BPMN `root.platform.workflows.mes-work-order-dispatch` ([исходник XML](../../examples/mes-platform/bpmn/work-order-dispatch.bpmn.xml)) при запуске ставит задачу оператору в рабочую очередь смены (`Work Queue`).
- На экране **Dispatch** оператор видит очередь задач и подтверждает выполнение заказа через функцию `mes_dispatch_confirmWorkOrder`.

### Расчёт OEE по сменам
На узле логики приложения развёрнуты скриптовые функции:
- `mes_platform_listLines` — список производственных линий (`LINE-A01`);
- `mes_oee_listShifts` — список смен;
- `mes_oee_getKpi` — расчёт `oeePct`, доступности, производительности и качества (на демонстрационной смене `dddddddd-dddd-dddd-dddd-dddddddddddd` возвращает OEE ≈ `85 %`);
- `mes_oee_addDowntime` — регистрация простоя линии.

### Статистический контроль качества (`Quality / SPC`)
- Примесь `quality-record-v1` добавляет поля `defectCode`, `severity`, `lotId` на узлы записей о браке.
- Экран **Quality** строит контрольный график по точкам измерения `spcMeasurement` относительно верхней и нижней границ допуска (`spcUcl`, `spcLcl`) и вызывает функцию `mes_quality_listSpcSamples`.

### Управление рецептурными партиями (`ISA-88 Batch Lite`)
- Шаблон `batch-v1` описывает партию (`batchId`, `recipe`, текущая фаза `phase`).
- Функции `mes_batch_runPhase` и `mes_batch_getStatus` переводят партию по технологическим фазам (`charge` → `react` → `discharge`).

### Прослеживаемость и генеалогия партий (`Genealogy`)
- Функция `mes_genealogy_queryByLot` и отчёт `mes-genealogy` строят граф связей между партией готовой продукции (`BATCH-LINE-A01-001`), использованным сырьём, производственным заказом и записями контроля качества.

### Очередь исходящих сообщений в ERP (`ERP Outbox`)
- Скрипт [`erp-outbox.json`](../../examples/mes-platform/erp-outbox.json) демонстрирует паттерн надёжной буферизации документов для 1С / SAP в таблице `mes_erp_outbox` с ключом идемпотентности `idempotency_key` (`${entityType}:${entityId}`) и функциями `mes_erp_enqueueOutbox` / `mes_erp_pollOutbox`.

---

## 4. Запуск автотестов эталонного решения MES

```bash
./gradlew :packages:ispf-server:test \
  --tests "com.ispf.server.application.reference.mes.MesBlueprintBootstrapTest" \
  --tests "com.ispf.server.application.reference.mes.MesWorkOrderDispatchIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesQualitySpcDashboardIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesBatchPhaseRunnerIntegrationTest" \
  --tests "com.ispf.server.application.reference.mes.MesGenealogyLiteIntegrationTest" \
  --tests "com.ispf.server.application.MesPlatformGaSmokeTest"
```
