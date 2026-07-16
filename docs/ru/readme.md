# Документация ISPF (русский)

**IoT Solutions Platform Framework** — self-hosted middleware для IoT, SCADA и промышленной автоматизации.

> Канонический язык: **английский** ([../en/readme.md](../en/readme.md)). Ниже — русское оглавление; тела страниц в `docs/ru/` со ссылкой на EN.  
> Сайт: [ispf.ai](https://ispf.ai) · Репозиторий: [Michaael/IoT-Solutions-Platform](https://github.com/Michaael/IoT-Solutions-Platform)

**Лицензия:** платформа — [GNU AGPL v3](license.md) (опционально commercial dual-license). Ядро **не** Apache 2.0.

---

## Начните здесь (≈15 мин)

1. [Быстрый старт — Попробовать ISPF](getting-started.md#попробовать-ispf-15-минут) — локальный API + Web Console  
2. [Обзор продукта](product.md) — зачем платформа  
3. [Модель объектов](object-model.md) — дерево (устройства, дашборды, правила — узлы)  
4. [Лицензия](license.md) — граница AGPL / Enterprise  

После запуска: `devices.demo-sensor-01` → правило тревоги → `dashboards.demo-sensor` → режим оператора ([JAR](http://localhost:8080?mode=operator) / [Vite](http://localhost:5173?mode=operator)).

---

## Выберите путь

| Я… | Дальше |
|----|--------|
| **Пробую продукт** | [Быстрый старт](getting-started.md) → [Руководство оператора](operator-guide.md) |
| **Делаю решение / bundle** | [Разработчик решений](solution-developer-guide.md) · [Приложения](applications.md) · [Принципы](application-principles.md) |
| **Подключаю OT / драйверы** | [Драйверы](drivers.md) · [Driver DDK](driver-ddk.md) · [Field pilot](field-pilot-playbook.md) |
| **Собираю HMI / SCADA** | [Дашборды](dashboards.md) · [SCADA](scada.md) · [Виджеты](widgets.md) |
| **Автоматизирую тревоги / BPMN** | [Автоматизация](automation.md) · [Workflows](workflows.md) |
| **Использую ИИ-студию / агента** | [AI development](ai-development.md) · [AI agent](ai-agent.md) |
| **Внедряю / эксплуатирую** | [Развёртывание](deployment.md) · [Безопасность](security.md) · [Наблюдаемость](observability.md) |
| **Контрибьючу в ядро** | [Быстрый старт — Contribute](getting-started.md#контрибут-локальный-dev--qa) · [Тестирование](testing.md) · [ADR](decisions/readme.md) |

**Статус vs коммерческие платформы:** [Competitive scorecard](competitive-scorecard.md). Бэклог: [Roadmap](roadmap.md).

**Теги статуса документов:** [doc-status.md](doc-status.md) — Stable · Beta · Draft · Charter · Lab · Internal.

---

## Полный каталог

<details>
<summary><strong>Продукт</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Обзор продукта](product.md) | Stable | Возможности, сценарии, карта документации |
| [Руководство оператора](operator-guide.md) | Stable | HMI, work queue, события |
| [Руководство разработчика решений](solution-developer-guide.md) | Stable | Deploy, operator UI, bundles |
| [Принципы приложений](application-principles.md) | Stable | Target approach P1–P10 |
| [Публичный API](solution-developer-public-api.md) | Stable | Граница platform ↔ bundle |
| [Глоссарий](glossary.md) | Stable | Термины |
| [Web Console](web-console.md) | Stable | Explorer, System, AI Studio |

</details>

<details>
<summary><strong>Платформа</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Быстрый старт](getting-started.md) | Stable | Try ISPF + QA для контрибьюторов |
| [Архитектура](architecture.md) | Stable | Видение, слои, расширяемость |
| [Модель объектов](object-model.md) | Stable | Дерево, переменные, события, функции |
| [Привязки](bindings.md) | Stable | CEL и platform bindings |
| [Логика платформы](platform-logic.md) | Beta | Правила; `@dashboardContext` — зрелость разная |
| [Blueprints](blueprints.md) | Stable | Модели / шаблоны |
| [История переменных](variable-history.md) | Stable | Time-series, retention |
| [API](api.md) | Stable | Справочник endpoints |
| [Приложения](applications.md) | Stable | Bundles, BFF, scheduler |
| [Отчёты](reports.md) | Stable | SQL-отчёты, CSV |
| [Roadmap](roadmap.md) | Charter | Фазы и бэклог |
| [Конкурентный scorecard](competitive-scorecard.md) | Stable | Готовность по коду |
| [Индекс ADR](decisions/readme.md) | Stable | Архитектурные решения |
| [Теги статуса](doc-status.md) | Stable | Словарь статусов |

</details>

<details>
<summary><strong>SCADA / HMI</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Обзор SCADA](scada.md) | Stable | Мнемосхемы, символы, привязки |
| [Справочник SCADA mimic](scada-mimic.md) | Stable | `diagramJson`, REST API |
| [Библиотека символов](scada-symbol-library.md) | Stable | P&ID pack (218 символов) |
| [Каталог виджетов](widgets.md) | Stable | Все типы виджетов |
| [Дашборды](dashboards.md) | Stable | Layout 84×8, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lab | Lighthouse, axe, FPS |
| [Виджет spreadsheet](spreadsheet-widget.md) | Stable | Формулы и привязки |
| [Operator apps](operator-apps.md) | Stable | Конфигурация operator shell |

</details>

<details>
<summary><strong>OT / драйверы / historian</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Каталог драйверов](drivers.md) | Beta | Packs; честность зрелости vs матрица PRODUCTION |
| [Driver DDK](driver-ddk.md) | Stable | SDK пользовательских драйверов |
| [Driver promotion](driver-promotion.md) | Stable | PRODUCTION + ready-for-field |
| [Field pilot playbook](field-pilot-playbook.md) | Lab | Runbook OT-валидации |
| [Уровни historian](historian-tiers.md) | Beta | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Lab | Продакшен rollout |
| [Кластер](cluster.md) | Beta | Multi-replica HA (capability vs demostand) |
| [Messaging](messaging.md) | Stable | NATS / MQTT |

</details>

<details>
<summary><strong>Analytics</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Historian cookbook](analytics-historian-cookbook.md) | Stable | Рецепты, binding rules, rollups |
| [Formulas and packs](analytics-formulas-and-packs.md) | Stable | Пакеты выражений |
| [Analytics roadmap](analytics-platform-roadmap.md) | Charter | Чартер BL-200…210 |
| [Tag catalog API](analytics-tag-catalog.md) | Stable | Analytics-теги |
| [0038-analytics-platform-architecture](decisions/0038-analytics-platform-architecture.md) | Stable | Архитектура |
| [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md) | Stable | Каталог функций |

</details>

<details>
<summary><strong>AI / автоматизация / MES</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [AI development](ai-development.md) | Beta | ContextPack, Studio; BL-178 open |
| [AI agent](ai-agent.md) | Beta | Agent API; gate ≥95% не закрыт |
| [Agent knowledge](agent-knowledge.md) | Internal | Карта маршрутизации агента |
| [Agent regression](agent-regression.md) | Lab | CI-гейты сценариев |
| [Автоматизация](automation.md) | Stable | Алерты, корреляторы |
| [Workflows](workflows.md) | Beta | Подмножество BPMN (не полный 2.0) |
| [MES platform reference](reference-mes-platform.md) | Beta | Marketplace MES; smoke ≠ завод |
| [MES walkthrough](reference-mes-walkthrough.md) | Lab | Сквозной MES-путь |

</details>

<details>
<summary><strong>Эксплуатация</strong> (deploy, labs, CI)</summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Развёртывание](deployment.md) | Stable | Docker, env vars |
| [Профили демостендов](demostands.md) | Lab | Prod, lab, edge |
| [Air-gap](air-gap-deployment.md) | Stable | Офлайн-установка |
| [Федерация](federation.md) | Beta | Hub / edge (caveats зрелости) |
| [Безопасность](security.md) | Stable | RBAC, MFA |
| [Наблюдаемость](observability.md) | Stable | Метрики, диагностика |
| [Тестирование](testing.md) | Stable | Unit, integration |
| [Нагрузочное тестирование](load-testing.md) | Lab | Baseline throughput |
| [Release dogfood](release-dogfood.md) | Internal | Чеклист релиза |
| [Lab training](lab-training.md) | Lab | Учебные packs |

</details>

<details>
<summary><strong>Экосистема и право</strong></summary>

| Документ | Статус | Описание |
|----------|--------|----------|
| [Marketplace](marketplace.md) | Draft | Partial BL-183; не полный GA |
| [Symbol marketplace](symbol-marketplace.md) | Draft | Listing API stub |
| [Партнёрская программа](partner-program.md) | Draft | Design; in-server API stub |
| [Сертификация](certification.md) | Draft | Учебные треки / exams |
| [Лицензия](license.md) | Stable | **AGPL v3** + dual-license |
| [Commercial licensing](commercial-licensing.md) | Stable | Enterprise |
| [License compliance](license-compliance.md) | Stable | Чеклист обязательств |
| [Plugins](plugins.md) | Stable | Core vs packs vs bundles |
| [Аудит документации](documentation-audit.md) | Internal | Структура, ссылки |
| [Полный аудит docs 2026-07-16](../en/documentation-full-audit-2026-07-16.md) | Internal | Content honesty pass |
| [Реестр российского ПО](russian-software-registry.md) | Internal | Правообладатель / RU market |

</details>

---

## Быстрые ссылки (local)

| | |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| Admin (all-in-one JAR) | http://localhost:8080 |
| Operator HMI (all-in-one JAR) | http://localhost:8080?mode=operator |
| Admin (Vite dev) | http://localhost:5173 |
| Operator HMI (Vite dev) | http://localhost:5173?mode=operator |
