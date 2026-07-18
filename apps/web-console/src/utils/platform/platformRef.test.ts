import { describe, expect, it } from "vitest";
import { formatPlatformRef, parsePlatformRef, refFromFields } from "./platformRef";

describe("platformRef", () => {
  it("parses slash variable ref", () => {
    const ref = parsePlatformRef("@/temperature/value");
    expect(ref).toEqual({
      object: "@",
      kind: "variable",
      name: "temperature",
      field: "value",
    });
  });

  it("formats function ref", () => {
    expect(
      formatPlatformRef({ object: "@", kind: "function", name: "calculate" })
    ).toBe("@/fn/calculate");
  });

  it("builds ref from split JSON fields", () => {
    expect(refFromFields("root.platform.devices.a", "temperature", "value")).toBe(
      "root.platform.devices.a/temperature"
    );
  });

  it("parses tag ref", () => {
    const ref = parsePlatformRef("root.platform.devices.a/tag/rule-1");
    expect(ref).toEqual({
      object: "root.platform.devices.a",
      kind: "tag",
      name: "rule-1",
    });
  });

  it("rejects legacy hash tag path", () => {
    expect(parsePlatformRef("root.platform.devices.a#rule-1")).toBeNull();
  });
});
