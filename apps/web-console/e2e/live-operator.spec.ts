import { test, expect } from "@playwright/test";
import {
  mockAuthenticatedApi,
  seedAuthSession,
} from "./fixtures/apiMocks";

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const hasLiveCreds = Boolean(username && password);

test.describe("operator shell (mocked)", () => {
  test("loads operator manifest shell with navigation", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-shell")).toContainText("Demo Application", {
      timeout: 15_000,
    });
    await expect(page.getByTestId("operator-nav")).toBeVisible();
  });

  test("switches manifest screens from operator nav", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-nav")).toBeVisible({ timeout: 15_000 });
    await page.getByRole("button", { name: "Temperature trend" }).click();
    await expect(page).toHaveURL(/screen=sensor-trend/);
    await expect(page.getByTestId("operator-shell")).toBeVisible();
  });

  test("operator nav exposes manifest screens", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-nav")).toBeVisible({ timeout: 15_000 });
    const tabs = page.getByTestId("operator-nav").getByRole("button");
    await expect(tabs.first()).toBeVisible();
    expect(await tabs.count()).toBeGreaterThan(2);
  });
});

test.describe("operator live", () => {
  test.skip(!hasLiveCreds, "Set E2E_USERNAME and E2E_PASSWORD for live operator smoke");

  async function signIn(page: import("@playwright/test").Page) {
    await page.goto("/");
    const localLogin = page.getByLabel("Username");
    if (!(await localLogin.isVisible().catch(() => false))) {
      test.skip(true, "Local login form not available (OIDC-only server)");
    }
    await localLogin.fill(username!);
    await page.getByLabel("Password").fill(password!);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByTestId("admin-shell")).toBeVisible({ timeout: 30_000 });
  }

  test("opens operator mode from admin session", async ({ page }) => {
    await signIn(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId("operator-nav")).toBeVisible();
  });

  test("loads demo operator manifest screens", async ({ page }) => {
    await signIn(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole("button", { name: "Готовые позиции" })).toBeVisible();
  });

  test("operator shell exposes navigation tabs", async ({ page }) => {
    await signIn(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-nav")).toBeVisible({ timeout: 30_000 });
    const navButtons = page.getByTestId("operator-nav").getByRole("button");
    await expect(navButtons.first()).toBeVisible();
    expect(await navButtons.count()).toBeGreaterThan(0);
  });
});

test.describe("operator PWA manifest", () => {
  test("serves web manifest with install icons", async ({ request }) => {
    const response = await request.get("/manifest.webmanifest");
    const contentType = response.headers()["content-type"] ?? "";
    if (!response.ok() || contentType.includes("text/html")) {
      test.skip(true, "PWA manifest is emitted by vite build/preview, not the dev server");
    }
    const manifest = await response.json();
    expect(manifest.name).toContain("ISPF");
    expect(manifest.start_url).toContain("mode=operator");
    expect(manifest.icons?.some((icon: { sizes: string }) => icon.sizes === "512x512")).toBeTruthy();
  });
});
