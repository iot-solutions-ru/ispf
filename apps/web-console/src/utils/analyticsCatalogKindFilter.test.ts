import { describe, expect, it } from "vitest";
import { matchesAnalyticsCatalogKindFilter } from "./analyticsCatalogKindFilter";
import type { AnalyticsCatalogEntryDto } from "../api/analyticsCatalog";

function entry(overrides: Partial<AnalyticsCatalogEntryDto>): AnalyticsCatalogEntryDto {
  return {
    id: "percentChange",
    displayName: "Percent change",
    tier: "C",
    kinds: ["helper", "binding-rule"],
    syntax: "percentChange(sourcePath, window)",
    parameters: [],
    description: "Tier C demo",
    examples: [],
    tags: ["kpi", "historian", "statistics"],
    pack: "ispf-analytics-kpi-demo",
    docAnchor: "percentChange",
    ...overrides,
  };
}

describe("matchesAnalyticsCatalogKindFilter", () => {
  it("matches historian via kinds", () => {
    expect(matchesAnalyticsCatalogKindFilter(entry({ kinds: ["historian"] }), "historian")).toBe(true);
  });

  it("matches historian via tags when kinds are legacy helper labels", () => {
    expect(matchesAnalyticsCatalogKindFilter(entry({ kinds: ["helper", "binding-rule"] }), "historian")).toBe(true);
  });

  it("does not match reactive for historian-only entry", () => {
    expect(matchesAnalyticsCatalogKindFilter(entry({ kinds: ["historian"] }), "reactive")).toBe(false);
  });
});
