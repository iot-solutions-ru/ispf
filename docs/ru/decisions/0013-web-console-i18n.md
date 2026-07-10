> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0013-web-console-i18n.md](../../en/decisions/0013-web-console-i18n.md).

# ADR-0013: Web Console i18n

## Статус

Принято (23 июня 2026 г.)

## Контекст

Строки UI веб-консоли были захардкожены примерно в 120 TSX-файлах — в основном на русском со смешанными английскими терминами продукта. Операторам и администраторам нужно единообразное переключение языка в admin shell, operator HMI, редакторах, federation и AI Studio.

## Решение

1. **Библиотека:** `react-i18next` + `i18next` с JSON-файлами namespace под `apps/web-console/src/locales/{locale}/`.
2. **Каноническая локаль:** **английский (`en`)** — новые UI-ключи добавляются только в `en/*.json`; `fallbackLng: 'en'`.
3. **Поддерживаемые локали:** `en`, `ru`, `de`, `zh` (упрощённый китайский). Украинский исключён.
4. **Сохранение выбора:** ключ `localStorage` `ispf.ui.locale`, query-параметр URL `?lang=`, язык браузера как начальная подсказка, по умолчанию `en`.
5. **Переключатель:** `LocaleSwitcher` в topbar администратора, topbar оператора и на карточке входа.
6. **Производные локали:** `ru`, `de`, `zh` генерируются из `en` через `tools/i18n/generate-locales.py` (пакетный Google Translate) + глоссарий в `tools/i18n/glossary.json`.
7. **CI:** `npm run i18n:check` сравнивает наборы ключей по локалям (канон = `en`).

## В объёме

Статический UI веб-консоли: labels, кнопки, вкладки, empty states, сообщения валидации, определённые во frontend-коде.

## Вне объёма

- `displayName` в дереве объектов и пользовательский контент
- Текст BPMN-диаграмм, manifest/bundle dashboards
- `error.message` сервера без маппинга error-code
- Текст ответов AI-агента (язык вывода LLM следует prompt, а не UI locale)

## Последствия

- Добавление UI-текста требует ключа в `en` + `t()` + `npm run i18n:translate` + `npm run i18n:check`.
- Размер bundle растёт из-за четырёх наборов locale JSON (приемлемо для admin console).
- Термины SCADA glossary должны совпадать с [GLOSSARY](../GLOSSARY.md).

## Связанные материалы

- [roadmap.md § Phase 19](../roadmap.md)
- [web-console](../web-console.md) § Localization
