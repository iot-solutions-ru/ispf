import { describe, expect, it } from "vitest";
import {
  buildSubscribePayload,
  noteObjectWebSocketCursor,
  resetObjectWebSocketCursorForTests,
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

  it("sends lastSeq after a frame and jumps the cursor on replay_gap", () => {
    resetObjectWebSocketCursorForTests();
    const release = trackObjectVariableSubscriptions([{ path: "root.dev.a" }]);
    noteObjectWebSocketCursor({ type: "VARIABLE_UPDATED", seq: 4 });
    expect(buildSubscribePayload().lastSeq).toBe(4);
    noteObjectWebSocketCursor({ type: "replay_gap", lastSeq: 9 });
    expect(buildSubscribePayload().lastSeq).toBe(9);
    noteObjectWebSocketCursor({ type: "replay_gap", lastSeq: 0 });
    expect(buildSubscribePayload().lastSeq).toBeUndefined();
    release();
    resetObjectWebSocketCursorForTests();
  });
});
