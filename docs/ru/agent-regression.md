> **Язык:** русская версия (вычитка). Канонический английский: [en/agent-regression.md](../en/agent-regression.md).

# Пакет регрессии агентов (BL-178)

Основа CI-валидации сценариев агента: курируемые промпты, ссылки на bundle и проверки схемы **до** живого прогона агента на экземпляре платформы.

## Структура

| Путь | Назначение |
|------|------------|
| `tools/agent-regression/scenario-schema.json` | JSON Schema для файлов сценариев |
| `tools/agent-regression/scenarios/*.json` | Определения сценариев (SCADA, MES, HVAC) |
| `tools/agent-regression/run-nightly.sh` | Nightly-заглушка: validate-scenarios + опциональный шлюз live pass rate |
| `scripts/run-agent-regression.sh` | Проверяет форму сценария и манифеста bundle |

## Файл сценария

Каждый сценарий описывает задачу агента и опциональный bundle под тестом:

```json
{
  "id": "mes-reference-deploy",
  "version": "1",
  "domain": "mes",
  "title": "MES reference bundle deploy",
  "prompt": "Deploy mes-reference and verify BFF functions.",
  "playbook": "mes-reference-lifecycle",
  "bundle": {
    "appId": "mes-reference",
    "manifestPath": "examples/mes-reference/bundle.json"
  },
  "acceptance": {
    "validateBundle": true,
    "requiredTools": ["validate_bundle", "import_package"]
  }
}
```

Домены: `scada`, `mes`, `hvac`.

## Запуск валидации (локально)

Из корня репозитория:

```bash
bash scripts/run-agent-regression.sh
```

Опционально: укажите работающий ISPF для проверки bundle через API (тот же контракт, что и `tools/bundle-validate-cli/validate.mjs`):

```bash
export ISPF_BASE_URL=http://localhost:8080
export ISPF_API_TOKEN=<admin-jwt>
bash scripts/run-agent-regression.sh --live
```

## Интеграция в CI

| Этап | Шлюз |
|-------|------|
| PR | job `agent-regression` в [ci.yml](../.github/workflows/ci.yml): `validate-scenarios.mjs` + `AgentRegressionCiTest` (схема + манифест; **падает при ошибках схемы**). **Только schema/manifest — без живого LLM** |
| Nightly | `run-nightly.sh` — только схемы. Опциональный BL-177 live one-shot при secrets `ISPF_AI_API_KEY` + `ISPF_AI_BASE_URL` (`run-live-oneshot.sh`). **`nightly-stub-results.json` устарел** — не доказательство live ≥95% |
| Ручной live | `ISPF_LLM_SMOKE=true` + `AgentLiveDeploySmokeTest` / `run-live-oneshot.sh` |

**Текущее число сценариев:** 50 (SCADA, MES, HVAC).

Репортёр pass rate:

- Полный suite: `validate-scenarios.mjs --results nightly.json --enforce-rate` — цель ≥95% (полный BL-178, **не достигнуто**)
- One-shot: `--results …/live-oneshot-results.json --enforce-rate --oneshot` — proof BL-177 (S31)

См. [competitive-scorecard](competitive-scorecard.md).

## См. также

- [ai-development](ai-development.md) — инструменты и playbook агента
- [AgentDeployPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) — рецепт e2e-деплоя BL-177
- [AgentSolutionGeneratorPlaybook](../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) — рецепт factory spec BL-180
- [roadmap](roadmap.md) — BL-177, BL-178
