import { describe, expect, it } from "vitest";
import {
  buildSubscribePayload,
  trackObjectVariableSubscriptions,
} from "./useObjectWebSocket";

describe("object variable WS interest merge", () => {
  it("builds path-wide subscribe without variablesByPath entry", () => {
    const release = trackObjectVariableSubscriptions([{ path: "root.dev.a" }]);
    expect(buildSubscribePayload()).toEqual({
      type: "subscribe",
      paths: ["root.dev.a"],
      variablesByPath: {},
    });
    release();
    expect(buildSubscribePayload()).toEqual({
      type: "subscribe",
      paths: [],
      variablesByPath: {},
    });
  });

  it("narrows interest to variables and unions consumers", () => {
    const a = trackObjectVariableSubscriptions([
      { path: "root.dev.a", variables: ["temperature"] },
    ]);
    const b = trackObjectVariableSubscriptions([
      { path: "root.dev.a", variables: ["humidity", "temperature"] },
    ]);
    expect(buildSubscribePayload()).toEqual({
      type: "subscribe",
      paths: ["root.dev.a"],
      variablesByPath: { "root.dev.a": ["humidity", "temperature"] },
    });
    a();
    expect(buildSubscribePayload().variablesByPath["root.dev.a"]).toEqual([
      "humidity",
      "temperature",
    ]);
    b();
    expect(buildSubscribePayload().paths).toEqual([]);
  });

  it("path-wide wins over variable-only for same path", () => {
    const narrow = trackObjectVariableSubscriptions([
      { path: "root.dev.a", variables: ["temperature"] },
    ]);
    const wide = trackObjectVariableSubscriptions([{ path: "root.dev.a" }]);
    expect(buildSubscribePayload()).toEqual({
      type: "subscribe",
      paths: ["root.dev.a"],
      variablesByPath: {},
    });
    wide();
    expect(buildSubscribePayload().variablesByPath["root.dev.a"]).toEqual(["temperature"]);
    narrow();
  });
});
