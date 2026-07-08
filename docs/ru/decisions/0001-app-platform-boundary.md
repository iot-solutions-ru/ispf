> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0001-app-platform-boundary.md](../../en/decisions/0001-app-platform-boundary.md).

# ADR-0001: Граница platform и solution (app/platform boundary)

Статус: **Принято**  
Дата: 2026-06-19 (формализовано 2026-06-22)

## Контекст

ISPF — middleware-платформа (framework), а не готовое отраслевое решение. Прикладные команды хотят доставлять логику через deploy API; platform-команда должна расширять только generic-механизмы, иначе `main` разрастается отраслевым Java.

## Решение

1. **Platform (`main`, Apache 2.0)** реализует generic engines один раз: object tree, CEL, BPMN, script runtime, drivers, bundle deploy, BFF gateway.
2. **Solution** наполняет механизмы **declarative-конфигурацией**: models, variables, events, functions, workflows, dashboards — через bundle deploy и REST API.
3. **Запрещено в `main`:**
   - отраслевой Java в `packages/ispf-server/`;
   - Flyway-миграции таблиц приложений в platform repo;
   - hardcoded BFF routes под отрасль;
   - дублирование бизнес-логики вне object tree.
4. **Bundle deploy** — упаковка и доставка конфигурации в object tree и app schema, не отдельный runtime.
5. Новые возможности platform оформляются как **REQ-PF** (см. [roadmap.md](../roadmap.md)).

## Последствия

- Reference apps (`examples/*`) и commercial bundle — вне обязательного ядра или с отдельной лицензией ([0003](0003-commercial-bundle-licensing.md)).
- Чеклист PR: [plugins.md](../PLUGINS.md).
- Application API описан в [applications.md](../applications.md).

## Связанные материалы

- [architecture.md § Основной принцип](../architecture.md#основной-принцип-бизнес-логика-в-механизмах-платформы)
- [0002](0002-dogfooding-gate.md)
