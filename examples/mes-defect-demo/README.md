# MES Defect Distribution Demo

Учебное end-to-end демо «распределение брака по линиям» на ISPF. Все названия линий, ордеров и маршрутов **вымышленные**.

| Артефакт | Назначение |
|----------|------------|
| `bundle.json` | Deploy через `POST /api/v1/applications/mes-defect-demo/deploy` |
| `bpmn/defect-distribution.bpmn.xml` | Исполняемый BPMN (встроен в bundle) |
| `demo-seed.json` | Описание seed-данных |
| `generate_bundle.mjs` | Генератор `bundle.json` после правок BPMN/скриптов |

## Быстрый старт

```powershell
# server на local/test profile
# Важно: не используйте PowerShell alias curl — он ломает кириллицу в bundle.json.
.\examples\mes-defect-demo\deploy.ps1
```

Operator App: **MES Defect Demo** (`mes-defect-demo`).

Дашборды:
- `mes-defect-overview` — сводка линий и очередь диспетчера
- `mes-defect-simulator` — имитация SCADA (два шага: событие → запуск процесса)
- `mes-defect-orders` — демо-ордера

## Сценарий (2 шага)

1. **Симулятор SCADA** — форма «Отправить в SCADA» (`mes_simulateDefect`).
2. **Запустить распределение** — кнопка на том же дашборде (workflow вручную).
3. В **Сводке** подтвердите задачу диспетчера.
4. Процесс завершается событием `mesDefectRouted`.

## CI

```bash
./gradlew :packages:ispf-server:test --tests "com.ispf.server.application.MesDefectDemoBundleSmokeTest"
```

## Регенерация bundle

```bash
node examples/mes-defect-demo/generate_bundle.mjs
cp examples/mes-defect-demo/bundle.json packages/ispf-server/src/test/resources/mes-defect-demo-bundle.json
```
