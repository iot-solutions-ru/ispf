(function () {
  "use strict";
  if (window.__ispfOperatorAgentWidget) {
    return;
  }
  window.__ispfOperatorAgentWidget = true;

  var SCRIPT = document.currentScript;
  var APP_ID = (SCRIPT && SCRIPT.getAttribute("data-ispf-app-id")) || appIdFromPath();
  if (!APP_ID || APP_ID === "_platform") {
    return;
  }

  var ru = /^(ru|uk|be)\b/i.test(document.documentElement.lang || navigator.language || "");
  var t = ru
    ? {
        open: "Открыть ИИ-помощник",
        title: "ИИ-помощник",
        hint: "только чтение · это приложение",
        placeholder: "Спросите про аварии, тренды, отчёты…",
        send: "Отправить",
        cancel: "Стоп",
        thinking: "Думаю…",
        llm: "ИИ не настроен на сервере.",
        login: "Войдите в консоль, чтобы пользоваться помощником.",
        loginAction: "Войти",
        failed: "Ошибка запроса",
      }
    : {
        open: "Open AI assistant",
        title: "AI assistant",
        hint: "read-only · this application",
        placeholder: "Ask about alarms, trends, reports…",
        send: "Send",
        cancel: "Stop",
        thinking: "Thinking…",
        llm: "AI is not configured on the server.",
        login: "Sign in to the console to use the assistant.",
        loginAction: "Sign in",
        failed: "Request failed",
      };

  var host = document.createElement("div");
  host.setAttribute("data-ispf-operator-agent", APP_ID);
  document.documentElement.appendChild(host);
  var root = host.attachShadow({ mode: "open" });

  root.innerHTML =
    '<style>' +
    ':host{all:initial}' +
    '*{box-sizing:border-box;font-family:ui-sans-serif,system-ui,Segoe UI,sans-serif}' +
    '.fab{position:fixed;right:1.25rem;bottom:1.25rem;z-index:2147483000;width:3.25rem;height:3.25rem;' +
    'border-radius:999px;border:1px solid #30363d;background:linear-gradient(145deg,#238636,#1f6feb);' +
    'color:#fff;font-weight:700;font-size:0.85rem;box-shadow:0 8px 24px rgba(0,0,0,.35);cursor:pointer}' +
    '.fab.open{background:#30363d}' +
    '.drawer{position:fixed;right:1rem;bottom:5rem;z-index:2147483000;width:min(420px,calc(100vw - 2rem));' +
    'max-height:min(70vh,640px);display:none;flex-direction:column;border:1px solid #30363d;border-radius:12px;' +
    'background:#161b22;color:#e6edf3;box-shadow:0 16px 48px rgba(0,0,0,.45);overflow:hidden}' +
    '.drawer.open{display:flex}' +
    '.head{display:flex;justify-content:space-between;gap:.75rem;padding:.75rem .9rem;border-bottom:1px solid #30363d}' +
    '.head strong{display:block;font-size:.95rem}' +
    '.sub{margin:.15rem 0 0;font-size:.8rem;color:#8b949e}' +
    '.log{flex:1;overflow:auto;padding:.75rem;min-height:8rem}' +
    '.bubble{margin:0 0 .65rem;padding:.55rem .7rem;border-radius:10px;font-size:.88rem;line-height:1.45;white-space:pre-wrap;word-break:break-word}' +
    '.user{background:#1f6feb22;border:1px solid #1f6feb55;margin-left:1.5rem}' +
    '.agent{background:#21262d;border:1px solid #30363d;margin-right:1.5rem}' +
    '.compose{border-top:1px solid #30363d;padding:.65rem .75rem .75rem;display:flex;align-items:stretch;gap:.5rem}' +
    'textarea{flex:1;min-height:2.5rem;height:2.5rem;resize:none;background:#21262d;color:#e6edf3;border:1px solid #30363d;border-radius:8px;padding:.45rem .55rem;font:inherit}' +
    '.actions{display:flex;align-items:stretch;gap:.5rem;flex-shrink:0}' +
    'button.act{border:1px solid #30363d;background:#21262d;color:#e6edf3;border-radius:8px;padding:0 .85rem;cursor:pointer;font:inherit;height:2.5rem;min-height:2.5rem}' +
    'button.pri{background:#1f6feb;border-color:#1f6feb;color:#fff}' +
    'button:disabled{opacity:.5;cursor:not-allowed}' +
    'a{color:#58a6ff}' +
    '</style>' +
    '<button type="button" class="fab" aria-expanded="false"></button>' +
    '<div class="drawer" role="dialog">' +
    '<div class="head"><div><strong></strong><p class="sub"></p></div>' +
    '<button type="button" class="act close" aria-label="×">×</button></div>' +
    '<div class="log"></div>' +
    '<form class="compose"><textarea rows="2"></textarea><div class="actions">' +
    '<button type="button" class="act cancel" hidden></button>' +
    '<button type="submit" class="act pri"></button></div></form></div>';

  var fab = root.querySelector(".fab");
  var drawer = root.querySelector(".drawer");
  var logEl = root.querySelector(".log");
  var form = root.querySelector("form");
  var input = root.querySelector("textarea");
  var sendBtn = root.querySelector(".pri");
  var cancelBtn = root.querySelector(".cancel");
  var closeBtn = root.querySelector(".close");
  var titleEl = root.querySelector("strong");
  var subEl = root.querySelector(".sub");

  fab.textContent = "AI";
  fab.title = t.open;
  fab.setAttribute("aria-label", t.open);
  titleEl.textContent = t.title;
  subEl.textContent = APP_ID + " · " + t.hint;
  input.placeholder = t.placeholder;
  sendBtn.textContent = t.send;
  cancelBtn.textContent = t.cancel;

  var open = false;
  var pending = false;
  var sessionId = null;
  var pollTimer = null;

  function setOpen(next) {
    open = next;
    fab.classList.toggle("open", open);
    fab.textContent = open ? "×" : "AI";
    fab.setAttribute("aria-expanded", open ? "true" : "false");
    drawer.classList.toggle("open", open);
    if (open) {
      input.focus();
      ensureStatus();
    }
  }

  fab.addEventListener("click", function () {
    setOpen(!open);
  });
  closeBtn.addEventListener("click", function () {
    setOpen(false);
  });

  function looksLikeJwt(value) {
    if (!value || value.length < 40 || value.length > 8192) {
      return false;
    }
    var parts = value.split(".");
    return parts.length === 3 && parts[0].indexOf("eyJ") === 0;
  }

  function looksLikeSessionToken(value) {
    if (!value || value.length < 16 || value.length > 8192 || /\s/.test(value)) {
      return false;
    }
    if (looksLikeJwt(value)) {
      return true;
    }
    if (/^[a-f0-9]{32}$/i.test(value)) {
      return true;
    }
    return value.length >= 24 && /^[A-Za-z0-9._\-+/=]+$/.test(value);
  }

  function tokenFromRaw(raw) {
    if (!raw) {
      return "";
    }
    var trimmed = String(raw).trim();
    if (!trimmed) {
      return "";
    }
    if (trimmed.charAt(0) === "{") {
      try {
        var parsed = JSON.parse(trimmed);
        var candidate = parsed && (parsed.token || parsed.accessToken);
        return looksLikeSessionToken(candidate) ? candidate : "";
      } catch (e) {
        return "";
      }
    }
    return looksLikeSessionToken(trimmed) ? trimmed : "";
  }

  function readStore(store, key) {
    try {
      return store.getItem(key);
    } catch (e) {
      return null;
    }
  }

  function tokenFromStore(store) {
    if (!store) {
      return "";
    }
    var known = ["ispf-auth-session", "oca_token", "ispf-token", "access_token", "accessToken"];
    var i;
    var tok;
    for (i = 0; i < known.length; i++) {
      tok = tokenFromRaw(readStore(store, known[i]));
      if (tok) {
        return tok;
      }
    }
    try {
      for (i = 0; i < store.length; i++) {
        var key = store.key(i);
        if (!key || !/token|auth|session|jwt/i.test(key)) {
          continue;
        }
        tok = tokenFromRaw(readStore(store, key));
        if (tok) {
          return tok;
        }
      }
    } catch (e) {
      /* ignore quota / private mode */
    }
    return "";
  }

  function token() {
    if (typeof window !== "undefined" && looksLikeSessionToken(window.__ISPF_AUTH_TOKEN)) {
      return window.__ISPF_AUTH_TOKEN;
    }
    return tokenFromStore(window.sessionStorage) || tokenFromStore(window.localStorage) || "";
  }

  function headers(json) {
    var h = {};
    if (json) {
      h["Content-Type"] = "application/json";
    }
    var tok = token();
    if (tok) {
      h.Authorization = "Bearer " + tok;
    }
    return h;
  }

  function api(path, opts) {
    return fetch("/api/v1/operator-apps/" + encodeURIComponent(APP_ID) + "/agent" + path, Object.assign({ credentials: "same-origin" }, opts || {})).then(
      function (res) {
        return res.text().then(function (text) {
          if (!res.ok) {
            throw new Error(text || res.status);
          }
          return text ? JSON.parse(text) : {};
        });
      }
    );
  }

  function addBubble(role, text) {
    var div = document.createElement("div");
    div.className = "bubble " + role;
    div.textContent = text;
    logEl.appendChild(div);
    logEl.scrollTop = logEl.scrollHeight;
    return div;
  }

  var statusReady = false;
  var providerOk = false;
  var loginPoll = null;
  var loginPromptShown = false;

  function stopLoginPoll() {
    if (loginPoll) {
      clearInterval(loginPoll);
      loginPoll = null;
    }
  }

  function showLogin() {
    if (loginPromptShown) {
      return;
    }
    loginPromptShown = true;
    logEl.innerHTML = "";
    var p = document.createElement("p");
    p.className = "sub";
    p.textContent = t.login + " ";
    var a = document.createElement("a");
    a.href = "/?mode=operator&app=" + encodeURIComponent(APP_ID);
    a.textContent = t.loginAction;
    p.appendChild(a);
    logEl.appendChild(p);
    sendBtn.disabled = true;
    if (!loginPoll) {
      loginPoll = setInterval(function () {
        if (token()) {
          stopLoginPoll();
          loginPromptShown = false;
          logEl.innerHTML = "";
          ensureStatus();
        }
      }, 800);
    }
  }

  function ensureStatus() {
    if (statusReady) {
      return;
    }
    if (!token()) {
      showLogin();
      return;
    }
    stopLoginPoll();
    api("/status", { headers: headers() })
      .then(function (data) {
        statusReady = true;
        loginPromptShown = false;
        providerOk = !!(data.provider && data.provider.available);
        if (data.title) {
          subEl.textContent = data.title + " · " + t.hint;
        }
        if (!providerOk) {
          addBubble("agent", t.llm);
          sendBtn.disabled = true;
        } else {
          sendBtn.disabled = false;
        }
      })
      .catch(function (err) {
        addBubble("agent", t.failed + ": " + (err && err.message ? err.message : err));
      });
  }

  function stopPoll() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  cancelBtn.addEventListener("click", function () {
    if (!sessionId || !pending) {
      return;
    }
    api("/sessions/" + encodeURIComponent(sessionId) + "/cancel", {
      method: "POST",
      headers: headers(),
    }).catch(function () {});
  });

  form.addEventListener("submit", function (ev) {
    ev.preventDefault();
    var text = (input.value || "").trim();
    if (!text || pending || !token()) {
      return;
    }
    input.value = "";
    addBubble("user", text);
    pending = true;
    sendBtn.disabled = true;
    cancelBtn.hidden = false;
    var thinking = addBubble("agent", t.thinking);

    var send = function (sid) {
      sessionId = sid;
      pollTimer = setInterval(function () {
        api("/sessions/" + encodeURIComponent(sid) + "/progress", { headers: headers() })
          .then(function (p) {
            var steps = p.steps || [];
            var tools = steps.filter(function (s) {
              return s.type === "tool";
            }).length;
            if (tools) {
              thinking.textContent = t.thinking + " (" + tools + ")";
            }
          })
          .catch(function () {});
      }, 500);
      return api("/sessions/" + encodeURIComponent(sid) + "/messages", {
        method: "POST",
        headers: headers(true),
        body: JSON.stringify({ message: text, uiLocale: ru ? "ru" : "en" }),
      });
    };

    var chain = sessionId
      ? send(sessionId)
      : api("/sessions", { method: "POST", headers: headers() }).then(function (s) {
          return send(s.sessionId);
        });

    chain
      .then(function (data) {
        thinking.textContent = data.summary || "";
      })
      .catch(function (err) {
        thinking.textContent = t.failed + ": " + (err && err.message ? err.message : err);
      })
      .finally(function () {
        pending = false;
        sendBtn.disabled = statusReady && !providerOk;
        cancelBtn.hidden = true;
        stopPoll();
      });
  });

  input.addEventListener("keydown", function (ev) {
    if (ev.key === "Enter" && !ev.shiftKey) {
      ev.preventDefault();
      form.requestSubmit();
    }
  });

  function appIdFromPath() {
    var m = location.pathname.match(/^\/apps\/([^/]+)/);
    return m ? decodeURIComponent(m[1]) : "";
  }
})();
