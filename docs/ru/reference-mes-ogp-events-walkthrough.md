> **Язык:** русская версия (вычитка). Канонический английский: [en/reference-mes-ogp-events-walkthrough.md](../en/reference-mes-ogp-events-walkthrough.md).

# MES OGP Events — walkthrough (UC-25)

Эталонное приложение `mes-ogp-events` демонстрирует регистрацию производственных событий в ISPF без custom Java в `ispf-server`. Бизнес-логика находится в application bundle под `examples/mes-ogp-events/`.

## Deploy

```powershell
node examples/mes-ogp-events/generate_bundle.mjs
.\examples\mes-ogp-events\deploy.ps1
```

Operator UI: `?mode=operator&app=mes-ogp-events`

Hub object (BFF): `root.platform.devices.ogp-mes-hub`

## Сценарий 1 — Setup delete-now (код 120)

1. Откройте дашборд **Симулятор OGP**.
2. Запустите `ogp_seedDemoStage`, если стадия пуста.
3. Нажмите сигнал **stop** (`ogp_simulateSignal`) — создаётся необработанная строка простоя.
4. Откройте дашборд **Регистрация события** (`ogp-operator-hmi`).
5. В отчёте **Необработанные события** скопируйте ID строки при необходимости.
6. Заполните function-form **Регистрация события**: код **120**, delete-now, roll **0**, метры, время, комментарий → **Зарегистрировать**.
7. Отчёт **Журнал процесса** показывает новую строку со статусом **Удален**.
8. Roll map получает **серый** интервал на roll `0` (след удалённого события).

## Сценарий 2 — Paint splashes register-for-next (код 141)

1. Симулируйте сигнал **defect button** или **knife**.
2. На шаге 1 мастера выберите **141 Брызги краски**, снимите **Удаляем сразу** в форме регистрации.
3. Выберите производственный roll **P1**, сторону PM/Passer, затронутые ряды.
4. Зарегистрируйте — статус журнала **Зарегистрирован**, **белый** интервал на roll map.

## Сценарий 3 — Unprocessed table and grouping (US-139)

1. Подайте два сигнала **stop** в пределах `downtime_group_timeout_sec` (по умолчанию 300 с).
2. Второй сигнал объединяется в одну необработанную строку (`member_count` > 1).
3. Удалите выбранные строки через function-form **Удалить необработанные** (ID из отчёта + комментарий).

## Сценарий 4 — Shift supervisor (US-77)

1. Откройте дашборд **Начальник смены**.
2. Сводный отчёт по простоям группирует события delete-now по order/stage.
3. Function-form **Корректировка записи** обновляет строки журнала (`ogp_updateProcessEvent`).

## Сценарий 5 — Roll release label (US-08)

1. Откройте дашборд **Выпуск рулона**.
2. Просмотрите интервалы roll map и ожидающие дефекты white-map.
3. Запустите `ogp_buildRollLabel` для roll `P1` — снимок сохраняется в `roll_label_data`.

## Сценарий 6 — 1C export (US-17)

1. Каждое зарегистрированное событие (кроме кодов с `exclude_from_1c`) ставит JSON в очередь `integration_outbox`.
2. Schedule `ogp-1c-export` или workflow `ogp-export-1c` запускает `ogp_export1cBatch`.
3. Dev: строки помечаются `sent` без внешнего HTTP (mock URL в `app_setting`).

## Operator dashboards (стандартные виджеты)

| Dashboard | Widgets |
|-----------|---------|
| `ogp-operator-hmi` | `value` strip + `tab-panel`: **Регистрация** (unprocessed report, wizard `function-form` 3 шага, delete form), **Журнал** |
| `ogp-shift-supervisor` | `tab-panel`: **Простои**, **Журнал** (report + edit form), **1С** (integration outbox) |
| `ogp-simulator` | `function-form`, `function`, `event-feed` |
| `ogp-admin-codes` | `report`, `function-form` |
| `ogp-roll-release` | `report`, `function-form` |

## Machine signals (симулятор)

Virtual driver profile `ogp-print-line` на `root.platform.devices.ogp-line-01` обновляет телеметрию (`windingRoll`, `speedMpm`, `meterM`). Используйте function-form дашборда симулятора или `ogp_simulateSignal` для stop / knife / defect / resume.

## CI smoke test

`MesOgpEventsBundleSmokeTest` деплоит bundle, симулирует stop, регистрирует событие 120 delete-now, проверяет журнал и batch export в 1C.

## Regenerate bundle после изменения скриптов

```powershell
node examples/mes-ogp-events/generate_bundle.mjs
Copy-Item examples/mes-ogp-events/bundle.json packages/ispf-server/src/test/resources/mes-ogp-events-bundle.json
```
