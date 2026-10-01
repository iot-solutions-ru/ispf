import { describe, expect, it } from "vitest";
import type { FunctionDescriptor } from "../../types";
import { functionHasImplementation } from "./functionInvocable";

function fn(partial: Partial<FunctionDescriptor> & Pick<FunctionDescriptor, "name">): FunctionDescriptor {
  return {
    description: "",
    inputSchema: { name: "in", fields: [] },
    outputSchema: { name: "out", fields: [] },
    sourceType: null,
    sourceBody: null,
    dataSourcePath: null,
    version: null,
    invokeRoles: [],
    ...partial,
  };
}

describe("functionHasImplementation", () => {
  it("allows built-in handler functions without sourceBody", () => {
    expect(functionHasImplementation(fn({ name: "appendTableRow" }))).toBe(true);
    expect(functionHasImplementation(fn({ name: "calculate", sourceType: "" }))).toBe(true);
  });

  it("requires body for script and java", () => {
    expect(functionHasImplementation(fn({ name: "x", sourceType: "script", sourceBody: null }))).toBe(false);
    expect(functionHasImplementation(fn({ name: "x", sourceType: "java", sourceBody: " " }))).toBe(false);
    expect(functionHasImplementation(fn({ name: "x", sourceType: "script", sourceBody: '{"steps":[]}' }))).toBe(true);
  });

  it("allows implicit script when sourceType is empty but body is set", () => {
    expect(
      functionHasImplementation(fn({ name: "x", sourceType: null, sourceBody: '{"steps":[{"type":"return"}]}' }))
    ).toBe(true);
  });
});
