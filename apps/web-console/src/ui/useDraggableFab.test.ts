import { describe, expect, it } from "vitest";
import { clampFabPos, defaultFabPos, drawerStyleFor } from "./useDraggableFab";

const viewport = { width: 1200, height: 800 };

describe("useDraggableFab geometry", () => {
  it("keeps the circle inside the viewport", () => {
    expect(clampFabPos({ x: -40, y: 900 }, viewport)).toEqual({ x: 8, y: 740 });
    expect(clampFabPos({ x: 200, y: 100 }, viewport)).toEqual({ x: 200, y: 100 });
  });

  it("starts near the bottom-right corner", () => {
    expect(defaultFabPos(viewport)).toEqual({ x: 1128, y: 728 });
  });

  it("opens the panel above the circle when there is room", () => {
    const style = drawerStyleFor({ x: 700, y: 600 }, 320, 420, viewport);
    expect(style.left).toBe(332);
    expect(style.top).toBe(268);
    expect(style.width).toBe(420);
  });

  it("drops the panel below the circle when the top edge is too close", () => {
    const style = drawerStyleFor({ x: 80, y: 20 }, 320, 420, viewport);
    expect(style.left).toBe(8);
    expect(style.top).toBe(84);
  });
});
