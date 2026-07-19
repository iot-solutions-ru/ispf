> **Язык:** русская краткая версия. Канонический английский: [../en/manufacturing-patterns.md](../en/manufacturing-patterns.md).

# Производственные паттерны ISPF

> **Статус:** Charter — каталог решений / marketplace. Граница: [ADR-0050](decisions/0050-manufacturing-patterns-as-solutions.md).

Этот документ фиксирует производственные паттерны ISPF как конфигурацию решений: bundle, marketplace-продукт, schema migrations, дерево объектов, BPMN, dashboards, reports и BFF/script functions. Это не новые MES-сущности в базовой платформе.

[ISA-95 catalog](isa95-catalog.md) описывает уровни и иерархию. Этот каталог описывает процессные паттерны: traceability, BoM, routing, quality, integration, documents и portal access.

## Краткий каталог

- **Traceability DAG** — граф lot/material/work-order/operation/equipment/quality в app schema + BFF + dashboards/reports.
- **Nested BoM** — многоуровневые материалы, ревизии, alternates, explosion/where-used через функции решения.
- **Ops dependency graph** — зависимости операций, hold/release/rework через app DAG + BPMN + work queue.
- **CTO configurator** — правила совместимости опций, генерация build package и draft work order.
- **Extensible attributes** — проектные поля через JSON/app schema/variables/blueprint metadata без Java в платформе.
- **QMS lite** — inspection, defect, NCR, disposition, approval workflow, связь с lot/work-order; детали BL-224 в канонической EN версии.
- **Integration catalog** — app outbox/inbox, idempotency, retry/DLQ, connector workflows; skeleton `mes-integration-catalog`; live ERP остается BL-169.
- **Domain MCP tools** — опубликованные BFF/workflow tools с schema, role checks и audit; карта capability -> function в [../en/mes-capability-mcp.md](../en/mes-capability-mcp.md).
- **Documents/labels** — work instructions, travelers, labels, certificates через reports/templates и document registry; platform document engine только после явного platform approval.
- **External portal role** — узкий read-only доступ поставщика/клиента/аудитора через tenant ACL, permissions и отдельный Operator UI.

## Правило границы

Данные и бизнес-термины производства живут в решении. Новая возможность платформы допустима только как generic capability с явным platform approval. MRP/accounting и live ERP ownership не входят в core.

Полная версия: [../en/manufacturing-patterns.md](../en/manufacturing-patterns.md).
