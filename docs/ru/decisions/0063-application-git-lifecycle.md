# ADR-0063: Git lifecycle приложения (полный sync бандла)

> **Status:** Accepted — 2026-10-11. Канон: [en/decisions/0063-application-git-lifecycle.md](../../en/decisions/0063-application-git-lifecycle.md). Hub: [doc-status.md](../doc-status.md).

Кратко: APPLICATION синхронизирует с git команды **тот же полный bundle**, что уже деплоит платформа. Сейчас: Connect / Disconnect / Status. План: Pull release → deploy, Push tree → PR. Версии = `bundle.version` + git SHA + deploy history. Git остаётся у команды; ISPF — клиент с credential. Канон EN описывает фазы и таблицу статуса.
