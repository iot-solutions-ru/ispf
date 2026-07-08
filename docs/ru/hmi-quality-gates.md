> **Язык:** русская версия (вычитка). Канонический английский: [en/hmi-quality-gates.md](../en/hmi-quality-gates.md).

# Контроль качества HMI (S21 / BL-92–95, BL-152)

Качество HMI оператора: Lighthouse, бюджет бандла, доступность axe, FPS мнемосхемы SCADA.

См. [ACCELERATION_PROGRAM.md](acceleration-program.md) · [ROADMAP § S21](roadmap.md).

## Проверки

| Проверка | Команда | Цель | CI |
| -------- | ------- | ---- | -- |
| Бюджет бандла | `npm run bundle:budget` | см. `scripts/bundle-budget.json` | ночной |
| Lighthouse | `npm run lighthouse:ci` | доступность входа ≥85; оператор ≥90 (`LH_MIN_ACCESSIBILITY_OPERATOR`) | ночной |
| axe: критические | `npm run test:quality` | 0 критических | ночной |
| FPS мнемосхемы (стресс) | `npm run test:quality` | ≥55 fps при 500 элементах (порог BL-152 для CI) | ночной |
| FPS мнемосхемы (e2e по умолчанию) | `quality-gates.spec.ts` | `MIMIC_MIN_FPS` по умолчанию **60** @ 500 эл. на **заглушённом** API оператора | ночной, когда проверка запускается |

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
| CI gate (BL-152) | 500 | ≥55 | по умолчанию `MIMIC_STRESS_ELEMENTS=500`, `MIMIC_MIN_FPS=55` |
| Цель excellence | 500 | ≥60 | `MIMIC_STRESS_ELEMENTS=500`, `MIMIC_MIN_FPS=60` |
| Legacy S21 proxy | 120 | ≥55 | `MIMIC_STRESS_ELEMENTS=120` |
| Ручная проверка tank-farm | полная схема | ≥60 | вкладка Performance в Chrome на мнемосхеме оператора |

Конструктор стресс-документа: `e2e/fixtures/stressMimic.ts`. Playwright измеряет минимальный FPS за два окна по 2 с (`e2e/quality-gates.spec.ts`).

## Известные пробелы (BL-93)

| Область | Статус | Примечания |
| ------- | ------ | ---------- |
| Контраст цветов (axe) | Готово | токены `--text-muted` + отдельные тесты контраста на входе и у оператора |
| Редактор мнемосхемы с клавиатуры | Готово | Escape — закрыть; стрелки — навигация между элементами; Shift+стрелка — сдвиг; инструменты V/P/C, Del, Ctrl+Z/Y/S; диалог `role` + `aria-pressed` на инструментах |
| Метки для экранных читалок | Готово | `AlarmBarOverlay` — `role="alert"` + `aria-live="assertive"` на каждую тревогу |
| Библиотека символов SCADA | Готово | [SCADA_SYMBOL_LIBRARY.md](scada-symbol-library.md), `customSvg.test.ts` |
| 60 fps мнемосхемы @ tank-farm | Готово | прокси стресса CI: 120 символов @ ≥55 fps (`stressMimic.ts`); полная схема tank-farm — тот же путь рендеринга |
| FPS мнемосхемы @ 500 эл. (живой WebSocket) | Пробел | e2e использует заглушённый API; в `quality-gates.spec.ts` по умолчанию `MIMIC_MIN_FPS=60` |
| Панель оператора в Lighthouse | Готово | `lighthouse-ci.mjs` аудирует `/?mode=operator&app=e2e-operator` с заглушками API |

## Профилирование

- Конструктор стресс-документа: `e2e/fixtures/stressMimic.ts`
- Путь рендеринга: `ScadaMimicCanvas` — мемоизированные соединения + отфильтрованные списки элементов
- Вкладка Performance в Chrome на `/?mode=operator` с мнемосхемой pipeline/tank-farm для ручных трассировок

## Путь к целевым показателям

| КПИ | Базовый уровень (июль 2026) | Цель S21 | Цель BL-152 |
| --- | --------------------------- | -------- | ----------- |
| Производительность Lighthouse (вход) | ~60–75 локально | только KPI; задайте `LH_MIN_PERFORMANCE=75` для принудительной проверки | — |
| Доступность Lighthouse | ~94 (вход/оператор) | ≥90 | оператор ≥95 (roadmap.md) |
| FPS мнемосхемы (120 эл.) | ≥60 в CI | порог ≥55 | — |
| FPS мнемосхемы (500 эл.) | e2e с заглушённым API оператора | по умолчанию ≥60 в spec; живой WebSocket не проверяется в CI |
| Входной JS-бандл | бюджет соблюдается | без регрессии | без регрессии |
