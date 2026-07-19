(function () {
  var root = document.documentElement;
  var key = "ispf-site-theme";
  var btn = document.getElementById("theme-toggle");

  function apply(theme) {
    root.setAttribute("data-theme", theme);
    if (btn) {
      btn.setAttribute("aria-pressed", theme === "dark" ? "true" : "false");
      btn.textContent = theme === "dark" ? "Light" : "Dark";
    }
  }

  var saved = localStorage.getItem(key);
  if (saved === "light" || saved === "dark") {
    apply(saved);
  } else {
    apply("light");
  }

  if (btn) {
    btn.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      localStorage.setItem(key, next);
      apply(next);
    });
  }

  if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    var visual = document.querySelector(".hero-visual img");
    if (visual) {
      window.addEventListener(
        "scroll",
        function () {
          var y = Math.min(window.scrollY, 240);
          visual.style.transform = "translateY(" + y * 0.06 + "px)";
        },
        { passive: true }
      );
    }
  }
})();
