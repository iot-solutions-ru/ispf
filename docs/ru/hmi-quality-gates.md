> **Язык:** русская версия (вычитка). Канонический английский: [en/hmi-quality-gates.md](../en/hmi-quality-gates.md).

# Контроль качества HMI (S21 / BL-92–95, BL-152)

> **Статус:** Lab — Lighthouse, axe, FPS. Hub: [doc-status.md](../en/doc-status.md).

Качество операторского HMI: Lighthouse, бюджет бандла, axe a11y, FPS мнемосхем SCADA.

См. [acceleration-program](acceleration-program.md) · [roadmap § S21](roadmap.md).

## Проверки

| Проверка | Команда | Цель | CI |
| -------- | ------- | ---- | -- |
| Бюджет бандла | `npm run bundle:budget` | см. `scripts/bundle-budget.json` | ночной |
| Lighthouse | `npm run lighthouse:ci` | вход a11y ≥85; оператор ≥90; ≥95 = ops stretch (не блокер BL-152) | ночной |
| axe: критические | `npm run test:quality` | 0 критических | ночной |
| FPS мнемосхемы (стресс) | `npm run test:quality` | ≥55 fps @ 500 el — **BL-152 Готово** | ночной |
| FPS мнемосхемы (WS update) | `npm run test:quality` | порог WS (`MIMIC_MIN_FPS_WS`, по умолчанию 35) при `VARIABLE_UPDATED` | ночной |
| FPS unmocked live | `E2E_LIVE_FPS=1` + creds | только evidence; **не** заявлять ≥60 без датированного прогона | по запросу |

```bash
cd apps/web-console
npm run build
npm run bundle:budget
npm run lighthouse:ci
npm run test:quality
```

Переопределение через env: `LH_MIN_PERFORMANCE`, `LH_MIN_ACCESSIBILITY`, `LH_MIN_ACCESSIBILITY_OPERATOR`, `MIMIC_MIN_FPS`, `MIMIC_STRESS_ELEMENTS`.

### Пороги стресс-теста мнемосхемы (BL-152)

| Профиль | Элементы | Порог FPS | Env |
| ------- | -------- | --------- | --- |
| CI gate (BL-152 **Готово**) | 500 | ≥55 | по умолчанию `MIMIC_STRESS_ELEMENTS=500`, `MIMIC_MIN_FPS=55` |
| Stretch (ops, не acceptance) | 500 | ≥60 | `MIMIC_MIN_FPS=60` + unmocked `E2E_LIVE_FPS=1` при наличии evidence |
| Legacy S21 proxy | 120 | ≥55 | `MIMIC_STRESS_ELEMENTS=120` |
| Ручная проверка tank-farm | полная схема | ≥60 | вкладка Performance в Chrome на мнемосхеме оператора |

Конструктор стресс-документа: `e2e/fixtures/stressMimic.ts`. Playwright измеряет минимальный FPS за два окна по 2 с (`e2e/quality-gates.spec.ts`).

## Известные пробелы (BL-93)

| Область | Статус | Примечания |
| ------- | ------ | ---------- |
| Контраст цветов (axe) | Готово | токены `--text-muted` + отдельные тесты контраста на входе и у оператора |
| Редактор мнемосхемы с клавиатуры | Готово | Escape — закрыть; стрелки — навигация; Shift+стрелка — сдвиг; V/P/C, Del, Ctrl+Z/Y/S |
| Метки для экранных читалок | Готово | `AlarmBarOverlay` — `role="alert"` + `aria-live="assertive"` |
| Библиотека символов SCADA | Готово | [scada-symbol-library](scada-symbol-library.md) |
| FPS @ 500 el (WS update path) | Готово | CI качает `VARIABLE_UPDATED`; порог `MIMIC_MIN_FPS_WS` (по умолчанию 35) |
| FPS @ 500 el (unmocked live) | Ops note | suite есть; нет датированного ≥60 — не заявлять |
| Панель оператора в Lighthouse | Готово (CI) | a11y floor 90; ≥95 только ops stretch |

## Профилирование

- Конструктор стресс-документа: `e2e/fixtures/stressMimic.ts`
- Путь рендеринга: `ScadaMimicCanvas` — мемоизированные соединения + отфильтрованные списки элементов
- Вкладка Performance в Chrome на `/?mode=operator` с мнемосхемой pipeline/tank-farm для ручных трассировок

## Путь к целевым показателям

| КПИ | Базовый уровень (июль 2026) | Цель S21 | Acceptance BL-152 |
| --- | --------------------------- | -------- | ----------------- |
| Производительность Lighthouse (вход) | ~60–75 локально | только KPI | — |
| Доступность Lighthouse | ~94 (вход/оператор) | ≥90 | CI ≥90; ≥95 ops stretch |
| FPS мнемосхемы (120 эл.) | ≥60 в CI | порог ≥55 | — |
| FPS мнемосхемы (500 эл.) | e2e mocked operator | ≥55 CI (Готово) | stretch ≥60 unmocked = ops |
| Входной JS-бандл | бюджет соблюдается | без регрессии | без регрессии |
