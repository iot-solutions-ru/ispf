> **Язык:** русская версия. Английский оригинал: [en/reference-mes-defect-walkthrough.md](../en/reference-mes-defect-walkthrough.md).

# Разбор эталонного примера: маршрутизация и переработка брака (`mes-defect-demo`)

> **Статус:** Lab — сквозной пример обработки производственного брака: сигнал с линии → расчёт рекомендуемого маршрута переработки → подтверждение диспетчером в очереди задач BPMN. Метки зрелости: [doc-status.md](doc-status.md).

Пакет **`mes-defect-demo`** ([`examples/mes-defect-demo/`](../../examples/mes-defect-demo/)) показывает, как совместить автоматический выбор маршрута переработки брака со ступенью обязательного подтверждения человеком в процессе BPMN (`root.platform.workflows.mes-defect-distribution`).

---

## 1. Сущности и правила выбора маршрута

| Таблица / узел | Назначение |
|----------------|------------|
| `mes_line` | Производственные линии типов `A`, `B` и `D` (`LINE-A01`, `LINE-B01`, `LINE-D01`) |
| `mes_order` | Производственный заказ со статусом (`open`, `closing`, `closed`) и счётчиком накопленного брака |
| `mes_defect_event` | Зарегистрированный объём несоответствующей продукции и признак особого вида отхода (`isSpecialScrap`) |
| `mes_recommendation` | Рассчитанный маршрут движения брака: возврат на свою линию (`REWORK_A`), подача в очередь соседней линии (`FEED_QUEUE`), вывоз на центральный узел (`TRANSPORT_HUB`) или спецмаршрут (`SPECIAL_ROUTE`) |
| **Узел логики (`SINGLETON`)** | `root.platform.singleton-blueprints.mes-defect-demo-hub-v1` — функции `mes_listLines`, `mes_simulateDefect`, `mes_calculateRoute`, `mes_confirmRoute`, `mes_finalizeDefect` |

---

## 2. Шаги выполнения процесса

1. **Установка пакета:** `POST /api/v1/applications/mes-defect-demo/deploy` создаёт таблицы, функции, процесс BPMN и экраны оператора.
2. **Регистрация события брака:** на экране имитатора (`mes-defect-simulator`) вызывается функция `mes_simulateDefect`, записывающая событие по выбранной линии.
3. **Автоматический расчёт маршрута в BPMN:** процесс `mes-defect-distribution` вызывает функцию `mes_calculateRoute`, формирует рекомендацию в `mes_recommendation` и ставит задачу подтверждения в рабочую очередь смены (`Work Queue`).
4. **Подтверждение диспетчером:** оператор открывает пульт `?mode=operator&app=mes-defect-demo`, проверяет предложенный маршрут (`mes_confirmRoute`) и завершает задачу.
5. **Завершение:** процесс вызывает `mes_finalizeDefect` и публикует событие `mesDefectRouted`.

Запуск автоматического теста сценария:

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesDefectDemoBundleSmokeTest"
```

**Связанные разделы:** [Процессы BPMN (`workflows.md`)](workflows.md) · [Прикладные решения (`applications.md`)](applications.md).
