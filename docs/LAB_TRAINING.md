# Lab Training — 18 упражнений

Пакет **Lab Training** демонстрирует Ignition-style Virtual Device, автоматизацию, отчёты и дашборды ISPF. Все объекты импортируются из `examples/lab-training/bundle.json`.

## Быстрый старт

1. Запустите сервер и Web Console (профиль `local`).
2. Импортируйте bundle в **Application** `lab-training` (`packageId` = `appId`):

```http
POST /api/v1/platform/packages/import?packageId=lab-training
Content-Type: application/json

<содержимое examples/lab-training/bundle.json>
```

Эквивалент legacy API:

```http
POST /api/v1/applications/lab-training/deploy
Content-Type: application/json

<тот же bundle.json>
```

При импорте платформа:

| Шаг | Результат |
|-----|-----------|
| Регистрация | запись в реестре приложений (`applications`) |
| Application в дереве | `root.platform.applications.lab-training` (модель `application-v1`) |
| Data source | `root.platform.data-sources.lab-training` |
| Operator HMI | `root.platform.operator-apps.lab-training` (из секции `operatorUi` bundle) |
| Содержимое bundle | дашборды, отчёты, alert-rules, correlators → каталоги `root.platform.*` |

Опционально можно **сначала** зарегистрировать пустое приложение:

```http
POST /api/v1/applications
Content-Type: application/json

{
  "appId": "lab-training",
  "displayName": "Lab Training",
  "schemaName": "app_lab_training"
}
```

Затем импорт с тем же `packageId` применит bundle к уже существующему Application.

3. Откройте operator-приложение: `?mode=operator&app=lab-training`
4. Учётные записи (создаются при старте сервера):

| Пользователь | Пароль | Роль |
|--------------|--------|------|
| `lab-user-a` | `lab-user-a` | operator |
| `lab-user-b` | `lab-user-b` | operator |

Устройства создаются bootstrap-ом: `root.platform.devices.lab-userA-01`, `root.platform.devices.lab-userB-01` (модель `virtual-lab-v1`, driver profile `lab`).

---

## Объекты дерева

| Задание | Объект / дашборд |
|---------|------------------|
| — | Application `root.platform.applications.lab-training`, operator app `root.platform.operator-apps.lab-training` |
| 1 | Пользователи + ACL на `lab-userB-01` |
| 2–4 | Alert rules + correlators под `root.platform.alert-rules.*`, `root.platform.correlators.*` |
| 5–18 | Дашборды `root.platform.dashboards.lab-*` |
| 11 | Отчёт `root.platform.reports.lab-all-devices-table` |

---

## Задания

### 1. Два пользователя и доступ userA к устройству userB

**Цель:** multi-user collaboration с per-object ACL.

**Реализация:**
- Пользователи `lab-user-a`, `lab-user-b` — `LabSecurityBootstrap`
- ACL на `root.platform.devices.lab-userB-01`: owner `lab-user-b`, editor `lab-user-a`
- ACL на `root.platform.devices.lab-userA-01`: owner `lab-user-a`, editor `lab-user-b`

**Проверка:** войдите как `lab-user-a`, откройте Variable editor (`lab-variable-editor`) — редактирование переменных на устройстве B доступно.

---

### 2. Тревога: Event1 → ON, Event2 (Int>20) → OFF

**Цель:** latch через correlator + переменную `alarmLatched`.

**Объекты:**
- Correlator `root.platform.correlators.lab-event1-latch-on` → `SET_VARIABLE alarmLatched=true` на event1
- Correlator `root.platform.correlators.lab-event2-unlatch` → `SET_VARIABLE alarmLatched=false` с `payloadFilterExpr: payload["int"] > 20`

**Проверка:** дашборд `lab-event-gen` или `lab-fan-composite` (индикатор Alarm latched). Вызовите `fireEvent1`, затем `fireEvent2` с int=25.

---

### 3. Тревога через 10 с после sum(Int+Float) ∈ [50, 100]

**Цель:** alert rule с задержкой и sustain.

**Объект:** `root.platform.alert-rules.lab-sum-range-sustained-alert`
- `watchVariable`: `sumIntFloat`
- `conditionExpr`: `self.sumIntFloat["value"] >= 50 && self.sumIntFloat["value"] <= 100`
- `delaySeconds`: 10, `sustainWhileTrue`: true

**Проверка:** на Variable editor задайте `intValue`+`floatValue` так, чтобы сумма была 75, подождите 10 с — событие `labSumRangeAlarm`.

---

### 4. Тревога: sum(table.Int) > 100 + corrective report

**Цель:** alert + correlator `OPEN_OPERATOR_REPORT`.

**Объекты:**
- Alert `root.platform.alert-rules.lab-table-sum-threshold` на `tableIntSum`
- Correlator `root.platform.correlators.lab-open-corrective-report` → отчёт `root.platform.reports.lab-table-corrective`

**Проверка:** добавьте строки в `table` через Form grid, пока `tableIntSum` > 100.

---

### 5. Фильтр Event2: Int>10 OR String contains "abc"

**Цель:** `payloadFilterExpr` в виджете `event-feed`.

**Дашборд:** `root.platform.dashboards.lab-event-gen` — нижний feed «Event 2 log (filtered)» с выражением `int > 10 || string contains abc`.

**Проверка:** события с int≤10 и без «abc» в string не отображаются во втором feed.

---

### 6. Форма Grid Layout

**Дашборд:** `root.platform.dashboards.lab-form-grid`

**Документация:** пример layout в [DASHBOARDS.md](DASHBOARDS.md#grid-layout-форма-function-form-lab-task-6).

---

### 7. Calculator function → calculate()

**Модель:** функция `calculate(inputA, inputB)` на `virtual-lab-v1`.

**Проверка:** вызов через API или дашборд Calculator (задание 9).

---

### 8. Relative model: sum Sine + Sawtooth

**Модель:** `virtual-lab-waves-sum-v1` (RELATIVE) с binding `sumWaves = sineWave + sawtoothWave`.

**Проверка:** дашборд `lab-virtual-overview` — виджет Sum waves.

---

### 9. Calculator grid widget

**Дашборд:** `root.platform.dashboards.lab-calculator` — `function-form` + `value` (результат `sumIntFloat` после calculate; для демо можно смотреть binding sum).

---

### 10. Query всех переменных + edit

**Дашборд:** `root.platform.dashboards.lab-variable-editor` — виджеты `variable-editor` для обоих lab devices.

---

### 11. Relative report: table всех virtual devices

**Отчёт:** `root.platform.reports.lab-all-devices-table` (тип `tree-variables`, pattern `root.platform.devices.lab-*`, variable `table`).

**Проверка:** `POST /api/v1/reports/by-path/run?path=root.platform.reports.lab-all-devices-table`

---

### 12. Посекундный график Sine + Sawtooth

**Дашборд:** `root.platform.dashboards.lab-charts` — `refreshIntervalMs: 1000`, charts с `historyRange: live`.

**Driver:** profile `lab`, `pollIntervalMs` в конфиге runtime.

---

### 13. Pie Chart по table

**Дашборд:** `root.platform.dashboards.lab-pie` — виджет `pie-chart`, source `table`, поля `string` / `int`.

---

### 14. Форма генерации событий + dual log

**Дашборд:** `root.platform.dashboards.lab-event-gen` — две формы `fireEvent1`/`fireEvent2`, два `event-feed` с фильтром по имени события.

---

### 15. Кнопка открывает другой widget (modal)

**Дашборд:** `root.platform.dashboards.lab-modal` — `dashboard-link` с `openMode: modal` → `lab-charts`.

---

### 16. История sine 5 мин: таблица + среднее

**Дашборд:** `root.platform.dashboards.lab-history` — виджет `history-table` на `sineWave`.

---

### 17. SVG кнопка + вентилятор (composite)

**Дашборд:** `root.platform.dashboards.lab-fan-composite` — `composite-widget` с `svg-widget` (`/lab-assets/button.svg` toggle `fanRunning`, `/lab-assets/fan.svg`).

---

### 18. Dashboard virtual devices

**Дашборд:** `root.platform.dashboards.lab-virtual-overview` — chart/value, event-feed, report table.

---

## Virtual Lab Device

| Variable | Тип | Описание |
|----------|-----|----------|
| `sineWave` | DOUBLE | `amplitude * sin(2π t / periodSec)` |
| `sawtoothWave` | DOUBLE | пилообразный сигнал |
| `intValue`, `floatValue` | INTEGER / DOUBLE | конфиг / ручная запись |
| `table` | RECORD_LIST | rows `{int, string}` |
| `sumWaves`, `sumIntFloat`, `tableIntSum` | bindings | вычисляемые |
| `alarmLatched`, `fanRunning` | BOOLEAN | для automation / HMI |

**Events:** `event1`, `event2` (payload `{int, string}`).

**Functions:** `calculate`, `fireEvent1`, `fireEvent2`, `appendTableRow`.

---

## Тесты

| Тест | Назначение |
|------|------------|
| `VirtualLabProfileTest` | driver profile `lab` |
| `LabAutomationTest` | delay alert, correlator payload, SET_VARIABLE |
| `TreeVariablesReportTest` | report type `tree-variables` |
| `LabTrainingBundleTest` | import bundle + key paths |
| `LabSecurityBootstrapTest` | users + ACL |
| `payloadFilter.test.ts` | event-feed OR/AND filter |

Запуск: `./gradlew test` (server), `npm test` в `apps/web-console`.

---

## Связанные документы

- [DASHBOARDS.md](DASHBOARDS.md) — виджеты и Grid Layout
- [AUTOMATION.md](AUTOMATION.md) — alert rules и correlators
- [REPORTS.md](REPORTS.md) — отчёты tree-first
- [SECURITY.md](SECURITY.md) — ACL API
