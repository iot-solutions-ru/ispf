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

После запуска: `devices.demo-sensor-01` → правило тревоги → `dashboards.demo-sensor` → [режим оператора](http://localhost:5173?mode=operator).

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

---

## Полный каталог

<details>
<summary><strong>Продукт</strong></summary>

| Документ | Аудитория | Описание |
|----------|-----------|----------|
| [Обзор продукта](product.md) | Все | Возможности, сценарии, карта документации |
| [Руководство оператора](operator-guide.md) | Оператор | HMI, work queue, события |
| [Руководство разработчика решений](solution-developer-guide.md) | Разработчик приложений | Deploy, operator UI, bundles |
| [Принципы приложений](application-principles.md) | Разработчик, агент | Target approach P1–P10 |
| [Публичный API](solution-developer-public-api.md) | Разработчик приложений | Граница platform ↔ bundle |
| [Глоссарий](glossary.md) | Все | Термины |
| [Web Console](web-console.md) | Админ | Explorer, System, AI Studio |

</details>

<details>
<summary><strong>Платформа</strong></summary>

| Документ | Описание |
|----------|----------|
| [Быстрый старт](getting-started.md) | Try ISPF + QA для контрибьюторов |
| [Архитектура](architecture.md) | Видение, слои, расширяемость |
| [Модель объектов](object-model.md) | Дерево, переменные, события, функции |
| [Привязки](bindings.md) | CEL и platform bindings |
| [Логика платформы](platform-logic.md) | Правила, контекст дашборда |
| [Blueprints](blueprints.md) | Модели / шаблоны |
| [История переменных](variable-history.md) | Time-series, retention |
| [API](api.md) | Справочник endpoints |
| [Приложения](applications.md) | Bundles, BFF, scheduler |
| [Отчёты](reports.md) | SQL-отчёты, CSV |
| [Roadmap](roadmap.md) | Фазы и бэклог |
| [Конкурентный scorecard](competitive-scorecard.md) | Готовность по коду |
| [Индекс ADR](decisions/readme.md) | Архитектурные решения |

</details>

<details>
<summary><strong>SCADA / HMI</strong></summary>

| Документ | Описание |
|----------|----------|
| [Обзор SCADA](scada.md) | Мнемосхемы, символы, привязки |
| [Справочник SCADA mimic](scada-mimic.md) | `diagramJson`, REST API |
| [Библиотека символов](scada-symbol-library.md) | P&ID pack (218 символов) |
| [Каталог виджетов](widgets.md) | Все типы виджетов |
| [Дашборды](dashboards.md) | Layout, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lighthouse, axe, FPS |
| [Виджет spreadsheet](spreadsheet-widget.md) | Формулы и привязки |
| [Operator apps](operator-apps.md) | Конфигурация operator shell |

</details>

<details>
<summary><strong>OT / драйверы / historian</strong></summary>

| Документ | Описание |
|----------|----------|
| [Каталог драйверов](drivers.md) | Встроенные драйверы |
| [Driver DDK](driver-ddk.md) | SDK пользовательских драйверов |
| [Driver promotion](driver-promotion.md) | Матрица PRODUCTION |
| [Field pilot playbook](field-pilot-playbook.md) | Runbook OT-валидации |
| [Уровни historian](historian-tiers.md) | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Продакшен rollout |
| [Кластер](cluster.md) | Multi-replica HA |
| [Messaging](messaging.md) | NATS / MQTT |

</details>

<details>
<summary><strong>Analytics</strong></summary>

| Документ | Описание |
|----------|----------|
| [Historian cookbook](analytics-historian-cookbook.md) | Рецепты, binding rules, rollups |
| [Formulas and packs](analytics-formulas-and-packs.md) | Пакеты выражений |
| [Analytics roadmap](analytics-platform-roadmap.md) | Чартер BL-200…210 |
| [Tag catalog API](analytics-tag-catalog.md) | Analytics-теги |
| [0038-analytics-platform-architecture](decisions/0038-analytics-platform-architecture.md) | Архитектура |
| [0042-analytics-function-catalog](decisions/0042-analytics-function-catalog.md) | Каталог функций |

</details>

<details>
<summary><strong>AI / автоматизация / MES</strong></summary>

| Документ | Описание |
|----------|----------|
| [AI development](ai-development.md) | ContextPack, tools, Studio |
| [AI agent](ai-agent.md) | Agent API и метрики |
| [Agent knowledge](agent-knowledge.md) | Карта маршрутизации агента |
| [Agent regression](agent-regression.md) | CI-гейты сценариев |
| [Автоматизация](automation.md) | Алерты, корреляторы |
| [Workflows](workflows.md) | BPMN-движок |
| [MES platform reference](reference-mes-platform.md) | ISA-95 bundles |
| [MES walkthrough](reference-mes-walkthrough.md) | Сквозной MES-путь |

</details>

<details>
<summary><strong>Эксплуатация</strong> (deploy, labs, CI — тяжёлые runbook’и)</summary>

| Документ | Описание |
|----------|----------|
| [Развёртывание](deployment.md) | Docker, env vars |
| [Профили демостендов](demostands.md) | Prod, lab, edge |
| [Air-gap](air-gap-deployment.md) | Офлайн-установка |
| [Федерация](federation.md) | Hub / edge peers |
| [Безопасность](security.md) | RBAC, MFA |
| [Наблюдаемость](observability.md) | Метрики, диагностика |
| [Тестирование](testing.md) | Unit, integration |
| [Нагрузочное тестирование](load-testing.md) | Baseline throughput |
| [Release dogfood](release-dogfood.md) | Чеклист релиза |
| [Lab training](lab-training.md) | Учебные packs |

</details>

<details>
<summary><strong>Экосистема и право</strong></summary>

| Документ | Описание |
|----------|----------|
| [Marketplace](marketplace.md) | Каталог и установка |
| [Symbol marketplace](symbol-marketplace.md) | Symbol packs |
| [Партнёрская программа](partner-program.md) | Уровни интеграторов |
| [Сертификация](certification.md) | Учебные треки |
| [Лицензия](license.md) | **AGPL v3** + dual-license |
| [Commercial licensing](commercial-licensing.md) | Enterprise |
| [License compliance](license-compliance.md) | Чеклист обязательств |
| [Plugins](plugins.md) | Core vs packs vs bundles |
| [Аудит документации](documentation-audit.md) | Структура, ссылки |
| [Реестр российского ПО](russian-software-registry.md) | Процесс реестра (опционально) |

</details>

---

## Быстрые ссылки (local)

| | |
| --- | --- |
| API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| Admin console | http://localhost:5173 |
| Operator HMI | http://localhost:5173?mode=operator |
