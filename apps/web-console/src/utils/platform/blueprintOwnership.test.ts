import { describe, expect, it } from "vitest";
import { isBlueprintOwned } from "./blueprintOwnership";

describe("isBlueprintOwned", () => {
  const ownership = {
    variables: ["mixVar1"],
    events: ["mixAlarm"],
    functions: ["reset"],
    bindingRuleIds: ["rule-1"],
  };

  it("matches contributed names", () => {
    expect(isBlueprintOwned(ownership, "variables", "mixVar1")).toBe(true);
    expect(isBlueprintOwned(ownership, "events", "mixAlarm")).toBe(true);
    expect(isBlueprintOwned(ownership, "functions", "reset")).toBe(true);
    expect(isBlueprintOwned(ownership, "bindingRuleIds", "rule-1")).toBe(true);
  });

  it("leaves locally created members deletable", () => {
    expect(isBlueprintOwned(ownership, "variables", "localNote")).toBe(false);
    expect(isBlueprintOwned(undefined, "variables", "mixVar1")).toBe(false);
  });
});
