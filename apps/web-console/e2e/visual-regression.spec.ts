import { test, expect } from "@playwright/test";
import {
  mockAuthConfig,
  mockAuthenticatedApi,
  seedAuthSession,
} from "./fixtures/apiMocks";

/** BL-131 — visual regression smoke (2% pixel tolerance). */

test.describe("visual regression smoke", () => {
  test("login page", async ({ page }) => {
    await mockAuthConfig(page);
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "ISPF" })).toBeVisible();
    await expect(page).toHaveScreenshot("login-page.png", {
      fullPage: false,
    });
  });

  test("admin explorer", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=admin");
    await expect(page.getByText("Admin console")).toBeVisible();
    await expect(page.getByText("Object tree")).toBeVisible();
    await expect(page).toHaveScreenshot("admin-explorer.png", {
      fullPage: false,
    });
  });

  test("operator launcher", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("operator-nav")).toBeVisible();
    await expect(page).toHaveScreenshot("operator-launcher.png", {
      fullPage: false,
    });
  });
});
