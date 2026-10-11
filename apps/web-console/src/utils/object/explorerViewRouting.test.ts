import { describe, expect, it } from "vitest";
import { isTypedCatalogObject, opensCatalogChildList } from "./explorerViewRouting";

describe("explorerViewRouting", () => {
  it("keeps typed inspectors for real catalog children", () => {
    expect(isTypedCatalogObject("DATA_SOURCE", true)).toBe(true);
    expect(isTypedCatalogObject("ALERT", true)).toBe(true);
    expect(isTypedCatalogObject("CORRELATOR", true)).toBe(true);
  });

  it("blocks typed inspectors for VISUAL_GROUP under the same path prefixes", () => {
    expect(isTypedCatalogObject("VISUAL_GROUP", true)).toBe(false);
    expect(isTypedCatalogObject("VISUAL_GROUP", false)).toBe(false);
  });

  it("opens the child list for groups and catalog folders", () => {
    expect(opensCatalogChildList("VISUAL_GROUP", false)).toBe(true);
    expect(opensCatalogChildList("DATA_SOURCES", true)).toBe(true);
    expect(opensCatalogChildList("DATA_SOURCE", false)).toBe(false);
  });
});
