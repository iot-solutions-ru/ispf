import { describe, expect, it } from "vitest";
import type { MimicElement } from "../types/scadaMimic";
import { directionFromArrowKey, findElementInDirection } from "./mimicKeyboardNav";

const base = (id: string, x: number, y: number): MimicElement => ({
  id,
  symbolId: "valve.gate",
  layerId: "layer-default",
  x,
  y,
  bindings: {},
  props: {},
});

describe("mimicKeyboardNav", () => {
  const elements = [
    base("a", 10, 10),
    base("b", 100, 10),
    base("c", 10, 100),
    base("d", 100, 100),
  ];

  it("finds nearest element to the right", () => {
    expect(findElementInDirection(elements, "a", "right")?.id).toBe("b");
  });

  it("finds nearest element below", () => {
    expect(findElementInDirection(elements, "a", "down")?.id).toBe("c");
  });

  it("finds nearest element to the left", () => {
    expect(findElementInDirection(elements, "b", "left")?.id).toBe("a");
  });

  it("finds nearest element above", () => {
    expect(findElementInDirection(elements, "c", "up")?.id).toBe("a");
  });

  it("returns first element when nothing is selected", () => {
    expect(findElementInDirection(elements, null, "right")?.id).toBe("a");
  });

  it("returns null when no neighbor exists in direction", () => {
    expect(findElementInDirection([base("solo", 0, 0)], "solo", "right")).toBeNull();
  });

  it("maps arrow keys to directions", () => {
    expect(directionFromArrowKey("ArrowRight")).toBe("right");
    expect(directionFromArrowKey("Escape")).toBeNull();
  });
});
