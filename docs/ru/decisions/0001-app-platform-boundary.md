> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0001-app-platform-boundary.md](../../en/decisions/0001-app-platform-boundary.md).

# ADR-0001: Граница platform и solution (app/platform boundary)

Статус: **Принято**  
Дата: 2026-06-19 (формализовано 2026-06-22)

> **Лицензия (2026-07):** платформа `main` — **GNU AGPL v3** (+ опционально Enterprise). См. [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md). Упоминания Apache 2.0 ниже — исторические.

## Контекст

ISPF — middleware-платформа (framework), а не готовое отраслевое решение. Прикладные команды хотят доставлять логику через deploy API; platform-команда должна расширять только generic-механизмы, иначе `main` разрастается отраслевым Java.

## Решение

1. **Platform (`main`, AGPL v3)** реализует generic engines один раз: object tree, CEL, BPMN, script runtime, drivers, bundle deploy, BFF gateway.
2. **Solution** наполняет механизмы **declarative-конфигурацией**: models, variables, events, functions, workflows, dashboards — через bundle deploy и REST API.
3. **Запрещено в `main`:**
   - отраслевой Java в `packages/ispf-server/`;
   - Flyway-миграции таблиц приложений в platform repo;
   - hardcoded BFF routes под отрасль;
   - дублирование бизнес-логики вне object tree.
4. **Bundle deploy** — упаковка и доставка конфигурации в object tree и app schema, не отдельный runtime.
5. Новые возможности platform оформляются как **REQ-PF** (см. [roadmap](../roadmap.md)).

## Последствия

- Reference apps (`examples/*`) и commercial bundle — вне обязательного ядра или с отдельной лицензией ([0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)).
- Чеклист PR: [PLUGINS](../PLUGINS.md).
- Application API описан в [applications](../applications.md).

## Связанные материалы

- [architecture.md § Основной принцип](../architecture.md#основной-принцип-бизнес-логика-в-механизмах-платформы)
- [0002-dogfooding-gate](0002-dogfooding-gate.md)
