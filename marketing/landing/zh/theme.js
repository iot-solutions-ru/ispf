(function () {
  "use strict";

  var STORAGE_THEME = "iot-site-theme";
  var LABELS = {
    ru: { light: "Светлая тема", dark: "Тёмная тема" },
    en: { light: "Light theme", dark: "Dark theme" },
    de: { light: "Helles Design", dark: "Dunkles Design" },
    zh: { light: "浅色主题", dark: "深色主题" },
  };

  function pageLang() {
    var lang = document.documentElement.getAttribute("lang") || "ru";
    return lang.indexOf("zh") === 0 ? "zh" : lang.split("-")[0];
  }

  function label(key) {
    var pack = LABELS[pageLang()] || LABELS.en;
    return pack[key] || LABELS.en[key];
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem(STORAGE_THEME, theme);
    var btn = document.getElementById("theme-toggle");
    if (btn) {
      var isLight = theme === "light";
      btn.setAttribute("aria-label", label(isLight ? "dark" : "light"));
      btn.textContent = isLight ? "\u2600" : "\u263e";
    }
  }

  function initTheme() {
    var theme = localStorage.getItem(STORAGE_THEME);
    if (!theme && window.matchMedia) {
      theme = window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
    }
    applyTheme(theme || "dark");
    var btn = document.getElementById("theme-toggle");
    if (btn) {
      btn.addEventListener("click", function () {
        var next = document.documentElement.getAttribute("data-theme") === "light" ? "dark" : "light";
        applyTheme(next);
      });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initTheme);
  } else {
    initTheme();
  }
})();
