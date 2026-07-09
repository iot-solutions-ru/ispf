import { describe, expect, it } from "vitest";
import { isAnalyticsTagDevice } from "./analyticsPath";

describe("isAnalyticsTagDevice", () => {
  it("returns true when derivedValue is listed", () => {
    expect(
      isAnalyticsTagDevice({
        type: "DEVICE",
        variableNames: ["temperature", "derivedValue"],
      }),
    ).toBe(true);
  });

  it("returns false for lite tree payloads with empty variableNames", () => {
    expect(
      isAnalyticsTagDevice({
        type: "DEVICE",
        variableNames: [],
      }),
    ).toBe(false);
  });

  it("returns false for non-device types", () => {
    expect(
      isAnalyticsTagDevice({
        type: "FOLDER",
        variableNames: ["derivedValue"],
      }),
    ).toBe(false);
  });
});
