import { describe, expect, it } from "vitest";
import { resolveSpreadsheetRefreshInterval } from "./spreadsheetLiveRefresh";

describe("resolveSpreadsheetRefreshInterval", () => {
  const widget = { type: "spreadsheet" as const, id: "s1", title: "S" };

  it("polls at dashboard interval by default", () => {
    expect(resolveSpreadsheetRefreshInterval(widget, 5000)).toBe(5000);
  });

  it("uses liveRefreshIntervalMs when live=true", () => {
    expect(
      resolveSpreadsheetRefreshInterval({ ...widget, live: true, liveRefreshIntervalMs: 2000 }, 5000)
    ).toBe(2000);
  });

  it("disables polling when live=false", () => {
    expect(resolveSpreadsheetRefreshInterval({ ...widget, live: false }, 5000)).toBe(false);
  });

  it("enforces minimum poll interval", () => {
    expect(resolveSpreadsheetRefreshInterval({ ...widget, live: true, liveRefreshIntervalMs: 100 }, 5000)).toBe(
      500
    );
  });
});
