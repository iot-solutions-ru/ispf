> **Язык:** русская версия (вычитка). Канонический английский: [en/ai-agent.md](../en/ai-agent.md).

# Операции AI-агента (BL-177…181)

Справочник для операторов и интеграторов по агенту ISPF Tree-First, набору регрессии, генератору решений и виджетам наблюдения.

См. также [ai-development](ai-development.md), [agent-regression](agent-regression.md), [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md).

---

## Генератор решений (BL-179 / BL-180)

Описание завода → черновик blueprint; опционально **live** apply (дерево + дашборды + alerts).

| Конечная точка | Цель |
|----------|---------|
| `POST /api/v1/ai/solutions/generate` | Prompt → draft; `apply:true` → live deploy (нужен LLM) |

Черновик:

```json
{
  "prompt": "SCADA tank farm with 2 pumps and high pressure alert"
}
```

Ответ (`mode: draft` keyword fallback, или `mode: llm` при классификации LLM):

```json
{
  "status": "OK",
  "mode": "draft",
  "domain": "scada",
  "domainSelection": "keyword",
  "playbook": "AgentSolutionGeneratorPlaybook",
  "blueprintDraft": {
    "id": "scada-tank-farm-with-2-pumps",
    "title": "SCADA facility overview",
    "domain": "scada",
    "referenceBundle": { "appId": "simulator", "manifestPath": "examples/simulator-profiles/bundle.json" }
  }
}
```

Live apply (BL-180):

```json
{
  "prompt": "Building HVAC with one AHU, overview dashboard and status alert",
  "apply": true
}
```

Нужен LLM (`ISPF_AI_*`). Возвращает `mode: live` с `hubPath`, `dashboardPath`, `alertPath`. Proof: `AiSolutionGeneratorLiveSmokeTest` (`ISPF_LLM_SMOKE=true`).

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
| `tools/agent-regression/scenarios/*.json` | 50 curated scenarios (SCADA, MES, HVAC) |
| `tools/agent-regression/validate-scenarios.mjs` | Schema + bundle + pass-rate (`--oneshot` для частичных live-результатов) |
| `AgentRegressionCiTest` | Java CI gate (schema only, no LLM) |
| `AgentLiveDeploySmokeTest` | Opt-in live LLM mes-platform deploy (`ISPF_LLM_SMOKE=true`) |
| `tools/agent-regression/run-live-oneshot.sh` | BL-177 one-shot → results JSON + `--enforce-rate --oneshot` |

### BL-177 live one-shot (вручную / nightly с secrets)

```bash
export ISPF_LLM_SMOKE=true
export ISPF_AI_PROVIDER=openai-compatible
export ISPF_AI_BASE_URL=https://api.deepseek.com/v1   # пример OpenAI-совместимого LLM, НЕ Admin Console
export ISPF_AI_MODEL=deepseek-v4-flash
export ISPF_AI_API_KEY=…                              # не коммитить

bash tools/agent-regression/run-live-oneshot.sh
# или:
./gradlew :packages:ispf-server:test --tests com.ispf.server.ai.agent.AgentLiveDeploySmokeTest
```

PASS: operator UI `mes-platform` live и BFF `mes_platform_listLines` → `LINE-A01` без правок человека.
Агент использует инструмент **`run_deploy_playbook`** с `{"appId":"mes-platform"}`.

Подтвердите локально:

```bash
node tools/agent-regression/validate-scenarios.mjs
```

Отчёт pass rate (полный suite vs one-shot):

```bash
node tools/agent-regression/validate-scenarios.mjs --results nightly-results.json --enforce-rate
node tools/agent-regression/validate-scenarios.mjs --results build/agent-regression/live-oneshot-results.json --enforce-rate --oneshot
```

Форма файла результатов:

```json
{
  "scenarios": [
    { "id": "mes-platform-cert", "status": "OK" },
    { "id": "scada-tank-farm", "status": "ERROR" }
  ]
}
```

Цель: **≥95 %** на всех сценариях (полный BL-178). **S31 one-shot** доказывает BL-177 через `--oneshot`. `nightly-stub-results.json` **устарел** — не доказательство live ≥95%.

---

## Виджет панели показателей инструмента AI (BL-180)

Наблюдение администратора за стоимостью и надежностью каждого агента для каждого инструмента. Источник данных:

| Конечная точка | Поля |
|----------|--------|
| `GET /api/v1/ai/agent/metrics/tools?days=7` | `tools[]`: `tool`, `callCount`, `avgLatencyMs`, `promptTokens`, `completionTokens`, `errorCount`, `errorRate` |

### Референсный layout дашборда

Встраивайте в дашборд платформы (`root.platform.dashboards.ai-ops`) с помощью виджета **диаграммы**, привязанного к API метрик инструмента через BFF или функцию скрипта. Минимальный эталонный макет:

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
| БЛ-178 | Пакет регрессионного агента | [agent-regression](agent-regression.md) |
| БЛ-179 | Draft API генератора решений | Этот документ § Генератор решений |
| БЛ-180 | Live apply генератора + виджет метрик | `AiSolutionGeneratorLiveSmokeTest`, playbook |
| БЛ-181 | Наблюдаемость агентов v2 | `/agent/metrics/tools`, [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md) |
