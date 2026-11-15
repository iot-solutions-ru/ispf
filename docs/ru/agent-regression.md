> **Язык:** русская версия. Английский оригинал: [en/agent-regression.md](../en/agent-regression.md).

# Регрессионные сценарии ИИ-агента и пользовательских маршрутов

> **Статус:** Lab — каталог из 52 проверочных сценариев (SCADA, MES, климат зданий) для автоматической проверки ИИ-агента и ручных маршрутов в веб-консоли. Метки зрелости: [doc-status.md](doc-status.md).

Набор регрессионных сценариев в каталоге `tools/agent-regression/` решает сразу две задачи:
1. Проверяет, что ИИ-агент корректно собирает и разворачивает типовые инженерные решения по текстовому запросу.
2. Задаёт пошаговые проверочные маршруты (`humanSteps`) для тестирования тех же сценариев человеком через интерфейс веб-консоли.

---

## 1. Устройство каталога `tools/agent-regression/`

| Файл / скрипт | Назначение |
|---------------|------------|
| `tools/agent-regression/scenario-schema.json` | JSON-схема описания сценария |
| `tools/agent-regression/scenarios/*.json` | 52 сценария по направлениям `scada`, `mes` и `hvac` |
| `tools/agent-regression/validate-scenarios.mjs` | Проверка корректности всех JSON-сценариев и связанных манифестов `bundle.json` |
| `tools/agent-regression/run-platform-gate.sh` | Прогон сценариев на встроенных механизмах платформы (без внешней LLM) |
| `tools/agent-regression/run-live-suite.sh` | Прогон набора сценариев с подключённой языковой моделью (`platform`, `bundle` или `full`) |
| `scripts/run-agent-regression.sh` | Быстрый локальный скрипт проверки схем и манифестов |

---

## 2. Формат файла сценария

Каждый сценарий содержит формулировку задачи на естественном языке (`prompt`), маршрут проверки в интерфейсе (`uiJourney`, `humanSteps`), ссылку на эталонный пакет (`bundle`) и критерии приёмки (`acceptance`):

```json
{
  "id": "mes-reference-deploy",
  "version": "1",
  "domain": "mes",
  "lane": "human",
  "title": "Развёртывание эталонного решения MES через консоль",
  "prompt": "Установи приложение mes-reference из каталога решений и открой пульт оператора…",
  "uiJourney": "Консоль администратора → Система → Решения → Установить → Режим оператора → Очередь задач",
  "humanSteps": [
    "Войдите под учётной записью admin и откройте раздел Система → Решения…",
    "Разверните пакет и перейдите в режим оператора…"
  ],
  "playbook": "mesReferenceLifecycle",
  "bundle": {
    "appId": "mes-reference",
    "manifestPath": "examples/mes-reference/bundle.json"
  },
  "acceptance": {
    "validateBundle": true,
    "requiredTools": ["validate_bundle", "import_package", "invoke_bff"],
    "operatorSurfaces": ["solutions", "marketplace", "operator", "work-queue"]
  }
}
```

| Поле | Описание |
|------|----------|
| `lane` | Режим прохождения: `human` (пошаговая проверка в веб-консоли), `agent` (автоматическая сборка через ИИ-студию) или `both` (оба способа) |
| `uiJourney` | Краткая цепочка экранов веб-консоли, задействованных в сценарии |
| `humanSteps` | Пошаговая инструкция для инженера или тестировщика без привязки к внутренним именам инструментов агента |
| `acceptance.operatorSurfaces` | Разделы интерфейса, которые должны открываться и работать после выполнения сценария |
| `acceptance.requiredTools` | Обязательные инструменты при автоматическом прогоне через ИИ-агента |

---

## 3. Основные сценарии для ручной и автоматической проверки

| Направление | Идентификаторы сценариев |
|-------------|--------------------------|
| **SCADA и телеметрия** | `scada-pump-station`, `scada-mimic-facility`, `scada-tank-farm`, `scada-alarm-shelf`, `scada-historian-trends`, `scada-solution-generator`, `scada-virtual-cluster`, `scada-water-treatment`, `scada-modbus-device` |
| **Цеховой учёт (MES)** | `mes-reference-deploy`, `mes-dispatch-workflow`, `mes-defect-demo`, `mes-oee-dispatch`, `mes-platform-production`, `mes-solution-generator-factory`, `mes-workflow-escalation` |
| **Здания и вентиляция (HVAC)** | `hvac-building-app`, `hvac-zone-comfort`, `hvac-ahu-comfort`, `hvac-scheduler-setpoints`, `hvac-solution-generator-site`, `hvac-chiller-plant` |

---

## 4. Как запустить проверку локально

Быстрая проверка всех файлов сценариев и манифестов без запуска сервера:

```bash
bash scripts/run-agent-regression.sh
```

Проверка манифестов через REST API запущенного локального сервера ISPF:

```bash
export ISPF_BASE_URL=http://localhost:8080
export ISPF_API_TOKEN=<токен-администратора>
bash scripts/run-agent-regression.sh --live
```

Полный прогон всех 52 сценариев с подключённой языковой моделью:

```bash
export ISPF_LLM_SMOKE=true
export ISPF_AI_BASE_URL=https://api.openai.com/v1
export ISPF_AI_API_KEY=<ваш-ключ>
export AGENT_LIVE_SUITE_MODE=full
export AGENT_LIVE_SUITE_ENFORCE=true
bash tools/agent-regression/run-live-suite.sh
```

**Связанные разделы:** [ИИ-студия и адаптер MCP](ai-development.md) · [Работа с ИИ-агентом](ai-agent.md) · [Руководство оператора](operator-guide.md) · [Веб-консоль](web-console.md).
