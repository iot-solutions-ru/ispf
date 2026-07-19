import { test, expect } from "@playwright/test";
import {
  mockAuthConfig,
  mockAuthenticatedApi,
  seedAuthSession,
} from "./fixtures/apiMocks";

/** BL-131 — visual regression smoke (2–4% pixel tolerance on stable regions). */

test.describe("visual regression smoke", () => {
  test("login page", async ({ page }) => {
    await mockAuthConfig(page);
    // Pin TZ so aria snapshot is stable across runner locales (normalizeTimeZoneList prepends browser zone).
    await page.addInitScript(() => {
      localStorage.setItem("ispf.ui.timeZone", "UTC");
    });
    const authReady = page.waitForResponse(
      (response) => response.url().includes("/api/v1/auth/config") && response.ok()
    );
    await page.goto("/");
    await authReady;
    const card = page.locator(".login-card");
    await expect(page.getByLabel("Username")).toBeVisible();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
    await expect(card).toMatchAriaSnapshot({
      name: "login-card.aria.yml",
    });
  });

  test("admin explorer", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");
    const shell = page.getByTestId("admin-shell");
    await expect(shell).toBeVisible();
    await expect(page.getByText("Object tree")).toBeVisible();
    await expect(shell).toHaveScreenshot("admin-shell.png");
  });

  test("operator launcher", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");
    const shell = page.getByTestId("operator-shell");
    await expect(shell).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("operator-nav")).toBeVisible();
    await expect(shell).toHaveScreenshot("operator-shell.png");
  });
});
