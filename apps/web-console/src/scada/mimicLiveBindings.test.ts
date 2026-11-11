import { describe, expect, it } from "vitest";
import { mimicBatchFallbackInterval } from "./mimicLiveBindings";

describe("mimicBatchFallbackInterval", () => {
  it("stops the batch poll while the object socket is connected", () => {
    expect(mimicBatchFallbackInterval(5000, true)).toBe(false);
  });

  it("uses the dashboard interval while the socket is down", () => {
    expect(mimicBatchFallbackInterval(5000, false)).toBe(5000);
  });

  it("stays idle when the dashboard disabled refresh", () => {
    expect(mimicBatchFallbackInterval(false, false)).toBe(false);
    expect(mimicBatchFallbackInterval(0, false)).toBe(false);
  });
});
