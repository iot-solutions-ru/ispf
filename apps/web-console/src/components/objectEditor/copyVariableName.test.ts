import { describe, expect, it } from "vitest";
import { schemaForVariableCopy, suggestedVariableCopyName, valueForVariableCopy } from "./copyVariableName";
import type { VariableDto } from "../../types";

function variable(partial: Partial<VariableDto> & Pick<VariableDto, "name">): VariableDto {
  return {
    value: null,
    readable: true,
    writable: true,
    updatedAt: null,
    historyEnabled: false,
    historyRetentionDays: null,
    ...partial,
  };
}

describe("suggestedVariableCopyName", () => {
  it("appends Copy to a code name", () => {
    expect(suggestedVariableCopyName("temperature")).toBe("temperatureCopy");
  });

  it("leaves the name empty when the source is not a code", () => {
    expect(suggestedVariableCopyName("a-b")).toBe("");
  });
});

describe("schemaForVariableCopy", () => {
  it("renames the schema to the new variable", () => {
    const schema = schemaForVariableCopy(
      variable({
        name: "temperature",
        schema: { name: "temperature", fields: [{ name: "value", type: "DOUBLE" }] },
      }),
      "temperatureCopy",
    );
    expect(schema?.name).toBe("temperatureCopy");
    expect(schema?.fields[0]?.name).toBe("value");
  });
});

describe("valueForVariableCopy", () => {
  it("keeps the current rows under the new schema", () => {
    const schema = { name: "temperatureCopy", fields: [{ name: "value", type: "DOUBLE" as const }] };
    const value = valueForVariableCopy(
      variable({
        name: "temperature",
        value: { schema: { name: "temperature", fields: schema.fields }, rows: [{ value: 3.5 }] },
      }),
      schema,
    );
    expect(value?.schema.name).toBe("temperatureCopy");
    expect(value?.rows).toEqual([{ value: 3.5 }]);
  });
});
