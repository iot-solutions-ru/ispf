> **Язык:** русская версия (вычитка). Канонический английский: [en/ai-agent.md](../en/ai-agent.md).

# Операции AI-агента (BL-177…181)

> **Статус:** Beta — Agent API; gate ≥95% не закрыт. Теги: [doc-status](../en/doc-status.md).

Справочник для операторов и интеграторов по агенту ISPF Tree-First, набору регрессии, генератору решений и виджетам наблюдения.

См. также [ai-development](ai-development.md), [agent-regression](agent-regression.md), [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md).

---

## Генератор решений (BL-179 / BL-180)

![Чат AI Studio](../assets/ispf-ai-studio.png)

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

Полный конвейер: [AgentSolutionGeneratorPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java).

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

**Цель (не достигнута):** ≥95 % live на всех сценариях (полный BL-178). По [competitive-scorecard](competitive-scorecard.md) AI **~7.0 PARTIAL** — one-shot/smoke **REAL**, полный gate ≥95% **открыт**. **S31 one-shot** доказывает BL-177 через `--oneshot`. `nightly-stub-results.json` **устарел** — не доказательство live ≥95%.

---

## Наблюдаемость агентов v2 (BL-181)

Админ-наблюдаемость: агрегаты ходов и стоимость/задержка/надёжность по каждому tool.

| Конечная точка | Auth | Поля |
|----------|------|--------|
| `GET /api/v1/ai/agent/metrics?days=7` | admin | `turnsByStatus`, `avgStepsPerTurn`, `topFailingTools`, суммы токенов/latency, `toolLatencyBreakdown` |
| `GET /api/v1/ai/agent/metrics/tools?days=7` | admin | `tools[]`: `tool`, `callCount`, `avgLatencyMs`, `maxLatencyMs`, `promptTokens`, `completionTokens`, `errorCount`, `errorRate` |

**AI Studio (REAL):** вкладка Status → `AgentMetricsPanel` загружает оба endpoint — карточки ходов и таблица **стоимость / задержка по tool** (`fetchAgentToolMetrics`).

**Автоповтор при сбое (REAL):**
- разбор JSON-действия LLM: `AgentLlmActionResolver` nudges + retry
- транзиентные **исключения** tool (timeout / connection / 5xx / rate-limit): один автоматический retry в `TreeFirstAgentService` (`AgentToolTransientRetry`); в результате может быть `retried: true`. Бизнес-ответы `status: ERROR` не повторяются.

### Опциональный дашборд / BFF

Референсный layout и маппинг BFF — в `examples/agent-metrics-dashboard/`. Привязывайте chart-виджеты к `GET /api/v1/ai/agent/metrics/tools`, если нужны те же ряды на операторском дашборде (сетка **84×8** — [dashboards](dashboards.md)).

---

## Связанное невыполненное задание

| ID | Особенность | Док |
|----|---------|-----|
| БЛ-177 | Комплексное развертывание агента | [AgentDeployPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) |
| БЛ-178 | Пакет регрессионного агента | [agent-regression](agent-regression.md) |
| БЛ-179 | Draft API генератора решений | Этот документ § Генератор решений |
| БЛ-180 | Live apply генератора + виджет метрик | `AiSolutionGeneratorLiveSmokeTest`, playbook |
| БЛ-181 | Наблюдаемость агентов v2 | `/agent/metrics/tools`, [0034-agent-observability-and-session-knowledge](decisions/0034-agent-observability-and-session-knowledge.md) |
