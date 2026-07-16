import { describe, expect, it } from "vitest";
import { variablesRefetchIntervalMs } from "./variablesQueryPolicy";

describe("variablesRefetchIntervalMs", () => {
  it("polls on an interval when WebSocket is disconnected", () => {
    expect(variablesRefetchIntervalMs(5000, false)).toBe(5000);
  });

  it("keeps polling when WebSocket is connected", () => {
    expect(variablesRefetchIntervalMs(5000, true)).toBe(5000);
  });
});
