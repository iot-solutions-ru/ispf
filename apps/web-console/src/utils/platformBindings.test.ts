import { describe, expect, it } from "vitest";
import {
  PLATFORM_BINDING_ENTRIES,
  PLATFORM_BINDING_NAMES,
  buildPlatformBindingExpression,
  defaultParamValues,
} from "./platformBindings";

describe("platformBindings", () => {
  it("lists 20 functions matching PlatformBindingRegistry", () => {
    expect(PLATFORM_BINDING_ENTRIES).toHaveLength(20);
    expect(PLATFORM_BINDING_NAMES).toContain("counterRate");
    expect(PLATFORM_BINDING_NAMES).toContain("call");
    expect(PLATFORM_BINDING_NAMES).toContain("sumRecordField");
  });

  it("uses unique ids", () => {
    const ids = PLATFORM_BINDING_ENTRIES.map((entry) => entry.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("builds movingAvg with object variables", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "movingAvg");
    expect(entry).toBeDefined();
    const values = defaultParamValues(entry!, {
      variableNames: ["temperature"],
    });
    expect(buildPlatformBindingExpression(entry!, values)).toBe("movingAvg(temperature, 60)");
  });

  it("builds readRef with object path", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "readRef");
    expect(entry).toBeDefined();
    const values = defaultParamValues(entry!, {
      objectPath: "root.platform.devices.pump-01",
      variableNames: ["pressure"],
    });
    expect(buildPlatformBindingExpression(entry!, values)).toBe(
      'read("root.platform.devices.pump-01/pressure")'
    );
  });

  it("builds avg analytics helper for chart bindings", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "avgHistorian");
    expect(entry).toBeDefined();
    const values = defaultParamValues(entry!, {
      variableNames: ["temperature"],
    });
    expect(buildPlatformBindingExpression(entry!, values)).toBe("avg(@/temperature, 5m)");
  });

  it("converts dot-form historian source to slash ref", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "avgHistorian");
    expect(entry).toBeDefined();
    expect(
      buildPlatformBindingExpression(entry!, {
        source: "root.platform.devices.analytics-demo.chain-a.derived-a",
        windowBucket: "5m",
      })
    ).toBe("avg(root.platform.devices.analytics-demo.chain-a/derived-a, 5m)");
  });

  it("omits optional callRef input", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "callRef");
    expect(entry).toBeDefined();
    expect(
      buildPlatformBindingExpression(entry!, {
        function: "scaleValue",
        input: "",
      })
    ).toBe("call(@/fn/scaleValue)");
  });
});
