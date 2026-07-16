# Аудит документации (2026-07-08)

> **Статус:** Internal — Структура, ссылки. Теги: [doc-status](../en/doc-status.md).

## Структура

| Путь | Назначение |
|------|------------|
| [docs/README.md](../README.md) | Двуязычный хаб (ссылки на EN + RU оглавления) |
| [docs/en/](../en/) | **Английский (канон)** — kebab-case |
| [docs/ru/](.) | **Русский** — вычитанные версии |

## Соглашение об именах

- **Было:** `SCADA_MIMIC.md`, `ROADMAP_PHASE25.md`
- **Стало:** `scada-mimic.md`, затем `roadmap-phase-25.md` (выделение фаз 25–33)
- **09.07.2026:** Фазы 25–33 снова влита в единый [roadmap](roadmap.md); `roadmap-phase-25.md` — только redirect stub
- **ADR:** `decisions/0001-app-platform-boundary.md` (тот же шаблон, lowercase)

## Проверка ссылок

- Внутренние `.md`-ссылки в `docs/en` и `docs/ru`: **0 битых** (проверено `audit_links.py`)
- Ссылки по репозиторию обновлены на `docs/en/...` через `fix_repo_links.py`
- Перекрёстные ссылки ADR: `../roadmap.md`, `../deployment.md` и т.д.
- Юридические файлы в корне репо: `../../LICENSE-COMMERCIAL.md`, `../../CLA.md`

## Языковая политика

| Расположение | Правило |
|--------------|---------|
| `docs/en/` | Канонический технический английский |
| `docs/ru/` | Вычитанные русские версии + баннер `> **Язык:** русская версия (вычитка)` |
| RU-native (glossary, operator-guide, product, widgets, …) | Исходный русский текст + тот же баннер |

## Вычитка (2026-07-08)

- Машинный перевод заменён **ручной вычиткой** на ключевых страницах + глоссарий терминов (`proofread_ru_terms.py`).
- Полностью вычитаны: scorecard, acceleration-program, architecture, getting-started, roadmap (единый, вкл. фазы 25–33), scada, drivers, deployment, security, marketplace, testing, symbol-marketplace, partner-program, agent-regression, hmi-quality-gates, **все ADR (0001–0037)**, **все reference-walkthrough**.
- ADR и walkthrough — терминологическая правка; углубить по запросу.

## Анонимизация (публичная документация)

В git не должно быть прод-хостов, публичных IP, SSH-пользователей/портов и личных GitHub org. Замены — как в [en/documentation-audit.md § Anonymization](../en/documentation-audit.md#anonymization-policy-public-docs).

Шаблоны lab — [`examples/`](../../examples/); реальные `deploy/lab-*` — только локально (gitignore).

```bash
python deploy/tools/anonymize-repo.py
rg 'ispf\.iot-solutions|84\.42|iot-solutions\.ru|m5\.wqtt|Michaael/' --glob '!deploy/tools/anonymize-repo.py' --glob '!docs/*/documentation-audit.md'
```

## Скрипты сопровождения

```bash
python tools/docs-audit/migrate_docs.py      # полная миграция (деструктивно)
python tools/docs-audit/fix_repo_links.py    # ссылки в репо → docs/en/
python tools/docs-audit/fix_remaining_links.py
python tools/docs-audit/audit_links.py       # CI: exit 1 при битых внутренних ссылках
python tools/docs-audit/proofread_ru_terms.py             # глоссарий терминов (не re-translate)
python tools/docs-audit/translate_to_ru.py --workers 6   # только первичный черновик EN→RU
python tools/docs-audit/fix_ru_banners.py                 # баннеры и пути en/…
python tools/docs-audit/sync_ru_links.py                  # восстановить href после перевода
```

## Известные follow-up

- Пересборка context pack: `python tools/ai-pack/build.py`
