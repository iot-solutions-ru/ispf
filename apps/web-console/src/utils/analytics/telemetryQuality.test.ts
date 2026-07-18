import { describe, expect, it } from "vitest";
import { isPlottableTelemetryQuality, parseTelemetryQuality } from "./telemetryQuality";

describe("telemetryQuality", () => {
  it("parses normalized levels", () => {
    expect(parseTelemetryQuality("GOOD")).toBe("GOOD");
    expect(parseTelemetryQuality("uncertain")).toBe("UNCERTAIN");
    expect(parseTelemetryQuality("bad")).toBe("BAD");
  });

  it("treats BAD as not plottable", () => {
    expect(isPlottableTelemetryQuality("BAD")).toBe(false);
    expect(isPlottableTelemetryQuality("GOOD")).toBe(true);
  });
});
