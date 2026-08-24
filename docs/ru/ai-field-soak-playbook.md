> **Язык:** русская версия (вычитка). Канонический английский: [en/ai-field-soak-playbook.md](../en/ai-field-soak-playbook.md).

# Playbook полевого soak AI generator (BL-180 / Post-S33)

> **Статус:** Lab — runbook field evidence. Хаб: [doc-status.md](doc-status.md).

Процедура для **живого generator** на именованной площадке. Harness в коде **Готово**; этот документ закрывает хвост Post-S33: **датированный JSON + журнал интегратора**.

См. [ai-agent](ai-agent.md) · [field-pilot-playbook § ready-for-field](field-pilot-playbook.md#ready-for-field-gate-policy).

---

## Быстрый старт

### Лаборатория (Gradle)

```bash
export ISPF_LLM_SMOKE=true
export ISPF_AI_BASE_URL=https://…/v1
export ISPF_AI_MODEL=gpt-4o-mini
export ISPF_AI_API_KEY=…
export AGENT_LIVE_GENERATOR_DOMAIN=hvac
bash tools/agent-regression/run-live-generator-oneshot.sh
```

### VPS

```bash
export ISPF_VPS_URL=https://ispf.iot-solutions.ru
export ISPF_VPS_PASSWORD=…
bash tools/agent-regression/run-vps-field-soak.sh hvac
```

Архив: `docs/evidence/ai-generator/YYYY-MM-DD-<site>-<domain>.json`

---

## Критерии sign-off

| Утверждение | Доказательство |
| ----------- | -------------- |
| Один домен live | JSON с `functionalOk: true` |
| Soft &lt;15 min | `softBudgetMet: true` в том же JSON |
| Field soak | 3 дня + [журнал](../evidence/ai-generator-soak-journal.template.md) |

**Не** заявлять три домена live без трёх отдельных JSON с реальных прогонов.

Пример схемы: [`live-generator-results.example.json`](../../tools/agent-regression/live-generator-results.example.json)
