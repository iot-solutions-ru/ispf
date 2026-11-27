/*
 * Makes the already-built platform console circles draggable.
 * The React console sets data-ispf-drag="1" and owns the gesture itself;
 * this script then leaves the button alone. Position key matches the ui-pack assistant.
 */
(function () {
  if (window.__ISPF_CONSOLE_FAB_DRAG) {
    return;
  }
  window.__ISPF_CONSOLE_FAB_DRAG = true;

  var POS_KEY = "ispf-operator-agent-pos";
  var SIZE = 52;
  var SELECTOR = ".admin-copilot-fab, .operator-agent-fab";

  function viewport() {
    return { width: window.innerWidth, height: window.innerHeight };
  }

  function clamp(pos) {
    var box = viewport();
    return {
      x: Math.min(Math.max(8, pos.x), Math.max(8, box.width - SIZE - 8)),
      y: Math.min(Math.max(8, pos.y), Math.max(8, box.height - SIZE - 8)),
    };
  }

  function readPos() {
    try {
      var raw = localStorage.getItem(POS_KEY);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && typeof parsed.x === "number" && typeof parsed.y === "number") {
          return clamp(parsed);
        }
      }
    } catch (ignore) {}
    return clamp({ x: window.innerWidth - 72, y: window.innerHeight - 72 });
  }

  function applyButton(fab, pos) {
    var sheet = document.getElementById("ispf-console-fab-pos");
    if (!sheet) {
      sheet = document.createElement("style");
      sheet.id = "ispf-console-fab-pos";
      document.head.appendChild(sheet);
    }
    var css =
      ".admin-copilot-fab,.operator-agent-fab{left:" +
      pos.x +
      "px !important;top:" +
      pos.y +
      "px !important;right:auto !important;bottom:auto !important;}";
    var drawer = drawerFor(fab);
    if (drawer && fab.classList.contains("open")) {
      var box = viewport();
      var width = Math.min(420, box.width - 16);
      var height = drawer.offsetHeight || 360;
      var left = pos.x + SIZE - width;
      if (left < 8) {
        left = 8;
      }
      if (left + width > box.width - 8) {
        left = Math.max(8, box.width - width - 8);
      }
      var top = pos.y - height - 12;
      if (top < 8) {
        top = pos.y + 64;
      }
      if (top + height > box.height - 8) {
        top = Math.max(8, box.height - height - 8);
      }
      css +=
        ".admin-copilot-drawer,.operator-agent-drawer{left:" +
        left +
        "px !important;top:" +
        top +
        "px !important;right:auto !important;bottom:auto !important;width:" +
        width +
        "px !important;}";
    }
    sheet.textContent = css;
  }

  function drawerFor(fab) {
    var parent = fab.parentElement;
    if (!parent) {
      return null;
    }
    return parent.querySelector(".admin-copilot-drawer, .operator-agent-drawer");
  }

  function hook(fab) {
    if (fab.dataset.ispfDrag === "1" || fab.dataset.consoleDrag === "1") {
      return;
    }
    fab.dataset.consoleDrag = "1";
    fab.style.cursor = "grab";
    fab.style.touchAction = "none";
    var hint = fab.getAttribute("data-move-hint") || "Можно перетащить";
    var title = fab.getAttribute("title") || fab.getAttribute("aria-label") || "";
    if (title.indexOf(hint) < 0) {
      fab.title = title ? title + ". " + hint : hint;
    }
    applyButton(fab, readPos());

    var drag = null;
    fab.addEventListener("pointerdown", function (event) {
      if (fab.dataset.ispfDrag === "1") {
        return;
      }
      if (event.button != null && event.button !== 0) {
        return;
      }
      var pos = readPos();
      drag = {
        id: event.pointerId,
        sx: event.clientX,
        sy: event.clientY,
        ox: pos.x,
        oy: pos.y,
        moved: false,
      };
      try {
        fab.setPointerCapture(event.pointerId);
      } catch (ignore) {}
    });
    fab.addEventListener("pointermove", function (event) {
      if (!drag || drag.id !== event.pointerId) {
        return;
      }
      var dx = event.clientX - drag.sx;
      var dy = event.clientY - drag.sy;
      if (!drag.moved && Math.abs(dx) + Math.abs(dy) < 6) {
        return;
      }
      drag.moved = true;
      fab.dataset.dragging = "1";
      fab.style.cursor = "grabbing";
      applyButton(fab, clamp({ x: drag.ox + dx, y: drag.oy + dy }));
    });
    function endDrag(event) {
      if (!drag || drag.id !== event.pointerId) {
        return;
      }
      var moved = drag.moved;
      var next = clamp({
        x: drag.ox + (event.clientX - drag.sx),
        y: drag.oy + (event.clientY - drag.sy),
      });
      drag = null;
      fab.dataset.dragging = "";
      fab.style.cursor = "grab";
      if (!moved) {
        return;
      }
      fab.dataset.dragged = "1";
      applyButton(fab, next);
      try {
        localStorage.setItem(POS_KEY, JSON.stringify(next));
      } catch (ignore) {}
    }
    fab.addEventListener("pointerup", endDrag);
    fab.addEventListener("pointercancel", endDrag);
    fab.addEventListener(
      "click",
      function (event) {
        if (fab.dataset.dragged !== "1") {
          return;
        }
        fab.dataset.dragged = "";
        event.preventDefault();
        event.stopPropagation();
      },
      true
    );
    fab.addEventListener("click", function () {
      setTimeout(scan, 0);
      setTimeout(scan, 300);
    });
  }

  function scan() {
    document.querySelectorAll(SELECTOR).forEach(hook);
    document.querySelectorAll(SELECTOR).forEach(function (fab) {
    if (fab.dataset.consoleDrag === "1" && fab.dataset.dragging !== "1" && fab.dataset.ispfDrag !== "1") {
      applyButton(fab, readPos());
    }
    });
  }

  var scheduled = false;
  function scanSoon() {
    if (scheduled) {
      return;
    }
    scheduled = true;
    requestAnimationFrame(function () {
      scheduled = false;
      scan();
    });
  }

  var observer = new MutationObserver(scanSoon);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener("resize", scanSoon);
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", scanSoon);
  } else {
    scanSoon();
  }
})();
