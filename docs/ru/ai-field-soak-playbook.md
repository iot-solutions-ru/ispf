> **Язык:** русская версия. Английский оригинал: [en/ai-field-soak-playbook.md](../en/ai-field-soak-playbook.md).

# Регламент приёмочных испытаний генератора решений ИИ на площадке

> **Статус:** Lab — порядок проверки и оформления протокола приёмки генератора решений (`POST /api/v1/ai/solutions/generate`) с подключённой языковой моделью. Метки зрелости: [doc-status.md](doc-status.md).

Этот документ описывает порядок проведения контрольных прогонов генератора решений (`apply: true`) и сохранения датированного отчёта о результатах.

**Связанные разделы:** [Работа с ИИ-агентом (`ai-agent.md`)](ai-agent.md) · [Регламент полевых испытаний (`field-pilot-playbook.md`)](field-pilot-playbook.md).

---

## 1. Запуск проверочного прогона

### Локально или в лаборатории

```bash
export ISPF_LLM_SMOKE=true
export ISPF_AI_BASE_URL=https://api.openai.com/v1
export ISPF_AI_MODEL=gpt-4o-mini
export ISPF_AI_API_KEY=<ваш-ключ>
export AGENT_LIVE_GENERATOR_DOMAIN=hvac
bash tools/agent-regression/run-live-generator-oneshot.sh
```

### На выделенном сервере (VPS)

```bash
export ISPF_VPS_URL=https://ispf.iot-solutions.ru
export ISPF_VPS_PASSWORD=<пароль-администратора>
bash tools/agent-regression/run-vps-field-soak.sh hvac
```

Скрипт сохраняет датированный JSON-отчёт в каталог `docs/evidence/ai-generator/YYYY-MM-DD-<площадка>-<домен>.json`.

---

## 2. Критерии успешного прохождения

| Проверяемый параметр | Критерий в итоговом отчёте |
|----------------------|----------------------------|
| Работоспособность созданного решения | Поле `functionalOk: true` (созданы узел логики `SINGLETON`, устройства, тревоги и экран оператора) |
| Укладывание в целевой бюджет времени (< 15 минут) | Поле `softBudgetMet: true` (`elapsedMs` ≤ `900000`) |
| Многодневное наблюдение на стенде | Заполненный журнал по шаблону [`ai-generator-soak-journal.template.md`](../evidence/ai-generator-soak-journal.template.md) |

Пример структуры файла результатов: [`tools/agent-regression/live-generator-results.example.json`](../../tools/agent-regression/live-generator-results.example.json).
