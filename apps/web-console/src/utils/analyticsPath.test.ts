import { describe, expect, it } from "vitest";
import { analyticsTagObjectPath, encodeHistorianTagPath, isAnalyticsTagDevice } from "./analyticsPath";

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

  it("extracts object path from slash tag path", () => {
    expect(
      analyticsTagObjectPath("root.platform.devices.sensor-a/tag/avg-temp-5m"),
    ).toBe("root.platform.devices.sensor-a");
  });

  it("encodes canonical historian tag path", () => {
    expect(
      encodeHistorianTagPath("root.platform.devices.chain-c", "chain-c-rule"),
    ).toBe("root.platform.devices.chain-c/tag/chain-c-rule");
  });
});
