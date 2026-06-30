import { describe, expect, it } from "vitest";
import { variablesRefetchIntervalMs } from "./variablesQueryPolicy";

describe("variablesRefetchIntervalMs", () => {
  it("polls on an interval when WebSocket is disconnected", () => {
    expect(variablesRefetchIntervalMs(5000, false)).toBe(5000);
  });

  it("disables polling when WebSocket pushes live invalidations", () => {
    expect(variablesRefetchIntervalMs(5000, true)).toBe(false);
  });
});
