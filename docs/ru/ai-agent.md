> **Язык:** русская версия (вычитка). Канонический английский: [en/ai-agent.md](../en/ai-agent.md).

# Операции AI-агента (BL-177…181)

Справочник для операторов и интеграторов по агенту ISPF Tree-First, набору регрессии, заглушке генератора решений и виджетам наблюдения.

См. также [AI_DEVELOPMENT.md](ai-development.md), [AGENT_REGRESSION.md](agent-regression.md), [ADR-0034](decisions/0034-agent-observability-and-session-knowledge.md).

---

## Заглушка генератора решений (BL-179)

Черновик проекта по ключевым словам без LLM — первый шаг к автоматизации «опиши завода».

| Конечная точка | Цель |
|----------|---------|
| `POST /api/v1/ai/solutions/generate` | Prompt → `blueprintDraft` (domain `mes` / `scada` / `hvac`) |

Запрос:

```json
{
  "prompt": "SCADA tank farm with 2 pumps and high pressure alert"
}
```

Response (`mode: stub`):

```json
{
  "status": "OK",
  "mode": "stub",
  "domain": "scada",
  "playbook": "AgentSolutionGeneratorPlaybook",
  "blueprintDraft": {
    "id": "scada-tank-farm-with-2-pumps",
    "title": "SCADA facility overview",
    "domain": "scada",
    "specBrief": { "title": "...", "entities": [], "functionalRequirements": [] },
    "suggestedArtifacts": {
      "rootFolder": "root.platform....",
      "devices": [],
      "dashboards": [],
      "alerts": [],
      "mimics": []
    },
    "referenceBundle": { "appId": "simulator-profiles", "manifestPath": "examples/simulator-profiles/bundle.json" },
    "nextSteps": ["create_object CUSTOM folder", "create_virtual_device", "..."]
  }
}
```

Ключевые слова для обнаружения домена:

| Домен | Ключевые слова (примеры) |
|--------|-------------------|
| `mes` | mes, dispatch, oee, work order, manufacturing |
| `hvac` | hvac, building, comfort, zone, ahu, chiller |
| `scada` | scada, pump, tank, mimic, modbus, snmp, historian |

Полный конвейер: [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java).

---

## Пакет регрессии агентов (BL-178)

| Путь | Цель |
|------|---------|
| `tools/agent-regression/scenarios/*.json` | 40 curated scenarios (SCADA, MES, HVAC) |
| `tools/agent-regression/validate-scenarios.mjs` | Schema + bundle manifest validation + pass-rate report |
| `AgentRegressionCiTest` | Java CI gate (schema only, no LLM) |
| `AgentLiveDeploySmokeTest` | Opt-in live LLM mes-platform deploy (`ISPF_LLM_SMOKE=true`) |

Подтвердите локально:

```bash
node tools/agent-regression/validate-scenarios.mjs
```

Отчет о проходимости (ночные прямые трансляции):

```bash
node tools/agent-regression/validate-scenarios.mjs --results nightly-results.json --enforce-rate
```

Форма файла результатов:

```json
{
  "scenarios": [
    { "id": "mes-reference-deploy", "status": "OK" },
    { "id": "scada-tank-farm", "status": "ERROR" }
  ]
}
```

Цель: **≥95 %** процент успешных попыток во всех сценариях. **Статус (0.9.102): не выполнено** — JSON-схема PR/ночных ворот + только результаты заглушки; см. [AGENT_REGRESSION.md](agent-regression.md).

---

## Виджет панели показателей инструмента AI (BL-180)

Наблюдение администратора за стоимостью и надежностью каждого агента для каждого инструмента. Источник данных:

| Конечная точка | Поля |
|----------|--------|
| `GET /api/v1/ai/agent/metrics/tools?days=7` | `tools[]`: `tool`, `callCount`, `avgLatencyMs`, `promptTokens`, `completionTokens`, `errorCount`, `errorRate` |

### Справочный макет информационной панели

Встраивайте в панель мониторинга платформы (`root.platform.dashboards.ai-ops`) с помощью виджета **диаграммы**, привязанного к API метрик инструмента через BFF или функцию скрипта. Минимальный эталонный макет:

```json
{
  "version": 2,
  "widgets": [
    {
      "id": "ai-tool-latency",
      "type": "chart",
      "title": "Agent tool latency (7d)",
      "grid": { "x": 0, "y": 0, "w": 6, "h": 4 },
      "options": {
        "chartType": "bar",
        "binding": {
          "source": "bff",
          "function": "ai_toolMetricsChart",
          "args": { "days": 7, "metric": "avgLatencyMs" }
        }
      }
    },
    {
      "id": "ai-tool-errors",
      "type": "chart",
      "title": "Agent tool error rate (7d)",
      "grid": { "x": 6, "y": 0, "w": 6, "h": 4 },
      "options": {
        "chartType": "bar",
        "binding": {
          "source": "bff",
          "function": "ai_toolMetricsChart",
          "args": { "days": 7, "metric": "errorRate" }
        }
      }
    },
    {
      "id": "ai-tool-tokens",
      "type": "indicator",
      "title": "Agent tokens (7d)",
      "grid": { "x": 0, "y": 4, "w": 4, "h": 2 },
      "options": {
        "binding": {
          "source": "bff",
          "function": "ai_toolMetricsTotals",
          "args": { "days": 7 }
        },
        "format": "integer"
      }
    }
  ]
}
```

**Эскиз BFF** (`ai_toolMetricsChart`): HTTP администратора `GET /api/v1/ai/agent/metrics/tools`, сопоставьте `tools[]` со строками `{ label: tool, value: metric }` для виджета диаграммы.

**Панель AI Studio:** Веб-консоль уже поставляется с `AgentMetricsPanel` (`apps/web-console/src/components/agent/AgentMetricsPanel.tsx`) для показателей уровня хода через `GET /api/v1/ai/agent/metrics`. Расширьте вкладку разбивки инструментов, вызывающую `/agent/metrics/tools` для получения тех же данных в пользовательском интерфейсе студии.

---

## Связанное невыполненное задание

| ID | Особенность | Док |
|----|---------|-----|
| БЛ-177 | Комплексное развертывание агента | [AgentDeployPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) |
| БЛ-178 | Пакет регрессионного агента | [AGENT_REGRESSION.md](agent-regression.md) |
| БЛ-179 | Заглушка генератора решений | Этот документ § Генератор решений |
| БЛ-180 | Генератор решений GA + виджет метрик | [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) |
| БЛ-181 | Наблюдаемость агентов v2 | `/agent/metrics/tools`, [ADR-0034](decisions/0034-agent-observability-and-session-knowledge.md) |
