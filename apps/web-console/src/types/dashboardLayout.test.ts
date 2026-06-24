import { describe, expect, it } from "vitest";
import { emptyLayout, resolveDashboardLayout } from "./dashboard";

describe("resolveDashboardLayout", () => {
  it("prefers layoutJson when it contains widgets", () => {
    const layout = resolveDashboardLayout({
      path: "root.platform.dashboards.demo",
      title: "Demo",
      refreshIntervalMs: 5000,
      layoutJson: JSON.stringify({
        columns: 12,
        rowHeight: 72,
        widgets: [{ id: "a", type: "value", title: "A", x: 2, y: 1, w: 3, h: 2 }],
      }),
      layout: {
        columns: 12,
        rowHeight: 72,
        widgets: [{ id: "b", type: "value", title: "B", x: 0, y: 0, w: 1, h: 1 }],
      },
    });
    expect(layout.widgets[0]?.id).toBe("a");
    expect(layout.widgets[0]?.x).toBe(2);
  });

  it("falls back to parsed layout object when layoutJson is invalid", () => {
    const layout = resolveDashboardLayout({
      path: "root.platform.dashboards.demo",
      title: "Demo",
      refreshIntervalMs: 5000,
      layoutJson: "{not-json",
      layout: {
        columns: 12,
        rowHeight: 72,
        widgets: [{ id: "b", type: "value", title: "B", x: 4, y: 3, w: 2, h: 2 }],
      },
    });
    expect(layout.widgets[0]?.id).toBe("b");
    expect(layout.widgets[0]?.x).toBe(4);
  });

  it("returns empty layout when view is missing", () => {
    expect(resolveDashboardLayout(undefined)).toEqual(emptyLayout());
  });
});
