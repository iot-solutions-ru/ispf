import { describe, expect, it } from "vitest";
import type { VariableDto } from "../../types";
import {
  definitionFromObjectVariable,
  objectVariablesAvailableForDefinition,
} from "./blueprintDefinitionVariables";

describe("objectVariablesAvailableForDefinition", () => {
  const objectVariables = [
    { name: "blueprintType" },
    { name: "suitabilityExpression" },
    { name: "mixVar1" },
    { name: "mixVar2" },
  ];

  it("offers object variables that are not catalog metadata and not already in the definition", () => {
    expect(objectVariablesAvailableForDefinition(objectVariables, [{ name: "mixVar2" }])).toEqual([
      { name: "mixVar1" },
    ]);
  });

  it("returns nothing when every payload variable is already in the definition", () => {
    expect(objectVariablesAvailableForDefinition(objectVariables, [
      { name: "mixVar1" },
      { name: "mixVar2" },
    ])).toEqual([]);
  });
});

describe("definitionFromObjectVariable", () => {
  it("copies the object variable name, schema, and value into the definition", () => {
    const variable: VariableDto = {
      name: "mixVar1",
      value: {
        schema: { name: "mixVar1", fields: [{ name: "value", type: "INTEGER" }] },
        rows: [{ value: 3 }],
      },
      readable: true,
      writable: true,
      updatedAt: null,
      historyEnabled: true,
      historyRetentionDays: 7,
    };
    expect(definitionFromObjectVariable(variable)).toMatchObject({
      name: "mixVar1",
      group: "default",
      readable: true,
      writable: true,
      historyEnabled: true,
      historyRetentionDays: 7,
      defaultValue: variable.value,
      schema: variable.value?.schema,
    });
  });
});
