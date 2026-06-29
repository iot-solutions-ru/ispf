import { describe, expect, it } from "vitest";
import type { DashboardWidget } from "../../types/dashboard";
import {
  bringWidgetForward,
  bringWidgetToFront,
  isWidgetVisible,
  nextWidgetZIndex,
  sendWidgetBackward,
  sendWidgetToBack,
  sortWidgetsForRender,
} from "./widgetLayerUtils";

function valueWidget(id: string, zIndex?: number, visible?: boolean): DashboardWidget {
  return {
    id,
    type: "value",
    title: id,
    x: 0,
    y: 0,
    w: 2,
    h: 2,
    zIndex,
    visible,
  };
}

describe("widgetLayerUtils", () => {
  it("sorts widgets by zIndex with array-index fallback", () => {
    const widgets = [valueWidget("a"), valueWidget("b", 5), valueWidget("c", 1)];
    expect(sortWidgetsForRender(widgets).map((w) => w.id)).toEqual(["a", "c", "b"]);
  });

  it("brings widget forward by swapping z-index with neighbor", () => {
    const widgets = [valueWidget("a", 1), valueWidget("b", 2)];
    const next = bringWidgetForward(widgets, "a");
    expect(next.find((w) => w.id === "a")?.zIndex).toBe(2);
    expect(next.find((w) => w.id === "b")?.zIndex).toBe(1);
  });

  it("sends widget backward by swapping z-index with neighbor", () => {
    const widgets = [valueWidget("a", 1), valueWidget("b", 2)];
    const next = sendWidgetBackward(widgets, "b");
    expect(next.find((w) => w.id === "a")?.zIndex).toBe(2);
    expect(next.find((w) => w.id === "b")?.zIndex).toBe(1);
  });

  it("moves widget to front with a new top z-index", () => {
    const widgets = [valueWidget("a", 1), valueWidget("b", 3)];
    const next = bringWidgetToFront(widgets, "a");
    expect(next.find((w) => w.id === "a")?.zIndex).toBe(4);
  });

  it("moves widget to back below current minimum", () => {
    const widgets = [valueWidget("a", 2), valueWidget("b", 5)];
    const next = sendWidgetToBack(widgets, "b");
    expect(next.find((w) => w.id === "b")?.zIndex).toBe(1);
  });

  it("assigns next z-index for new widgets", () => {
    expect(nextWidgetZIndex([valueWidget("a", 2), valueWidget("b", 7)])).toBe(8);
  });

  it("treats visible undefined as true", () => {
    expect(isWidgetVisible(valueWidget("a"))).toBe(true);
    expect(isWidgetVisible(valueWidget("b", 0, false))).toBe(false);
  });
});
