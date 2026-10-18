import { describe, expect, it } from "vitest";
import { sqlBindingVariableOptions } from "./sqlBindingVariableOptions";

describe("sqlBindingVariableOptions", () => {
  const names = ["sqlVar", "suitabilityExpression"];

  it("keeps a variable that is not on the target object", () => {
    expect(sqlBindingVariableOptions(names, "testBinding").map((item) => item.value)).toEqual([
      "testBinding",
      "sqlVar",
      "suitabilityExpression",
    ]);
  });

  it("does not change the list when another listed variable is chosen", () => {
    const before = sqlBindingVariableOptions(names, "testBinding");
    const after = sqlBindingVariableOptions(names, "testBinding");
    expect(after).toEqual(before);
  });
});
