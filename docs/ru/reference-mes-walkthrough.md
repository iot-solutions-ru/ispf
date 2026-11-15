> **Язык:** русская версия. Английский оригинал: [en/reference-mes-walkthrough.md](../en/reference-mes-walkthrough.md).

# Разбор эталонного примера: налив и отгрузка продукции (`mes-reference`)

> **Статус:** Lab — учебный пример прикладного пакета: наряд на отгрузку, учёт уровня в резервуаре, управление эстакадой налива и контроль перегрева. Метки зрелости: [doc-status.md](doc-status.md).

Пакет **`mes-reference`** ([`examples/mes-reference/`](../../examples/mes-reference/)) наглядно показывает, как связать табличный учёт нарядов в SQL-схеме приложения с телеметрией оборудования и правилами тревог в дереве объектов.

---

## 1. Устройство модели

| Сущность | Как представлена в пакете | Назначение |
|----------|---------------------------|------------|
| **Наряд на отгрузку** (`mes_dispatch_order`) | Таблица в SQL-схеме `app_mes_ref` | Номер наряда, резервуар, плановый объём и статус (`pending` → `filling` → `completed`) |
| **Резервуар** (`mes_tank`) | Таблица в SQL-схеме `app_mes_ref` | Текущий уровень заполнения (в демо: `T-01`, `72 %`) |
| **Узел логики приложения** | `SINGLETON` по пути `root.platform.singleton-blueprints.mes-reference-hub-v1` | Хранит скриптовые функции `mes_listOrders`, `mes_startFilling`, `mes_completeFilling` |
| **Датчик и стояк эстакады** | Узлы `DEVICE` (`root.platform.devices.demo-sensor-01`, `mes-rack-01`) | Показания температуры, расходомера и правило тревоги перегрева |

---

## 2. Пошаговый сценарий работы

1. **Установка пакета:** запрос `POST /api/v1/applications/mes-reference/deploy` создаёт схему `app_mes_ref`, таблицы с двумя тестовыми нарядами (`DO-1001`, `DO-1002`), узел логики и функции.
2. **Чтение списка нарядов:** вызов функции `mes_listOrders` через `POST /api/v1/bff/invoke` возвращает текущие наряды на отгрузку.
3. **Запуск налива:** вызов `mes_startFilling` с номером наряда `DO-1001` переводит заказ в статус `filling`.
4. **Завершение налива:** вызов `mes_completeFilling` фиксирует отгруженный объём и переводит наряд в статус `completed`.
5. **Контроль перегрева эстакады:** при превышении температуры выше `85 °C` срабатывает правило тревоги и публикует событие `mesRackOverTemp`, а коррелятор взводит признак активной аварии.

---

## 3. Команды для быстрой проверки

Запуск автоматического теста пакета:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesReferenceBundleSmokeTest"
```

Ручная установка и вызов функции на запущенном локальном сервере:

```bash
curl -s -X POST http://localhost:8080/api/v1/applications/mes-reference/deploy \
  -H "Content-Type: application/json" \
  --data-binary @examples/mes-reference/bundle.json

curl -s -X POST http://localhost:8080/api/v1/bff/invoke \
  -H "Content-Type: application/json" \
  -d '{"objectPath":"root.platform.singleton-blueprints.mes-reference-hub-v1","functionName":"mes_listOrders","input":{"schema":{"name":"in","fields":[]},"rows":[{}]}}'
```

**Связанные разделы:** [Руководство разработчика решений](solution-developer-guide.md) · [Прикладные решения (`applications.md`)](applications.md).
