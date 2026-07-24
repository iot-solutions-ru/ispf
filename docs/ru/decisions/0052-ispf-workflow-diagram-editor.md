> **Язык:** русская версия. Канонический английский: [en/decisions/0052-ispf-workflow-diagram-editor.md](../../en/decisions/0052-ispf-workflow-diagram-editor.md).

# ADR-0052: ISPF Workflow Diagram Editor (без bpmn.io)

## Статус

**Accepted** (2026-07-24)

## Решение (кратко)

1. Свой редактор диаграмм (React + SVG), без `bpmn-js` и без watermark bpmn.io.
2. Чужой BPMN — через `adaptForeignBpmn` (перестройка + warnings), не embed Camunda.
3. Движок остаётся `ispf-plugin-workflow` (ADR-0047); расширение элементов — только вместе с parser/engine.
4. Панель свойств — основной путь; XML — для ИИ и крайних случаев.
5. Marketplace «элементы» — palette presets (`bpmn-element-pack`), не новые типы BPMN в парсере.

Подробности и consequences — в EN-версии.
