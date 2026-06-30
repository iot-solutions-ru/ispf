import { describe, expect, it } from "vitest";
import { parseHaystackTagsJson, serializeHaystackTags } from "../constants/haystackMarkers";

describe("haystackMarkers", () => {
  it("parses JSON tag arrays", () => {
    expect(parseHaystackTagsJson('["equip","temp"]')).toEqual(["equip", "temp"]);
    expect(parseHaystackTagsJson("")).toEqual([]);
    expect(parseHaystackTagsJson("not-json")).toEqual([]);
  });

  it("serializes unique sorted tags", () => {
    expect(serializeHaystackTags(["temp", "equip", "temp"])).toBe('["equip","temp"]');
  });
});
