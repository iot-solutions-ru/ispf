# Документация ISPF (русский)

**IoT Solutions Platform Framework** — middleware-платформа для IoT, SCADA и промышленной автоматизации.

> Канонический технический язык: **английский** ([../en/readme.md](../en/readme.md)). Ниже — полное русское оглавление; тела страниц — в `docs/ru/` с баннером ссылки на EN.

## Продукт

| Документ | Аудитория | Описание |
|----------|-----------|----------|
| [Обзор продукта](product.md) | Все | Возможности, сценарии, карта документации |
| [Руководство оператора](operator-guide.md) | Оператор | HMI, work queue, события |
| [Руководство разработчика решений](solution-developer-guide.md) | Разработчик приложений | Deploy, operator UI, bundles |
| [Принципы приложений](application-principles.md) | Разработчик, агент | North star P1–P10 |
| [Публичный API](solution-developer-public-api.md) | Разработчик приложений | Стабильная граница platform ↔ bundle |
| [Глоссарий](glossary.md) | Все | Термины и определения |

## Платформа

| Документ | Описание |
|----------|-------------|
| [Быстрый старт](getting-started.md) | Установка, профили, первый запуск |
| [Архитектура](architecture.md) | Видение, слои, расширяемость |
| [Модель объектов](object-model.md) | Дерево, переменные, события, функции |
| [Привязки](bindings.md) | CEL и platform bindings |
| [Логика платформы](platform-logic.md) | Правила, контекст дашборда |
| [История переменных](variable-history.md) | Time-series, retention |
| [REST API](api.md) | Справочник endpoints |
| [Приложения](applications.md) | Bundles, BFF, scheduler |
| [Отчёты](reports.md) | SQL-отчёты, экспорт CSV |
| [Roadmap](roadmap.md) | Phase 0–24, BL-01…139 |
| [Roadmap Phase 25+](roadmap-phase-25.md) | Excellence Program → 10/10 |
| [Конкурентный scorecard](competitive-scorecard.md) | Матрица готовности по коду |
| [Индекс ADR](decisions/readme.md) | Архитектурные решения |

## SCADA / HMI

| Документ | Описание |
|----------|-------------|
| [Обзор SCADA](scada.md) | Мнемосхемы, символы, привязки |
| [Справочник SCADA mimic](scada-mimic.md) | `diagramJson`, REST API |
| [Библиотека символов](scada-symbol-library.md) | P&ID pack (218 символов) |
| [Каталог виджетов](widgets.md) | Все типы виджетов |
| [Дашборды](dashboards.md) | Layout, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lighthouse, axe, FPS |
| [Виджет spreadsheet](spreadsheet-widget.md) | Формулы и привязки |

## OT / драйверы / historian

| Документ | Описание |
|----------|-------------|
| [Каталог драйверов](drivers.md) | Встроенные драйверы |
| [Driver DDK](driver-ddk.md) | SDK пользовательских драйверов |
| [Driver promotion](driver-promotion.md) | Матрица PRODUCTION |
| [Field pilot playbook](field-pilot-playbook.md) | Runbook OT-валидации |
| [Уровни historian](historian-tiers.md) | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Продакшен rollout |

## AI / автоматизация / MES

| Документ | Описание |
|----------|-------------|
| [AI development](ai-development.md) | ContextPack, tools, Studio |
| [AI agent](ai-agent.md) | Agent API и метрики |
| [Agent knowledge](agent-knowledge.md) | Внутренняя карта маршрутизации агента |
| [Agent regression](agent-regression.md) | CI-гейты сценариев |
| [Автоматизация](automation.md) | Алерты, корреляторы |
| [Workflows](workflows.md) | BPMN-движок |
| [MES platform reference](reference-mes-platform.md) | ISA-95 bundles |

## Эксплуатация

| Документ | Описание |
|----------|-------------|
| [Развёртывание](deployment.md) | Docker, env vars |
| [Профили демостендов](demostands.md) | Prod, lab, edge топологии |
| [Кластер](cluster.md) | Multi-replica |
| [Федерация](federation.md) | Hub / edge peers |
| [Безопасность](security.md) | RBAC, MFA |
| [Наблюдаемость](observability.md) | Метрики, диагностика |
| [Тестирование](testing.md) | Unit, integration |
| [Нагрузочное тестирование](load-testing.md) | Baseline throughput |

## Экосистема

| Документ | Описание |
|----------|-------------|
| [Marketplace](marketplace.md) | Каталог и установка |
| [Symbol marketplace](symbol-marketplace.md) | Распространение symbol pack |
| [Партнёрская программа](partner-program.md) | Уровни интеграторов |
| [Сертификация](certification.md) | Учебные треки |
| [Лицензия](license.md) | Apache 2.0 core |
| [Аудит документации](documentation-audit.md) | Структура, имена, проверка ссылок |

## Быстрые ссылки

- API: `http://localhost:8080/api/v1`
- Web Console: `http://localhost:5173` (dev)
- Operator HMI: `http://localhost:5173?mode=operator`
