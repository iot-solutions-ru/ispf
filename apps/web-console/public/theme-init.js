// Theme bootstrap — runs before first paint to avoid a theme flash.
// Kept as an external script so the page works under `script-src 'self'` CSP.
(function () {
  var key = "ispf-theme";
  var pref = "system";
  try {
    var stored = localStorage.getItem(key);
    if (stored === "light" || stored === "dark" || stored === "system") pref = stored;
  } catch (e) {}
  var theme =
    pref === "system"
      ? window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light"
      : pref;
  document.documentElement.dataset.theme = theme;
  document.documentElement.dataset.themePreference = pref;
})();
