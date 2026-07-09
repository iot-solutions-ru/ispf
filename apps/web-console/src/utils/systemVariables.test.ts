import { describe, expect, it } from "vitest";
import { filterUserVariableNames, isHiddenObjectVariable } from "./systemVariables";

describe("systemVariables", () => {
  it("hides historian and binding metadata", () => {
    expect(isHiddenObjectVariable("@historianRuleMeta")).toBe(true);
    expect(isHiddenObjectVariable("@bindingRules")).toBe(true);
    expect(isHiddenObjectVariable("temperature")).toBe(false);
  });

  it("filters @-prefixed names from pickers", () => {
    expect(
      filterUserVariableNames(["temperature", "@historianRuleMeta", "@bindingRules", "derived-a"]),
    ).toEqual(["temperature", "derived-a"]);
  });
});
