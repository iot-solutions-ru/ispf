# Мини-ТЭЦ (эталон) на ISPF

Эталонный цифровой двойник АСУ ТП: 3×ГПУ, ГРПБ, РУМБ 10/0,4 кВ, ДГУ, нагрузочный модуль, станционный hub, защиты, интегрированная operator HMI (мнемосхема §5.1 требований ТЭЦ).

## Архитектура

Логика mini-TEC живёт в **`application/reference/minitec`** (модели, bootstrap, script-функции). Платформа предоставляет только общие механизмы: `setSelection` на mimic, `pulse` / `script` functions, `writeVariable` step, virtual driver profiles `tec-*`.

После запуска `ispf-server` с fixtures решение поднимается автоматически (`MiniTecPlatformBootstrap`).

**Operator UI (главный экран):** `?mode=operator&app=mini-tec` → дашборд `mini-tec-hmi`

**Зоны мнемосхемы:** Генерация / Газ / Электроснабжение (вкладки на HMI)

## Демо-пользователи (RBAC по участкам)

| Логин | Зона |
|-------|------|
| `operator-gas` / `operator-gas` | только ГРПБ |
| `operator-electrical` / `operator-electrical` | РУМБ, нагрузочный модуль |
| `operator` / `operator` | полный доступ (без ACL) |
| `operator-engineer` / `operator-engineer` | диагностика всех узлов станции (без zone ACL) |

## Учебные сценарии

1. **Пожар на ГРПБ:** дашборд ГРПБ → «Пожар (учебный)» или `simulate_fire` → alarm bar, подсветка, gas trip всех ГПУ.
2. **Недомощность:** поднять нагрузку на load-module → `stationUnderpower` → авто-сброс нагрузки.
3. **Карточка оборудования:** клик по ГПУ на схеме → панель справа (P, статус, sparkline 1h).
4. **Экспорт:** кнопка «Экспорт PNG» на виджете мнемосхемы; CSV тренда — кнопка «История» на chart.

## REST API (интеграция)

| Назначение | Endpoint |
|------------|----------|
| Телеметрия | `GET /api/v1/objects/by-path/variables?path=...` |
| История | `GET /api/v1/objects/by-path/variables/{name}/history?from=&to=` |
| События | `GET /api/v1/events?objectPath=...` |
| Отчёты | `GET /api/v1/applications/mini-tec/reports/tec-daily-energy/export?format=csv` |
| WebSocket | `WS /ws/objects` (live updates) |

OPC-UA: для полевого ГПУ можно заменить virtual driver на `opcua` profile (см. [docs/en/drivers.md](../../docs/en/drivers.md)).

## Дерево объектов

| Путь | Назначение |
|------|------------|
| `root.platform.devices.mini-tec-plant` | Папка станции |
| `...gpu-01` … `gpu-03` | Газопоршневые модули |
| `...grpb` | ГРПБ |
| `...rumb-10kv` | РУМБ 10/0,4 кВ |
| `...dgu` | ДГУ |
| `...load-module` | Нагрузочный модуль |
| `...station-hub` | Агрегаты, KPI, защиты шин |

## Симуляция

Virtual driver profiles: `tec-gpu`, `tec-grpb`, `tec-rumb`, `tec-dgu`, `tec-load` (`VirtualTecPoll.java`).

## Деплой bundle

```http
POST /api/v1/applications/mini-tec/deploy
Content-Type: application/json

<file: examples/mini-tec/bundle.json>
```

## Синхронизация фикстур с prod (VPS)

После правок на `ispf.iot-solutions.ru` выгрузите эталон обратно в репозиторий:

```bash
# на VPS
bash /opt/ispf/bin/export-minitec-fixtures.sh /tmp/minitec-fixtures-export

# локально: scp -r root@ispf...:/tmp/minitec-fixtures-export ./examples/mini-tec/vps-export
# скопировать dashboards → packages/ispf-server/src/main/resources/bootstrap/mini-tec/dashboards/
# mimics → bootstrap/mini-tec-*.json, bundle → examples/mini-tec/bundle.json
```

Скрипт: [`deploy/export-minitec-fixtures.sh`](../../deploy/export-minitec-fixtures.sh). Дашборды и мнемосхемы читает `MiniTecFixtureDocuments` / `MiniTecMimicDocument` при bootstrap.

## Потребители (номинальная нагрузка)

| Потребитель | Переменная | кВт |
|-------------|------------|-----|
| Потребитель 1 | `consumerLoad1Kw` | 2430 |
| Потребитель 2 | `consumerLoad2Kw` | 1200 |
| Резерв | `consumerLoad3Kw` | 500 |

Установленная мощность станции: **4440 кВт** (3×1480).

## Модели

`mini-tec-gpu-v1`, `mini-tec-grpb-v1`, `mini-tec-rumb-v1`, `mini-tec-dgu-v1`, `mini-tec-load-module-v1`, `mini-tec-station-hub-v1` — см. `MiniTecBlueprintBootstrap.java`.

См. также [docs/en/reference-mini-tec-walkthrough.md](../../docs/en/reference-mini-tec-walkthrough.md).
