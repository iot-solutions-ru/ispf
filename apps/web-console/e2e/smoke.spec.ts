import { test, expect } from "@playwright/test";
import {
  doubleClickTreeObjectByLabel,
  expandTreeTo,
  mockAuthConfig,
  mockAuthenticatedApi,
  MOCK_DASHBOARD_PATH,
  MOCK_DEVICE_PATH,
  seedAuthSession,
  selectTreeObjectByLabel,
  waitForDashboardLoad,
  waitForObjectEditor,
} from "./fixtures/apiMocks";

test.describe("login page", () => {
  test("renders local sign-in form with mocked auth config", async ({ page }) => {
    await mockAuthConfig(page);
    await page.goto("/");

    await expect(page.getByRole("heading", { name: "ISPF" })).toBeVisible();
    await expect(page.getByLabel("Username")).toBeVisible();
    await expect(page.getByLabel("Password")).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });
});

test.describe("admin explorer", () => {
  test("opens Explorer shell with mocked session", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expect(page.getByText("Admin console")).toBeVisible();
    await expect(page.getByRole("button", { name: "Explorer" })).toHaveClass(/active/);
    await expect(page.getByText("Object tree")).toBeVisible();
    await expect(page.locator(".tree-label", { hasText: "Platform" })).toBeVisible();
  });
});

test.describe("explorer device variables", () => {
  test("selects a device in the tree (deep link)", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Devices");
    const sensorRow = page.locator(".tree-row").filter({ hasText: "Lab sensor" });
    await sensorRow.scrollIntoViewIfNeeded();
    await sensorRow.click();

    await expect(page).toHaveURL(/lab-sensor/);
  });

  test("expands the tree to a nested device row", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Devices");
    await expect(page.locator(".tree-label", { hasText: "Lab sensor" })).toBeVisible();
  });

  test("shows Variables tab after device inspector loads", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Devices");
    const editorLoaded = waitForObjectEditor(page, MOCK_DEVICE_PATH);
    await selectTreeObjectByLabel(page, "Lab sensor");
    await editorLoaded;

    await expect(page.locator(".properties-editor")).toBeVisible();
    await page.getByRole("button", { name: "Variables" }).click();
    await expect(page.locator(".property-name", { hasText: "temperature" })).toBeVisible();
  });
});

test.describe("dashboard builder", () => {
  test("opens dashboard builder from tree double-click", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Dashboards");
    const dashboardLoaded = waitForDashboardLoad(page, MOCK_DASHBOARD_PATH);
    await doubleClickTreeObjectByLabel(page, "Ops board");
    await dashboardLoaded;

    await expect(page.locator(".dashboard-shell")).toBeVisible();
    await expect(page.getByText("Dashboard · HMI")).toBeVisible();
    await expect(page.getByText("42.5 °C")).toBeVisible();
  });

  test("opens dashboard builder via Open in editor", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Dashboards");
    await selectTreeObjectByLabel(page, "Ops board");
    await expect(page.getByRole("button", { name: "Open in editor" })).toBeVisible();

    const dashboardLoaded = waitForDashboardLoad(page, MOCK_DASHBOARD_PATH);
    await page.getByRole("button", { name: "Open in editor" }).click();
    await dashboardLoaded;

    await expect(page.locator(".dashboard-shell")).toBeVisible();
    await expect(page.locator(".dashboard-shell").getByRole("heading", { name: "Ops board" })).toBeVisible();
  });
});

test.describe("dashboard preview", () => {
  test("loads dashboard layout via API mock", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    const payload = await page.evaluate(async (path) => {
      const response = await fetch(`/api/v1/dashboards/by-path?path=${encodeURIComponent(path)}`);
      return response.json() as Promise<{ title: string; layoutJson: string }>;
    }, MOCK_DASHBOARD_PATH);

    expect(payload.title).toBe("Ops board");
    expect(payload.layoutJson).toContain("42.5 °C");
  });
});

test.describe("binding expression builder", () => {
  test("opens platform function catalog on Bindings tab", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");

    await expandTreeTo(page, "Devices");
    const editorLoaded = waitForObjectEditor(page, MOCK_DEVICE_PATH);
    await selectTreeObjectByLabel(page, "Lab sensor");
    await editorLoaded;

    await page.locator("nav.tabs").getByRole("button", { name: "Bindings" }).click();
    await page.waitForResponse(
      (response) =>
        response.url().includes("/api/v1/objects/by-path/binding-rules")
        && response.ok(),
      { timeout: 15_000 },
    );
    await expect(page.getByRole("button", { name: "+ Rule" })).toBeVisible();
    await page.getByRole("button", { name: "+ Rule" }).click();

    await expect(page.getByRole("heading", { name: "New rule" })).toBeVisible();
    const modal = page.locator(".modal");
    await modal.locator(".binding-expression-field").first().getByRole("button", { name: "Functions" }).click();
    const catalog = modal.locator(".platform-binding-catalog");
    await expect(catalog).toBeVisible();
    await expect(catalog.locator("code", { hasText: "movingAvg" }).first()).toBeVisible();
    await expect(catalog.getByRole("button", { name: "Build…" }).first()).toBeVisible();
  });
});

test.describe("operator deep link", () => {
  test("loads demo operator manifest from public bundle", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByRole("strong").filter({ hasText: "Demo Application" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Admin console" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Готовые позиции" })).toBeVisible();
  });
});

