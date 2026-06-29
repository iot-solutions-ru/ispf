import { describe, expect, it } from "vitest";
import type { MimicConnection, MimicElement } from "../types/scadaMimic";
import { recomputeConnectionPoints, rerouteConnectionsForElement } from "./connectionRouting";

const tank: MimicElement = {
  id: "t1",
  symbolId: "tank.vertical",
  layerId: "layer-default",
  x: 100,
  y: 80,
  bindings: {},
  props: {},
};

const valve: MimicElement = {
  id: "v1",
  symbolId: "valve.gate",
  layerId: "layer-default",
  x: 300,
  y: 120,
  bindings: {},
  props: {},
};

function makeConnection(points: { x: number; y: number }[]): MimicConnection {
  return {
    id: "conn-1",
    layerId: "layer-default",
    from: { elementId: "t1", port: "e" },
    to: { elementId: "v1", port: "n" },
    points,
  };
}

describe("recomputeConnectionPoints", () => {
  it("re-routes from scratch instead of preserving stale bend points", () => {
    const connection = makeConnection([
      { x: 0, y: 0 },
      { x: 50, y: 0 },
      { x: 50, y: 200 },
      { x: 400, y: 200 },
    ]);

    const rerouted = recomputeConnectionPoints(connection, [tank, valve]);
    expect(rerouted).not.toEqual(connection.points);
    expect(rerouted).toEqual([
      { x: 180, y: 140 },
      { x: 249, y: 140 },
      { x: 249, y: 120 },
      { x: 318, y: 120 },
    ]);
  });
});

describe("rerouteConnectionsForElement", () => {
  it("updates stored points when a connected element moves", () => {
    const connection = makeConnection([
      { x: 180, y: 140 },
      { x: 249, y: 140 },
      { x: 249, y: 120 },
      { x: 318, y: 120 },
    ]);
    const movedTank = { ...tank, x: 40, y: 40 };
    const [updated] = rerouteConnectionsForElement("t1", [connection], [movedTank, valve]);

    expect(updated.points).toEqual([
      { x: 120, y: 100 },
      { x: 219, y: 100 },
      { x: 219, y: 120 },
      { x: 318, y: 120 },
    ]);
  });
});
