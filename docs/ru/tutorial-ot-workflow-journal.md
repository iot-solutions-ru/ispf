> **Язык:** русская версия. Английский оригинал: [en/tutorial-ot-workflow-journal.md](../en/tutorial-ot-workflow-journal.md).

# Урок 1: Журнал и пошаговая хронология выполнения процесса BPMN

> **Статус:** Beta — просмотр истории запусков и времени выполнения каждого шага процесса `WORKFLOW`. См. также: [Все уроки серии](ot-automation-excellence-tutorials.md).

## Цель урока
Запустить процесс BPMN и посмотреть подробную хронологию прохождения его шагов в веб-консоли и через REST API.

---

## Пошаговое выполнение

### Шаг 1. Создайте учебный процесс `WORKFLOW`
Создайте узел `root.platform.workflows.journal-lab` и в визуальном редакторе BPMN соберите простую цепочку **Старт → Сервисный шаг (`serviceTask` с действием `ispf:action="log"`) → Завершение**, затем переведите процесс в статус **`ACTIVE`**.

### Шаг 2. Запустите экземпляр процесса
Нажмите кнопку **«Запустить» (`Run`)** в редакторе или выполните запрос:

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/run?path=root.platform.workflows.journal-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{}}'
```

Сохраните возвращённый идентификатор экземпляра `instanceId`.

### Шаг 3. Посмотрите хронологию шагов в веб-консоли и по API
В редакторе процесса выберите завершённый запуск — откроется лента шагов с отметками времени и статусами каждого узла диаграммы. Те же данные доступны по API:

```bash
# Список запусков процесса
curl -s "$BASE/api/v1/workflows/by-path/runs?path=root.platform.workflows.journal-lab" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Пошаговый журнал конкретного экземпляра
curl -s "$BASE/api/v1/workflows/instances/$INSTANCE_ID/steps" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Следующий урок:** [Урок 2 — Вызов процесса BPMN как типизированного инструмента](tutorial-ot-workflow-as-tool.md).
