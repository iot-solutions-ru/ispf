import { describe, expect, it } from "vitest";
import type { MimicElement } from "../types/scadaMimic";
import {
  alignElements,
  applyElementResize,
  distributeElements,
  flipElement,
  getElementBounds,
  rotateElement,
} from "./layoutOps";

const elA: MimicElement = {
  id: "a",
  symbolId: "valve.gate",
  layerId: "layer-default",
  x: 10,
  y: 20,
  bindings: {},
  props: {},
};

const elB: MimicElement = {
  id: "b",
  symbolId: "valve.gate",
  layerId: "layer-default",
  x: 100,
  y: 50,
  bindings: {},
  props: {},
};

const elC: MimicElement = {
  id: "c",
  symbolId: "valve.gate",
  layerId: "layer-default",
  x: 200,
  y: 80,
  bindings: {},
  props: {},
};

describe("alignElements", () => {
  it("aligns left edges", () => {
    const result = alignElements([elA, elB], new Set(["a", "b"]), "left");
    expect(result.find((e) => e.id === "a")?.x).toBe(10);
    expect(result.find((e) => e.id === "b")?.x).toBe(10);
  });

  it("aligns top edges", () => {
    const result = alignElements([elA, elB], new Set(["a", "b"]), "top");
    expect(result.find((e) => e.id === "a")?.y).toBe(20);
    expect(result.find((e) => e.id === "b")?.y).toBe(20);
  });
});

describe("distributeElements", () => {
  it("distributes horizontally by center", () => {
    const result = distributeElements([elA, elB, elC], new Set(["a", "b", "c"]), "horizontal");
    const b = result.find((e) => e.id === "b");
    const boundsB = getElementBounds(b!);
    const boundsA = getElementBounds(result.find((e) => e.id === "a")!);
    const boundsC = getElementBounds(result.find((e) => e.id === "c")!);
    expect(boundsB.centerX - boundsA.centerX).toBeCloseTo(boundsC.centerX - boundsB.centerX, 0);
  });
});

describe("flipElement", () => {
  it("toggles flipX", () => {
    const once = flipElement(elA, "h");
    expect(once.props?.flipX).toBe(true);
    const twice = flipElement(once, "h");
    expect(twice.props?.flipX).toBe(false);
  });
});

describe("rotateElement", () => {
  it("rotates by 90 degrees", () => {
    expect(rotateElement(elA, 90).rotation).toBe(90);
    expect(rotateElement({ ...elA, rotation: 270 }, 90).rotation).toBe(0);
  });
});

describe("applyElementResize", () => {
  it("expands from southeast handle", () => {
    const resized = applyElementResize(elA, "se", 20, 30);
    expect(resized.x).toBe(10);
    expect(resized.y).toBe(20);
    expect(resized.width).toBeGreaterThan(getElementBounds(elA).width);
    expect(resized.height).toBeGreaterThan(getElementBounds(elA).height);
  });

  it("moves origin when resizing northwest", () => {
    const resized = applyElementResize(elA, "nw", -10, -10);
    expect(resized.x).toBeLessThan(elA.x);
    expect(resized.y).toBeLessThan(elA.y);
  });
});
