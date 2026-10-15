import { describe, expect, it } from "vitest";
import type { DashboardWidget } from "../../types/dashboard";
import { collectDashboardSelectionKeys } from "./selectionKeys";

describe("collectDashboardSelectionKeys", () => {
  it("collects unique trimmed keys from every selection slot, sorted", () => {
    const widgets = [
      { selectionKey: " order " },
      { rowSelectionKey: "line", selectionKey: "order" },
      { cardSelectionKey: "pump" },
      { rowTargetSelectionKey: "batch" },
      { selectionKey: "" },
      { selectionKey: "   " },
    ] as DashboardWidget[];

    expect(collectDashboardSelectionKeys(widgets)).toEqual(["batch", "line", "order", "pump"]);
  });
});