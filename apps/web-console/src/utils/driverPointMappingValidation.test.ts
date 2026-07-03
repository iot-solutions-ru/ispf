import { describe, expect, it } from "vitest";
import {
  hasDriverMappingErrors,
  validateDriverPointMappingsJson,
} from "./driverPointMappingValidation";

describe("validateDriverPointMappingsJson", () => {
  it("accepts valid mappings", () => {
    const result = validateDriverPointMappingsJson(
      JSON.stringify({ temperature: "ns=2;s=Temp" }),
      ["temperature"],
    );
    expect(hasDriverMappingErrors(result)).toBe(false);
    expect(result.mappings.temperature).toBe("ns=2;s=Temp");
  });

  it("flags invalid json", () => {
    const result = validateDriverPointMappingsJson("{bad", []);
    expect(hasDriverMappingErrors(result)).toBe(true);
    expect(result.issues[0]?.code).toBe("invalid_json");
  });

  it("warns on unknown variable and duplicate address", () => {
    const result = validateDriverPointMappingsJson(
      JSON.stringify({ a: "point-1", b: "point-1" }),
      ["a"],
    );
    expect(result.issues.some((issue) => issue.code === "unknown_variable")).toBe(true);
    expect(result.issues.some((issue) => issue.code === "duplicate_address")).toBe(true);
  });

  it("suggests haystack tags for string mappings", () => {
    const result = validateDriverPointMappingsJson(
      JSON.stringify({ temperature: "HOLDING:1:40001" }),
      ["temperature"],
    );
    expect(result.issues.some((issue) => issue.code === "haystack_object_suggested")).toBe(true);
  });

  it("hints missing haystack tags on extended mapping", () => {
    const result = validateDriverPointMappingsJson(
      JSON.stringify({
        temperature: { point: "ns=2;s=Temp", haystackTags: ["point"] },
      }),
      ["temperature"],
    );
    expect(result.issues.some((issue) => issue.code === "haystack_tags_missing")).toBe(true);
  });
});
