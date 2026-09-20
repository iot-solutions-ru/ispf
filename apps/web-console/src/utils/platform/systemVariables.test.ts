import { describe, expect, it } from "vitest";
import {
  filterUserVariableNames,
  isDeletableUserVariable,
  isHiddenObjectVariable,
} from "./systemVariables";

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

  it("allows deleting user variables but not driver or reserved names", () => {
    expect(isDeletableUserVariable("numberVar")).toBe(true);
    expect(isDeletableUserVariable("temperature")).toBe(true);
    expect(isDeletableUserVariable("@bindingRules")).toBe(false);
    expect(isDeletableUserVariable("driverId")).toBe(false);
    expect(isDeletableUserVariable("driverPointMappingsJson")).toBe(false);
    expect(isDeletableUserVariable("uiIcon")).toBe(false);
  });
});
