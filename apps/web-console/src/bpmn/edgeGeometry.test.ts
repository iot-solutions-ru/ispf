import { describe, expect, it } from "vitest";
import { borderPoint, edgeEndpoints, isConditionalEdge } from "./edgeGeometry";
import type { FlowNode } from "./model/types";

function node(partial: Partial<FlowNode> & Pick<FlowNode, "type">): FlowNode {
  return {
    id: "n",
    name: "",
    x: 0,
    y: 0,
    width: 100,
    height: 80,
    ispf: {},
    ...partial,
  };
}

describe("edgeGeometry", () => {
  it("hits circle border for events", () => {
    const start = node({ type: "startEvent", width: 36, height: 36, x: 0, y: 0 });
    const p = borderPoint(start, { x: 200, y: 18 });
    expect(p.x).toBeCloseTo(36, 0);
    expect(p.y).toBeCloseTo(18, 0);
  });

  it("marks conditional only from activities", () => {
    const task = node({ type: "serviceTask" });
    const gw = node({ type: "exclusiveGateway", width: 50, height: 50 });
    const edge = { id: "f", sourceRef: "a", targetRef: "b", condition: "x > 1" };
    expect(isConditionalEdge(edge, task)).toBe(true);
    expect(isConditionalEdge(edge, gw)).toBe(false);
  });

  it("returns distinct endpoints", () => {
    const a = node({ type: "serviceTask", x: 0, y: 0 });
    const b = node({ type: "endEvent", width: 36, height: 36, x: 200, y: 22 });
    const { from, to } = edgeEndpoints(a, b);
    expect(from.x).toBeLessThan(to.x);
    expect(from.x).toBeGreaterThan(a.x);
  });
});
