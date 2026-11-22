# Examples

Each folder under `examples/` is one sample: an application bundle, an operator screen, a lab, or a marketplace listing. This page is the index. A check in CI fails when a new folder has no `README.md` or is missing from the tables below.

Deploy a bundle with `ispf deploy` or `POST /api/v1/applications/{appId}/deploy`. Guide: [Applications](../docs/en/applications.md) · [Приложения](../docs/ru/applications.md).

## Pick a starter

These run on a local ISPF with the evaluation driver set. No programmable controller is required. The MQTT row still needs a broker. The driver-pack row is a manifest, not a running device.

| You want | Open |
| --- | --- |
| A plant you can copy and rename | [demo-plant](demo-plant/) |
| A mini-CHP station with a simulated load | [mini-tec](mini-tec/) |
| A discrete shop (the demo plant, already forked) | [plant-discrete-mes](plant-discrete-mes/) |
| OEE as availability × performance × quality | [mes-oee-reference](mes-oee-reference/) |
| Building ventilation | [building-hvac-app](building-hvac-app/) |
| A warehouse, a farm, a store, or a fuel station | [warehousetwin](warehousetwin/), [farmtwin](farmtwin/), [storetwin](storetwin/), [oil-control](oil-control/) |
| SQL reports on a small item table | [demo-app](demo-app/) |
| Eighteen training exercises | [lab-training](lab-training/) |
| Temperatures over MQTT (needs a broker) | [lab-mqtt-temperature](lab-mqtt-temperature/) |
| The shape of a driver pack (manifest only) | [driver-pack-sample](driver-pack-sample/) |

The built-in `demo-sensor-01` is not in this folder. It ships with the server. See [Getting started](../docs/en/getting-started.md).

## С чего начать

Строки ниже поднимаются на локальном ISPF с оценочным набором драйверов. Контроллер не нужен. Для MQTT всё же нужен брокер. Строка про пакет драйвера — это манифест, а не работающее устройство.

| Задача | Папка |
| --- | --- |
| Завод, который копируют и переименовывают | [demo-plant](demo-plant/) |
| Мини-ТЭЦ с расчётной нагрузкой | [mini-tec](mini-tec/) |
| Дискретный цех (уже сделанный форк демо-завода) | [plant-discrete-mes](plant-discrete-mes/) |
| OEE как доступность × производительность × качество | [mes-oee-reference](mes-oee-reference/) |
| Вентиляция здания | [building-hvac-app](building-hvac-app/) |
| Склад, ферма, магазин или АЗС | [warehousetwin](warehousetwin/), [farmtwin](farmtwin/), [storetwin](storetwin/), [oil-control](oil-control/) |
| SQL-отчёты по небольшой таблице | [demo-app](demo-app/) |
| Восемнадцать учебных упражнений | [lab-training](lab-training/) |
| Температуры по MQTT (нужен брокер) | [lab-mqtt-temperature](lab-mqtt-temperature/) |
| Как устроен пакет драйвера (только манифест) | [driver-pack-sample](driver-pack-sample/) |

Встроенный `demo-sensor-01` лежит не здесь, а в поставке сервера. См. [Быстрый старт](../docs/ru/getting-started.md).

## Applications

| Example | What it is | Ships |
| --- | --- | --- |
| [building-hvac-app](building-hvac-app/) | Building HVAC | bundle |
| [claims-control](claims-control/) | Claims desk | bundle |
| [coffegate](coffegate/) | Coffee machine fleet | bundle |
| [demo-app](demo-app/) | SQL reports on items and categories | bundle |
| [demo-plant](demo-plant/) | Plant template. Fork this, do not install a second copy of the product | bundle |
| [erp-mes-aps](erp-mes-aps/) | ISA-95 planning contour | bundle |
| [erp-mes-cmms](erp-mes-cmms/) | ISA-95 maintenance contour | bundle |
| [erp-mes-core](erp-mes-core/) | ISA-95 core plant contour | bundle |
| [erp-mes-pharma](erp-mes-pharma/) | ISA-95 / ISA-88 pharma contour | bundle |
| [erp-mes-printing](erp-mes-printing/) | ISA-95 printing contour | bundle |
| [farmtwin](farmtwin/) | Farm twin | bundle |
| [it-infra-monitoring](it-infra-monitoring/) | IT infrastructure monitoring | bundle |
| [lab-mqtt-temperature](lab-mqtt-temperature/) | Four MQTT temperature sensors and a map | bundle |
| [lab-training](lab-training/) | Training exercises. Walkthrough: [Lab training](../docs/en/lab-training.md) | bundle |
| [laminate-flow-mes](laminate-flow-mes/) | Flow production (laminate), planned by line | bundle |
| [marketplace-demo](marketplace-demo/) | Tiny bundle for a marketplace install check | bundle |
| [mes-cto](mes-cto/) | Configure-to-order | bundle |
| [mes-defect-demo](mes-defect-demo/) | Defect routing | bundle |
| [mes-integration-catalog](mes-integration-catalog/) | MES integration catalog | bundle |
| [mes-oee-reference](mes-oee-reference/) | OEE reference | bundle |
| [mes-ogp-events](mes-ogp-events/) | OGP event registration | bundle |
| [mes-platform](mes-platform/) | Dispatch, OEE, batch, and quality | bundle |
| [mes-platform-production](mes-platform-production/) | Same platform, shaped for a production deploy | bundle |
| [mes-reference](mes-reference/) | Work order, tank, and loading rack | bundle |
| [mes-wrangel-cps](mes-wrangel-cps/) | Raw-material workshop for one named site (shift accounting, reports) | bundle |
| [mini-tec](mini-tec/) | Mini-CHP twin. Load is calculated in SQL, not by the virtual driver | bundle |
| [oil-control](oil-control/) | Fuel station balance | bundle |
| [ot-alarm-classify](ot-alarm-classify/) | Classify an OT alarm | bundle |
| [plant-discrete-mes](plant-discrete-mes/) | Discrete shop forked from [demo-plant](demo-plant/) | bundle |
| [plant-north-fork](plant-north-fork/) | SQL reports forked from [demo-app](demo-app/) | bundle |
| [platform-primitive](platform-primitive/) | One device, one dashboard, no industry model | bundle |
| [predictive](predictive/) | Predictive maintenance | bundle |
| [simulator-profiles](simulator-profiles/) | Profiles for the virtual driver | bundle |
| [spreadsheet-demo](spreadsheet-demo/) | Spreadsheet widget bound to an object | bundle |
| [storetwin](storetwin/) | Retail store twin | bundle |
| [tank-farm](tank-farm/) | Tank farm | bundle |
| [warehouse-app](warehouse-app/) | Warehouse reference | bundle |
| [warehousetwin](warehousetwin/) | Warehouse twin | bundle |
| [woodmes](woodmes/) | Wood-processing plant | bundle |

## Operator screens

These are hosted pages at `/apps/<appId>/`. Where the row names an application, that bundle is the sibling folder.

| Screen | Application |
| --- | --- |
| [claims-control-ui](claims-control-ui/) | [claims-control](claims-control/) |
| [coffegate-ui](coffegate-ui/) | [coffegate](coffegate/) |
| [demo-plant-ui](demo-plant-ui/) | [demo-plant](demo-plant/) |
| [doom](doom/) | Freedoom, hosted by ISPF. Not a plant application |
| [farmtwin-ui](farmtwin-ui/) | [farmtwin](farmtwin/) |
| [laminate-flow-ui](laminate-flow-ui/) | [laminate-flow-mes](laminate-flow-mes/) |
| [m11-monitor-ui](m11-monitor-ui/) | [it-infra-monitoring](it-infra-monitoring/) |
| [marketplace-ui-pack-demo](marketplace-ui-pack-demo/) | Sample ui-pack, not an industry app |
| [mes-wrangel-ui](mes-wrangel-ui/) | [mes-wrangel-cps](mes-wrangel-cps/) |
| [oil-control-ui](oil-control-ui/) | [oil-control](oil-control/) |
| [plant-discrete-mes-ui](plant-discrete-mes-ui/) | [plant-discrete-mes](plant-discrete-mes/) |
| [predictive-ui](predictive-ui/) | [predictive](predictive/) |
| [storetwin-ui](storetwin-ui/) | [storetwin](storetwin/) |
| [warehousetwin-ui](warehousetwin-ui/) | [warehousetwin](warehousetwin/) |
| [woodmes-ui](woodmes-ui/) | [woodmes](woodmes/) |

## Labs, listings, and tooling

| Example | What it is |
| --- | --- |
| [agent-metrics-dashboard](agent-metrics-dashboard/) | Dashboard layout for AI tool metrics |
| [alert-rule-evolution](alert-rule-evolution/) | How an alert rule is extended over time |
| [analytics-platform](analytics-platform/) | Analytics samples for a site and for a larger estate |
| [analytics-rolling-avg](analytics-rolling-avg/) | Rolling average on the historian |
| [certification](certification/) | Exam questions as JSON. Nothing here grades them |
| [ci-template](ci-template/) | Workflow template for validating a bundle |
| [driver-pack-sample](driver-pack-sample/) | Manifest for a driver pack, no protocol code |
| [escalation-templates](escalation-templates/) | Workflow templates for an acknowledgement timeout |
| [field-sites](field-sites/) | Checklists for a named line. The sample site is [mes-line-a01](field-sites/mes-line-a01/) |
| [framed-serial-tcp](framed-serial-tcp/) | ASCII frames over TCP |
| [historian-sla-dashboard](historian-sla-dashboard/) | Layout for historian query timing |
| [historian-tiers](historian-tiers/) | Deploy profiles for historian tiers |
| [lab-mqtt-historian-stress](lab-mqtt-historian-stress/) | MQTT load against the historian |
| [licensed-driver-pack-pilot](licensed-driver-pack-pilot/) | Staging notes for a licensed driver pack |
| [marketplace-analytics-energy-pack-demo](marketplace-analytics-energy-pack-demo/) | Listing for the energy-delta analytics pack |
| [marketplace-analytics-pack-demo](marketplace-analytics-pack-demo/) | Listing for a KPI analytics pack |
| [marketplace-catalog](marketplace-catalog/) | Listing copies read by the marketplace validator. Not a second product |
| [marketplace-symbol-hvac-demo](marketplace-symbol-hvac-demo/) | HVAC symbol pack listing |
| [mssql-single-engine](mssql-single-engine/) | Opt-in env for metadata + JDBC journal/history on one SQL Server DB (Prod Yes B2 on Evaluation = capped JDBC, DELETE+90d; not product SWITCH / 10y / 20s) |
| [solution-tck](solution-tck/) | Scripts that score a lab server. Not an application bundle |

`python tools/examples-index/check_examples_index.py` checks this page against the folders on disk.
