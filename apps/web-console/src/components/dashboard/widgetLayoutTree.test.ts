import { describe, expect, it } from "vitest";
import type { DashboardLayout } from "../../types/dashboard";
import {
  findWidgetInLayout,
  getChildrenAtSlot,
  reparentWidgetToSlot,
  resolveAddTargetSlot,
  setChildrenAtSlot,
} from "./widgetLayoutTree";

const layout: DashboardLayout = {
  columns: 84,
  rowHeight: 8,
  widgets: [
    {
      id: "tabs-1",
      type: "tab-panel",
      title: "Tabs",
      x: 0,
      y: 0,
      w: 42,
      h: 28,
      tabsJson: JSON.stringify([
        {
          id: "metrics",
          label: "Metrics",
          children: [{ id: "value-1", type: "value", title: "T", x: 0, y: 0, w: 21, h: 14, objectPath: "", variableName: "t" }],
        },
        { id: "actions", label: "Actions", children: [] },
      ]),
    },
    { id: "chart-1", type: "chart", title: "Chart", x: 42, y: 0, w: 42, h: 28, objectPath: "", variableName: "t" },
  ],
};

describe("widgetLayoutTree", () => {
  it("finds nested widget location", () => {
    const found = findWidgetInLayout(layout, "value-1");
    expect(found?.slot).toEqual({ kind: "tab", containerId: "tabs-1", tabId: "metrics" });
  });

  it("reparents root widget into tab slot", () => {
    const next = reparentWidgetToSlot(layout, "chart-1", {
      kind: "tab",
      containerId: "tabs-1",
      tabId: "actions",
    });
    expect(next.widgets.some((w) => w.id === "chart-1")).toBe(false);
    const children = getChildrenAtSlot(next, { kind: "tab", containerId: "tabs-1", tabId: "actions" });
    expect(children.some((w) => w.id === "chart-1")).toBe(true);
  });

  it("adds to active tab when container selected", () => {
    const slot = resolveAddTargetSlot(layout, "tabs-1", { tabId: { "tabs-1": "actions" } });
    expect(slot).toEqual({ kind: "tab", containerId: "tabs-1", tabId: "actions" });
  });

  it("updates children at slot", () => {
    const next = setChildrenAtSlot(layout, { kind: "root" }, []);
    expect(next.widgets).toHaveLength(0);
  });
});
