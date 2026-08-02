import { test, expect } from "@playwright/test";

/**
 * Browser smoke for MapLibre GL JS v6 (ESM worker + WebGL2 + raster style used by MapWidget).
 * Page: /map-smoke/ (Vite multi-page entry).
 */
test.describe("maplibre v6 map widget smoke", () => {
  test("loads map canvas, marker, and popup", async ({ page }) => {
    const pageErrors: string[] = [];
    page.on("pageerror", (err) => pageErrors.push(String(err)));

    await page.goto("/map-smoke/");
    await expect(page.getByTestId("map-smoke-root")).toBeVisible();
    await expect(page.locator("canvas.maplibregl-canvas")).toBeVisible({ timeout: 30_000 });
    await expect
      .poll(() => page.locator("html").getAttribute("data-map-ready"), { timeout: 30_000 })
      .toBe("1");
    await expect(page.locator("html")).not.toHaveAttribute("data-map-error");

    const marker = page.getByTestId("smoke-marker");
    await expect(marker).toBeVisible();
    await marker.click();
    await expect(page.getByText("Truck 1")).toBeVisible();

    expect(pageErrors, `page errors: ${pageErrors.join(" | ")}`).toEqual([]);
  });
});
