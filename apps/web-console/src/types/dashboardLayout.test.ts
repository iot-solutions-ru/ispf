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

  it("does not scale or rewrite fine-grid coordinates", () => {
    const layout = normalizeDashboardLayout({
      columns: 84,
      rowHeight: 8,
      widgets: [
        { id: "a", type: "value", title: "A", x: 0, y: 0, w: 4, h: 2 },
        { id: "b", type: "value", title: "B", x: 4, y: 0, w: 4, h: 2 },
      ],
    });
    expect(layout.widgets[0]?.w).toBe(4);
    expect(layout.widgets[0]?.h).toBe(2);
    expect(layout.widgets[1]?.x).toBe(4);
    expect(layout.widgets[1]?.y).toBe(0);
  });

  it("does not migrate 12×72 layouts anymore", () => {
    const layout = normalizeDashboardLayout({
      columns: 12,
      rowHeight: 72,
      widgets: [
        { id: "a", type: "value", title: "A", x: 0, y: 0, w: 6, h: 2 },
        { id: "b", type: "value", title: "B", x: 6, y: 0, w: 6, h: 2 },
      ],
    });
    expect(layout.columns).toBe(12);
    expect(layout.rowHeight).toBe(72);
    expect(layout.widgets[0]?.w).toBe(6);
    expect(layout.widgets[1]?.x).toBe(6);
  });

  it("does not inflate NAV when sibling widgets are compact", () => {
    const layout = normalizeDashboardLayout({
      columns: 84,
      rowHeight: 8,
      widgets: [
        { id: "nav", type: "dashboard-link", title: "Nav", x: 0, y: 0, w: 10, h: 7 },
        { id: "kpi", type: "value", title: "KPI", x: 10, y: 0, w: 12, h: 7 },
      ],
    });
    expect(layout.widgets[0]?.w).toBe(10);
    expect(layout.widgets[0]?.h).toBe(7);
    expect(layout.widgets[1]?.x).toBe(10);
    expect(layout.widgets[1]?.y).toBe(0);
  });
});
