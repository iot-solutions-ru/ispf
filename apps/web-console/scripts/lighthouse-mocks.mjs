/**
 * Minimal API mocks for Lighthouse operator-shell audit (mirrors e2e/apiMocks essentials).
 */

const MOCK_DASHBOARD_PATH = "root.platform.dashboards.ops-board";
const NOW = "2026-06-28T00:00:00.000Z";

const MOCK_DASHBOARD_LAYOUT = {
  columns: 84,
  rowHeight: 8,
  widgets: [
    {
      id: "w1",
      type: "label",
      title: "E2E KPI",
      text: "42.5 °C",
      x: 0,
      y: 0,
      w: 28,
      h: 14,
    },
  ],
};

export const MOCK_AUTH_SESSION = {
  token: "lighthouse-mock-token",
  username: "admin",
  displayName: "Admin",
  roles: ["admin"],
};

const MOCK_OPERATOR_UI = {
  appId: "e2e-operator",
  title: "E2E Operator",
  defaultDashboard: MOCK_DASHBOARD_PATH,
  dashboards: [{ path: MOCK_DASHBOARD_PATH, title: "Ops board" }],
  alarmBar: {
    enabled: true,
    position: "top",
    minLevel: "WARNING",
    rules: [
      {
        id: "lh-high-temp",
        eventNames: ["high-temp"],
        title: "High temperature",
        minLevel: "WARNING",
      },
    ],
  },
};

const MOCK_DASHBOARD_VIEW = {
  path: MOCK_DASHBOARD_PATH,
  title: "Ops board",
  refreshIntervalMs: 5000,
  layoutJson: JSON.stringify(MOCK_DASHBOARD_LAYOUT),
  layout: MOCK_DASHBOARD_LAYOUT,
};

const EMPTY_CONTEXT = { selection: {}, params: {}, widgets: {} };

function normalizeApiPath(pathname) {
  return pathname.startsWith("/hmi/") ? pathname.slice("/hmi".length) : pathname;
}

function resolveMock(pathname, searchParams) {
  const apiPath = normalizeApiPath(pathname);
  switch (apiPath) {
    case "/api/v1/auth/config":
      return { mode: "local", localLoginEnabled: true };
    case "/api/v1/auth/me":
      return {
        authenticated: true,
        principal: MOCK_AUTH_SESSION.username,
        roles: MOCK_AUTH_SESSION.roles,
        timeZone: "UTC",
      };
    case "/api/v1/info":
      return {
        name: "ISPF",
        shortName: "ISPF",
        version: "0.9.33-lh",
        timestamp: NOW,
        javaVersion: "25",
        springBootVersion: "3.4.0",
        capabilities: [],
      };
    case "/api/v1/operator-apps":
      return [{ appId: "e2e-operator", title: "E2E Operator" }];
    case "/api/v1/operator-apps/e2e-operator/ui":
      return MOCK_OPERATOR_UI;
    case "/api/v1/dashboards/by-path": {
      const path = searchParams.get("path");
      if (path === MOCK_DASHBOARD_PATH) return MOCK_DASHBOARD_VIEW;
      return null;
    }
    case "/api/v1/dashboards/by-path/context": {
      const path = searchParams.get("path");
      if (path === MOCK_DASHBOARD_PATH) {
        return {
          path,
          context: EMPTY_CONTEXT,
          contextJson: JSON.stringify(EMPTY_CONTEXT),
        };
      }
      return null;
    }
    case "/api/v1/events":
    case "/api/v1/alert-rules":
    case "/api/v1/alarm-shelves":
    case "/api/v1/objects/by-path/binding-rules":
    case "/api/v1/objects/by-path/variables":
      return [];
    case "/api/v1/platform/metrics":
      return { timestamp: NOW, sections: [] };
    case "/api/v1/platform/license":
      return {
        installationId: "lh-mock",
        enforce: false,
        mode: "community",
        valid: true,
        message: "mock",
      };
    case "/api/v1/platform/storage/health":
      return { timestamp: NOW, backends: [] };
    default:
      if (apiPath.startsWith("/api/v1/ai/")) {
        return { enabled: false, available: false };
      }
      return null;
  }
}

/** Install fetch/XHR mocks before navigation (works with Lighthouse navigation). */
export function installLighthouseApiMocks(page, origin) {
  const apiOrigin = origin.replace(/\/$/, "");
  return page.evaluateOnNewDocument(
    ({ apiOrigin, session, dashboardView, operatorUi, emptyContext, dashboardPath }) => {
      localStorage.setItem("ispf-auth-session", JSON.stringify(session));

      const routes = {
        "/api/v1/auth/config": { mode: "local", localLoginEnabled: true },
        "/api/v1/auth/me": {
          authenticated: true,
          principal: session.username,
          roles: session.roles,
          timeZone: "UTC",
        },
        "/api/v1/info": {
          name: "ISPF",
          shortName: "ISPF",
          version: "0.9.33-lh",
          timestamp: "2026-06-28T00:00:00.000Z",
          capabilities: [],
        },
        "/api/v1/operator-apps": [{ appId: "e2e-operator", title: "E2E Operator" }],
        "/api/v1/operator-apps/e2e-operator/ui": operatorUi,
        "/api/v1/events": [],
        "/api/v1/alert-rules": [],
        "/api/v1/alarm-shelves": [],
        "/api/v1/objects/by-path/binding-rules": [],
        "/api/v1/objects/by-path/variables": [],
        "/api/v1/platform/metrics": { timestamp: "2026-06-28T00:00:00.000Z", sections: [] },
        "/api/v1/platform/license": { installationId: "lh-mock", enforce: false, mode: "community", valid: true },
        "/api/v1/platform/storage/health": { timestamp: "2026-06-28T00:00:00.000Z", backends: [] },
      };

      const originalFetch = window.fetch.bind(window);
      const normalizeApiPath = (pathname) =>
        pathname.startsWith("/hmi/") ? pathname.slice("/hmi".length) : pathname;
      window.fetch = async (input, init) => {
        const url = typeof input === "string" ? input : input.url;
        let pathname;
        let searchParams;
        try {
          const parsed = new URL(url, window.location.origin);
          pathname = parsed.pathname;
          searchParams = parsed.searchParams;
        } catch {
          return originalFetch(input, init);
        }
        const apiPath = normalizeApiPath(pathname);
        if (!apiPath.startsWith("/api/v1/")) {
          return originalFetch(input, init);
        }
        if (apiPath === "/api/v1/dashboards/by-path" && searchParams.get("path") === dashboardPath) {
          return new Response(JSON.stringify(dashboardView), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          });
        }
        if (apiPath === "/api/v1/dashboards/by-path/context" && searchParams.get("path") === dashboardPath) {
          const body = { path: dashboardPath, context: emptyContext, contextJson: JSON.stringify(emptyContext) };
          return new Response(JSON.stringify(body), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          });
        }
        if (Object.prototype.hasOwnProperty.call(routes, apiPath)) {
          return new Response(JSON.stringify(routes[apiPath]), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          });
        }
        if (apiPath.startsWith("/api/v1/ai/")) {
          return new Response(JSON.stringify({ enabled: false, available: false }), {
            status: 200,
            headers: { "Content-Type": "application/json" },
          });
        }
        return new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      };
    },
    {
      apiOrigin,
      session: MOCK_AUTH_SESSION,
      dashboardView: MOCK_DASHBOARD_VIEW,
      operatorUi: MOCK_OPERATOR_UI,
      emptyContext: EMPTY_CONTEXT,
      dashboardPath: MOCK_DASHBOARD_PATH,
    }
  );
}

export { resolveMock, MOCK_DASHBOARD_PATH };
