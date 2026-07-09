#!/usr/bin/env python3
"""Build i18n.js from locales/*.json (ru, en, de, zh)."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOCALES_DIR = ROOT / "locales"
OUT = ROOT / "i18n.js"
LANGS = ("ru", "en", "de", "zh")

I18N_JS_TEMPLATE = """\
(function () {{
  "use strict";

  var STORAGE_THEME = "iot-site-theme";
  var STORAGE_LANG = "iot-site-lang";
  var LANGS = {langs_json};

  var I18N = {i18n_json};

  var currentLang = "ru";

  function t(key) {{
    var pack = I18N[currentLang] || I18N.ru;
    if (pack[key] != null) return pack[key];
    pack = I18N.en || I18N.ru;
    if (pack && pack[key] != null) return pack[key];
    return (I18N.ru && I18N.ru[key]) || key;
  }}

  function applyTheme(theme) {{
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(STORAGE_THEME, theme);
    var btn = document.getElementById("theme-toggle");
    if (btn) {{
      var isLight = theme === "light";
      btn.setAttribute("aria-label", t(isLight ? "theme.dark" : "theme.light"));
      btn.textContent = isLight ? "\\u2600" : "\\u263e";
    }}
  }}

  function applyLanguage(lang) {{
    if (LANGS.indexOf(lang) === -1) lang = "ru";
    currentLang = lang;
    document.documentElement.lang = lang === "zh" ? "zh-Hans" : lang;
    localStorage.setItem(STORAGE_LANG, lang);

    document.querySelectorAll("[data-i18n]").forEach(function (el) {{
      el.textContent = t(el.getAttribute("data-i18n"));
    }});
    document.querySelectorAll("[data-i18n-html]").forEach(function (el) {{
      el.innerHTML = t(el.getAttribute("data-i18n-html"));
    }});
    document.querySelectorAll("[data-i18n-alt]").forEach(function (el) {{
      el.setAttribute("alt", t(el.getAttribute("data-i18n-alt")));
    }});
    document.querySelectorAll("[data-i18n-aria]").forEach(function (el) {{
      el.setAttribute("aria-label", t(el.getAttribute("data-i18n-aria")));
    }});

    document.title = t("meta.title");
    var meta = document.querySelector('meta[name="description"]');
    if (meta) meta.setAttribute("content", t("meta.description"));

    document.querySelectorAll("[data-lang-btn]").forEach(function (btn) {{
      btn.classList.toggle("active", btn.getAttribute("data-lang-btn") === lang);
      btn.setAttribute("aria-pressed", btn.classList.contains("active") ? "true" : "false");
    }});

    var themeBtn = document.getElementById("theme-toggle");
    if (themeBtn) {{
      var theme = document.documentElement.getAttribute("data-theme") || "dark";
      themeBtn.setAttribute("aria-label", t(theme === "light" ? "theme.dark" : "theme.light"));
    }}
  }}

  function initThemeLang() {{
    var theme = localStorage.getItem(STORAGE_THEME);
    if (!theme) {{
      theme = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
    }}
    applyTheme(theme);

    var lang = localStorage.getItem(STORAGE_LANG) || "ru";
    applyLanguage(lang);

    var themeBtn = document.getElementById("theme-toggle");
    if (themeBtn) {{
      themeBtn.addEventListener("click", function () {{
        var next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
        applyTheme(next);
      }});
    }}

    document.querySelectorAll("[data-lang-btn]").forEach(function (btn) {{
      btn.addEventListener("click", function () {{
        applyLanguage(btn.getAttribute("data-lang-btn"));
      }});
    }});
  }}

  window.IotSiteI18n = {{ applyLanguage: applyLanguage, applyTheme: applyTheme, t: t, langs: LANGS }};

  if (document.readyState === "loading") {{
    document.addEventListener("DOMContentLoaded", initThemeLang);
  }} else {{
    initThemeLang();
  }}
}})();
"""


def load_locales() -> dict[str, dict[str, str]]:
    packs: dict[str, dict[str, str]] = {}
    all_keys: set[str] = set()
    for lang in LANGS:
        path = LOCALES_DIR / f"{lang}.json"
        if not path.is_file():
            raise SystemExit(f"Missing locale file: {path}")
        data = json.loads(path.read_text(encoding="utf-8"))
        packs[lang] = data
        all_keys.update(data.keys())
    for lang in LANGS:
        missing = sorted(all_keys - set(packs[lang].keys()))
        extra = sorted(set(packs[lang].keys()) - all_keys)
        if missing:
            raise SystemExit(f"{lang}.json missing {len(missing)} keys, e.g. {missing[:5]}")
        if extra:
            raise SystemExit(f"{lang}.json has extra keys: {extra[:5]}")
    return packs


def main() -> None:
    packs = load_locales()
    key_count = len(next(iter(packs.values())))
    out = I18N_JS_TEMPLATE.format(
        langs_json=json.dumps(list(LANGS), ensure_ascii=False),
        i18n_json=json.dumps(packs, ensure_ascii=False, indent=2),
    )
    OUT.write_text(out, encoding="utf-8")
    print(f"Generated {OUT} ({key_count} keys × {len(LANGS)} langs)")


if __name__ == "__main__":
    main()
