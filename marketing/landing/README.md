# ISPF Marketing Landing (multi-locale)

Исходники: **`index.html`** (RU) + **`en/`**, **`de/`**, **`zh/`** (генерируются).

## Языки

| URL | Язык |
|-----|------|
| `/` | Русский |
| `/en/` | English |
| `/de/` | Deutsch |
| `/zh/` | 中文 |

Переключатель в шапке — обычные ссылки, без JS-i18n.

## Редактирование

1. Правьте **`index.html`** (русский мастер).
2. Добавьте/обновите строки в **`locales/translations.json`** (`en`, `de`, `zh` — одинаковые ключи, русский текст → перевод).
3. Сгенерируйте страницы и соберите prod:

```powershell
cd marketing/landing
python merge_translations.py   # locales/*.json ← translations.json
python generate_locales.py     # en/de/zh/index.html из index.html
python build.py                # dist/ с inline assets + theme.js
```

## Структура

```
marketing/landing/
  index.html          # RU
  en/index.html
  de/index.html
  zh/index.html
  theme.js            # только светлая/тёмная тема
  locales/
    translations.json # все переводы (мастер)
    en.json de.json zh.json
  generate_locales.py
  build.py
  dist/               # output для VPS
```

## Публикация

| Канал | URL |
|-------|-----|
| **VPS (prod)** | https://www.iot-solutions.ru |
| GitHub Pages | https://michaael.github.io/IoT-Solution-Site/ |

```powershell
# синхронизировать landing/ в IoT-Solution-Site, затем:
cd IoT-Solution-Site
.\deploy\vps-deploy-direct.ps1
```

Старый **`i18n.js`** больше не используется на prod.
