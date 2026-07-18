> **Язык:** русская версия (вычитка). Канонический английский: [en/agent-regression.md](../en/agent-regression.md).

# Пакет регрессии агентов (BL-178)

> **Статус:** Lab — CI-гейты сценариев. Теги: [doc-status](../en/doc-status.md).

Основа CI-валидации сценариев агента: курируемые промпты, **человеческие UI-маршруты**, ссылки на bundle и проверки схемы **до** живого прогона агента на экземпляре платформы.

## Структура

| Путь | Назначение |
|------|------------|
| `tools/agent-regression/scenario-schema.json` | JSON Schema для файлов сценариев |
| `tools/agent-regression/scenarios/*.json` | Определения сценариев (SCADA, MES, HVAC) |
| `tools/agent-regression/human-ui-rewrites.json` | Карта переписывания lane/UI |
| `tools/agent-regression/apply-human-ui-rewrites.mjs` | Применяет карту к `scenarios/*.json` |
| `tools/agent-regression/run-nightly.sh` | Nightly-заглушка: validate-scenarios + опциональный шлюз live pass rate |
| `scripts/run-agent-regression.sh` | Проверяет форму сценария и манифеста bundle |

## Файл сценария

Каждый сценарий — запрос инженера площадки (без имён tool’ов в `prompt`), опциональный UI-маршрут и bundle:

```json
{
  "id": "mes-reference-deploy",
  "version": "1",
  "domain": "mes",
  "lane": "human",
  "title": "MES reference deploy (UI)",
  "prompt": "Install the mes-reference application from Solutions/Marketplace…",
  "uiJourney": "Admin → System → Solutions → Install → Operator → Work Queue",
  "humanSteps": [
    "Sign in as admin; open System → Solutions…",
    "Deploy the full bundle; open Operator…"
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

| Поле | Смысл |
|------|--------|
| `lane` | `human` — клики в веб-консоли; `agent` — AI Studio/playbook + проверка UI; `both` — любой путь |
| `uiJourney` | Путь по консоли (Исследователь / Система→Решения / Mimic / Dashboard / Operator / Аварии / Work Queue / ИИ-студия) |
| `humanSteps` | Пошаговые клики без имён agent tools |
| `acceptance.operatorSurfaces` | Поверхности UI, где должен быть успех |
| `acceptance.requiredTools` | Для live-suite / playbook агента |

Домены: `scada`, `mes`, `hvac`.

### Золотой набор (`lane: human`)

Воспроизводите на консоли (локальный JAR или демо-стенд [ispf.iot-solutions.ru](https://ispf.iot-solutions.ru/), `admin`/`admin`):

| Домен | id сценариев |
|-------|----------------|
| SCADA | `scada-pump-station`, `scada-mimic-facility`, `scada-tank-farm`, `scada-alarm-shelf`, `scada-historian-trends`, `scada-solution-generator`, `scada-virtual-cluster`, `scada-water-treatment`, `scada-modbus-device` |
| MES | `mes-reference-deploy`, `mes-dispatch-workflow`, `mes-defect-demo`, `mes-oee-dispatch`, `mes-platform-production`, `mes-solution-generator-factory`, `mes-workflow-escalation` |
| HVAC | `hvac-building-app`, `hvac-zone-comfort`, `hvac-ahu-comfort`, `hvac-scheduler-setpoints`, `hvac-solution-generator-site`, `hvac-chiller-plant` |

Хром админки на стенде: **Исследователь**, **Система → Решения**, **ИИ-студия**. Оператор: `/?mode=operator&app=<appId>`, стартеры **Work Queue** и **Alarm Console**.

**Важно для ручных UI-маршрутов:**

- Листинг маркетплейса на публичном стенде может поставить *catalog stub* без `operatorUi`. Для golden deploy нужен полный `examples/<app>/bundle.json` (ИИ-студия → Пакет приложения), если Operator пишет, что UI не найден.
- Применение шаблона дашборда `scada-facility-overview` должно **само создать** `root.platform.mimics.facility-overview`, если объекта нет (`DashboardService.applyTemplateLayout` + `MimicService.ensureMimicExists`). 404 по этому пути — баг платформы, не ошибка пользователя.

Остальные сценарии — `lane: agent` или `both` (всего ≥50 для CI).

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
|------|------|
| PR | job `agent-regression` в [ci.yml](../../.github/workflows/ci.yml): `validate-scenarios.mjs` + `AgentRegressionCiTest` (схема + манифест; **падает при ошибках схемы**). **Только schema/manifest — без живого LLM** |
| Nightly | `run-nightly.sh` — только схемы. Опциональный BL-177 live one-shot при secrets `ISPF_AI_API_KEY` + `ISPF_AI_BASE_URL` (`run-live-oneshot.sh`). **`nightly-stub-results.json` устарел** — не доказательство live ≥95% |
| Platform gate | `run-platform-gate.sh` — fixture + generator + **`AgentBundleDeploySuiteTest`** (≥95% bundles) + **BL-179** `OperatorAgentContinuityIntegrationTest` |
| Live suite | `run-live-suite.sh` — hybrid: сначала tool playbook, LLM fallback; режимы `platform`/`bundle`/`full` |
| Ручной live | `ISPF_LLM_SMOKE=true` + `AgentLiveDeploySmokeTest` / `run-live-oneshot.sh` (`AGENT_LIVE_APP_ID` опционально) |

**Текущее число сценариев:** 52 (SCADA, MES, HVAC), включая `kind: platform-primitive` — примерно 22 `human`, 18 `agent`, 12 `both`.

Репортёр pass rate:

- Полный suite: `AGENT_LIVE_SUITE_MODE=full bash tools/agent-regression/run-live-suite.sh` → `--enforce-rate` (BL-178; нужен LLM)
- Subset: `AGENT_LIVE_SUITE_MODE=platform|bundle` → `--enforce-rate --oneshot`
- One-shot: `--results …/live-oneshot-results.json --enforce-rate --oneshot` — proof BL-177 (S31)
- Platform gate (без LLM): `bash tools/agent-regression/run-platform-gate.sh`

См. [competitive-scorecard](competitive-scorecard.md).

## См. также

- [ai-development](ai-development.md) — инструменты и playbook агента
- [operator-guide](operator-guide.md) — HMI оператора / Work Queue
- [web-console](web-console.md) — Explorer / Система / ИИ-студия
- [AgentDeployPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentDeployPlaybook.java) — рецепт e2e-деплоя BL-177
- [AgentSolutionGeneratorPlaybook](../../packages/ispf-server/src/main/java/com/ispf/server/ai/agent/AgentSolutionGeneratorPlaybook.java) — рецепт factory spec BL-180
- [roadmap](roadmap.md) — BL-177, BL-178
