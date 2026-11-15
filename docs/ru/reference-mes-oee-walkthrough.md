> **Язык:** русская версия. Английский оригинал: [en/reference-mes-oee-walkthrough.md](../en/reference-mes-oee-walkthrough.md).

# Разбор эталонного примера: расчёт OEE смены (`mes-oee-reference`)

> **Статус:** Stable — минимальный учебный пакет для расчёта общей эффективности оборудования (OEE) и учёта простоев смены. Метки зрелости: [doc-status.md](doc-status.md).

Пакет **`mes-oee-reference`** ([`examples/mes-oee-reference/`](../../examples/mes-oee-reference/)) демонстрирует расчёт производственного показателя **OEE (`Доступность × Производительность × Качество`)** в SQL-функциях приложения и работу оператора через шлюз BFF.

---

## 1. Формулы расчёта OEE

В таблице смен `mes_oee_shift` хранятся плановая длительность смены (`planned`), суммарный простой в минутах (`downtime`), идеальное время цикла на единицу продукции в секундах (`ideal_cycle_sec`), общий выпуск (`total_units`) и количество годной продукции (`good_units`):

| Составляющая | Формула |
|--------------|---------|
| **Доступность (`Availability`)** | `(planned − downtime) / planned × 100 %` |
| **Производительность (`Performance`)** | `(ideal_cycle_sec × total_units) / рабочее_время_в_секундах × 100 %` (не более `100 %`) |
| **Качество (`Quality`)** | `good_units / total_units × 100 %` |
| **Итоговый `OEE`** | `Availability × Performance × Quality` (в процентах) |

На предзаполненной демонстрационной смене линии **`LINE-A01 / Morning`** (план 480 мин, простой 45 мин, идеальный цикл 12 с, выпуск 2 100 шт., годных 2 050 шт.) расчёт возвращает **OEE ≈ 85 %**.

---

## 2. Основные функции пакета

| Функция BFF | Назначение |
|-------------|------------|
| `mes_oee_listShifts` | Возвращает список зарегистрированных смен из таблицы `mes_oee_shift` |
| `mes_oee_getKpi` | Рассчитывает по выбранному `shiftId` итоговый `oeePct` и все три составляющие (`Availability`, `Performance`, `Quality`) |
| `mes_oee_addDowntime` | Добавляет минуты простоя к смене и пересчитывает показатель доступности |

---

## 3. Команды для запуска и проверки

Запуск автотеста:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesOeeReferenceBundleSmokeTest"
```

Установка и проверка через `curl`:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-oee-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-oee-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.singleton-blueprints.mes-oee-reference-hub-v1","functionName":"mes_oee_getKpi","input":{"schema":{"name":"in","fields":[{"name":"shiftId","type":"STRING"}]},"rows":[{"shiftId":"dddddddd-dddd-dddd-dddd-dddddddddddd"}]}}'
```

**Связанные разделы:** [Эталонное решение MES (`mes-platform`)](reference-mes-platform.md) · [Руководство разработчика решений](solution-developer-guide.md).
