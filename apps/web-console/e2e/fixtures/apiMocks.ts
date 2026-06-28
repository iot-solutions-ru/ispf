import type { Page, Route } from "@playwright/test";

export interface MockAuthSession {
  token: string;
  username: string;
  displayName: string;
  roles: string[];
}

export const MOCK_ADMIN_SESSION: MockAuthSession = {
  token: "e2e-mock-token",
  username: "admin",
  displayName: "Admin",
  roles: ["admin"],
};

const NOW = "2026-06-28T00:00:00.000Z";

function json(route: Route, body: unknown, status = 200) {
  return route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

function mockObject(path: string, type: string, displayName: string) {
  return {
    id: path.replace(/\./g, "-"),
    path,
    type,
    displayName,
    description: "",
    templateId: null,
    createdAt: NOW,
    sortOrder: 0,
    variableNames: [],
    eventNames: [],
  };
}

const TREE_CHILDREN: Record<string, ReturnType<typeof mockObject>[]> = {
  root: [mockObject("root.platform", "PLATFORM", "Platform")],
  "root.platform": [mockObject("root.platform.devices", "DEVICES", "Devices")],
  "root.platform.devices": [mockObject("root.platform.devices.lab-sensor", "DEVICE", "Lab sensor")],
};

export async function mockAuthConfig(page: Page, mode: "local" | "oidc" = "local") {
  await page.route("**/api/v1/auth/config", (route) =>
    json(route, {
      mode,
      localLoginEnabled: mode === "local",
      oidc: mode === "oidc" ? { issuer: "https://keycloak.example/realms/ispf", clientId: "web-console" } : undefined,
    })
  );
}

/** Playwright matches routes in reverse registration order — register the catch-all first. */
export async function mockAuthenticatedApi(page: Page, session: MockAuthSession = MOCK_ADMIN_SESSION) {
  await mockAuthConfig(page);

  await page.route("**/api/v1/**", (route) => {
    if (route.request().method() !== "GET") {
      return json(route, { message: "mock not implemented" }, 501);
    }
    return json(route, []);
  });

  await page.route("**/api/v1/auth/me", (route) =>
    json(route, {
      authenticated: true,
      principal: session.username,
      roles: session.roles,
    })
  );

  await page.route("**/api/v1/auth/login", (route) =>
    json(route, {
      token: session.token,
      username: session.username,
      displayName: session.displayName,
      roles: session.roles,
    })
  );

  await page.route("**/api/v1/info", (route) =>
    json(route, {
      name: "ISPF",
      shortName: "ISPF",
      version: "0.9.32-e2e",
      timestamp: NOW,
      javaVersion: "25",
      springBootVersion: "3.4.0",
      capabilities: [],
    })
  );

  await page.route("**/api/v1/platform/update/**", (route) =>
    json(route, {
      checkEnabled: false,
      applyEnabled: false,
      currentVersion: "0.9.32-e2e",
      latestVersion: null,
      updateAvailable: false,
      releaseName: null,
      releaseUrl: null,
      releaseNotes: null,
      publishedAt: null,
      checkedAt: null,
      checkError: null,
      applyState: "idle",
      applyMessage: null,
      applyStartedAt: null,
    })
  );

  await page.route("**/api/v1/operator-apps", (route) => json(route, []));

  await page.route(/\/api\/v1\/operator-apps\/[^/]+\/ui$/, (route) =>
    json(route, { message: "not found" }, 404)
  );

  await page.route("**/api/v1/objects/leases**", (route) => json(route, []));

  await page.route("**/api/v1/objects**", (route) => {
    const parent = new URL(route.request().url()).searchParams.get("parent") ?? "";
    return json(route, TREE_CHILDREN[parent] ?? []);
  });

  await page.route("**/api/v1/applications/**", (route) => {
    const url = route.request().url();
    if (
      url.includes("/operator-manifest") ||
      url.includes("/operator-ui") ||
      url.includes("/hmi-ui") ||
      url.includes("/reports/")
    ) {
      return json(route, { message: "not found" }, 404);
    }
    return json(route, []);
  });
}

export async function seedAuthSession(page: Page, session: MockAuthSession = MOCK_ADMIN_SESSION) {
  await page.addInitScript((stored) => {
    localStorage.setItem("ispf-auth-session", JSON.stringify(stored));
  }, session);
}
