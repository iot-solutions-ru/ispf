> **Язык:** русская версия (вычитка). Канонический английский: [en/tutorial-ot-workflow-journal.md](../en/tutorial-ot-workflow-journal.md).

# Туториал: журнал выполнения workflow

> **Статус:** Beta — ADR-0049 Wave 1. Хаб: [OT Automation туториалы](ot-automation-excellence-tutorials.md).

## Цель

Запустить простой BPMN workflow и посмотреть **timeline шагов** в UI и через REST.

## Подготовка

См. [хаб](ot-automation-excellence-tutorials.md#предварительные-требования). Создайте `WORKFLOW`, например `root.platform.workflows.journal-lab`.

## Шаги

### 1. Минимальный BPMN

В Workflow Builder сохраните BPMN с `serviceTask` → `log` (см. EN-пример XML) или нарисуйте Start → Service Task → End и задайте `ispf:action=log` в панели ISPF.

### 2. Запуск

**UI:** Workflow Builder → **Run**.

**API:**

```bash
curl -s -X POST "$BASE/api/v1/workflows/by-path/run?path=root.platform.workflows.journal-lab" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"input":{}}'
```

Запомните `instanceId`.

### 3. Timeline в UI

Выберите instance — блок **timeline** с шагами, статусом и таймингом.

### 4. Шаги через API

```bash
curl -s "$BASE/api/v1/workflows/by-path/runs?path=root.platform.workflows.journal-lab" \
  -H "Authorization: Bearer $TOKEN" | jq .

curl -s "$BASE/api/v1/workflows/instances/$INSTANCE_ID/steps" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

## Проверка

- [ ] Run завершился (`COMPLETED`)
- [ ] В timeline есть шаг `log`
- [ ] `GET .../steps` возвращает упорядоченные строки

## Дальше

[Workflow как tool](tutorial-ot-workflow-as-tool.md)
