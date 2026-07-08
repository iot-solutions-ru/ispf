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
    expect(PLATFORM_BINDING_NAMES).toContain("callFunctionAt");
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

  it("builds refAt with object path", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "refAt");
    expect(entry).toBeDefined();
    const values = defaultParamValues(entry!, {
      objectPath: "root.platform.devices.pump-01",
      variableNames: ["pressure"],
    });
    expect(buildPlatformBindingExpression(entry!, values)).toBe(
      'refAt("root.platform.devices.pump-01", pressure)'
    );
  });

  it("builds rollingAvg analytics helper for chart bindings", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "rollingAvg");
    expect(entry).toBeDefined();
    const values = defaultParamValues(entry!, {
      variableNames: ["temperature"],
    });
    expect(buildPlatformBindingExpression(entry!, values)).toBe("rollingAvg('temperature', '5m')");
  });

  it("omits optional callFunction input", () => {
    const entry = PLATFORM_BINDING_ENTRIES.find((item) => item.id === "callFunction");
    expect(entry).toBeDefined();
    expect(
      buildPlatformBindingExpression(entry!, {
        function: "scaleValue",
        input: "",
      })
    ).toBe("callFunction(scaleValue)");
  });
});
