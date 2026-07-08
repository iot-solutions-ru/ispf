import { describe, expect, it } from "vitest";
import {
  DASHBOARD_COLUMNS,
  DASHBOARD_ROW_HEIGHT,
  emptyLayout,
  normalizeDashboardLayout,
  resolveDashboardLayout,
} from "./dashboard";

describe("resolveDashboardLayout", () => {
  it("prefers layoutJson when it contains widgets", () => {
    const layout = resolveDashboardLayout({
      path: "root.platform.dashboards.demo",
      title: "Demo",
      refreshIntervalMs: 5000,
      layoutJson: JSON.stringify({
        columns: DASHBOARD_COLUMNS,
        rowHeight: DASHBOARD_ROW_HEIGHT,
        widgets: [{ id: "a", type: "value", title: "A", x: 14, y: 7, w: 21, h: 14 }],
      }),
      layout: {
        columns: DASHBOARD_COLUMNS,
        rowHeight: DASHBOARD_ROW_HEIGHT,
        widgets: [{ id: "b", type: "value", title: "B", x: 0, y: 0, w: 1, h: 1 }],
      },
    });
    expect(layout.widgets[0]?.id).toBe("a");
    expect(layout.widgets[0]?.x).toBe(14);
    expect(layout.rowHeight).toBe(DASHBOARD_ROW_HEIGHT);
  });

  it("falls back to parsed layout object when layoutJson is invalid", () => {
    const layout = resolveDashboardLayout({
      path: "root.platform.dashboards.demo",
      title: "Demo",
      refreshIntervalMs: 5000,
      layoutJson: "{not-json",
      layout: {
        columns: DASHBOARD_COLUMNS,
        rowHeight: DASHBOARD_ROW_HEIGHT,
        widgets: [{ id: "b", type: "value", title: "B", x: 28, y: 21, w: 14, h: 14 }],
      },
    });
    expect(layout.widgets[0]?.id).toBe("b");
    expect(layout.widgets[0]?.x).toBe(28);
    expect(layout.rowHeight).toBe(DASHBOARD_ROW_HEIGHT);
  });

  it("returns empty layout when view is missing", () => {
    expect(resolveDashboardLayout(undefined)).toEqual(emptyLayout());
  });
});

describe("normalizeDashboardLayout", () => {
  it("uses fine grid defaults for new layouts", () => {
    expect(normalizeDashboardLayout({})).toEqual(emptyLayout());
  });

  it("scales legacy 12-column widgets on fine grid", () => {
    const layout = normalizeDashboardLayout({
      columns: 84,
      rowHeight: 8,
      widgets: [
        { id: "a", type: "value", title: "A", x: 0, y: 0, w: 4, h: 2 },
        { id: "b", type: "value", title: "B", x: 0, y: 0, w: 4, h: 2 },
      ],
    });
    expect(layout.widgets[0]?.w).toBe(28);
    expect(layout.widgets[0]?.h).toBe(14);
    expect(layout.widgets[1]?.y).toBeGreaterThanOrEqual(14);
  });

  it("scales legacy w=7 widgets on fine grid (SCR-07 style split)", () => {
    const layout = normalizeDashboardLayout({
      columns: 84,
      rowHeight: 8,
      widgets: [
        { id: "feed", type: "event-feed", title: "Feed", x: 0, y: 2, w: 5, h: 5 },
        { id: "report", type: "report", title: "Report", x: 5, y: 2, w: 7, h: 5 },
      ],
    });
    expect(layout.widgets[0]?.w).toBe(35);
    expect(layout.widgets[1]?.w).toBe(49);
    expect(layout.widgets[1]?.x).toBe(35);
  });

  it("scales only legacy widgets in a mixed layout", () => {
    const layout = normalizeDashboardLayout({
      columns: 84,
      rowHeight: 8,
      widgets: [
        { id: "fine", type: "value", title: "Fine", x: 0, y: 0, w: 84, h: 14 },
        { id: "legacy", type: "value", title: "Legacy", x: 0, y: 6, w: 6, h: 4 },
      ],
    });
    expect(layout.widgets[0]?.w).toBe(84);
    expect(layout.widgets[1]?.w).toBe(42);
  });
});
