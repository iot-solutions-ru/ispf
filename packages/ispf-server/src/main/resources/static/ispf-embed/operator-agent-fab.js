/**
 * Operator AI FAB for hosted ui-packs and external SPAs (mes-demo).
 * Uses the Bearer already used by the page — never logs in itself.
 * Talks only to /operator-apps/{data-app-id}/agent so scope, instructions,
 * and knowledge documents stay those of that operator application.
 */
(function () {
  var script =
    document.currentScript ||
    document.querySelector('script[src*="operator-agent-fab.js"]');
  if (!script || window.__ISPF_OPERATOR_AGENT_FAB) {
    return;
  }
  window.__ISPF_OPERATOR_AGENT_FAB = true;

  var appId = (script.getAttribute("data-app-id") || "").trim() || appIdFromLocation();
  var apiBase = (script.getAttribute("data-api-base") || "/api/v1").replace(/\/$/, "");
  if (!appId) {
    return;
  }

  var ru = /^(ru)\b/i.test(
    (document.documentElement && document.documentElement.lang) || navigator.language || ""
  );
  var text = ru
    ? {
        open: "Открыть ИИ-помощника",
        title: "ИИ-помощник",
        readOnly: "только чтение",
        docs: "документов",
        newChat: "Новый чат",
        close: "Закрыть",
        send: "Отправить",
        placeholder: "Спросите про это приложение…",
        thinking: "Думаю…",
        unavailable: "Языковая модель недоступна",
        failed: "Не удалось отправить",
        suggestions: [
          "Какие тревоги сейчас активны?",
          "Покажи сводку за смену",
          "Что в очереди задач?",
        ],
      }
    : {
        open: "Open AI assistant",
        title: "AI assistant",
        readOnly: "read-only",
        docs: "documents",
        newChat: "New chat",
        close: "Close",
        send: "Send",
        placeholder: "Ask about this application…",
        thinking: "Thinking…",
        unavailable: "Language model is unavailable",
        failed: "Send failed",
        suggestions: ["Which alarms are active?", "Summarize this shift", "What is in the task queue?"],
      };

  var capturedToken = "";
  var capturedUser = "";
  var sessionId = null;
  var messages = [];
  var pending = false;
  var open = false;
  var status = null;
  var root = null;
  var fab = null;

  hookFetch();
  hookXhr();
  window.addEventListener("storage", onStorage);
  setInterval(syncVisibility, 1000);
  syncVisibility();

  function appIdFromLocation() {
    var match = (location.pathname || "").match(/^\/apps\/([^/]+)/);
    return match ? decodeURIComponent(match[1]) : "";
  }

  function hookFetch() {
    if (!window.fetch || window.fetch.__ispfAgentHook) {
      return;
    }
    var original = window.fetch.bind(window);
    function wrapped(input, init) {
      try {
        noteRequest(requestUrl(input), requestAuth(input, init), requestMethod(input, init));
      } catch (ignore) {
        /* capture must not break the pack */
      }
      return original(input, init);
    }
    wrapped.__ispfAgentHook = true;
    window.fetch = wrapped;
  }

  function hookXhr() {
    var proto = XMLHttpRequest && XMLHttpRequest.prototype;
    if (!proto || proto.__ispfAgentHook) {
      return;
    }
    var open = proto.open;
    var setHeader = proto.setRequestHeader;
    proto.open = function (method, url) {
      this.__ispfMethod = method;
      this.__ispfUrl = url;
      return open.apply(this, arguments);
    };
    proto.setRequestHeader = function (name, value) {
      if (name && String(name).toLowerCase() === "authorization") {
        noteRequest(String(this.__ispfUrl || ""), String(value || ""), String(this.__ispfMethod || ""));
      }
      return setHeader.apply(this, arguments);
    };
    proto.__ispfAgentHook = true;
  }

  function requestUrl(input) {
    if (typeof input === "string") {
      return input;
    }
    if (input && typeof input.url === "string") {
      return input.url;
    }
    return "";
  }

  function requestMethod(input, init) {
    if (init && init.method) {
      return init.method;
    }
    if (input && input.method) {
      return input.method;
    }
    return "GET";
  }

  function requestAuth(input, init) {
    var headers = (init && init.headers) || (input && input.headers);
    if (!headers) {
      return "";
    }
    if (typeof headers.get === "function") {
      return headers.get("Authorization") || headers.get("authorization") || "";
    }
    return headers.Authorization || headers.authorization || "";
  }

  function noteRequest(url, authorization, method) {
    if (url.indexOf("/api/") < 0 && url.indexOf(apiBase) < 0) {
      return;
    }
    if (String(method || "").toUpperCase() === "POST" && url.indexOf("/auth/logout") >= 0) {
      forgetToken();
      return;
    }
    var token = bearerOf(authorization);
    if (token) {
      rememberToken(token);
    }
  }

  function bearerOf(value) {
    var match = String(value || "").match(/Bearer\s+(\S+)/i);
    return match ? match[1] : "";
  }

  function readStoredToken() {
    var stores = [];
    try {
      stores.push(sessionStorage);
    } catch (ignore) {}
    try {
      stores.push(localStorage);
    } catch (ignore) {}
    for (var i = 0; i < stores.length; i++) {
      var session = stores[i].getItem("ispf-auth-session");
      if (session) {
        try {
          var parsed = JSON.parse(session);
          if (parsed && parsed.token) {
            return { token: String(parsed.token), user: String(parsed.username || "") };
          }
        } catch (ignore) {}
      }
    }
    for (var j = 0; j < stores.length; j++) {
      var plain = stores[j].getItem("token");
      if (plain && plain.length > 8) {
        var login = stores[j].getItem("mes_auth_login") || "";
        return { token: plain, user: login };
      }
    }
    return null;
  }

  function onStorage(event) {
    if (!event || event.key === "ispf-auth-session" || event.key === "token" || event.key === null) {
      if (!readStoredToken()) {
        forgetToken();
      }
      syncVisibility();
    }
  }

  function currentIdentity() {
    if (capturedToken) {
      return { token: capturedToken, user: capturedUser };
    }
    return readStoredToken();
  }

  function rememberToken(token) {
    var stored = readStoredToken();
    var user = stored && stored.token === token ? stored.user : capturedUser;
    if (token !== capturedToken) {
      if (capturedToken && token !== capturedToken) {
        resetChat();
      }
      capturedToken = token;
      capturedUser = user || "";
    }
    syncVisibility();
  }

  function forgetToken() {
    capturedToken = "";
    capturedUser = "";
    resetChat();
    if (root) {
      root.hidden = true;
    }
    open = false;
  }

  function resetChat() {
    sessionId = null;
    messages = [];
    pending = false;
    status = null;
    render();
  }

  function syncVisibility() {
    var identity = currentIdentity();
    if (!identity || !identity.token) {
      if (root) {
        root.hidden = true;
      }
      return;
    }
    if (identity.token !== capturedToken) {
      rememberToken(identity.token);
      return;
    }
    ensureUi();
    root.hidden = false;
  }

  function ensureUi() {
    if (root) {
      return;
    }
    var href = script.src || "/ispf-embed/operator-agent-fab.js";
    var css = document.createElement("link");
    css.rel = "stylesheet";
    css.href = href.replace(/\.js(\?.*)?$/, ".css");
    document.head.appendChild(css);

    root = document.createElement("div");
    root.id = "ispf-operator-agent";
    fab = document.createElement("button");
    fab.type = "button";
    fab.className = "ispf-op-agent-fab";
    fab.textContent = "AI";
    fab.setAttribute("aria-label", text.open);
    fab.title = text.open;
    fab.addEventListener("click", function () {
      open = !open;
      fab.classList.toggle("open", open);
      fab.textContent = open ? "×" : "AI";
      fab.setAttribute("aria-expanded", open ? "true" : "false");
      render();
      if (open) {
        loadStatus();
      }
    });
    root.appendChild(fab);
    document.body.appendChild(root);
  }

  function render() {
    if (!root) {
      return;
    }
    var existing = root.querySelector(".ispf-op-agent-drawer");
    if (!open) {
      if (existing) {
        existing.remove();
      }
      return;
    }
    var drawer = existing || document.createElement("div");
    drawer.className = "ispf-op-agent-drawer";
    drawer.setAttribute("role", "dialog");
    drawer.setAttribute("aria-label", text.title);
    var docs =
      status && typeof status.documentCount === "number" && status.documentCount > 0
        ? " · " + status.documentCount + " " + text.docs
        : "";
    var title = (status && status.title) || appId;
    var log = messages
      .map(function (message) {
        return (
          '<div class="ispf-op-agent-bubble ' +
          message.role +
          '">' +
          escapeHtml(message.text) +
          "</div>"
        );
      })
      .join("");
    if (!messages.length && !pending) {
      log = text.suggestions
        .map(function (suggestion) {
          return (
            '<button type="button" class="ispf-op-agent-bubble agent" data-suggest="' +
            escapeAttr(suggestion) +
            '">' +
            escapeHtml(suggestion) +
            "</button>"
          );
        })
        .join("");
    }
    if (pending) {
      log += '<div class="ispf-op-agent-bubble agent">' + escapeHtml(text.thinking) + "</div>";
    }
    var unavailable =
      status && status.provider && status.provider.available === false
        ? '<div class="ispf-op-agent-alert">' + escapeHtml(text.unavailable) + "</div>"
        : "";
    drawer.innerHTML =
      '<div class="ispf-op-agent-head"><div><strong>' +
      escapeHtml(text.title) +
      '</strong><p class="ispf-op-agent-sub">' +
      escapeHtml(title) +
      " · " +
      escapeHtml(text.readOnly) +
      escapeHtml(docs) +
      '</p></div><div class="ispf-op-agent-actions"><button type="button" data-action="new">' +
      escapeHtml(text.newChat) +
      '</button><button type="button" data-action="close" aria-label="' +
      escapeAttr(text.close) +
      '">×</button></div></div>' +
      unavailable +
      '<div class="ispf-op-agent-log">' +
      log +
      '</div><form class="ispf-op-agent-compose"><textarea name="message" placeholder="' +
      escapeAttr(text.placeholder) +
      '"></textarea><div class="ispf-op-agent-compose-row"><button type="submit"' +
      (pending ? " disabled" : "") +
      ">" +
      escapeHtml(text.send) +
      "</button></div></form>";
    if (!existing) {
      root.appendChild(drawer);
    }
    drawer.querySelector('[data-action="close"]').addEventListener("click", function () {
      open = false;
      fab.classList.remove("open");
      fab.textContent = "AI";
      render();
    });
    drawer.querySelector('[data-action="new"]').addEventListener("click", function () {
      if (pending && sessionId) {
        agent("/sessions/" + encodeURIComponent(sessionId) + "/cancel", { method: "POST" }).catch(
          function () {}
        );
      }
      resetChat();
      render();
    });
    drawer.querySelectorAll("[data-suggest]").forEach(function (button) {
      button.addEventListener("click", function () {
        send(button.getAttribute("data-suggest") || "");
      });
    });
    drawer.querySelector("form").addEventListener("submit", function (event) {
      event.preventDefault();
      var field = drawer.querySelector("textarea");
      send(field.value);
      field.value = "";
    });
    var logEl = drawer.querySelector(".ispf-op-agent-log");
    logEl.scrollTop = logEl.scrollHeight;
  }

  function loadStatus() {
    agent("/status", { method: "GET" })
      .then(function (body) {
        status = body;
        render();
      })
      .catch(function (error) {
        if (error && error.status === 401) {
          forgetToken();
        }
      });
  }

  function send(raw) {
    var message = String(raw || "").trim();
    var identity = currentIdentity();
    if (!message || pending || !identity) {
      return;
    }
    messages.push({ role: "user", text: message });
    pending = true;
    render();
    var locale = (document.documentElement && document.documentElement.lang) || (ru ? "ru" : "en");
    ensureSession(identity)
      .then(function (id) {
        sessionId = id;
        return agent("/sessions/" + encodeURIComponent(id) + "/messages", {
          method: "POST",
          body: JSON.stringify({ message: message, uiLocale: locale }),
        });
      })
      .then(function (body) {
        messages.push({
          role: "agent",
          text: (body && (body.summary || body.assistantSummary)) || "",
        });
      })
      .catch(function (error) {
        if (error && error.status === 401) {
          forgetToken();
          return;
        }
        messages.push({
          role: "agent",
          text: text.failed + ": " + ((error && error.message) || "error"),
        });
      })
      .then(function () {
        pending = false;
        render();
      });
  }

  function ensureSession() {
    if (sessionId) {
      return Promise.resolve(sessionId);
    }
    return agent("/sessions", { method: "POST" }).then(function (body) {
      return body.sessionId;
    });
  }

  function agent(path, options) {
    var identity = currentIdentity();
    if (!identity || !identity.token) {
      return Promise.reject(new Error("no session"));
    }
    var headers = {
      Authorization: "Bearer " + identity.token,
      Accept: "application/json",
    };
    if (options.body) {
      headers["Content-Type"] = "application/json";
    }
    return fetch(apiBase + "/operator-apps/" + encodeURIComponent(appId) + "/agent" + path, {
      method: options.method || "GET",
      headers: headers,
      body: options.body,
    }).then(function (response) {
      if (response.status === 401) {
        var error = new Error("unauthorized");
        error.status = 401;
        throw error;
      }
      if (!response.ok) {
        return response.text().then(function (body) {
          throw new Error(body || String(response.status));
        });
      }
      if (response.status === 204) {
        return {};
      }
      return response.json();
    });
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/"/g, "&quot;");
  }
})();
