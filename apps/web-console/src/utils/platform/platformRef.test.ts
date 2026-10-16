import { describe, expect, it } from "vitest";
import type { VariableDto } from "../../types";
import { formatPlatformRef, normalizeVariableRefField, parsePlatformRef, refFromFields } from "./platformRef";

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

  it("drops target field that is not in variable schema", () => {
    const variables: VariableDto[] = [
      {
        name: "blueprintType",
        value: {
          schema: { name: "blueprintType", fields: [{ name: "value", type: "STRING" }] },
          rows: [{ value: "INSTANCE" }],
        },
        readable: true,
        writable: true,
        updatedAt: null,
        historyEnabled: false,
        historyRetentionDays: null,
      },
    ];
    expect(normalizeVariableRefField("blueprintType", "sine", variables)).toBe("value");
  });
});
